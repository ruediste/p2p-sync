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
import com.github.ruediste.p2psync.proto.Sync;

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

        assertEquals(handleA.data.syncUnitId(), handleB.data.syncUnitId());
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
     * Verifies an incoming operation with an invalid user signature is rejected
     * before staging.
     */
    @Test
    public void invalidUserOperationSignatureIsRejectedBeforeStaging() {
        var handleB = nodeB.join(handleA.data.userKey);
        handleA.modify(root -> root.userStatusMessage = "authenticated");
        var operation = handleA.data.localOperationTree.firstEntry().getValue();
        byte[] invalidSignature = operation.signature.clone();
        invalidSignature[0] ^= 1;

        assertThrows(IllegalArgumentException.class, () -> handleB.data.receive(List.of(
                new Node.Transfer(operation.id, operation.payload.length, 0, operation.payload,
                        invalidSignature, true)),
                nodeB));
        assertTrue(handleB.data.stagingTree.isEmpty());
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

    /**
     * Verifies SyncUnit type, signed membership history, roles, and participation
     * survive reopen/removal.
     */
    @Test
    public void syncUnitMembershipAndParticipationPersistAcrossReopen() {
        Path dataFile = tempDir.resolve("sync-units.db");
        var first = new Node(new Network(), dataFile);
        var owner = first.createNewUser("owner");
        var member = first.createNewUser("member");
        var channelId = first.createSyncUnit(owner.data.userId(), SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        first.inviteToSyncUnit(channelId, owner.data.userId(), member.data.userId(),
                SyncUnitAuthorization.Role.MANAGER);
        first.acceptSyncUnitInvitation(channelId, member.data.userId());
        String messageId = first.sendChannelMessage(channelId, member.data.userId(),
                Sync.SyncUnitMessage.newBuilder().setText("persisted message")
                        .addAttachments(Sync.SyncUnitAttachment.newBuilder()
                                .setName("note.txt").setMediaType("text/plain")
                                .setContent(com.google.protobuf.ByteString.copyFromUtf8("attachment")))
                        .build());
        var folderId = first.createSyncUnit(owner.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
        first.inviteToSyncUnit(folderId, owner.data.userId(), member.data.userId(),
                SyncUnitAuthorization.Role.EDITOR);
        first.acceptSyncUnitInvitation(folderId, member.data.userId());
        first.putSharedFile(folderId, owner.data.userId(), "notes/readme.txt", "stored file".getBytes());
        var todoId = first.createSyncUnit(owner.data.userId(), SyncUnitAuthorization.Type.TODO_LIST);
        first.putTodoItem(todoId, owner.data.userId(), "task-1", "finish sync".getBytes());
        first.close();

        var reopened = new Node(new Network(), dataFile);
        var authorization = reopened.getSyncUnitAuthorization(channelId);
        assertEquals(SyncUnitAuthorization.Role.MANAGER, authorization.members().get(owner.data.userId()));
        assertEquals(SyncUnitAuthorization.Role.MANAGER, authorization.members().get(member.data.userId()));
        assertTrue(reopened.users.get(owner.data.userId()).participatingSyncUnitIds.contains(channelId.toString()));
        assertTrue(reopened.users.get(member.data.userId()).participatingSyncUnitIds.contains(channelId.toString()));
        assertTrue(reopened.users.get(member.data.userId()).participatingSyncUnitIds.contains(folderId.toString()));
        assertArrayEquals("stored file".getBytes(),
                reopened.readSharedFile(folderId, member.data.userId(), "notes/readme.txt"));
        assertArrayEquals("finish sync".getBytes(), reopened.readTodoItem(todoId, owner.data.userId(), "task-1"));
        var messages = reopened.getChannelMessages(channelId, member.data.userId());
        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).messageId());
        assertEquals("persisted message", messages.get(0).message().getText());
        assertEquals("attachment", messages.get(0).message().getAttachments(0).getContent().toStringUtf8());

        reopened.removeFromSyncUnit(channelId, owner.data.userId(), member.data.userId());
        reopened.close();

        var reopenedAfterRemoval = new Node(new Network(), dataFile);
        assertFalse(
                reopenedAfterRemoval.getSyncUnitAuthorization(channelId).members().containsKey(member.data.userId()));
        assertFalse(reopenedAfterRemoval.users.get(member.data.userId()).participatingSyncUnitIds
                .contains(channelId.toString()));
        assertTrue(reopenedAfterRemoval.users.get(member.data.userId()).participatingSyncUnitIds
                .contains(folderId.toString()));
        reopenedAfterRemoval.close();
    }

    /**
     * Verifies invited SyncUnits replicate authorized content and revocation clears
     * the member's local copy.
     */
    @Test
    public void sharedUnitsSyncContentAndRemoveRevokedCopies() {
        var recipient = nodeB.createNewUser("recipient");
        var folderId = nodeA.createSyncUnit(handleA.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
        nodeA.putSharedFile(folderId, handleA.data.userId(), "readme.txt", "before invite".getBytes());
        nodeA.inviteToSyncUnit(folderId, handleA.data.userId(), recipient.data.userId(),
                SyncUnitAuthorization.Role.VIEWER);
        assertTrue(nodeB.syncUnits.containsKey(folderId));
        nodeB.joinSyncUnit(nodeA.peerId, folderId, recipient.data.userId());
        assertArrayEquals("before invite".getBytes(),
                nodeB.readSharedFile(folderId, recipient.data.userId(), "readme.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> nodeB.putSharedFile(folderId, recipient.data.userId(), "blocked.txt", new byte[0]));

        var channelId = nodeA.createSyncUnit(handleA.data.userId(), SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        nodeA.inviteToSyncUnit(channelId, handleA.data.userId(), recipient.data.userId(),
                SyncUnitAuthorization.Role.MEMBER);
        nodeB.joinSyncUnit(nodeA.peerId, channelId, recipient.data.userId());
        String messageId = nodeB.sendChannelMessage(channelId, recipient.data.userId(),
                Sync.SyncUnitMessage.newBuilder().setText("hello channel").build());
        nodeA.syncKnownPeers();
        assertEquals(messageId, nodeA.getChannelMessages(channelId, handleA.data.userId()).get(0).messageId());

        nodeA.changeSyncUnitRole(channelId, handleA.data.userId(), recipient.data.userId(),
                SyncUnitAuthorization.Role.MANAGER);
        nodeB.syncKnownPeers();
        assertEquals(SyncUnitAuthorization.Role.MANAGER,
                nodeB.getSyncUnitAuthorization(channelId).members().get(recipient.data.userId()));

        nodeA.removeFromSyncUnit(channelId, handleA.data.userId(), recipient.data.userId());
        assertFalse(nodeB.syncUnits.containsKey(channelId));
        assertFalse(recipient.data.participatingSyncUnitIds.contains(channelId.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> nodeB.syncSyncUnitFrom(nodeA.peerId, channelId));
    }

    /**
     * Verifies accepted-unit participation is replicated through the user's signed
     * history to another node.
     */
    @Test
    public void participationSelectionReplicatesAcrossUserNodes() {
        var recipient = nodeB.createNewUser("recipient");
        var recipientReplicaNode = new Node(network);
        var recipientReplica = recipientReplicaNode.join(recipient.data.userKey, "recipient-replica");
        var folderId = nodeA.createSyncUnit(handleA.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
        nodeA.putSharedFile(folderId, handleA.data.userId(), "shared.txt", "replicated participation".getBytes());
        nodeA.inviteToSyncUnit(folderId, handleA.data.userId(), recipient.data.userId(),
                SyncUnitAuthorization.Role.VIEWER);

        nodeB.joinSyncUnit(nodeA.peerId, folderId, recipient.data.userId());
        recipientReplica.syncFrom(nodeB.peerId);
        assertTrue(recipientReplica.data.participatingSyncUnitIds.contains(folderId.toString()));
        recipientReplica.getNode().syncKnownPeers();
        assertArrayEquals("replicated participation".getBytes(),
                recipientReplica.getNode().readSharedFile(folderId, recipient.data.userId(), "shared.txt"));
        recipientReplicaNode.close();
    }

    /**
     * Verifies unsent messages survive restart, retry when a recipient returns, and
     * deduplicate on delivery.
     */
    @Test
    public void directMessageRetriesAndDeduplicatesAcrossRestart() {
        Path senderFile = tempDir.resolve("sender.db");
        Path recipientFile = tempDir.resolve("recipient.db");
        var firstNetwork = new Network();
        var senderNode = new Node(firstNetwork, senderFile);
        var recipientNode = new Node(firstNetwork, recipientFile);
        var sender = senderNode.createNewUser("sender");
        var recipient = recipientNode.createNewUser("recipient");
        var recipientReplicaNode = new Node(firstNetwork);
        recipientReplicaNode.join(recipient.data.userKey, "recipient-replica");
        var recipientId = recipient.data.userId();
        firstNetwork.nodes.remove(recipientNode.peerId);
        firstNetwork.nodes.remove(recipientReplicaNode.peerId);
        sender.sendChatMessage(recipientId, "queued while offline", List.of(
                Sync.SyncUnitAttachment.newBuilder().setName("image.bin").setMediaType("application/octet-stream")
                        .setContent(com.google.protobuf.ByteString.copyFrom(new byte[] { 1, 2, 3 })).build()));
        assertEquals(1, senderNode.getOutboxMessages(sender.data.userId()).size());
        senderNode.close();
        recipientNode.close();
        recipientReplicaNode.close();

        var resumedNetwork = new Network();
        var resumedSenderNode = new Node(resumedNetwork, senderFile);
        var resumedSender = resumedSenderNode.getUserByName("sender");
        assertEquals(1, resumedSenderNode.getOutboxMessages(resumedSender.data.userId()).size());
        resumedSenderNode.syncKnownPeers();
        var resumedRecipientNode = new Node(resumedNetwork, recipientFile);
        var resumedRecipientReplicaNode = new Node(resumedNetwork);
        var resumedRecipientReplica = resumedRecipientReplicaNode.join(
                resumedRecipientNode.users.get(recipientId).userKey, "recipient-replica");
        resumedSenderNode.syncKnownPeers();
        resumedRecipientReplica.syncFrom(resumedRecipientNode.peerId);

        assertTrue(resumedSenderNode.getOutboxMessages(resumedSender.data.userId()).isEmpty());
        var inbox = resumedRecipientNode.getInboxMessages(recipientId);
        assertEquals(1, inbox.size());
        assertEquals("queued while offline", inbox.get(0).getBody());
        assertEquals(3, inbox.get(0).getAttachments(0).getContent().size());
        assertEquals(1, resumedRecipientReplicaNode.getInboxMessages(recipientId).size());
        assertTrue(resumedRecipientReplica.data.allOperations().values().stream().anyMatch(operation -> {
            try {
                return Sync.UserOperation.parseFrom(operation.payload)
                        .getKind() == Sync.UserOperation.Kind.RECEIVE_MESSAGE;
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalStateException(e);
            }
        }));
        resumedSenderNode.syncKnownPeers();
        assertEquals(1, resumedRecipientNode.getInboxMessages(recipientId).size());

        String mailId = resumedSender.sendMailMessage(recipientId, "subject", "mail body", List.of());
        var channelId = resumedSenderNode.createSyncUnit(resumedSender.data.userId(),
                SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        resumedSenderNode.inviteToSyncUnit(channelId, resumedSender.data.userId(), recipientId,
                SyncUnitAuthorization.Role.MEMBER);
        String invitationId = resumedSender.sendMessage(recipientId,
                Sync.UserMessage.Type.MESSAGE_CHANNEL_INVITATION, "channel invite", "join channel", List.of(),
                channelId);
        var receivedMessages = resumedRecipientNode.getInboxMessages(recipientId);
        assertEquals(3, receivedMessages.size());
        var mail = receivedMessages.stream().filter(message -> message.getMessageId().equals(mailId)).findFirst()
                .orElseThrow();
        assertEquals(Sync.UserMessage.Type.MAIL, mail.getType());
        assertEquals("subject", mail.getSubject());
        var invitation = receivedMessages.stream()
                .filter(message -> message.getMessageId().equals(invitationId)).findFirst().orElseThrow();
        assertEquals(channelId.bytes().length, invitation.getInvitedSyncUnitId().size());
        resumedSenderNode.close();
        resumedRecipientNode.close();
        resumedRecipientReplicaNode.close();
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
            var recipient = remoteB.createNewUser("recipient");
            remoteA.addRemoteAddress(remoteB.peerId, addressesB.get(0));

            String messageId = userA.sendChatMessage(recipient.data.userId(), "delivered remotely", List.of(
                    Sync.SyncUnitAttachment.newBuilder().setName("remote.txt").setMediaType("text/plain")
                            .setContent(com.google.protobuf.ByteString.copyFromUtf8("remote attachment")).build()));
            assertTrue(remoteA.getOutboxMessages(userA.data.userId()).isEmpty());
            assertEquals(messageId, remoteB.getInboxMessages(recipient.data.userId()).get(0).getMessageId());
            assertEquals("remote attachment", remoteB.getInboxMessages(recipient.data.userId()).get(0)
                    .getAttachments(0).getContent().toStringUtf8());
            remoteA.deliverPendingMessages();
            assertEquals(1, remoteB.getInboxMessages(recipient.data.userId()).size());

            var folderOwner = remoteA.createNewUser("folder-owner");
            var folderId = remoteA.createSyncUnit(folderOwner.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            remoteA.putSharedFile(folderId, folderOwner.data.userId(), "shared.txt", "from owner".getBytes());
            remoteA.inviteToSyncUnit(folderId, folderOwner.data.userId(), recipient.data.userId(),
                    SyncUnitAuthorization.Role.EDITOR);
            var folderOwnerHandle = remoteA.getUserByName("folder-owner");
            folderOwnerHandle.sendMessage(recipient.data.userId(),
                    Sync.UserMessage.Type.SHARED_FOLDER_INVITATION, "folder invite", "open shared folder", List.of(),
                    folderId);
            assertTrue(remoteB.getInboxMessages(recipient.data.userId()).stream()
                    .anyMatch(message -> message.getInvitedSyncUnitId()
                            .equals(com.google.protobuf.ByteString.copyFrom(folderId.bytes()))));
            remoteB.joinSyncUnit(remoteA.peerId, folderId, recipient.data.userId());
            assertArrayEquals("from owner".getBytes(),
                    remoteB.readSharedFile(folderId, recipient.data.userId(), "shared.txt"));
            remoteB.putSharedFile(folderId, recipient.data.userId(), "reply.txt", "from editor".getBytes());
            remoteA.syncKnownPeers();
            assertArrayEquals("from editor".getBytes(),
                    remoteA.readSharedFile(folderId, folderOwner.data.userId(), "reply.txt"));
            remoteA.removeFromSyncUnit(folderId, folderOwner.data.userId(), recipient.data.userId());
            assertFalse(remoteA.getSyncUnitAuthorization(folderId).members().containsKey(recipient.data.userId()));
            remoteB.syncKnownPeers();
            assertFalse(remoteB.syncUnits.containsKey(folderId));

            userA.modify(root -> root.userStatusMessage = "sent remotely");
            remoteB.syncKnownPeers();
            assertEquals("sent remotely", userB.read(root -> root.userStatusMessage));

            userB.modify(root -> root.userStatusMessage = "reply remotely");
            remoteA.syncKnownPeers();
            assertEquals("reply remotely", userA.read(root -> root.userStatusMessage));
        } finally {
            remoteA.close();
            remoteB.close();
        }
    }
}
