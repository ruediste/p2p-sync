package com.github.ruediste.p2psync.libp2p.crypto.keys;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

import crypto.pb.Crypto;

/**
 * Ed25519 public key, backed by the JDK's built-in {@code "Ed25519"} {@link java.security.KeyFactory}/
 * {@link Signature} providers (JEP 339, available since JDK 15) — no external crypto library.
 *
 * <p>
 * Ported from {@code io.libp2p.crypto.keys.Ed25519PublicKey} (jvm-libp2p), which wraps
 * BouncyCastle's {@code Ed25519PublicKeyParameters} instead.
 */
public final class Ed25519PublicKey extends PubKey {

    static final String ALGORITHM = "Ed25519";
    static final NamedParameterSpec PARAMS = new NamedParameterSpec(ALGORITHM);

    private final PublicKey pub;

    Ed25519PublicKey(PublicKey pub) {
        super(Crypto.KeyType.Ed25519);
        this.pub = pub;
    }

    /**
     * The raw 32-byte Ed25519 public key point, matching {@code Data} in {@code crypto.proto}.
     *
     * <p>
     * The JDK's X.509 ({@code SubjectPublicKeyInfo}) encoding of an Ed25519 public key is a
     * fixed 12-byte DER header followed by the 32 raw key bytes — see RFC 8410 §4 — so the raw
     * point is simply the encoding's last 32 bytes.
     */
    @Override
    public byte[] raw() {
        byte[] encoded = pub.getEncoded();
        return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
    }

    @Override
    public boolean verify(byte[] data, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(pub);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 verification failed", e);
        }
    }

    /**
     * Unmarshals a raw 32-byte Ed25519 public key point (as produced by {@link #raw()}) back
     * into an {@link Ed25519PublicKey}.
     *
     * <p>
     * Per RFC 8032 §5.1.2, the 32-byte encoding is the little-endian {@code y} coordinate with
     * the sign of {@code x} folded into the most-significant bit of the last byte; this is
     * exactly what {@link EdECPoint} represents, so decoding is a direct (standard JCA API)
     * translation, no hand-rolled curve arithmetic needed.
     */
    public static Ed25519PublicKey unmarshal(byte[] raw) {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Invalid Ed25519 public key length: " + raw.length);
        }
        byte[] littleEndianY = raw.clone();
        boolean xOdd = (littleEndianY[31] & 0x80) != 0;
        littleEndianY[31] &= 0x7f;
        byte[] bigEndianY = new byte[32];
        for (int i = 0; i < 32; i++) {
            bigEndianY[i] = littleEndianY[31 - i];
        }
        EdECPoint point = new EdECPoint(xOdd, new BigInteger(1, bigEndianY));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PublicKey pub = keyFactory.generatePublic(new EdECPublicKeySpec(PARAMS, point));
            return new Ed25519PublicKey(pub);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        }
    }

    static Ed25519PublicKey fromJdkKey(PublicKey pub) {
        return new Ed25519PublicKey(pub);
    }
}
