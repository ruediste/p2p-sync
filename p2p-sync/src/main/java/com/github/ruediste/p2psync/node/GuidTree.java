package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

/**
 * Per-SyncUnit, node-local short-GUID mapping. Both the forward tree
 * (NodeId -> short node id) and the reverse tree (short node id -> NodeId)
 * use this type; keys and values are fixed 32-byte binary
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

    long getNodeId(NodeId nodeId) {
        byte[] value = tree.get(nodeId.bytes());
        if (value == null)
            return -1;
        return ByteBuffer.wrap(value).getLong(0);
    }

    NodeId getNodeIdForShortId(long nodeId) {
        byte[] key = nodeIdKey(nodeId);
        byte[] value = tree.get(key);
        return value == null ? null : NodeId.fromBytes(value);
    }

    void put(NodeId nodeId, long shortNodeId) {
        tree.put(nodeId.bytes(), nodeIdValue(shortNodeId));
    }

    void putReverse(long shortNodeId, NodeId nodeId) {
        tree.put(nodeIdKey(shortNodeId), nodeId.bytes());
    }

    void remove(NodeId nodeId, long shortNodeId) {
        tree.remove(nodeId.bytes());
        tree.remove(nodeIdKey(shortNodeId));
    }

    List<byte[]> shortNodeIds() {
        var result = new ArrayList<byte[]>();
        for (var entry : tree)
            result.add(entry.key);
        return result;
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
