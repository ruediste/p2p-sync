package com.github.ruediste.p2psync.libp2p;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.protobuf.ByteString;

import crypto.pb.Crypto;
import spipe.pb.Spipe;

/**
 * M0 acceptance test: confirms the protobuf-maven-plugin generated
 * {@code crypto.pb.Crypto} and {@code spipe.pb.Spipe} classes are on the
 * compile/test classpath and round-trip correctly.
 */
public class ProtobufToolchainTest {

    @Test
    public void cryptoPublicKeyRoundTrips() throws Exception {
        byte[] rawKey = { 1, 2, 3, 4, 5 };
        Crypto.PublicKey proto = Crypto.PublicKey.newBuilder()
                .setType(Crypto.KeyType.Ed25519)
                .setData(ByteString.copyFrom(rawKey))
                .build();

        byte[] marshaled = proto.toByteArray();

        Crypto.PublicKey parsed = Crypto.PublicKey.parseFrom(marshaled);
        assertEquals(Crypto.KeyType.Ed25519, parsed.getType());
        assertArrayEquals(rawKey, parsed.getData().toByteArray());
    }

    @Test
    public void spipeNoiseHandshakePayloadRoundTrips() throws Exception {
        byte[] libp2pKey = { 9, 9, 9 };
        byte[] signature = { 7, 7 };
        Spipe.NoiseHandshakePayload proto = Spipe.NoiseHandshakePayload.newBuilder()
                .setLibp2PKey(ByteString.copyFrom(libp2pKey))
                .setNoiseStaticKeySignature(ByteString.copyFrom(signature))
                .build();

        Spipe.NoiseHandshakePayload parsed = Spipe.NoiseHandshakePayload.parseFrom(proto.toByteArray());
        assertArrayEquals(libp2pKey, parsed.getLibp2PKey().toByteArray());
        assertArrayEquals(signature, parsed.getNoiseStaticKeySignature().toByteArray());
    }
}
