package com.github.ruediste.p2psync.libp2p.crypto;

import java.util.Arrays;

import crypto.pb.Crypto;

/**
 * A public key that can verify signatures produced by its paired {@link PrivKey}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.crypto.PubKey} (jvm-libp2p).
 */
public abstract class PubKey {

    private final Crypto.KeyType keyType;

    protected PubKey(Crypto.KeyType keyType) {
        this.keyType = keyType;
    }

    public final Crypto.KeyType getKeyType() {
        return keyType;
    }

    /**
     * Verifies that {@code signature} is a valid signature of {@code data} produced by the
     * paired private key.
     */
    public abstract boolean verify(byte[] data, byte[] signature);

    /**
     * The raw key material, in the format expected by {@code crypto.proto}'s {@code Data} field
     * for this key's {@link #getKeyType()} (e.g. the 32-byte raw point for Ed25519).
     */
    public abstract byte[] raw();

    /**
     * A serialized, storable representation of this key (the marshaled {@code crypto.pb.Crypto.PublicKey}
     * protobuf message).
     */
    public final byte[] bytes() {
        return Marshaling.marshalPublicKey(this);
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
