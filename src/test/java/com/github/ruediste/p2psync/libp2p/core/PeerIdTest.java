package com.github.ruediste.p2psync.libp2p.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import org.junit.Test;

public class PeerIdTest {

    @Test
    public void base58RoundTrips() {
        PeerId id = PeerId.random();
        String base58 = id.toBase58();
        PeerId decoded = PeerId.fromBase58(base58);
        assertTrue(Arrays.equals(id.getBytes(), decoded.getBytes()));
        assertEquals(id, decoded);
    }

    @Test
    public void hexRoundTrips() {
        PeerId id = PeerId.random();
        String hex = id.toHex();
        PeerId decoded = PeerId.fromHex(hex);
        assertEquals(id, decoded);
    }

    @Test
    public void rejectsTooShortOrTooLong() {
        try {
            new PeerId(new byte[10]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new PeerId(new byte[60]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void distinctRandomIdsAreNotEqual() {
        assertFalse(PeerId.random().equals(PeerId.random()));
        assertNotEquals(PeerId.random(), PeerId.random());
    }
}
