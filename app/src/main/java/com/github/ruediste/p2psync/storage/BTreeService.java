package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Service for managing B-Trees within the data storage system. The tree
 * follows the B-Tree implementation of BTRFS.
 *
 * <p>
 * A tree is a copy-on-write B+ tree with variable sized values. Keys have a
 * fixed size per tree. Internal nodes only store keys and child pointers,
 * leaves
 * store the actual key/value pairs. All keys of a node are sorted in ascending
 * (unsigned lexicographic) order and each child pointer holds the smallest key
 * reachable in the child's subtree.
 *
 * <p>
 * Modifications never overwrite existing tree blocks. Instead the affected path
 * is copied, old blocks are released and new blocks are allocated through the
 * {@link DataStorage}. A {@link Tree} therefore references the root of a
 * particular version of the tree; the caller is responsible for persisting the
 * root offset.
 */
public class BTreeService {

    private static final int HEADER_SIZE = 5; // nritems (4) + level (1)
    private static final int NODE_PTR_EXTRA = 8; // blockptr (8)

    private final DataStorage dataStorage;

    public BTreeService(DataStorage dataStorage) {
        this.dataStorage = dataStorage;
    }

    /** Root node type discriminator: level 0 is a leaf, level > 0 a node. */
    public static class Tree {
        public final DataStorage.BlockArea kind;
        public final int keySize;
        public long rootOffset;

        public Tree(DataStorage.BlockArea kind, int keySize) {
            this.kind = kind;
            this.keySize = keySize;
        }
    }

    public static class Entry {
        public final byte[] key;
        public final byte[] value;

        public Entry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }

