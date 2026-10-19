package com.github.ruediste.p2psync.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single-file copy-on-write storage system with SYSTEM, META, and DATA areas.
 * It manages block allocation, reference counts, free-space trees, and
 * transaction-local block changes.
 */
public class DataStorage implements Closeable {

    public static final byte[] MAGIC = new byte[] {
            (byte) 0xd6, (byte) 0x1b, (byte) 0x52, (byte) 0xd5, (byte) 0x6d,
            (byte) 0xc6, (byte) 0x7e, (byte) 0x4f, (byte) 0xfd, (byte) 0x40,
            (byte) 0xc6, (byte) 0xd8, (byte) 0xf5, (byte) 0xee, (byte) 0x03, (byte) 0xdb
    };

    public static final long FORMAT_VERSION = 1;
    public static final int SUPERBLOCK_SIZE = 4096;
    private static final int BLOCK_OFFSET_SIZE = Long.BYTES;

    private final byte[] DIRECT_WRITE_EMPTY_ARRAY = new byte[0];

    final class BTreeStorage implements BTree.Storage {
        private final Area area;
        Optional<PersistableOnDelete> useDeferredRefCountUpdate = Optional.empty();

        public BTreeStorage deferred(PersistableOnDelete onDelete) {
            this.useDeferredRefCountUpdate = Optional.of(onDelete);
            return this;
        }

        BTreeStorage(Area area) {
            this.area = area;
        }

        @Override
        public int blockSize() {
            return DataStorage.this.blockSize(area);
        }

        @Override
        public byte[] readBlock(long blockOffset) {
            return DataStorage.this.readBlock(blockOffset, area);
        }

        @Override
        public boolean isBlockModified(long blockOffset) {
            return DataStorage.this.isBlockModified(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, byte[] data) {
            DataStorage.this.writeBlock(blockOffset, area, data);
        }

        @Override
        public long writeNewBlock(byte[] data) {
            return DataStorage.this.writeNewBlock(area, data);
        }

        @Override
        public void incrementRefCount(long blockOffset) {
            DataStorage.this.incrementRefCount(blockOffset, area);
        }

        @Override
        public void decrementRefCount(long blockOffset, Consumer<Long> onDelete) {
            if (useDeferredRefCountUpdate.isPresent()) {
                DataStorage.this.decrementRefCount(blockOffset, area, useDeferredRefCountUpdate.get());
            } else {
                DataStorage.this.decrementRefCount(blockOffset, area, onDelete);
            }
        }

        @Override
        public boolean prepareFreeSpace(int blockCount) {
            return !refillFreeBlocks(area, blockCount);
        }
    }

    public enum Area {
        SYSTEM,
        META,
        DATA
    }

    public static class SuperBlock {
        byte[] magic = new byte[MAGIC.length];
        long version;
        long rootBlock;

        // block sizes have to be here in order to load the root block
        public int dataBlockSize;
        public int metaBlockSize;
        public int systemBlockSize;
    }

    /**
     * The root block itself is not ref counted. Before writing a new root block to
     * disk,
     * the ref count of all referenced blocks is decremented.
     */
    public static class RootBlock {
        public long dataFreeSpaceTree;
        public long metaFreeSpaceTree;
        public long systemFreeSpaceTree;

        /** Heads of the deferred-free chains */
        public long systemDeferredFreeBlockList;
        public long metaDeferredFreeBlockList;
        public long dataDeferredFreeBlockList;

        /**
         * List of meta blocks which to be removed from the free space tree but deferred
         * to the next transaction, in order to avoid infinite recursion in the free
         * space tree updates (writes to the tree cause cascading changes).
         */
        public long deferredMetaFreeTreeRemoveList;

        /**
         * List of ref count adjustments deferred to the next transaction, in order
         * to avoid infinite recursion in the ref count tree updates (writes to the tree
         * cause cascading changes)l
         */
        public long deferredRefCountAdjustmentList;

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
        public long fileLength;
    }

    public static class InfoBlock {
        boolean dirty;

        private int inlineDataThreshold;

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            this.dirty = false;
        }

        public int getInlineDataThreshold() {
            return inlineDataThreshold;
        }

        public void setInlineDataThreshold(int inlineDataThreshold) {
            this.dirty |= this.inlineDataThreshold != inlineDataThreshold;
            this.inlineDataThreshold = inlineDataThreshold;
        }

    }

    enum PersistableOnDelete {
        META_FREE_SPACE_TREE_BLOCK(1) {

            @Override
            public void onDelete(DataStorage storage, long blockOffset) {
                storage.freeSpaceTrees.get(Area.META).decrementRefCount.accept(blockOffset);
            }

        },
        REF_COUNT_TREE_BLOCK(2) {
            @Override
            public void onDelete(DataStorage storage, long blockOffset) {
                storage.refCountTree.decrementRefCount.accept(blockOffset);
            }
        };

        private final int code;

        PersistableOnDelete(int code) {
            this.code = code;
        }

        int code() {
            return code;
        }

        static PersistableOnDelete fromCode(int code) {
            for (PersistableOnDelete value : values()) {
                if (value.code == code)
                    return value;
            }
            throw new IllegalStateException("Invalid deferred ref-count delete action: " + code);
        }

        public abstract void onDelete(DataStorage storage, long blockOffset);
    }

    static class RefCountAdjustment {
        /**
         * If true, the underlying ref count is 0 and the adjustment represents the
         * total final ref count.
         */
        boolean equalsCount;
        int adjustment;
        Optional<Consumer<Long>> onDelete = Optional.empty();
        Optional<PersistableOnDelete> persistableOnDelete = Optional.empty();
        final Area area;

        private RefCountAdjustment(Area area) {
            this.area = area;
        }

        public static RefCountAdjustment create(Area area) {
            return new RefCountAdjustment(area);
        }

        @Override
        public String toString() {
            return adjustment +
                    "@" + area;
        }
    }

    RandomAccessFile file;

    SuperBlock superBlock;
    RootBlock rootBlock;
    InfoBlock infoBlock;

