package com.github.ruediste.p2psync.node;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.BlockType;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;
import com.google.protobuf.GeneratedMessage;

/**
 * Caller-identified blob storage. Blobs live in a B-tree keyed by a 32-byte
 * identifier; the blob itself is a linked list of DATA blocks whose last block
 * carries the final length. Using a B-tree (rather than a bare root handle)
 * keeps the block ownership bookkeeping of {@link DataStorage} intact when a
 * blob is replaced or removed.
 */
public class BlobStorage {
    public static final byte[] NODE_STATE_ID = new byte[32];

    private static final class ChainBlock implements BlockType.BlockInstance<ChainBlock> {
        final long nextOffset;
        final byte[] data;

        ChainBlock(long nextOffset, byte[] data) {
            this.nextOffset = nextOffset;
            this.data = data;
        }

        @Override
        public BlockType<ChainBlock> getBlockType() {
            return CHAIN_TYPE;
        }
    }

    private static final BlockType<ChainBlock> CHAIN_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(ChainBlock value, DataStorage storage) {
            return value.nextOffset == 0 ? List.of()
                    : List.of(new BlockRef(value.nextOffset, Area.DATA, this));
        }

        @Override
        public ByteBuffer serialize(ChainBlock value) {
            return ByteBuffer.allocate(Long.BYTES + Integer.BYTES + value.data.length)
                    .putLong(value.nextOffset)
                    .putInt(value.data.length)
                    .put(value.data)
                    .flip();
        }

        @Override
        public ChainBlock deserialize(ByteBuffer buffer) {
            if (buffer.remaining() < Long.BYTES + Integer.BYTES)
                throw new IllegalStateException("Invalid blob chain block");
            long nextOffset = buffer.getLong();
            int length = buffer.getInt();
            if (length < 0 || length > buffer.remaining())
                throw new IllegalStateException("Invalid blob chain block length");
            byte[] data = new byte[length];
            buffer.get(data);
            return new ChainBlock(nextOffset, data);
        }
    };

    private record BlobRef(long length, long firstOffset) {
    }

    private static final BlockType<BlobRef> BLOB_REF_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(BlobRef value, DataStorage storage) {
            return value.firstOffset == 0 ? List.of()
                    : List.of(new BlockRef(value.firstOffset, Area.DATA, CHAIN_TYPE));
        }

        @Override
        public ByteBuffer serialize(BlobRef value) {
            return ByteBuffer.allocate(Long.BYTES * 2).putLong(value.length).putLong(value.firstOffset).flip();
        }

        @Override
        public BlobRef deserialize(ByteBuffer buffer) {
            if (buffer.remaining() != Long.BYTES * 2)
                throw new IllegalStateException("Invalid blob reference");
            return new BlobRef(buffer.getLong(), buffer.getLong());
        }
    };

    private final DataStorage dataStorage;
    private final BTree<byte[], BlobRef> index;
    private final DataStorage.RootRefHandle<BTree.NodeBlock> indexRoot;
    private int chunkSize;

    public BlobStorage(DataStorage dataStorage, int rootHandleId) {
        this.dataStorage = dataStorage;
        index = new BTree<>(dataStorage.new BTreeStorage(Area.META), BTree.KeySerializer.bytes(32),
                BTree.entryType(BLOB_REF_TYPE));
        indexRoot = dataStorage.getRootRefHandle(rootHandleId, Area.META, index.blockType);
    }

    public void restoreRoot() {
        index.rootOffset = indexRoot.blockOffset;
        chunkSize = dataStorage.blockSize(Area.DATA) - Long.BYTES - Integer.BYTES;
    }

    public void commit() {
        indexRoot.blockOffset = index.rootOffset;
        dataStorage.commit();
    }

    public Optional<byte[]> get(byte[] id) {
        BlobRef ref = index.get(id);
        if (ref == null)
            return Optional.empty();
        if (ref.length < 0 || ref.length > Integer.MAX_VALUE)
            throw new IllegalStateException("Blob is too large to return as a byte array");
        byte[] result = new byte[(int) ref.length];
        long offset = ref.firstOffset;
        int position = 0;
        while (position < result.length) {
            if (offset == 0)
                throw new IllegalStateException("Blob chain ended before the announced length");
            ChainBlock block = dataStorage.readBlock(offset, Area.DATA, CHAIN_TYPE);
            if (block.data.length == 0)
                throw new IllegalStateException("Blob chain contains an empty block");
            int count = Math.min(block.data.length, result.length - position);
            System.arraycopy(block.data, 0, result, position, count);
            position += count;
            offset = block.nextOffset;
        }
        if (offset != 0)
            throw new IllegalStateException("Blob chain is longer than the announced length");
        return Optional.of(result);
    }

    public void put(byte[] id, byte[] data) {
        long nextOffset = 0;
        for (int end = data.length; end > 0; end -= chunkSize) {
            int start = Math.max(0, end - chunkSize);
            byte[] chunk = java.util.Arrays.copyOfRange(data, start, end);
            nextOffset = dataStorage.writeNewBlock(Area.DATA, new ChainBlock(nextOffset, chunk));
        }
        index.put(id, new BlobRef(data.length, nextOffset));
    }

    public void put(byte[] id, GeneratedMessage message) {
        put(id, message.toByteArray());
    }

    public void remove(byte[] id) {
        index.remove(id);
    }
}
