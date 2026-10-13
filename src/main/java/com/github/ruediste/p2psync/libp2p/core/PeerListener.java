package com.github.ruediste.p2psync.libp2p.core;

/**
 * Callback invoked when a peer is discovered through a {@link Discoverer}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.PeerListener} (jvm-libp2p).
 */
@FunctionalInterface
public interface PeerListener {

    void peerFound(PeerInfo peerInfo);
}
