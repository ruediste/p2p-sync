package com.github.ruediste.p2psync.libp2p.core;

import java.io.Closeable;

/**
 * A single logical P2P stream: the {@link P2PInputStream}/{@link P2POutputStream} pair for it,
 * plus whether this side is the one that opened/dialed it (the _initiator_) or the one that
 * accepted it (the _responder_).
 *
 * <p>
 * This is the unit multistream-select negotiates on (see {@code multistream.Multistream}): a
 * whole connection's raw/Noise-encrypted streams before security/muxer negotiation, or a
 * single Yamux stream's streams for per-application-protocol negotiation. Bundling the
 * initiator/responder flag together with the streams themselves (rather than passing it around
 * as a separate {@code boolean}) means a single {@code negotiate(P2PStream)} call can pick the
 * right negotiation role on its own — see {@code ARCHITECTURE.md}.
 */
public final class P2PStream implements Closeable {

    private final P2PInputStream in;
    private final P2POutputStream out;
    private final boolean initiator;

    public P2PStream(P2PInputStream in, P2POutputStream out, boolean initiator) {
        this.in = in;
        this.out = out;
        this.initiator = initiator;
    }

    public P2PInputStream getIn() {
        return in;
    }

    public P2POutputStream getOut() {
        return out;
    }

    /**
     * {@code true} if this side opened/dialed this stream (the multistream-select
     * _initiator_), {@code false} if this side accepted it (the _responder_).
     */
    public boolean isInitiator() {
        return initiator;
    }

    /**
     * Closes both the input and output stream. If both throw, the first exception is thrown
     * with the second added as a suppressed exception (matching the usual
     * try-with-resources/{@code Closeable} convention).
     */
    @Override
    public void close() {
        RuntimeException error = null;
        try {
            in.close();
        } catch (RuntimeException e) {
            error = e;
        }
        try {
            out.close();
        } catch (RuntimeException e) {
            if (error == null) {
                error = e;
            } else {
                error.addSuppressed(e);
            }
        }
        if (error != null) {
            throw error;
        }
    }
}
