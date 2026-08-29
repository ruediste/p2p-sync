package com.github.ruediste.p2psync.libp2p.crypto.keys;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;

import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

import crypto.pb.Crypto;

/**
 * Ed25519 private key, backed by the JDK's built-in {@code "Ed25519"} {@link KeyPairGenerator}/
 * {@link Signature} providers (JEP 339, available since JDK 15) — no external crypto library.
 *
 * <p>
 * Ported from {@code io.libp2p.crypto.keys.Ed25519PrivateKey} (jvm-libp2p), which wraps
 * BouncyCastle's {@code Ed25519PrivateKeyParameters} instead. BouncyCastle exposes
 * {@code Ed25519PrivateKeyParameters.generatePublicKey()} to derive the paired public key from
 * just the 32-byte seed; the standard {@code java.security} API has no equivalent (a
 * {@link java.security.KeyFactory} can only reconstruct a {@code PrivateKey} object from a raw
 * seed via {@code EdECPrivateKeySpec}, it cannot re-derive the associated point). Instead,
 * {@link #unmarshal} feeds the raw seed bytes through a {@link SecureRandom} stand-in whose
 * {@code nextBytes} simply returns those exact bytes: {@link KeyPairGenerator} (algorithm
 * {@code "Ed25519"}, {@code SunEC} provider) consumes exactly 32 bytes from the supplied
 * {@link SecureRandom} as the private scalar/seed with no extra hashing/mixing, so this
 * reliably regenerates the identical key pair (verified in {@code Ed25519KeysTest}) — the same
 * technique {@link #generateKeyPair} itself effectively performs when the caller supplies a
 * real {@link SecureRandom} for fresh key generation.
 */
public final class Ed25519PrivateKey extends PrivKey {

    private final PrivateKey priv;
    private final Ed25519PublicKey pub;

    private Ed25519PrivateKey(PrivateKey priv, Ed25519PublicKey pub) {
        super(Crypto.KeyType.Ed25519);
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
     * Unmarshals a raw 32-byte Ed25519 seed (as produced by {@link #raw()}) back into an
     * {@link Ed25519PrivateKey} — see the class Javadoc for how the paired public key is
     * re-derived.
     */
    public static Ed25519PrivateKey unmarshal(byte[] rawSeed) {
        if (rawSeed.length != 32) {
            throw new IllegalArgumentException("Invalid Ed25519 private key seed length: " + rawSeed.length);
        }
        return fromKeyPairGenerator(new FixedSeedSecureRandom(rawSeed));
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

    /**
     * A {@link SecureRandom} that returns a fixed byte sequence instead of actual random data,
     * used only to feed a known 32-byte seed through {@link KeyPairGenerator} (see class
     * Javadoc). Never used for anything actually requiring randomness.
     */
    private static final class FixedSeedSecureRandom extends SecureRandom {
        private final byte[] seed;
        private int pos;

        FixedSeedSecureRandom(byte[] seed) {
            this.seed = seed;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (pos + bytes.length > seed.length) {
                throw new IllegalStateException("Ed25519 key pair generation consumed more than the 32-byte seed");
            }
            System.arraycopy(seed, pos, bytes, 0, bytes.length);
            pos += bytes.length;
        }
    }
}
