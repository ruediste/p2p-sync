package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.storage.DataStorage.Area;
import com.github.ruediste.p2psync.storage.DataStorage.InfoBlock;
import com.github.ruediste.p2psync.storage.DataStorage.RootRefHandle;

public class RootBlock extends SlotBlock<RootBlock> {
    private BlockType<RootBlock> type;

    public class RootRefsSlot extends SlotBlock.RefsSlot {
        public List<Integer> handleIds = new ArrayList<>();

        @Override
        List<BlockRef> getReferencedBlocks(DataStorage storage) {
            List<BlockRef> result = new ArrayList<>();
            for (int index = 0; index < blockOffsets.size(); index++) {
                long offset = blockOffsets.get(index);
                if (offset == 0)
                    continue;
                RootRefHandle<?> handle = storage.rootRefHandles.get(handleIds.get(index));
                if (handle == null)
                    continue;
                result.add(new BlockRef(offset, handle.area, handle.blockType));
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

    public RootBlock(DataStorage storage) {
        this.type = getBlockType(storage);
    }

    public final RefSlot dataFreeSpaceTree = exclusiveTreeSlot();
    public final RefSlot metaFreeSpaceTree = exclusiveTreeSlot();
    public final RefSlot systemFreeSpaceTree = exclusiveTreeSlot();
    public final RefSlot refCountTree = exclusiveTreeSlot();
    public final RefSlot infoBlock = refSlot(Area.SYSTEM, InfoBlock.TYPE);
    public final SlotBlock.LongSlot fileLength = longSlot();

    // These lists are not ref counted on purpose, as their lifecycle is simple:
    // allocate just before the commit, and add to deferred free block list just
    // afterwards. All lists are stored in the META area.
    public final LongSlot systemDeferredFreeBlockList = longSlot();
    public final LongSlot metaDeferredFreeBlockList = longSlot();
    public final LongSlot dataDeferredFreeBlockList = longSlot();
    public final LongSlot pendingMetaFreeTreeRemovalList = longSlot();
    public final LongSlot refCountUpdateList = longSlot();

    public final RootRefsSlot refHandles = rootRefsSlot();

    public final UInt16sSlot longHandleIds = uint16sSlot();
    public final SlotBlock.LongsSlot longHandles = longsSlot();

    private RefSlot exclusiveTreeSlot() {
        // Persist the root pointer without retaining a counted edge. BTree explicitly
        // retires replaced exclusive nodes, independently of root-block refcounting.
        var slot = new RefSlot() {
            @Override
            List<BlockRef> getReferencedBlocks(DataStorage storage) {
                return List.of();
            }
        };
        slots.add(slot);
        return slot;
    }

    private RootRefsSlot rootRefsSlot() {
        var slot = new RootRefsSlot();
        slots.add(slot);
        return slot;
    }

    @Override
    public BlockType<RootBlock> getBlockType() {
        return type;
    }

    public static BlockType<RootBlock> getBlockType(DataStorage storage) {
        return new SlotBlock.SlotBlockType<RootBlock>(() -> new RootBlock(storage));
    }

    public long getDeferredFreeListHead(Area area) {
        return switch (area) {
            case SYSTEM -> systemDeferredFreeBlockList.value;
            case META -> metaDeferredFreeBlockList.value;
            case DATA -> dataDeferredFreeBlockList.value;
        };
    }

    public void setDeferredFreeListHead(Area area, long head) {
        switch (area) {
            case SYSTEM -> systemDeferredFreeBlockList.value = head;
            case META -> metaDeferredFreeBlockList.value = head;
            case DATA -> dataDeferredFreeBlockList.value = head;
        }
    }

    public void setFreeTreeRoot(Area area, long root) {
        switch (area) {
            case SYSTEM -> systemFreeSpaceTree.blockOffset = root;
            case META -> metaFreeSpaceTree.blockOffset = root;
            case DATA -> dataFreeSpaceTree.blockOffset = root;
        }
    }

    public long getFreeTreeRoot(Area area) {
        return switch (area) {
            case SYSTEM -> systemFreeSpaceTree.blockOffset;
            case META -> metaFreeSpaceTree.blockOffset;
            case DATA -> dataFreeSpaceTree.blockOffset;
        };
    }
}