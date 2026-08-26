package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import org.junit.Test;

public class MultiaddrTest {

    @Test
    public void parsesAndSerializesIp4Tcp() {
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001");
        assertEquals("/ip4/127.0.0.1/tcp/4001", addr.toString());
        assertTrue(addr.has(Protocol.IP4));
        assertTrue(addr.has(Protocol.TCP));
        assertFalse(addr.has(Protocol.P2P));
        assertEquals("127.0.0.1", addr.getFirstComponent(Protocol.IP4).getStringValue());
        assertEquals("4001", addr.getFirstComponent(Protocol.TCP).getStringValue());
    }

    @Test
    public void byteSerializationRoundTrips() {
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001");
        byte[] serialized = addr.serialize();
        Multiaddr deserialized = Multiaddr.deserialize(serialized);
        assertEquals(addr, deserialized);
        assertEquals(addr.toString(), deserialized.toString());
    }

    @Test
    public void parsesAndExtractsPeerIdFromP2pComponent() {
        PeerId peerId = PeerId.random();
        String addrString = "/ip4/127.0.0.1/tcp/4001/p2p/" + peerId.toBase58();
        Multiaddr addr = new Multiaddr(addrString);
        assertEquals(addrString, addr.toString());

        PeerId extracted = addr.getPeerId();
        assertArrayEquals(peerId.getBytes(), extracted.getBytes());
    }

    @Test
    public void getPeerIdReturnsNullWhenAbsent() {
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001");
        assertNull(addr.getPeerId());
    }

    @Test
    public void withP2pAppendsComponent() {
        PeerId peerId = PeerId.random();
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001").withP2P(peerId);
        assertArrayEquals(peerId.getBytes(), addr.getPeerId().getBytes());
        assertEquals("/ip4/127.0.0.1/tcp/4001/p2p/" + peerId.toBase58(), addr.toString());
    }

    @Test
    public void withP2pIsIdempotentForSameId() {
        PeerId peerId = PeerId.random();
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001").withP2P(peerId);
        Multiaddr again = addr.withP2P(peerId);
        assertEquals(addr, again);
    }

    @Test
    public void withP2pRejectsMismatchedExistingId() {
        Multiaddr addr = new Multiaddr(
                "/ip4/127.0.0.1/tcp/4001/p2p/" + PeerId.random().toBase58());
        try {
            addr.withP2P(PeerId.random());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void rejectsMalformedAddresses() {
        assertThrows("no leading slash", "ip4/127.0.0.1/tcp/4001");
        assertThrows("unknown protocol", "/foo/127.0.0.1");
        assertThrows("invalid ip4 value", "/ip4/not-an-ip/tcp/4001");
        assertThrows("invalid tcp port", "/ip4/127.0.0.1/tcp/999999");
        assertThrows("missing value", "/ip4/127.0.0.1/tcp");
    }

    private static void assertThrows(String description, String malformedAddr) {
        try {
            new Multiaddr(malformedAddr);
            fail("expected IllegalArgumentException for " + description + ": " + malformedAddr);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
