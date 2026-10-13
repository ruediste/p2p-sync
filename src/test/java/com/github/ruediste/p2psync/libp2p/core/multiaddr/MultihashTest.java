package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.security.MessageDigest;
import org.junit.Test;

public class MultihashTest {

    @Test
    public void identityDigestRoundTrips() throws Exception {
        byte[] content = "hello".getBytes();
        byte[] mh = Multihash.sum(Multihash.Digest.IDENTITY, content);
        Multihash.Decoded decoded = Multihash.decode(mh);
        assertEquals(Multihash.Digest.IDENTITY, decoded.digest);
        assertArrayEquals(content, decoded.value);
    }

    @Test
    public void sha2_256DigestMatchesJdk() throws Exception {
        byte[] content = "hello world".getBytes();
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(content);
        byte[] mh = Multihash.sum(Multihash.Digest.SHA2_256, content);
        Multihash.Decoded decoded = Multihash.decode(mh);
        assertEquals(Multihash.Digest.SHA2_256, decoded.digest);
        assertArrayEquals(expected, decoded.value);
    }

    @Test
    public void encodingHasExpectedPrefix() {
        // identity code 0x00, length 5 ("hello")
        byte[] mh = Multihash.sum(Multihash.Digest.IDENTITY, "hello".getBytes());
        assertEquals(0x00, mh[0]);
        assertEquals(0x05, mh[1]);

        // sha2-256 code 0x12, length 32
        byte[] shaMh = Multihash.sum(Multihash.Digest.SHA2_256, "hello".getBytes());
        assertEquals(0x12, shaMh[0]);
        assertEquals(32, shaMh[1]);
    }
}
