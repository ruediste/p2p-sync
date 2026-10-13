package com.github.ruediste.p2psync.libp2p.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Minimal blocking output stream abstraction used throughout the libp2p port
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
 * until the write has
 * fully completed, exactly like {@link OutputStream}.
 *
 * <p>
 * Unlike {@link OutputStream}, none of these methods declare a checked
 * exception — see
 * {@link P2PInputStream} for the rationale: implementations must catch any
 * underlying
 * {@link IOException} and rethrow it wrapped in {@link UncheckedIOException}.
 */
public abstract class P2POutputStream implements Closeable {

    /**
     * Same contract as {@link OutputStream#write(byte[], int, int)}: blocks until
     * all
     * {@code len} bytes have been written (or an error occurs).
     *
     * @throws UncheckedIOException if the underlying I/O fails.
     */
    public abstract void write(byte[] buf, int off, int len);

    /**
     * Same contract as {@link OutputStream#write(int)}: blocks until the single
     * given byte
     * (the low 8 bits of {@code b}) has been written.
     *
     * <p>
     * Defined in terms of {@link #write(byte[], int, int)}, same rationale as
     * {@link P2PInputStream#read()}.
     */
    public void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    /** Shorthand for {@code write(buf, 0, buf.length)}. */
    public final void write(byte[] buf) {
        write(buf, 0, buf.length);
    }

    @Override
    public abstract void close();

    /**
     * Flushes any internally buffered bytes to the underlying sink.
     *
     * <p>
     * The default implementation is a no-op, which is correct for streams with
     * no internal buffering (raw socket streams, the in-memory test pipe, etc.).
     * Implementations that buffer output (e.g. wrapping a
     * {@link java.io.BufferedOutputStream}) must override this.
     *
     * <p>
     * Blocking request/response protocols such as the Noise handshake write a
     * complete message and then block on a read; without a flush after each such
     * write, a buffered peer would leave the message sitting in local memory and
     * the two sides would deadlock.
     */
    public void flush() {
    }

    /**
     * Adapts a plain {@link OutputStream} (e.g. {@code Socket#getOutputStream()},
     * or a
     * {@code PipedOutputStream} used in tests) to a {@link P2POutputStream},
     * wrapping any
     * {@link IOException} it throws in an {@link UncheckedIOException}.
     */
    public static P2POutputStream wrap(OutputStream out) {
        return new P2POutputStream() {
            @Override
            public void write(byte[] buf, int off, int len) {
                try {
                    out.write(buf, off, len);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void write(int b) {
                try {
                    out.write(b);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void flush() {
                try {
                    out.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void close() {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
