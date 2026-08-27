package com.github.ruediste.p2psync.libp2p.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.ConnectionHandler;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpServer;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpTransport;

public class M4TransportTest {

    /** Stub upgrader that does nothing — validates the upgrade pipeline sequencing. */
    private static final ConnectionUpgrader STUB_UPGRADER = new ConnectionUpgrader() {
        @Override
        public void establishSecureChannel(Connection connection) {
        }

        @Override
        public void establishMuxer(Connection connection) {
        }
    };

    @Test
    public void loopbackConnectAndUpgrade() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        CountDownLatch serverDone = new CountDownLatch(1);
        ConnectionBuilder builder = new ConnectionBuilder(STUB_UPGRADER);

        TcpServer server = new TcpServer(serverSocket, builder, new ConnectionHandler() {
            @Override
            public void handleConnection(Connection connection) {
                assertNotNull(connection);
                assertTrue(connection.isInitiator() == false);
                assertNotNull(connection.remoteAddress());
                serverDone.countDown();
            }
        });
        server.start();

        TcpTransport transport = new TcpTransport(builder);
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/" + port);
        Connection clientConn = transport.dial(addr);

        assertNotNull(clientConn);
        assertTrue(clientConn.isInitiator());
        assertTrue(serverDone.await(5, TimeUnit.SECONDS));

        clientConn.close();
        server.close();
    }

    @Test
    public void tcpTransportHandlesAddress() {
        TcpTransport transport = new TcpTransport(new ConnectionBuilder(STUB_UPGRADER));
        assertTrue(transport.handles(new Multiaddr("/ip4/127.0.0.1/tcp/9000")));
        assertTrue(transport.handles(new Multiaddr("/ip4/1.2.3.4/tcp/1234/p2p/12D3KooWBMq1iwruB5Nho4FPPRUhD5UGuauPrWNwLgRx7RkJYatC")));
    }

    @Test
    public void serverGetListenAddress() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        ConnectionBuilder builder = new ConnectionBuilder(STUB_UPGRADER);
        TcpServer server = new TcpServer(serverSocket, builder, conn -> {
        });
        server.start();
        Multiaddr addr = server.getListenAddress();
        assertTrue(addr.toString().contains("/tcp/" + port));
        server.close();
    }
}
