package com.github.ruediste.p2psync.libp2p.crypto.keys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

public class Ed25519KeysTest {

    @Test
    public void signAndVerifyRoundTrips() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        PubKey pub = priv.publicKey();

        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        byte[] signature = priv.sign(data);

        assertTrue(pub.verify(data, signature));
    }

    @Test
    public void tamperedDataFailsVerification() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        PubKey pub = priv.publicKey();

        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        byte[] signature = priv.sign(data);

        byte[] tampered = "G'day?".getBytes(StandardCharsets.UTF_8);
        assertFalse(pub.verify(tampered, signature));
    }

    @Test
    public void rawPrivateKeyRoundTripsThroughUnmarshal() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        byte[] seed = priv.raw();

        Ed25519PrivateKey reconstructed = Ed25519PrivateKey.unmarshal(seed);

        assertArrayEquals(seed, reconstructed.raw());
        assertArrayEquals(priv.publicKey().raw(), reconstructed.publicKey().raw());
    }

    @Test
    public void reconstructedPrivateKeyCanSignAndBeVerifiedByOriginalPublicKey() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        Ed25519PrivateKey reconstructed = Ed25519PrivateKey.unmarshal(priv.raw());

        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        byte[] signature = reconstructed.sign(data);

        assertTrue(priv.publicKey().verify(data, signature));
    }

    @Test
    public void rawPublicKeyRoundTripsThroughUnmarshal() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        byte[] rawPub = priv.publicKey().raw();

        Ed25519PublicKey reconstructed = Ed25519PublicKey.unmarshal(rawPub);

        assertArrayEquals(rawPub, reconstructed.raw());

        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        assertTrue(reconstructed.verify(data, priv.sign(data)));
    }

    @Test
    public void rawSeedLengthIsThirtyTwoBytes() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        assertTrue(priv.raw().length == 32);
        assertTrue(priv.publicKey().raw().length == 32);
    }

    @Test
    public void unmarshalRejectsWrongLength() {
        try {
            Ed25519PrivateKey.unmarshal(new byte[10]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            Ed25519PublicKey.unmarshal(new byte[10]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void generateKeyPairIsUsableThroughPrivKeyFactory() {
        PrivKey priv = Ed25519PrivateKey.generateKeyPair(new SecureRandom());
        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        assertTrue(priv.publicKey().verify(data, priv.sign(data)));
    }
}
