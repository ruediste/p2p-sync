package com.github.ruediste.p2psync.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;

public final class NodeId implements Comparable<NodeId> {
    public static final int LENGTH = 32;
    private static final byte[] DOMAIN = "p2p-sync:node-id:v1:".getBytes(StandardCharsets.US_ASCII);

    private final byte[] bytes;
    private final int hashCode;

    private NodeId(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH)
            throw new IllegalArgumentException("NodeId must contain 32 bytes");
        this.bytes = bytes.clone();
        hashCode = Arrays.hashCode(this.bytes);
    }

    public static NodeId fromBytes(byte[] bytes) {
        return new NodeId(bytes);
    }

    public static NodeId fromPublicKey(PubKey publicKey) {
        return new NodeId(derive(DOMAIN, publicKey));
    }

    private static byte[] derive(byte[] domain, PubKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");
        byte[] key = publicKey.bytes();
        return CryptoUtils.sha256(ByteBuffer.allocate(domain.length + key.length)
                .put(domain).put(key).array());
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String hex() {
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public int compareTo(NodeId other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NodeId id && Arrays.equals(bytes, id.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return hex();
    }
}