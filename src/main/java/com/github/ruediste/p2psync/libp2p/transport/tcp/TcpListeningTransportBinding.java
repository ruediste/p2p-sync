package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.function.Consumer;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransport;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransportBinding;

public class TcpListeningTransportBinding implements ListeningTransportBinding {

    /** Listening socket accept backlog. */
    private static final int BACKLOG = 50;

    @Override
    public boolean handles(Multiaddr address) {
        return SocketUtils.getHostPort(address).isPresent();
    }

    @Override
    public ListeningTransport listen(Multiaddr address, Consumer<RawConnection> connectionHandler) {
        var hostPort = SocketUtils.getHostPort(address)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Address " + address));

        java.net.ServerSocket serverSocket;
        try {
            serverSocket = new java.net.ServerSocket(hostPort.port(), BACKLOG,
                    InetAddress.getByName(hostPort.host()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to listen on " + address, e);
        }
        var server = new TcpServer(serverSocket, connectionHandler);
        server.start();
        return server;
    }

}
