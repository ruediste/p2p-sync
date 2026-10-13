package com.github.ruediste.p2psync.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A BTRFS-like data storage system.
 *
 * <p>
 * All data lives in a single file. The file starts with a 4KB superblock which
 * points to the current root block. The root block in turn points to the
 * individual trees and the info block. A new root block is allocated whenever a
 * transaction is committed; the commit happens at the moment the superblock is
 * updated with the offset of the new root block.
 *
 * <p>
 * The storage is organized into three different areas ({@link BlockArea}):
 * system, meta and data. Each area has its own block
 * size and its own free space tree.
 *
 * <p>
 * Following the BTRFS approach, the free space and reference count information
 * are persisted as B-trees. This results in a cycle: allocating new space
 * requires updating the space allocation trees, which in turn requires
 * allocating new blocks. As all free space trees are stored in the meta area,
 * this only applies to the meta area free space tree.
 * This is resolved by the concept of reserved space. During
 * a transaction,
 * </p>
 */
public class DataStorage implements Closeable {

    public static final byte[] MAGIC = new byte[] { (byte) 0xd6, (byte) 0x1b, (byte) 0x52, (byte) 0xd5, (byte) 0x6d,
            (byte) 0xc6, (byte) 0x7e, (byte) 0x4f, (byte) 0xfd, (byte) 0x40, (byte) 0xc6, (byte) 0xd8, (byte) 0xf5,
            (byte) 0xee, (byte) 0x03, (byte) 0xdb };

    public static final long FORMAT_VERSION = 1;
    public static final int SUPERBLOCK_SIZE = 4096;

    private static final int TREE_KEY_SIZE = 8;
    private static final long OWNER_REFCOUNT = 1;
    private static final long OWNER_FREE_SPACE = 100;

    public static class SuperBlock {
        byte[] magic = new byte[16];
        long version; // Version of the file format
        long rootBlock; // offset of the root block within the file
        int dataBlockSize;
        int metaBlockSize;
        int systemBlockSize;
    }

    public static class RootBlock {
        public long dataFreeSpaceTree;
        public long metaFreeSpaceTree;
        public long systemFreeSpaceTree;
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

    public enum BlockArea {
        SYSTEM,
        META,
        DATA
    }

    public static class InfoBlock {
        boolean dirty;
        private int dataBlockSize; // typically 4KB
        private int metaBlockSize; // typically 16KB
        private int systemBlockSize; // typically 4KB
        private int inlineDataThreshold; // typically 16KB
        private int maxExtentDataBlockSize; // typically 4KB

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

    // Current state of the storage system, matching the state of the current
    // transaction.
    public SuperBlock superBlock;
    public RootBlock rootBlock;
    public InfoBlock infoBlock;

    private final BTreeService btreeService = new BTreeService(this);

    private RandomAccessFile file;
    private String path;

    // Free space per kind, keyed by start offset, value is the size in bytes.
    // This is the allocator's working set for the current transaction. The
    // persistent free space B-trees are the authoritative source of truth; this
    // map is reconciled with them incrementally on every commit.
    private final EnumMap<BlockArea, TreeMap<Long, Long>> freeSpace = new EnumMap<>(BlockArea.class);

    // Snapshot of the free space currently represented by the persistent
    // free space B-trees. Only the difference to {@link #freeSpace} is applied
    // on commit, which keeps the trees updated incrementally instead of
    // rebuilding them.
    private final EnumMap<BlockArea, TreeMap<Long, Long>> persistedFreeSpace = new EnumMap<>(BlockArea.class);

    // Reference counts of allocated (user) blocks, persisted across commits.
    private final TreeMap<Long, Long> refCounts = new TreeMap<>();

    // Persistent free space and refcount trees.
    private final EnumMap<BlockArea, BTreeService.Tree> freeSpaceTrees = new EnumMap<>(BlockArea.class);
    private BTreeService.Tree refCountTree;

    // Pending refcount deltas for the current transaction.
    private final Map<Long, Long> pendingDelta = new HashMap<>();
    private final Map<Long, BlockArea> pendingKind = new HashMap<>();

    // Blocks that have been modified in the current transaction. The key is the
    // block offset and the value is the block data. Direct writes are performed
    // immediately, in which case the value is set to null.
    private final HashMap<Long, byte[]> modifiedBlocks = new HashMap<>();

    // Currently managed blocks (root block and info block).
    private long rootOffset;
    private long infoOffset;

