package com.github.ruediste.p2psync.libp2p.security.noise;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.Marshaling;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.security.CantDecryptInboundException;
import com.github.ruediste.p2psync.libp2p.security.InvalidRemotePubKeyException;
import com.github.ruediste.p2psync.libp2p.security.MalformedNoiseHandshakeException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import spipe.pb.Spipe;

/**
 * The Noise {@code XX} handshake for libp2p (protocol name
 * {@code Noise_XX_25519_ChaChaPoly_SHA256}), implemented directly on top of the
 * JDK's {@code X25519} + {@code ChaCha20-Poly1305} + SHA-256 providers — no
 * external {@code noise-java} dependency.
 *
 * <p>
 * This is the blocking-I/O deviation from the original Netty-based plan (see
 * {@code ARCHITECTURE.md}): upstream jvm-libp2p implements the same handshake
 * as
 * a Netty {@code channelRead} state machine wrapped in a
 * {@code UShortLengthCodec}. Here {@link #run(P2PInputStream, P2POutputStream,
 * PrivKey, boolean, PeerId)} is a single synchronous request/response loop —
 * the
 * XX pattern is inherently three blocking round-trips — that reads/writes
 * 2-byte-big-endian-length-prefixed (max 65535) Noise messages directly against
 * the connection's raw streams and returns the two post-split
 * {@link CipherState}s (one per direction).
 *
 * <p>
 * Wire format / crypto behavior follows the libp2p noise spec:
 * <ul>
 * <li>single cipher suite {@code Noise_XX_25519_ChaChaPoly_SHA256} (XX pattern:
 * {@code e / e, ee, s, es / s, se});</li>
 * <li>chaChaPoly nonce = 12 bytes: four zero bytes followed by the 8-byte
 * little-endian counter;</li>
 * <li>handshake payload = {@code spipe.pb.Spipe.NoiseHandshakePayload} with the
 * libp2p identity public key and a signature of
 * {@code "noise-libp2p-static-key:" || noiseStaticPubKey} made by the identity
 * private key;</li>
 * <li>the Noise static key is distinct from the identity key and is (like
 * upstream) generated once per process;</li>
 * <li>after the handshake, transport frames are independent length-prefixed
 * AEAD ciphertexts (handled by {@link NoiseXXFramedInputStream}/
 * {@link NoiseXXFramedOutputStream}).</li>
 * </ul>
 *
 * <p>
 * Protocol-level violations (bad signature, wrong peer id, failed AEAD
 * authentication) surface as plain {@link RuntimeException}s (per the M1
 * deviation note); real I/O failures already surface as
 * {@link UncheckedIOException} from the underlying streams.
 */
public final class NoiseXXHandshake {

    /** The libp2p Noise protocol name. */
    public static final String PROTOCOL_NAME = "Noise_XX_25519_ChaChaPoly_SHA256";

    /** Prefix of the identity-key signature over the Noise static public key. */
    static final String NOISE_SIGNATURE_PHRASE = "noise-libp2p-static-key:";

    /** Maximum length of a single Noise message (handshake or transport). */
    static final int MAX_FRAME_LENGTH = 0xFFFF;

    /** ChaChaPoly authentication tag length. */
    static final int TAG_LENGTH = 16;

    /** X25519 public key / DH output length. */
    static final int DH_LENGTH = 32;

    /** Largest plaintext chunk that fits in a single Noise transport frame. */
    static final int SPLIT_MAX = MAX_FRAME_LENGTH - TAG_LENGTH;

    private static final byte[] EMPTY = new byte[0];

    // libp2p's Noise static key is a single per-process keypair (matches
    // upstream's process-wide `localStaticPrivateKey25519` companion object);
    // it is distinct from the peer identity key.
    private static volatile byte[] processStaticPublic;
    private static volatile byte[] processStaticPrivate;

    private NoiseXXHandshake() {
    }

