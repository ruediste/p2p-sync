package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.Connection;

/**
 * Upgrades a raw {@link Connection} through the libp2p security and muxer negotiation layers.
 * Each method is a plain blocking call; implementations are expected to run the Noise handshake
 * (M5) and Yamux negotiation (M6) synchronously.
 *
 * <p>
 * This can be stubbed with a fake implementation for testing before M5/M6 exist.
 */
public interface ConnectionUpgrader {

    /**
     * Runs security negotiation on {@code connection} (e.g. Noise XX handshake) and updates
     * the connection's secure session state. A no-op stub is acceptable before M5 is implemented.
     */
    void establishSecureChannel(Connection connection);

    /**
     * Runs muxer negotiation on {@code connection} (e.g. Yamux multistream-select) and starts
     * the muxer's background reader thread. A no-op stub is acceptable before M6 is implemented.
     */
    void establishMuxer(Connection connection);
}
