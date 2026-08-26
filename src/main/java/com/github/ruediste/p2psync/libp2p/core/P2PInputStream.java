package com.github.ruediste.p2psync.libp2p.core;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Minimal blocking input stream abstraction used throughout the libp2p port
 * instead of a
 * Netty {@code Channel}/{@code ByteBuf}. See {@code ARCHITECTURE.md} for the
 * full rationale.
 *
 * <p>
 * Every layer of the stack (raw TCP socket, Noise-encrypted connection, an
 * individual Yamux
 * stream) is exposed to the layer above it as a
 * {@link P2PInputStream}/{@link P2POutputStream}
 * pair. Implementations are expected to block the calling (virtual) thread
 * until data is
 * available, exactly like {@link InputStream}.
 *
 * <p>
 * Unlike {@link InputStream}, none of these methods declare a checked
 * exception: this mirrors
 * the M1 deviation note in {@code ImplementationPlan.md} (no bespoke exception
 * hierarchy, just
 * plain JDK exceptions) — implementations must catch any underlying
 * {@link IOException} and
 * rethrow it wrapped in {@link UncheckedIOException} instead of declaring
 * {@code throws
 * IOException} everywhere. This keeps every caller's code a plain straight-line
 * sequence of
 * blocking calls with no {@code try/catch}-for-plumbing noise; callers that do
 * want to react to
 * I/O failures can still catch {@link UncheckedIOException} and unwrap {@link
 * UncheckedIOException#getCause()}.
 */
public abstract class P2PInputStream implements Closeable {

    /**
     * Same contract as {@link InputStream#read(byte[], int, int)}: blocks until at
     * least one
     * byte is available (or EOF/error), and may return fewer bytes than requested.
     * Returns
     * {@code -1} on end of stream.
     *
     * @throws UncheckedIOException if the underlying I/O fails.
     */
    public abstract int read(byte[] buf, int off, int len);

    /**
     * Same contract as {@link InputStream#read()}: blocks for, and returns, a
     * single byte
     * (0-255), or {@code -1} on EOF.
     *
     * <p>
     * Defined in terms of {@link #read(byte[], int, int)} so subclasses only ever
     * need to
     * implement the array-based version; overriding this one too is only worth it
     * if a layer
     * has a cheaper single-byte path.
     */
    public int read() {
        byte[] single = new byte[1];
        int n = read(single, 0, 1);
        return n < 0 ? -1 : single[0] & 0xFF;
    }

    /**
     * Reads exactly {@code len} bytes into {@code buf} starting at {@code off},
     * blocking (and
     * looping over {@link #read(byte[], int, int)}) until either that many bytes
     * have been
     * read or the stream reaches EOF first.
     *
     * @throws UncheckedIOException wrapping an {@link EOFException} if the stream
     *                              reaches EOF
     *                              before {@code len} bytes were read.
     */
    public void readFully(byte[] buf, int off, int len) {
        int total = 0;
        while (total < len) {
            int n = read(buf, off + total, len - total);
            if (n < 0) {
                throw new UncheckedIOException(new EOFException(
                        "Unexpected end of stream after " + total + " of " + len + " bytes"));
            }
            total += n;
        }
    }

    /** Shorthand for {@code readFully(buf, 0, buf.length)}. */
    public void readFully(byte[] buf) {
        readFully(buf, 0, buf.length);
    }

    @Override
    public abstract void close();

    /**
     * Adapts a plain {@link InputStream} (e.g. {@code Socket#getInputStream()}, or a
     * {@code PipedInputStream} used in tests) to a {@link P2PInputStream}, wrapping any
     * {@link IOException} it throws in an {@link UncheckedIOException}.
     */
    public static P2PInputStream wrap(InputStream in) {
        return new P2PInputStream() {
            @Override
            public int read(byte[] buf, int off, int len) {
                try {
                    return in.read(buf, off, len);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public int read() {
                try {
                    return in.read();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void close() {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}