    /**
     * Runs the full XX handshake against {@code rawIn}/{@code rawOut}.
     *
     * <p>
     * Both parties must call this concurrently (each side blocks on reads). The
     * initiator's {@code expectedRemotePeerId} — typically taken from a dialed
     * multiaddr's {@code /p2p/} component — is verified against the peer id
     * extracted from the responder's handshake payload; a mismatch is rejected
     * with {@link InvalidRemotePubKeyException}.
     *
     * @return the session: the two peers' identities plus the outbound/inbound
     *         {@link CipherState}s for subsequent transport frames.
     */
    public static Result run(P2PStream raw, PrivKey localIdentityKey,
            PeerId expectedRemotePeerId) {

        P2PInputStream rawIn = raw.getIn();
        P2POutputStream rawOut = raw.getOut();
        boolean initiator = raw.isInitiator();
        loadStaticKeyPair();
        HandshakeState state = new HandshakeState(initiator);
        PeerId localId = PeerId.fromPubKey(localIdentityKey.publicKey());
        byte[] identityPayload = buildPayload(localIdentityKey);

        if (initiator) {
            byte[] msg1 = state.writeMessage(EMPTY);
            writeFrame(rawOut, msg1);

            byte[] msg2 = readFrame(rawIn);
            byte[] remotePayload = state.readMessage(msg2);
            PubKey remotePubKey = verifyPayload(remotePayload, state.remoteStaticKey());
            PeerId remoteId = PeerId.fromPubKey(remotePubKey);
            if (expectedRemotePeerId != null && !expectedRemotePeerId.equals(remoteId)) {
                throw new InvalidRemotePubKeyException(
                        "Peer id mismatch: expected " + expectedRemotePeerId + " but got "
                                + remoteId);
            }

            byte[] msg3 = state.writeMessage(identityPayload);
            writeFrame(rawOut, msg3);

            CipherStatePair keys = state.split();
            return new Result(localId, remoteId, remotePubKey, keys.outbound, keys.inbound);
        } else {
            byte[] msg1 = readFrame(rawIn);
            state.readMessage(msg1);

            byte[] msg2 = state.writeMessage(identityPayload);
            writeFrame(rawOut, msg2);

            byte[] msg3 = readFrame(rawIn);
            byte[] remotePayload = state.readMessage(msg3);
            PubKey remotePubKey = verifyPayload(remotePayload, state.remoteStaticKey());

            CipherStatePair keys = state.split();
            return new Result(localId, PeerId.fromPubKey(remotePubKey), remotePubKey, keys.outbound, keys.inbound);
        }
    }

    /** Convenience overload for the responder or when no peer check is wanted. */
    public static Result run(P2PStream raw, PrivKey localIdentityKey) {
        return run(raw, localIdentityKey, null);
    }

    // ------------------------------------------------------------------
    // Handshake payload (signing + verification)
    // ------------------------------------------------------------------

    private static byte[] buildPayload(PrivKey localIdentityKey) {
        byte[] identityPublicKey = Marshaling.marshalPublicKey(localIdentityKey.publicKey());
        byte[] phrase = noiseSignaturePhrase(processStaticPublic);
        byte[] signature = localIdentityKey.sign(phrase);
        return Spipe.NoiseHandshakePayload.newBuilder()
                .setLibp2PKey(ByteString.copyFrom(identityPublicKey))
                .setNoiseStaticKeySignature(ByteString.copyFrom(signature))
                .build()
                .toByteArray();
    }

    private static PubKey verifyPayload(byte[] payload, byte[] remoteStaticPublicKey) {
        if (remoteStaticPublicKey == null) {
            throw new InvalidRemotePubKeyException("Remote did not send a Noise static key during the handshake");
        }
        Spipe.NoiseHandshakePayload proto;
        try {
            proto = Spipe.NoiseHandshakePayload.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidRemotePubKeyException("Malformed Noise handshake payload", e);
        }
        PubKey pubKey;
        try {
            pubKey = Marshaling.unmarshalPublicKey(proto.getLibp2PKey().toByteArray());
        } catch (IllegalArgumentException e) {
            throw new InvalidRemotePubKeyException("Malformed identity public key in Noise handshake payload", e);
        }
        byte[] signature = proto.getNoiseStaticKeySignature().toByteArray();
        if (!pubKey.verify(noiseSignaturePhrase(remoteStaticPublicKey), signature)) {
            throw new InvalidRemotePubKeyException("Invalid signature over the remote Noise static public key");
        }
        return pubKey;
    }

    private static byte[] noiseSignaturePhrase(byte[] noiseStaticPublicKey) {
        byte[] prefix = NOISE_SIGNATURE_PHRASE.getBytes(StandardCharsets.US_ASCII);
        return concat(prefix, noiseStaticPublicKey);
    }

    // ------------------------------------------------------------------
    // Message framing (2-byte big-endian length prefix, max 65535)
    // ------------------------------------------------------------------

