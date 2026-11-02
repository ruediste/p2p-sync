package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;

import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.util.FullGuid;
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
    private boolean nextNodeIdComputed;
    private boolean dirty;

    GuidMapper(DataStorage dataStorage, UnitRootStore unitRoots, SyncUnitId unitId, long nextNodeId) {
        this.dataStorage = dataStorage;
        this.unitRoots = unitRoots;
        this.unitId = unitId;
        this.nextNodeId = nextNodeId;
        forward = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_FORWARD));
        reverse = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_REVERSE));
    }

    long nextNodeId() {
        if (!nextNodeIdComputed) {
            long max = -1;
            for (byte[] hash : reverse.nodeHashes()) {
                // The reverse tree uses the short node id as its key.
                long id = ByteBuffer.wrap(hash).getLong(0);
                max = Math.max(max, id);
            }
            nextNodeId = Math.max(nextNodeId, max + 1);
            nextNodeIdComputed = true;
        }
        return nextNodeId;
    }

    ShortGuid toShort(FullGuid fullId) {
        byte[] nodeHash = fullId.nodeHash();
        long counter = fullId.counter();
        long nodeId = forward.getNodeId(nodeHash);
        if (nodeId < 0) {
            long next = nextNodeId();
            if (next >= 0xFFFF_FFFFL)
                throw new IllegalStateException("Short node-id space exhausted");
            nodeId = next;
            nextNodeId = next + 1;
            forward.put(nodeHash, nodeId);
            reverse.putReverse(nodeId, nodeHash);
            dirty = true;
        }
        ShortGuid result = new ShortGuid();
        result.nodeId = (int) nodeId;
        result.counter = counter;
        return result;
    }

    ShortGuid toShort(byte[] fullId) {
        return toShort(FullGuid.fromBytes(fullId));
    }

    FullGuid toFull(ShortGuid guid) {
        byte[] nodeHash = reverse.getNodeHash(Integer.toUnsignedLong(guid.nodeId));
        if (nodeHash == null)
            throw new IllegalStateException("Missing reverse GUID mapping");
        return new FullGuid(nodeHash, guid.counter);
    }

    void flush() {
        if (!dirty)
            return;
        unitRoots.put(unitId, UnitRootStore.GUID_FORWARD, forward.rootOffset());
        unitRoots.put(unitId, UnitRootStore.GUID_REVERSE, reverse.rootOffset());
        dirty = false;
    }

    static FullGuid fullId(byte[] nodeHash, long counter) {
        return new FullGuid(nodeHash, counter);
    }

    static String hex(FullGuid fullId) {
        return fullId.hex();
    }
}
