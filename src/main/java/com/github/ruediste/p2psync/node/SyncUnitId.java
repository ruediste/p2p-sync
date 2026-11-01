package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;

public final class SyncUnitId implements Comparable<SyncUnitId> {
    private static final byte[] USER_PREFIX = "p2p-sync:user-unit:v1:".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] bytes;
    private final int hashCode;

    private SyncUnitId(byte[] bytes) {
        if (bytes.length != 32)
            throw new IllegalArgumentException("SyncUnit IDs must contain 32 bytes");
        this.bytes = bytes.clone();
        this.hashCode = Arrays.hashCode(this.bytes);
    }

    public static SyncUnitId create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new SyncUnitId(bytes);
    }

    /** Deterministic per-node unit id: node-hash prefix followed by a counter. */
    public static SyncUnitId create(byte[] nodeHash, long counter) {
        if (nodeHash == null || nodeHash.length != 32)
            throw new IllegalArgumentException("Node hash must contain 32 bytes");
        byte[] bytes = new byte[32];
        System.arraycopy(nodeHash, 0, bytes, 0, 24);
        ByteBuffer.wrap(bytes, 24, Long.BYTES).putLong(counter);
        return new SyncUnitId(bytes);
    }

    public static SyncUnitId forUser(PubKey userId) {
        byte[] userKey = userId.bytes();
        byte[] input = ByteBuffer.allocate(USER_PREFIX.length + userKey.length)
                .put(USER_PREFIX).put(userKey).array();
        return new SyncUnitId(CryptoUtils.sha256(input));
    }

    public static SyncUnitId fromBytes(byte[] bytes) {
        return new SyncUnitId(bytes);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SyncUnitId id && Arrays.equals(bytes, id.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(SyncUnitId other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }

    @Override
    public String toString() {
        return HexFormat.of().formatHex(bytes);
    }
}
