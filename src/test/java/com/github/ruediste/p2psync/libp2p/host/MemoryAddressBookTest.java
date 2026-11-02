package com.github.ruediste.p2psync.libp2p.host;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Verifies that {@link MemoryAddressBook} honors per-address TTLs: a short-lived
 * address disappears after expiry, while a non-positive TTL keeps it forever and
 * {@link MemoryAddressBook#removePeer} drops the peer immediately.
 */
public class MemoryAddressBookTest {

    @Test
    public void expiresShortLivedAddresses() throws InterruptedException {
        MemoryAddressBook book = new MemoryAddressBook();
        PeerId id = PeerId.random();
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001");

        book.addAddrs(id, 20, addr);
        assertTrue(book.getAddrs(id).contains(addr));

        Thread.sleep(50);
        assertNull(book.getAddrs(id), "expired addresses should be dropped");
        assertFalse(book.getPeerIds().contains(id));
    }

    @Test
    public void nonPositiveTtlNeverExpires() {
        MemoryAddressBook book = new MemoryAddressBook();
        PeerId id = PeerId.random();
        Multiaddr addr = new Multiaddr("/ip4/127.0.0.1/tcp/4001");

        book.setAddrs(id, 0, addr);
        assertTrue(book.getAddrs(id).contains(addr));

        book.removePeer(id);
        assertNull(book.getAddrs(id));
    }
}