    public Tree createTree(DataStorage.BlockArea kind, int keySize) {
        Tree tree = new Tree(kind, keySize);
        tree.rootOffset = 0;
        return tree;
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** Returns the value associated with {@code key}, or {@code null}. */
    public byte[] get(Tree tree, byte[] key) {
        checkKey(tree, key);
        long off = tree.rootOffset;
        while (off != 0) {
            byte[] block = dataStorage.readBlock(off, tree.kind);
            if (level(block) == 0) {
                int idx = leafSearch(block, tree.keySize, key);
                if (idx < 0)
                    return null;
                return leafValue(block, tree.keySize, idx);
            }
            List<Ptr> ptrs = parseNode(block, tree.keySize);
            off = ptrs.get(findChild(ptrs, key)).blockptr;
        }
        return null;
    }

    public boolean contains(Tree tree, byte[] key) {
        return get(tree, key) != null;
    }

    // ------------------------------------------------------------------
    // Insert / update
    // ------------------------------------------------------------------

    /**
     * Insert or replace the value for {@code key}. The tree's root offset is
     * updated in place.
     */
    public void put(Tree tree, byte[] key, byte[] value) {
        checkKey(tree, key);
        if (tree.rootOffset == 0) {
            List<Entry> entries = new ArrayList<>();
            entries.add(new Entry(key.clone(), value.clone()));
            tree.rootOffset = writeNewLeaf(tree, entries);
            return;
        }
        Result res = insertInto(tree, tree.rootOffset, key, value);
        if (res.splitOffset != 0) {
            List<Ptr> ptrs = new ArrayList<>();
            ptrs.add(new Ptr(res.firstKey, res.newOffset));
            ptrs.add(new Ptr(res.splitKey, res.splitOffset));
            tree.rootOffset = writeNewNode(tree, ptrs, 1);
        } else {
            tree.rootOffset = res.newOffset;
        }
    }

    private static class Result {
        long newOffset;
        byte[] firstKey;
        long splitOffset;
        byte[] splitKey;
    }

    private Result insertInto(Tree tree, long offset, byte[] key, byte[] value) {
        byte[] block = dataStorage.readBlock(offset, tree.kind);
        Result res = new Result();
        if (level(block) == 0) {
            List<Entry> entries = parseLeaf(block, tree.keySize);
            int idx = searchEntries(entries, key);
            if (idx >= 0) {
                entries.set(idx, new Entry(entries.get(idx).key, value.clone()));
            } else {
                int ins = -(idx + 1);
                entries.add(ins, new Entry(key.clone(), value.clone()));
            }

            if (fitsLeaf(tree, entries)) {
                res.newOffset = writeNewLeaf(tree, entries);
                res.firstKey = entries.get(0).key;
            } else {
                int k = chooseLeafSplit(tree, entries);
                List<Entry> left = new ArrayList<>(entries.subList(0, k));
                List<Entry> right = new ArrayList<>(entries.subList(k, entries.size()));
                res.newOffset = writeNewLeaf(tree, left);
                res.splitOffset = writeNewLeaf(tree, right);
                res.firstKey = left.get(0).key;
                res.splitKey = right.get(0).key;
            }
            dataStorage.freeBlock(offset, tree.kind);
            return res;
        }

        List<Ptr> ptrs = parseNode(block, tree.keySize);
        int child = findChild(ptrs, key);
        Result childRes = insertInto(tree, ptrs.get(child).blockptr, key, value);
        ptrs.get(child).blockptr = childRes.newOffset;
        ptrs.get(child).key = childRes.firstKey;
        if (childRes.splitOffset != 0) {
            ptrs.add(child + 1,
                    new Ptr(childRes.splitKey, childRes.splitOffset));
        }

        if (fitsNode(tree, ptrs)) {
            res.newOffset = writeNewNode(tree, ptrs, level(block));
            res.firstKey = ptrs.get(0).key;
        } else {
            int mid = ptrs.size() / 2;
            List<Ptr> left = new ArrayList<>(ptrs.subList(0, mid));
            List<Ptr> right = new ArrayList<>(ptrs.subList(mid, ptrs.size()));
            res.newOffset = writeNewNode(tree, left, level(block));
            res.splitOffset = writeNewNode(tree, right, level(block));
            res.firstKey = left.get(0).key;
            res.splitKey = right.get(0).key;
        }
        dataStorage.freeBlock(offset, tree.kind);
        return res;
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /** Remove {@code key} if present. Returns true if an entry was removed. */
    public boolean remove(Tree tree, byte[] key) {
        checkKey(tree, key);
        if (tree.rootOffset == 0)
            return false;
        Result res = removeFrom(tree, tree.rootOffset, key);
        if (res == null)
            return false;
        tree.rootOffset = res.newOffset;

        // Collapse a root node that is left with a single child.
        while (tree.rootOffset != 0) {
            byte[] block = dataStorage.readBlock(tree.rootOffset, tree.kind);
            if (level(block) == 0)
                break;
            List<Ptr> ptrs = parseNode(block, tree.keySize);
            if (ptrs.size() != 1)
                break;
            long child = ptrs.get(0).blockptr;
            dataStorage.freeBlock(tree.rootOffset, tree.kind);
            tree.rootOffset = child;
        }
        return true;
    }

    private Result removeFrom(Tree tree, long offset, byte[] key) {
        byte[] block = dataStorage.readBlock(offset, tree.kind);
        Result res = new Result();
        if (level(block) == 0) {
            List<Entry> entries = parseLeaf(block, tree.keySize);
            int idx = searchEntries(entries, key);
            if (idx < 0)
                return null;
            entries.remove(idx);
            if (entries.isEmpty()) {
                dataStorage.freeBlock(offset, tree.kind);
                res.newOffset = 0;
                return res;
            }
            res.newOffset = writeNewLeaf(tree, entries);
            res.firstKey = entries.get(0).key;
            dataStorage.freeBlock(offset, tree.kind);
            return res;
        }

        List<Ptr> ptrs = parseNode(block, tree.keySize);
        int child = findChild(ptrs, key);
        Result childRes = removeFrom(tree, ptrs.get(child).blockptr, key);
        if (childRes == null)
            return null;
        if (childRes.newOffset == 0) {
            ptrs.remove(child);
        } else {
            ptrs.get(child).blockptr = childRes.newOffset;
            ptrs.get(child).key = childRes.firstKey;
        }
        if (ptrs.isEmpty()) {
            dataStorage.freeBlock(offset, tree.kind);
            res.newOffset = 0;
            return res;
        }
        res.newOffset = writeNewNode(tree, ptrs, level(block));
        res.firstKey = ptrs.get(0).key;
        dataStorage.freeBlock(offset, tree.kind);
        return res;
    }

    // ------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------

    public void forEach(Tree tree, BiConsumer<byte[], byte[]> consumer) {
        if (tree.rootOffset != 0)
            walk(tree, tree.rootOffset, consumer);
    }

    /**
     * Invoke {@code consumer} for the offset of every block (nodes and leaves)
     * that makes up the given tree.
     */
    public void collectBlocks(Tree tree, java.util.function.LongConsumer consumer) {
        if (tree.rootOffset != 0)
            collectBlocks(tree, tree.rootOffset, consumer);
    }

    private void collectBlocks(Tree tree, long offset, java.util.function.LongConsumer consumer) {
        consumer.accept(offset);
        byte[] block = dataStorage.readBlock(offset, tree.kind);
        if (level(block) == 0)
            return;
        for (Ptr p : parseNode(block, tree.keySize))
            collectBlocks(tree, p.blockptr, consumer);
    }

    private void walk(Tree tree, long offset, BiConsumer<byte[], byte[]> consumer) {
        byte[] block = dataStorage.readBlock(offset, tree.kind);
        if (level(block) == 0) {
            List<Entry> entries = parseLeaf(block, tree.keySize);
            for (Entry e : entries)
                consumer.accept(e.key, e.value);
            return;
        }
        for (Ptr p : parseNode(block, tree.keySize))
            walk(tree, p.blockptr, consumer);
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

    private boolean fitsLeaf(Tree tree, List<Entry> entries) {
        long used = (long) entries.size() * (tree.keySize + 8);
        for (Entry e : entries)
            used += e.value.length;
        return used <= dataStorage.blockSize(tree.kind) - HEADER_SIZE;
    }

    private boolean fitsNode(Tree tree, List<Ptr> ptrs) {
        return (long) ptrs.size() * (tree.keySize + NODE_PTR_EXTRA) <= dataStorage.blockSize(tree.kind) - HEADER_SIZE;
    }

    private int chooseLeafSplit(Tree tree, List<Entry> entries) {
        int capacity = dataStorage.blockSize(tree.kind) - HEADER_SIZE;
        int n = entries.size();
        // Find the smallest index so that the right half still fits; then check the
        // left half fits too, otherwise increase.
        long[] suffix = new long[n + 1];
        for (int i = n - 1; i >= 0; i--)
            suffix[i] = suffix[i + 1] + entries.get(i).value.length + tree.keySize + 8;
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

    private byte[] serializeLeaf(Tree tree, List<Entry> entries) {
        int blockSize = dataStorage.blockSize(tree.kind);
        byte[] buf = new byte[blockSize];
        ByteBuffer bb = wrap(buf);
        writeHeader(bb, entries.size(), 0);
        int itemSize = tree.keySize + 8;
        int dataEnd = blockSize;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            dataEnd -= e.value.length;
            System.arraycopy(e.value, 0, buf, dataEnd, e.value.length);
            int itemOff = HEADER_SIZE + i * itemSize;
            System.arraycopy(e.key, 0, buf, itemOff, tree.keySize);
            bb.putInt(itemOff + tree.keySize, dataEnd);
            bb.putInt(itemOff + tree.keySize + 4, e.value.length);
        }
        return buf;
    }

    private byte[] serializeNode(Tree tree, List<Ptr> ptrs, int level) {
        int blockSize = dataStorage.blockSize(tree.kind);
        byte[] buf = new byte[blockSize];
        ByteBuffer bb = wrap(buf);
        writeHeader(bb, ptrs.size(), level);
        int ptrSize = tree.keySize + NODE_PTR_EXTRA;
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr p = ptrs.get(i);
            int off = HEADER_SIZE + i * ptrSize;
            System.arraycopy(p.key, 0, buf, off, tree.keySize);
            bb.putLong(off + tree.keySize, p.blockptr);
        }
        return buf;
    }

    private long writeNewLeaf(Tree tree, List<Entry> entries) {
        long off = dataStorage.allocateBlock(tree.kind);
        dataStorage.writeBlock(off, tree.kind, serializeLeaf(tree, entries));
        return off;
    }

    private long writeNewNode(Tree tree, List<Ptr> ptrs, int level) {
        long off = dataStorage.allocateBlock(tree.kind);
        dataStorage.writeBlock(off, tree.kind, serializeNode(tree, ptrs, level));
        return off;
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

    private List<Entry> parseLeaf(byte[] block, int keySize) {
        int n = nritems(block);
        int itemSize = keySize + 8;
        List<Entry> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int off = HEADER_SIZE + i * itemSize;
            byte[] key = new byte[keySize];
            System.arraycopy(block, off, key, 0, keySize);
            ByteBuffer bb = wrap(block);
            int dataOff = bb.getInt(off + keySize);
            int size = bb.getInt(off + keySize + 4);
            byte[] value = new byte[size];
            System.arraycopy(block, dataOff, value, 0, size);
            result.add(new Entry(key, value));
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

    private int searchEntries(List<Entry> entries, byte[] key) {
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

    private void checkKey(Tree tree, byte[] key) {
        if (key.length != tree.keySize)
            throw new IllegalArgumentException(
                    "key must be " + tree.keySize + " bytes, got " + key.length);
    }

    private static ByteBuffer wrap(byte[] buf) {
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    }
}
