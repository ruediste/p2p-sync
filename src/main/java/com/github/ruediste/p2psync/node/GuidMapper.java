package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.HexFormat;

import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.util.ShortGuid;

/**
 * Per-SyncUnit, node-local translation between full entity ids
 * ({@code nodeHash[32] + counter[8]}) and the short GUIDs used by
 * {@link com.github.ruediste.p2psync.storage.FileTree}.
 */
final class GuidMapper {
    private final DataStorage dataStorage;
    private final UnitRootStore unitRoots;
    private final SyncUnitId unitId;
    private final GuidTree forward;
    private final GuidTree reverse;
    private long nextNodeId;

    GuidMapper(DataStorage dataStorage, UnitRootStore unitRoots, SyncUnitId unitId, long nextNodeId) {
        this.dataStorage = dataStorage;
        this.unitRoots = unitRoots;
        this.unitId = unitId;
        this.nextNodeId = nextNodeId;
        forward = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_FORWARD));
        reverse = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_REVERSE));
    }

    long nextNodeId() {
        if (nextNodeId == 0) {
            long max = -1;
            for (byte[] hash : reverse.nodeHashes()) {
                // The reverse tree uses the short node id as its key.
                long id = ByteBuffer.wrap(hash).getLong(0);
                max = Math.max(max, id);
            }
            nextNodeId = max + 1;
        }
        return nextNodeId;
    }

    ShortGuid toShort(byte[] fullId) {
        if (fullId.length != 40)
            throw new IllegalArgumentException("Full entity id must contain 40 bytes");
        byte[] nodeHash = java.util.Arrays.copyOfRange(fullId, 0, 32);
        long counter = ByteBuffer.wrap(fullId, 32, 8).getLong();
        long nodeId = forward.getNodeId(nodeHash);
        if (nodeId < 0) {
            long next = nextNodeId();
            if (next >= 0xFFFF_FFFFL)
                throw new IllegalStateException("Short node-id space exhausted");
            nodeId = next;
            nextNodeId = next + 1;
            forward.put(nodeHash, nodeId);
            reverse.putReverse(nodeId, nodeHash);
        }
        ShortGuid result = new ShortGuid();
        result.nodeId = (int) nodeId;
        result.counter = counter;
        return result;
    }

    byte[] toFull(ShortGuid guid) {
        byte[] nodeHash = reverse.getNodeHash(Integer.toUnsignedLong(guid.nodeId));
        if (nodeHash == null)
            throw new IllegalStateException("Missing reverse GUID mapping");
        return ByteBuffer.allocate(40).put(nodeHash).putLong(guid.counter).array();
    }

    void flush() {
        unitRoots.put(unitId, UnitRootStore.GUID_FORWARD, forward.rootOffset());
        unitRoots.put(unitId, UnitRootStore.GUID_REVERSE, reverse.rootOffset());
    }

    static byte[] fullId(byte[] nodeHash, long counter) {
        return ByteBuffer.allocate(40).put(nodeHash).putLong(counter).array();
    }

    static String hex(byte[] fullId) {
        return HexFormat.of().formatHex(fullId);
    }
}
