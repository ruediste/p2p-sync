package com.github.ruediste.p2psync.node;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.util.FullGuid;

public final class SyncUnitId implements Comparable<SyncUnitId> {
    public static final int LENGTH = FullGuid.LENGTH;
    private static final byte[] USER_NAMESPACE = "p2p-sync:user-unit:v1:".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final FullGuid fullGuid;

    public SyncUnitId(FullGuid fullGuid) {
        this.fullGuid = java.util.Objects.requireNonNull(fullGuid);
    }

    public FullGuid fullGuid() {
        return fullGuid;
    }

    public static SyncUnitId create() {
        byte[] nodeBytes = new byte[NodeId.LENGTH];
        RANDOM.nextBytes(nodeBytes);
        return new SyncUnitId(new FullGuid(NodeId.fromBytes(nodeBytes), RANDOM.nextLong()));
    }

    /** Deterministic per-node unit id: NodeId prefix followed by a counter. */
    public static SyncUnitId create(NodeId nodeId, long counter) {
        if (nodeId == null)
            throw new IllegalArgumentException("NodeId must not be null");
        return new SyncUnitId(new FullGuid(nodeId, counter));
    }

    public static SyncUnitId forUser(UserId userId) {
        byte[] userIdBytes = userId.bytes();
        byte[] input = new byte[USER_NAMESPACE.length + userIdBytes.length];
        System.arraycopy(USER_NAMESPACE, 0, input, 0, USER_NAMESPACE.length);
        System.arraycopy(userIdBytes, 0, input, USER_NAMESPACE.length, userIdBytes.length);
        return new SyncUnitId(new FullGuid(NodeId.fromBytes(CryptoUtils.sha256(input)), 0));
    }

    public static SyncUnitId fromBytes(byte[] bytes) {
        return new SyncUnitId(FullGuid.fromBytes(bytes));
    }

    public byte[] bytes() {
        return fullGuid.bytes();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SyncUnitId id && fullGuid.equals(id.fullGuid);
    }

    @Override
    public int hashCode() {
        return fullGuid.hashCode();
    }

    @Override
    public int compareTo(SyncUnitId other) {
        return java.util.Arrays.compareUnsigned(bytes(), other.bytes());
    }

    @Override
    public String toString() {
        return fullGuid.hex();
    }
}
