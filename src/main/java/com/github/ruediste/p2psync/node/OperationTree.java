package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.BlockType;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

/**
 * Signed operation history stored directly in a B-tree. The key is the
 * operation id (big-endian Lamport clock followed by the 32-byte node hash).
 * Payloads are stored in a chain of DATA blocks and the B-tree value only keeps
 * the first block, so an operation may be arbitrarily larger than a META block
 * without making the leaf entry too large to split.
 */
final class OperationTree {
    static final int KEY_SIZE = Long.BYTES + 32;

    /**
     * Marker for the chained-value format. A legacy value started with the
     * non-negative payload length, so a negative first int is unambiguous.
     */
    private static final int CHAINED_VALUE_MAGIC = 0xC0DE_1A75;

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
                throw new IllegalStateException("Invalid operation chain block");
            long nextOffset = buffer.getLong();
            int length = buffer.getInt();
            if (length < 0 || length > buffer.remaining())
                throw new IllegalStateException("Invalid operation chain block length");
            byte[] data = new byte[length];
            buffer.get(data);
            return new ChainBlock(nextOffset, data);
        }
    };

    private static final class OperationValue {
        final int payloadLength;
        final byte[] signature;
        /** First block of the chained format, 0 for an empty payload. */
        final long firstPayloadBlock;
        /** Previous inline block list format, null for chained values. */
        final long[] payloadBlocks;

        private OperationValue(int payloadLength, byte[] signature, long firstPayloadBlock, long[] payloadBlocks) {
            this.payloadLength = payloadLength;
            this.signature = signature;
            this.firstPayloadBlock = firstPayloadBlock;
            this.payloadBlocks = payloadBlocks;
        }

        static OperationValue chained(int payloadLength, byte[] signature, long firstPayloadBlock) {
            return new OperationValue(payloadLength, signature, firstPayloadBlock, null);
        }

        // todo: remove legacy support
        static OperationValue legacy(int payloadLength, byte[] signature, long[] payloadBlocks) {
            return new OperationValue(payloadLength, signature, 0, payloadBlocks);
        }

        boolean chained() {
            return payloadBlocks == null;
        }
    }

    private static final BlockType<OperationValue> VALUE_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(OperationValue value, DataStorage storage) {
            if (!value.chained()) {
                var result = new ArrayList<BlockRef>(value.payloadBlocks.length);
                for (long offset : value.payloadBlocks)
                    result.add(new BlockRef(offset, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
                return result;
            }
            return value.firstPayloadBlock == 0 ? List.of()
                    : List.of(new BlockRef(value.firstPayloadBlock, Area.DATA, CHAIN_TYPE));
        }

        @Override
        public ByteBuffer serialize(OperationValue value) {
            if (!value.chained()) {
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3 + value.signature.length
                        + value.payloadBlocks.length * Long.BYTES);
                buffer.putInt(value.payloadLength);
                buffer.putInt(value.signature.length);
                buffer.put(value.signature);
                buffer.putInt(value.payloadBlocks.length);
                for (long offset : value.payloadBlocks)
                    buffer.putLong(offset);
                return buffer.flip();
            }
            return ByteBuffer.allocate(Integer.BYTES * 3 + value.signature.length + Long.BYTES)
                    .putInt(CHAINED_VALUE_MAGIC)
                    .putInt(value.payloadLength)
                    .putInt(value.signature.length)
                    .put(value.signature)
                    .putLong(value.firstPayloadBlock)
                    .flip();
        }

        @Override
        public OperationValue deserialize(ByteBuffer buffer) {
            if (buffer.remaining() < Integer.BYTES * 2)
                throw new IllegalStateException("Invalid operation value");
            int first = buffer.getInt();
            if (first == CHAINED_VALUE_MAGIC) {
                int payloadLength = buffer.getInt();
                if (buffer.remaining() < Integer.BYTES)
                    throw new IllegalStateException("Invalid operation value signature length");
                int signatureLength = buffer.getInt();
                if (payloadLength < 0 || signatureLength < 0 || signatureLength > buffer.remaining())
                    throw new IllegalStateException("Invalid operation value lengths");
                byte[] signature = new byte[signatureLength];
                buffer.get(signature);
                if (buffer.remaining() != Long.BYTES)
                    throw new IllegalStateException("Invalid chained operation value");
                return OperationValue.chained(payloadLength, signature, buffer.getLong());
            }
            if (first < 0)
                throw new IllegalStateException("Invalid legacy operation value length");
            int payloadLength = first;
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
            return OperationValue.legacy(payloadLength, signature, blocks);
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
            tree.put(key(id), OperationValue.chained(0, operation.signature, 0));
            return;
        }
        tree.put(key(id), OperationValue.chained(operation.payload.length, operation.signature,
                writeChain(operation.payload)));
    }

    /**
     * Writes the payload into a linked list of DATA blocks and returns its head.
     */
    private long writeChain(byte[] payload) {
        int chunkSize = dataStorage.blockSize(Area.DATA) - Long.BYTES - Integer.BYTES;
        if (chunkSize <= 0)
            throw new IllegalStateException("DATA block is too small for operation payloads");
        long nextOffset = 0;
        for (int end = payload.length; end > 0; end -= chunkSize) {
            int start = Math.max(0, end - chunkSize);
            byte[] chunk = Arrays.copyOfRange(payload, start, end);
            nextOffset = dataStorage.writeNewBlock(Area.DATA, new ChainBlock(nextOffset, chunk));
        }
        return nextOffset;
    }

    boolean contains(Node.OperationId id) {
        return tree.contains(key(id));
    }

    Node.Operation get(Node.OperationId id) {
        OperationValue value = tree.get(key(id));
        return value == null ? null : toOperation(id, value);
    }

    Node.OperationId nextIdAfter(Node.OperationId id) {
        var entries = id == null ? tree.iterator() : tree.floorOrLowestIterator(key(id));
        while (entries.hasNext()) {
            var candidate = id(entries.next().key);
            if (id == null || candidate.compareTo(id) > 0)
                return candidate;
        }
        return null;
    }

    Node.OperationId lastId() {
        Node.OperationId result = null;
        for (var entry : tree)
            result = id(entry.key);
        return result;
    }

    boolean remove(Node.OperationId id) {
        return tree.remove(key(id));
    }

    Iterable<Node.Operation> operations() {
        return () -> new Iterator<>() {
            private final Iterator<BTree.Entry<byte[], OperationValue>> entries = tree.iterator();

            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public Node.Operation next() {
                var entry = entries.next();
                return toOperation(id(entry.key), entry.value);
            }
        };
    }

    private Node.Operation toOperation(Node.OperationId id, OperationValue value) {
        byte[] payload;
        if (value.chained())
            payload = readChain(value.firstPayloadBlock, value.payloadLength);
        else if (value.payloadBlocks.length == 0) {
            if (value.payloadLength != 0)
                throw new IllegalStateException("Operation payload blocks are missing");
            payload = new byte[0];
        } else {
            payload = new byte[value.payloadLength];
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

    private byte[] readChain(long firstOffset, int payloadLength) {
        if (payloadLength < 0)
            throw new IllegalStateException("Invalid operation payload length");
        byte[] payload = new byte[payloadLength];
        long offset = firstOffset;
        int position = 0;
        while (position < payload.length) {
            if (offset == 0)
                throw new IllegalStateException("Operation payload chain ended early");
            ChainBlock block = dataStorage.readBlock(offset, Area.DATA, CHAIN_TYPE);
            if (block.data.length == 0)
                throw new IllegalStateException("Operation payload chain contains an empty block");
            int count = Math.min(block.data.length, payload.length - position);
            System.arraycopy(block.data, 0, payload, position, count);
            position += count;
            offset = block.nextOffset;
        }
        if (offset != 0)
            throw new IllegalStateException("Operation payload chain is longer than the announced length");
        return payload;
    }

    private static byte[] key(Node.OperationId id) {
        return ByteBuffer.allocate(KEY_SIZE)
                .putLong(id.clock)
                .put(id.nodeId.bytes())
                .array();
    }

    private static Node.OperationId id(byte[] key) {
        if (key.length != KEY_SIZE)
            throw new IllegalArgumentException("Invalid operation key length");
        var buffer = ByteBuffer.wrap(key);
        long clock = buffer.getLong();
        byte[] nodeId = new byte[32];
        buffer.get(nodeId);
        return new Node.OperationId(clock, com.github.ruediste.p2psync.identity.NodeId.fromBytes(nodeId));
    }
}
