package com.github.ruediste.p2psync.libp2p.network;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.ConnectionEstablishedListener;
import com.github.ruediste.p2psync.libp2p.core.Network;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ConnectionBuilder;
import com.github.ruediste.p2psync.libp2p.transport.DiallingTransport;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransport;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransportBinding;

/**
 * Manages transports, listening endpoints, and the active connection table.
 *
 * <p>
 * Ported from {@code io.libp2p.network.NetworkImpl} (jvm-libp2p), adapted for
 * this project's blocking-I/O / virtual-thread model.
 */
public final class NetworkImpl implements Network {

    private final List<DiallingTransport> transports;
    private final ConnectionBuilder connectionBuilder;
    private final ConnectionEstablishedListener connectionEstablishedListener;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final Map<Multiaddr, ListeningTransport> servers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<ListeningTransportBinding> listeningTransportBindings;

    public NetworkImpl(List<DiallingTransport> initiatingTransports,
            List<ListeningTransportBinding> listeningTransportBindings, ConnectionBuilder connectionBuilder,
            ConnectionEstablishedListener connectionEstablishedListener) {
        this.listeningTransportBindings = listeningTransportBindings;
        this.transports = List.copyOf(initiatingTransports);
        this.connectionBuilder = connectionBuilder;
        this.connectionEstablishedListener = connectionEstablishedListener;
    }

    @Override
    public List<DiallingTransport> transports() {
        return transports;
    }

    @Override
    public ConnectionEstablishedListener connectionHandler() {
        return connectionEstablishedListener;
    }

    @Override
    public List<Connection> connections() {
        return List.copyOf(connections);
    }

    @Override
    public CompletableFuture<Void> listen(Multiaddr addr) {
        return CompletableFuture.supplyAsync(() -> {

            try {
                for (var binding : listeningTransportBindings) {
                    if (!binding.handles(addr))
                        continue;
                    var transport = binding.listen(addr, rawConnection -> {
                        var conn = connectionBuilder.upgrade(rawConnection);
                        connections.add(conn);
                        try {
                            connectionEstablishedListener.handleConnection(conn);
                        } catch (RuntimeException e) {
                            connections.remove(conn);
                            conn.close();
                            throw e;
                        }
                    });
                    servers.put(addr, transport);
                    return null;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to listen on " + addr, e);
            }
            throw new RuntimeException("No transport found for " + addr);

        }, executor);
    }

    @Override
    public CompletableFuture<Void> unlisten(Multiaddr addr) {
        return CompletableFuture.runAsync(() -> {
            ListeningTransport server = servers.remove(addr);
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to close listener for " + addr, e);
                }
            }
        }, executor);
    }

    @Override
    public List<Multiaddr> listenAddresses() {
        return servers.values().stream()
                .map(ListeningTransport::getActualListeningAddr)
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Connection> connect(PeerId id, Multiaddr... addrs) {
        return CompletableFuture.supplyAsync(() -> {
            // Reuse an existing, still-open connection to this peer if present
            for (Connection conn : connections) {
                if (conn.muxerSession() != null && !conn.muxerSession().isClosed()
                        && id.equals(conn.secureSession().getRemoteId())) {
                    return conn;
                }
            }

            // Try each transport / address in sequence, return first success.
            Exception lastError = null;
            for (Multiaddr addr : addrs) {
                Multiaddr addrWithP2P;
                try {
                    addrWithP2P = addr.withP2P(id);
                } catch (IllegalArgumentException e) {
                    lastError = e;
                    continue;
                }
                for (DiallingTransport transport : transports) {
                    if (!transport.handles(addrWithP2P)) {
                        continue;
                    }
                    RawConnection raw = null;
                    Connection conn = null;
                    try {
                        raw = transport.dial(addrWithP2P);
                        // The expected identity is verified as part of the
                        // upgrade, before the application listener sees it.
                        conn = connectionBuilder.upgrade(raw, id);
                        if (!id.equals(conn.secureSession().getRemoteId())) {
                            throw new RuntimeException("Remote peer identity mismatch: expected " + id
                                    + ", got " + conn.secureSession().getRemoteId());
                        }
                        connections.add(conn);
                        try {
                            connectionEstablishedListener.handleConnection(conn);
                        } catch (RuntimeException e) {
                            connections.remove(conn);
                            conn.close();
                            throw e;
                        }
                        return conn;
                    } catch (Exception e) {
                        lastError = e;
                        if (conn != null) {
                            connections.remove(conn);
                            closeQuietly(conn);
                        } else if (raw != null) {
                            closeQuietly(raw);
                        }
                    }
                }
            }
            throw new RuntimeException("Failed to connect to " + id + " via " + List.of(addrs),
                    lastError);
        }, executor);
    }

    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (RuntimeException ignored) {
            // already failing; keep the original cause
        }
    }

    private static void closeQuietly(RawConnection raw) {
        try {
            raw.stream().close();
        } catch (RuntimeException ignored) {
            // already failing; keep the original cause
        }
    }

    @Override
    public CompletableFuture<Void> disconnect(Connection conn) {
        return CompletableFuture.runAsync(() -> {
            connections.remove(conn);
            conn.close();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            for (var server : servers.values()) {
                try {
                    server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            servers.clear();
            for (Connection conn : connections) {
                conn.close();
            }
            connections.clear();
            executor.shutdown();
        }, executor);
    }

}