package com.github.ruediste.p2psync.libp2p.crypto;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PublicKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import crypto.pb.Crypto;

/**
 * Thin (un)marshaling helpers converting between {@link PubKey}/{@link PrivKey} instances and
 * the generated {@code crypto.pb.Crypto.PublicKey}/{@code Crypto.PrivateKey} protobuf messages.
 *
 * <p>
 * Ported from the marshaling functions in {@code io.libp2p.core.crypto.Key} (jvm-libp2p);
 * trimmed to dispatch only to the Ed25519 implementation (see "Future extension points" in
 * {@code ImplementationPlan.md}).
 */
public final class Marshaling {

    private Marshaling() {
    }

    /**
     * Converts a public key object into a protobuf-serialized public key.
     */
    public static byte[] marshalPublicKey(PubKey pubKey) {
        return Crypto.PublicKey.newBuilder()
                .setType(pubKey.getKeyType())
                .setData(ByteString.copyFrom(pubKey.raw()))
                .build()
                .toByteArray();
    }

    /**
     * Converts a private key object into a protobuf-serialized private key.
     */
    public static byte[] marshalPrivateKey(PrivKey privKey) {
        return Crypto.PrivateKey.newBuilder()
                .setType(privKey.getKeyType())
                .setData(ByteString.copyFrom(privKey.raw()))
                .build()
                .toByteArray();
    }

    /**
     * Converts the protobuf-serialized public key into its representative object.
     */
    public static PubKey unmarshalPublicKey(byte[] data) {
        Crypto.PublicKey proto;
        try {
            proto = Crypto.PublicKey.parseFrom(data);
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

    /**
     * Converts the protobuf-serialized private key into its representative object.
     */
    public static PrivKey unmarshalPrivateKey(byte[] data) {
        Crypto.PrivateKey proto;
        try {
            proto = Crypto.PrivateKey.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid marshaled private key", e);
        }
        byte[] raw = proto.getData().toByteArray();
        switch (proto.getType()) {
            case Ed25519:
                return Ed25519PrivateKey.unmarshal(raw);
            default:
                throw new IllegalArgumentException("Unsupported key type: " + proto.getType());
        }
    }
}
