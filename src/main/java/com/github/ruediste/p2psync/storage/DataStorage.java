package com.github.ruediste.p2psync.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A BTRFS-like single-file Copy-on-Write (CoW) data storage system.
 * 
 * Manages block allocation, reference counting, and free space trees
 * across three distinct block kinds (SYSTEM, META, DATA).
 * 
 * ### **Implementation Mechanics**
 * 
 * 1. **Circular Dependency Resolution (`allocatorTreeMode`):**
 * When writing structural allocator metadata during a commit (updating the
 * reference count and free space trees), `allocatorTreeMode` is toggled `true`.
 * Allocations in this mode bypass generating new reference count entries,
 * preventing infinite allocation loops. These structural blocks are recovered
 * during `open()` by walking the B-trees.
 * 2. **Safe Free Space Updating:**
 * Allocating raw blocks modifies only the in-memory authoritative `freeSpace`
 * maps during a transaction, preventing stack overflow recursion. The
 * persistent free space trees are populated/updated inside the commit phase
 * under `allocatorTreeMode`.
 * 3. **Deferred Block Reuse (`reserved` Set):**
 * Blocks freed or replaced in the current transaction are moved to a `reserved`
 * set. They are held back from re-allocation until the `SuperBlock` root offset
 * is updated on disk, guaranteeing crash consistency.
 * 4. **Atomic Commits:**
 * The transaction is finalized only when `writeSuperBlock()` writes the new
 * `rootBlock` pointer to disk.
 * 
 * ---
 */
public class DataStorage implements Closeable {

    public static final byte[] MAGIC = new byte[] {
            (byte) 0xd6, (byte) 0x1b, (byte) 0x52, (byte) 0xd5, (byte) 0x6d,
            (byte) 0xc6, (byte) 0x7e, (byte) 0x4f, (byte) 0xfd, (byte) 0x40,
            (byte) 0xc6, (byte) 0xd8, (byte) 0xf5, (byte) 0xee, (byte) 0x03, (byte) 0xdb
    };

    public static final long FORMAT_VERSION = 1;
    public static final int SUPERBLOCK_SIZE = 4096;
    private static final int BLOCK_OFFSET_SIZE = 8; // uint64_t

    public static class SuperBlock {
        byte[] magic = new byte[MAGIC.length];
        long version;
        long rootBlock;
        int dataBlockSize;
        int metaBlockSize;
        int systemBlockSize;
    }

    public static class RootBlock {
        public long dataFreeSpaceTree;
        public long metaFreeSpaceTree;
        public long systemFreeSpaceTree;

        /**
         * Reference to the list of blocks allocated in the last transaction and freed
         * in the current transaction. They will
         * be added to the free space tree at the start of the next transaction.
         */
        public long freedBlockList;

        /**
         * Reference to the list of meta blocks currently reserved. They are used for to
         * allocate blocks for the metaFreeSpaceTree, in order to avoid infinite
         * recursion.
         */
        public long metaReservedBlockList;

        public long systemKvTree;
        public long refCountTree;
        public long infoBlock;

        public long nextGuidCounter;
        public long guidMappingTree;
        public long distributedClock;
        public long operationStagingTree;
        public long operationRetrievalTree;
        public long fileSystemTree;
        public long operationTree;
        public long snapshotTree;
    }

    public enum Area {
        SYSTEM,
        META,
        DATA
    }

    public static class InfoBlock {
        boolean dirty;
        private int dataBlockSize;
        private int metaBlockSize;
        private int systemBlockSize;

        private int inlineDataThreshold;
        private int maxExtentDataBlockSize;

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            this.dirty = false;
        }

        public int getDataBlockSize() {
            return dataBlockSize;
        }

        public void setDataBlockSize(int dataBlockSize) {
            this.dirty |= this.dataBlockSize != dataBlockSize;
            this.dataBlockSize = dataBlockSize;
        }

        public int getMetaBlockSize() {
            return metaBlockSize;
        }

        public void setMetaBlockSize(int metaBlockSize) {
            this.dirty |= this.metaBlockSize != metaBlockSize;
            this.metaBlockSize = metaBlockSize;
        }

        public int getSystemBlockSize() {
            return systemBlockSize;
        }

