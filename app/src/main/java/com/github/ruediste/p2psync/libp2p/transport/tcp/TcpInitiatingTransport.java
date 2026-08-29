package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.InitiatingTransport;

/**
 * TCP transport implementation. Dials remote addresses and checks whether a
 * multiaddr is
 * handleable (ip4/ip6 + tcp).
 */
public final class TcpInitiatingTransport implements InitiatingTransport {

    @Override
    public RawConnection dial(Multiaddr address) {
        var hostPort = SocketUtils.getHostPort(address)
                .orElseThrow(() -> new IllegalArgumentException("No TCP component in multiaddr: " + address));

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostPort.host(), hostPort.port()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to connect to " + hostPort, e);
        }
        return SocketUtils.toConnection(socket, true);
    }

    @Override
    public boolean handles(Multiaddr address) {
        return SocketUtils.getHostPort(address).isPresent();
    }

}
