package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.function.Consumer;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransportBinding;

public class TcpListeningTransportBinding implements ListeningTransportBinding {

    @Override
    public boolean handles(Multiaddr address) {
        return SocketUtils.getHostPort(address).isPresent();
    }

    @Override
    public Closeable listen(Multiaddr address, Consumer<RawConnection> connectionHandler) {
        var hostPort = SocketUtils.getHostPort(address)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Address " + address));

        java.net.ServerSocket serverSocket;
        try {
            serverSocket = new java.net.ServerSocket(hostPort.port(), 50,
                    InetAddress.getByName(hostPort.host()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var server = new TcpServer(serverSocket, connectionHandler);
        server.start();
        return server;
    }

}
