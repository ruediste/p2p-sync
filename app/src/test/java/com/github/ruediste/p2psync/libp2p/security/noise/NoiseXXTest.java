package com.github.ruediste.p2psync.libp2p.security.noise;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.libp2p.security.CantDecryptInboundException;
import com.github.ruediste.p2psync.libp2p.security.InvalidRemotePubKeyException;
import com.github.ruediste.p2psync.libp2p.security.MalformedNoiseHandshakeException;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;
import com.github.ruediste.p2psync.libp2p.test.BytePipe;

/**
 * Noise {@code XX} security transport over an in-memory byte pipe.
 *
 * <p>
 * Launches the initiator and responder handshakes on separate virtual threads
 * (both sides block on reads, so they must run concurrently) and verifies:
 * <ul>
 * <li>a handshake completes and each side learns the other's correct
 * {@link PeerId}/{@code PubKey};</li>
 * <li>the first handshake message on the wire is exactly the 32-byte initiator
 * ephemeral X25519 public key (confidentiality/correct framing);</li>
 * <li>post-split AEAD data flows both directions across a range of sizes
 * spanning multiple Noise frames;</li>
 * <li>a tampered (bit-flipped) transport frame fails AEAD authentication with
 * {@link CantDecryptInboundException};</li>
 * <li>dialing with a wrong expected remote peer id is rejected with
 * {@link InvalidRemotePubKeyException}.</li>
 * </ul>
 */
public class NoiseXXTest {

    private static final long TIMEOUT_SECONDS = 5;

    /** A {@code P2PInput/Output} stream pair both ends of a handshake share. */
    private static final class Pipe {
        interface SessionFactory {
            SecureSession init(P2PStream stream) throws Exception;
        }

        final BytePipe aToB;
        final BytePipe bToA;
        P2PStream aRaw;
        P2PStream bRaw;

        Pipe() {
            aToB = new BytePipe();
            bToA = new BytePipe();
            linkAtoB(aToB.output());
        }

        /**
         * Re-points A's output to {@code tee}, which must write through to
         * {@link #aToB}'s output.
         */
        void linkAtoB(P2POutputStream tee) {
            aRaw = new P2PStream(bToA.input(), tee, true);
            bRaw = new P2PStream(aToB.input(), bToA.output(), false);
        }
    }

