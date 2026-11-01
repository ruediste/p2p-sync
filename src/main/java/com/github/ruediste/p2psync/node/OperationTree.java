package com.github.ruediste.p2psync.node;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.BlockType;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

/**
 * Signed operation history stored directly in a B-tree. The key is the
 * operation id (big-endian Lamport clock followed by the 32-byte node hash).
 * Payloads are split into DATA blocks and referenced from the B-tree value, so
 * an operation may be much larger than a META block.
 */
final class OperationTree {
    static final int KEY_SIZE = Long.BYTES + 32;

    private static final class OperationValue {
        final byte[] payload;
        final byte[] signature;
        final long[] payloadBlocks;

        OperationValue(byte[] payload, byte[] signature, long[] payloadBlocks) {
            this.payload = payload;
            this.signature = signature;
            this.payloadBlocks = payloadBlocks;
        }
    }

    private static final BlockType<OperationValue> VALUE_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(OperationValue value, DataStorage storage) {
            var result = new ArrayList<BlockRef>(value.payloadBlocks.length);
            for (long offset : value.payloadBlocks)
                result.add(new BlockRef(offset, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
            return result;
        }

        @Override
        public ByteBuffer serialize(OperationValue value) {
            int size = 2 * Integer.BYTES + value.signature.length + Integer.BYTES
                    + value.payloadBlocks.length * Long.BYTES;
            var buffer = ByteBuffer.allocate(size);
            buffer.putInt(value.payload.length);
            buffer.putInt(value.signature.length);
            buffer.put(value.signature);
            buffer.putInt(value.payloadBlocks.length);
            for (long offset : value.payloadBlocks)
                buffer.putLong(offset);
            return buffer.flip();
        }

        @Override
        public OperationValue deserialize(ByteBuffer buffer) {
            if (buffer.remaining() < 2 * Integer.BYTES)
                throw new IllegalStateException("Invalid operation value");
            int payloadLength = buffer.getInt();
            int signatureLength = buffer.getInt();
            if (payloadLength < 0 || signatureLength < 0 || signatureLength > buffer.remaining())
                throw new IllegalStateException("Invalid operation value lengths");
            byte[] signature = new byte[signatureLength];
            buffer.get(signature);
            if (buffer.remaining() < Integer.BYTES)
                throw new IllegalStateException("Invalid operation value block count");
            int blockCount = buffer.getInt();
            if (blockCount < 0 || blockCount > buffer.remaining() / Long.BYTES)
                throw new IllegalStateException("Invalid operation value block count");
            long[] blocks = new long[blockCount];
            for (int i = 0; i < blockCount; i++)
                blocks[i] = buffer.getLong();
            return new OperationValue(new byte[payloadLength], signature, blocks);
        }
    };

    static final BlockType<BTree.NodeBlock> BLOCK_TYPE = BTree.blockType(BTree.KeySerializer.bytes(KEY_SIZE),
            Area.META, BTree.entryType(VALUE_TYPE));

    private final DataStorage dataStorage;
    private final BTree<byte[], OperationValue> tree;

    OperationTree(DataStorage dataStorage) {
        this(dataStorage, 0);
    }

    OperationTree(DataStorage dataStorage, long rootOffset) {
        this.dataStorage = dataStorage;
        tree = new BTree<>(dataStorage.new BTreeStorage(Area.META), BTree.KeySerializer.bytes(KEY_SIZE),
                BTree.entryType(VALUE_TYPE), rootOffset);
    }

    long rootOffset() {
        return tree.rootOffset;
    }

    void put(Node.OperationId id, Node.Operation operation) {
        if (operation.payload.length == 0) {
            tree.put(key(id), new OperationValue(operation.payload, operation.signature, new long[0]));
            return;
        }
        int chunkSize = dataStorage.blockSize(Area.DATA);
        var blocks = new ArrayList<Long>();
        for (int offset = 0; offset < operation.payload.length; offset += chunkSize) {
            int end = Math.min(operation.payload.length, offset + chunkSize);
            byte[] chunk = java.util.Arrays.copyOfRange(operation.payload, offset, end);
            blocks.add(dataStorage.writeNewBlock(Area.DATA, new DataStorage.ByteArrayBlock(chunk)));
        }
        long[] offsets = blocks.stream().mapToLong(Long::longValue).toArray();
        tree.put(key(id), new OperationValue(operation.payload, operation.signature, offsets));
    }

    boolean contains(Node.OperationId id) {
        return tree.contains(key(id));
    }

    Node.Operation get(Node.OperationId id) {
        OperationValue value = tree.get(key(id));
        return value == null ? null : toOperation(id, value);
    }

    boolean remove(Node.OperationId id) {
        return tree.remove(key(id));
    }

    List<Node.Operation> values() {
        var result = new ArrayList<Node.Operation>();
        for (var entry : tree) {
            Node.OperationId id = id(entry.key);
            result.add(toOperation(id, entry.value));
        }
        return result;
    }

    private Node.Operation toOperation(Node.OperationId id, OperationValue value) {
        byte[] payload;
        if (value.payloadBlocks.length == 0) {
            if (value.payload.length != 0)
                throw new IllegalStateException("Operation payload blocks are missing");
            payload = new byte[0];
        } else {
            payload = new byte[value.payload.length];
            int position = 0;
            for (long offset : value.payloadBlocks) {
                byte[] block = dataStorage.readBlock(offset, Area.DATA,
                        DataStorage.BYTE_ARRAY_BLOCK_TYPE).data();
                int count = Math.min(block.length, payload.length - position);
                System.arraycopy(block, 0, payload, position, count);
                position += count;
            }
            if (position != payload.length)
                throw new IllegalStateException("Operation payload blocks are incomplete");
        }
        return new Node.Operation(id, payload, value.signature);
    }

    private static byte[] key(Node.OperationId id) {
        return ByteBuffer.allocate(KEY_SIZE)
                .putLong(id.clock)
                .put(id.nodeHash)
                .array();
    }

    private static Node.OperationId id(byte[] key) {
        if (key.length != KEY_SIZE)
            throw new IllegalArgumentException("Invalid operation key length");
        var buffer = ByteBuffer.wrap(key);
        long clock = buffer.getLong();
        byte[] nodeHash = new byte[32];
        buffer.get(nodeHash);
        return new Node.OperationId(clock, nodeHash);
    }
}
