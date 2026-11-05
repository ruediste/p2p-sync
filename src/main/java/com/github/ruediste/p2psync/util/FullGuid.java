package com.github.ruediste.p2psync.util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import com.github.ruediste.p2psync.identity.NodeId;

/**
 * A full entity id consisting of a 32-byte NodeId followed by an 8-byte
 * counter. The byte representation is the 40-byte big-endian encoding stored in
 * user operations and file-tree state.
 */
public final class FullGuid {
    public static final int LENGTH = 40;
    private static final int NODE_ID_LENGTH = NodeId.LENGTH;

    private final NodeId nodeId;
    private final long counter;

    public FullGuid(NodeId nodeId, long counter) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.counter = counter;
    }

    public static FullGuid fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH)
            throw new IllegalArgumentException("Full entity id must contain 40 bytes");
        return new FullGuid(NodeId.fromBytes(Arrays.copyOfRange(bytes, 0, NODE_ID_LENGTH)),
                ByteBuffer.wrap(bytes, NODE_ID_LENGTH, Long.BYTES).getLong());
    }

    public static FullGuid fromHex(String hex) {
        Objects.requireNonNull(hex, "hex");
        return fromBytes(HexFormat.of().parseHex(hex));
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public long counter() {
        return counter;
    }

    public byte[] bytes() {
        return ByteBuffer.allocate(LENGTH).put(nodeId.bytes()).putLong(counter).array();
    }

    public String hex() {
        return HexFormat.of().formatHex(bytes());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FullGuid other && counter == other.counter && nodeId.equals(other.nodeId);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(counter) + nodeId.hashCode();
    }

    @Override
    public String toString() {
        return hex();
    }
}