    /**
     * The unallocated blocks store all blocks which are not used anymore, but have
     * been
     * used in the last transaction. They are persisted to disk and added back to
     * the
     * free space tree at the beginning of the next transaction.
     * 
     * The blocks in the list have the following:
     * <ul>
     * <li>uint64_t: offset of the next unallocated block list block, 0 for end of
     * list</li>
     * <li>uint64_t: number of unallocated blocks in this block (could be a
     * uint16_t, but
     * using uint64_t for alignment)</li>
     * <li>uint64_t[]: array of unallocated block offsets</li>
     * </ul>
     * 
     * The lists are stored in system space.
     */
    EnumMap<Area, ByteBuffer> deferredFreeBlocks = new EnumMap<>(Area.class);

    private void initializeDeferredFreeBlocks() {
        deferredFreeBlocks.clear();
        for (Area area : Area.values()) {
            ByteBuffer block = ByteBuffer.allocate(superBlock.systemBlockSize);
            block.putLong(0); // offset of the next unallocated block list block, 0 for end of list
            block.putLong(0); // skip number of unallocated blocks in this block (will be updated later)
            deferredFreeBlocks.put(area, block);
        }
    }

    EnumMap<Area, TreeSet<Long>> freeBlocks = new EnumMap<>(Area.class);
    {
        for (Area area : Area.values()) {
            freeBlocks.put(area, new TreeSet<>());
        }
    }

    /**
     * Allocated offsets that still need to be removed from their area's free tree.
     */
    EnumMap<Area, HashSet<Long>> pendingFreeTreeRemovals = new EnumMap<>(Area.class);

    /**
     * Snapshot currently being removed; callbacks use it to detect in-flight
     * blocks.
     */
    EnumMap<Area, HashSet<Long>> blocksBeingRemovedFromFreeTree = new EnumMap<>(Area.class);

    /**
     * In-flight blocks that hit zero refs, to reinsert after their removal batch.
     */
    EnumMap<Area, HashSet<Long>> putBackBlocksToFreeTree = new EnumMap<>(Area.class);
    {
        for (Area area : Area.values()) {
            pendingFreeTreeRemovals.put(area, new HashSet<>());
            blocksBeingRemovedFromFreeTree.put(area, new HashSet<>());
            putBackBlocksToFreeTree.put(area, new HashSet<>());
        }
    }

    // Persistent B-trees for free space
    final EnumMap<Area, BTree> freeSpaceTrees = new EnumMap<>(Area.class);

    /**
     * Offset of the last block that has been added to the {@link #freeBlocks}. Null
     * if no block has been added yet.
     */
    final EnumMap<Area, Long> freeSpaceAllocationOffset = new EnumMap<>(Area.class);

    /**
     * Persistent B-tree for reference counts of blocks.
     * Key: block offset (uint64)
     * Value: reference count (uint64)
     */
    BTree refCountTree;

    /**
     * In-memory modified blocks (offset -> data). Direct writes store
     * {@link #DIRECT_WRITE_EMPTY_ARRAY}.
     */
    final HashMap<Long, byte[]> modifiedBlocks = new HashMap<>();

    /**
     * Invariant: adjustments can only be negative if the block has been allocated
     * in a previous transaction. A block
     * that has been allocated in the current transaction is freed immediately if
     * the adjustment becomes 0.
     */
    HashMap<Long, RefCountAdjustment> refCountAdjustments = new HashMap<>();

    private void resetInMemoryState() {
        superBlock = null;
        rootBlock = null;
        infoBlock = null;
        deferredFreeBlocks.clear();
        for (Area area : Area.values()) {
            freeBlocks.get(area).clear();
            pendingFreeTreeRemovals.get(area).clear();
            blocksBeingRemovedFromFreeTree.get(area).clear();
            putBackBlocksToFreeTree.get(area).clear();
        }
        freeSpaceTrees.clear();
        freeSpaceAllocationOffset.clear();
        modifiedBlocks.clear();
        refCountAdjustments.clear();
        refCountTree = null;
    }

    private static void validateFormatArguments(long systemBytes, long metaBytes, long dataBytes,
            int dataBlockSize, int metaBlockSize, int systemBlockSize) {
        if (systemBytes <= 0 || metaBytes <= 0 || dataBytes <= 0)
            throw new IllegalArgumentException("Area sizes must be positive");
        if (dataBlockSize <= 0 || metaBlockSize <= 0 || systemBlockSize < 20 * Long.BYTES)
            throw new IllegalArgumentException("Invalid block sizes");
        if (systemBytes % systemBlockSize != 0 || metaBytes % metaBlockSize != 0 || dataBytes % dataBlockSize != 0)
            throw new IllegalArgumentException("Area sizes must be multiples of their block sizes");
    }

    private void validateBlockOffset(long offset, int blockSize, String description) {
        long fileLength;
        try {
            fileLength = file.length();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (offset < SUPERBLOCK_SIZE || offset > fileLength - blockSize)
            throw new UncheckedIOException(new IOException("Invalid " + description + " offset: " + offset));
    }

    public void incrementRefCount(long blockOffset, Area area) {
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> RefCountAdjustment.create(area));
        adjustment.adjustment += 1;

        if (adjustment.adjustment <= 0) {
            throw new IllegalStateException("Always increment ref count before decrementing");
        }
    }

    public void decrementRefCount(long blockOffset, Area area, Consumer<Long> onDelete) {
        decrementRefCount(blockOffset, area, Optional.of(onDelete), Optional.empty());
    }

    public void decrementRefCount(long blockOffset, Area area, PersistableOnDelete onDelete) {
        decrementRefCount(blockOffset, area, Optional.empty(), Optional.of(onDelete));
    }

    private void decrementRefCount(long blockOffset, Area area, Optional<Consumer<Long>> onDelete,
            Optional<PersistableOnDelete> persistableOnDelete) {
        if (blockOffset == 0)
            return;

        if (onDelete.isEmpty() && persistableOnDelete.isEmpty())
            throw new IllegalStateException("No onDelete action provided");

        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> RefCountAdjustment.create(area));

        adjustment.adjustment--;

