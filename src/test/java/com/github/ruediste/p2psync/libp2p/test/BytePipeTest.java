package com.github.ruediste.p2psync.libp2p.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;

/**
 * Unit tests for {@link BytePipe}, the in-memory blocking byte pipe used to
 * wire two sides of a protocol stack together in tests.
 *
 * <p>
 * Verifies the pipe's stream contract: bytes written are read back in order
 * regardless of write/read chunk boundaries, reads block until data is
 * available, and closing the output side signals EOF to readers. Concurrency
 * cases are exercised on virtual threads, matching how {@code NoiseXXTest}
 * drives the pipe.
 */
public class BytePipeTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Test
    public void writeAndReadPreservesData() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        byte[] payload = random(1000);
        out.write(payload);
        out.close();

        assertArrayEquals(payload, readAll(in));
    }

    @Test
    public void reassemblesStreamAcrossRandomWriteAndReadBoundaries() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        Random rand = new Random(42);
        byte[] payload = random(50_000);
        int pos = 0;
        while (pos < payload.length) {
            int len = Math.min(1 + rand.nextInt(997), payload.length - pos);
            out.write(payload, pos, len);
            pos += len;
        }
        out.close();

        byte[] received = new byte[payload.length];
        byte[] buf = new byte[31];
        int off = 0;
        int got;
        while ((got = in.read(buf, 0, buf.length)) >= 0) {
            System.arraycopy(buf, 0, received, off, got);
            off += got;
        }
        assertEquals(payload.length, off);
        assertArrayEquals(payload, received);
    }

    @Test
    public void writeCopiesTheSourceBuffer() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        byte[] source = new byte[] { 1, 2, 3 };
        out.write(source);
        source[0] = (byte) 0xFF;

        out.close();
        assertArrayEquals(new byte[] { 1, 2, 3 }, readAll(in));
    }

    @Test
    public void writeHonorsOffsetAndLength() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 9, 9, 1, 2, 3, 9 }, 2, 3);
        out.close();

        assertArrayEquals(new byte[] { 1, 2, 3 }, readAll(in));
    }

    @Test
    public void readHonorsOffsetInDestinationBuffer() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2, 3 });
        out.close();

        byte[] buf = new byte[5];
        int n = in.read(buf, 1, 3);
        assertEquals(3, n);
        assertEquals(0, buf[0]);
        assertEquals(0, buf[4]);
        assertArrayEquals(new byte[] { 1, 2, 3 }, Arrays.copyOfRange(buf, 1, 4));
    }

    @Test
    public void readsSplitWithinACurrentChunk() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2, 3, 4, 5 });
        out.close();

        // a small read only drains part of the current chunk; the rest is still
        // readable by later reads
        byte[] small = new byte[2];
        assertEquals(2, in.read(small, 0, 2));
        assertArrayEquals(new byte[] { 1, 2 }, small);
        byte[] rest = new byte[10];
        assertEquals(3, in.read(rest, 0, 10));
        assertArrayEquals(new byte[] { 3, 4, 5 }, Arrays.copyOfRange(rest, 0, 3));
        assertEquals(-1, in.read(rest, 0, 10));
    }

    @Test
    public void overSizedReadConsumesWholeChunkThenEof() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2, 3, 4, 5 });
        out.close();

        // a read with a larger buffer consumes the whole current chunk, then the
        // next read blocks on the queue and sees EOF
        byte[] buf = new byte[10];
        assertEquals(5, in.read(buf, 0, 10));
        assertEquals(-1, in.read(buf, 0, 10));
    }

    @Test
    public void singleByteWriteAndRead() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(0xAB);
        assertEquals(0xAB, in.read());
        out.close();
        assertEquals(-1, in.read());
    }

    @Test
    public void zeroLengthWritesAreIgnored() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[0]);
        out.close();

        assertEquals(-1, in.read());
    }

    @Test
    public void zeroLengthReadReturnsZero() {
        BytePipe pipe = new BytePipe();
        pipe.output();
        P2PInputStream in = pipe.input();

        assertEquals(0, in.read(new byte[1], 0, 0));
    }

    @Test
    public void readFullyReadsWithOffset() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2, 3, 4, 5 });
        out.close();

        byte[] buf = new byte[9];
        in.readFully(buf, 2, 5);
        assertEquals(0, buf[0]);
        assertEquals(0, buf[1]);
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, Arrays.copyOfRange(buf, 2, 7));
    }

    @Test
    public void readFullyThrowsOnPrematureEof() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2 });
        out.close();

        try {
            in.readFully(new byte[5]);
            fail("expected UncheckedIOException");
        } catch (UncheckedIOException expected) {
            assertTrue(expected.getCause() instanceof EOFException);
        }
    }

    @Test
    public void eofIsSignalledAfterOutputClose() {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        out.write(new byte[] { 1, 2, 3 });
        out.close();

        assertEquals(1, in.read());
        // remaining bytes are still readable before EOF
        assertArrayEquals(new byte[] { 2, 3 }, readAll(in));
        // and every subsequent read reports EOF, not an exception
        assertEquals(-1, in.read());
    }

    @Test(timeout = 5000)
    public void readBlocksUntilDataIsWritten() throws Exception {
        BytePipe pipe = new BytePipe();
        P2PInputStream in = pipe.input();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Integer> read = executor.submit(() -> in.read());
            Thread.sleep(200);
            assertFalse(read.isDone());

            pipe.output().write(0x2A);
            assertEquals(0x2A, (int) read.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void readBlocksUntilCloseSignalsEof() throws Exception {
        BytePipe pipe = new BytePipe();
        P2PInputStream in = pipe.input();
        P2POutputStream out = pipe.output();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Integer> read = executor.submit(() -> in.read());
            Thread.sleep(200);
            assertFalse(read.isDone());

            out.close();
            assertEquals(-1, (int) read.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 20000)
    public void concurrentWriterAndReaderConserveData() throws Exception {
        BytePipe pipe = new BytePipe();
        P2POutputStream out = pipe.output();
        P2PInputStream in = pipe.input();

        byte[] payload = random(1_000_000);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<?> writer = executor.submit(() -> {
                int slice = 1000;
                for (int i = 0; i < payload.length; i += slice) {
                    out.write(payload, i, Math.min(slice, payload.length - i));
                }
                out.close();
            });
            Future<byte[]> reader = executor.submit(() -> readAll(in));

            writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertArrayEquals(payload, reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void outputAndInputThrowAfterPipeIsClosed() {
        BytePipe pipe = new BytePipe();
        pipe.output().close();

        try {
            pipe.output();
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertEquals("BytePipe is closed", expected.getMessage());
        }
        try {
            pipe.input();
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertEquals("BytePipe is closed", expected.getMessage());
        }
    }

    private static byte[] random(int size) {
        byte[] data = new byte[size];
        new Random(1234).nextBytes(data);
        return data;
    }

    private static byte[] readAll(P2PInputStream in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16];
        int n;
        while ((n = in.read(buf, 0, buf.length)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}