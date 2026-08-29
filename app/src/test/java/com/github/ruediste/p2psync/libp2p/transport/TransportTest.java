package com.github.ruediste.p2psync.libp2p.transport;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpInitiatingTransport;
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

        TcpInitiatingTransport transport = new TcpInitiatingTransport();
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
        TcpInitiatingTransport transport = new TcpInitiatingTransport();
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
        Multiaddr addr = server.getListenAddress();
        assertTrue(addr.toString().contains("/tcp/" + port));
        server.close();
    }
}