    private static void writeFrame(P2POutputStream out, byte[] message) {
        if (message.length > MAX_FRAME_LENGTH) {
            throw new IllegalStateException("Noise message too long: " + message.length);
        }
        out.write((message.length >>> 8) & 0xFF);
        out.write(message.length & 0xFF);
        out.write(message);
        // the handshake is three blocking round-trips: each side writes a
        // complete message, then blocks on a read. Without an explicit flush
        // the message could sit in a buffered peer indefinitely and both sides
        // would deadlock.
        out.flush();
    }

    private static byte[] readFrame(P2PInputStream in) {
        int hi = in.read();
        if (hi < 0) {
            throw new UncheckedIOException(new EOFException("Connection closed during Noise handshake"));
        }
        int lo = in.read();
        if (lo < 0) {
            throw new UncheckedIOException(new EOFException("Truncated Noise frame length header"));
        }
        int length = (hi << 8) | lo;
        if (length > MAX_FRAME_LENGTH) {
            throw new IllegalStateException("Noise frame length out of range: " + length);
        }
        byte[] message = new byte[length];
        in.readFully(message);
        return message;
    }

    // ------------------------------------------------------------------
    // Static Noise key (process-wide, like upstream)
    // ------------------------------------------------------------------

    private static synchronized void loadStaticKeyPair() {
        if (processStaticPublic != null) {
            return;
        }
        byte[][] pair = X25519.generateKeyPair();
        processStaticPrivate = pair[0];
        processStaticPublic = pair[1];
    }

    // ------------------------------------------------------------------
    // Result / session value
    // ------------------------------------------------------------------

    /** Outcome of a completed XX handshake. */
    public static final class Result {
        private final PeerId localId;
        private final PeerId remoteId;
        private final PubKey remotePubKey;
        private final CipherState outbound;
        private final CipherState inbound;

        Result(PeerId localId, PeerId remoteId, PubKey remotePubKey, CipherState outbound, CipherState inbound) {
            this.localId = localId;
            this.remoteId = remoteId;
            this.remotePubKey = remotePubKey;
            this.outbound = outbound;
            this.inbound = inbound;
        }

        public PeerId getLocalId() {
            return localId;
        }

        public PeerId getRemoteId() {
            return remoteId;
        }

        public PubKey getRemotePubKey() {
            return remotePubKey;
        }

        /** {@link CipherState} for encrypting outbound transport frames. */
        public CipherState getOutboundCipher() {
            return outbound;
        }

        /** {@link CipherState} for decrypting inbound transport frames. */
        public CipherState getInboundCipher() {
            return inbound;
        }
    }

    // ------------------------------------------------------------------
    // Core Noise primitives
    // ------------------------------------------------------------------

    /**
     * A Noise {@code CipherState}: a 32-byte key (or empty = "no key" for the
     * plaintext handshake phase) plus a monotonically increasing 64-bit counter
     * nonce, used for both handshake and (post-split) transport AEAD operations.
     *
     * <p>
     * Instances are <b>thread-safe</b>: {@link #encryptWithAd} and
     * {@link #decryptWithAd} are synchronized on the instance, so a given AEAD
     * counter is allocated, used, and incremented atomically. Without this, two
     * threads could read the same {@code nonce}, encrypt with it, and reuse a
     * key/nonce pair — catastrophic for ChaCha20-Poly1305. Atomic, but note that
     * this only protects the counter: the framed streams additionally serialize
     * wire emission so frame order matches nonce order.
     */
    public static final class CipherState {

        /**
         * Largest Noise AEAD nonce (2^64 - 1). The Noise spec mandates the
         * counter must never wrap: reaching this value is a fatal error and no
         * further encryption/decryption may be attempted, lest the nonce be
         * reused against the same key (catastrophic for ChaCha20-Poly1305).
         *
         * <p>
         * Matches upstream noise-java's {@code CipherState} guard
         * ({@code "Nonce has wrapped around"}).
         */
        private static final long MAX_NONCE = 0xFFFFFFFFFFFFFFFFL;

        private final byte[] key;
        private long nonce;

        CipherState(byte[] key) {
            this.key = key == null ? null : key.clone();
            this.nonce = 0;
        }

        public boolean hasKey() {
            return key != null;
        }

