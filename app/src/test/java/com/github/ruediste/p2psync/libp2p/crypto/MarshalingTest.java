package com.github.ruediste.p2psync.libp2p.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;

import crypto.pb.Crypto;

public class MarshalingTest {

    @Test
    public void publicKeyMarshalUnmarshalRoundTrips() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        PubKey pub = priv.publicKey();

        byte[] marshaled = Marshaling.marshalPublicKey(pub);
        PubKey unmarshaled = Marshaling.unmarshalPublicKey(marshaled);

        assertArrayEquals(pub.raw(), unmarshaled.raw());
        assertEquals(pub, unmarshaled);
    }

    @Test
    public void privateKeyMarshalUnmarshalRoundTrips() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();

        byte[] marshaled = Marshaling.marshalPrivateKey(priv);
        PrivKey unmarshaled = Marshaling.unmarshalPrivateKey(marshaled);

        assertArrayEquals(priv.raw(), unmarshaled.raw());
        assertEquals(priv, unmarshaled);

        byte[] data = "G'day!".getBytes(StandardCharsets.UTF_8);
        assertTrue(priv.publicKey().verify(data, unmarshaled.sign(data)));
    }

    @Test
    public void marshaledPublicKeyUsesExpectedProtobufWireFormat() {
        Ed25519PrivateKey priv = Ed25519PrivateKey.generateKeyPair();
        byte[] rawPub = priv.publicKey().raw();

        byte[] marshaled = Marshaling.marshalPublicKey(priv.publicKey());

        // crypto.proto: PublicKey { required KeyType Type = 1; required bytes Data = 2;
        // }
        // field 1 (varint, tag 0x08), value 1 (Ed25519); field 2 (length-delimited, tag
        // 0x12),
        // length 32, followed by the 32 raw key bytes -- 36 bytes total.
        byte[] expected = new byte[4 + 32];
        expected[0] = 0x08;
        expected[1] = 0x01;
        expected[2] = 0x12;
        expected[3] = 0x20;
        System.arraycopy(rawPub, 0, expected, 4, 32);

        assertArrayEquals(expected, marshaled);
    }

    @Test
    public void unmarshalPublicKeyRejectsUnsupportedType() {
        byte[] data = Crypto.PublicKey.newBuilder()
                .setType(Crypto.KeyType.RSA)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[4]))
                .build()
                .toByteArray();
        try {
            Marshaling.unmarshalPublicKey(data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void unmarshalPrivateKeyRejectsUnsupportedType() {
        byte[] data = Crypto.PrivateKey.newBuilder()
                .setType(Crypto.KeyType.RSA)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[4]))
                .build()
                .toByteArray();
        try {
            Marshaling.unmarshalPrivateKey(data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
