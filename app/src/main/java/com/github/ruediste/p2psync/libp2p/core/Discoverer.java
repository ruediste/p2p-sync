package com.github.ruediste.p2psync.libp2p.core;

import java.util.concurrent.CompletableFuture;

/**
 * A peer discovery mechanism, e.g. LAN mDNS.
 *
 * <p>
 * Ported from {@code io.libp2p.discovery.Discoverer} (jvm-libp2p).
 */
public interface Discoverer {

    CompletableFuture<Void> start(PeerListener newPeerFoundListener);

    CompletableFuture<Void> stop();

}
