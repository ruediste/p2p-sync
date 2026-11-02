package com.github.ruediste.p2psync.util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A full entity id consisting of a 32-byte node hash followed by an 8-byte
 * counter. The byte representation is the 40-byte big-endian encoding stored in
 * user operations and file-tree state.
 */
public final class FullGuid {
    public static final int LENGTH = 40;
    private static final int NODE_HASH_LENGTH = 32;

    private final byte[] nodeHash;
    private final long counter;

    public FullGuid(byte[] nodeHash, long counter) {
        Objects.requireNonNull(nodeHash, "nodeHash");
        if (nodeHash.length != NODE_HASH_LENGTH)
            throw new IllegalArgumentException("Node hash must contain 32 bytes");
        this.nodeHash = nodeHash.clone();
        this.counter = counter;
    }

    public static FullGuid fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH)
            throw new IllegalArgumentException("Full entity id must contain 40 bytes");
        return new FullGuid(Arrays.copyOfRange(bytes, 0, NODE_HASH_LENGTH),
                ByteBuffer.wrap(bytes, NODE_HASH_LENGTH, Long.BYTES).getLong());
    }

    public static FullGuid fromHex(String hex) {
        Objects.requireNonNull(hex, "hex");
        return fromBytes(HexFormat.of().parseHex(hex));
    }

    public byte[] nodeHash() {
        return nodeHash.clone();
    }

    public long counter() {
        return counter;
    }

    public byte[] bytes() {
        return ByteBuffer.allocate(LENGTH).put(nodeHash).putLong(counter).array();
    }

    public String hex() {
        return HexFormat.of().formatHex(bytes());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FullGuid other && counter == other.counter
                && Arrays.equals(nodeHash, other.nodeHash);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(counter) + Arrays.hashCode(nodeHash);
    }

    @Override
    public String toString() {
        return hex();
    }
}
