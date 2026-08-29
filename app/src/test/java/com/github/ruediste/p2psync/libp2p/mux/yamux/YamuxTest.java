package com.github.ruediste.p2psync.libp2p.mux.yamux;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.test.BytePipe;

/**
 * Tests for the Yamux stream multiplexer over paired {@link BytePipe}s.
 *
 * <p>
 * Launches two {@link YamuxSession}s connected by a pair of byte pipes (one
 * per direction), exercises bidirectional data flow, window exhaustion, stream
 * close/reset, and multiple concurrent streams.
 */
public class YamuxTest {

    private static final long TIMEOUT_SECONDS = 10;

    /**
     * Wires two {@link YamuxSession}s together via two {@link BytePipe}s (one
     * per direction).
     */
    private static final class MuxerPair {
        final BytePipe aToB = new BytePipe();
        final BytePipe bToA = new BytePipe();

        final YamuxSession connA;
        final YamuxSession connB;
        final P2PStream streamA;
        final P2PStream streamB;

        MuxerPair() {
            // A is initiator, B is responder
            streamA = new P2PStream(bToA.input(), aToB.output(), true);
            streamB = new P2PStream(aToB.input(), bToA.output(), false);

            // No application multistream for basic muxer tests — inbound streams
            // would fail negotiation, which is fine for this test. We test
            // outbound-only streams.
            // We still need to pass *something*; a non-null fails on first inbound SYN.
            // Instead, null means we don't handle inbound streams.
            connA = new YamuxSession(streamA.getIn(), streamA.getOut(), true,
                    null);
            connB = new YamuxSession(streamB.getIn(), streamB.getOut(), false,
                    null);
        }

        void close() {
            connA.close();
            connB.close();
            streamA.close();
            streamB.close();
        }
    }

    private static YamuxStream openStream(YamuxSession conn, YamuxSession remote) {
        // We open a stream directly, bypassing multistream-select for these tests
        return conn.openStream();
    }

    @Test
    public void bidirectionalDataFlow() throws Exception {
        MuxerPair pair = new MuxerPair();
        try {
            YamuxStream streamA = openStream(pair.connA, pair.connB);
            YamuxStream streamB = waitForRemoteStream(pair.connB);

            assertNotNull(streamB);

            byte[] dataA = random(1000);
            byte[] dataB = random(500);

            // Write concurrently from both sides
            Thread tA = Thread.ofVirtual().start(() -> streamA.getOutputStream().write(dataA));
            Thread tB = Thread.ofVirtual().start(() -> streamB.getOutputStream().write(dataB));

            tA.join(5000);
            tB.join(5000);

            assertArrayEquals(dataA, readExact(streamB.getInputStream(), dataA.length));
            assertArrayEquals(dataB, readExact(streamA.getInputStream(), dataB.length));
        } finally {
            pair.close();
        }
    }

    @Test
    public void dataLargerThanWindow() throws Exception {
        MuxerPair pair = new MuxerPair();
        try {
            YamuxStream streamA = openStream(pair.connA, pair.connB);
            YamuxStream streamB = waitForRemoteStream(pair.connB);
            assertNotNull(streamB);

            // Send data larger than the initial window (256KB)
            int size = YamuxSession.INITIAL_WINDOW_SIZE + 100_000;
            byte[] data = random(size);

            Thread t = Thread.ofVirtual().start(() -> streamA.getOutputStream().write(data));

            byte[] received = readExact(streamB.getInputStream(), size);
            assertArrayEquals(data, received);
            t.join(5000);
        } finally {
            pair.close();
        }
    }

    @Test
    public void multipleConcurrentStreams() throws Exception {
        MuxerPair pair = new MuxerPair();
        try {
            int streamCount = 5;
            YamuxStream[] streamsA = new YamuxStream[streamCount];
            YamuxStream[] streamsB = new YamuxStream[streamCount];

            for (int i = 0; i < streamCount; i++) {
                streamsA[i] = openStream(pair.connA, pair.connB);
            }

            List<YamuxStream> bList = waitForRemoteStreams(pair.connB, streamCount);

            for (int i = 0; i < streamCount; i++) {
                streamsB[i] = bList.get(i);
            }

            byte[][] datas = new byte[streamCount][];
            for (int i = 0; i < streamCount; i++) {
                datas[i] = random(1000 + i * 100);
            }

            // Write on all streams concurrently
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                for (int i = 0; i < streamCount; i++) {
                    final int idx = i;
                    executor.submit(() -> streamsA[idx].getOutputStream().write(datas[idx]));
                }
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            // Read on all streams
            for (int i = 0; i < streamCount; i++) {
                byte[] received = readExact(streamsB[i].getInputStream(), datas[i].length);
                assertArrayEquals("Stream " + i, datas[i], received);
            }
        } finally {
            pair.close();
        }
    }

    @Test
    public void finHalfClose() throws Exception {
        MuxerPair pair = new MuxerPair();
        try {
            YamuxStream streamA = openStream(pair.connA, pair.connB);
            YamuxStream streamB = waitForRemoteStream(pair.connB);
            assertNotNull(streamB);

            byte[] data = random(100);
            streamA.getOutputStream().write(data);
            assertArrayEquals(data, readExact(streamB.getInputStream(), data.length));

            // Close A's write side
            streamA.closeForWriting();

            // B should see EOF on read
            int eof = streamB.getInputStream().read();
            assertEquals(-1, eof);

            // B can still write to A
            byte[] reply = random(50);
            streamB.getOutputStream().write(reply);
            assertArrayEquals(reply, readExact(streamA.getInputStream(), reply.length));
        } finally {
            pair.close();
        }
    }

    @Test
    public void rstReset() throws Exception {
        MuxerPair pair = new MuxerPair();
        try {
            YamuxStream streamA = openStream(pair.connA, pair.connB);
            YamuxStream streamB = waitForRemoteStream(pair.connB);
            assertNotNull(streamB);

            // Reset the stream from A's side
            streamA.resetStream();

            // B should see reset on read
            try {
                streamB.getInputStream().read();
                fail("Expected RuntimeException from reset");
            } catch (RuntimeException expected) {
                // expected
            }
        } finally {
            pair.close();
        }
    }

    // ---- helpers ----

    /**
     * Waits for a stream to appear on the given connection (inserted by the
     * remote side's SYN), with a timeout.
     */
    private static YamuxStream waitForRemoteStream(YamuxSession conn) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            if (!conn.streams.isEmpty()) {
                return conn.streams.values().stream().findFirst().orElse(null);
            }
            Thread.sleep(10);
        }
        return null;
    }

    private static List<YamuxStream> waitForRemoteStreams(YamuxSession conn, int count) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        List<YamuxStream> result = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            for (YamuxStream s : conn.streams.values()) {
                if (!result.contains(s)) {
                    result.add(s);
                    if (result.size() >= count) {
                        return result;
                    }
                }
            }
            Thread.sleep(10);
        }
        throw new RuntimeException("Timed out waiting for " + count + " streams, got " + result.size());
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

    private static byte[] random(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }
}
