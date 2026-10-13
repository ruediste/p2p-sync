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
import java.util.function.Consumer;

import com.github.ruediste.p2psync.libp2p.core.ConnectionEstablishedListener;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransport;

/**
 * Wraps a bound {@link ServerSocket}. {@link #start()} spawns one dedicated
 * virtual thread
 * running an accept loop. Each accepted socket gets its own fresh virtual
 * thread that runs the
 * full connection upgrade sequence synchronously before invoking the
 * application's
 * {@link ConnectionEstablishedListener}.
 */
public final class TcpServer implements ListeningTransport {

    private final ServerSocket serverSocket;
    private final Consumer<RawConnection> connectionHandler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<RawConnection> connections = new ArrayList<>();
    private Thread acceptThread;

    public TcpServer(ServerSocket serverSocket, Consumer<RawConnection> connectionHandler) {
        this.serverSocket = serverSocket;
        this.connectionHandler = connectionHandler;
    }

    @Override
    public Multiaddr getActualListeningAddr() {
        return new Multiaddr("/ip4/" + serverSocket.getInetAddress().getHostAddress()
                + "/tcp/" + serverSocket.getLocalPort());
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
        RawConnection connection = SocketUtils.toConnection(socket, false);
        lock.lock();
        try {
            connections.add(connection);
        } finally {
            lock.unlock();
        }
        try {
            connectionHandler.accept(connection);
        } catch (RuntimeException e) {
            // The handler failed to upgrade/take over the connection; nothing
            // owns the socket anymore, so close it. On success the socket
            // stays open: its lifecycle is now owned by the layers above
            // (they close it via Connection.close()).
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore close failure
            }
            throw e;
        }
    }

    @Override
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

    public List<RawConnection> getConnections() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(connections));
        } finally {
            lock.unlock();
        }
    }
}
