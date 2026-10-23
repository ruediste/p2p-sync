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

import com.github.ruediste.p2psync.storage.BlockType.BlockInstance;
import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.storage.BlockType.LongSlot;
import com.github.ruediste.p2psync.storage.BlockType.LongsSlot;
import com.github.ruediste.p2psync.storage.BlockType.RefSlot;
import com.github.ruediste.p2psync.storage.BlockType.RefsSlot;
import com.github.ruediste.p2psync.storage.BlockType.SlotBlock;
import com.github.ruediste.p2psync.storage.BlockType.SlotBlockType;
import com.github.ruediste.p2psync.storage.BlockType.UInt16sSlot;
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

    static final BlockType<ByteArrayBlock> BYTE_ARRAY_BLOCK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(ByteArrayBlock value) {
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

    static class ByteArrayBlock implements BlockInstance<ByteArrayBlock> {
        final byte[] data;

        ByteArrayBlock(byte[] data) {
            this.data = data.clone();
        }

        @Override
        public BlockType<ByteArrayBlock> getBlockType() {
            return BYTE_ARRAY_BLOCK_TYPE;
        }
    }

    private static class DeferredListBlock implements BlockInstance<DeferredListBlock> {
        final byte[] data;
        private final BlockType<DeferredListBlock> type;

        DeferredListBlock(byte[] data, BlockType<DeferredListBlock> type) {
            this.data = data.clone();
            this.type = type;
        }

        @Override
        public BlockType<DeferredListBlock> getBlockType() {
            return type;
        }
    }

    private static class DeferredFreeBlockListBlock implements BlockInstance<DeferredFreeBlockListBlock> {
        long next;
        final List<Long> blockOffsets = new ArrayList<>();

        DeferredFreeBlockListBlock(long next) {
            this.next = next;
        }

        @Override
        public BlockType<DeferredFreeBlockListBlock> getBlockType() {
            return DEFERRED_FREE_BLOCK_LIST_TYPE;
        }
    }

    private static final BlockType<DeferredFreeBlockListBlock> DEFERRED_FREE_BLOCK_LIST_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(DeferredFreeBlockListBlock value) {
            return value.next == 0 ? List.of() : List.of(new BlockRef(value.next, Area.SYSTEM, this));
        }

        @Override
        public ByteBuffer serialize(DeferredFreeBlockListBlock value) {
            ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES + value.blockOffsets.size() * Long.BYTES);
            buffer.putLong(value.next);
            buffer.putLong(value.blockOffsets.size());
            value.blockOffsets.forEach(buffer::putLong);
            return buffer.flip();
        }

        @Override
        public DeferredFreeBlockListBlock deserialize(ByteBuffer buffer) {
            requireDeferredListHeader(buffer);
            long next = buffer.getLong();
            long count = buffer.getLong();
            if (count < 0 || count > buffer.remaining() / Long.BYTES)
                throw new IllegalArgumentException(
                        "Invalid deferred-free list count: " + count + " (next block: " + next + ")");
            var result = new DeferredFreeBlockListBlock(next);
            for (int index = 0; index < count; index++)
                result.blockOffsets.add(buffer.getLong());
            return result;
        }
    };

    private static final BlockType<DeferredListBlock> DEFERRED_META_FREE_TREE_REMOVE_BLOCK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(DeferredListBlock value) {
            ByteBuffer buffer = ByteBuffer.wrap(value.data);
            requireDeferredListHeader(buffer);
            long next = buffer.getLong();
            long count = buffer.getLong();
            if (count < 0 || count > buffer.remaining() / Long.BYTES)
                throw new IllegalArgumentException("Invalid deferred META free-tree list count: " + count);
            List<BlockRef> references = new ArrayList<>((int) count + 1);
            if (next != 0)
                references.add(new BlockRef(next, Area.META, this));
            BlockType<?> treeType = BTree.blockType(BLOCK_OFFSET_SIZE, Area.META);
            for (int index = 0; index < count; index++) {
                long blockOffset = buffer.getLong();
                if (blockOffset != 0)
                    references.add(new BlockRef(blockOffset, Area.META, treeType));
            }
            return references;
        }

        @Override
        public ByteBuffer serialize(DeferredListBlock value) {
            return ByteBuffer.wrap(value.data);
        }

        @Override
        public DeferredListBlock deserialize(ByteBuffer buffer) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return new DeferredListBlock(data, this);
        }
    };

    private static final BlockType<DeferredListBlock> DEFERRED_REF_COUNT_ADJUSTMENT_BLOCK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(DeferredListBlock value) {
            ByteBuffer buffer = ByteBuffer.wrap(value.data);
            requireDeferredListHeader(buffer);
            long next = buffer.getLong();
            long count = buffer.getLong();
            int recordSize = Long.BYTES + Integer.BYTES + 2;
            if (count < 0 || count > buffer.remaining() / recordSize)
                throw new IllegalArgumentException("Invalid deferred refcount list count: " + count);
            return next == 0 ? List.of() : List.of(new BlockRef(next, Area.META, this));
        }

        @Override
        public ByteBuffer serialize(DeferredListBlock value) {
            return ByteBuffer.wrap(value.data);
        }

        @Override
        public DeferredListBlock deserialize(ByteBuffer buffer) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return new DeferredListBlock(data, this);
        }
    };

    private static void requireDeferredListHeader(ByteBuffer buffer) {
        if (buffer.remaining() < 2 * Long.BYTES)
            throw new IllegalArgumentException("Truncated deferred list block");
    }

    final class BTreeStorage implements BTree.Storage {
        private final Area area;

        BTreeStorage(Area area) {
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
        public boolean isBlockModified(long blockOffset) {
            return DataStorage.this.isBlockModified(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, BTree.NodeBlock data) {
            DataStorage.this.writeBlock(blockOffset, area, data);
        }

        @Override
        public long writeNewBlock(BTree.NodeBlock data) {
            return DataStorage.this.writeNewBlock(area, data);
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

    public enum PersistableBlockType {
        META_BTREE(1, BTree.blockType(BLOCK_OFFSET_SIZE, Area.META)),
        DEFERRED_META_FREE_TREE_REMOVE_LIST(2, DEFERRED_META_FREE_TREE_REMOVE_BLOCK_TYPE),
        DEFERRED_REF_COUNT_ADJUSTMENT_LIST(3, DEFERRED_REF_COUNT_ADJUSTMENT_BLOCK_TYPE),
        INFO_BLOCK(4, InfoBlock.TYPE),
        BYTE_ARRAY_BLOCK(5, BYTE_ARRAY_BLOCK_TYPE),
        DEFERRED_FREE_BLOCK_LIST(6, DEFERRED_FREE_BLOCK_LIST_TYPE);

        public final int code;
        public final BlockType<?> blockType;

        private PersistableBlockType(int code, BlockType<?> blockType) {
            this.code = code;
            this.blockType = blockType;
        }

        private static final Map<BlockType<?>, PersistableBlockType> byType = new HashMap<>();
        private static final Map<Integer, PersistableBlockType> byCode = new HashMap<>();
        static {
            for (PersistableBlockType type : PersistableBlockType.values()) {
                byType.put(type.blockType, type);
                byCode.put(type.code, type);
            }
        }

        public static Optional<PersistableBlockType> fromBlockType(BlockType<?> blockType) {
            return Optional.ofNullable(byType.get(blockType));
        }

        static PersistableBlockType fromCode(int code) {
            var result = byCode.get(code);
            if (result == null || result.blockType == null)
                throw new IllegalStateException("Unknown persistable block type: " + code);
            return result;
        }
    }

    public static class RootBlock extends SlotBlock<RootBlock> {
        static final SlotBlockType<RootBlock> TYPE = new SlotBlockType<>(RootBlock::new);
        private static final BlockType<?> META_TREE_TYPE = BTree.blockType(BLOCK_OFFSET_SIZE, Area.META);

        public class RootRefsSlot extends RefsSlot {
            public List<Integer> handleIds = new ArrayList<>();
            private Map<Integer, RootRefHandle<?>> handles = Map.of();

            @Override
            List<BlockRef> getReferencedBlocks() {
                List<BlockRef> result = new ArrayList<>();
                for (int index = 0; index < blockOffsets.size(); index++) {
                    long offset = blockOffsets.get(index);
                    if (offset == 0)
                        continue;
                    RootRefHandle<?> handle = handles.get(handleIds.get(index));
                    if (handle != null) {
                        result.add(new BlockRef(offset, handle.area, handle.blockType));
                    } else {
                        int target = refHandleTargets.values.get(index);
                        result.add(new BlockRef(offset, areaFromCode(target >>> 8),
                                PersistableBlockType.fromCode(target & 0xFF).blockType));
                    }
                }
                return result;
            }

            @Override
            void serialize(ByteBuffer buffer) {
                if (handleIds.size() != blockOffsets.size())
                    throw new IllegalStateException("Root reference handle IDs and offsets differ in size");
                if (handleIds.size() > 0xFFFF)
                    throw new IllegalArgumentException("Root reference handle list exceeds unsigned 16-bit length");
                buffer.putShort((short) handleIds.size());
                for (int id : handleIds)
                    buffer.putShort((short) id);
                super.serialize(buffer);
            }

            @Override
            int serializedSize() {
                if (handleIds.size() != blockOffsets.size())
                    throw new IllegalStateException("Root reference handle IDs and offsets differ in size");
                return Short.BYTES + handleIds.size() * Short.BYTES + super.serializedSize();
            }

            @Override
            void deserialize(ByteBuffer buffer) {
                int size = Short.toUnsignedInt(buffer.getShort());
                if (size > buffer.remaining() / Short.BYTES)
                    throw new IllegalArgumentException("Invalid root reference handle ID list length: " + size);
                handleIds = new ArrayList<>(size);
                for (int index = 0; index < size; index++)
                    handleIds.add(Short.toUnsignedInt(buffer.getShort()));
                super.deserialize(buffer);
                if (handleIds.size() != blockOffsets.size())
                    throw new IllegalArgumentException("Root reference handle IDs and offsets differ in size");
            }
        }

        public final RefSlot dataFreeSpaceTree = refSlot(Area.META, META_TREE_TYPE);
        public final RefSlot metaFreeSpaceTree = refSlot(Area.META, META_TREE_TYPE);
        public final RefSlot systemFreeSpaceTree = refSlot(Area.META, META_TREE_TYPE);
        public final RefSlot refCountTree = refSlot(Area.META, META_TREE_TYPE);
        public final RefSlot infoBlock = refSlot(Area.SYSTEM, InfoBlock.TYPE);
        public final LongSlot fileLength = longSlot();
        public final RefSlot systemDeferredFreeBlockList = refSlot(Area.SYSTEM, DEFERRED_FREE_BLOCK_LIST_TYPE);
        public final RefSlot metaDeferredFreeBlockList = refSlot(Area.SYSTEM, DEFERRED_FREE_BLOCK_LIST_TYPE);
        public final RefSlot dataDeferredFreeBlockList = refSlot(Area.SYSTEM, DEFERRED_FREE_BLOCK_LIST_TYPE);
        public final RefSlot deferredMetaFreeTreeRemoveList = refSlot(Area.META,
                DEFERRED_META_FREE_TREE_REMOVE_BLOCK_TYPE);
        public final RefSlot deferredRefCountAdjustmentList = refSlot(Area.META,
                DEFERRED_REF_COUNT_ADJUSTMENT_BLOCK_TYPE);

        public final RootRefsSlot refHandles = rootRefsSlot();

        public final UInt16sSlot longHandleIds = uint16sSlot();
        public final LongsSlot longHandles = longsSlot();
        public final UInt16sSlot refHandleTargets = uint16sSlot();

        private RootRefsSlot rootRefsSlot() {
            var slot = new RootRefsSlot();
            slots.add(slot);
            return slot;
        }

        private void setRootRefHandles(Map<Integer, RootRefHandle<?>> handles) {
            refHandles.handles = handles;
        }

        @Override
        public BlockType<RootBlock> getBlockType() {
            return TYPE;
        }
    }

    private HashMap<Integer, RootRefHandle<?>> rootRefHandles = new HashMap<>();

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

    private HashMap<Integer, RootLongHandle> rootLongHandles = new HashMap<>();

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
        if (rootBlock != null)
            restoreRootRefHandle(handle);
        rootRefHandles.put(id, handle);
        return handle;
    }

    private void restoreRootRefHandle(RootRefHandle<?> handle) {
        int index = rootBlock.refHandles.handleIds.indexOf(handle.id);
        handle.blockOffset = index < 0 ? 0 : rootBlock.refHandles.blockOffsets.get(index);
        if (handle.blockOffset != 0) {
            int target = rootBlock.refHandleTargets.values.get(index);
            if (areaFromCode(target >>> 8) != handle.area
                    || PersistableBlockType.fromCode(target & 0xFF).blockType != handle.blockType)
                throw new IllegalArgumentException("RootRefHandle target type or area differs for id " + handle.id);
        }
    }

    public static class InfoBlock implements BlockInstance<InfoBlock> {
        static final BlockType<InfoBlock> TYPE = new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(InfoBlock value) {
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

    static class RefCountAdjustment {
        /**
         * If true, the underlying ref count is 0 and the adjustment represents the
         * total final ref count.
         */
        boolean equalsCount;
        int adjustment;
        Optional<BlockType<?>> blockType = Optional.empty();

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
    EnumMap<Area, DeferredFreeBlockListBlock> deferredFreeBlocks = new EnumMap<>(Area.class);

    private void initializeDeferredFreeBlocks() {
        deferredFreeBlocks.clear();
        for (Area area : Area.values()) {
            deferredFreeBlocks.put(area, new DeferredFreeBlockListBlock(0));
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

    record ModifiedBlock(byte[] data, List<BlockRef> references) {
    }

    /**
     * Write-time snapshots of modified blocks. Direct writes retain references
     * with null data; reserved blocks have a null entry until written.
     */
    final HashMap<Long, ModifiedBlock> modifiedBlocks = new HashMap<>();

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
        rootRefHandles.values().forEach(handle -> handle.blockOffset = 0);
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

    public void incrementRefCount(BlockRef ref) {
        incrementRefCount(ref.blockOffset(), ref.area());
    }

    public void incrementRefCount(long blockOffset, Area area) {
        if (blockOffset == 0)
            return;
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> RefCountAdjustment.create(area));
        adjustment.adjustment += 1;
        if (adjustment.adjustment == 0 && !adjustment.equalsCount)
            refCountAdjustments.remove(blockOffset);
    }

    public void decrementRefCount(BlockRef ref) {
        if (ref.blockOffset() == 0)
            return;

        var adjustment = refCountAdjustments.computeIfAbsent(ref.blockOffset(),
                k -> RefCountAdjustment.create(ref.area()));

        adjustment.adjustment--;

        if (adjustment.adjustment == 0) {
            // check if this is a block that was newly allocated in this transaction and the
            // count reached zero. If so, remove it.
            if (adjustment.equalsCount && modifiedBlocks.containsKey(ref.blockOffset())) {
                releaseReferencedBlocks(ref.blockOffset(), ref.area(), ref.type());
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
            }
            refCountAdjustments.remove(ref.blockOffset());
        } else if (adjustment.adjustment < 0) {
            if (adjustment.blockType.isEmpty())
                adjustment.blockType = Optional.of(ref.type());
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void releaseReferencedBlocks(long blockOffset, Area area, BlockType<?> type) {
        var existing = modifiedBlocks.get(blockOffset);
        if (existing != null) {
            existing.references().forEach(this::decrementRefCount);
            return;
        }
        BlockType rawType = type;
        Object value = rawType.deserialize(ByteBuffer.wrap(readFromDisk(blockOffset, blockSize(area))));
        for (Object reference : rawType.getReferencedBlocks(value))
            decrementRefCount((BlockRef) reference);
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
            loadDeferredMetaFreeTreeRemoveBlocks();
            loadDeferredRefCountAdjustmentList();
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
        rootBlock.setRootRefHandles(rootRefHandles);

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
            var tree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE, (key, value) -> {
            });
            tree.put(u64(systemStart), u64(systemBytes));
            freeSpaceTrees.put(Area.SYSTEM, tree);
        }
        {
            var tree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE,
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

        refCountTree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE, null);

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
        rootBlock = readBlock(superBlock.rootBlock, Area.SYSTEM, RootBlock.TYPE);
        rootBlock.setRootRefHandles(rootRefHandles);
        rootRefHandles.values().forEach(this::restoreRootRefHandle);
    }

    private RootBlock copyRootBlock(RootBlock value) {
        RootBlock result = RootBlock.TYPE.deserialize(RootBlock.TYPE.serialize(value));
        result.setRootRefHandles(rootRefHandles);
        return result;
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
            BTree.Storage storage = new BTreeStorage(Area.META);
            BTree tree = new BTree(storage, BLOCK_OFFSET_SIZE, null, getFreeTreeRoot(area));
            freeSpaceTrees.put(area, tree);
        }
        refCountTree = new BTree(new BTreeStorage(Area.META), BLOCK_OFFSET_SIZE, null,
                rootBlock.refCountTree.blockOffset);
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
        adjustment.adjustment = 0;
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
            case SYSTEM -> rootBlock.systemFreeSpaceTree.blockOffset;
            case META -> rootBlock.metaFreeSpaceTree.blockOffset;
            case DATA -> rootBlock.dataFreeSpaceTree.blockOffset;
        };
    }

    private void setFreeTreeRoot(Area area, long root) {
        switch (area) {
            case SYSTEM -> rootBlock.systemFreeSpaceTree.blockOffset = root;
            case META -> rootBlock.metaFreeSpaceTree.blockOffset = root;
            case DATA -> rootBlock.dataFreeSpaceTree.blockOffset = root;
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
        var existing = modifiedBlocks.get(blockOffset);
        if (existing != null && existing.data() != null)
            return existing.data().clone();
        return readFromDisk(blockOffset, blockSize(area));
    }

    public <T extends BlockInstance<T>> T readBlock(long blockOffset, Area area, BlockType<T> type) {
        return type.deserialize(ByteBuffer.wrap(readBlock(blockOffset, area)));
    }

    public void writeBlock(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        writeBlock(blockOffset, area, new ByteArrayBlock(data));
    }

    public boolean isBlockModified(long blockOffset) {
        return modifiedBlocks.containsKey(blockOffset);
    }

    public <T extends BlockInstance<T>> void writeBlock(long blockOffset, Area area, T data) {
        storeModifiedBlock(blockOffset, area, data, false);
    }

    public void writeBlockDirect(long blockOffset, Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        storeModifiedBlock(blockOffset, area, new ByteArrayBlock(data), true);
    }

    private <T extends BlockInstance<T>> void storeModifiedBlock(long blockOffset, Area area, T data, boolean direct) {
        if (!modifiedBlocks.containsKey(blockOffset))
            throw new IllegalStateException("Block " + blockOffset + " has not been allocated in this transaction");
        byte[] bytes = serializeToBlock(data, area);
        var type = data.getBlockType();
        List<BlockRef> references = List.copyOf(type.getReferencedBlocks(data));
        references.forEach(this::incrementRefCount);
        var existing = modifiedBlocks.put(blockOffset, new ModifiedBlock(direct ? null : bytes, references));
        if (existing != null)
            existing.references().forEach(this::decrementRefCount);
        var adjustment = refCountAdjustments.get(blockOffset);
        if (adjustment != null)
            adjustment.blockType = Optional.of(type);
        if (direct)
            writeToDisk(blockOffset, bytes);
    }

    public <T extends BlockInstance<T>> long writeNewBlock(Area area, T data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, null);
        storeModifiedBlock(off, area, data, false);
        return off;
    }

    /**
     * Writes a new block with the given data and returns its offset.
     * The block may not reference other blocks.
     */
    public long writeNewBlock(Area area, byte[] data) {
        if (data.length != blockSize(area))
            throw new IllegalArgumentException(
                    "block size mismatch: expected " + blockSize(area) + " got " + data.length);
        return writeNewBlock(area, new ByteArrayBlock(data));
    }

    public <T extends BlockInstance<T>> long writeNewBlockDirect(Area area, T data) {
        long off = takeFreeBlock(area);
        modifiedBlocks.put(off, null);
        storeModifiedBlock(off, area, data, true);
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

    private <T extends BlockInstance<T>> byte[] serializeToBlock(T data, Area area) {
        return serializeToBlock(data.getBlockType().serialize(data), area);
    }

    private byte[] serializeToBlock(ByteBuffer serialized, Area area) {
        if (serialized.remaining() > blockSize(area))
            throw new IllegalArgumentException("serialized block exceeds " + area + " block size");
        byte[] result = new byte[blockSize(area)];
        serialized.get(result, 0, serialized.remaining());
        return result;
    }

    public void addToDeferredFreeBlocks(long blockOffset, Area area) {
        // this block has been modified in a previous transaction
        var list = deferredFreeBlocks.get(area);
        int capacity = (blockSize(Area.SYSTEM) - 2 * Long.BYTES) / Long.BYTES;
        if (list.blockOffsets.size() == capacity) {
            // the current freed block list block is full, allocate a new one
            long newBlock = writeNewBlock(Area.SYSTEM, list);
            list = new DeferredFreeBlockListBlock(newBlock);
            deferredFreeBlocks.put(area, list);
        }
        list.blockOffsets.add(blockOffset);
    }

    private void persistDeferredFreeBlockHeads() {
        for (Area area : Area.values()) {
            var list = deferredFreeBlocks.get(area);
            long head = list.blockOffsets.isEmpty() ? list.next : writeNewBlock(Area.SYSTEM, list);
            setDeferredFreeListHead(area, head);
            deferredFreeBlocks.put(area, new DeferredFreeBlockListBlock(0));
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
            case SYSTEM -> rootBlock.systemDeferredFreeBlockList.blockOffset;
            case META -> rootBlock.metaDeferredFreeBlockList.blockOffset;
            case DATA -> rootBlock.dataDeferredFreeBlockList.blockOffset;
        };
    }

    private void setDeferredFreeListHead(Area area, long head) {
        switch (area) {
            case SYSTEM -> rootBlock.systemDeferredFreeBlockList.blockOffset = head;
            case META -> rootBlock.metaDeferredFreeBlockList.blockOffset = head;
            case DATA -> rootBlock.dataDeferredFreeBlockList.blockOffset = head;
        }
    }

    private void addDeferredFreeBlocksToFreeTree() {
        for (Area area : Area.values()) {
            long offset = getDeferredFreeListHead(area);
            setDeferredFreeListHead(area, 0);
            Set<Long> visited = new HashSet<>();
            while (offset != 0) {
                validateBlockOffset(offset, blockSize(Area.SYSTEM), "deferred-free list block");
                if (!visited.add(offset))
                    throw new IllegalStateException("Cycle in deferred-free list for " + area);
                DeferredFreeBlockListBlock block;
                try {
                    block = readBlock(offset, Area.SYSTEM, DEFERRED_FREE_BLOCK_LIST_TYPE);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Invalid deferred-free list block at " + offset + " for " + area, e);
                }
                for (long freed : block.blockOffsets) {
                    validateBlockOffset(freed, blockSize(area), "deferred-free block");
                    addBlockToFreeTree(freed, area);
                }
                offset = block.next;
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
        long offset = rootBlock.deferredMetaFreeTreeRemoveList.blockOffset;
        rootBlock.deferredMetaFreeTreeRemoveList.blockOffset = 0;
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

        long offset = rootBlock.deferredRefCountAdjustmentList.blockOffset;
        rootBlock.deferredRefCountAdjustmentList.blockOffset = 0;
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
                Optional<BlockType<?>> blockType = actionCode == 0
                        ? Optional.empty()
                        : Optional.of(PersistableBlockType.fromCode(actionCode).blockType);

                validateBlockOffset(blockOffset, blockSize(area), "deferred ref-count adjustment block");
                if (adjustmentValue == 0)
                    throw new IllegalStateException("Zero deferred ref-count adjustment for block " + blockOffset);
                if (!adjustedBlocks.add(blockOffset))
                    throw new IllegalStateException("Duplicate deferred ref-count adjustment for block " + blockOffset);
                if (adjustmentValue < 0 && blockType.isEmpty())
                    throw new IllegalStateException(
                            "Missing delete action for deferred ref-count adjustment " + blockOffset);

                var adjustment = new RefCountAdjustment(area);
                adjustment.blockType = blockType;
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
        freeUnreferencedAllocations();
        var entries = refCountAdjustments.entrySet();
        refCountAdjustments = new HashMap<>();

        for (var e : entries) {
            long blockOffset = e.getKey();
            var delta = e.getValue();
            int adjustment = delta.adjustment;
            if (adjustment == 0) {
                refCountAdjustments.put(blockOffset, delta);
                continue;
            }

            var existingCount = refCountTree.get(u64(blockOffset));
            var count = existingCount == null ? 0 : toLong(existingCount);
            count += adjustment;
            if (count == 0) {
                if (existingCount != null)
                    refCountTree.remove(u64(blockOffset));

                if (delta.blockType.isEmpty()) {
                    throw new IllegalStateException(
                            "No block type specified for " + delta.area + " block " + blockOffset);
                }
                delta.blockType.ifPresent(type -> releaseReferencedBlocks(blockOffset, delta.area, type));

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

    private void updateRootBlockTreeReferences() {
        for (var entry : freeSpaceTrees.entrySet())
            setFreeTreeRoot(entry.getKey(), entry.getValue().rootOffset);
        rootBlock.refCountTree.blockOffset = refCountTree.rootOffset;
    }

    private void freeUnreferencedAllocations() {
        updateRootBlockTreeReferences();

        Set<Long> roots = new HashSet<>();
        for (BlockRef reference : RootBlock.TYPE.getReferencedBlocks(rootBlock))
            roots.add(reference.blockOffset());
        for (var entry : new ArrayList<>(refCountAdjustments.entrySet())) {
            long offset = entry.getKey();
            var adjustment = refCountAdjustments.get(offset);
            if (adjustment != null && adjustment.equalsCount && adjustment.adjustment == 0
                    && !roots.contains(offset)) {
                incrementRefCount(offset, adjustment.area);
                decrementRefCount(new BlockRef(offset, adjustment.area, adjustment.blockType.orElseThrow()));
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

        // Drain non-META free-tree work, leaving recursive META updates for
        // persistence.
        while (true) {
            applyRefCountAdjustments();
            removePendingFreeTreeRemovals(false);
            boolean hasNonMetaPendingRemovals = pendingFreeTreeRemovals.get(Area.SYSTEM).size() > 0
                    || pendingFreeTreeRemovals.get(Area.DATA).size() > 0;
            if (!hasNonMetaPendingRemovals)
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

    private <T> DeferredPersistor persistDeferredMetaList(BlockType<DeferredListBlock> blockType,
            Supplier<List<T>> entries, int entrySize,
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
                    modifiedBlocks.put(offset, null);
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
                        long blockOffset = listOffsets.get(blockIndex);
                        storeModifiedBlock(blockOffset, Area.META, new DeferredListBlock(bytes, blockType), false);
                        writeToDisk(blockOffset, modifiedBlocks.get(blockOffset).data());
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
                DEFERRED_META_FREE_TREE_REMOVE_BLOCK_TYPE,
                () -> new ArrayList<>(pendingFreeTreeRemovals.get(Area.META)), Long.BYTES,
                ByteBuffer::putLong, head -> rootBlock.deferredMetaFreeTreeRemoveList.blockOffset = head);
    }

    private DeferredPersistor persistRefCountAdjustmentList() {
        return persistDeferredMetaList(
                DEFERRED_REF_COUNT_ADJUSTMENT_BLOCK_TYPE,
                () -> refCountAdjustments.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList(),
                // blockOffset + adjustment + area + action
                Long.BYTES + Integer.BYTES + 2,
                (buffer, entry) -> {
                    long blockOffset = entry.getKey();
                    RefCountAdjustment adjustment = entry.getValue();
                    int actionCode = 0;
                    if (adjustment.blockType.isPresent()) {
                        var persistableType = PersistableBlockType.fromBlockType(adjustment.blockType.get())
                                .orElseThrow(() -> new IllegalStateException(
                                        "Block type is not registered for deferred refcount updates: "
                                                + adjustment.blockType.get()));
                        actionCode = persistableType.code;
                    }
                    if (adjustment.adjustment < 0 && actionCode == 0)
                        throw new IllegalStateException(
                                "Missing durable delete action for block " + blockOffset);
                    buffer.putLong(blockOffset);
                    buffer.putInt(adjustment.adjustment);
                    buffer.put((byte) areaCode(adjustment.area));
                    buffer.put((byte) actionCode);
                }, head -> rootBlock.deferredRefCountAdjustmentList.blockOffset = head);
    }

    private void updateRootHandles() {
        {
            var ids = new ArrayList<>(rootBlock.refHandles.handleIds);
            var offsets = new ArrayList<>(rootBlock.refHandles.blockOffsets);
            var targets = new ArrayList<>(rootBlock.refHandleTargets.values);
            while (targets.size() < ids.size())
                targets.add(0);
            for (var handle : rootRefHandles.values()) {
                int target = PersistableBlockType.fromBlockType(handle.blockType)
                        .map(type -> areaCode(handle.area) << 8 | type.code)
                        .orElseGet(() -> {
                            if (handle.blockOffset != 0)
                                throw new IllegalStateException(
                                        "Unregistered root handle block type for id " + handle.id);
                            return 0;
                        });
                int index = ids.indexOf(handle.id);
                if (index < 0) {
                    ids.add(handle.id);
                    offsets.add(handle.blockOffset);
                    targets.add(target);
                } else {
                    offsets.set(index, handle.blockOffset);
                    targets.set(index, target);
                }
            }
            rootBlock.refHandles.handleIds = ids;
            rootBlock.refHandles.blockOffsets = offsets;
            rootBlock.refHandleTargets.values = targets;
        }
        {
            var handles = rootLongHandles.values().stream().toList();
            rootBlock.longHandleIds.values = handles.stream().map(x -> x.id).toList();
            rootBlock.longHandles.values = handles.stream().map(x -> x.value).toList();
        }
    }

    public final Event<Void> beforeCommit = new Event<>();

    public void commit() {
        beforeCommit.trigger(null);
        updateRootHandles();
        persistDeferredFreeBlockHeads();
        for (Area area : Area.values())
            deferredFreeBlocks.put(area, new DeferredFreeBlockListBlock(getDeferredFreeListHead(area)));
        updateRootBlockTreeReferences();
        long previousRootOffset = superBlock.rootBlock;
        superBlock.rootBlock = writeNewBlock(Area.SYSTEM, copyRootBlock(rootBlock));
        incrementRefCount(superBlock.rootBlock, Area.SYSTEM);
        decrementRefCount(new BlockRef(previousRootOffset, Area.SYSTEM, RootBlock.TYPE));
        applyAndRemovePendingFreeTreeRemovals();

        persistDeferredFreeBlockHeads();
        updateRootBlockTreeReferences();
        writeBlock(superBlock.rootBlock, Area.SYSTEM, copyRootBlock(rootBlock));
        applyAndRemovePendingFreeTreeRemovals();

        // store deferred meta free tree removals and ref count updates
        var persistors = new ArrayList<DeferredPersistor>();
        persistors.add(persistDeferredMetaFreeTreeRemoveBlockList());
        var refCountPersistor = persistRefCountAdjustmentList();
        persistors.add(refCountPersistor);
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
        updateRootBlockTreeReferences();
        writeBlock(superBlock.rootBlock, Area.SYSTEM, copyRootBlock(rootBlock));
        for (var adjustment : refCountAdjustments.values()) {
            if (adjustment.adjustment == 0)
                throw new IllegalStateException("Unreferenced allocation remains at commit");
        }
        refCountPersistor.persist.run();

        // perform the commit
        flushModified();
        writeSuperBlock();

        // prepare next transaction
        rootBlock.deferredRefCountAdjustmentList.blockOffset = 0;
        rootBlock.deferredMetaFreeTreeRemoveList.blockOffset = 0;
        addDeferredFreeBlocksToFreeTree();
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