        /**
         * Encrypts {@code plaintext} with AEAD (authentication tag appended),
         * advancing the nonce.
         *
         * @throws IllegalStateException if the nonce counter has reached its
         *                               maximum value (2^64 - 1), refusing any
         *                               further operations per the Noise spec.
         */
        public synchronized byte[] encryptWithAd(byte[] ad, byte[] plaintext) {
            if (!hasKey()) {
                return plaintext.clone();
            }
            if (nonce == MAX_NONCE) {
                throw new IllegalStateException("Noise AEAD nonce exhausted (2^64 - 1); refusing to reuse the counter");
            }
            try {
                byte[] ciphertext = IvCipher.aead(Cipher.ENCRYPT_MODE, key, buildNonce(nonce), ad, plaintext);
                nonce++;
                return ciphertext;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("ChaCha20-Poly1305 encryption failed", e);
            }
        }

        /**
         * Decrypts {@code ciphertext}, advancing the nonce only on success. An
         * authentication failure throws {@link CantDecryptInboundException} and
         * the nonce is left unchanged (per the Noise spec).
         *
         * @throws IllegalStateException if the nonce counter has reached its
         *                               maximum value (2^64 - 1), refusing any
         *                               further operations per the Noise spec.
         */
        public synchronized byte[] decryptWithAd(byte[] ad, byte[] ciphertext) {
            if (!hasKey()) {
                return ciphertext.clone();
            }
            if (nonce == MAX_NONCE) {
                throw new IllegalStateException("Noise AEAD nonce exhausted (2^64 - 1); refusing to reuse the counter");
            }
            try {
                byte[] plaintext = IvCipher.aead(Cipher.DECRYPT_MODE, key, buildNonce(nonce), ad, ciphertext);
                nonce++;
                return plaintext;
            } catch (GeneralSecurityException e) {
                throw new CantDecryptInboundException("Unable to decrypt a message from remote", e);
            }
        }
    }

    private static byte[] buildNonce(long n) {
        byte[] nonce = new byte[12];
        for (int i = 0; i < 8; i++) {
            nonce[4 + i] = (byte) (n >>> (8 * i));
        }
        return nonce;
    }

    /**
     * The Noise {@code SymmetricState}: chaining key + handshake hash +
     * CipherState.
     */
    private static final class SymmetricState {
        private byte[] ck;
        private byte[] h;
        private CipherState cipher = new CipherState(null);

        void initialize(byte[] protocolName) {
            if (protocolName.length <= 32) {
                h = Arrays.copyOf(protocolName, 32);
            } else {
                h = Crypto.sha256(protocolName);
            }
            ck = h.clone();
            cipher = new CipherState(null);
        }

        void mixHash(byte[] data) {
            h = Crypto.sha256(concat(h, data));
        }

        void mixKey(byte[] inputKeyMaterial) {
            byte[][] output = Crypto.hkdf(ck, inputKeyMaterial, 2);
            ck = output[0];
            cipher = new CipherState(output[1]);
        }

        byte[] encryptAndHash(byte[] plaintext) {
            byte[] ciphertext = cipher.encryptWithAd(h, plaintext);
            mixHash(ciphertext);
            return ciphertext;
        }

        byte[] decryptAndHash(byte[] ciphertext) {
            byte[] plaintext = cipher.decryptWithAd(h, ciphertext);
            mixHash(ciphertext);
            return plaintext;
        }

        /**
         * Returns the pair of transport keys (initiator→responder,
         * responder→initiator).
         */
        byte[][] split() {
            return Crypto.hkdf(ck, EMPTY, 2);
        }
    }

    /**
     * The Noise {@code HandshakeState} for the {@code XX} handshake pattern
     * (tokens {@code e / e, ee, s, es / s, se}).
     */
    private static final class HandshakeState {
        private static final int TOKEN_E = 1;
        private static final int TOKEN_S = 2;
        private static final int TOKEN_EE = 3;
        private static final int TOKEN_ES = 4;
        private static final int TOKEN_SE = 5;

        private static final int[][] XX_MESSAGE_PATTERNS = {
                { TOKEN_E },
                { TOKEN_E, TOKEN_EE, TOKEN_S, TOKEN_ES },
                { TOKEN_S, TOKEN_SE },
        };

        private final SymmetricState symmetric = new SymmetricState();
        private final boolean initiator;
        private byte[] ephemeralPrivate;
        private byte[] ephemeralPublic;
        private byte[] remoteStaticPublic;
        private byte[] remoteEphemeralPublic;
        private int messageIndex;

        HandshakeState(boolean initiator) {
            this.initiator = initiator;
            symmetric.initialize(PROTOCOL_NAME.getBytes(StandardCharsets.US_ASCII));
        }

