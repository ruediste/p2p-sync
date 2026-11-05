package com.github.ruediste.p2psync.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;

class IdentityTest {

    /**
     * Verifies identity hashes use canonical key bytes and distinct type domains.
     */
    @Test
    void derivesStableDomainSeparatedIdsFromCanonicalPublicKeyBytes() {
        var key = Ed25519PrivateKey.generateKeyPair().publicKey();
        byte[] keyBytes = key.bytes();
        byte[] nodeDomain = "p2p-sync:node-id:v1:".getBytes(StandardCharsets.US_ASCII);
        byte[] userDomain = "p2p-sync:user-id:v1:".getBytes(StandardCharsets.US_ASCII);

        var expectedNodeId = CryptoUtils.sha256(ByteBuffer.allocate(nodeDomain.length + keyBytes.length)
                .put(nodeDomain).put(keyBytes).array());
        var expectedUserId = CryptoUtils.sha256(ByteBuffer.allocate(userDomain.length + keyBytes.length)
                .put(userDomain).put(keyBytes).array());

        assertArrayEquals(expectedNodeId, NodeId.fromPublicKey(key).bytes());
        assertArrayEquals(expectedUserId, UserId.fromPublicKey(key).bytes());
        assertEquals(NodeId.fromPublicKey(key), NodeId.fromBytes(expectedNodeId));
        assertEquals(UserId.fromPublicKey(key), UserId.fromBytes(expectedUserId));
        assertNotEquals(NodeId.fromPublicKey(key).hex(), UserId.fromPublicKey(key).hex());
    }

    /**
     * Verifies identity inputs are fixed-width and cannot mutate an existing value.
     */
    @Test
    void validatesLengthAndDefensivelyCopiesBytes() {
        assertThrows(IllegalArgumentException.class, () -> NodeId.fromBytes(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> UserId.fromBytes(new byte[33]));

        byte[] source = new byte[32];
        source[0] = 7;
        var id = NodeId.fromBytes(source);
        source[0] = 8;
        byte[] returned = id.bytes();
        returned[0] = 9;
        assertEquals(7, id.bytes()[0]);
    }
}