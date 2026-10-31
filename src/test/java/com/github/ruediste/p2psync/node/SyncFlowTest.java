package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.node.Node.Network;

public class SyncFlowTest {
    @TempDir
    Path tempDir;

    private Network network;
    private Node nodeA;
    private Node nodeB;
    private NodeUserHandle handleA;

    @BeforeEach
    public void before() {
        network = new Network();
        nodeA = new Node(network);
        nodeB = new Node(network);
        handleA = nodeA.createNewUser();
    }

    @AfterEach
    public void closeTemporaryNodes() {
        network.nodes.values().stream().toList().forEach(Node::close);
    }

    /**
     * Verifies that local operations are replicated and the total order converges.
     */
    @Test
    public void sync_no_conflict() {
        var handleB = nodeB.join(handleA.data.userKey);

        handleA.modify(root -> root.userStatusMessage = "foo");
        handleB.syncFrom(nodeA.peerId);
        assertEquals("foo", handleB.read(r -> r.userStatusMessage));

        handleB.modify(root -> root.userStatusMessage = "bar");
        handleA.syncFrom(nodeB.peerId);
        assertEquals("bar", handleA.read(r -> r.userStatusMessage));

    }

    /**
     * Verifies joining retrieves existing operations before local writes are
     * accepted.
     */
    @Test
    public void joinRetrievesHistoryBeforeLocalModification() {
        handleA.modify(root -> root.userStatusMessage = "before join");
        var handleB = nodeB.join(handleA.data.userKey);

        assertEquals("before join", handleB.read(root -> root.userStatusMessage));
        handleB.modify(root -> root.userStatusMessage = "after join");
        handleA.syncFrom(nodeB.peerId);
        assertEquals("after join", handleA.read(root -> root.userStatusMessage));
    }

    /**
     * Verifies that a truncated operation is resumed before it enters the main
     * history.
     */
    @Test
    public void truncatedOperationResumesOnNextSync() {
        var handleB = nodeB.join(handleA.data.userKey);
        handleA.modify(root -> root.userStatusMessage = "x".repeat(128));

        handleB.syncFrom(nodeA.peerId, 10, 24);
        assertTrue(handleB.data.operationTree.isEmpty());
        assertEquals(24, handleB.data.retrievalTree.values().iterator().next().receivedLength);

        handleB.syncFrom(nodeA.peerId, 10, 512);
        assertTrue(handleB.data.retrievalTree.isEmpty());
        assertEquals("x".repeat(128), handleB.read(root -> root.userStatusMessage));
        assertEquals(handleA.data.localOperationTree.keySet(), handleB.data.operationTree.keySet());
    }

    /**
     * Verifies concurrent root updates converge using the clock and node-hash
     * order.
     */
    @Test
    public void concurrentUpdatesConvergeDeterministically() {
        var handleB = nodeB.join(handleA.data.userKey);
        handleA.modify(root -> root.userStatusMessage = "from A");
        handleB.modify(root -> root.userStatusMessage = "from B");

        handleA.syncFrom(nodeB.peerId);
        handleB.syncFrom(nodeA.peerId);

        String statusA = handleA.read(root -> root.userStatusMessage);
        String statusB = handleB.read(root -> root.userStatusMessage);
        assertEquals(statusA, statusB);
        assertEquals(handleA.data.operationTree.keySet(), handleB.data.operationTree.keySet());
    }

    /** Verifies the two-phase leave process converges among the remaining peers. */
    @Test
    public void leavingNodeIsRemovedAfterActivePeersConfirm() {
        var handleB = nodeB.join(handleA.data.userKey);
        var nodeC = new Node(network);
        var handleC = nodeC.join(handleA.data.userKey);
        handleB.syncFrom(nodeA.peerId);
        handleC.syncFrom(nodeA.peerId);

        handleA.startLeaving(nodeB.peerId);
        for (int round = 0; round < 12; round++) {
            handleA.syncFrom(nodeC.peerId);
            handleC.syncFrom(nodeA.peerId);
        }

        assertTrue(!handleA.data.activeNodeSet.nodes.contains(nodeB.nodeKey()));
        assertTrue(!handleC.data.activeNodeSet.nodes.contains(nodeB.nodeKey()));
        assertEquals(ActiveNodeSet.Phase.I, handleA.data.activeNodeSet.leavingProcess.phase);
        assertEquals(ActiveNodeSet.Phase.I, handleC.data.activeNodeSet.leavingProcess.phase);
        nodeC.close();
    }

