package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.github.ruediste.p2psync.storage.BlockType.BlockInstance;

/**
 * Service for managing B-Trees within the data storage system. The tree
 * follows the B-Tree implementation of BTRFS.
 *
 * <p>
 * A tree is a copy-on-write B+ tree with variable sized values. Keys have a
 * fixed serialized size per tree. Internal nodes only store keys and child
 * pointers,
 * leaves
 * store the actual key/value pairs. All keys of a node are sorted in ascending
 * (unsigned lexicographic serialized-byte) order and each child pointer holds
 * the smallest key
 * reachable in the child's subtree.
 *
 * <p>
 * Modifications copy existing tree blocks, but may overwrite blocks modified in
 * the current transaction. Blocks are managed through {@link Storage}.
 * A {@link BTree} therefore references the root of a
 * particular version of the tree; the caller is responsible for persisting the
 * root offset. Entries declare their outgoing block references through their
 * {@link BlockType}; those references are owned by the leaf blocks.
 */
public class BTree<K, V> implements Iterable<BTree.Entry<K, V>> {

    private static final int HEADER_SIZE = 5; // nritems (4) + level (1)
    private static final int NODE_PTR_EXTRA = 8; // blockptr (8)

    private final Storage storage;
    private final KeySerializer<K> keySerializer;
    private final BlockType<Entry<K, V>> entryType;
    public final int keySize;
    public long rootOffset;

    public final BlockType<NodeBlock> blockType;

    public static final BlockType<Entry<byte[], byte[]>> BYTE_ARRAY_ENTRY_TYPE = entryType(BlockType.BYTES);

