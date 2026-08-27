package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.github.ruediste.p2psync.libp2p.core.ConnectionHandler;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ConnectionBuilder;

/**
 * Wraps a bound {@link ServerSocket}. {@link #start()} spawns one dedicated
 * virtual thread
 * running an accept loop. Each accepted socket gets its own fresh virtual
 * thread that runs the
 * full connection upgrade sequence synchronously before invoking the
 * application's
 * {@link ConnectionHandler}.
 */
public final class TcpServer {

    private final ServerSocket serverSocket;
    private final ConnectionBuilder connectionBuilder;
    private final ConnectionHandler connectionHandler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<com.github.ruediste.p2psync.libp2p.core.Connection> connections = new ArrayList<>();
    private Thread acceptThread;

    public TcpServer(ServerSocket serverSocket, ConnectionBuilder connectionBuilder,
            ConnectionHandler connectionHandler) {
        this.serverSocket = serverSocket;
        this.connectionBuilder = connectionBuilder;
        this.connectionHandler = connectionHandler;
    }

    public Multiaddr getListenAddress() {
        return new Multiaddr("/ip4/0.0.0.0/tcp/" + serverSocket.getLocalPort());
    }

    public void start() {
        lock.lock();
        try {
            if (acceptThread != null) {
                throw new IllegalStateException("Server already started");
            }
            acceptThread = Thread.ofVirtual().name("tcp-accept-" + serverSocket.getLocalPort())
                    .unstarted(this::acceptLoop);
            acceptThread.start();
        } finally {
            lock.unlock();
        }
    }

    private void acceptLoop() {
        try {
            while (!closed.get()) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    if (closed.get()) {
                        break;
                    }
                    throw new UncheckedIOException(e);
                }
                Thread.ofVirtual().name("tcp-handle-" + socket.getRemoteSocketAddress())
                        .start(() -> handleAccepted(socket));
            }
        } catch (RuntimeException e) {
            if (!closed.get()) {
                throw e;
            }
        }
    }

    private void handleAccepted(Socket socket) {
        try {
            com.github.ruediste.p2psync.libp2p.core.Connection connection = connectionBuilder.upgrade(socket, false);
            lock.lock();
            try {
                connections.add(connection);
            } finally {
                lock.unlock();
            }
            connectionHandler.handleConnection(connection);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore close failure
            }
        }
    }

    public void close() {
        lock.lock();
        try {
            if (closed.compareAndSet(false, true)) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (acceptThread != null) {
                    try {
                        acceptThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    acceptThread = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<com.github.ruediste.p2psync.libp2p.core.Connection> getConnections() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(connections));
        } finally {
            lock.unlock();
        }
    }
}
