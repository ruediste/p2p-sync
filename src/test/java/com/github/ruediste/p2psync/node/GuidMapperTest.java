package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.util.FullGuid;
import com.github.ruediste.p2psync.util.ShortGuid;

class GuidMapperTest {
    @TempDir
    Path tempDir;

    /**
     * Verifies full GUID encoding and per-unit short mapping survive a node reopen.
     */
    @Test
    void fullGuidAndShortMappingRoundTripAcrossRestart() {
        Path dataFile = tempDir.resolve("node.db");
        var network = new Node.Network();
        var entityNodeId = NodeId.fromPublicKey(Ed25519PrivateKey.generateKeyPair().publicKey());
        var fullGuid = new FullGuid(entityNodeId, 0x0102030405060708L);
        var expectedBytes = ByteBuffer.allocate(FullGuid.LENGTH)
                .put(entityNodeId.bytes()).putLong(fullGuid.counter()).array();
        assertArrayEquals(expectedBytes, fullGuid.bytes());
        assertEquals(fullGuid, FullGuid.fromBytes(expectedBytes));

        UserId localUserId;
        ShortGuid shortGuid;
        try (var node = new Node(network, dataFile)) {
            var user = node.createNewUser("alice");
            localUserId = user.data.userId();
            shortGuid = user.data.guidMapper.toShort(fullGuid);
            assertEquals(fullGuid, user.data.guidMapper.toFull(shortGuid));
            node.persistState();
        }

        try (var reopened = new Node(network, dataFile)) {
            var mapper = reopened.getUser(localUserId).data.guidMapper;
            assertEquals(shortGuid.shortNodeId, mapper.toShort(fullGuid).shortNodeId);
            assertEquals(fullGuid, mapper.toFull(shortGuid));
        }
    }
}
