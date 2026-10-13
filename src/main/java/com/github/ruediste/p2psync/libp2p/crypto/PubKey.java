package com.github.ruediste.p2psync.libp2p.crypto;

import java.util.Arrays;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PublicKey;
import com.github.ruediste.p2psync.proto.Libp2P;
import com.github.ruediste.p2psync.proto.Libp2P.PublicKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * A public key that can verify signatures produced by its paired
 * {@link PrivKey}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.crypto.PubKey} (jvm-libp2p).
 */
public abstract class PubKey {

    private final Libp2P.KeyType keyType;

    protected PubKey(Libp2P.KeyType keyType) {
        this.keyType = keyType;
    }

    public final Libp2P.KeyType getKeyType() {
        return keyType;
    }

    /**
     * Verifies that {@code signature} is a valid signature of {@code data} produced
     * by the
     * paired private key.
     */
    public abstract boolean verify(byte[] data, byte[] signature);

    /**
     * The raw key material, in the format expected by {@code crypto.proto}'s
     * {@code Data} field
     * for this key's {@link #getKeyType()} (e.g. the 32-byte raw point for
     * Ed25519).
     */
    public abstract byte[] raw();

    /**
     * A serialized, storable representation of this key (the marshaled
     * {@code com.github.ruediste.p2psync.proto.libp2p.Crypto.PublicKey}
     * protobuf message).
     */
    public final byte[] bytes() {
        return this.toProto().toByteArray();
    }

    public PublicKey toProto() {
        return Libp2P.PublicKey.newBuilder()
                .setType(getKeyType())
                .setData(ByteString.copyFrom(raw()))
                .build();
    }

    public static PubKey from(byte[] data) {
        Libp2P.PublicKey proto;
        try {
            proto = Libp2P.PublicKey.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid marshaled public key", e);
        }
        byte[] raw = proto.getData().toByteArray();
        switch (proto.getType()) {
            case Ed25519:
                return Ed25519PublicKey.unmarshal(raw);
            default:
                throw new IllegalArgumentException("Unsupported key type: " + proto.getType());
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return Arrays.equals(bytes(), ((PubKey) other).bytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(raw());
    }
}
