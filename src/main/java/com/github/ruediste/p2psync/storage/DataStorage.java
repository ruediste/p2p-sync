package com.github.ruediste.p2psync.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.github.ruediste.p2psync.storage.BlockType.BlockInstance;
import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.util.Event;

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

    public static final BlockType<ByteArrayBlock> BYTE_ARRAY_BLOCK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(ByteArrayBlock value, DataStorage storage) {
            return List.of();
        }

        @Override
        public ByteBuffer serialize(ByteArrayBlock value) {
            return ByteBuffer.wrap(value.data);
        }

        @Override
        public ByteArrayBlock deserialize(ByteBuffer buffer) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return new ByteArrayBlock(data);
        }
    };

    public static class ByteArrayBlock implements BlockInstance<ByteArrayBlock> {
        final byte[] data;

        public ByteArrayBlock(byte[] data) {
            this.data = data.clone();
        }

        public byte[] data() {
            return data.clone();
        }

        @Override
        public BlockType<ByteArrayBlock> getBlockType() {
            return BYTE_ARRAY_BLOCK_TYPE;
        }
    }

    /**
     * Represents potential modifications to the data storage. Used
     * to formalize reasoning especially during the commit process.
     */
    @SuppressWarnings("unused")
    private enum Mod {
        /** no modification */
        NONE,
        /** general reference count update */
        REFCOUNT_UPDATE,
        /** reference count update for blocks of a free tree */
        REFCOUNT_UPDATE_FREE_TREE,
        /** reference count update for blocks of the meta free */
        REFCOUNT_UPDATE_FREE_TREE_META,
        /** add pending free tree removal {@link #pendingFreeTreeRemovals} */
        ADD_PENDING_FREE_TREE_REMOVAL
    }

    private static class DeferredFreeBlockListBlock {
        /** Offset of this block in the storage, 0 if not yet written */
        long offset;
        long next;
        final List<Long> blockOffsets = new ArrayList<>();

        DeferredFreeBlockListBlock(long next) {
            this.next = next;
        }
    }

    public class BTreeStorage implements BTree.Storage {
        protected final Area area;

        public BTreeStorage(Area area) {
            this.area = area;
        }

        @Override
        public int blockSize() {
            return DataStorage.this.blockSize(area);
        }

        @Override
        public Area area() {
            return area;
        }

        @Override
        public BTree.NodeBlock readBlock(long blockOffset, BlockType<BTree.NodeBlock> type) {
            return DataStorage.this.readBlock(blockOffset, area, type);
        }

        @Override
        public long replaceBlock(long blockOffset, BTree.NodeBlock data, RefMode mode) {
            return DataStorage.this.replaceBlock(blockOffset, area, data, mode);
        }

        @Override
        public long writeNewBlock(BTree.NodeBlock data, RefMode mode) {
            return DataStorage.this.writeNewBlock(area, data, mode);
        }

        @Override
        public void retireExclusiveBlock(long blockOffset) {
            DataStorage.this.retireExclusiveBlock(blockOffset, area);
        }

        @Override
        public RefMode childRefMode(long blockOffset, RefMode mode) {
            return DataStorage.this.childRefMode(blockOffset, mode);
        }

        @Override
        public boolean prepareFreeSpace(int blockCount) {
            return refillFreeBlocks(area, blockCount);
        }
    }

    class MetaFreeTreeBTreeStorage extends BTreeStorage {
        private int mutationDepth;

        /**
         * Blocks which are being retired exclusively within the current mutation.
         */
        private final java.util.ArrayDeque<RetiredExclusiveBlock> retiredExclusiveBlocks = new java.util.ArrayDeque<>();

        MetaFreeTreeBTreeStorage(Area area) {
            super(area);
        }

        /**
         * Runs a structural mutation. Currently only used by
         * {@link #retireExclusiveBlock(long, Area)}.
         * Reclamation runs after the outermost successful callback returns. A failed
         * structural mutation must be abandoned by closing and reopening storage.
         */
        @Override
        public <T> T mutate(Supplier<T> mutation) {
            mutationDepth++;
            boolean completed = false;
            try {
                T result = mutation.get();
                completed = true;
                return result;
            } finally {
                mutationDepth--;
                if (completed && mutationDepth == 0)
                    drainRetiredExclusiveBlocks();
            }
        }

        /**
         * Queues retirement within {@link #mutate(Supplier)}. The children are not
         * modified or reclaimed. It is the caller's responsibility to ensure that any
         * child blocks are properly handled.
         */
        @Override
        public void retireExclusiveBlock(long blockOffset) {
            if (mutationDepth == 0)
                throw new IllegalStateException("retireExclusiveBlock must be called within a mutate block");
            retiredExclusiveBlocks.add(new RetiredExclusiveBlock(blockOffset, area));
        }

        private void drainRetiredExclusiveBlocks() {
            while (!retiredExclusiveBlocks.isEmpty()) {
                var block = retiredExclusiveBlocks.remove();
                DataStorage.this.retireExclusiveBlock(block.offset, block.area);
            }
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

    private HashMap<BlockType<?>, PersistableBlockType> blockTypeToPersistable = new HashMap<>();

    private void refreshPersistableBlockTypes() {
        blockTypeToPersistable.clear();
        for (PersistableBlockType type : PersistableBlockType.values()) {
            blockTypeToPersistable.put(type.blockType.apply(this), type);
        }
    }

    Optional<PersistableBlockType> persistableBlockType(BlockType<?> blockType) {
        return Optional.ofNullable(blockTypeToPersistable.get(blockType));
    }

    public enum PersistableBlockType {
        META_FREE_TREE(1, ds -> ds.freeSpaceTrees.get(Area.META).blockType),
        REF_COUNT_TREE(2, ds -> ds.refCountTree.blockType),
        ;

        public final int code;
        public final Function<DataStorage, BlockType<?>> blockType;

        private PersistableBlockType(int code, Function<DataStorage, BlockType<?>> blockType) {
            this.code = code;
            this.blockType = blockType;
        }

        private static final Map<Integer, PersistableBlockType> byCode = new HashMap<>();
        static {
            for (PersistableBlockType type : PersistableBlockType.values()) {
                byCode.put(type.code, type);
            }
        }

        static PersistableBlockType fromCode(int code) {
            var result = byCode.get(code);
            if (result == null)
                throw new IllegalStateException("Unknown persistable block type: " + code);
            return result;
        }
    }

    public class RootRefHandle<T extends BlockInstance<T>> {

        public final int id;
        public final Area area;
        public final BlockType<T> blockType;

        public long blockOffset;

        private RootRefHandle(int id, Area area, BlockType<T> blockType) {
            this.id = id;
            this.area = area;
            this.blockType = blockType;
        }

        public void write(T value) {
            if (blockOffset == 0 || !isBlockModified(blockOffset)) {
                blockOffset = writeNewBlock(area, value);
            } else
                writeBlock(blockOffset, area, value);
        }
    }

    public class RootLongHandle {

        public final int id;
        public long value;

        private RootLongHandle(int id) {
            this.id = id;
        }

    }

    public <T extends BlockInstance<T>> RootRefHandle<T> getRootRefHandle(int id, Area area, BlockType<T> blockType) {
        if (id < 0 || id > 0xFFFF)
            throw new IllegalArgumentException("RootRefHandle id must be an unsigned 16-bit integer");
        if (rootRefHandles.containsKey(id))
            throw new IllegalStateException("RootRefHandle with id " + id + " already exists");
        var handle = new RootRefHandle<>(id, area, blockType);
        rootRefHandles.put(id, handle);
        return handle;
    }

    public static class InfoBlock implements BlockInstance<InfoBlock> {
        static final BlockType<InfoBlock> TYPE = new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(InfoBlock value, DataStorage storage) {
                return List.of();
            }

            @Override
            public ByteBuffer serialize(InfoBlock value) {
                return ByteBuffer.allocate(Integer.BYTES).putInt(value.inlineDataThreshold).flip();
            }

            @Override
            public InfoBlock deserialize(ByteBuffer buffer) {
                var value = new InfoBlock();
                value.inlineDataThreshold = buffer.getInt();
                return value;
            }
        };

        private int inlineDataThreshold;

        public int getInlineDataThreshold() {
            return inlineDataThreshold;
        }

        public void setInlineDataThreshold(int inlineDataThreshold) {
            this.inlineDataThreshold = inlineDataThreshold;
        }

        @Override
        public BlockType<InfoBlock> getBlockType() {
            return TYPE;
        }

    }

    public final Event<Void> beforeCommit = new Event<>();

    HashMap<Integer, RootRefHandle<?>> rootRefHandles = new HashMap<>();

    private HashMap<Integer, RootLongHandle> rootLongHandles = new HashMap<>();

    RandomAccessFile file;

    SuperBlock superBlock;
    RootBlock rootBlock;
    InfoBlock infoBlock;

    /**
     * The deferred free blocks store all blocks which are not used anymore, but
     * have been used in the last transaction. They are persisted to disk and added
     * back
     * to the free space tree at the beginning of the next transaction.
     * 
     * The blocks are stored as a linked list, with each block pointing to the next
     * one. Only the head of the list is kept in memory. The lists are stored in
     * system space.
     * 
     * They are stored in the {@link Area#META} area.
     */
    EnumMap<Area, DeferredFreeBlockListBlock> deferredFreeBlockHeads = new EnumMap<>(Area.class);

    private void initializeDeferredFreeBlockHeads() {
        deferredFreeBlockHeads.clear();
        for (Area area : Area.values()) {
            deferredFreeBlockHeads.put(area, new DeferredFreeBlockListBlock(0));
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
     * While removing the {@link #pendingFreeTreeRemovals} from the free trees,
     * callbacks use it to detect in-flight
     * blocks. When they become free during the removal process, they are added to
     * the {@link #putBackBlocksToFreeTree}.
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
    final EnumMap<Area, BTree<byte[], byte[]>> freeSpaceTrees = new EnumMap<>(Area.class);

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
    BTree<Long, Long> refCountTree;

    /**
     * An entry in {@link #modifiedBlocks}.
     * 
     * @param data       the block data, or null for direct writes and blocks which
     *                   have never been written
     * @param references the list of block references contained in this block
     */
    record ModifiedBlock(byte[] data, List<BlockRef> references) {
    }

    /**
     * Write-time snapshots of modified blocks. Direct writes retain references
     * with null data; reserved blocks have a null entry until written.
     */
    final HashMap<Long, ModifiedBlock> modifiedBlocks = new HashMap<>();

    /**
     * Pending absolute reference counts. Newly allocated blocks are freed
     * immediately when their count becomes zero.
     */
    RefCountUpdates refCountUpdates = new RefCountUpdates(this);

    /**
     * The update currently being written; callbacks for this block must not cancel
     * or reclaim it while its tree write is still being applied.
     */
    private long refCountBeingApplied;

    private record RetiredExclusiveBlock(long offset, Area area) {
    }

    /** Returns the current transaction's count, including unapplied changes. */
    public long getRefCount(long blockOffset) {
        var update = refCountUpdates.get(blockOffset);
        return update == null ? getRefPersistedCount(blockOffset) : update.count;
    }

    /**
     * Determines the ownership policy for a child of a block being changed.
     * 
     * @param blockOffset the offset of the parent block being changed
     * @param mode        the reference mode for the modification of the parent
     *                    block
     * @return the effective reference mode for the child
     */
    public RefMode childRefMode(long blockOffset, RefMode mode) {
        return switch (mode) {
            case EXCLUSIVE -> RefMode.EXCLUSIVE;
            case SHARE -> RefMode.SHARE;
            // Surviving owners of a shared parent still need its original child path.
            case TRANSFER -> getRefCount(blockOffset) > 1 ? RefMode.SHARE : RefMode.TRANSFER;
        };
    }

    public void retireExclusiveBlock(long blockOffset, Area area) {
        if (!modifiedBlocks.containsKey(blockOffset)) {
            // The previous superblock can still reach this block until commit.
            addToDeferredFreeBlocks(blockOffset, area);
            return;
        }
        modifiedBlocks.remove(blockOffset);
        // In-flight removals must finish before reinsertion. Pending allocations
        // are still in the free tree, so cancelling the reservation suffices.
        if (blocksBeingRemovedFromFreeTree.get(area).contains(blockOffset))
            putBackBlocksToFreeTree.get(area).add(blockOffset);
        else if (pendingFreeTreeRemovals.get(area).remove(blockOffset))
            freeBlocks.get(area).add(blockOffset);
        else
            addBlockToFreeTree(blockOffset, area);
    }

    private void resetInMemoryState() {
        superBlock = null;
        rootBlock = null;
        rootRefHandles.values().forEach(handle -> handle.blockOffset = 0);
        infoBlock = null;
        deferredFreeBlockHeads.clear();
        for (Area area : Area.values()) {
            freeBlocks.get(area).clear();
            pendingFreeTreeRemovals.get(area).clear();
            blocksBeingRemovedFromFreeTree.get(area).clear();
            putBackBlocksToFreeTree.get(area).clear();
        }
        freeSpaceTrees.clear();
        freeSpaceAllocationOffset.clear();
        modifiedBlocks.clear();
        refCountUpdates.clear();
        refCountTree = null;
    }

    public static BlockType<Long> LONG_BLOCK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(Long value, DataStorage storage) {
            return Collections.emptyList();
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
    };

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

    public void incrementRefCount(BlockRef ref) {
        if (ref.blockOffset() == 0)
            return;
        var update = refCountUpdates.getOrCreate(ref.blockOffset(), ref.area());
        update.count = Math.incrementExact(update.count);
        if (ref.blockOffset() != refCountBeingApplied && update.count == update.initialCount)
            refCountUpdates.remove(ref.blockOffset());
    }

    public void decrementRefCount(BlockRef ref) {
        if (ref.blockOffset() == 0)
            return;

        var update = refCountUpdates.getOrCreate(ref.blockOffset(), ref.area());

        if (update.count <= 0)
            throw new IllegalStateException("Reference count for " + ref.area() + " block " + ref.blockOffset()
                    + " would become negative");
        update.count--;

        if (update.count == 0) {
            // check if this is a block that was newly allocated in this transaction and the
            // count reached zero. If so, remove it.
            if (ref.blockOffset() != refCountBeingApplied && modifiedBlocks.containsKey(ref.blockOffset())
                    && update.initialCount == 0) {
                releaseReferencedBlocks(ref.blockOffset(), ref.area(), Optional.of(ref.type()));
                modifiedBlocks.remove(ref.blockOffset());
                boolean wasPending = pendingFreeTreeRemovals.get(ref.area()).remove(ref.blockOffset());
                boolean wasBeingRemoved = blocksBeingRemovedFromFreeTree.get(ref.area()).contains(ref.blockOffset());
                if (wasPending && wasBeingRemoved)
                    throw new IllegalStateException("Block is both pending and being removed: " + ref.blockOffset());
                if (wasBeingRemoved)
                    putBackBlocksToFreeTree.get(ref.area()).add(ref.blockOffset());
                else if (wasPending)
                    freeBlocks.get(ref.area()).add(ref.blockOffset());
                else
                    addBlockToFreeTree(ref.blockOffset(), ref.area());
                refCountUpdates.remove(ref.blockOffset());
            } else {
                refCountUpdates.setBlockType(ref.blockOffset(), update, ref.type());
            }
        } else if (ref.blockOffset() != refCountBeingApplied && update.count == update.initialCount) {
            refCountUpdates.remove(ref.blockOffset());
        }
    }

    private void releaseReferencedBlocks(long blockOffset, Area area, Optional<BlockType<?>> type) {
        var existing = modifiedBlocks.get(blockOffset);
        if (existing != null) {
            existing.references().forEach(this::decrementRefCount);
            return;
        }
        if (type.isEmpty()) {
            throw new IllegalStateException(
                    "No block type specified for " + area + " block " + blockOffset);
        }
        releaseReferencedBlocksHelper(blockOffset, area, type.get());
    }

    private <T> void releaseReferencedBlocksHelper(long blockOffset, Area area, BlockType<T> type) {
        T value = type.deserialize(ByteBuffer.wrap(readFromDisk(blockOffset, blockSize(area))));
        for (BlockRef reference : type.getReferencedBlocks(value, this))
            decrementRefCount(reference);
    }

    public void open(String path) {
        close();
        resetInMemoryState();
        File storageFile = new File(path);
        if (!storageFile.exists() || storageFile.length() == 0) {
            format(path);
            return;
        }
        if (storageFile.length() < SUPERBLOCK_SIZE)
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
            loadPendingMetaFreeTreeRemoveBlocks();
            loadDeferredRefCountUpdateList();
            addDeferredFreeBlocksToFreeTree();
        } catch (RuntimeException e) {
            close();
            resetInMemoryState();
            throw e;
        }
    }

    /**
     * Opens an existing file read-only and loads only the committed root state,
     * without starting a transaction. Used for inspection; never commit afterwards.
     */
    void openReadOnly(String path) {
        close();
        resetInMemoryState();
        try {
            file = new RandomAccessFile(path, "r");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            if (file.length() < SUPERBLOCK_SIZE)
                throw new UncheckedIOException(new IOException("Truncated data storage superblock"));
            readSuperBlock();
            readRootBlock();
            readInfoBlock();
            loadSystemTrees();
        } catch (IOException e) {
            close();
            resetInMemoryState();
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            close();
            resetInMemoryState();
            throw e;
        }
    }

    public void format(String path) {
        format(path, 32 * 1024, 2 * 1024 * 1024, 8 * 1024 * 1024, 4 * 1024, 16 * 1024, 4 * 1024);
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
        initializeDeferredFreeBlockHeads();

        infoBlock = new InfoBlock();
        infoBlock.inlineDataThreshold = 16384;

        rootBlock = new RootBlock(this);

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
            rootBlock.fileLength.value = Math.addExact(dataStart, dataBytes);
            file.setLength(rootBlock.fileLength.value);
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
            var tree = new BTree<>(new BTreeStorage(Area.META), BTree.KeySerializer.bytes(BLOCK_OFFSET_SIZE),
                    BTree.BYTE_ARRAY_ENTRY_TYPE, 0, RefMode.EXCLUSIVE);
            tree.put(u64(systemStart), u64(systemBytes));
            freeSpaceTrees.put(Area.SYSTEM, tree);
        }
        {
            var tree = new BTree<>(new MetaFreeTreeBTreeStorage(Area.META),
                    BTree.KeySerializer.bytes(BLOCK_OFFSET_SIZE),
                    BTree.BYTE_ARRAY_ENTRY_TYPE, 0, RefMode.EXCLUSIVE);
            tree.put(u64(metaStart), u64(metaBytes));
            freeSpaceTrees.put(Area.META, tree);
        }
        {
            var tree = new BTree<>(new BTreeStorage(Area.META), BTree.KeySerializer.bytes(BLOCK_OFFSET_SIZE),
                    BTree.BYTE_ARRAY_ENTRY_TYPE, 0, RefMode.EXCLUSIVE);
            tree.put(u64(dataStart), u64(dataBytes));
            freeSpaceTrees.put(Area.DATA, tree);
        }

        refCountTree = new BTree<>(new BTreeStorage(Area.META), BTree.KeySerializer.longs(),
                BTree.entryType(LONG_BLOCK_TYPE), 0, RefMode.EXCLUSIVE);

        refreshPersistableBlockTypes();

        rootBlock.infoBlock.blockOffset = writeNewBlock(Area.SYSTEM, infoBlock);
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
        ByteBuffer bb = ByteBuffer.wrap(buf);
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
        initializeDeferredFreeBlockHeads();
    }

    private void writeSuperBlock() {
        byte[] buf = new byte[SUPERBLOCK_SIZE];
        ByteBuffer bb = ByteBuffer.wrap(buf);
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
        rootBlock = readBlock(superBlock.rootBlock, Area.SYSTEM, RootBlock.getBlockType(this));

        // restore root reference handles
        for (int i = 0; i < rootBlock.refHandles.handleIds.size(); i++) {
            var id = rootBlock.refHandles.handleIds.get(i);
            var value = rootBlock.refHandles.blockOffsets.get(i);
            Optional.ofNullable(rootRefHandles.get(id))
                    .ifPresent(handle -> handle.blockOffset = value);
        }

        // restore root long handles
        for (int i = 0; i < rootBlock.longHandleIds.values.size(); i++) {
            var id = rootBlock.longHandleIds.values.get(i);
            var value = rootBlock.longHandles.values.get(i);
            Optional.ofNullable(rootLongHandles.get(id))
                    .ifPresent(handle -> handle.value = value);
        }
    }

    private void truncateToRootFileLength() {
        long actualLength = getFileLength();
        if (rootBlock.fileLength.value < SUPERBLOCK_SIZE || rootBlock.fileLength.value > actualLength)
            throw new UncheckedIOException(
                    new IOException("Invalid committed file length: " + rootBlock.fileLength.value));
        if (superBlock.rootBlock > rootBlock.fileLength.value - blockSize(Area.SYSTEM))
            throw new UncheckedIOException(new IOException("Committed file length does not contain its root block"));

        try {
            file.setLength(rootBlock.fileLength.value);
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
        validateBlockOffset(rootBlock.infoBlock.blockOffset, blockSize(Area.SYSTEM), "info block");
        infoBlock = readBlock(rootBlock.infoBlock.blockOffset, Area.SYSTEM, InfoBlock.TYPE);
    }

    private void loadSystemTrees() {
        for (Area area : Area.values()) {
            BTree.Storage storage = area == Area.META
                    ? new MetaFreeTreeBTreeStorage(Area.META)
                    : new BTreeStorage(Area.META);
            BTree<byte[], byte[]> tree = new BTree<>(storage, BTree.KeySerializer.bytes(BLOCK_OFFSET_SIZE),
                    BTree.BYTE_ARRAY_ENTRY_TYPE, rootBlock.getFreeTreeRoot(area), RefMode.EXCLUSIVE);
            freeSpaceTrees.put(area, tree);
        }
        refCountTree = new BTree<>(new BTreeStorage(Area.META), BTree.KeySerializer.longs(),
                BTree.entryType(LONG_BLOCK_TYPE),
                rootBlock.refCountTree.blockOffset, RefMode.EXCLUSIVE);

        refreshPersistableBlockTypes();
    }

    /**
     * Take a free block from the specified area. If no free blocks are available,
     * attempts to refill the free blocks, possibly extending the storage if needed.
     *
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    long takeFreeBlock(Area area) {
        var offset = takeFreeBlockNoRefcount(area);
        refCountUpdates.create(offset, area, 0);
        return offset;
    }

    long takeFreeBlockNoRefcount(Area area) {
        var freeBlocks = this.freeBlocks.get(area);
        if (freeBlocks.isEmpty())
            refillFreeBlocks(area, 256);
        var offset = freeBlocks.isEmpty() ? null : freeBlocks.first();
        if (offset == null)
            throw new IllegalStateException("No free " + area + " blocks available after refill");
        if (pendingFreeTreeRemovals.get(area).contains(offset)
                || blocksBeingRemovedFromFreeTree.get(area).contains(offset))
            throw new IllegalStateException("Block is already allocated or reserved: " + offset);
        if (refCountUpdates.containsKey(offset))
            throw new IllegalStateException("Block already has a refcount update: " + offset);
        freeBlocks.remove(offset);
        pendingFreeTreeRemovals.get(area).add(offset);
        modifiedBlocks.put(offset, new ModifiedBlock(null, List.of()));
        return offset;
    }

    /**
     * Refill the free blocks for the given area up to the desired count. Returns
     * true if there were already enough free blocks, false if the storage had to be
     * extended.
     * 
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (result && area ! = META),
     * otherwise
     * only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} (result) can happen in for all
     * areas, as all free trees live in the META area</li>
     * </ul>
     */
    private boolean refillFreeBlocks(Area area, int desiredFreeBlockCount) {
        var blocks = freeBlocks.get(area);
        if (desiredFreeBlockCount <= blocks.size())
            return true;

        BTree<byte[], byte[]> tree = freeSpaceTrees.get(area);
        if (tree == null)
            throw new IllegalStateException("Free-space tree is not initialized for " + area);

        int blockSize = blockSize(area);
        var unchanged = true;

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
                if (extentEnd > rootBlock.fileLength.value)
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
                    cursor = offset;

                    // Skip blocks which are already being removed from the free tree. This can
                    // happen as deferred removals are added to to the pendingFreeTreeRemovals,
                    // while the allocation cursor starts at the beginning of the free blocks.
                    if (pendingFreeTreeRemovals.get(area).contains(offset)
                            || blocksBeingRemovedFromFreeTree.get(area).contains(offset)) {
                        continue;
                    }

                    if (!blocks.add(offset))
                        throw new IllegalStateException("Free extent returned a duplicate block " + offset);
                }
            }

            freeSpaceAllocationOffset.put(area, cursor);

            // If we have gathered enough free blocks, update the allocation offset and
            // return.
            if (blocks.size() >= desiredFreeBlockCount) {
                return unchanged;
            }

            // we iterated to the end of the current free-space extents without gathering
            // enough free blocks, so we need to extend the storage.
            unchanged = false;
            extend(area);
        }
    }

    private static long alignUp(long value, int alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
    }

    public int blockSize(Area kind) {
        return switch (kind) {
            case SYSTEM -> superBlock.systemBlockSize;
            case META -> superBlock.metaBlockSize;
            case DATA -> superBlock.dataBlockSize;
        };
    }

    public byte[] readBlock(long blockOffset, Area area) {
        var existing = modifiedBlocks.get(blockOffset);
        if (existing != null && existing.data() != null)
            return existing.data().clone();
        return readFromDisk(blockOffset, blockSize(area));
    }

    public <T extends BlockInstance<T>> T readBlock(long blockOffset, Area area, BlockType<T> type) {
        return type.deserialize(ByteBuffer.wrap(readBlock(blockOffset, area)));
    }

    /**
     * Write a block. The block may not reference other blocks.
     * 
     * <ul>
     * <li>{@link Mod#NONE}</li>
     * </ul>
     */
    public void writeBlock(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been allocated in this transaction");
        modifiedBlocks.put(blockOffset, new ModifiedBlock(data, List.of()));
    }

    public boolean isBlockModified(long blockOffset) {
        return modifiedBlocks.containsKey(blockOffset);
    }

    /**
     * Write a new block. The refcount of the new block is zero.
     * The block is added to the current transaction and will be written to disk
     * later.
     * *
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li> If the block or the old block references
     * other blocks
     * </ul>
     */
    public <T extends BlockInstance<T>> void writeBlock(long blockOffset, Area area, T data) {
        writeBlock(blockOffset, area, data, false);
    }

    /** Writes a transaction-local block under counted or structural ownership. */
    public <T extends BlockInstance<T>> void writeBlock(long blockOffset, Area area, T data, RefMode mode) {
        java.util.Objects.requireNonNull(mode);
        if (mode != RefMode.EXCLUSIVE) {
            writeBlock(blockOffset, area, data);
            return;
        }
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Exclusive block is not transaction-local: " + blockOffset);
        // Structural children are managed by the caller, not by counted write
        // callbacks.
        ByteBuffer buffer = data.getBlockType().serialize(data);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        modifiedBlocks.put(blockOffset, new ModifiedBlock(bytes, List.of()));
    }

    /**
     * Reuses an exclusively writable transaction-local block, or copies it.
     * Counted incoming edges remain the caller's responsibility. Call within
     * {@link #mutate(Supplier)} and publish the returned offset before returning.
     */
    public <T extends BlockInstance<T>> long replaceBlock(long blockOffset, Area area, T data, RefMode mode) {
        java.util.Objects.requireNonNull(mode);

        if (blockOffset == 0) {
            // No existing block: allocate the structure's first block.
            return writeNewBlock(area, data, mode);
        }

        boolean overwriteInPlace = switch (mode) {
            case SHARE -> {
                // Preserve the source even if it is transaction-local.
                yield false;
            }
            case EXCLUSIVE -> {
                // Structural ownership needs no refcount check, but committed bytes
                // must remain unchanged until the new superblock is published.
                yield isBlockModified(blockOffset);
            }
            case TRANSFER -> {
                // Committed bytes must survive until commit, regardless of ownership.
                if (!isBlockModified(blockOffset))
                    yield false;
                // Move only one incoming reference; preserve the source for other owners.
                if (getRefCount(blockOffset) > 1)
                    yield false;
                // Reuse a singly owned block, or a new block awaiting its first reference.
                yield true;
            }
        };

        if (overwriteInPlace) {
            writeBlock(blockOffset, area, data, mode);
            return blockOffset;
        }
        long replacement = writeNewBlock(area, data, mode);
        // Exclusive blocks have no counted incoming edge to trigger reclamation.
        // Counted sources instead follow the caller's incoming-reference update.
        if (mode == RefMode.EXCLUSIVE)
            retireExclusiveBlock(blockOffset, area);
        return replacement;
    }

    /**
     * Write a new block directly to disk. The refcount of the new block is zero.
     * The block is written immediately and not kept in memory for later writing.
     * The block may not reference other blocks.
     * <ul>
     * <li>{@link Mod#NONE}</li>
     * </ul>
     */
    public void writeBlockDirect(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been allocated in this transaction");
        writeToDisk(blockOffset, data);
    }

    public <T extends BlockInstance<T>> void writeBlockDirect(long blockOffset, Area area, T data) {
        writeBlock(blockOffset, area, data, true);
    }

    /**
     * Store a modified block in the current transaction. If direct is true,
     * the block is written directly to disk. Otherwise, the serialized block is
     * kept in memory and written to disk later.
     * Updates the reference counts for both the new block and decrements the
     * reference counts for any replaced blocks.
     * 
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    private <T extends BlockInstance<T>> void writeBlock(long blockOffset, Area area, T data, boolean direct) {
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been allocated in this transaction");
        byte[] bytes = data.getBlockType().serialize(data).array();
        var type = data.getBlockType();
        List<BlockRef> references = List.copyOf(type.getReferencedBlocks(data, this));
        references.forEach(this::incrementRefCount);
        var existing = modifiedBlocks.put(blockOffset, new ModifiedBlock(direct ? null : bytes, references));
        if (existing != null)
            existing.references().forEach(this::decrementRefCount);
        if (refCountUpdates.containsKey(blockOffset))
            if (direct)
                writeToDisk(blockOffset, bytes);
    }

    /**
     * Write a new block. The refcount of the new block is zero.
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    public <T extends BlockInstance<T>> long writeNewBlock(Area area, T data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, null);
        writeBlock(off, area, data);
        return off;
    }

    /**
     * Allocates under the supplied ownership policy; its parent supplies counted
     * references.
     */
    public <T extends BlockInstance<T>> long writeNewBlock(Area area, T data, RefMode mode) {
        java.util.Objects.requireNonNull(mode);
        if (mode != RefMode.EXCLUSIVE)
            return writeNewBlock(area, data);
        // Keep allocator reservations, but do not create a refcount entry.
        long offset = takeFreeBlockNoRefcount(area);
        writeBlock(offset, area, data, mode);
        return offset;
    }

    /**
     * Writes a new block with the given data and returns its offset.
     * The block may not reference other blocks.
     * 
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * </ul>
     */
    public long writeNewBlock(Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        return writeNewBlock(area, new ByteArrayBlock(data));
    }

    /**
     * Write a new block directly to disk. The refcount of the new block is zero.
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    public <T extends BlockInstance<T>> long writeNewBlockDirect(Area area, T data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, null);
        writeBlock(off, area, data, true);
        return off;
    }

    /**
     * Writes a new block with the given raw byte data and returns its offset.
     * The block may not reference other blocks.
     */
    public long writeNewBlockDirect(Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        return writeNewBlockDirect(area, new ByteArrayBlock(data));
    }

    /**
     * Adds the given block which was used in the previous transaction and is no
     * longer referenced to the deferred free blocks list for the specified area.
     * 
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    public void addToDeferredFreeBlocks(long blockOffset, Area area) {
        // this block has been modified in a previous transaction
        var head = deferredFreeBlockHeads.get(area);
        int capacity = (blockSize(Area.META) - 2 * Long.BYTES) / Long.BYTES;
        if (head.blockOffsets.size() == capacity) {
            // the current freed block list block is full, allocate a new one
            writeDeferredFreeBlockListBlock(head);
            head = new DeferredFreeBlockListBlock(head.offset);
            deferredFreeBlockHeads.put(area, head);
        }
        head.blockOffsets.add(blockOffset);
    }

    private void writeDeferredFreeBlockListBlock(DeferredFreeBlockListBlock block) {
        if (block.offset == 0)
            block.offset = takeFreeBlockNoRefcount(Area.META);
        var buffer = ByteBuffer.allocate(blockSize(Area.META));
        buffer.putLong(block.next);
        buffer.putLong(block.blockOffsets.size());
        for (long blockOffset : block.blockOffsets)
            buffer.putLong(blockOffset);
        writeBlock(block.offset, Area.META, buffer.array());
    }

    /**
     * Adds the given block to the appropriate free space tree, merging with
     * adjacent free blocks if possible.
     */
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

    private void addDeferredFreeBlocksToFreeTree() {
        for (Area area : Area.values()) {
            long offset = rootBlock.getDeferredFreeListHead(area);
            rootBlock.setDeferredFreeListHead(area, 0);

            while (offset != 0) {
                // free the list block itself first
                addToDeferredFreeBlocks(offset, Area.META);

                var block = ByteBuffer.wrap(readBlock(offset, Area.META));
                var next = block.getLong();
                var count = block.getLong();
                for (int i = 0; i < count; i++) {
                    long freed = block.getLong();
                    addBlockToFreeTree(freed, area);
                }
                offset = next;
            }
        }
    }

    private void loadPendingMetaFreeTreeRemoveBlocks() {
        int capacity = (blockSize(Area.META) - 2 * Long.BYTES) / Long.BYTES;
        long offset = rootBlock.pendingMetaFreeTreeRemovalList.value;
        Set<Long> visited = new HashSet<>();
        while (offset != 0) {
            validateBlockOffset(offset, blockSize(Area.META), "deferred-meta-free-tree-remove list block");
            if (!visited.add(offset))
                throw new IllegalStateException("Cycle in deferred-meta-free-tree-remove list");
            PendingMetaFreeTreeRemoveListBlock block = PendingMetaFreeTreeRemoveListBlock.deserialize(
                    readBlock(offset, Area.META), capacity);

            addToDeferredFreeBlocks(offset, Area.META);
            for (long reserved : block.blockOffsets) {
                validateBlockOffset(reserved, blockSize(Area.META), "deferred-meta-free-tree-remove block");
                if (!pendingFreeTreeRemovals.get(Area.META).add(reserved))
                    throw new IllegalStateException("Duplicate deferred-meta-free-tree-remove block: " + reserved);
            }
            offset = block.next;
        }
    }

    private void loadDeferredRefCountUpdateList() {
        refCountUpdates.clear();

        long offset = rootBlock.refCountUpdateList.value;
        rootBlock.refCountUpdateList.value = 0;
        Set<Long> visited = new HashSet<>();
        Set<Long> updatedBlocks = new HashSet<>();
        while (offset != 0) {
            validateBlockOffset(offset, blockSize(Area.META), "deferred-ref-count-update list block");
            if (!visited.add(offset))
                throw new IllegalStateException("Cycle in deferred ref-count update list");

            addToDeferredFreeBlocks(offset, Area.META);

            ByteBuffer buffer = ByteBuffer.wrap(readBlock(offset, Area.META));
            long next = buffer.getLong();
            long count = buffer.getLong();

            for (int index = 0; index < count; index++) {
                long blockOffset = buffer.getLong();
                long countValue = buffer.getLong();
                Area area = areaFromCode(Byte.toUnsignedInt(buffer.get()));
                int blockTypeCode = Byte.toUnsignedInt(buffer.get());
                Optional<BlockType<?>> blockType = blockTypeCode == 0
                        ? Optional.empty()
                        : Optional.of(PersistableBlockType.fromCode(blockTypeCode).blockType.apply(this));

                validateBlockOffset(blockOffset, blockSize(area), "deferred ref-count update block");
                if (countValue < 0)
                    throw new IllegalStateException("Negative deferred ref-count for block " + blockOffset);
                if (!updatedBlocks.add(blockOffset))
                    throw new IllegalStateException("Duplicate deferred ref-count update for block " + blockOffset);
                if (countValue == 0 && blockType.isEmpty())
                    throw new IllegalStateException(
                            "Missing delete action for deferred ref-count update " + blockOffset);

                var update = refCountUpdates.getOrCreate(blockOffset, area);
                update.count = countValue;
                blockType.ifPresent(bt -> refCountUpdates.setBlockType(blockOffset, update, bt));
            }
            offset = next;
        }
    }

    static Area areaFromCode(int code) {
        return switch (code) {
            case 1 -> Area.SYSTEM;
            case 2 -> Area.META;
            case 3 -> Area.DATA;
            default -> throw new IllegalStateException("Invalid deferred ref-count area: " + code);
        };
    }

    /**
     * Extends the storage area by allocating additional blocks. Updates the
     * corresponding free tree.
     *
     * @see Mod#REFCOUNT_UPDATE_FREE_TREE (area ! = META), otherwise only meta free
     *      tree is modified
     * @see Mod#REFCOUNT_UPDATE_FREE_TREE_META can happen for all areas, as all free
     *      trees live in the META area, and requesting additional blocks may
     *      require extending the META area as well.
     */
    private void extend(Area area) {
        BTree<byte[], byte[]> tree = freeSpaceTrees.get(area);
        if (tree == null)
            throw new IllegalStateException("Free-space tree is not initialized for " + area);
        int blockSize = blockSize(area);
        try {
            long start = alignUp(file.length(), blockSize);
            long minimumGrowth = Math.multiplyExact((long) blockSize, 256);
            long growth = alignUp(Math.max(1L << 20, minimumGrowth), blockSize);
            long end = Math.addExact(start, growth);
            file.setLength(end);
            rootBlock.fileLength.value = end;

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

    /**
     * Removes a block from the free-space tree for the specified area.
     * Splits the free extent if necessary.
     * *
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    private void removeBlockFromFreeTree(Area area, long blockOffset) {
        BTree<byte[], byte[]> tree = freeSpaceTrees.get(area);
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

    /**
     * Writes all pending free tree removals from the free trees.
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE} (area ! = META), otherwise only
     * meta free tree is modified</li>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE_META} can happen in for all
     * areas, as all free trees live in the META area</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    void applyPendingFreeTreeRemovals() {
        // Work from a snapshot so recursive tree writes become pending for a later
        // sweep.
        for (Area area : Area.values()) {

            // Take a snapshot of the pending removals for this area.
            var pending = pendingFreeTreeRemovals.get(area);
            blocksBeingRemovedFromFreeTree.put(area, pending);
            pendingFreeTreeRemovals.put(area, new HashSet<>());

            // Remove each pending block from the free tree.
            for (long blockOffset : pending)
                removeBlockFromFreeTree(area, blockOffset);

            blocksBeingRemovedFromFreeTree.put(area, new HashSet<>());

            // Reinsert only after the batch is gone from the tree, avoiding recursive
            // updates.
            for (long blockOffset : putBackBlocksToFreeTree.get(area))
                addBlockToFreeTree(blockOffset, area);

            // free memory
            putBackBlocksToFreeTree.put(area, new HashSet<>());
        }
    }

    private void applyRefCountUpdates() {
        while (refCountUpdates.persistableCount() > 0 || refCountUpdates.nonPersistableCount() > 0) {
            // update the root block to ensure it reflects the latest block references
            updateAndWriteRootBlock();

            for (var entry : new ArrayList<>(refCountUpdates.entrySet())) {
                long blockOffset = entry.getKey();
                var update = entry.getValue();
                if (refCountUpdates.get(blockOffset) != update)
                    continue;
                long count = update.count;
                refCountBeingApplied = blockOffset;
                try {
                    if (count == 0)
                        refCountTree.remove(blockOffset);
                    else if (count > 0)
                        refCountTree.put(blockOffset, count);
                    else
                        throw new IllegalStateException("Reference count for " + update.area + " block " + blockOffset
                                + " went negative: " + count);
                    update.initialCount = count;
                } finally {
                    refCountBeingApplied = 0;
                }
                if (update.count != count)
                    continue;
                refCountUpdates.remove(blockOffset);
                if (count == 0) {
                    releaseReferencedBlocks(blockOffset, update.area, update.blockType());

                    if (modifiedBlocks.containsKey(blockOffset)) {
                        modifiedBlocks.remove(blockOffset);
                        boolean wasPending = pendingFreeTreeRemovals.get(update.area).remove(blockOffset);
                        boolean wasBeingRemoved = blocksBeingRemovedFromFreeTree.get(update.area).contains(blockOffset);
                        if (wasPending && wasBeingRemoved)
                            throw new IllegalStateException("Block is both pending and being removed: " + blockOffset);
                        if (wasBeingRemoved)
                            putBackBlocksToFreeTree.get(update.area).add(blockOffset);
                        else if (wasPending)
                            freeBlocks.get(update.area).add(blockOffset);
                        else
                            addBlockToFreeTree(blockOffset, update.area);
                    } else {
                        // This block was used in the previous transaction. Add it to the free space in
                        // the next transaction.
                        addToDeferredFreeBlocks(blockOffset, update.area);
                    }
                }
            }
        }
    }

    private void applyRefCountUpdatesAndRemovePendingFreeTreeRemovals() {
        // Bound recursive META-tree updates; persist any remaining META work for next
        // time.
        for (int round = 0; round < 2; round++) {
            applyRefCountUpdates();
            applyPendingFreeTreeRemovals();
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

    /**
     * @param blockAllocator Allocates blocks, returns true there were already
     *                       enough blocks allocated, false if new blocks were
     *                       allocated
     */
    private record DeferredPersistor(Supplier<Boolean> blockAllocator, Runnable persist) {
    }

    private <T> DeferredPersistor persistListInMeta(Supplier<Collection<T>> entries, int entrySize,
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
                    var offset = takeFreeBlockNoRefcount(Area.META);
                    modifiedBlocks.put(offset, null);
                    listOffsets.add(offset);
                    return false;
                },
                () -> {
                    Collection<T> records = entries.get();
                    if (records.isEmpty() && listOffsets.isEmpty()) {
                        setHead.accept(0L);
                        return;
                    }

                    int recordIndex = 0;
                    long next = 0;
                    var it = records.iterator();
                    for (int blockIndex = 0; blockIndex < listOffsets.size(); blockIndex++) {
                        byte[] bytes = new byte[blockSize(Area.META)];
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        int count = Math.min(capacity, records.size() - recordIndex);
                        buffer.putLong(next);
                        buffer.putLong(count);
                        for (int index = 0; index < count; index++)
                            persist.accept(buffer, it.next());
                        recordIndex += count;
                        long blockOffset = listOffsets.get(blockIndex);
                        writeBlock(blockOffset, Area.META, bytes);
                        next = blockOffset;
                    }
                    if (it.hasNext())
                        throw new IllegalStateException("Deferred META list entries exceed allocated blocks");
                    // each block links to its predecessor, so the last written block is the head
                    setHead.accept(next);
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

    private DeferredPersistor persistPendingMetaFreeTreeRemovalList() {
        return persistListInMeta(
                () -> pendingFreeTreeRemovals.get(Area.META),
                Long.BYTES,
                ByteBuffer::putLong, head -> rootBlock.pendingMetaFreeTreeRemovalList.value = head);
    }

    /**
     * write the deferred free block heads and update the root block with their
     * offsets
     * <ul>
     * <li>{@link Mod#REFCOUNT_UPDATE_FREE_TREE}</li>
     * <li>{@link Mod#ADD_PENDING_FREE_TREE_REMOVAL}</li>
     * <li>{@link Mod#REFCOUNT_UPDATE}</li>
     * </ul>
     */
    private DeferredPersistor writeDeferredFreeBlockHeads() {
        return new DeferredPersistor(() -> {
            var result = true;
            for (Area area : Area.values()) {
                var head = deferredFreeBlockHeads.get(area);
                if (head.offset == 0) {
                    head.offset = takeFreeBlockNoRefcount(Area.META);
                    result = false;
                }
            }
            return result;
        }, () -> {
            for (Area area : Area.values()) {
                var head = deferredFreeBlockHeads.get(area);
                writeDeferredFreeBlockListBlock(head);
                rootBlock.setDeferredFreeListHead(area, head.offset);
            }
        });
    }

    /**
     * Update the root block with the current state of handles and other references
     * <ul>
     * <li>{@link Mod#NONE}</li>
     * </ul>
     */
    private void flushRootBlockHandles() {
        // update root block ref handles
        {
            var ids = new ArrayList<>(rootBlock.refHandles.handleIds);
            var offsets = new ArrayList<>(rootBlock.refHandles.blockOffsets);
            for (var handle : rootRefHandles.values()) {
                int index = ids.indexOf(handle.id);
                if (index < 0) {
                    ids.add(handle.id);
                    offsets.add(handle.blockOffset);
                } else {
                    offsets.set(index, handle.blockOffset);
                }
            }
            rootBlock.refHandles.handleIds = ids;
            rootBlock.refHandles.blockOffsets = offsets;
        }

        // update long handles
        {
            var handles = rootLongHandles.values().stream().toList();
            rootBlock.longHandleIds.values = handles.stream().map(x -> x.id).toList();
            rootBlock.longHandles.values = handles.stream().map(x -> x.value).toList();
        }
    }

    private void updateAndWriteRootBlock() {
        updateRootBlock();
        writeBlock(superBlock.rootBlock, Area.SYSTEM, rootBlock);
    }

    private void updateRootBlock() {
        for (var entry : freeSpaceTrees.entrySet())
            rootBlock.setFreeTreeRoot(entry.getKey(), entry.getValue().rootOffset);
        rootBlock.refCountTree.blockOffset = refCountTree.rootOffset;
    }

    public void commit() {
        beforeCommit.trigger(null);
        flushRootBlockHandles();

        // do an initial write of the root block, ensuring that all ref counts are
        // updated and that the corresponding block is allocated.
        {
            updateRootBlock();
            long previousRootOffset = superBlock.rootBlock;
            superBlock.rootBlock = writeNewBlock(Area.SYSTEM, rootBlock);
            incrementRefCount(new BlockRef(superBlock.rootBlock, Area.SYSTEM, RootBlock.getBlockType(this)));
            decrementRefCount(new BlockRef(previousRootOffset, Area.SYSTEM, RootBlock.getBlockType(this)));
        }

        applyRefCountUpdatesAndRemovePendingFreeTreeRemovals();

        // Now all the heavy lifting is completed. We'll only touch the meta free tree
        // when allocating space (when extending the meta area) to persist the deferred
        // lists
        var persistors = new ArrayList<DeferredPersistor>();
        persistors.add(persistPendingMetaFreeTreeRemovalList());
        persistors.add(writeDeferredFreeBlockHeads());
        rootBlock.refCountUpdateList.value = 0;
        while (true) {
            updateAndWriteRootBlock();
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
        updateAndWriteRootBlock();

        // perform the commit
        flushModified();
        writeSuperBlock();

        // prepare next transaction
        // ------------------------

        // clear the modified blocks
        modifiedBlocks.clear();

        // start with fresh free blocks
        Stream.of(Area.values()).forEach(area -> freeBlocks.put(area, new TreeSet<>()));
        freeSpaceAllocationOffset.clear();

        // switch to new deferred free block heads, such that freeing blocks of the last
        // transaction will use the new heads
        initializeDeferredFreeBlockHeads();

        // add deferred free blocks to the free tree and initialize the deferred free
        // block heads
        addDeferredFreeBlocksToFreeTree();

        // add blocks of the pending meta free tree removal list and the ref count
        // update list to the deferred free blocks
        // important: needs to be done after adding deferred free blocks of the last
        // transaction to the free tree, otherwise they would be freed right away
        addListBlocksToDeferredFreeBlocks(rootBlock.pendingMetaFreeTreeRemovalList.value, Area.META);
        addListBlocksToDeferredFreeBlocks(rootBlock.refCountUpdateList.value, Area.META);

    }

    private void addListBlocksToDeferredFreeBlocks(long offset, Area area) {
        while (offset != 0) {
            var block = ByteBuffer.wrap(readBlock(offset, area));
            addToDeferredFreeBlocks(offset, area);

            // read the offset of the next block
            offset = block.getLong();
        }
    }

    private void flushModified() {
        for (var entry : modifiedBlocks.entrySet()) {
            if (entry.getValue() != null && entry.getValue().data() != null)
                writeToDisk(entry.getKey(), entry.getValue().data());
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

    public long getRefPersistedCount(long blockOffset) {
        var value = refCountTree.get(blockOffset);
        return value == null ? 0 : value;
    }
}