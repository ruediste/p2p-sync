package com.github.ruediste.p2psync.libp2p.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.DiallingTransport;

/**
 * Manages transports, listening endpoints, and active connections.
 *
 * <p>
 * Ported from {@code io.libp2p.core.Network} (jvm-libp2p), simplified for
 * this project's blocking-I/O model. Each method returns a
 * {@link CompletableFuture} purely so the public API shape is compatible with
 * the existing plan — internally every method just kicks off blocking work on
 * a virtual thread.
 */
public interface Network {

    List<DiallingTransport> transports();

    ConnectionEstablishedListener connectionHandler();

    List<Connection> connections();

    CompletableFuture<Void> listen(Multiaddr addr);

    CompletableFuture<Void> unlisten(Multiaddr addr);

    /**
     * The addresses the network's transports are actually listening on (with
     * ports resolved from any wildcard/zero ports in the configured addresses).
     */
    List<Multiaddr> listenAddresses();

    CompletableFuture<Connection> connect(PeerId id, Multiaddr... addrs);

    CompletableFuture<Void> disconnect(Connection conn);

    CompletableFuture<Void> close();
}