package com.github.ruediste.p2psync.libp2p.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpDiallingTransport;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpServer;

public class TransportTest {

    /**
     * Stub upgrader that does nothing — validates the upgrade pipeline sequencing.
     */
    private static final ConnectionBuilder STUB_BUILDER = new ConnectionBuilder() {

        @Override
        public Connection upgrade(RawConnection rawConnection) {
            return new Connection(rawConnection, null, null);
        }

    };

    @Test
    public void loopbackConnectAndUpgrade() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        CountDownLatch serverDone = new CountDownLatch(1);

        TcpServer server = new TcpServer(serverSocket, connection -> {
            assertNotNull(connection);
            assertTrue(connection.stream().isInitiator() == false);
            assertNotNull(connection.remoteAddress());
            serverDone.countDown();

        });
        server.start();

        TcpDiallingTransport transport = new TcpDiallingTransport();
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/" + port);
        RawConnection raw = transport.dial(addr);
        Connection clientConn = STUB_BUILDER.upgrade(raw);

        assertNotNull(clientConn);
        assertTrue(clientConn.rawConnection().stream().isInitiator());
        assertTrue(serverDone.await(5, TimeUnit.SECONDS));

        server.close();
    }

    @Test
    public void tcpTransportHandlesAddress() {
        TcpDiallingTransport transport = new TcpDiallingTransport();
        assertTrue(transport.handles(new Multiaddr("/ip4/127.0.0.1/tcp/9000")));
        assertTrue(transport.handles(
                new Multiaddr("/ip4/1.2.3.4/tcp/1234/p2p/12D3KooWBMq1iwruB5Nho4FPPRUhD5UGuauPrWNwLgRx7RkJYatC")));
    }

    @Test
    public void serverGetListenAddress() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        TcpServer server = new TcpServer(serverSocket, conn -> {
        });
        server.start();
        Multiaddr addr = server.getActualListeningAddr();
        assertTrue(addr.toString().contains("/tcp/" + port));
        server.close();
    }

    @Test
    public void socketAddressUsesIp6ForIpv6Sockets() throws Exception {
        // The multiaddr must use /ip6/ for IPv6 addresses so it can be parsed
        // back, instead of always emitting /ip4/.
        java.net.InetAddress ipv6 = java.net.InetAddress.getByName("2001:db8::1");
        Multiaddr addr = com.github.ruediste.p2psync.libp2p.transport.tcp.SocketUtils
                .toMultiaddr(ipv6, 4001);
        assertTrue(addr.has(com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol.IP6),
                addr.toString());
        assertEquals(ipv6, java.net.InetAddress.getByName(
                addr.getFirstComponent(com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol.IP6)
                        .getStringValue()));
    }
}