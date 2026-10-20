package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.github.ruediste.p2psync.storage.DataStorage.Area;

public interface BlockType<T> {
    record BlockRef(long blockOffset, Area area, BlockType<?> type) {
    }

    List<BlockRef> getReferencedBlocks(T value);

    ByteBuffer serialize(T value);

    T deserialize(ByteBuffer buffer);

    public interface BlockInstance<TSelf> {
        BlockType<TSelf> getBlockType();
    }

    public static abstract class Slot {
        abstract List<BlockRef> getReferencedBlocks();

        abstract void serialize(ByteBuffer buffer);

        abstract int serializedSize();

        abstract void deserialize(ByteBuffer buffer);
    }

    public static class RefSlot extends Slot {
        public long blockOffset;
        public Area area;
        public BlockType<?> type;

        public RefSlot() {
        }

        public RefSlot(Area area, BlockType<?> type) {
            this.area = area;
            this.type = type;
        }

        @Override
        List<BlockRef> getReferencedBlocks() {
            if (blockOffset == 0)
                return List.of();
            if (area == null || type == null)
                throw new IllegalStateException("Reference slot target type and area must be configured");
            return List.of(new BlockRef(blockOffset, area, type));
        }

        @Override
        void serialize(ByteBuffer buffer) {
            buffer.putLong(blockOffset);
        }

        @Override
        int serializedSize() {
            return Long.BYTES;
        }

        @Override
        void deserialize(ByteBuffer buffer) {
            blockOffset = buffer.getLong();
        }
    }

    public static class RefsSlot extends Slot {
        public List<Long> blockOffsets = new ArrayList<>();
        public Area area;
        public BlockType<?> type;

        public RefsSlot() {
        }

        public RefsSlot(Area area, BlockType<?> type) {
            this.area = area;
            this.type = type;
        }

        @Override
        List<BlockRef> getReferencedBlocks() {
            if (blockOffsets.stream().noneMatch(offset -> offset != 0))
                return List.of();
            if (area == null || type == null)
                throw new IllegalStateException("Reference slot target type and area must be configured");
            return blockOffsets.stream().filter(offset -> offset != 0)
                    .map(offset -> new BlockRef(offset, area, type)).toList();
        }

        @Override
        void serialize(ByteBuffer buffer) {
            buffer.putInt(blockOffsets.size());
            for (Long offset : blockOffsets) {
                buffer.putLong(offset);
            }
        }

        @Override
        int serializedSize() {
            return Integer.BYTES + Long.BYTES * blockOffsets.size();
        }

        @Override
        void deserialize(ByteBuffer buffer) {
            int size = buffer.getInt();
            if (size < 0 || size > buffer.remaining() / Long.BYTES)
                throw new IllegalArgumentException("Invalid reference-slot length: " + size);
            blockOffsets = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                blockOffsets.add(buffer.getLong());
            }
        }
    }

    public static class BytesSlot extends Slot {
        public byte[] value = new byte[0];

        @Override
        List<BlockRef> getReferencedBlocks() {
            return List.of();
        }

        @Override
        void serialize(ByteBuffer buffer) {
            buffer.putInt(value.length);
            buffer.put(value);
        }

        @Override
        int serializedSize() {
            return Integer.BYTES + value.length;
        }

        @Override
        void deserialize(ByteBuffer buffer) {
            int size = buffer.getInt();
            if (size < 0 || size > buffer.remaining())
                throw new IllegalArgumentException("Invalid byte-slot length: " + size);
            value = new byte[size];
            buffer.get(value);
        }
    }

    public static class IntSlot extends Slot {
        public int value;

        @Override
        List<BlockRef> getReferencedBlocks() {
            return List.of();
        }

        @Override
        void serialize(ByteBuffer buffer) {
            buffer.putInt(value);
        }

        @Override
        int serializedSize() {
            return Integer.BYTES;
        }

        @Override
        void deserialize(ByteBuffer buffer) {
            value = buffer.getInt();
        }
    }

    public static class LongSlot extends Slot {
        public long value;

        @Override
        List<BlockRef> getReferencedBlocks() {
            return List.of();
        }

        @Override
        void serialize(ByteBuffer buffer) {
            buffer.putLong(value);
        }

        @Override
        int serializedSize() {
            return Long.BYTES;
        }

        @Override
        void deserialize(ByteBuffer buffer) {
            value = buffer.getLong();
        }
    }

    public static class SlotBlockType<T extends SlotBlock<?>> implements BlockType<T> {
        private Supplier<T> constructor;

        public SlotBlockType(Supplier<T> constructor) {
            this.constructor = constructor;
        }

        @Override
        public List<BlockRef> getReferencedBlocks(T value) {
            return value.slots.stream().flatMap(slot -> slot.getReferencedBlocks().stream()).toList();
        }

        @Override
        public ByteBuffer serialize(T value) {
            int size = value.slots.stream().mapToInt(Slot::serializedSize).sum();
            ByteBuffer buffer = ByteBuffer.allocate(size);
            for (Slot slot : value.slots) {
                slot.serialize(buffer);
            }
            buffer.flip();
            return buffer;
        }

        @Override
        public T deserialize(ByteBuffer buffer) {
            T instance = constructor.get();
            for (Slot slot : instance.slots) {
                slot.deserialize(buffer);
            }
            return instance;
        }
    };

    public static abstract class SlotBlock<TSelf> implements BlockInstance<TSelf> {
        List<Slot> slots = new ArrayList<>();

        private <T extends Slot> T addSlot(T slot) {
            slots.add(slot);
            return slot;
        }

        protected RefSlot refSlot() {
            return addSlot(new RefSlot());
        }

        protected RefSlot refSlot(Area area, BlockType<?> type) {
            return addSlot(new RefSlot(area, type));
        }

        protected IntSlot intSlot() {
            return addSlot(new IntSlot());
        }

        protected LongSlot longSlot() {
            return addSlot(new LongSlot());
        }

        protected RefsSlot refsSlot() {
            return addSlot(new RefsSlot());
        }

        protected RefsSlot refsSlot(Area area, BlockType<?> type) {
            return addSlot(new RefsSlot(area, type));
        }

        protected BytesSlot bytesSlot() {
            return addSlot(new BytesSlot());
        }

    }
}