    /** Adapts a value-only codec; deserialized entries have a null key. */
    public static <K, V> BlockType<Entry<K, V>> entryType(BlockType<V> valueType) {
        return new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(Entry<K, V> entry, DataStorage storage) {
                return valueType.getReferencedBlocks(entry.value, storage);
            }

            @Override
            public ByteBuffer serialize(Entry<K, V> entry) {
                return valueType.serialize(entry.value);
            }

            @Override
            public Entry<K, V> deserialize(ByteBuffer buffer) {
                return new Entry<>(null, valueType.deserialize(buffer));
            }
        };
    }

    public static interface KeySerializer<T> extends BlockSerializer<T> {
        int keySize();

        static KeySerializer<byte[]> bytes(int size) {
            if (size <= 0)
                throw new IllegalArgumentException("Key size must be positive");
            return new ByteArrayKeySerializer(size);
        }

        static KeySerializer<Long> longs() {
            return new LongKeySerializer();
        }
    }

    private record LongKeySerializer() implements KeySerializer<Long> {
        @Override
        public int keySize() {
            return Long.BYTES;
        }

        @Override
        public ByteBuffer serialize(Long value) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(value);
            buffer.flip();
            return buffer;
        }

        @Override
        public Long deserialize(ByteBuffer buffer) {
            if (buffer.remaining() != Long.BYTES)
                throw new IllegalArgumentException("Expected " + Long.BYTES + " key bytes, got " + buffer.remaining());
            return buffer.getLong();
        }
    }

    private record ByteArrayKeySerializer(int keySize) implements KeySerializer<byte[]> {
        @Override
        public ByteBuffer serialize(byte[] value) {
            if (value.length != keySize)
                throw new IllegalArgumentException("Expected " + keySize + " key bytes, got " + value.length);
            return ByteBuffer.wrap(value);
        }

        @Override
        public byte[] deserialize(ByteBuffer buffer) {
            if (buffer.remaining() != keySize)
                throw new IllegalArgumentException("Expected " + keySize + " key bytes, got " + buffer.remaining());
            byte[] result = new byte[keySize];
            buffer.get(result);
            return result;
        }
    }

    public static final class NodeBlock implements BlockInstance<NodeBlock> {
        final byte[] data;
        private final BlockType<NodeBlock> type;

        NodeBlock(byte[] data, BlockType<NodeBlock> type) {
            this.data = data.clone();
            this.type = type;
        }

        @Override
        public BlockType<NodeBlock> getBlockType() {
            return type;
        }
    }

    public static <K, V> BlockType<NodeBlock> blockType(KeySerializer<K> keySerializer,
            DataStorage.Area area, BlockType<Entry<K, V>> entryType) {
        int keySize = keySerializer.keySize();
        return new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(NodeBlock value, DataStorage storage) {
                if (value.data.length < HEADER_SIZE)
                    throw new IllegalArgumentException("B-tree block is shorter than its header");
                ByteBuffer buffer = BTree.wrap(value.data);
                int itemCount = buffer.getInt();
                int level = Byte.toUnsignedInt(buffer.get());
                if (itemCount < 0)
                    throw new IllegalArgumentException("Negative B-tree item count");
                if (level == 0) {
                    int itemSize = keySize + 8;
                    if (HEADER_SIZE + (long) itemCount * itemSize > value.data.length)
                        throw new IllegalArgumentException("B-tree leaf items exceed block size");
                    List<BlockRef> references = new ArrayList<>();
                    for (int index = 0; index < itemCount; index++) {
                        int itemOffset = HEADER_SIZE + index * itemSize;
                        int dataOffset = buffer.getInt(itemOffset + keySize);
                        int dataSize = buffer.getInt(itemOffset + keySize + 4);
                        K itemKey = keySerializer.deserialize(ByteBuffer.wrap(value.data, itemOffset, keySize).slice());
                        Entry<K, V> item = entryType
                                .deserialize(ByteBuffer.wrap(value.data, dataOffset, dataSize).slice());
                        references.addAll(entryType.getReferencedBlocks(new Entry<>(itemKey, item.value), storage));
                    }
                    return references;
                }
                int pointerSize = keySize + NODE_PTR_EXTRA;
                if (HEADER_SIZE + (long) itemCount * pointerSize > value.data.length)
                    throw new IllegalArgumentException("B-tree node pointers exceed block size");
                List<BlockRef> references = new ArrayList<>(itemCount);
                for (int index = 0; index < itemCount; index++) {
                    int offset = HEADER_SIZE + index * pointerSize + keySize;
                    long childOffset = buffer.getLong(offset);
                    if (childOffset != 0)
                        references.add(new BlockRef(childOffset, area, this));
                }
                return references;
            }

            @Override
            public ByteBuffer serialize(NodeBlock value) {
                return ByteBuffer.wrap(value.data);
            }

            @Override
            public NodeBlock deserialize(ByteBuffer buffer) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                return new NodeBlock(data, this);
            }

        };
    }

    public interface Storage {
        int blockSize();

        DataStorage.Area area();

        NodeBlock readBlock(long blockOffset, BlockType<NodeBlock> type);

        /**
         * Check if this block has been modified in the current transaction, and can
         * therefore be modified directly.
         */
        boolean isBlockModified(long blockOffset);

        /** Write a block which has already been modified in the current transaction. */
        void writeBlock(long blockOffset, NodeBlock data);

        /** Allocate and write a new block; its parent supplies its reference. */
        long writeNewBlock(NodeBlock data);

        /**
         * Ensure that there is enough free space to write the specified number of
         * blocks.
         * Returns true if there was enough space, false if the tree might have
         * changed.
         */
        boolean prepareFreeSpace(int blockCount);
    }

    /**
     * Constructs a new empty BTree.
     *
     * @param entryType serializes payloads from full entries; deserialization
     *                  returns
     *                  entries with a null key, which the tree restores separately
     */
    public BTree(Storage storage, KeySerializer<K> keySerializer, BlockType<Entry<K, V>> entryType) {
        this(storage, keySerializer, entryType, 0);
    }

    /**
     * Constructs a new BTree for an existing tree. If the rootOffset is zero, the
     * tree is considered empty. The caller must persist or retain the root
     * reference.
     *
     * @param entryType serializes payloads from full entries; deserialization
     *                  returns
     *                  entries with a null key, which the tree restores separately
     */
    public BTree(Storage storage, KeySerializer<K> keySerializer, BlockType<Entry<K, V>> entryType, long rootOffset) {
        this.storage = storage;
        this.keySerializer = java.util.Objects.requireNonNull(keySerializer);
        this.entryType = java.util.Objects.requireNonNull(entryType);
        this.keySize = keySerializer.keySize();
        this.blockType = blockType(keySerializer, storage.area(), entryType);
        this.rootOffset = rootOffset;
    }

    public static class Entry<K, V> {
        public final K key;
        public final V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class SerializedEntry {
        public final byte[] key;
        public final byte[] value;

        public SerializedEntry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** Returns the value associated with {@code key}, or {@code null}. */
    public V get(K key) {
        byte[] value = getSerialized(serializeKey(key));
        return value == null ? null : entryType.deserialize(ByteBuffer.wrap(value)).value;
    }

    private byte[] getSerialized(byte[] key) {
        checkKey(key);
        long off = rootOffset;
        while (off != 0) {
            byte[] block = readBlock(off);
            if (level(block) == 0) {
                int idx = leafSearch(block, keySize, key);
                if (idx < 0)
                    return null;
                return leafValue(block, keySize, idx);
            }
            List<Ptr> ptrs = parseNode(block, keySize);
            off = ptrs.get(findChild(ptrs, key)).blockptr;
        }
        return null;
    }

    public boolean contains(K key) {
        return getSerialized(serializeKey(key)) != null;
    }

    // ------------------------------------------------------------------
    // Insert / update
    // ------------------------------------------------------------------

    /**
     * Insert or replace the value for {@code key}. The tree's root offset is
     * updated in place.
     */
    public void put(K key, V value) {
        putSerialized(serializeKey(key), bytes(entryType.serialize(new Entry<>(key, value))));
    }

    private void putSerialized(byte[] key, byte[] value) {
        checkKey(key);

        prepareSpaceForWrite();

        if (rootOffset == 0) {
            List<SerializedEntry> entries = new ArrayList<>();
            entries.add(new SerializedEntry(key.clone(), value.clone()));
            rootOffset = writeNewLeaf(entries);
            return;
        }
        int rootLevel = level(readBlock(rootOffset));
        Result res = insertInto(rootOffset, key, value);
        if (res.splitOffset != 0) {
            List<Ptr> ptrs = new ArrayList<>();
            ptrs.add(new Ptr(res.firstKey, res.newOffset));
            ptrs.add(new Ptr(res.splitKey, res.splitOffset));
            rootOffset = writeNewNode(ptrs, rootLevel + 1);
        } else {
            rootOffset = res.newOffset;
        }
    }

    private void prepareSpaceForWrite() {
        while (true) {
            int required = getWorstCaseBlocksForWrite();
            if (storage.prepareFreeSpace(required))
                break;
        }
    }

    public int getWorstCaseBlocksForWrite() {
        int required;
        if (rootOffset == 0)
            required = 1;
        else {
            int h = level(readBlock(rootOffset)) + 1;
            // worst case: split every block along the path into two blocks, and add a new
            // root
            required = 2 * h + 1;
        }
        return required;
    }

    private static class Result {
        long newOffset;
        byte[] firstKey;
        long splitOffset;
        byte[] splitKey;
    }

    private Result insertInto(long offset, byte[] key, byte[] value) {
        byte[] block = readBlock(offset);
        Result res = new Result();
        if (level(block) == 0) {
            List<SerializedEntry> entries = parseLeaf(block, keySize);
            int idx = searchEntries(entries, key);
            if (idx >= 0) {
                entries.set(idx, new SerializedEntry(entries.get(idx).key, value.clone()));
            } else {
                int ins = -(idx + 1);
                entries.add(ins, new SerializedEntry(key.clone(), value.clone()));
            }

            if (fitsLeaf(entries)) {
                res.newOffset = writeLeaf(offset, entries);
                res.firstKey = entries.get(0).key;
            } else {
                int k = chooseLeafSplit(entries);
                List<SerializedEntry> left = new ArrayList<>(entries.subList(0, k));
                List<SerializedEntry> right = new ArrayList<>(entries.subList(k, entries.size()));
                res.splitOffset = writeNewLeaf(right);
                res.newOffset = writeLeaf(offset, left);
                res.firstKey = left.get(0).key;
                res.splitKey = right.get(0).key;
            }
            return res;
        }

        List<Ptr> ptrs = parseNode(block, keySize);
        int child = findChild(ptrs, key);
        Result childRes = insertInto(ptrs.get(child).blockptr, key, value);
        ptrs.get(child).blockptr = childRes.newOffset;
        ptrs.get(child).key = childRes.firstKey;
        if (childRes.splitOffset != 0) {
            ptrs.add(child + 1,
                    new Ptr(childRes.splitKey, childRes.splitOffset));
        }

        if (fitsNode(ptrs)) {
            res.newOffset = writeNode(offset, ptrs, level(block));
            res.firstKey = ptrs.get(0).key;
        } else {
            int mid = ptrs.size() / 2;
            List<Ptr> left = new ArrayList<>(ptrs.subList(0, mid));
            List<Ptr> right = new ArrayList<>(ptrs.subList(mid, ptrs.size()));
            res.splitOffset = writeNewNode(right, level(block));
            res.newOffset = writeNode(offset, left, level(block));
            res.firstKey = left.get(0).key;
            res.splitKey = right.get(0).key;
        }
        return res;
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /** Remove {@code key} if present. Returns true if an entry was removed. */
    public boolean remove(K key) {
        return removeSerialized(serializeKey(key));
    }

    private boolean removeSerialized(byte[] key) {
        checkKey(key);
        if (rootOffset == 0)
            return false;

        prepareSpaceForWrite();

        Result res = removeFrom(rootOffset, key);
        if (res == null)
            return false;
        rootOffset = res.newOffset;

        // Collapse a root node that is left with a single child.
        while (rootOffset != 0) {
            byte[] block = readBlock(rootOffset);
            if (level(block) == 0)
                break;
            List<Ptr> ptrs = parseNode(block, keySize);
            if (ptrs.size() != 1)
                break;
            long child = ptrs.get(0).blockptr;
            rootOffset = child;
        }
        return true;
    }

    /**
     * Remove and return the first entry if present. Returns an empty Optional if
     * the tree is empty.
     */
    public Optional<Entry<K, V>> removeFirst() {
        Iterator<Entry<K, V>> iterator = iterator();
        if (!iterator.hasNext())
            return Optional.empty();
        Entry<K, V> first = iterator.next();
        remove(first.key);
        return Optional.of(first);
    }

    /**
     * Removes {@code key} from the subtree rooted at {@code offset} using
     * copy-on-write. Returns {@code null} if the key is absent; otherwise returns
     * the replacement subtree root and its first key.
     */
    private Result removeFrom(long offset, byte[] key) {
        byte[] block = readBlock(offset);
        Result res = new Result();
        if (level(block) == 0) {
            List<SerializedEntry> entries = parseLeaf(block, keySize);
            int idx = searchEntries(entries, key);
            if (idx < 0)
                return null;
            entries.remove(idx);
            if (entries.isEmpty()) {
                res.newOffset = 0;
                return res;
            }
            res.newOffset = writeLeaf(offset, entries);
            res.firstKey = entries.get(0).key;
            return res;
        }

        List<Ptr> ptrs = parseNode(block, keySize);
        int child = findChild(ptrs, key);
        if (!containsInSubtree(ptrs.get(child).blockptr, key))
            return null;
        Result childRes = removeFrom(ptrs.get(child).blockptr, key);
        if (childRes == null)
            return null;
        if (childRes.newOffset == 0) {
            ptrs.remove(child);
        } else {
            ptrs.get(child).blockptr = childRes.newOffset;
            ptrs.get(child).key = childRes.firstKey;
        }
        if (ptrs.isEmpty()) {
            res.newOffset = 0;
            return res;
        }
        res.newOffset = writeNode(offset, ptrs, level(block));
        res.firstKey = ptrs.get(0).key;
        return res;
    }

    // ------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------

    public void forEach(BiConsumer<K, V> consumer) {
        if (rootOffset != 0)
            walk(rootOffset, consumer);
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        if (rootOffset == 0)
            return Collections.emptyIterator();

        List<Frame> path = new ArrayList<>();
        long offset = rootOffset;
        byte[] block = readBlock(offset);
        while (level(block) != 0) {
            List<Ptr> ptrs = parseNode(block, keySize);
            path.add(new Frame(ptrs, 0));
            offset = ptrs.get(0).blockptr;
            block = readBlock(offset);
        }

        return new BTreeIterator(new Position(path, parseLeaf(block, keySize), 0));
    }

    /**
     * Invoke {@code consumer} for the offset of every block (nodes and leaves)
     * that makes up the given tree.
     */
    public void collectBlocks(java.util.function.LongConsumer consumer) {
        if (rootOffset != 0)
            collectBlocks(rootOffset, consumer);
    }

    private void collectBlocks(long offset, java.util.function.LongConsumer consumer) {
        consumer.accept(offset);
        byte[] block = readBlock(offset);
        if (level(block) == 0)
            return;
        for (Ptr p : parseNode(block, keySize))
            collectBlocks(p.blockptr, consumer);
    }

    private void walk(long offset, BiConsumer<K, V> consumer) {
        byte[] block = readBlock(offset);
        if (level(block) == 0) {
            List<SerializedEntry> entries = parseLeaf(block, keySize);
            for (SerializedEntry entry : entries) {
                Entry<K, V> decoded = deserializeEntry(entry);
                consumer.accept(decoded.key, decoded.value);
            }
            return;
        }
        for (Ptr p : parseNode(block, keySize))
            walk(p.blockptr, consumer);
    }

    /**
     * Returns an iterator whose first entry has the greatest key less than or
     * equal to {@code key}, followed by all entries with greater keys in
     * ascending unsigned lexicographic order. If no
     * stored key is less than or equal to {@code key}, the iterator starts at the
     * lowest key.
     *
     * @param key key to find the floor of; must serialize to {@link #keySize} bytes
     * @return the floor entry and all subsequent entries, or an empty iterator
     */
    public Iterator<Entry<K, V>> floorOrLowestIterator(K key) {
        return floorIterator(serializeKey(key), true);
    }

    /**
     * Returns an iterator whose first entry has the greatest key less than or
     * equal to {@code key}, followed by all entries with greater keys in
     * ascending unsigned lexicographic order. Returns an empty iterator if no
     * stored key is less than or equal to {@code key}.
     *
     * @param key key to find the floor of; must serialize to {@link #keySize} bytes
     * @return the floor entry and all subsequent entries, or an empty iterator
     */
    public Iterator<Entry<K, V>> floorIterator(K key) {
        return floorIterator(serializeKey(key), false);
    }

    private Iterator<Entry<K, V>> floorIterator(byte[] key, boolean returnLowestIfNotFound) {
        checkKey(key);
        if (rootOffset == 0)
            return Collections.emptyIterator();

        List<Frame> path = new ArrayList<>();
        long offset = rootOffset;
        byte[] block = readBlock(offset);
        while (level(block) != 0) {
            List<Ptr> ptrs = parseNode(block, keySize);
            int childIndex = findChild(ptrs, key);
            path.add(new Frame(ptrs, childIndex));
            offset = ptrs.get(childIndex).blockptr;
            block = readBlock(offset);
        }

        List<SerializedEntry> entries = parseLeaf(block, keySize);
        int search = searchEntries(entries, key);
        int entryIndex = search >= 0 ? search : -search - 2;
        if (entryIndex < 0)
            if (returnLowestIfNotFound) {
                entryIndex = 0;
            } else {
                return Collections.emptyIterator();
            }
        return new BTreeIterator(new Position(path, entries, entryIndex));
    }

    private static class Frame {
        final List<Ptr> ptrs;
        int childIndex;

        Frame(List<Ptr> ptrs, int childIndex) {
            this.ptrs = ptrs;
            this.childIndex = childIndex;
        }
    }

    private static class Position {
        final List<Frame> path;
        final List<SerializedEntry> entries;
        final int entryIndex;

        Position(List<Frame> path, List<SerializedEntry> entries, int entryIndex) {
            this.path = path;
            this.entries = entries;
            this.entryIndex = entryIndex;
        }
    }

    private class BTreeIterator implements Iterator<Entry<K, V>> {
        private Position position;

        BTreeIterator(Position position) {
            this.position = position;
        }

        @Override
        public boolean hasNext() {
            return position != null;
        }

        @Override
        public Entry<K, V> next() {
            if (position == null)
                throw new NoSuchElementException();
            Entry<K, V> result = deserializeEntry(position.entries.get(position.entryIndex));
            position = nextPosition(position);
            return result;
        }
    }

    private Position nextPosition(Position position) {
        if (position.entryIndex + 1 < position.entries.size())
            return new Position(position.path, position.entries, position.entryIndex + 1);

        List<Frame> path = position.path;
        for (int i = path.size() - 1; i >= 0; i--) {
            Frame frame = path.get(i);
            if (frame.childIndex + 1 >= frame.ptrs.size())
                continue;

            frame.childIndex++;
            while (path.size() > i + 1)
                path.remove(path.size() - 1);
            long offset = frame.ptrs.get(frame.childIndex).blockptr;
            byte[] block = readBlock(offset);
            while (level(block) != 0) {
                List<Ptr> ptrs = parseNode(block, keySize);
                path.add(new Frame(ptrs, 0));
                offset = ptrs.get(0).blockptr;
                block = readBlock(offset);
            }
            return new Position(path, parseLeaf(block, keySize), 0);
        }

        return null;
    }

    // ------------------------------------------------------------------
    // Block layout
    // ------------------------------------------------------------------

    private static void writeHeader(ByteBuffer bb, int nritems, int level) {
        bb.putInt(nritems);
        bb.put((byte) level);
    }

    private static int nritems(byte[] block) {
        return wrap(block).getInt(0);
    }

    private static int level(byte[] block) {
        return block[4] & 0xff;
    }

    private boolean fitsLeaf(List<SerializedEntry> entries) {
        long used = (long) entries.size() * (keySize + 8);
        for (SerializedEntry e : entries)
            used += e.value.length;
        return used <= storage.blockSize() - HEADER_SIZE;
    }

    private boolean fitsNode(List<Ptr> ptrs) {
        return (long) ptrs.size() * (keySize + NODE_PTR_EXTRA) <= storage.blockSize() - HEADER_SIZE;
    }

    private int chooseLeafSplit(List<SerializedEntry> entries) {
        int capacity = storage.blockSize() - HEADER_SIZE;
        int n = entries.size();
        // Find the smallest index so that the right half still fits; then check the
        // left half fits too, otherwise increase.
        long[] suffix = new long[n + 1];
        for (int i = n - 1; i >= 0; i--)
            suffix[i] = suffix[i + 1] + entries.get(i).value.length + keySize + 8;
        int k = 1;
        while (k < n && suffix[k] > capacity)
            k++;
        long leftUsed = suffix[0] - suffix[k];
        while (leftUsed > capacity && k < n - 1) {
            k++;
            leftUsed = suffix[0] - suffix[k];
        }
        return k;
    }

    private byte[] serializeLeaf(List<SerializedEntry> entries) {
        int blockSize = storage.blockSize();
        byte[] buf = new byte[blockSize];
        ByteBuffer bb = wrap(buf);
        writeHeader(bb, entries.size(), 0);
        int itemSize = keySize + 8;
        int dataEnd = blockSize;
        for (int i = entries.size() - 1; i >= 0; i--) {
            SerializedEntry e = entries.get(i);
            dataEnd -= e.value.length;
            System.arraycopy(e.value, 0, buf, dataEnd, e.value.length);
            int itemOff = HEADER_SIZE + i * itemSize;
            System.arraycopy(e.key, 0, buf, itemOff, keySize);
            bb.putInt(itemOff + keySize, dataEnd);
            bb.putInt(itemOff + keySize + 4, e.value.length);
        }
        return buf;
    }

    private byte[] serializeNode(List<Ptr> ptrs, int level) {
        int blockSize = storage.blockSize();
        byte[] buf = new byte[blockSize];
        ByteBuffer bb = wrap(buf);
        writeHeader(bb, ptrs.size(), level);
        int ptrSize = keySize + NODE_PTR_EXTRA;
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr p = ptrs.get(i);
            int off = HEADER_SIZE + i * ptrSize;
            System.arraycopy(p.key, 0, buf, off, keySize);
            bb.putLong(off + keySize, p.blockptr);
        }
        return buf;
    }

    private long writeNewLeaf(List<SerializedEntry> entries) {
        return writeNewBlock(serializeLeaf(entries));
    }

    private long writeNewNode(List<Ptr> ptrs, int level) {
        return writeNewBlock(serializeNode(ptrs, level));
    }

    private long writeLeaf(long offset, List<SerializedEntry> entries) {
        return replaceBlock(offset, serializeLeaf(entries));
    }

    private long writeNode(long offset, List<Ptr> ptrs, int level) {
        return replaceBlock(offset, serializeNode(ptrs, level));
    }

    private long replaceBlock(long offset, byte[] data) {
        if (storage.isBlockModified(offset)) {
            writeBlock(offset, data);
            return offset;
        }
        return writeNewBlock(data);
    }

    private byte[] readBlock(long offset) {
        return storage.readBlock(offset, blockType).data;
    }

    private void writeBlock(long offset, byte[] data) {
        storage.writeBlock(offset, new NodeBlock(data, blockType));
    }

    private long writeNewBlock(byte[] data) {
        return storage.writeNewBlock(new NodeBlock(data, blockType));
    }

    private boolean containsInSubtree(long offset, byte[] key) {
        byte[] block = readBlock(offset);
        while (level(block) != 0) {
            List<Ptr> pointers = parseNode(block, keySize);
            block = readBlock(pointers.get(findChild(pointers, key)).blockptr);
        }
        return leafSearch(block, keySize, key) >= 0;
    }

    // ------------------------------------------------------------------
    // Parsing / search
    // ------------------------------------------------------------------

    private static class Ptr {
        byte[] key;
        long blockptr;

        Ptr(byte[] key, long blockptr) {
            this.key = key;
            this.blockptr = blockptr;
        }
    }

    private List<SerializedEntry> parseLeaf(byte[] block, int keySize) {
        int n = nritems(block);
        int itemSize = keySize + 8;
        List<SerializedEntry> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int off = HEADER_SIZE + i * itemSize;
            byte[] key = new byte[keySize];
            System.arraycopy(block, off, key, 0, keySize);
            ByteBuffer bb = wrap(block);
            int dataOff = bb.getInt(off + keySize);
            int size = bb.getInt(off + keySize + 4);
            byte[] value = new byte[size];
            System.arraycopy(block, dataOff, value, 0, size);
            result.add(new SerializedEntry(key, value));
        }
        return result;
    }

    private byte[] leafValue(byte[] block, int keySize, int idx) {
        int off = HEADER_SIZE + idx * (keySize + 8);
        ByteBuffer bb = wrap(block);
        int dataOff = bb.getInt(off + keySize);
        int size = bb.getInt(off + keySize + 4);
        byte[] value = new byte[size];
        System.arraycopy(block, dataOff, value, 0, size);
        return value;
    }

    private List<Ptr> parseNode(byte[] block, int keySize) {
        int n = nritems(block);
        int ptrSize = keySize + NODE_PTR_EXTRA;
        List<Ptr> result = new ArrayList<>(n);
        ByteBuffer bb = wrap(block);
        for (int i = 0; i < n; i++) {
            int off = HEADER_SIZE + i * ptrSize;
            byte[] key = new byte[keySize];
            System.arraycopy(block, off, key, 0, keySize);
            long blockptr = bb.getLong(off + keySize);
            result.add(new Ptr(key, blockptr));
        }
        return result;
    }

    /**
     * Returns the index of {@code key} if present, otherwise {@code -(insertion
     * point) - 1}.
     */
    private int leafSearch(byte[] block, int keySize, byte[] key) {
        int n = nritems(block);
        int itemSize = keySize + 8;
        int low = 0;
        int high = n - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = compare(block, HEADER_SIZE + mid * itemSize, key, 0, keySize);
            if (c < 0)
                low = mid + 1;
            else if (c > 0)
                high = mid - 1;
            else
                return mid;
        }
        return -(low + 1);
    }

    /**
     * Searches the key-sorted entries for {@code key} using unsigned
     * lexicographic ordering. Returns its index if found, or
     * {@code -(insertion point) - 1} if absent.
     */
    private int searchEntries(List<SerializedEntry> entries, byte[] key) {
        int low = 0;
        int high = entries.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = compare(entries.get(mid).key, key);
            if (c < 0)
                low = mid + 1;
            else if (c > 0)
                high = mid - 1;
            else
                return mid;
        }
        return -(low + 1);
    }

    /**
     * Returns the child whose lower-bound key is the greatest one not greater
     * than {@code key}, or the first child if {@code key} precedes all bounds.
     * 
     * @param ptrs the list of child pointers
     * @param key  the key to search for
     * @return the index of the child to follow
     */
    private int findChild(List<Ptr> ptrs, byte[] key) {
        int low = 0;
        int high = ptrs.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = compare(ptrs.get(mid).key, key);
            if (c <= 0) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static int compare(byte[] a, int aOff, byte[] b, int bOff, int len) {
        for (int i = 0; i < len; i++) {
            int x = (a[aOff + i] & 0xff);
            int y = (b[bOff + i] & 0xff);
            if (x != y)
                return x - y;
        }
        return 0;
    }

    public static int compare(byte[] a, byte[] b) {
        return compare(a, 0, b, 0, Math.min(a.length, b.length));
    }

    private void checkKey(byte[] key) {
        if (key.length != keySize)
            throw new IllegalArgumentException(
                    "key must be " + keySize + " bytes, got " + key.length);
    }

    private byte[] serializeKey(K key) {
        byte[] result = bytes(keySerializer.serialize(key));
        checkKey(result);
        return result;
    }

    private Entry<K, V> deserializeEntry(SerializedEntry entry) {
        return new Entry<>(keySerializer.deserialize(ByteBuffer.wrap(entry.key)),
                entryType.deserialize(ByteBuffer.wrap(entry.value)).value);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static ByteBuffer wrap(byte[] buf) {
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    }

}