        /**
         * The remote Noise static public key, once the "s" token has been processed.
         */
        byte[] remoteStaticKey() {
            return remoteStaticPublic;
        }

        byte[] writeMessage(byte[] payload) {
            int[] tokens = XX_MESSAGE_PATTERNS[messageIndex++];
            byte[] message = EMPTY;
            for (int token : tokens) {
                switch (token) {
                    case TOKEN_E:
                        byte[][] pair = X25519.generateKeyPair();
                        ephemeralPrivate = pair[0];
                        ephemeralPublic = pair[1];
                        message = concat(message, ephemeralPublic);
                        symmetric.mixHash(ephemeralPublic);
                        break;
                    case TOKEN_S:
                        byte[] encryptedStatic = symmetric.encryptAndHash(processStaticPublic);
                        message = concat(message, encryptedStatic);
                        break;
                    case TOKEN_EE:
                        symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteEphemeralPublic));
                        break;
                    case TOKEN_ES:
                        if (initiator) {
                            symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteStaticPublic));
                        } else {
                            symmetric.mixKey(X25519.dh(processStaticPrivate, remoteEphemeralPublic));
                        }
                        break;
                    case TOKEN_SE:
                        if (initiator) {
                            symmetric.mixKey(X25519.dh(processStaticPrivate, remoteEphemeralPublic));
                        } else {
                            symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteStaticPublic));
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown Noise token: " + token);
                }
            }
            return concat(message, symmetric.encryptAndHash(payload));
        }

        byte[] readMessage(byte[] message) {
            int[] tokens = XX_MESSAGE_PATTERNS[messageIndex++];
            int pos = 0;
            for (int token : tokens) {
                switch (token) {
                    case TOKEN_E:
                        remoteEphemeralPublic = checkRange(message, pos, pos + DH_LENGTH);
                        pos += DH_LENGTH;
                        symmetric.mixHash(remoteEphemeralPublic);
                        break;
                    case TOKEN_S:
                        byte[] encryptedStatic = checkRange(message, pos, pos + DH_LENGTH + TAG_LENGTH);
                        pos += DH_LENGTH + TAG_LENGTH;
                        remoteStaticPublic = symmetric.decryptAndHash(encryptedStatic);
                        break;
                    case TOKEN_EE:
                        symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteEphemeralPublic));
                        break;
                    case TOKEN_ES:
                        if (initiator) {
                            symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteStaticPublic));
                        } else {
                            symmetric.mixKey(X25519.dh(processStaticPrivate, remoteEphemeralPublic));
                        }
                        break;
                    case TOKEN_SE:
                        if (initiator) {
                            symmetric.mixKey(X25519.dh(processStaticPrivate, remoteEphemeralPublic));
                        } else {
                            symmetric.mixKey(X25519.dh(ephemeralPrivate, remoteStaticPublic));
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown Noise token: " + token);
                }
            }
            return symmetric.decryptAndHash(checkRange(message, pos, message.length));
        }

        CipherStatePair split() {
            byte[][] keys = symmetric.split();
            if (initiator) {
                return new CipherStatePair(new CipherState(keys[0]), new CipherState(keys[1]));
            }
            return new CipherStatePair(new CipherState(keys[1]), new CipherState(keys[0]));
        }
    }

    private static final class CipherStatePair {
        final CipherState outbound;
        final CipherState inbound;

        CipherStatePair(CipherState outbound, CipherState inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }
    }

    // ------------------------------------------------------------------
    // X25519 (JDK provider)
    // ------------------------------------------------------------------

    private static final class X25519 {
        private X25519() {
        }

        /**
         * Generates a fresh key pair, returned as
         * {@code {privateScalar, uCoordinateLE}} (32-byte values).
         */
        static byte[][] generateKeyPair() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
                generator.initialize(NamedParameterSpec.X25519);
                KeyPair pair = generator.generateKeyPair();
                byte[] priv = ((java.security.interfaces.XECPrivateKey) pair.getPrivate()).getScalar()
                        .orElseThrow(() -> new IllegalStateException("X25519 private key has no extractable scalar"));
                byte[] pub = toFixedLittleEndian(((java.security.interfaces.XECPublicKey) pair.getPublic()).getU());
                return new byte[][] { priv, pub };
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("X25519 key pair generation failed", e);
            }
        }

        /**
         * X25519 DH between a raw private scalar and a remote public key (the
         * 32-byte little-endian u-coordinate per RFC 7748 §5).
         */
        static byte[] dh(byte[] privateScalar, byte[] remoteUCoordinate) {
            if (privateScalar == null || remoteUCoordinate == null) {
                throw new IllegalStateException("X25519 DH called without the required keys");
            }
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("X25519");
                PrivateKey priv = keyFactory.generatePrivate(
                        new XECPrivateKeySpec(NamedParameterSpec.X25519, privateScalar));
                PublicKey pub = keyFactory.generatePublic(
                        new XECPublicKeySpec(NamedParameterSpec.X25519, toUnsigned(remoteUCoordinate)));
                KeyAgreement agreement = KeyAgreement.getInstance("X25519");
                agreement.init(priv);
                agreement.doPhase(pub, true);
                byte[] secret = agreement.generateSecret();
                if (isAllZero(secret)) {
                    throw new IllegalStateException("All-zero X25519 shared secret");
                }
                return secret;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("X25519 DH failed", e);
            }
        }

        /**
         * The {@code u} coordinate returned by {@code XECPublicKey.getU()}
         * (a {@code BigInteger}) re-encoded as its canonical 32-byte
         * little-endian wire representation.
         */
        private static byte[] toFixedLittleEndian(BigInteger u) {
            byte[] bigEndian = u.toByteArray();
            byte[] littleEndian = new byte[DH_LENGTH];
            int copy = Math.min(bigEndian.length, DH_LENGTH);
            for (int i = 0; i < copy; i++) {
                littleEndian[i] = bigEndian[bigEndian.length - 1 - i];
            }
            return littleEndian;
        }

        /** Decodes a 32-byte little-endian u-coordinate into a {@link BigInteger}. */
        private static BigInteger toUnsigned(byte[] littleEndian) {
            byte[] bigEndian = new byte[littleEndian.length];
            for (int i = 0; i < littleEndian.length; i++) {
                bigEndian[i] = littleEndian[littleEndian.length - 1 - i];
            }
            return new BigInteger(1, bigEndian);
        }

        private static boolean isAllZero(byte[] bytes) {
            for (byte b : bytes) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------
    // SHA-256 / HMAC / HKDF and byte helpers
    // ------------------------------------------------------------------

    private static final class Crypto {
        private Crypto() {
        }

        static byte[] sha256(byte[] data) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(data);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }

        static byte[] hmac(byte[] key, byte[] data) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                return mac.doFinal(data);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HMAC-SHA-256 failed", e);
            }
        }

        /**
         * Noise HKDF (RFC 5869 variant with zero-length info), returning
         * {@code numOutputs} keys.
         */
        static byte[][] hkdf(byte[] chainingKey, byte[] inputKeyMaterial, int numOutputs) {
            byte[] tempKey = hmac(chainingKey, inputKeyMaterial);
            byte[][] outputs = new byte[numOutputs][];
            byte[] previous = EMPTY;
            for (int i = 1; i <= numOutputs; i++) {
                byte[] input = new byte[previous.length + 1];
                System.arraycopy(previous, 0, input, 0, previous.length);
                input[previous.length] = (byte) i;
                byte[] output = hmac(tempKey, input);
                outputs[i - 1] = output;
                previous = output;
            }
            return outputs;
        }
    }

    /** ChaCha20-Poly1305 AEAD (JDK provider). */
    private static final class IvCipher {
        private IvCipher() {
        }

        static byte[] aead(int mode, byte[] key, byte[] nonce, byte[] ad, byte[] data) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(mode, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            if (ad != null && ad.length > 0) {
                cipher.updateAAD(ad);
            }
            return cipher.doFinal(data);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Copies {@code data[from, to)} into a fresh array, rejecting any
     * out-of-bounds range with a {@link MalformedNoiseHandshakeException}.
     *
     * <p>
     * Unlike the raw {@link Arrays#copyOfRange} behaviour previously used here,
     * an overlong request is not silently zero-padded: a truncated handshake
     * frame must never be parsed with zero-filled key material (which could
     * carry DH/crypto processing past its intended input) and must instead
     * fail fast as a protocol violation.
     */
    private static byte[] checkRange(byte[] data, int from, int to) {
        if (from < 0 || to > data.length || from > to) {
            throw new MalformedNoiseHandshakeException(
                    "Truncated Noise handshake message: requested bytes [" + from + ", " + to + ") but message has "
                            + "length " + data.length);
        }
        return Arrays.copyOfRange(data, from, to);
    }
}