        if (adjustment.adjustment == 0) {
            // check if this is a block that was newly allocated in this transaction and the
            // count reached zero. If so, remove it.
            if (adjustment.equalsCount && modifiedBlocks.remove(blockOffset) != null) {
                onDelete.ifPresent(callback -> callback.accept(blockOffset));
                persistableOnDelete.ifPresent(action -> action.onDelete(this, blockOffset));

                boolean wasPending = pendingFreeTreeRemovals.get(area).remove(blockOffset);
                boolean wasBeingRemoved = blocksBeingRemovedFromFreeTree.get(area).contains(blockOffset);
                if (wasPending && wasBeingRemoved)
                    throw new IllegalStateException("Block is both pending and being removed: " + blockOffset);
                if (wasBeingRemoved)
                    putBackBlocksToFreeTree.get(area).add(blockOffset);
                else if (wasPending)
                    freeBlocks.get(area).add(blockOffset);
                else
                    addBlockToFreeTree(blockOffset, area);
            }

            // remove the ref count adjustment for this block, as it has reached zero and
            // thus has no effect
            refCountAdjustments.remove(blockOffset);
        } else {
            // prefer persistableOnDelete over onDelete
            if (persistableOnDelete.isPresent()) {
                if (adjustment.adjustment < 0 && adjustment.persistableOnDelete.isEmpty()) {
                    adjustment.persistableOnDelete = persistableOnDelete;
                    adjustment.onDelete = Optional.empty();
                }
            } else {
                if (adjustment.adjustment < 0 && adjustment.onDelete.isEmpty()
                        && adjustment.persistableOnDelete.isEmpty()) {
                    adjustment.onDelete = onDelete;
                }
            }
        }
    }

    private void onDeferredRefCountAdjustmentListBlockDelete(long blockOffset) {
        validateBlockOffset(blockOffset, blockSize(Area.META), "deferred-ref-count-adjustment list block");
        var buffer = wrap(readBlock(blockOffset, Area.META));
        long next = buffer.getLong();
        if (next != 0)
            decrementRefCount(next, Area.META, this::onDeferredRefCountAdjustmentListBlockDelete);
    }

    public void open(String path) {
        close();
        resetInMemoryState();
        File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            format(path);
            return;
        }
        if (f.length() < SUPERBLOCK_SIZE)
            throw new UncheckedIOException(new IOException("Truncated data storage superblock"));
        try {
            file = new RandomAccessFile(path, "rw");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            readSuperBlock();
            readRootBlock();
            truncateToRootFileLength();
            readInfoBlock();
            loadSystemTrees();
            loadDeferredMetaFreeTreeRemoveBlocks();
            loadDeferredRefCountAdjustmentList();
            retainWorkingTreeRoots();
            addDeferredFreeBlocksToFreeTree();
        } catch (RuntimeException e) {
            close();
            resetInMemoryState();
            throw e;
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
        validateFormatArguments(systemBytes, metaBytes, dataBytes, dataBlockSize, metaBlockSize, systemBlockSize);
        close();
        resetInMemoryState();
        try {
            file = new RandomAccessFile(path, "rw");
            file.setLength(0);
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
        initializeDeferredFreeBlocks();

        infoBlock = new InfoBlock();
        infoBlock.inlineDataThreshold = 16384;

        rootBlock = new RootBlock();

        // define file layout
        long systemStart;
        long metaStart;
        long dataStart;
        {
            systemStart = alignUp(SUPERBLOCK_SIZE, systemBlockSize);
            metaStart = alignUp(Math.addExact(systemStart, systemBytes), metaBlockSize);
            dataStart = alignUp(Math.addExact(metaStart, metaBytes), dataBlockSize);
        }
        try {
            rootBlock.fileLength = Math.addExact(dataStart, dataBytes);
            file.setLength(rootBlock.fileLength);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Formatted area layout exceeds supported file size", e);
        }

        // Seed a bounded META bootstrap pool; the rest of the area is fetched from its
        // tree.
        long bootstrapBlocks = Math.min(256, metaBytes / metaBlockSize);
        long bootstrapEnd = Math.addExact(metaStart, Math.multiplyExact(bootstrapBlocks, (long) metaBlockSize));
        for (var offset = metaStart; offset < bootstrapEnd; offset += metaBlockSize)
            freeBlocks.get(Area.META).add(offset);
        if (bootstrapBlocks > 0)
            freeSpaceAllocationOffset.put(Area.META, bootstrapEnd - metaBlockSize);

        // initialize the free space trees
        {
            var tree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE, (key, value) -> {
            });
            tree.put(u64(systemStart), u64(systemBytes));
            freeSpaceTrees.put(Area.SYSTEM, tree);
        }
        {
            var tree = new BTree(new BTreeStorage(Area.META).deferred(PersistableOnDelete.META_FREE_SPACE_TREE_BLOCK),
                    BLOCK_OFFSET_SIZE,
                    (key, value) -> {
                    });
            tree.put(u64(metaStart), u64(metaBytes));
            freeSpaceTrees.put(Area.META, tree);
        }
        {
            var tree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE, (key, value) -> {
            });
            tree.put(u64(dataStart), u64(dataBytes));
            freeSpaceTrees.put(Area.DATA, tree);
        }

        refCountTree = new BTree(new BTreeStorage(Area.META).deferred(PersistableOnDelete.REF_COUNT_TREE_BLOCK),
                BLOCK_OFFSET_SIZE, null);
        commit();

    }

    private void readSuperBlock() {
        byte[] buf = new byte[SUPERBLOCK_SIZE];
        try {
            file.seek(0);
            file.readFully(buf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ByteBuffer bb = wrap(buf);
        superBlock = new SuperBlock();
        bb.get(superBlock.magic);
        if (!java.util.Arrays.equals(superBlock.magic, MAGIC)) {
            throw new UncheckedIOException(new IOException("Not a p2p-sync data file (bad magic)"));
        }
        superBlock.version = bb.getLong();
        superBlock.rootBlock = bb.getLong();
        superBlock.dataBlockSize = bb.getInt();
        superBlock.metaBlockSize = bb.getInt();
        superBlock.systemBlockSize = bb.getInt();
        if (superBlock.version != FORMAT_VERSION)
            throw new UncheckedIOException(
                    new IOException("Unsupported data storage format version: " + superBlock.version));
        if (superBlock.dataBlockSize <= 0 || superBlock.metaBlockSize <= 0
                || superBlock.systemBlockSize < 20 * Long.BYTES)
            throw new UncheckedIOException(new IOException("Invalid data storage block sizes"));
        validateBlockOffset(superBlock.rootBlock, superBlock.systemBlockSize, "root block");
        initializeDeferredFreeBlocks();
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

    private void readRootBlock() {
        byte[] buf = readBlock(superBlock.rootBlock, Area.SYSTEM);
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
        r.fileLength = bb.getLong();
        r.systemDeferredFreeBlockList = bb.getLong();
        r.metaDeferredFreeBlockList = bb.getLong();
        r.dataDeferredFreeBlockList = bb.getLong();
        r.deferredMetaFreeTreeRemoveList = bb.getLong();
        r.deferredRefCountAdjustmentList = bb.getLong();
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
        bb.putLong(r.fileLength);
        bb.putLong(r.systemDeferredFreeBlockList);
        bb.putLong(r.metaDeferredFreeBlockList);
        bb.putLong(r.dataDeferredFreeBlockList);
        bb.putLong(r.deferredMetaFreeTreeRemoveList);
        bb.putLong(r.deferredRefCountAdjustmentList);
        return buf;
    }

    private void truncateToRootFileLength() {
        long actualLength = getFileLength();
        if (rootBlock.fileLength < SUPERBLOCK_SIZE || rootBlock.fileLength > actualLength)
            throw new UncheckedIOException(new IOException("Invalid committed file length: " + rootBlock.fileLength));
        if (superBlock.rootBlock > rootBlock.fileLength - blockSize(Area.SYSTEM))
            throw new UncheckedIOException(new IOException("Committed file length does not contain its root block"));

        try {
            file.setLength(rootBlock.fileLength);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long getFileLength() {
        try {
            return file.length();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readInfoBlock() {
        validateBlockOffset(rootBlock.infoBlock, blockSize(Area.SYSTEM), "info block");
        byte[] buf = readBlock(rootBlock.infoBlock, Area.SYSTEM);
        infoBlock = deserializeInfoBlock(buf);
    }

    private InfoBlock deserializeInfoBlock(byte[] buf) {
        ByteBuffer bb = wrap(buf);
        var infoBlock = new InfoBlock();
        infoBlock.inlineDataThreshold = bb.getInt();
        return infoBlock;
    }

    private byte[] serializeInfoBlock(InfoBlock infoBlock) {
        byte[] buf = new byte[blockSize(Area.SYSTEM)];
        ByteBuffer bb = wrap(buf);
        bb.putInt(infoBlock.inlineDataThreshold);
        return buf;
    }

    private void loadSystemTrees() {
        for (Area area : Area.values()) {
            BTree.Storage storage = area == Area.META
                    ? new BTreeStorage(Area.META).deferred(PersistableOnDelete.META_FREE_SPACE_TREE_BLOCK)
                    : new BTreeStorage(Area.META);
            BTree tree = new BTree(storage, BLOCK_OFFSET_SIZE, null, getFreeTreeRoot(area));
            freeSpaceTrees.put(area, tree);
        }
        refCountTree = new BTree(new BTreeStorage(Area.META).deferred(PersistableOnDelete.REF_COUNT_TREE_BLOCK),
                BLOCK_OFFSET_SIZE, null,
                rootBlock.refCountTree);
    }

    private void retainWorkingTreeRoots() {
        for (Area area : Area.values()) {
            long rootOffset = freeSpaceTrees.get(area).rootOffset;
            if (rootOffset != 0)
                incrementRefCount(rootOffset, Area.META);
        }
        if (refCountTree.rootOffset != 0)
            incrementRefCount(refCountTree.rootOffset, Area.META);
    }

    long takeFreeBlock(Area area) {
        var blocks = freeBlocks.get(area);
        if (blocks.isEmpty())
            refillFreeBlocks(area, 256);
        var offset = blocks.isEmpty() ? null : blocks.first();
        if (offset == null)
            throw new IllegalStateException("No free " + area + " blocks available after refill");
        if (pendingFreeTreeRemovals.get(area).contains(offset)
                || blocksBeingRemovedFromFreeTree.get(area).contains(offset))
            throw new IllegalStateException("Block is already allocated or reserved: " + offset);
        if (refCountAdjustments.containsKey(offset))
            throw new IllegalStateException("Block already has a refcount adjustment: " + offset);
        blocks.remove(offset);
        pendingFreeTreeRemovals.get(area).add(offset);
        var adjustment = RefCountAdjustment.create(area);
        adjustment.equalsCount = true;
        adjustment.adjustment = 1;
        refCountAdjustments.put(offset, adjustment);
        return offset;
    }

    /*
     * Refill the free blocks for the given area up to the desired count. Returns
     * true if the storage was extended.
     */
    private boolean refillFreeBlocks(Area area, int desiredFreeBlockCount) {
        var blocks = freeBlocks.get(area);
        if (desiredFreeBlockCount <= blocks.size())
            return false;

        BTree tree = freeSpaceTrees.get(area);
        if (tree == null)
            throw new IllegalStateException("Free-space tree is not initialized for " + area);

        int blockSize = blockSize(area);
        var extended = false;

        while (true) {
            Long cursor = freeSpaceAllocationOffset.get(area);
            var iterator = cursor == null ? tree.iterator() : tree.floorOrLowestIterator(u64(cursor));
            while (iterator.hasNext() && blocks.size() < desiredFreeBlockCount) {
                // read the next free-space tree entry
                var entry = iterator.next();
                if (entry.key.length != Long.BYTES || entry.value.length != Long.BYTES)
                    throw new IllegalStateException("Invalid free-space tree entry size for " + area);
                long extentOffset = toLong(entry.key);
                long extentSize = toLong(entry.value);
                if (extentOffset < SUPERBLOCK_SIZE || extentSize <= 0
                        || extentOffset % blockSize != 0 || extentSize % blockSize != 0)
                    throw new IllegalStateException("Misaligned or empty free extent in " + area + " tree");
                long extentEnd;
                try {
                    extentEnd = Math.addExact(extentOffset, extentSize);
                } catch (ArithmeticException e) {
                    throw new IllegalStateException("Free extent overflows file offsets", e);
                }
                if (extentEnd > rootBlock.fileLength)
                    throw new IllegalStateException("Free extent exceeds storage file length in " + area + " tree");

                long offset = extentOffset;
                // Skip blocks that are before the cursor
                if (cursor != null) {
                    // the cursor is not in this extent, skip to the next one
                    if (extentEnd <= cursor)
                        continue;

                    if (offset <= cursor) {
                        try {
                            offset = Math.addExact(cursor, blockSize);
                        } catch (ArithmeticException e) {
                            throw new IllegalStateException("Free-space cursor overflows file offsets", e);
                        }
                    }
                }

                // iterate over the blocks within the current free-space extent and add them to
                // the free block list
                for (; offset < extentEnd
                        && blocks.size() < desiredFreeBlockCount; offset += blockSize) {
                    if (pendingFreeTreeRemovals.get(area).contains(offset)
                            || blocksBeingRemovedFromFreeTree.get(area).contains(offset)) {
                        cursor = offset;
                        continue;
                    }
                    if (!blocks.add(offset))
                        throw new IllegalStateException("Free extent returned a duplicate block " + offset);
                    cursor = offset;
                }
            }

            freeSpaceAllocationOffset.put(area, cursor);

            // If we have gathered enough free blocks, update the allocation offset and
            // return.
            if (blocks.size() >= desiredFreeBlockCount) {
                return extended;
            }

            // we iterated to the end of the current free-space extents without gathering
            // enough free blocks, so we need to extend the storage.
            extended = true;
            extend(area);
        }
    }

    private static long alignUp(long value, int alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
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
        if (mod != null && mod != DIRECT_WRITE_EMPTY_ARRAY) {
            return mod.clone();
        }

        // Either there was a direct write, or the block was
        // not modified. Read it from the disk.
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
        modifiedBlocks.put(blockOffset, DIRECT_WRITE_EMPTY_ARRAY);

        writeToDisk(blockOffset, data);
    }

    public long writeNewBlock(Area area, byte[] data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, data.clone());
        return off;
    }

    public long writeNewBlockDirect(Area area, byte[] data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, DIRECT_WRITE_EMPTY_ARRAY);
        writeToDisk(off, data);
        return off;
    }

    public void addToDeferredFreeBlocks(long blockOffset, Area area) {
        // this block has been modified in a previous transaction
        var list = deferredFreeBlocks.get(area);
        if (list.remaining() == 0) {
            // the current freed block list block is full, allocate a new one

            // first, write the current block
            list.putLong(8, (list.position() / 8) - 2); // subtract 2 for the first two
                                                        // uint64_t
            // fields
            var newBlock = writeNewBlock(Area.SYSTEM, list.array());

            list.clear(); // reset the buffer for the new block
            list.putLong(newBlock); // write the offset of the new freed block list block
            list.putLong(0); // skip the number of freed blocks field for now
        }

        list.putLong(blockOffset);
    }

    private void persistDeferredFreeBlockHeads() {
        int headerSize = 2 * Long.BYTES;
        int capacity = (blockSize(Area.SYSTEM) - headerSize) / Long.BYTES;
        for (Area area : Area.values()) {
            ByteBuffer list = deferredFreeBlocks.get(area);
            long head = list.getLong(0);
            if (list.position() > headerSize) {
                int count = (list.position() - headerSize) / Long.BYTES;
                if (count > capacity)
                    throw new IllegalStateException("Deferred-free list buffer exceeds block capacity");
                list.putLong(Long.BYTES, count);
                head = writeNewBlock(Area.SYSTEM, list.array());
            }
            setDeferredFreeListHead(area, head);
            list.clear();
            list.putLong(0);
            list.putLong(0);
        }
    }

    private void addBlockToFreeTree(long blockOffset, Area area) {
        var tree = freeSpaceTrees.get(area);
        var it = tree.floorOrLowestIterator(u64(blockOffset));
        long mergedOffset = blockOffset;
        long mergedSize = blockSize(area);
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
                if (offset == blockOffset + blockSize(area)) {
                    mergedSize += size;
                    successorKey = entry.key;
                }
                break;
            }
        }
        if (successorKey != null)
            tree.remove(successorKey);
        tree.put(u64(mergedOffset), u64(mergedSize));
    }

    private long getDeferredFreeListHead(Area area) {
        return switch (area) {
            case SYSTEM -> rootBlock.systemDeferredFreeBlockList;
            case META -> rootBlock.metaDeferredFreeBlockList;
            case DATA -> rootBlock.dataDeferredFreeBlockList;
        };
    }

    private void setDeferredFreeListHead(Area area, long head) {
        switch (area) {
            case SYSTEM -> rootBlock.systemDeferredFreeBlockList = head;
            case META -> rootBlock.metaDeferredFreeBlockList = head;
            case DATA -> rootBlock.dataDeferredFreeBlockList = head;
        }
    }

    private void addDeferredFreeBlocksToFreeTree() {
        int capacity = (blockSize(Area.SYSTEM) - 2 * Long.BYTES) / Long.BYTES;
        for (Area area : Area.values()) {
            long offset = getDeferredFreeListHead(area);
            setDeferredFreeListHead(area, 0);
            Set<Long> visited = new HashSet<>();
            while (offset != 0) {
                validateBlockOffset(offset, blockSize(Area.SYSTEM), "deferred-free list block");
                if (!visited.add(offset))
                    throw new IllegalStateException("Cycle in deferred-free list for " + area);
                ByteBuffer buffer = wrap(readBlock(offset, Area.SYSTEM));
                long next = buffer.getLong();
                long count = buffer.getLong();
                if (count < 0 || count > capacity)
                    throw new IllegalStateException("Invalid deferred-free list count: " + count);
                for (int i = 0; i < count; i++) {
                    long freed = buffer.getLong();
                    validateBlockOffset(freed, blockSize(area), "deferred-free block");
                    addBlockToFreeTree(freed, area);
                }
                offset = next;
            }
        }
    }

    private void loadDeferredMetaFreeTreeRemoveBlocks() {
        for (Area area : Area.values()) {
            pendingFreeTreeRemovals.get(area).clear();
            blocksBeingRemovedFromFreeTree.get(area).clear();
            putBackBlocksToFreeTree.get(area).clear();
        }
        int capacity = (blockSize(Area.META) - 2 * Long.BYTES) / Long.BYTES;
        long offset = rootBlock.deferredMetaFreeTreeRemoveList;
        rootBlock.deferredMetaFreeTreeRemoveList = 0;
        Set<Long> visited = new HashSet<>();
        while (offset != 0) {
            validateBlockOffset(offset, blockSize(Area.META), "deferred-meta-free-tree-remove list block");
            if (!visited.add(offset))
                throw new IllegalStateException("Cycle in deferred-meta-free-tree-remove list");
            PendingMetaFreeTreeRemoveListBlock block = PendingMetaFreeTreeRemoveListBlock.deserialize(
                    readBlock(offset, Area.META), capacity);
            for (long reserved : block.blockOffsets) {
                validateBlockOffset(reserved, blockSize(Area.META), "deferred-meta-free-tree-remove block");
                if (!pendingFreeTreeRemovals.get(Area.META).add(reserved))
                    throw new IllegalStateException("Duplicate deferred-meta-free-tree-remove block: " + reserved);
            }
            offset = block.next;
        }
    }

    private void loadDeferredRefCountAdjustmentList() {
        refCountAdjustments.clear();

        long offset = rootBlock.deferredRefCountAdjustmentList;
        rootBlock.deferredRefCountAdjustmentList = 0;
        Set<Long> visited = new HashSet<>();
        Set<Long> adjustedBlocks = new HashSet<>();
        while (offset != 0) {
            validateBlockOffset(offset, blockSize(Area.META), "deferred-ref-count-adjustment list block");
            if (!visited.add(offset))
                throw new IllegalStateException("Cycle in deferred ref-count adjustment list");

            ByteBuffer buffer = wrap(readBlock(offset, Area.META));
            long next = buffer.getLong();
            long count = buffer.getLong();

            for (int index = 0; index < count; index++) {
                long blockOffset = buffer.getLong();
                int adjustmentValue = buffer.getInt();
                Area area = areaFromCode(Byte.toUnsignedInt(buffer.get()));
                int actionCode = Byte.toUnsignedInt(buffer.get());
                Optional<PersistableOnDelete> action = actionCode == 0
                        ? Optional.empty()
                        : Optional.of(PersistableOnDelete.fromCode(actionCode));

                validateBlockOffset(blockOffset, blockSize(area), "deferred ref-count adjustment block");
                if (adjustmentValue == 0)
                    throw new IllegalStateException("Zero deferred ref-count adjustment for block " + blockOffset);
                if (!adjustedBlocks.add(blockOffset))
                    throw new IllegalStateException("Duplicate deferred ref-count adjustment for block " + blockOffset);
                if (adjustmentValue < 0 && action.isEmpty())
                    throw new IllegalStateException(
                            "Missing delete action for deferred ref-count adjustment " + blockOffset);

                var adjustment = new RefCountAdjustment(area);
                adjustment.persistableOnDelete = action;
                adjustment.adjustment = adjustmentValue;
                refCountAdjustments.put(blockOffset, adjustment);
            }
            offset = next;
        }
    }

    private static int areaCode(Area area) {
        return switch (area) {
            case SYSTEM -> 1;
            case META -> 2;
            case DATA -> 3;
        };
    }

    private static Area areaFromCode(int code) {
        return switch (code) {
            case 1 -> Area.SYSTEM;
            case 2 -> Area.META;
            case 3 -> Area.DATA;
            default -> throw new IllegalStateException("Invalid deferred ref-count area: " + code);
        };
    }

    private void extend(Area area) {
        BTree tree = freeSpaceTrees.get(area);
        if (tree == null)
            throw new IllegalStateException("Free-space tree is not initialized for " + area);
        int blockSize = blockSize(area);
        try {
            long start = alignUp(file.length(), blockSize);
            long minimumGrowth = Math.multiplyExact((long) blockSize, 256);
            long growth = alignUp(Math.max(1L << 20, minimumGrowth), blockSize);
            long end = Math.addExact(start, growth);
            file.setLength(end);
            rootBlock.fileLength = end;

            // all free space trees live in the META area. Make sure there is enough space
            // when extending the META area
            if (area == Area.META) {
                var freeBlocks = this.freeBlocks.get(Area.META);
                var required = tree.getWorstCaseBlocksForWrite() - freeBlocks.size();
                if (required > 0) {
                    for (int i = 0; i < required; i++) {
                        freeBlocks.add(start + i * blockSize);
                    }
                    freeSpaceAllocationOffset.put(Area.META, start + (required - 1) * blockSize);
                }
            }
            tree.put(u64(start), u64(growth));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Storage extension overflows file offsets", e);
        }
    }

    private void removeBlockFromFreeTree(Area area, long blockOffset) {
        BTree tree = freeSpaceTrees.get(area);
        var iterator = tree.floorOrLowestIterator(u64(blockOffset));
        if (!iterator.hasNext())
            throw new IllegalStateException(
                    "Allocated block is missing from " + area + " free-space tree: " + blockOffset);

        var entry = iterator.next();
        long extentOffset = toLong(entry.key);
        long extentSize = toLong(entry.value);
        long extentEnd;
        long blockEnd;
        try {
            extentEnd = Math.addExact(extentOffset, extentSize);
            blockEnd = Math.addExact(blockOffset, blockSize(area));
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Free-space range overflows file offsets", e);
        }
        if (blockOffset < extentOffset || blockEnd > extentEnd)
            throw new IllegalStateException(
                    "Allocated block is not contained in " + area + " free extent: " + blockOffset);

        tree.remove(entry.key);
        if (blockOffset > extentOffset)
            tree.put(u64(extentOffset), u64(blockOffset - extentOffset));
        if (blockEnd < extentEnd)
            tree.put(u64(blockEnd), u64(extentEnd - blockEnd));
    }

    void removePendingFreeTreeRemovals(boolean includeMeta) {
        // Work from a snapshot so recursive tree writes become pending for a later
        // sweep.
        for (Area area : Area.values()) {
            if (!includeMeta && area == Area.META)
                continue;
            var pending = pendingFreeTreeRemovals.get(area);
            var beingRemoved = blocksBeingRemovedFromFreeTree.get(area);
            if (!beingRemoved.isEmpty())
                throw new IllegalStateException("Free-tree removal batch is already in progress for " + area);
            beingRemoved.addAll(pending);
            pending.clear();
        }

        for (Area area : Area.values()) {
            if (!includeMeta && area == Area.META)
                continue;
            for (long blockOffset : blocksBeingRemovedFromFreeTree.get(area))
                removeBlockFromFreeTree(area, blockOffset);
        }

        for (Area area : Area.values()) {
            if (!includeMeta && area == Area.META)
                continue;
            // Reinsert only after the batch is gone from the tree, avoiding recursive
            // updates.
            blocksBeingRemovedFromFreeTree.get(area).clear();
            for (long blockOffset : putBackBlocksToFreeTree.get(area))
                addBlockToFreeTree(blockOffset, area);
            putBackBlocksToFreeTree.get(area).clear();
        }
    }

    private void applyRefCountAdjustments() {
        var entries = refCountAdjustments.entrySet();
        refCountAdjustments = new HashMap<>();

        for (var e : entries) {
            long blockOffset = e.getKey();
            var delta = e.getValue();
            int adjustment = delta.adjustment;

            var existingCount = refCountTree.get(u64(blockOffset));
            var count = existingCount == null ? 0 : toLong(existingCount);
            count += adjustment;
            if (count == 0) {
                if (existingCount != null)
                    refCountTree.remove(u64(blockOffset));

                if (delta.onDelete.isEmpty() && delta.persistableOnDelete.isEmpty()) {
                    throw new IllegalStateException(
                            "No onDelete action specified for " + delta.area + " block " + blockOffset);
                }
                delta.onDelete.ifPresent(c -> c.accept(blockOffset));
                delta.persistableOnDelete.ifPresent(x -> x.onDelete(this, blockOffset));

                if (modifiedBlocks.containsKey(blockOffset)) {
                    boolean wasPending = pendingFreeTreeRemovals.get(delta.area).remove(blockOffset);
                    boolean wasBeingRemoved = blocksBeingRemovedFromFreeTree.get(delta.area).contains(blockOffset);
                    if (wasPending && wasBeingRemoved)
                        throw new IllegalStateException("Block is both pending and being removed: " + blockOffset);
                    if (wasBeingRemoved)
                        putBackBlocksToFreeTree.get(delta.area).add(blockOffset);
                    else if (wasPending)
                        freeBlocks.get(delta.area).add(blockOffset);
                    else
                        addBlockToFreeTree(blockOffset, delta.area);
                } else {
                    // This block was used in the previous transaction. Add it to the free space in
                    // the next transaction.
                    addToDeferredFreeBlocks(blockOffset, delta.area);
                }
            } else if (count < 0) {
                throw new IllegalStateException("Reference count for " + delta.area + " block " + blockOffset
                        + " went negative: existing=" + (count - adjustment) + ", adjustment=" + adjustment);
            } else {
                refCountTree.put(u64(blockOffset), u64(count));
            }
        }
    }

    private void applyAndRemovePendingFreeTreeRemovals() {
        // Bound recursive META-tree updates; persist any remaining META work for next
        // time.
        for (int round = 0; round < 2; round++) {
            applyRefCountAdjustments();
            removePendingFreeTreeRemovals(true);
        }

        // The durable list stores META offsets only. Drain other areas and immediate
        // callbacks, while leaving their recursively generated META work for
        // persistence.
        while (true) {
            applyRefCountAdjustments();
            removePendingFreeTreeRemovals(false);
            boolean hasNonPersistableAdjustments = refCountAdjustments.values().stream()
                    .anyMatch(adjustment -> adjustment.onDelete.isPresent());
            boolean hasNonMetaPendingRemovals = pendingFreeTreeRemovals.get(Area.SYSTEM).size() > 0
                    || pendingFreeTreeRemovals.get(Area.DATA).size() > 0;
            if (!hasNonPersistableAdjustments && !hasNonMetaPendingRemovals)
                return;
        }
    }

    private static class PendingMetaFreeTreeRemoveListBlock {
        public long next;
        public long count;
        public List<Long> blockOffsets;

        public static PendingMetaFreeTreeRemoveListBlock deserialize(byte[] bytes, int capacity) {
            PendingMetaFreeTreeRemoveListBlock block = new PendingMetaFreeTreeRemoveListBlock();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            block.next = buffer.getLong();
            block.count = buffer.getLong();
            if (block.count < 0 || block.count > capacity)
                throw new IllegalStateException("Invalid meta-reserved list count: " + block.count);
            block.blockOffsets = new ArrayList<>();
            for (int i = 0; i < block.count; i++) {
                block.blockOffsets.add(buffer.getLong());
            }
            return block;
        }
    }

    private record DeferredPersistor(Supplier<Boolean> blockAllocator, Runnable persist) {
    }

    private <T> DeferredPersistor persistDeferredMetaList(Supplier<List<T>> entries, int entrySize,
            BiConsumer<ByteBuffer, T> persist, Consumer<Long> setHead) {
        if (entrySize <= 0)
            throw new IllegalArgumentException("Deferred META list entry size must be positive");
        int capacity = (blockSize(Area.META) - 2 * Long.BYTES) / entrySize;
        if (capacity <= 0)
            throw new IllegalStateException("META block is too small for a deferred list");

        List<Long> listOffsets = new ArrayList<>();
        return new DeferredPersistor(
                () -> {
                    int requiredListBlocks = requiredDeferredMetaListBlockCount(
                            entries.get().size(), listOffsets.size(), capacity);
                    if (listOffsets.size() >= requiredListBlocks)
                        return true;
                    var offset = takeFreeBlock(Area.META);
                    incrementRefCount(offset, Area.META);
                    modifiedBlocks.put(offset, DIRECT_WRITE_EMPTY_ARRAY);
                    listOffsets.add(offset);
                    return false;
                },
                () -> {
                    List<T> records = entries.get();
                    if (records.isEmpty()) {
                        setHead.accept(0L);
                        return;
                    }

                    int recordIndex = 0;
                    for (int blockIndex = 0; blockIndex < listOffsets.size(); blockIndex++) {
                        byte[] bytes = new byte[blockSize(Area.META)];
                        ByteBuffer buffer = wrap(bytes);
                        long next = blockIndex + 1 < listOffsets.size() ? listOffsets.get(blockIndex + 1) : 0;
                        int count = Math.min(capacity, records.size() - recordIndex);
                        buffer.putLong(next);
                        buffer.putLong(count);
                        for (int index = 0; index < count; index++)
                            persist.accept(buffer, records.get(recordIndex++));
                        writeBlockDirect(listOffsets.get(blockIndex), Area.META, bytes);
                    }
                    if (recordIndex != records.size())
                        throw new IllegalStateException("Deferred META list entries exceed allocated blocks");
                    setHead.accept(listOffsets.get(0));
                });
    }

    static int requiredDeferredMetaListBlockCount(int entryCount, int listBlockCount, int capacity) {
        if (entryCount < listBlockCount || listBlockCount < 0 || capacity <= 0)
            throw new IllegalArgumentException("Invalid deferred META list sizing");

        int entriesBesidesOwnBlocks = entryCount - listBlockCount;
        if (entriesBesidesOwnBlocks == 0)
            return 0;
        if (capacity == 1)
            throw new IllegalStateException("META deferred list cannot store entries with capacity 1");

        // Every list block is also an entry, leaving capacity - 1 slots per block
        // for entries that do not belong to the list itself.
        return 1 + (entriesBesidesOwnBlocks - 1) / (capacity - 1);
    }

    private DeferredPersistor persistDeferredMetaFreeTreeRemoveBlockList() {
        return persistDeferredMetaList(
                () -> new ArrayList<>(pendingFreeTreeRemovals.get(Area.META)), Long.BYTES,
                ByteBuffer::putLong, head -> rootBlock.deferredMetaFreeTreeRemoveList = head);
    }

    private DeferredPersistor persistRefCountAdjustmentList() {
        return persistDeferredMetaList(
                () -> refCountAdjustments.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList(),
                // blockOffset + adjustment + area + action
                Long.BYTES + Integer.BYTES + 2,
                (buffer, entry) -> {
                    long blockOffset = entry.getKey();
                    RefCountAdjustment adjustment = entry.getValue();
                    Optional<PersistableOnDelete> action = adjustment.persistableOnDelete;
                    int actionCode = action.map(PersistableOnDelete::code).orElse(0);
                    if (adjustment.adjustment == 0)
                        throw new IllegalStateException("Should have been removed when adjustment became 0");
                    if (adjustment.adjustment < 0 && action.isEmpty())
                        throw new IllegalStateException(
                                "Missing durable delete action for block " + blockOffset);
                    buffer.putLong(blockOffset);
                    buffer.putInt(adjustment.adjustment);
                    buffer.put((byte) areaCode(adjustment.area));
                    buffer.put((byte) actionCode);
                }, head -> rootBlock.deferredRefCountAdjustmentList = head);
    }

    private Consumer<Long> decrementMetaReservedListBlock;
    {
        decrementMetaReservedListBlock = (offset) -> {
            PendingMetaFreeTreeRemoveListBlock block = PendingMetaFreeTreeRemoveListBlock.deserialize(
                    readBlock(offset, Area.META), (blockSize(Area.META) - 2 * Long.BYTES) / Long.BYTES);
            if (block.next != 0)
                decrementRefCount(block.next, Area.META, decrementMetaReservedListBlock);
            block.blockOffsets.forEach(o -> decrementRefCount(o, Area.META,
                    PersistableOnDelete.META_FREE_SPACE_TREE_BLOCK));
        };
    }

    public void commit() {
        // update the info block
        if (infoBlock.dirty || rootBlock.infoBlock == 0) {
            decrementRefCount(rootBlock.infoBlock, Area.SYSTEM, ignored -> {
                // NOP
            });
            rootBlock.infoBlock = writeNewBlock(Area.SYSTEM, serializeInfoBlock(infoBlock));
            infoBlock.dirty = false;
        }

        // Perform the heavy tree updating
        applyAndRemovePendingFreeTreeRemovals();
        decrementRefCount(superBlock.rootBlock, Area.SYSTEM, this::onRootBlockDelete);
        applyAndRemovePendingFreeTreeRemovals();

        // allocate the root block
        superBlock.rootBlock = takeFreeBlock(Area.SYSTEM);
        modifiedBlocks.put(superBlock.rootBlock, DIRECT_WRITE_EMPTY_ARRAY);

        persistDeferredFreeBlockHeads();
        applyAndRemovePendingFreeTreeRemovals();

        // store deferred meta free tree removals and ref count updates
        var persistors = new ArrayList<DeferredPersistor>();
        persistors.add(persistDeferredMetaFreeTreeRemoveBlockList());
        persistors.add(persistRefCountAdjustmentList());
        while (true) {
            // All persistors might need new blocks allocated, but they should
            // eventually eventually settle
            boolean allAllocated = true;
            for (DeferredPersistor persistor : persistors) {
                if (!persistor.blockAllocator.get()) {
                    allAllocated = false;
                }
            }
            if (allAllocated)
                break;
        }
        for (DeferredPersistor persistor : persistors)
            persistor.persist.run();

        // prepare and write the root block (already allocated)
        for (Area area : Area.values())
            setFreeTreeRoot(area, freeSpaceTrees.get(area).rootOffset);
        rootBlock.refCountTree = refCountTree.rootOffset;
        writeBlock(superBlock.rootBlock, Area.SYSTEM, serializeRootBlock(rootBlock));

        // perform the commit
        flushModified();
        writeSuperBlock();

        // prepare next transaction
        loadDeferredMetaFreeTreeRemoveBlocks();
        loadDeferredRefCountAdjustmentList();
        retainWorkingTreeRoots();
        addDeferredFreeBlocksToFreeTree();
    }

    private void onRootBlockDelete(long offset) {
        var rootBlock = deserializeRootBlock(readBlock(offset, Area.SYSTEM));
        decrementRefCount(rootBlock.dataFreeSpaceTree, Area.META,
                freeSpaceTrees.get(Area.DATA).decrementRefCount);
        decrementRefCount(rootBlock.metaFreeSpaceTree, Area.META,
                freeSpaceTrees.get(Area.META).decrementRefCount);
        decrementRefCount(rootBlock.systemFreeSpaceTree, Area.META,
                freeSpaceTrees.get(Area.SYSTEM).decrementRefCount);
        decrementRefCount(rootBlock.refCountTree, Area.META,
                refCountTree.decrementRefCount);
        decrementRefCount(rootBlock.deferredMetaFreeTreeRemoveList, Area.META,
                decrementMetaReservedListBlock);
        decrementRefCount(rootBlock.deferredRefCountAdjustmentList, Area.META,
                this::onDeferredRefCountAdjustmentListBlockDelete);
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

    public long getRefCount(long blockOffset) {
        var value = refCountTree.get(u64(blockOffset));
        return value == null ? 0 : toLong(value);
    }
}