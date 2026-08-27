package com.github.ruediste.p2psync.libp2p.mux;

import java.util.List;

import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;

/**
 * The multiplexer session, capable of opening new streams over a single
 * underlying connection.
 */
public interface MuxerSession {
    /**
     * Initiates a new stream, negotiating one of the given protocol bindings
     * over it via multistream-select (acting as initiator).
     */
    <T> T createStream(List<ProtocolBinding<T>> protocols);

    default <T> T createStream(ProtocolBinding<T> protocol) {
        return createStream(List.of(protocol));
    }
}