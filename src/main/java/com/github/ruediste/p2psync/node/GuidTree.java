package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

/**
 * Per-SyncUnit, node-local short-GUID mapping. Both the forward tree
 * (full node hash -> short node id) and the reverse tree (short node id ->
 * full node hash) use this type; keys and values are fixed 32-byte binary
 * values.
 */
final class GuidTree {
    static final int KEY_SIZE = 32;
    static final int VALUE_SIZE = 32;
    static final com.github.ruediste.p2psync.storage.BlockType<BTree.NodeBlock> BLOCK_TYPE = BTree.blockType(
            BTree.KeySerializer.bytes(KEY_SIZE), Area.META, BTree.BYTE_ARRAY_ENTRY_TYPE);

    private final BTree<byte[], byte[]> tree;

    GuidTree(DataStorage dataStorage) {
        this(dataStorage, 0);
    }

    GuidTree(DataStorage dataStorage, long rootOffset) {
        tree = new BTree<>(dataStorage.new BTreeStorage(Area.META), BTree.KeySerializer.bytes(KEY_SIZE),
                BTree.BYTE_ARRAY_ENTRY_TYPE, rootOffset);
    }

    long rootOffset() {
        return tree.rootOffset;
    }

    long getNodeId(byte[] nodeHash) {
        byte[] value = tree.get(padHash(nodeHash));
        if (value == null)
            return -1;
        return ByteBuffer.wrap(value).getLong(0);
    }

    byte[] getNodeHash(long nodeId) {
        byte[] key = nodeIdKey(nodeId);
        return tree.get(key);
    }

    void put(byte[] nodeHash, long nodeId) {
        tree.put(padHash(nodeHash), nodeIdValue(nodeId));
    }

    void putReverse(long nodeId, byte[] nodeHash) {
        if (nodeHash.length != 32)
            throw new IllegalArgumentException("Node hash must contain 32 bytes");
        tree.put(nodeIdKey(nodeId), nodeHash.clone());
    }

    void remove(byte[] nodeHash, long nodeId) {
        tree.remove(padHash(nodeHash));
        tree.remove(nodeIdKey(nodeId));
    }

    List<byte[]> nodeHashes() {
        var result = new ArrayList<byte[]>();
        for (var entry : tree)
            result.add(entry.key);
        return result;
    }

    List<byte[]> reverseHashes() {
        var result = new ArrayList<byte[]>();
        for (var entry : tree)
            result.add(entry.value);
        return result;
    }

    private static byte[] padHash(byte[] nodeHash) {
        if (nodeHash.length != 32)
            throw new IllegalArgumentException("Node hash must contain 32 bytes");
        return nodeHash.clone();
    }

    private static byte[] nodeIdKey(long nodeId) {
        if (nodeId < 0 || nodeId > 0xFFFF_FFFFL)
            throw new IllegalArgumentException("Node id is out of range: " + nodeId);
        byte[] result = new byte[KEY_SIZE];
        ByteBuffer.wrap(result).putLong(0, nodeId);
        return result;
    }

    private static byte[] nodeIdValue(long nodeId) {
        if (nodeId < 0 || nodeId > 0xFFFF_FFFFL)
            throw new IllegalArgumentException("Node id is out of range: " + nodeId);
        byte[] result = new byte[VALUE_SIZE];
        ByteBuffer.wrap(result).putLong(0, nodeId);
        return result;
    }
}
