package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.MultiaddrComponent;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;
import com.github.ruediste.p2psync.libp2p.transport.ConnectionBuilder;
import com.github.ruediste.p2psync.libp2p.transport.Transport;

/**
 * TCP transport implementation. Dials remote addresses and checks whether a
 * multiaddr is
 * handleable (ip4/ip6 + tcp).
 */
public final class TcpTransport implements Transport {

    private static final String[] PROTOCOLS = { "tcp" };

    private final ConnectionBuilder connectionBuilder;

    public TcpTransport(ConnectionBuilder connectionBuilder) {
        this.connectionBuilder = connectionBuilder;
    }

    public ConnectionBuilder getConnectionBuilder() {
        return connectionBuilder;
    }

    @Override
    public Connection dial(Multiaddr address) {
        MultiaddrComponent tcpComponent = address.getFirstComponent(Protocol.TCP);
        if (tcpComponent == null) {
            throw new IllegalArgumentException("No TCP component in multiaddr: " + address);
        }
        String host = resolveHost(address);
        int port = tcpComponent.getIntValue();

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to connect to " + host + ":" + port, e);
        }
        return connectionBuilder.upgrade(socket, true);
    }

    @Override
    public boolean handles(Multiaddr address) {
        for (MultiaddrComponent component : address.getComponents()) {
            if (component.getProtocol() == Protocol.IP4 || component.getProtocol() == Protocol.IP6) {
                continue;
            }
            if (component.getProtocol() == Protocol.TCP) {
                continue;
            }
            if (component.getProtocol() == Protocol.P2P) {
                continue;
            }
            return false;
        }
        return address.getFirstComponent(Protocol.TCP) != null;
    }

    @Override
    public String[] getProtocols() {
        return PROTOCOLS;
    }

    private static String resolveHost(Multiaddr address) {
        MultiaddrComponent ip4 = address.getFirstComponent(Protocol.IP4);
        if (ip4 != null) {
            return ip4.getStringValue();
        }
        MultiaddrComponent ip6 = address.getFirstComponent(Protocol.IP6);
        if (ip6 != null) {
            return ip6.getStringValue();
        }
        throw new IllegalArgumentException("No IP component in multiaddr: " + address);
    }
}