    // Structure blocks owned by the current commit (allocator trees, root and
    // info). They are freed at the beginning of the next commit.
    private final Set<Long> allocatorBlocks = new HashSet<>();

    // While set, allocations belong to the allocator's own trees and therefore
    // do not create reference count entries.
    private boolean allocatorTreeMode;

    // Blocks which must not be reused before the superblock has been updated.
    private final Set<Long> reserved = new HashSet<>();

    // Number of metadata/system bytes that are reserved for the allocator's own
    // structural allocations (B-tree nodes, root and info blocks). Ordinary
    // allocations may not consume below this watermark, so a commit is always
    // able to allocate the blocks it needs.
    private long metaReserved;

    public DataStorage() {
        for (BlockArea kind : BlockArea.values()) {
            freeSpace.put(kind, new TreeMap<>());
            persistedFreeSpace.put(kind, new TreeMap<>());
            freeSpaceTrees.put(kind, btreeService.createTree(BlockArea.SYSTEM, TREE_KEY_SIZE));
        }
        refCountTree = btreeService.createTree(BlockArea.SYSTEM, TREE_KEY_SIZE);
    }

    // ------------------------------------------------------------------
    // Opening / formatting
    // ------------------------------------------------------------------

    public void open(String path) {
        this.path = path;
        File f = new File(path);
        boolean exists = f.exists() && f.length() >= SUPERBLOCK_SIZE;
        if (!exists) {
            format(path, 4096, 16384, 4096, 512 * 1024, 2 * 1024 * 1024, 8 * 1024 * 1024);
        } else {
            try {
                file = new RandomAccessFile(path, "rw");
                readSuperBlock();
                readRootBlock();
                readInfoBlock();
                loadAllocatorTrees();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Create a new, empty data file.
     *
     * @param systemBytes initial size of the system block region
     * @param metaBytes   initial size of the metadata block region
     * @param dataBytes   initial size of the data block region
     */
    public void format(String path, int dataBlockSize, int metaBlockSize, int systemBlockSize, long systemBytes,
            long metaBytes, long dataBytes) {
        this.path = path;
        try {
            new File(path).delete();
            file = new RandomAccessFile(path, "rw");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        superBlock = new SuperBlock();
        System.arraycopy(MAGIC, 0, superBlock.magic, 0, MAGIC.length);
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

        // Lay out the initial regions directly after the superblock.
        long pos = SUPERBLOCK_SIZE;
        long systemStart = pos;
        pos += systemBytes;
        long metaStart = pos;
        pos += metaBytes;
        long dataStart = pos;
        pos += dataBytes;
        try {
            file.setLength(pos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        freeSpace.get(BlockArea.SYSTEM).put(systemStart, systemBytes);
        freeSpace.get(BlockArea.META).put(metaStart, metaBytes);
        freeSpace.get(BlockArea.DATA).put(dataStart, dataBytes);

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
        rootOffset = superBlock.rootBlock;
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
        byte[] buf = new byte[blockSize(BlockArea.SYSTEM)];
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

    private void readInfoBlock() throws IOException {
        byte[] buf = readFromDisk(rootBlock.infoBlock, blockSize(BlockArea.SYSTEM));
        infoOffset = rootBlock.infoBlock;
        ByteBuffer bb = wrap(buf);
        infoBlock = new InfoBlock();
        infoBlock.dataBlockSize = bb.getInt();
        infoBlock.metaBlockSize = bb.getInt();
        infoBlock.systemBlockSize = bb.getInt();
        infoBlock.inlineDataThreshold = bb.getInt();
        infoBlock.maxExtentDataBlockSize = bb.getInt();
    }

    private void writeInfoBlock() {
        byte[] buf = new byte[blockSize(BlockArea.SYSTEM)];
        ByteBuffer bb = wrap(buf);
        bb.putInt(infoBlock.dataBlockSize);
        bb.putInt(infoBlock.metaBlockSize);
        bb.putInt(infoBlock.systemBlockSize);
        bb.putInt(infoBlock.inlineDataThreshold);
        bb.putInt(infoBlock.maxExtentDataBlockSize);
        writeToDisk(infoOffset, buf);
    }

    // ------------------------------------------------------------------
    // Allocator trees
    // ------------------------------------------------------------------

    private void loadAllocatorTrees() {
        for (BlockArea kind : BlockArea.values()) {
            freeSpace.get(kind).clear();
            BTreeService.Tree tree = btreeService.createTree(BlockArea.SYSTEM, TREE_KEY_SIZE,
                    OWNER_FREE_SPACE + kind.ordinal());
            tree.rootOffset = getFreeTreeRoot(kind);
            freeSpaceTrees.put(kind, tree);
            btreeService.forEach(tree, (k, v) -> freeSpace.get(kind).put(toLong(k), toLong(v)));
        }

        refCounts.clear();
        refCountTree = btreeService.createTree(BlockArea.SYSTEM, TREE_KEY_SIZE, OWNER_REFCOUNT);
        refCountTree.rootOffset = rootBlock.refCountTree;
        btreeService.forEach(refCountTree, (k, v) -> refCounts.put(toLong(k), toLong(v)));

        // Record what the persistent free space trees currently represent before
        // recovering the tree's own structure blocks. This snapshot is the basis
        // for the incremental updates performed on the next commit.
        for (BlockArea kind : BlockArea.values()) {
            persistedFreeSpace.get(kind).clear();
            persistedFreeSpace.get(kind).putAll(freeSpace.get(kind));
        }

        // The trees may list their own structure blocks as free (they were
        // allocated while their content was being written). Recover them by
        // walking the trees.
        allocatorBlocks.clear();
        for (BlockArea kind : BlockArea.values())
            btreeService.collectBlocks(freeSpaceTrees.get(kind), off -> {
                removeFromFree(off, BlockArea.SYSTEM);
                allocatorBlocks.add(off);
            });
        btreeService.collectBlocks(refCountTree, off -> {
            removeFromFree(off, BlockArea.SYSTEM);
            allocatorBlocks.add(off);
        });
        removeFromFree(rootOffset, BlockArea.SYSTEM);
        removeFromFree(infoOffset, BlockArea.SYSTEM);
        allocatorBlocks.add(rootOffset);
        allocatorBlocks.add(infoOffset);
    }

    private long getFreeTreeRoot(BlockArea kind) {
        return switch (kind) {
            case SYSTEM -> rootBlock.systemFreeSpaceTree;
            case META -> rootBlock.metaFreeSpaceTree;
            case DATA -> rootBlock.dataFreeSpaceTree;
        };
    }

    private void setFreeTreeRoot(BlockArea kind, long root) {
        switch (kind) {
            case SYSTEM -> rootBlock.systemFreeSpaceTree = root;
            case META -> rootBlock.metaFreeSpaceTree = root;
            case DATA -> rootBlock.dataFreeSpaceTree = root;
        }
    }

    // ------------------------------------------------------------------
    // Block sizes / IO
    // ------------------------------------------------------------------

    public int blockSize(BlockArea kind) {
        return switch (kind) {
            case SYSTEM -> superBlock.systemBlockSize;
            case META -> superBlock.metaBlockSize;
            case DATA -> superBlock.dataBlockSize;
        };
    }

    /**
     * Reads a block, taking modified blocks and direct writes into account.
     */
    public byte[] readBlock(long blockOffset, BlockArea kind) {
        byte[] mod = modifiedBlocks.get(blockOffset);
        if (mod != null)
            return mod.clone();
        try {
            return readFromDisk(blockOffset, blockSize(kind));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isBlockModified(long blockOffset) {
        return modifiedBlocks.containsKey(blockOffset);
    }

    /**
     * Track a modification of a block. The data is kept in memory until the next
     * commit.
     */
    public void writeBlock(long blockOffset, BlockArea kind, byte[] data) {
        if (data.length != blockSize(kind))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(kind) + " got " + data.length);
        modifiedBlocks.put(blockOffset, data.clone());
    }

    /**
     * Track a block whose data has already been written directly to disk.
     */
    public void writeBlockDirect(long blockOffset, BlockArea kind, byte[] data) {
        writeToDisk(blockOffset, data);
        modifiedBlocks.put(blockOffset, null);
    }

    public long writeNewBlock(BlockArea kind, byte[] data) {
        long off = allocateBlock(kind);
        writeBlock(off, kind, data);
        return off;
    }

    public long writeNewBlockDirect(BlockArea kind, byte[] data) {
        long off = allocateBlock(kind);
        writeBlockDirect(off, kind, data);
        return off;
    }

    // ------------------------------------------------------------------
    // Allocation / free space
    // ------------------------------------------------------------------

    /**
     * Allocate a block and bump its reference count. The returned block is not
     * written.
     */
    public long allocateBlock(BlockArea kind) {
        long off = allocateRawBlock(kind);
        if (allocatorTreeMode) {
            allocatorBlocks.add(off);
        } else {
            pendingDelta.merge(off, 1L, Long::sum);
            pendingKind.put(off, kind);
        }
        return off;
    }

    private long allocateRawBlock(BlockArea kind) {
        int bs = blockSize(kind);
        // Keep the reserved metadata watermark free for the allocator's own
        // structural allocations by growing the file instead of dipping into it.
        if (!allocatorTreeMode && metaReserved > 0
                && (kind == BlockArea.SYSTEM || kind == BlockArea.META)
                && getMetaFreeBytes() - bs < metaReserved) {
            extend(kind);
        }
        TreeMap<Long, Long> map = freeSpace.get(kind);
        for (Map.Entry<Long, Long> e : map.entrySet()) {
            long start = e.getKey();
            long size = e.getValue();
            long candidate = start;
            while (candidate + bs <= start + size && reserved.contains(candidate))
                candidate += bs;
            if (candidate + bs <= start + size) {
                map.remove(start);
                if (candidate > start)
                    map.put(start, candidate - start);
                long after = candidate + bs;
                if (after < start + size)
                    map.put(after, start + size - after);
                return candidate;
            }
        }
        extend(kind);
        return allocateRawBlock(kind);
    }

    /**
     * Decrement the reference count of a block. When it reaches zero during
     * commit, the block is returned to the free space map.
     */
    public void freeBlock(long blockOffset, BlockArea kind) {
        if (allocatorTreeMode) {
            freeRaw(blockOffset, kind);
            // A block freed during a commit must not be reused before the
            // superblock has been updated.
            reserved.add(blockOffset);
            return;
        }
        pendingDelta.merge(blockOffset, -1L, Long::sum);
        pendingKind.put(blockOffset, kind);
    }

    private void extend(BlockArea kind) {
        try {
            long end = file.length();
            long grow = Math.max(blockSize(kind), 1 << 20);
            file.setLength(end + grow);
            addFree(kind, end, grow);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addFree(BlockArea kind, long offset, long size) {
        TreeMap<Long, Long> map = freeSpace.get(kind);
        Map.Entry<Long, Long> prev = map.floorEntry(offset);
        if (prev != null && prev.getKey() + prev.getValue() == offset) {
            offset = prev.getKey();
            size += prev.getValue();
            map.remove(prev.getKey());
        }
        Map.Entry<Long, Long> next = map.ceilingEntry(offset);
        while (next != null && next.getKey() <= offset + size) {
            size = Math.max(size, next.getKey() + next.getValue() - offset);
            map.remove(next.getKey());
            next = map.ceilingEntry(offset);
        }
        map.put(offset, size);
    }

    /**
     * Return a block to the free space map. Frees are idempotent: a block that
     * is already free or currently reserved is left untouched. This is required
     * because during a commit the previous allocator blocks are freed up front
     * and the copy-on-write updates of the allocator trees may free the very
     * same blocks again.
     */
    private void freeRaw(long offset, BlockArea kind) {
        if (offset == 0)
            return;
        if (reserved.contains(offset))
            return;
        TreeMap<Long, Long> map = freeSpace.get(kind);
        Map.Entry<Long, Long> e = map.floorEntry(offset);
        if (e != null && offset < e.getKey() + e.getValue())
            return;
        addFree(kind, offset, blockSize(kind));
    }

    /** Whether the given offset currently lies within a free extent. */
    private boolean isFree(long offset, BlockArea kind) {
        TreeMap<Long, Long> map = freeSpace.get(kind);
        Map.Entry<Long, Long> e = map.floorEntry(offset);
        return e != null && offset < e.getKey() + e.getValue();
    }

    private void removeFromFree(long offset, BlockArea kind) {
        TreeMap<Long, Long> map = freeSpace.get(kind);
        if (offset == 0)
            return;
        Map.Entry<Long, Long> e = map.floorEntry(offset);
        if (e == null)
            return;
        long start = e.getKey();
        long size = e.getValue();
        if (offset >= start && offset < start + size) {
            map.remove(start);
            if (offset > start)
                map.put(start, offset - start);
            long after = offset + blockSize(kind);
            if (after < start + size)
                map.put(after, start + size - after);
        }
    }

    /**
     * Apply the deferred reference count updates to the in-memory map and,
     * incrementally, to the persistent reference count B-tree. This must run
     * while {@link #allocatorTreeMode} is set, so that the blocks allocated by
     * the B-tree updates do not themselves create reference count entries.
     */
    private void applyRefCountDeltas() {
        for (Map.Entry<Long, Long> e : pendingDelta.entrySet()) {
            long off = e.getKey();
            long delta = e.getValue();
            BlockArea kind = pendingKind.get(off);
            long newCount = refCounts.getOrDefault(off, 0L) + delta;
            byte[] keyBytes = u64(off);
            if (newCount <= 0) {
                refCounts.remove(off);
                btreeService.remove(refCountTree, keyBytes);
                freeRaw(off, kind);
            } else {
                refCounts.put(off, newCount);
                btreeService.put(refCountTree, keyBytes, u64(newCount));
            }
        }
        pendingDelta.clear();
        pendingKind.clear();
    }

    /**
     * Apply the difference between the free space tree snapshot and the current
     * in-memory free space to a persistent free space B-tree.
     *
     * <p>
     * Only the changed extents are written: keys that disappeared are removed,
     * keys that were added or whose size changed are put. This keeps the tree
     * updated incrementally instead of rebuilding it from scratch.
     */
    private void updateFreeSpaceTree(BlockArea kind, TreeMap<Long, Long> desired) {
        BTreeService.Tree tree = freeSpaceTrees.get(kind);
        TreeMap<Long, Long> persisted = persistedFreeSpace.get(kind);
        Set<Long> keys = new HashSet<>(desired.keySet());
        keys.addAll(persisted.keySet());
        for (Long key : keys) {
            Long d = desired.get(key);
            Long p = persisted.get(key);
            if (d == null) {
                if (p != null)
                    btreeService.remove(tree, u64(key));
            } else if (p == null || !p.equals(d)) {
                btreeService.put(tree, u64(key), u64(d));
            }
        }
        persisted.clear();
        persisted.putAll(desired);
        setFreeTreeRoot(kind, tree.rootOffset);
    }

    /**
     * Rebuild a free space tree from the in-memory map. This is used for the
     * system free space tree, whose own blocks are allocated from the system
     * region. The tree blocks allocated here are not part of the snapshot taken
     * before the rebuild, so they are recovered on open by walking the tree.
     */
    private void rebuildFreeSpaceTree(BlockArea kind) {
        BTreeService.Tree tree = btreeService.createTree(BlockArea.SYSTEM, TREE_KEY_SIZE,
                OWNER_FREE_SPACE + kind.ordinal());
        for (Map.Entry<Long, Long> e : new TreeMap<>(freeSpace.get(kind)).entrySet())
            btreeService.put(tree, u64(e.getKey()), u64(e.getValue()));
        freeSpaceTrees.put(kind, tree);
        setFreeTreeRoot(kind, tree.rootOffset);
        persistedFreeSpace.get(kind).clear();
        persistedFreeSpace.get(kind).putAll(freeSpace.get(kind));
    }

    // ------------------------------------------------------------------
    // Transaction / commit
    // ------------------------------------------------------------------

    /**
     * Commit the current transaction. The commit is the moment the superblock is
     * updated with the offset of the new root block.
     */
    public void commit() {
        // 1. Flush pending block modifications to disk.
        flushModified();

        // An unchanged info block is kept across commits.
        boolean keepInfo = infoOffset != 0 && !infoBlock.isDirty();

        // All structural allocations performed below belong to the allocator's
        // own trees: they must not create reference count entries and must be
        // excluded from the free space trees (they are recovered on open by
        // walking the trees).
        allocatorTreeMode = true;
        try {
            // 2. Free the previous commit's allocator structure blocks, then
            // keep them reserved so they cannot be overwritten before the
            // superblock is updated. The copy-on-write updates below will try to
            // free these blocks again; freeRaw is idempotent so this is safe.
            Set<Long> toFree = new HashSet<>(allocatorBlocks);
            if (keepInfo)
                toFree.remove(infoOffset);
            for (long b : toFree)
                freeRaw(b, BlockArea.SYSTEM);
            reserved.addAll(toFree);
            allocatorBlocks.clear();
            if (keepInfo)
                allocatorBlocks.add(infoOffset);

            // 3. Apply deferred reference count updates incrementally.
            applyRefCountDeltas();
            rootBlock.refCountTree = refCountTree.rootOffset;

            // 4. Info block, if changed.
            if (!keepInfo) {
                infoOffset = allocateRawBlock(BlockArea.SYSTEM);
                allocatorBlocks.add(infoOffset);
                writeInfoBlock();
            }
            rootBlock.infoBlock = infoOffset;

            // 5. Reserve the new root block.
            rootOffset = allocateRawBlock(BlockArea.SYSTEM);
            allocatorBlocks.add(rootOffset);

            // 6. Update the data and metadata free space trees incrementally.
            // These updates only allocate system blocks (the trees' own kind),
            // so there is no self-reference. The system free space tree is
            // rebuilt last from the in-memory map; its own newly allocated
            // blocks are deliberately excluded from the snapshot and recovered
            // on open, which breaks the circular dependency the same way BTRFS
            // does.
            updateFreeSpaceTree(BlockArea.DATA, new TreeMap<>(freeSpace.get(BlockArea.DATA)));
            updateFreeSpaceTree(BlockArea.META, new TreeMap<>(freeSpace.get(BlockArea.META)));
            rebuildFreeSpaceTree(BlockArea.SYSTEM);

            // 7. Root block content.
            writeBlock(rootOffset, BlockArea.SYSTEM, serializeRootBlock(rootBlock));
        } finally {
            allocatorTreeMode = false;
        }

        // 8. Flush managed blocks before publishing the new root.
        flushModified();

        // 9. Publish (atomic commit point).
        superBlock.rootBlock = rootOffset;
        writeSuperBlock();

        // 10. Clear the reservation now that the superblock is durable.
        reserved.clear();

        // 11. Recompute the set of blocks owned by the current trees. The old
        // tree blocks were returned to the free space map at the start of the
        // commit (so they are reflected in the persisted free space trees), but
        // the blocks that are still referenced by the new trees must stay
        // allocated. Walking the new trees yields exactly that set; removing
        // them from the in-memory map keeps them from being handed out until
        // they are freed by a later commit. The tree's own blocks are recovered
        // from the persisted free space trees on open the same way.
        Set<Long> live = new HashSet<>();
        for (BlockArea kind : BlockArea.values())
            btreeService.collectBlocks(freeSpaceTrees.get(kind), live::add);
        btreeService.collectBlocks(refCountTree, live::add);
        live.add(rootOffset);
        live.add(infoOffset);
        for (long b : live)
            removeFromFree(b, BlockArea.SYSTEM);
        allocatorBlocks.clear();
        allocatorBlocks.addAll(live);

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

    private byte[] readFromDisk(long offset, int size) throws IOException {
        byte[] buf = new byte[size];
        file.seek(offset);
        file.readFully(buf);
        return buf;
    }

    private static ByteBuffer wrap(byte[] buf) {
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] u64(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    private static long toLong(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong();
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

    // ------------------------------------------------------------------
    // Accessors (mostly for introspection and tests)
    // ------------------------------------------------------------------

    public String getPath() {
        return path;
    }

    public BTreeService getBTreeService() {
        return btreeService;
    }

    public Map<BlockArea, TreeMap<Long, Long>> getFreeSpace() {
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

    /**
     * Total size of the free area of the given kind, in bytes.
     */
    public long getFreeBytes(BlockArea kind) {
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

    // ------------------------------------------------------------------
    // Metadata reservation
    // ------------------------------------------------------------------

    /**
     * Total free bytes available for metadata (system and metadata blocks).
     */
    private long getMetaFreeBytes() {
        return getFreeBytes(BlockArea.SYSTEM) + getFreeBytes(BlockArea.META);
    }

    /**
     * Number of metadata bytes currently reserved for the allocator's own
     * structural allocations.
     */
    public long getMetaReserved() {
        return metaReserved;
    }

    /**
     * Metadata bytes that ordinary (non-allocator) allocations may still
     * consume without dipping into the reservation.
     */
    public long getAvailableMetaBytes() {
        return Math.max(0, getMetaFreeBytes() - metaReserved);
    }

    /**
     * Reserve metadata space for the allocator's structural allocations. While
     * the returned reservation is open, ordinary allocations will grow the file
     * instead of consuming the reserved watermark, so a commit can always
     * allocate the B-tree blocks, root and info blocks it needs.
     *
     * <p>
     * This is the equivalent of the BTRFS metadata reservation tickets: the
     * worst case metadata growth of a transaction is reserved before the
     * transaction performs user writes.
     */
    public Reservation reserveMeta(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("reservation must not be negative");
        metaReserved += bytes;
        return new Reservation(bytes);
    }

    /** A metadata reservation. Closing it releases the reserved bytes. */
    public class Reservation implements Closeable {
        private final long bytes;
        private boolean closed;

        private Reservation(long bytes) {
            this.bytes = bytes;
        }

        public long getBytes() {
            return bytes;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                metaReserved -= bytes;
            }
        }
    }
}
