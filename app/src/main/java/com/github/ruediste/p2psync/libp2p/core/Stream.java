package com.github.ruediste.p2psync.libp2p.core;

import java.io.Closeable;

/**
 * A single stream within a muxed connection (or the whole connection before muxing). Thin wrapper
 * around a {@link P2PStream} plus the negotiated protocol id.
 */
public final class Stream implements Closeable {

    private final P2PStream stream;
    private final String protocol;

    public Stream(P2PStream stream, String protocol) {
        this.stream = stream;
        this.protocol = protocol;
    }

    public P2PStream getStream() {
        return stream;
    }

    public String getProtocol() {
        return protocol;
    }

    public boolean isInitiator() {
        return stream.isInitiator();
    }

    @Override
    public void close() {
        stream.close();
    }
}
