package com.github.ruediste.p2psync.storage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.List;

import com.github.ruediste.p2psync.storage.DataStorage.Area;

public abstract class BlockType<T> implements BlockSerializer<T> {
    private RuntimeException ex = new RuntimeException();

    public static final BlockType<byte[]> BYTES = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(byte[] value, DataStorage storage) {
            return List.of();
        }

        @Override
        public ByteBuffer serialize(byte[] value) {
            return ByteBuffer.wrap(value);
        }

        @Override
        public byte[] deserialize(ByteBuffer buffer) {
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        }
    };

    public record BlockRef(long blockOffset, Area area, BlockType<?> type) {
    }

    public abstract List<BlockRef> getReferencedBlocks(T value, DataStorage storage);

    public interface BlockInstance<TSelf> {
        BlockType<TSelf> getBlockType();
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        ex.printStackTrace(writer);
        writer.flush();
        return "BlockType{" + sw + '}';
    }
}