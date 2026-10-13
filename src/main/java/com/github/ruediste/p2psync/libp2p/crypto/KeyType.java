package com.github.ruediste.p2psync.libp2p.crypto;

import com.github.ruediste.p2psync.proto.Libp2P;

/**
 * Enumerates the libp2p key types this port knows how to generate/marshal.
 *
 * <p>
 * Ported (trimmed) from {@code io.libp2p.core.crypto.KeyType} (jvm-libp2p).
 * Upstream also lists
 * {@code RSA}/{@code SECP256K1}/{@code ECDSA}; only {@code ED25519} is
 * implemented here (see
 * "Future extension points" in {@code ImplementationPlan.md}) — adding a case
 * for another type
 * is a small, mechanical change once the underlying signature primitive exists.
 */
public enum KeyType {

    ED25519(Libp2P.KeyType.Ed25519);

    private final Libp2P.KeyType proto;

    KeyType(Libp2P.KeyType proto) {
        this.proto = proto;
    }

    /**
     * The corresponding generated
     * {@code com.github.ruediste.p2psync.proto.libp2p.Crypto.KeyType} protobuf enum
     * constant, as
     * stored in marshaled {@code PublicKey}/{@code PrivateKey} messages.
     */
    public Libp2P.KeyType toProto() {
        return proto;
    }

    /**
     * Reverse lookup used by {@link Marshaling} when unmarshaling a key.
     *
     * @throws IllegalArgumentException if {@code proto} isn't a supported/known key
     *                                  type
     */
    public static KeyType fromProto(Libp2P.KeyType proto) {
        for (KeyType type : values()) {
            if (type.proto == proto) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported key type: " + proto);
    }
}
