package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.BlockType;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;
import com.google.protobuf.GeneratedMessage;

public class BlockStorage {

    private static final BlockType<ChunkBlock> CHUNK_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(ChunkBlock value, DataStorage storage) {
            return List.of();
        }

        @Override
        public ByteBuffer serialize(ChunkBlock value) {
            return ByteBuffer.allocate(Integer.BYTES + value.data.length).putInt(value.data.length)
                    .put(value.data).flip();
        }

        @Override
        public ChunkBlock deserialize(ByteBuffer buffer) {
            if (buffer.remaining() < Integer.BYTES)
                throw new IllegalStateException("Invalid block-store chunk");
            int length = buffer.getInt();
            if (length < 0 || length > buffer.remaining())
                throw new IllegalStateException("Invalid block-store chunk length");
            byte[] data = new byte[length];
            buffer.get(data);
            return new ChunkBlock(data);
        }
    };

    private static final BlockType<ManifestBlock> MANIFEST_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(ManifestBlock value, DataStorage storage) {
            var result = new ArrayList<BlockRef>(value.chunkOffsets.length + 1);
            for (long offset : value.chunkOffsets)
                result.add(new BlockRef(offset, Area.DATA, CHUNK_TYPE));
            if (value.nextOffset != 0)
                result.add(new BlockRef(value.nextOffset, Area.DATA, this));
            return result;
        }

        @Override
        public ByteBuffer serialize(ManifestBlock value) {
            var buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + value.chunkOffsets.length * Long.BYTES);
            buffer.putLong(value.nextOffset).putInt(value.chunkOffsets.length);
            for (long offset : value.chunkOffsets)
                buffer.putLong(offset);
            return buffer.flip();
        }

        @Override
        public ManifestBlock deserialize(ByteBuffer buffer) {
            if (buffer.remaining() < Long.BYTES + Integer.BYTES)
                throw new IllegalStateException("Invalid block-store manifest");
            long nextOffset = buffer.getLong();
            int count = buffer.getInt();
            if (count < 0 || count > buffer.remaining() / Long.BYTES)
                throw new IllegalStateException("Invalid block-store manifest length");
            long[] offsets = new long[count];
            for (int index = 0; index < count; index++)
                offsets[index] = buffer.getLong();
            return new ManifestBlock(nextOffset, offsets);
        }
    };

    private static final BlockType<BlobRef> BLOB_REF_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(BlobRef value, DataStorage storage) {
            return value.manifestOffset == 0 ? List.of()
                    : List.of(new BlockRef(value.manifestOffset, Area.DATA, MANIFEST_TYPE));
        }

        @Override
        public ByteBuffer serialize(BlobRef value) {
            return ByteBuffer.allocate(Long.BYTES * 2).putLong(value.length).putLong(value.manifestOffset).flip();
        }

        @Override
        public BlobRef deserialize(ByteBuffer buffer) {
            if (buffer.remaining() != Long.BYTES * 2)
                throw new IllegalStateException("Invalid block-store reference");
            return new BlobRef(buffer.getLong(), buffer.getLong());
        }
    };

    private static final class ChunkBlock implements BlockType.BlockInstance<ChunkBlock> {
        final byte[] data;

        ChunkBlock(byte[] data) {
            this.data = data;
        }

        @Override
        public BlockType<ChunkBlock> getBlockType() {
            return CHUNK_TYPE;
        }
    }

    private static final class ManifestBlock implements BlockType.BlockInstance<ManifestBlock> {
        final long nextOffset;
        final long[] chunkOffsets;

        ManifestBlock(long nextOffset, long[] chunkOffsets) {
            this.nextOffset = nextOffset;
            this.chunkOffsets = chunkOffsets;
        }

        @Override
        public BlockType<ManifestBlock> getBlockType() {
            return MANIFEST_TYPE;
        }
    }

    private record BlobRef(long length, long manifestOffset) {
    }

    private final DataStorage dataStorage;
    private final BTree<byte[], BlobRef> index;
    private final DataStorage.RootRefHandle<BTree.NodeBlock> indexRoot;

    public BlockStorage(DataStorage dataStorage, int rootHandleId) {
        this.dataStorage = dataStorage;
        BlockType<BTree.Entry<byte[], BlobRef>> entryType = BTree.entryType(BLOB_REF_TYPE);
        index = new BTree<byte[], BlobRef>(dataStorage.new BTreeStorage(Area.META),
                BTree.KeySerializer.bytes(32), entryType);
        indexRoot = dataStorage.getRootRefHandle(rootHandleId, Area.META, index.blockType);
    }

    public void restoreRoot() {
        index.rootOffset = indexRoot.blockOffset;
    }

    public void commit() {
        indexRoot.blockOffset = index.rootOffset;
        dataStorage.commit();
    }

    public Optional<byte[]> getBlock(BlockId id) {
        BlobRef reference = index.get(id.bytes());
        if (reference == null)
            return Optional.empty();
        if (reference.length > Integer.MAX_VALUE)
            throw new IllegalStateException("Stored block exceeds supported byte-array size");
        byte[] result = new byte[(int) reference.length];
        int position = 0;
        long manifestOffset = reference.manifestOffset;
        while (manifestOffset != 0) {
            var manifest = dataStorage.readBlock(manifestOffset, Area.DATA, MANIFEST_TYPE);
            for (long chunkOffset : manifest.chunkOffsets) {
                var chunk = dataStorage.readBlock(chunkOffset, Area.DATA, CHUNK_TYPE);
                if (chunk.data.length > result.length - position)
                    throw new IllegalStateException("Stored block chunks exceed declared length");
                System.arraycopy(chunk.data, 0, result, position, chunk.data.length);
                position += chunk.data.length;
            }
            manifestOffset = manifest.nextOffset;
        }
        if (position != result.length || !Arrays.equals(CryptoUtils.sha256(result), id.bytes()))
            throw new IllegalStateException("Stored block content does not match its identifier");
        return Optional.of(result);
    }

    public void put(BlockId id, byte[] bytes) {
        if (!Arrays.equals(CryptoUtils.sha256(bytes), id.bytes()))
            throw new IllegalArgumentException("Block identifier does not match its content");
        var existing = getBlock(id);
        if (existing.isPresent()) {
            if (!Arrays.equals(existing.get(), bytes))
                throw new IllegalStateException("Content-address collision");
            return;
        }
        int chunkCapacity = dataStorage.blockSize(Area.DATA) - Integer.BYTES;
        var chunkOffsets = new ArrayList<Long>();
        for (int offset = 0; offset < bytes.length; offset += chunkCapacity) {
            int length = Math.min(chunkCapacity, bytes.length - offset);
            chunkOffsets.add(dataStorage.writeNewBlock(Area.DATA,
                    new ChunkBlock(Arrays.copyOfRange(bytes, offset, offset + length))));
        }
        int manifestCapacity = (dataStorage.blockSize(Area.DATA) - Long.BYTES - Integer.BYTES) / Long.BYTES;
        long manifestOffset = 0;
        for (int end = chunkOffsets.size(); end > 0; end -= manifestCapacity) {
            int start = Math.max(0, end - manifestCapacity);
            long[] offsets = new long[end - start];
            for (int index = start; index < end; index++)
                offsets[index - start] = chunkOffsets.get(index);
            manifestOffset = dataStorage.writeNewBlock(Area.DATA, new ManifestBlock(manifestOffset, offsets));
        }
        index.put(id.bytes(), new BlobRef(bytes.length, manifestOffset));
    }

    public void remove(BlockId id) {
        index.remove(id.bytes());
    }

    public void collectGarbage(Set<BlockId> reachable) {
        var unreachable = new ArrayList<BlockId>();
        for (var entry : index)
            if (!reachable.contains(new BlockId(entry.key)))
                unreachable.add(new BlockId(entry.key));
        for (BlockId id : unreachable)
            index.remove(id.bytes());
    }

    public BlockId store(GeneratedMessage proto) {
        var data = proto.toByteArray();
        var id = new BlockId(CryptoUtils.sha256(data));
        put(id, data);
        return id;
    }

}
