package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;
import com.github.ruediste.p2psync.libp2p.transport.InitiatingTransport;

/**
 * TCP transport implementation. Dials remote addresses and checks whether a
 * multiaddr is
 * handleable (ip4/ip6 + tcp).
 */
public final class TcpInitiatingTransport implements InitiatingTransport {

    @Override
    public RawConnection dial(Multiaddr address) {
        var hostPort = getHostPort(address)
                .orElseThrow(() -> new IllegalArgumentException("No TCP component in multiaddr: " + address));

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostPort.host, hostPort.port));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to connect to " + hostPort, e);
        }
        return SocketUtils.toConnection(socket, true);
    }

    @Override
    public boolean handles(Multiaddr address) {
        return getHostPort(address).isPresent();
    }

    private static Optional<HostPort> getHostPort(Multiaddr address) {
        var components = address.getComponents();
        if (components.size() < 2)
            return Optional.empty();
        var first = components.get(0);
        String host;
        if (first.getProtocol() == Protocol.IP4 || first.getProtocol() == Protocol.IP6)
            host = first.getStringValue();
        else
            return Optional.empty();

        var second = components.get(1);
        if (second.getProtocol() == Protocol.TCP)
            return Optional.of(new HostPort(host, second.getIntValue()));
        else
            return Optional.empty();
    }

    private record HostPort(String host, int port) {
    }
}