    /**
     * Verifies local names are unique and status summaries do not expose
     * credentials.
     */
    @Test
    public void nodeManagementUsesLocalNamesAndRedactedStatus() {
        var alice = nodeA.createNewUser("alice");
        assertThrows(IllegalArgumentException.class, () -> nodeA.createNewUser("alice"));
        nodeA.renameUser(alice.data.userId(), "personal");
        assertEquals(alice.data.userId(), nodeA.getUserByName("personal").data.userId());
        var status = nodeA.listUsers().get(0);
        assertEquals("personal", status.localName());
        assertEquals(1, status.activeNodeCount());
        assertEquals(0, status.pendingLocalOperationCount());
        Path credentialFile = tempDir.resolve("alice.userkey");
        nodeA.exportUserKey(alice.data.userId(), credentialFile, "portable-password".toCharArray());
        assertEquals(alice.data.userKey,
                Node.importUserKey(credentialFile, "portable-password".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> Node.importUserKey(credentialFile, "wrong-password".toCharArray()));
        String clipboardCredential = nodeA.exportUserKeyToClipboard(
                alice.data.userId(), "clipboard-password".toCharArray());
        assertTrue(clipboardCredential.startsWith("p2p-sync-user-key:v1:"));
        assertEquals(alice.data.userKey, Node.importUserKeyFromClipboard(
                clipboardCredential, "clipboard-password".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> Node.importUserKeyFromClipboard(
                clipboardCredential, "wrong-password".toCharArray()));

        Path emptyPhraseFile = tempDir.resolve("alice-empty.userkey");
        nodeA.exportUserKey(alice.data.userId(), emptyPhraseFile, new char[0]);
        assertEquals(alice.data.userKey, Node.importUserKey(emptyPhraseFile, new char[0]));
        String emptyPhraseClipboard = nodeA.exportUserKeyToClipboard(alice.data.userId(), new char[0]);
        assertEquals(alice.data.userKey, Node.importUserKeyFromClipboard(emptyPhraseClipboard, new char[0]));
    }

    /**
     * Verifies concurrent file operations on separate paths merge without data
     * loss.
     */
    @Test
    public void concurrentFileOperationsMergeAndReplicateContent() {
        var handleB = nodeB.join(handleA.data.userKey);
        handleA.createDirectory("/docs");
        handleA.createFile("/docs/alpha.txt");
        handleA.writeFile("/docs/alpha.txt", 0, "alpha".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        handleB.createFile("/beta.txt");
        handleB.writeFile("/beta.txt", 0, "beta".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        handleA.syncFrom(nodeB.peerId);
        handleB.syncFrom(nodeA.peerId);

        assertEquals(List.of("beta.txt", "docs"), handleA.listDirectory("/").stream()
                .map(NodeUserHandle.DirectoryEntry::name).sorted().toList());
        assertEquals("alpha", new String(handleA.readFile("/docs/alpha.txt"), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("beta", new String(handleB.readFile("/beta.txt"), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(handleA.data.operationTree.keySet(), handleB.data.operationTree.keySet());
    }

    /** Verifies user-tree rename, move, truncate, and delete operations. */
    @Test
    public void fileTreeOperationsUpdatePathsAndContent() {
        handleA.createDirectory("/docs");
        handleA.createDirectory("/archive");
        handleA.createFile("/docs/note.txt");
        handleA.writeFile("/docs/note.txt", 0, "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        handleA.move("/docs/note.txt", "/archive/note.txt");
        handleA.rename("/archive/note.txt", "renamed.txt");
        handleA.truncateFile("/archive/renamed.txt", 5);

        assertEquals("hello", new String(handleA.readFile("/archive/renamed.txt"),
                java.nio.charset.StandardCharsets.UTF_8));
        handleA.delete("/archive/renamed.txt");
        assertTrue(!handleA.exists("/archive/renamed.txt"));
        assertThrows(IllegalArgumentException.class, () -> handleA.delete("/"));
    }

    /**
     * Verifies user names, credentials, histories, and files survive reopening a
     * node.
     */
    @Test
    public void persistentNodeReopensWithUserHistoryAndFiles() {
        Path dataFile = tempDir.resolve("node.db");
        var first = new Node(new Network(), dataFile);
        var handle = first.createNewUser("archive");
        var userId = handle.data.userId();
        var peerId = first.peerId;
        handle.createDirectory("/docs");
        handle.createFile("/docs/readme.txt");
        handle.writeFile("/docs/readme.txt", 0, "durable".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] largeContent = new byte[96_000];
        for (int index = 0; index < largeContent.length; index++)
            largeContent[index] = (byte) (index * 17);
        handle.createFile("/docs/large.bin");
        handle.writeFile("/docs/large.bin", 0, largeContent);
        first.close();
        assertTrue(Files.exists(dataFile));
        assertFalse(Files.exists(tempDir.resolve("node/node.db")));
        assertFalse(Files.exists(tempDir.resolve("node-state.pb")));
        assertFalse(Files.exists(tempDir.resolve("blocks")));

        var reopened = new Node(new Network(), dataFile);
        assertEquals(peerId, reopened.peerId);
        assertEquals(userId, reopened.listUsers().get(0).userId());
        var restored = reopened.getUserByName("archive");
        assertEquals("durable", new String(restored.readFile("/docs/readme.txt"),
                java.nio.charset.StandardCharsets.UTF_8));
        assertArrayEquals(largeContent, restored.readFile("/docs/large.bin"));
        assertEquals(handle.data.operationTree.size() + handle.data.localOperationTree.size(),
                restored.data.operationTree.size() + restored.data.localOperationTree.size());
        reopened.close();
    }

    /** Verifies stable-log pruning retains a snapshot baseline for later joins. */
    @Test
    public void stableLogTruncationSupportsLaterJoin() {
        handleA.createFile("/one.txt");
        handleA.writeFile("/one.txt", 0, "first".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var handleB = nodeB.join(handleA.data.userKey);
        handleB.createFile("/two.txt");
        handleB.writeFile("/two.txt", 0, "second".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        handleA.syncFrom(nodeB.peerId);
        handleB.syncFrom(nodeA.peerId);

        assertTrue(handleA.truncateStableLog());
        assertTrue(handleB.truncateStableLog());
        assertTrue(handleA.data.operationTree.size() < 4);

        var nodeC = new Node(network);
        var handleC = nodeC.join(handleA.data.userKey, "joined");
        assertEquals("first", new String(handleC.readFile("/one.txt"),
                java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("second", new String(handleC.readFile("/two.txt"),
                java.nio.charset.StandardCharsets.UTF_8));
        nodeC.close();
    }

    /** Verifies remote join and operation transfer over TCP, Noise, and Yamux. */
    @Test
    public void remoteNodesJoinAndSyncOverLibp2p() {
        var remoteA = new Node(new Network(), tempDir.resolve("remote-a"));
        var remoteB = new Node(new Network(), tempDir.resolve("remote-b"));
        try {
            var userA = remoteA.createNewUser("shared");
            List<Multiaddr> addressesA = remoteA.startRemote("/ip4/127.0.0.1/tcp/0", false);
            List<Multiaddr> addressesB = remoteB.startRemote("/ip4/127.0.0.1/tcp/0", false);
            remoteB.addRemoteAddress(remoteA.peerId, addressesA.get(0));
            var userB = remoteB.joinRemote(userA.data.userKey, "copy");

            userA.modify(root -> root.userStatusMessage = "sent remotely");
            remoteB.syncKnownPeers();
            assertEquals("sent remotely", userB.read(root -> root.userStatusMessage));

            userB.modify(root -> root.userStatusMessage = "reply remotely");
            remoteA.addRemoteAddress(remoteB.peerId, addressesB.get(0));
            remoteA.syncKnownPeers();
            assertEquals("reply remotely", userA.read(root -> root.userStatusMessage));
        } finally {
            remoteA.close();
            remoteB.close();
        }
    }
}
