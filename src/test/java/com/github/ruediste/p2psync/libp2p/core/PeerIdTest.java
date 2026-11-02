package com.github.ruediste.p2psync.libp2p.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PublicKey;

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

    @Test
    public void fromPubKeyIsStableForSamePubKeyBytes() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        PubKey pub = priv.publicKey();

        PeerId id1 = PeerId.fromPubKey(pub);
        PeerId id2 = PeerId.fromPubKey(pub);

        assertEquals(id1, id2);
        assertEquals(id1.toBase58(), id2.toBase58());
    }

    @Test
    public void fromPubKeyUsesIdentityMultihashForEd25519() {
        // Golden fixture, independently computed (see the implementation notes in the
        // commit
        // introducing this test): a fixed 32-byte Ed25519 public key point, marshaled
        // per
        // crypto.proto (field 1 KeyType.Ed25519 = varint tag 0x08 0x01; field 2 Data =
        // tag 0x12,
        // length 0x20, then the 32 raw bytes -- 36 bytes total), wrapped in the
        // multihash
        // "identity" digest (36 <= 42, so PeerId.fromPubKey must pick identity over
        // sha2-256):
        // code 0x00, length 0x24 (36), then the 36 marshaled bytes -- 38 bytes total --
        // and
        // finally base58-encoded. This exercises the exact "12D3KooW..." prefix real
        // libp2p
        // Ed25519 peer ids have (the fixed protobuf+multihash header bytes decode to
        // that
        // prefix in base58 regardless of the actual key bytes).
        byte[] rawPub = fromHex("1fa3c8e2a1b1c11e5a83e1d2df5f6f8a91c2b3d4e5f60718293a4b5c6d7e8f90");
        PubKey pub = Ed25519PublicKey.unmarshal(rawPub);

        PeerId id = PeerId.fromPubKey(pub);

        assertEquals("12D3KooWBwsfChbsA4wh7caj22LbektSmfLcXb18ywCNCJQkETmR", id.toBase58());
    }

    @Test
    public void fromPubKeyRoundTripsThroughPrivateKeySignature() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        PeerId id = PeerId.fromPubKey(priv.publicKey());

        // A PeerId derived straight from raw() bytes must match one derived through a
        // full
        // marshal/unmarshal round trip of the same key.
        PubKey reconstructed = Ed25519PublicKey.unmarshal(priv.publicKey().raw());
        assertEquals(id, PeerId.fromPubKey(reconstructed));
    }

    @Test
    public void fromHexRejectsOddLengthAndNonHex() {
        // Both malformed inputs must fail instead of silently producing a
        // truncated/zeroed peer id.
        try {
            PeerId.fromHex("abc");
            fail("expected IllegalArgumentException for odd length");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            PeerId.fromHex("zz" + "00".repeat(31));
            fail("expected IllegalArgumentException for non-hex characters");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void bytesAreDefensivelyCopied() {
        // Mutating either input or output arrays must not change the PeerId.
        byte[] input = new byte[32];
        input[0] = 1;
        PeerId id = new PeerId(input);
        input[0] = 2;
        assertEquals(1, id.getBytes()[0]);

        byte[] output = id.getBytes();
        output[0] = 3;
        assertEquals(1, id.getBytes()[0]);
    }

    private static byte[] fromHex(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }
}
