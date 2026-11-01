package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class SyncUnitTransferTest {
    /**
     * Verifies a large content operation is served in budgeted segments and
     * resumes from the stored per-operation offset until it is complete.
     */
    @Test
    void resumesLargeContentOperationAcrossSegments() {
        try (var node = new Node(new Node.Network())) {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(),
                    SyncUnitAuthorization.Type.SHARED_FOLDER);
            byte[] content = new byte[2000];
            for (int index = 0; index < content.length; index++)
                content[index] = (byte) (index * 31);
            node.putSharedFile(folder, handle.data.userId(), "large.bin", content);

            var source = node.syncUnits.get(folder);
            var receiver = new Node.SyncUnitData(folder, source.type, source.creator);
            receiver.authorizationOperations.addAll(source.authorizationOperations);
            receiver.replayAuthorization();
            receiver.replayContent();

            int rounds = 0;
            int lastReceived = 0;
            while (receiver.contentOperations.size() < source.contentOperations.size()) {
                var fromClock = new TreeMap<String, Long>();
                receiver.contentOperations.values().forEach(operation -> fromClock.merge(
                        "c:" + java.util.HexFormat.of().formatHex(operation.nodeHash()),
                        operation.lamportClock(), Math::max));
                var offsets = new TreeMap<String, Integer>();
                receiver.partialContentOperations.values().forEach(partial -> offsets.put(
                        java.util.HexFormat.of().formatHex(partial.id.nodeHash) + ":" + partial.id.clock,
                        partial.receivedLength));
                var transfers = source.serveContent(fromClock, offsets, 64);
                receiver.receiveContent(transfers);
                int received = receiver.contentOperations.isEmpty()
                        ? receiver.partialContentOperations.values().stream()
                                .mapToInt(partial -> partial.receivedLength).max().orElse(0)
                        : content.length;
                assertTrue(received >= lastReceived);
                lastReceived = received;
                rounds++;
                assertTrue(rounds < 100, "transfer did not make progress");
            }

            assertTrue(rounds > 1);
            assertTrue(receiver.partialContentOperations.isEmpty());
            assertEquals(1, receiver.contentOperations.size());
            assertArrayEquals(content, receiver.contentOperations.firstEntry().getValue().payload());
            assertArrayEquals(content, source.contentOperations.firstEntry().getValue().payload());
        }
    }

    /**
     * Verifies offsets are ignored for operations already covered by the
     * per-source clock.
     */
    @Test
    void skipsOperationsAlreadyCoveredByClock() {
        try (var node = new Node(new Node.Network())) {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(),
                    SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.putSharedFile(folder, handle.data.userId(), "a.txt", "one".getBytes());
            node.putSharedFile(folder, handle.data.userId(), "b.txt", "two".getBytes());
            var source = node.syncUnits.get(folder);
            var first = source.contentOperations.firstEntry().getValue();
            var clocks = Map.of("c:" + java.util.HexFormat.of().formatHex(first.nodeHash()),
                    first.lamportClock());
            var transfers = source.serveContent(clocks, Map.of(), Integer.MAX_VALUE);
            assertEquals(source.contentOperations.size() - 1, transfers.size());
        }
    }
}
