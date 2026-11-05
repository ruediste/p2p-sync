package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.identity.UserId;

class SharedFolderContentTest {
    @TempDir
    Path tempDir;

    /**
     * Verifies shared-folder content is materialized in the FileTree by storing,
     * reading and deleting a nested path without an in-memory map.
     */
    @Test
    void deleteNestedFile() {
        try (var node = new Node(new Node.Network())) {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.putSharedFile(folder, handle.data.userId(), "notes/todo.txt", "shared todo".getBytes());
            assertArrayEquals("shared todo".getBytes(),
                    node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));
            assertEquals(1, node.listSharedFileInfos(folder, handle.data.userId()).size());
            node.deleteSharedFile(folder, handle.data.userId(), "notes/todo.txt");
            assertEquals(0, node.listSharedFileInfos(folder, handle.data.userId()).size());
            assertNull(node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));
        }
    }

    /**
     * Verifies empty shared-folder directories are real FileTree entries: they
     * are visible through stat/list, are deleted explicitly, and survive a
     * restart because the per-unit FileTree root is persisted.
     */
    @Test
    void emptyDirectoriesArePersistentFileTreeEntries() {
        Path dataFile = tempDir.resolve("node.db");
        SyncUnitId folder;
        UserId userId;
        try (var node = new Node(new Node.Network(), dataFile)) {
            var handle = node.createNewUser("alice");
            userId = handle.data.userId();
            folder = node.createSyncUnit(userId, SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.createSharedDirectory(folder, userId, "docs");
            node.createSharedDirectory(folder, userId, "docs/empty");

            assertTrue(node.sharedEntry(folder, userId, "docs").directory());
            assertTrue(node.sharedEntry(folder, userId, "docs/empty").directory());
            List<Node.SharedEntryInfo> rootEntries = node.listSharedDirectory(folder, userId, "");
            assertEquals(1, rootEntries.size());
            assertEquals("docs", rootEntries.get(0).path());
            List<Node.SharedEntryInfo> docsEntries = node.listSharedDirectory(folder, userId, "docs");
            assertEquals(1, docsEntries.size());
            assertEquals("docs/empty", docsEntries.get(0).path());
        }

        try (var node = new Node(new Node.Network(), dataFile)) {
            assertTrue(node.sharedEntry(folder, userId, "docs/empty").directory());
            node.deleteSharedDirectory(folder, userId, "docs");
            assertNull(node.sharedEntry(folder, userId, "docs"));
        }
    }

    /**
     * Verifies shared-folder directory operations are signed content operations
     * and materialize on the peer's own per-unit FileTree.
     */
    @Test
    void directoryOperationsSyncBetweenNodes() {
        var network = new Node.Network();
        try (var nodeA = new Node(network); var nodeB = new Node(network)) {
            var owner = nodeA.createNewUser("owner");
            var member = nodeB.createNewUser("member");
            var folder = nodeA.createSyncUnit(owner.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            nodeA.inviteToSyncUnit(folder, owner.data.userId(), member.data.userId(),
                    SyncUnitAuthorization.Role.EDITOR);
            nodeB.joinSyncUnit(nodeA.peerId, folder, member.data.userId());

            nodeA.createSharedDirectory(folder, owner.data.userId(), "docs/empty");
            nodeB.syncKnownPeers();
            assertTrue(nodeB.sharedEntry(folder, member.data.userId(), "docs/empty").directory());

            nodeB.deleteSharedDirectory(folder, member.data.userId(), "docs");
            nodeA.syncKnownPeers();
            assertNull(nodeA.sharedEntry(folder, owner.data.userId(), "docs"));
        }
    }

    /**
     * Verifies a shared file larger than one META leaf (many DATA blocks) keeps
     * its operation payload in a block chain, so the value stored in the
     * operation B-tree stays small and the file survives a restart.
     */
    @Test
    void largeSharedFileSurvivesRestart() {
        Path dataFile = tempDir.resolve("node.db");
        byte[] content = new byte[12 * 1024 * 1024];
        for (int index = 0; index < content.length; index++)
            content[index] = (byte) (index * 31);
        SyncUnitId folder;
        UserId userId;
        try (var node = new Node(new Node.Network(), dataFile)) {
            var handle = node.createNewUser("alice");
            userId = handle.data.userId();
            folder = node.createSyncUnit(userId, SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.putSharedFile(folder, userId, "big.bin", content);
        }

        try (var node = new Node(new Node.Network(), dataFile)) {
            assertNotNull(node.sharedEntry(folder, userId, "big.bin"));
            assertArrayEquals(content, node.readSharedFile(folder, userId, "big.bin"));
        }
    }
}
