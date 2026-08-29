package com.github.ruediste.p2psync.libp2p.test;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;

/**
 * A thread-safe in-memory byte pipe, used to connect two sides of a protocol
 * stack in unit tests without real sockets.
 *
 * <p>
 * One instance models a single direction: {@link #output()} writes chunks into
 * a
 * blocking queue, {@link #input()} reads them back as a byte stream. A reader
 * blocks until data (or the writer's EOF) is available, so it behaves like a
 * blocking socket stream with virtual threads. Chunks are strictly stream
 * bytes, not frames: arbitrary write boundaries are allowed and the reader
 * reassembles them.
 */
public final class BytePipe {

    /** Sentinel pushed by {@link #output() close} to signal EOF to readers. */
    private static final byte[] EOF = new byte[0];

    private final LinkedBlockingQueue<byte[]> buffer = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    /** The write side of this pipe. */
    public P2POutputStream output() {
        if (closed)
            throw new RuntimeException("BytePipe is closed");
        return new P2POutputStream() {
            @Override
            public void write(byte[] buf, int off, int len) {
                if (len > 0) {
                    buffer.add(Arrays.copyOfRange(buf, off, off + len));
                }
            }

            @Override
            public void close() {
                closed = true;
                buffer.add(EOF);
            }
        };
    }

    /** The read side of this pipe. */
    public P2PInputStream input() {
        if (closed)
            throw new RuntimeException("BytePipe is closed");
        return new P2PInputStream() {
            private byte[] current;
            private int pos;
            boolean isEof = false;

            @Override
            public int read(byte[] buf, int off, int len) {
                if (isEof)
                    return -1;
                if (len == 0) {
                    return 0;
                }
                try {
                    while (current == null || pos >= current.length) {
                        byte[] next = buffer.take();
                        if (next == EOF) {
                            isEof = true;
                            return -1;
                        }
                        current = next;
                        pos = 0;
                    }
                    int n = Math.min(len, current.length - pos);
                    System.arraycopy(current, pos, buf, off, n);
                    pos += n;
                    return n;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while reading from byte pipe", e);
                }
            }

            @Override
            public void close() {
            }
        };
    }
}