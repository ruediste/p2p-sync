package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.util.FullGuid;
import com.github.ruediste.p2psync.util.ShortGuid;

/**
 * Per-SyncUnit, node-local translation between full entity ids
 * ({@code NodeId[32] + counter[8]}) and the short GUIDs used by
 * {@link com.github.ruediste.p2psync.storage.FileTree}.
 */
final class GuidMapper {
    private final UnitRootStore unitRoots;
    private final SyncUnitId unitId;
    private final GuidTree forward;
    private final GuidTree reverse;
    private long nextNodeId;
    private boolean nextNodeIdComputed;
    private boolean dirty;

    GuidMapper(DataStorage dataStorage, UnitRootStore unitRoots, SyncUnitId unitId, long nextNodeId) {
        this.unitRoots = unitRoots;
        this.unitId = unitId;
        this.nextNodeId = nextNodeId;
        forward = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_FORWARD));
        reverse = new GuidTree(dataStorage, unitRoots.get(unitId, UnitRootStore.GUID_REVERSE));
    }

    long nextNodeId() {
        if (!nextNodeIdComputed) {
            long max = -1;
            for (byte[] key : reverse.shortNodeIds()) {
                long id = ByteBuffer.wrap(key).getLong(0);
                max = Math.max(max, id);
            }
            nextNodeId = Math.max(nextNodeId, max + 1);
            nextNodeIdComputed = true;
        }
        return nextNodeId;
    }

    ShortGuid toShort(FullGuid fullId) {
        NodeId nodeId = fullId.nodeId();
        long counter = fullId.counter();
        long shortNodeId = forward.getNodeId(nodeId);
        if (shortNodeId < 0) {
            long next = nextNodeId();
            if (next >= 0xFFFF_FFFFL)
                throw new IllegalStateException("Short node-id space exhausted");
            shortNodeId = next;
            nextNodeId = next + 1;
            forward.put(nodeId, shortNodeId);
            reverse.putReverse(shortNodeId, nodeId);
            dirty = true;
        }
        ShortGuid result = new ShortGuid();
        result.shortNodeId = (int) shortNodeId;
        result.counter = counter;
        return result;
    }

    ShortGuid toShort(byte[] fullId) {
        return toShort(FullGuid.fromBytes(fullId));
    }

    FullGuid toFull(ShortGuid guid) {
        NodeId nodeId = reverse.getNodeIdForShortId(Integer.toUnsignedLong(guid.shortNodeId));
        if (nodeId == null)
            throw new IllegalStateException("Missing reverse GUID mapping");
        return new FullGuid(nodeId, guid.counter);
    }

    void flush() {
        if (!dirty)
            return;
        unitRoots.put(unitId, UnitRootStore.GUID_FORWARD, forward.rootOffset());
        unitRoots.put(unitId, UnitRootStore.GUID_REVERSE, reverse.rootOffset());
        dirty = false;
    }

    static FullGuid fullId(NodeId nodeId, long counter) {
        return new FullGuid(nodeId, counter);
    }

    static String hex(FullGuid fullId) {
        return fullId.hex();
    }
}
