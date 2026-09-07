package com.github.ruediste.p2psync.libp2p;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.ruediste.p2psync.proto.Libp2P;
import com.github.ruediste.p2psync.proto.Noise;
import com.google.protobuf.ByteString;

/**
 * M0 acceptance test: confirms the protobuf-maven-plugin generated
 * {@code com.github.ruediste.p2psync.proto.libp2p.Crypto} and
 * {@code com.github.ruediste.p2psync.proto.libp2p.Spipe} classes are on the
 * compile/test classpath and round-trip correctly.
 */
public class ProtobufToolchainTest {

    @Test
    public void cryptoPublicKeyRoundTrips() throws Exception {
        byte[] rawKey = { 1, 2, 3, 4, 5 };
        Libp2P.PublicKey proto = Libp2P.PublicKey.newBuilder()
                .setType(Libp2P.KeyType.Ed25519)
                .setData(ByteString.copyFrom(rawKey))
                .build();

        byte[] marshaled = proto.toByteArray();

        Libp2P.PublicKey parsed = Libp2P.PublicKey.parseFrom(marshaled);
        assertEquals(Libp2P.KeyType.Ed25519, parsed.getType());
        assertArrayEquals(rawKey, parsed.getData().toByteArray());
    }

    @Test
    public void spipeNoiseHandshakePayloadRoundTrips() throws Exception {
        byte[] libp2pKey = { 9, 9, 9 };
        byte[] signature = { 7, 7 };
        Noise.NoiseHandshakePayload proto = Noise.NoiseHandshakePayload.newBuilder()
                .setLibp2PKey(ByteString.copyFrom(libp2pKey))
                .setNoiseStaticKeySignature(ByteString.copyFrom(signature))
                .build();

        Noise.NoiseHandshakePayload parsed = Noise.NoiseHandshakePayload.parseFrom(proto.toByteArray());
        assertArrayEquals(libp2pKey, parsed.getLibp2PKey().toByteArray());
        assertArrayEquals(signature, parsed.getNoiseStaticKeySignature().toByteArray());
    }
}
