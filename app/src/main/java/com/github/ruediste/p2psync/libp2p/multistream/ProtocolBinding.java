package com.github.ruediste.p2psync.libp2p.multistream;

import com.github.ruediste.p2psync.libp2p.core.P2PStream;

/**
 * A {@link ProtocolBinding} represents the entry point to a protocol: it provides metadata
 * about the protocol (its protocol id(s) and matching heuristics) and the logic to run once
 * that protocol has been negotiated on a given {@link P2PStream} (a connection's stream for
 * security/muxer bindings, or a single application stream for application-level protocol
 * bindings).
 *
 * <p>
 * Ported from {@code io.libp2p.core.multistream.ProtocolBinding} (jvm-libp2p), simplified for
 * this project's blocking-I/O model: instead of returning a {@code CompletableFuture} for an
 * asynchronous {@code initChannel} callback driven by pipeline events, {@link #init} is a
 * plain blocking method — since everything here already runs on its own virtual thread (see
 * {@code ARCHITECTURE.md}), there is no need for the caller to get a future back; it simply
 * calls this method and gets the controller directly (or an exception).
 */
public interface ProtocolBinding<TController> {

    /**
     * Supported protocol id(s) for this binding.
     */
    ProtocolDescriptor getProtocolDescriptor();

    /**
     * Runs this protocol's logic on the given stream, which has already agreed (via
     * multistream-select) to speak {@code selectedProtocol}. Blocks until the protocol's setup
     * is complete (e.g. a handshake) and returns a controller object for interacting with it;
     * for protocols that don't need any post-negotiation setup, this may return immediately.
     */
    TController init(P2PStream stream, String selectedProtocol);

    /**
     * Creates a {@link ProtocolBinding} with a {@link ProtocolMatcher#strict(String)} matcher
     * and the given handler.
     */
    static <T> ProtocolBinding<T> createSimple(String protocolName, Handler<T> handler) {
        return new ProtocolBinding<T>() {
            @Override
            public ProtocolDescriptor getProtocolDescriptor() {
                return new ProtocolDescriptor(protocolName);
            }

            @Override
            public T init(P2PStream stream, String selectedProtocol) {
                return handler.init(stream, selectedProtocol);
            }
        };
    }

    @FunctionalInterface
    interface Handler<T> {
        T init(P2PStream stream, String selectedProtocol);
    }
}
