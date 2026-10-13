package com.github.ruediste.p2psync.libp2p;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.echo.TrivialEchoProtocol;
import com.github.ruediste.p2psync.libp2p.host.HostBuilder;

/**
 * End-to-end integration test: start two {@link Host} instances on loopback
 * TCP, connect them, and verify the full upgrade pipeline (Noise XX handshake,
 * Yamux negotiation) works.
 *
 * <p>
 * Also exercises an application stream via {@link TrivialEchoProtocol} to prove
 * the per-stream multistream-select + Yamux data path end-to-end.
 *
 * <p>
 * Corresponds to milestone M8 in {@code ImplementationPlan.md}.
 */
public class EndToEndConnectionTest {

    private static final long TIMEOUT_SECONDS = 10;

    private CopyOnWriteArrayList<Connection> connectionsOnA;
    private CopyOnWriteArrayList<Connection> connectionsOnB;
    private TrivialEchoProtocol echoProto;
    private Host nodeA;
    private Host nodeB;
    private Connection conn;

    @Before
    public void setUp() throws Exception {
        connectionsOnA = new CopyOnWriteArrayList<>();
        connectionsOnB = new CopyOnWriteArrayList<>();
        echoProto = new TrivialEchoProtocol();

        nodeA = new HostBuilder()
                .listenAddress("/ip4/127.0.0.1/tcp/0")
                .connectionHandler(connectionsOnA::add)
                .protocolHandler(echoProto)
                .build();

        nodeB = new HostBuilder()
                .listenAddress("/ip4/127.0.0.1/tcp/0")
                .connectionHandler(connectionsOnB::add)
                .build();

        nodeA.start().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        nodeB.start().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Multiaddr aAddr = nodeA.network().listenAddresses().get(0);
        conn = nodeB.network().connect(nodeA.peerId(), aAddr)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() throws Exception {
        nodeA.stop().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        nodeB.stop().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void twoNodesConnectAndExchangeData() throws Exception {
        assertNotNull("secureSession should be present", conn.secureSession());
        assertNotNull("muxerSession should be present", conn.muxerSession());
        assertEquals("remote peer id should match node A",
                nodeA.peerId(), conn.getRemotePeerId());

        assertEquals("nodeA should have 1 connection", 1, connectionsOnA.size());
        assertEquals("nodeB should have 1 connection", 1, connectionsOnB.size());

        assertEquals(nodeB.peerId(), connectionsOnA.get(0).getRemotePeerId());
        assertEquals(nodeA.peerId(), connectionsOnB.get(0).getRemotePeerId());
    }

    @Test
    public void echoProtocolOverYamuxStream() throws Exception {
        TrivialEchoProtocol.Controller echo = nodeB.newStream(List.of(echoProto), conn);

        byte[] sent = "Hello, p2p-sync!".getBytes("UTF-8");
        echo.echo(sent);

        byte[] received = new byte[sent.length];
        int n = echo.read(received, 0, received.length);
        assertEquals(sent.length, n);
        assertArrayEquals(sent, received);

        echo.close();
    }

    @Test
    public void twoNodesMultipleEchoStreams() throws Exception {
        int streamCount = 3;
        TrivialEchoProtocol.Controller[] echoes = new TrivialEchoProtocol.Controller[streamCount];
        for (int i = 0; i < streamCount; i++) {
            echoes[i] = nodeB.newStream(List.of(echoProto), conn);
        }

        for (int i = 0; i < streamCount; i++) {
            byte[] sent = ("stream-" + i + "-data").getBytes("UTF-8");
            echoes[i].echo(sent);

            byte[] received = new byte[sent.length];
            int n = echoes[i].read(received, 0, received.length);
            assertEquals(sent.length, n);
            assertArrayEquals("echo mismatch on stream " + i, sent, received);

            echoes[i].close();
        }
    }
}
