package com.github.ruediste.p2psync.libp2p.crypto.keys;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.EdECPrivateKeySpec;

import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.proto.Libp2P;

/**
 * Ed25519 private key, backed by the JDK's built-in {@code "Ed25519"}
 * {@link KeyPairGenerator}/{@link Signature} providers (JEP 339, available since
 * JDK 15) — no external crypto library.
 *
 * <p>
 * Ported from {@code io.libp2p.crypto.keys.Ed25519PrivateKey} (jvm-libp2p),
 * which wraps BouncyCastle's {@code Ed25519PrivateKeyParameters} instead. Fresh
 * keys are generated through the standard {@link KeyPairGenerator}. When
 * reconstructing a key from its 32-byte seed, {@link #unmarshal} creates the
 * {@link PrivateKey} via {@link EdECPrivateKeySpec} and derives the paired
 * public key with the RFC 8032 scalar multiplication in
 * {@link #derivePublicKey(byte[])}, avoiding any reliance on provider-internal
 * {@link SecureRandom} consumption.
 */
public final class Ed25519PrivateKey extends PrivKey {

    private final PrivateKey priv;
    private final Ed25519PublicKey pub;

    private Ed25519PrivateKey(PrivateKey priv, Ed25519PublicKey pub) {
        super(Libp2P.KeyType.Ed25519);
        this.priv = priv;
        this.pub = pub;
    }

    /**
     * The raw 32-byte seed, matching {@code Data} in {@code crypto.proto}.
     */
    @Override
    public byte[] raw() {
        return ((EdECPrivateKey) priv).getBytes()
                .orElseThrow(() -> new IllegalStateException("Ed25519 private key has no extractable raw seed"));
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance(Ed25519PublicKey.ALGORITHM);
            signer.initSign(priv);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    @Override
    public PubKey publicKey() {
        return pub;
    }

    /**
     * Generates a fresh Ed25519 key pair using a new default {@link SecureRandom}.
     */
    public static Ed25519PrivateKey generateKeyPair() {
        return generateKeyPair(new SecureRandom());
    }

    /**
     * Generates a fresh Ed25519 key pair.
     */
    public static Ed25519PrivateKey generateKeyPair(SecureRandom random) {
        return fromKeyPairGenerator(random);
    }

    /**
     * Unmarshals a raw 32-byte Ed25519 seed (as produced by {@link #raw()}) back
     * into an {@link Ed25519PrivateKey}.
     *
     * <p>
     * The private key object is created directly from the seed via the standard
     * {@link EdECPrivateKeySpec}. The paired public key is derived by the
     * RFC 8032 Ed25519 scalar multiplication itself (see
     * {@link #derivePublicKey(byte[])}), so this no longer depends on a provider
     * internally consuming a fake {@link SecureRandom} in a specific way.
     */
    public static Ed25519PrivateKey unmarshal(byte[] rawSeed) {
        if (rawSeed.length != 32) {
            throw new IllegalArgumentException("Invalid Ed25519 private key seed length: " + rawSeed.length);
        }
        try {
            PrivateKey priv = KeyFactory.getInstance(Ed25519PublicKey.ALGORITHM)
                    .generatePrivate(new EdECPrivateKeySpec(Ed25519PublicKey.PARAMS, rawSeed));
            Ed25519PublicKey pub = Ed25519PublicKey.unmarshal(derivePublicKey(rawSeed));
            return new Ed25519PrivateKey(priv, pub);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 key reconstruction failed", e);
        }
    }

    // ---- RFC 8032 public-key derivation from a seed ----

    private static final BigInteger P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));

    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    private static final BigInteger BASE_Y = BigInteger.valueOf(4)
            .multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);

    private static final BigInteger BASE_X = new BigInteger(
            "15112221349535400772501151409588531511454012693041857206046113283949847762202");

    /**
     * Derives the Ed25519 public key from the 32-byte seed: hash with SHA-512,
     * clamp the lower half, and compute {@code [s]B} on the twisted Edwards
     * curve. Only used when reconstructing an existing private key; fresh key
     * generation still uses {@link KeyPairGenerator}.
     */
    static byte[] derivePublicKey(byte[] seed) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-512").digest(seed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is not available", e);
        }
        byte[] scalarBytes = new byte[32];
        System.arraycopy(hash, 0, scalarBytes, 0, 32);
        scalarBytes[0] &= (byte) 248;
        scalarBytes[31] &= (byte) 127;
        scalarBytes[31] |= (byte) 64;
        BigInteger scalar = fromLittleEndian(scalarBytes);

        BigInteger x = BigInteger.ZERO;
        BigInteger y = BigInteger.ONE;
        for (int i = scalar.bitLength() - 1; i >= 0; i--) {
            BigInteger[] doubled = addPoints(x, y, x, y);
            x = doubled[0];
            y = doubled[1];
            if (scalar.testBit(i)) {
                BigInteger[] added = addPoints(x, y, BASE_X, BASE_Y);
                x = added[0];
                y = added[1];
            }
        }

        byte[] encoded = toLittleEndian(y, 32);
        if (x.testBit(0)) {
            encoded[31] |= (byte) 0x80;
        }
        return encoded;
    }

    private static BigInteger[] addPoints(BigInteger x1, BigInteger y1, BigInteger x2,
            BigInteger y2) {
        BigInteger x1y2 = x1.multiply(y2).mod(P);
        BigInteger x2y1 = x2.multiply(y1).mod(P);
        BigInteger x1x2 = x1.multiply(x2).mod(P);
        BigInteger y1y2 = y1.multiply(y2).mod(P);
        BigInteger dProduct = D.multiply(x1x2).multiply(y1y2).mod(P);

        BigInteger x3 = x1y2.add(x2y1).mod(P)
                .multiply(BigInteger.ONE.add(dProduct).mod(P).modInverse(P)).mod(P);
        BigInteger y3 = y1y2.add(x1x2).mod(P)
                .multiply(BigInteger.ONE.subtract(dProduct).mod(P).modInverse(P)).mod(P);
        return new BigInteger[] { x3, y3 };
    }

    private static BigInteger fromLittleEndian(byte[] bytes) {
        byte[] bigEndian = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            bigEndian[i] = bytes[bytes.length - 1 - i];
        }
        return new BigInteger(1, bigEndian);
    }

    private static byte[] toLittleEndian(BigInteger value, int length) {
        byte[] bigEndian = value.toByteArray();
        byte[] littleEndian = new byte[length];
        int copy = Math.min(bigEndian.length, length);
        for (int i = 0; i < copy; i++) {
            littleEndian[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return littleEndian;
    }

    private static Ed25519PrivateKey fromKeyPairGenerator(SecureRandom random) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(Ed25519PublicKey.ALGORITHM);
            generator.initialize(Ed25519PublicKey.PARAMS, random);
            KeyPair keyPair = generator.generateKeyPair();
            return new Ed25519PrivateKey(keyPair.getPrivate(), Ed25519PublicKey.fromJdkKey(keyPair.getPublic()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 key pair generation failed", e);
        }
    }

}
