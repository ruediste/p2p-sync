package com.github.ruediste.p2psync.libp2p.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.ConnectionHandler;
import com.github.ruediste.p2psync.libp2p.core.Network;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ConnectionBuilder;
import com.github.ruediste.p2psync.libp2p.transport.InitiatingTransport;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpServer;

/**
 * Manages transports, listening endpoints, and the active connection table.
 *
 * <p>
 * Ported from {@code io.libp2p.network.NetworkImpl} (jvm-libp2p), adapted for
 * this project's blocking-I/O / virtual-thread model.
 */
public final class NetworkImpl implements Network {

    private final List<InitiatingTransport> transports;
    private final ConnectionBuilder connectionBuilder;
    private final ConnectionHandler connectionHandler;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final List<TcpServer> servers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public NetworkImpl(List<InitiatingTransport> transports, ConnectionBuilder connectionBuilder,
            ConnectionHandler connectionHandler) {
        this.transports = List.copyOf(transports);
        this.connectionBuilder = connectionBuilder;
        this.connectionHandler = connectionHandler;
    }

    @Override
    public List<InitiatingTransport> transports() {
        return transports;
    }

    @Override
    public ConnectionHandler connectionHandler() {
        return connectionHandler;
    }

    @Override
    public List<Connection> connections() {
        return List.copyOf(connections);
    }

    @Override
    public CompletableFuture<Void> listen(Multiaddr addr) {
        return CompletableFuture.supplyAsync(() -> {
            findTransport(addr); // validate a transport exists for this addr
            try {
                int port = addr.getFirstComponent(
                        com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol.TCP).getIntValue();
                java.net.ServerSocket serverSocket = new java.net.ServerSocket(port);

                TcpServer server = new TcpServer(serverSocket, connectionBuilder,
                        createHookedConnectionHandler());
                server.start();
                servers.add(server);
                return null;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to listen on " + addr, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> unlisten(Multiaddr addr) {
        return CompletableFuture.runAsync(() -> {
            servers.removeIf(server -> {
                if (server.getListenAddress().equals(addr)) {
                    server.close();
                    return true;
                }
                return false;
            });
        }, executor);
    }

    @Override
    public CompletableFuture<Connection> connect(PeerId id, Multiaddr... addrs) {
        return CompletableFuture.supplyAsync(() -> {
            // Reuse existing connection to this peer if present
            for (Connection conn : connections) {
                if (conn.getRemotePeerId() != null && id.equals(conn.getRemotePeerId())) {
                    return conn;
                }
            }

            // Append /p2p/<peerId> to each address for post-Noise identity verification
            List<Multiaddr> addrsWithP2P = new ArrayList<>();
            for (Multiaddr addr : addrs) {
                addrsWithP2P.add(addr.withP2P(id));
            }

            // Try each transport / address in sequence, return first success
            Exception lastError = null;
            for (Multiaddr addr : addrsWithP2P) {
                for (InitiatingTransport transport : transports) {
                    if (transport.handles(addr)) {
                        try {
                            RawConnection raw = transport.dial(addr);
                            Connection conn = connectionBuilder.upgrade(raw);
                            connections.add(conn);
                            connectionHandler.handleConnection(conn);
                            // Verify the remote peer identity matches what we expect
                            if (!id.equals(conn.getRemotePeerId())) {
                                conn.close();
                                connections.remove(conn);
                                throw new RuntimeException(
                                        "Remote peer identity mismatch: expected " + id
                                                + ", got " + conn.getRemotePeerId());
                            }
                            return conn;
                        } catch (Exception e) {
                            lastError = e;
                        }
                    }
                }
            }
            throw new RuntimeException("Failed to connect to " + id + " via " + List.of(addrs),
                    lastError);
        }, executor);
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
            for (TcpServer server : servers) {
                server.close();
            }
            servers.clear();
            for (Connection conn : connections) {
                conn.close();
            }
            connections.clear();
            executor.shutdown();
        }, executor);
    }

    private InitiatingTransport findTransport(Multiaddr addr) {
        return transports.stream()
                .filter(t -> t.handles(addr))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No transport supports address: " + addr));
    }

    private ConnectionHandler createHookedConnectionHandler() {
        return conn -> {
            connections.add(conn);
            try {
                connectionHandler.handleConnection(conn);
            } catch (RuntimeException e) {
                connections.remove(conn);
                throw e;
            }
        };
    }
}