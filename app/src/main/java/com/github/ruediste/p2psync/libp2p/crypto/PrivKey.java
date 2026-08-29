package com.github.ruediste.p2psync.libp2p.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;

import crypto.pb.Crypto;

/**
 * A private key that can be used to derive its paired {@link PubKey} and sign data.
 *
 * <p>
 * Ported from {@code io.libp2p.core.crypto.PrivKey} (jvm-libp2p). The upstream top-level
 * {@code generateKeyPair(type, bits, random)} function (in {@code Key.kt}) is exposed here as
 * the static {@link #generate(KeyType)}/{@link #generate(KeyType, SecureRandom)} factory
 * methods instead, since Java has no top-level functions.
 */
public abstract class PrivKey {

    private final Crypto.KeyType keyType;

    protected PrivKey(Crypto.KeyType keyType) {
        this.keyType = keyType;
    }

    public final Crypto.KeyType getKeyType() {
        return keyType;
    }

    /**
     * Cryptographically signs the given bytes.
     */
    public abstract byte[] sign(byte[] data);

    /**
     * Returns the public key paired with this private key.
     */
    public abstract PubKey publicKey();

    /**
     * The raw key material, in the format expected by {@code crypto.proto}'s {@code Data} field
     * for this key's {@link #getKeyType()} (e.g. the 32-byte raw seed for Ed25519).
     */
    public abstract byte[] raw();

    /**
     * A serialized, storable representation of this key (the marshaled {@code crypto.pb.Crypto.PrivateKey}
     * protobuf message).
     */
    public final byte[] bytes() {
        return Marshaling.marshalPrivateKey(this);
    }

    /**
     * Generates a new key pair of the given type, returning its {@link PrivKey}
     * ({@code privKey.publicKey()} yields the paired {@link PubKey}).
     */
    public static PrivKey generate(KeyType type) {
        return generate(type, new SecureRandom());
    }

    public static PrivKey generate(KeyType type, SecureRandom random) {
        switch (type) {
            case ED25519:
                return Ed25519PrivateKey.generateKeyPair(random);
            default:
                throw new IllegalArgumentException("Unsupported key type: " + type);
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
        return Arrays.equals(bytes(), ((PrivKey) other).bytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(raw());
    }
}