    /**
     * Runs both ends of a handshake concurrently on one virtual-thread executor
     * and returns the two sessions. Submitting both before awaiting either is
     * required: each side blocks on reads until the other has sent its next
     * handshake message.
     */
    private static SecureSession[] runBoth(Pipe pipe, Pipe.SessionFactory aFactory,
            Pipe.SessionFactory bFactory) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<SecureSession> fa = executor.submit(() -> aFactory.init(pipe.aRaw));
            Future<SecureSession> fb = executor.submit(() -> bFactory.init(pipe.bRaw));
            SecureSession a = fa.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            SecureSession b = fb.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new SecureSession[] { a, b };
        } finally {
            executor.shutdownNow();
        }
    }

    /** Convenience overload returning the two sessions in an array. */
    private static SecureSession[] handshake(Pipe pipe, Ed25519PrivateKey privA, Ed25519PrivateKey privB,
            PeerId expectedA) throws Exception {
        return runBoth(pipe,
                stream -> new NoiseXXProtocolBinding(privA, expectedA).init(stream, "/noise"),
                stream -> new NoiseXXProtocolBinding(privB).init(stream, "/noise"));
    }

    @Test
    public void handshakeCompletesAndBidirectionalDataFlows() throws Exception {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey privB = Ed25519PrivateKey.generateKeyPair();

        Pipe pipe = new Pipe();
        SecureSession[] sessions = handshake(pipe, privA, privB, null);
        SecureSession aSession = sessions[0];
        SecureSession bSession = sessions[1];

        // each side learned the other's identity
        assertEquals(PeerId.fromPubKey(privA.publicKey()), aSession.getLocalId());
        assertEquals(PeerId.fromPubKey(privB.publicKey()), aSession.getRemoteId());
        assertEquals(privB.publicKey(), aSession.getRemotePubKey());
        assertEquals(PeerId.fromPubKey(privB.publicKey()), bSession.getLocalId());
        assertEquals(PeerId.fromPubKey(privA.publicKey()), bSession.getRemoteId());
        assertEquals(privA.publicKey(), bSession.getRemotePubKey());

        // 1 and 2 frame boundaries, plus a chunk that spans many frames
        int[] sizes = { 1, 16, NoiseXXHandshake.SPLIT_MAX, NoiseXXHandshake.SPLIT_MAX + 1, 200_000 };
        for (int size : sizes) {
            byte[] aToBData = randomData(size);
            byte[] bToAData = randomData(size);

            Thread.ofVirtual().start(() -> aSession.getStream().getOut().write(aToBData));
            Thread.ofVirtual().start(() -> bSession.getStream().getOut().write(bToAData));

            assertArrayEquals("A->B size " + size, aToBData, readExact(bSession.getStream().getIn(), size));
            assertArrayEquals("B->A size " + size, bToAData, readExact(aSession.getStream().getIn(), size));
        }

        aSession.getStream().close();
        bSession.getStream().close();
    }

    @Test
    public void firstHandshakeMessageIsTheInitiatorEphemeralKey() throws Exception {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey privB = Ed25519PrivateKey.generateKeyPair();

        Pipe pipe = new Pipe();
        TeeP2POutputStream aTee = new TeeP2POutputStream(pipe.aToB.output());
        pipe.linkAtoB(aTee);
        handshake(pipe, privA, privB, null);

        byte[] probe = aTee.probe();
        // first message = 2-byte length prefix (32) + 32-byte ephemeral public key
        assertTrue("expected a first message on the wire, probe size " + probe.length, probe.length >= 34);
        assertEquals(0, probe[0] & 0xFF);
        assertEquals(NoiseXXHandshake.DH_LENGTH, probe[1] & 0xFF);
    }

    @Test
    public void tamperedTransportFrameFailsDecryption() throws Exception {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey privB = Ed25519PrivateKey.generateKeyPair();

        Pipe pipe = new Pipe();
        TeeP2POutputStream aTee = new TeeP2POutputStream(pipe.aToB.output());
        pipe.linkAtoB(aTee);
        SecureSession[] sessions = handshake(pipe, privA, privB, null);
        SecureSession aSession = sessions[0];
        SecureSession bSession = sessions[1];

        // 1. a legitimate frame crosses the wire and decrypts
        String first = "first-valid-message";
        aSession.getStream().getOut().write(first.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(first.getBytes(StandardCharsets.UTF_8),
                readExact(bSession.getStream().getIn(), first.length()));

        // 2. re-send that same frame, tampered: flip a bit in the ciphertext,
        // leaving the 2-byte length prefix intact. The probe holds every wire
        // frame A sent (msg1, msg3, then the step-1 data frame), so grab the last one.
        byte[] frame = lastFrame(aTee.probe());
        assertTrue("expected the step-1 data frame, got " + frame.length,
                frame.length >= 2 + NoiseXXHandshake.TAG_LENGTH);
        int frameLength = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        frame[2 + NoiseXXHandshake.TAG_LENGTH] ^= 0x01;
        P2POutputStream direct = pipe.aToB.output();
        direct.write((frameLength >>> 8) & 0xFF);
        direct.write(frameLength & 0xFF);
        direct.write(frame, 2, frame.length - 2);

        // 3. the responder's next read must fail AEAD authentication
        try {
            readExact(bSession.getStream().getIn(), 1);
            fail("Expected decryption to fail on the tampered frame");
        } catch (CantDecryptInboundException expected) {
            // expected
        }

        aSession.getStream().close();
        bSession.getStream().close();
    }

    @Test
    public void initiatorRejectsWrongRemotePeerId() throws Throwable {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey privB = Ed25519PrivateKey.generateKeyPair();
        PeerId wrongRemote = PeerId.random(); // spoofed /p2p/ component

        Pipe pipe = new Pipe();

        // responder blocks waiting for message 3 which never comes once the
        // initiator rejects; close the pipe afterwards to unblock it
        Thread responder = Thread.ofVirtual().name("responder").start(() -> {
            try {
                new NoiseXXProtocolBinding(privB).init(pipe.bRaw, "/noise");
            } catch (RuntimeException expected) {
                // torn down after initiator rejection
            }
        });

        try {
            awaitSingle(() -> new NoiseXXProtocolBinding(privA, wrongRemote).init(pipe.aRaw, "/noise"));
            fail("Expected the initiator to reject the mismatched peer id");
        } catch (InvalidRemotePubKeyException expected) {
            // expected
        } finally {
            pipe.aToB.input().close();
            pipe.bToA.input().close();
            responder.join(1_000);
        }
    }

    @Test
    public void truncatedHandshakeFrameIsRejected() throws Throwable {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();

        Pipe pipe = new Pipe();

        // adversarial responder: swallows the initiator's first message (msg1,
        // the 32-byte ephemeral key) and replies with a frame that declares only
        // 31 bytes -- one short of what TOKEN_E requires.
        Thread responder = Thread.ofVirtual().name("truncator").start(() -> {
            try {
                P2PInputStream in = pipe.aToB.input();
                int hi = in.read();
                int lo = in.read();
                int len = (hi << 8) | lo;
                byte[] ignored = new byte[len];
                in.readFully(ignored);
                P2POutputStream out = pipe.bToA.output();
                out.write(0);
                out.write(31);
                out.write(new byte[31]);
            } catch (RuntimeException expected) {
                // torn down after initiator rejection
            }
        });

        try {
            awaitSingle(() -> new NoiseXXProtocolBinding(privA).init(pipe.aRaw, "/noise"));
            fail("Expected the initiator to reject the truncated handshake frame");
        } catch (MalformedNoiseHandshakeException expected) {
            // expected
        } finally {
            pipe.aToB.input().close();
            pipe.bToA.input().close();
            responder.join(1_000);
        }
    }

    @Test
    public void nonceOverflowIsFatal() throws Throwable {
        // a CipherState carrying any 32-byte key: reading/writing its AEAD
        // counter requires a live handshake only to obtain a key, which a
        // directly-constructed state provides for the overflow check
        byte[] key = new byte[32];
        new Random(42).nextBytes(key);
        NoiseXXHandshake.CipherState cipher = new NoiseXXHandshake.CipherState(key);

        // force the AEAD counter to its maximum value (2^64 - 1); the very next
        // operation must be refused outright rather than reuse the counter
        java.lang.reflect.Field nonceField = NoiseXXHandshake.CipherState.class
                .getDeclaredField("nonce");
        nonceField.setAccessible(true);
        nonceField.setLong(cipher, 0xFFFFFFFFFFFFFFFFL);

        try {
            cipher.encryptWithAd(null, new byte[8]);
            fail("Expected nonce exhaustion to be fatal");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals("Refusal must not consume the counter", 0xFFFFFFFFFFFFFFFFL,
                nonceField.getLong(cipher));

        try {
            cipher.decryptWithAd(null, new byte[8]);
            fail("Expected nonce exhaustion to be fatal");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals("Refusal must not consume the counter", 0xFFFFFFFFFFFFFFFFL,
                nonceField.getLong(cipher));
    }

    @Test
    public void concurrentCallsOnSharedCipherNeverReuseANonce() throws Exception {
        byte[] key = new byte[32];
        new Random(42).nextBytes(key);
        NoiseXXHandshake.CipherState cipher = new NoiseXXHandshake.CipherState(key);

        int threads = 8;
        int perThread = 500;
        // identical cipher input on every call: if two calls ever shared a
        // key/nonce pair their ciphertexts would be identical and the set would
        // shrink accordingly
        byte[] plaintext = "identical plaintext makes nonce reuse visible".getBytes(StandardCharsets.UTF_8);
        Set<String> ciphertexts = Collections.synchronizedSet(new HashSet<>());

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        ciphertexts.add(Arrays.toString(cipher.encryptWithAd(null, plaintext)));
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals("each AEAD counter must be used exactly once",
                threads * perThread, ciphertexts.size());
        assertEquals("the counter must have advanced exactly once per encryption",
                threads * perThread, nonceOf(cipher));
    }

    @Test
    public void concurrentWritesKeepFrameOrderAlignedWithNonceOrder() throws Exception {
        Ed25519PrivateKey privA = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey privB = Ed25519PrivateKey.generateKeyPair();

        Pipe pipe = new Pipe();
        SecureSession[] sessions = handshake(pipe, privA, privB, null);
        P2POutputStream out = sessions[0].getStream().getOut();
        P2PInputStream in = sessions[1].getStream().getIn();

        // several writers hammer one outbound stream; the sole reader on the
        // other side must be able to decrypt every frame. Any interleave that
        // emitted a frame with a counter out of order vs. its nonce would make
        // the next read fail with CantDecryptInboundException.
        int threads = 8;
        int chunks = 40;
        int chunkLen = 333;
        int total = threads * chunks * chunkLen;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                byte[] data = new byte[chunkLen];
                new Random(t).nextBytes(data);
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < chunks; i++) {
                        out.write(data);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // readExact throws (UncheckedIOException on EOF, CantDecryptInboundException
        // on counter/wire desync) if any frame can't be decrypted in order; hence
        // reaching the end proves the ordering guarantee
        byte[] received = readExact(in, total);
        assertEquals("decrypted byte volume must match what was written", total, received.length);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Submits a single handshake task on a fresh virtual thread and joins it with a
     * timeout.
     */
    private static void awaitSingle(Callable<?> task) throws Throwable {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            try {
                executor.submit(task).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause();
            } finally {
                executor.shutdownNow();
            }
        } catch (RuntimeException | Error e) {
            throw e;
        }
    }

    /** White-box read of the AEAD counter, for asserting exactly-once use. */
    private static long nonceOf(NoiseXXHandshake.CipherState cipher) throws Exception {
        java.lang.reflect.Field nonceField = NoiseXXHandshake.CipherState.class.getDeclaredField("nonce");
        nonceField.setAccessible(true);
        return nonceField.getLong(cipher);
    }

    private static byte[] readExact(P2PInputStream in, int n) {
        byte[] buf = new byte[n];
        int pos = 0;
        while (pos < n) {
            int r = in.read(buf, pos, n - pos);
            if (r < 0) {
                throw new RuntimeException("Unexpected EOF after " + pos + " of " + n + " bytes");
            }
            pos += r;
        }
        return buf;
    }

    private static byte[] randomData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    /**
     * Walks a tee probe's {@code [len(2)][data]} frames and returns the entire last
     * frame.
     */
    private static byte[] lastFrame(byte[] probe) {
        int pos = 0;
        byte[] last = null;
        while (pos + 2 <= probe.length) {
            int len = ((probe[pos] & 0xFF) << 8) | (probe[pos + 1] & 0xFF);
            if (pos + 2 + len > probe.length) {
                break; // trailing partial write (shouldn't happen)
            }
            last = Arrays.copyOfRange(probe, pos, pos + 2 + len);
            pos += 2 + len;
        }
        if (last == null) {
            throw new IllegalStateException("No complete frame found in probe of " + probe.length + " bytes");
        }
        return last;
    }

    /**
     * An {@link P2POutputStream} that writes through to the delegate while also
     * copying every byte into an in-memory probe, so a test can inspect the wire
     * bytes (e.g. verify handshake framing) without disturbing the peer reading
     * from the delegate.
     */
    private static final class TeeP2POutputStream extends P2POutputStream {
        private final P2POutputStream delegate;
        private final java.io.ByteArrayOutputStream probe = new java.io.ByteArrayOutputStream();

        TeeP2POutputStream(P2POutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            delegate.write(buf, off, len);
            probe.write(buf, off, len);
        }

        @Override
        public void write(int b) {
            delegate.write(b);
            probe.write(b);
        }

        @Override
        public void close() {
            delegate.close();
        }

        byte[] probe() {
            return probe.toByteArray();
        }
    }
}