        public void setSystemBlockSize(int systemBlockSize) {
            this.dirty |= this.systemBlockSize != systemBlockSize;
            this.systemBlockSize = systemBlockSize;
        }

        public int getInlineDataThreshold() {
            return inlineDataThreshold;
        }

        public void setInlineDataThreshold(int inlineDataThreshold) {
            this.dirty |= this.inlineDataThreshold != inlineDataThreshold;
            this.inlineDataThreshold = inlineDataThreshold;
        }

        public int getMaxExtentDataBlockSize() {
            return maxExtentDataBlockSize;
        }

        public void setMaxExtentDataBlockSize(int maxExtentDataBlockSize) {
            this.dirty |= this.maxExtentDataBlockSize != maxExtentDataBlockSize;
            this.maxExtentDataBlockSize = maxExtentDataBlockSize;
        }
    }

    public SuperBlock superBlock;
    public RootBlock rootBlock;
    public InfoBlock infoBlock;

    private RandomAccessFile file;

    // Persistent B-trees for free space and refcounts
    private final EnumMap<Area, BTree> freeSpaceTrees = new EnumMap<>(Area.class);

    /**
     * Persistent B-tree for reference counts of blocks.
     * Key: block offset (uint64)
     * Value: reference count (uint64)
     */
    private BTree refCountTree;

    // In-memory modified blocks (offset -> data). Direct writes store null value.
    private final HashMap<Long, byte[]> modifiedBlocks = new HashMap<>();

    private static class RefCountAdjustment {
        int adjustment;
        Optional<Runnable> onDelete = Optional.empty();
        Area area;

        private RefCountAdjustment(int adjustment, Area area, Optional<Runnable> onDelete) {
            this.adjustment = adjustment;
            this.area = area;
            this.onDelete = onDelete;
        }
    }

    private HashMap<Long, RefCountAdjustment> refCountAdjustments = new HashMap<>();

    public void incrementRefCount(long blockOffset, Area area) {
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> new RefCountAdjustment(0, area, Optional.empty()));
        adjustment.adjustment++;
        // onDelete is only relevant when the adjustment is negative
        if (adjustment.adjustment == 0)
            adjustment.onDelete = Optional.empty();
    }

    public void decrementRefCount(long blockOffset, Area area, Consumer<Long> onDelete) {
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> new RefCountAdjustment(0, area, Optional.empty()));
        adjustment.adjustment--;
        if (adjustment.adjustment == 0) {
            // check if this is a block that was newly allocated in this transaction. If so,
            // remove it.
            if (modifiedBlocks.remove(blockOffset) != null) {
                refCountAdjustments.remove(blockOffset);

                // TODO: add block back to the free tree

            }

        } else if (adjustment.adjustment == -1)
            adjustment.onDelete = Optional.ofNullable(onDelete).map(c -> () -> c.accept(blockOffset));
    }

    // reserved blocks for meta area
    private final Set<Long> metaReservedBlocks = new HashSet<>();

    public void open(String path) {
        File f = new File(path);
        boolean exists = f.exists() && f.length() >= SUPERBLOCK_SIZE;
        if (!exists) {
            format(path);
        } else {
            try {
                file = new RandomAccessFile(path, "rw");
                readSuperBlock();
                readRootBlock();
                readInfoBlock();
                loadSystemTrees();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public void format(String path) {
        format(path, 512 * 1024, 2 * 1024 * 1024, 8 * 1024 * 1024, 4 * 1024, 16 * 1024, 4 * 1024);
    }

    public void format(String path, long systemBytes, long metaBytes, long dataBytes) {
        format(path, systemBytes, metaBytes, dataBytes, 4 * 1024, 16 * 1024, 4 * 1024);
    }

    public void format(String path, long systemBytes, long metaBytes, long dataBytes,
            int dataBlockSize, int metaBlockSize, int systemBlockSize) {
        try {
            new File(path).delete();
            file = new RandomAccessFile(path, "rw");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        superBlock = new SuperBlock();
        superBlock.magic = MAGIC;
        superBlock.version = FORMAT_VERSION;
        superBlock.dataBlockSize = dataBlockSize;
        superBlock.metaBlockSize = metaBlockSize;
        superBlock.systemBlockSize = systemBlockSize;
        superBlock.rootBlock = 0;

        infoBlock = new InfoBlock();
        infoBlock.dataBlockSize = dataBlockSize;
        infoBlock.metaBlockSize = metaBlockSize;
        infoBlock.systemBlockSize = systemBlockSize;
        infoBlock.inlineDataThreshold = 16384;
        infoBlock.maxExtentDataBlockSize = dataBlockSize;

        rootBlock = new RootBlock();

        // TODO: Initialize the storage layout and free space maps for system, meta, and
        // data areas.

        commit();
    }

    private void readSuperBlock() throws IOException {
        byte[] buf = new byte[SUPERBLOCK_SIZE];
        file.seek(0);
        file.readFully(buf);
        ByteBuffer bb = wrap(buf);
        superBlock = new SuperBlock();
        bb.get(superBlock.magic);
        if (!java.util.Arrays.equals(superBlock.magic, MAGIC)) {
            throw new IOException("Not a p2p-sync data file (bad magic)");
        }
        superBlock.version = bb.getLong();
        superBlock.rootBlock = bb.getLong();
        superBlock.dataBlockSize = bb.getInt();
        superBlock.metaBlockSize = bb.getInt();
        superBlock.systemBlockSize = bb.getInt();
    }

    private void writeSuperBlock() {
        byte[] buf = new byte[SUPERBLOCK_SIZE];
        ByteBuffer bb = wrap(buf);
        bb.put(superBlock.magic);
        bb.putLong(superBlock.version);
        bb.putLong(superBlock.rootBlock);
        bb.putInt(superBlock.dataBlockSize);
        bb.putInt(superBlock.metaBlockSize);
        bb.putInt(superBlock.systemBlockSize);
        try {
            file.seek(0);
            file.write(buf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readRootBlock() throws IOException {
        byte[] buf = readFromDisk(superBlock.rootBlock, superBlock.systemBlockSize);
        rootBlock = deserializeRootBlock(buf);
    }

    private RootBlock deserializeRootBlock(byte[] buf) {
        ByteBuffer bb = wrap(buf);
        RootBlock r = new RootBlock();
        r.dataFreeSpaceTree = bb.getLong();
        r.metaFreeSpaceTree = bb.getLong();
        r.systemFreeSpaceTree = bb.getLong();
        r.systemKvTree = bb.getLong();
        r.refCountTree = bb.getLong();
        r.infoBlock = bb.getLong();
        r.nextGuidCounter = bb.getLong();
        r.guidMappingTree = bb.getLong();
        r.distributedClock = bb.getLong();
        r.operationStagingTree = bb.getLong();
        r.operationRetrievalTree = bb.getLong();
        r.fileSystemTree = bb.getLong();
        r.operationTree = bb.getLong();
        r.snapshotTree = bb.getLong();
        return r;
    }

    private byte[] serializeRootBlock(RootBlock r) {
        byte[] buf = new byte[blockSize(Area.SYSTEM)];
        ByteBuffer bb = wrap(buf);
        bb.putLong(r.dataFreeSpaceTree);
        bb.putLong(r.metaFreeSpaceTree);
        bb.putLong(r.systemFreeSpaceTree);
        bb.putLong(r.systemKvTree);
        bb.putLong(r.refCountTree);
        bb.putLong(r.infoBlock);
        bb.putLong(r.nextGuidCounter);
        bb.putLong(r.guidMappingTree);
        bb.putLong(r.distributedClock);
        bb.putLong(r.operationStagingTree);
        bb.putLong(r.operationRetrievalTree);
        bb.putLong(r.fileSystemTree);
        bb.putLong(r.operationTree);
        bb.putLong(r.snapshotTree);
        return buf;
    }

    private void readInfoBlock() {
        byte[] buf = readFromDisk(rootBlock.infoBlock, superBlock.systemBlockSize);
        infoBlock = deserializeInfoBlock(buf);
    }

    private void writeInfoBlock() {
        decrementRefCount(rootBlock.infoBlock, Area.SYSTEM, null);
        rootBlock.infoBlock = writeNewBlock(Area.SYSTEM, serializeInfoBlock(infoBlock));
        infoBlock.dirty = false;
    }

    private InfoBlock deserializeInfoBlock(byte[] buf) {
        ByteBuffer bb = wrap(buf);
        var infoBlock = new InfoBlock();
        infoBlock.dataBlockSize = bb.getInt();
        infoBlock.metaBlockSize = bb.getInt();
        infoBlock.systemBlockSize = bb.getInt();
        infoBlock.inlineDataThreshold = bb.getInt();
        infoBlock.maxExtentDataBlockSize = bb.getInt();
        return infoBlock;
    }

    private byte[] serializeInfoBlock(InfoBlock infoBlock) {
        byte[] buf = new byte[blockSize(Area.SYSTEM)];
        ByteBuffer bb = wrap(buf);
        bb.putInt(infoBlock.dataBlockSize);
        bb.putInt(infoBlock.metaBlockSize);
        bb.putInt(infoBlock.systemBlockSize);
        bb.putInt(infoBlock.inlineDataThreshold);
        bb.putInt(infoBlock.maxExtentDataBlockSize);
        return buf;
    }

    private void loadSystemTrees() {
        for (Area area : Area.values()) {
            BTree tree = new BTree(this, Area.SYSTEM, BLOCK_OFFSET_SIZE, getFreeTreeRoot(area));
            freeSpaceTrees.put(area, tree);
        }
        refCountTree = new BTree(this, Area.SYSTEM, BLOCK_OFFSET_SIZE, rootBlock.refCountTree);
    }

    private long getFreeTreeRoot(Area area) {
        return switch (area) {
            case SYSTEM -> rootBlock.systemFreeSpaceTree;
            case META -> rootBlock.metaFreeSpaceTree;
            case DATA -> rootBlock.dataFreeSpaceTree;
        };
    }

    private void setFreeTreeRoot(Area area, long root) {
        switch (area) {
            case SYSTEM -> rootBlock.systemFreeSpaceTree = root;
            case META -> rootBlock.metaFreeSpaceTree = root;
            case DATA -> rootBlock.dataFreeSpaceTree = root;
        }
    }

    public int blockSize(Area kind) {
        return switch (kind) {
            case SYSTEM -> superBlock.systemBlockSize;
            case META -> superBlock.metaBlockSize;
            case DATA -> superBlock.dataBlockSize;
        };
    }

    public byte[] readBlock(long blockOffset, Area area) {
        byte[] mod = modifiedBlocks.get(blockOffset);
        if (mod != null) {
            return mod.clone();
        }

        // either there was a direct write (null value stored in map), or the block was
        // not modified
        return readFromDisk(blockOffset, blockSize(area));
    }

    public boolean isBlockModified(long blockOffset) {
        return modifiedBlocks.containsKey(blockOffset);
    }

    public void writeBlock(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been modified in this transaction");
        modifiedBlocks.put(blockOffset, data.clone());
    }

    public void writeBlockDirect(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been modified in this transaction");

        // clear existing cached value if present
        modifiedBlocks.put(blockOffset, null);

        writeToDisk(blockOffset, data);
    }

    public long writeNewBlock(Area area, byte[] data) {
        long off = allocateBlock(area);
        modifiedBlocks.put(off, data.clone());
        return off;
    }

    public long writeNewBlockDirect(Area area, byte[] data) {
        long off = allocateBlock(area);
        modifiedBlocks.put(off, null);
        writeToDisk(off, data);
        return off;
    }

    public long allocateBlock(Area area) {
        int bs = blockSize(area);
        var freeTree = freeSpaceTrees.get(area);

        var firstFree = freeTree.removeFirst();

        // If there is no more free space, extend the storage
        if (firstFree.isEmpty()) {
            extend(area);
            firstFree = freeTree.removeFirst();
        }

        var offset = toLong(firstFree.get().key);
        var size = toLong(firstFree.get().value);
        if (size > bs) {
            freeTree.put(u64(offset + bs), u64(size - bs));
        }
        return offset;
    }

    /**
     * Head of the freed block list. Format:
     * <ul>
     * <li>uint64_t: offset of the next freed block list block, 0 for end of
     * list</li>
     * <li>uint64_t: number of freed blocks in this block (could be a uint16_t, but
     * using uint64_t for alignment)</li>
     * <li>uint64_t[]: array of freed block offsets</li>
     * </ul>
     * 
     * The list is stored in system space. The position of the buffer is kept at the
     * next block to add.
     */
    private ByteBuffer freedBlocks = ByteBuffer.allocate(infoBlock.systemBlockSize); // TODO: proper initialization

    public void freeBlock(long blockOffset, Area kind) {
        if (modifiedBlocks.containsKey(blockOffset)) {
            // this block has been modified in this transaction, return it to the free tree
            // make sure to merge with adjacent free blocks if possible
            var tree = freeSpaceTrees.get(kind);
            var it = tree.floorOrLowestIterator(u64(blockOffset));
            long mergedOffset = blockOffset;
            long mergedSize = blockSize(kind);
            byte[] successorKey = null;
            for (int inspected = 0; inspected < 2 && it.hasNext(); inspected++) {
                var entry = it.next();
                var offset = toLong(entry.key);
                var size = toLong(entry.value);

                if (offset < blockOffset) {
                    if (offset + size == blockOffset) {
                        mergedOffset = offset;
                        mergedSize += size;
                    }
                } else {
                    if (offset == blockOffset + blockSize(kind)) {
                        mergedSize += size;
                        successorKey = entry.key;
                    }
                    break;
                }
            }
            if (successorKey != null)
                tree.remove(successorKey);
            tree.put(u64(mergedOffset), u64(mergedSize));
        } else {
            // this block has been modified in a previous transaction, add it to the freed
            // list
            if (freedBlocks.remaining() == 0) {
                // the current freed block list block is full, allocate a new one

                // first, write the current block
                freedBlocks.putLong(8, (freedBlocks.position() / 8) - 2); // subtract 2 for the first two uint64_t
                                                                          // fields
                var newBlock = writeNewBlock(Area.SYSTEM, freedBlocks.array());

                freedBlocks.clear(); // reset the buffer for the new block
                freedBlocks.putLong(newBlock); // write the offset of the new freed block list block
                freedBlocks.putLong(0); // skip the number of freed blocks field for now
            }

            freedBlocks.putLong(blockOffset);
        }
    }

    private void extend(Area kind) {
        try {
            long end = file.length();
            long grow = 1 << 20;
            file.setLength(end + grow);
            var key = new byte[8];
            ByteBuffer.wrap(key).putLong(end);

            var value = new byte[8];
            ByteBuffer.wrap(value).putLong(grow);

            freeSpaceTrees.get(kind).put(key, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void applyRefCountDeltas() {
        for (var e : refCountAdjustments.entrySet()) {
            long blockOffset = e.getKey();
            var delta = e.getValue();

            var count = toLong(refCountTree.get(u64(blockOffset)));
            count += delta.adjustment;
            if (count == 0) {
                refCountTree.remove(u64(blockOffset));
                freeBlock(blockOffset, delta.area);
                delta.onDelete.get().run();
            } else if (count < 0) {
                throw new IllegalStateException("Reference count for block " + blockOffset + " went negative");
            } else {
                refCountTree.put(u64(blockOffset), u64(count));
            }
        }
        refCountAdjustments.clear();
    }

    private Consumer<Long> decrementListRefCount() {

    }

    public void commit() {
        if (infoBlock.dirty) {
            writeInfoBlock();
        }
        applyRefCountDeltas();

        decrementRefCount(superBlock.rootBlock, Area.SYSTEM, (offset) -> {
            var s = deserializeRootBlock(readBlock(offset, Area.SYSTEM));

            /*
             * public long dataFreeSpaceTree;
             * public long metaFreeSpaceTree;
             * public long systemFreeSpaceTree;
             * public long freedBlockList;
             * public long metaReservedBlockList;
             * 
             * public long systemKvTree;
             * public long refCountTree;
             * public long infoBlock;
             * 
             * public long nextGuidCounter;
             * public long guidMappingTree;
             * public long distributedClock;
             * public long operationStagingTree;
             * public long operationRetrievalTree;
             * public long fileSystemTree;
             * public long operationTree;
             * public long snapshotTree;
             */

            decrementRefCount(s.dataFreeSpaceTree, Area.SYSTEM, BTree.decrementRefCount(this));
            decrementRefCount(s.metaFreeSpaceTree, Area.SYSTEM, BTree.decrementRefCount(this));
            decrementRefCount(s.systemFreeSpaceTree, Area.SYSTEM, BTree.decrementRefCount(this));
            decrementRefCount(s.freedBlockList, Area.SYSTEM, BTree.decrementRefCount(this));
            decrementRefCount(s.refCountTree, Area.SYSTEM, BTree.decrementRefCount(this));
        });

        flushModified();

        boolean keepInfo = infoOffset != 0 && !infoBlock.isDirty();

        // 2. Structural tree writes execute in allocator mode
        allocatorTreeMode = true;
        try {
            // Free prior allocator blocks into reserved set
            Set<Long> toFree = new HashSet<>(allocatorBlocks);
            if (keepInfo)
                toFree.remove(infoOffset);
            reserved.addAll(toFree);
            for (long b : toFree)
                freeRaw(b, Area.SYSTEM);
            allocatorBlocks.clear();
            if (keepInfo)
                allocatorBlocks.add(infoOffset);

            // 3. Process deferred refcount deltas
            applyRefCountDeltas();
            rootBlock.refCountTree = refCountTree.rootOffset;

            // 4. Info block update
            if (!keepInfo) {
                infoOffset = allocateRawBlock(Area.SYSTEM);
                allocatorBlocks.add(infoOffset);
                writeInfoBlock();
            }
            rootBlock.infoBlock = infoOffset;

            // 5. Reserve root block offset
            rootOffset = allocateRawBlock(Area.SYSTEM);
            allocatorBlocks.add(rootOffset);

            // 6. Build persistent free space B-trees
            for (Area kind : Area.values()) {
                BTree tree = new BTree(this, Area.SYSTEM, BLOCK_OFFSET_SIZE);
                for (Map.Entry<Long, Long> e : new TreeMap<>(freeSpace.get(kind)).entrySet()) {
                    tree.put(u64(e.getKey()), u64(e.getValue()));
                }
                freeSpaceTrees.put(kind, tree);
                setFreeTreeRoot(kind, tree.rootOffset);
            }

            // 7. Write root block
            writeBlock(rootOffset, Area.SYSTEM, serializeRootBlock(rootBlock));
        } finally {
            allocatorTreeMode = false;
        }

        // 8. Flush structural writes
        flushModified();

        // 9. Atomic commit step: publish root pointer via superblock
        superBlock.rootBlock = rootOffset;
        writeSuperBlock();

        // 10. Clean up state
        reserved.clear();
        infoBlock.clearDirty();
    }

    private void flushModified() {
        for (Map.Entry<Long, byte[]> e : modifiedBlocks.entrySet()) {
            if (e.getValue() != null)
                writeToDisk(e.getKey(), e.getValue());
        }
        modifiedBlocks.clear();
    }

    private void writeToDisk(long offset, byte[] data) {
        try {
            file.seek(offset);
            file.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] readFromDisk(long offset, int size) {
        try {
            byte[] buf = new byte[size];
            file.seek(offset);
            file.readFully(buf);
            return buf;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ByteBuffer wrap(byte[] buf) {
        return ByteBuffer.wrap(buf);
    }

    private static byte[] u64(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private static long toLong(byte[] value) {
        return ByteBuffer.wrap(value).getLong();
    }

    @Override
    public void close() {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            file = null;
        }
    }

    public Map<Area, TreeMap<Long, Long>> getFreeSpace() {
        return freeSpace;
    }

    public long getRefCount(long offset) {
        return refCounts.getOrDefault(offset, 0L);
    }

    public int getRefCountEntryCount() {
        return refCounts.size();
    }

    public long getRootOffset() {
        return rootOffset;
    }

    public long getInfoOffset() {
        return infoOffset;
    }

    public long getFreeBytes(Area kind) {
        long total = 0;
        for (long size : freeSpace.get(kind).values())
            total += size;
        return total;
    }

    public long getFileSize() {
        try {
            return file.length();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}