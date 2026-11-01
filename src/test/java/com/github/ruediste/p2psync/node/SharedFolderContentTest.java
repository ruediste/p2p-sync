package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SharedFolderContentTest {
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
            assertArrayEquals("shared todo".getBytes(), node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));
            assertEquals(1, node.listSharedFileInfos(folder, handle.data.userId()).size());
            node.deleteSharedFile(folder, handle.data.userId(), "notes/todo.txt");
            assertEquals(0, node.listSharedFileInfos(folder, handle.data.userId()).size());
            assertNull(node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));
        }
    }
}
