package com.github.ruediste.p2psync.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;

public final class UserId implements Comparable<UserId> {
    public static final int LENGTH = 32;
    private static final byte[] DOMAIN = "p2p-sync:user-id:v1:".getBytes(StandardCharsets.US_ASCII);

    private final byte[] bytes;
    private final int hashCode;

    private UserId(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH)
            throw new IllegalArgumentException("UserId must contain 32 bytes");
        this.bytes = bytes.clone();
        hashCode = Arrays.hashCode(this.bytes);
    }

    public static UserId fromBytes(byte[] bytes) {
        return new UserId(bytes);
    }

    public static UserId fromPublicKey(PubKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey");
        byte[] key = publicKey.bytes();
        return new UserId(CryptoUtils.sha256(ByteBuffer.allocate(DOMAIN.length + key.length)
                .put(DOMAIN).put(key).array()));
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String hex() {
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public int compareTo(UserId other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserId id && Arrays.equals(bytes, id.bytes);
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