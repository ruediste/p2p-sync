package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.proto.Sync;

class ContactsTest {
    @TempDir
    Path tempDir;

    private Node.Network network;
    private Node nodeA;
    private Node nodeB;
    private NodeUserHandle handleA;

    @BeforeEach
    void before() {
        network = new Node.Network();
        nodeA = new Node(network);
        nodeB = new Node(network);
        handleA = nodeA.createNewUser();
    }

    @AfterEach
    void closeNodes() {
        network.nodes.values().stream().toList().forEach(Node::close);
    }

    /**
     * Verifies contacts support all optional text fields and that updates are
     * applied per field rather than replacing the whole contact.
     */
    @Test
    void contactsCanBeCreatedUpdatedSearchedAndDeleted() {
        var contact = handleA.createContact(null, "Ada", "Lovelace", "ada@example.org", "123", "notes");
        assertEquals("Ada Lovelace", contact.displayName());
        assertTrue(handleA.searchContacts("example.org").stream()
                .anyMatch(item -> item.contactId().equals(contact.contactId())));

        var updated = handleA.updateContact(contact.contactId(), null, "Ada", "Byron", "ada@example.org",
                "456", "updated");
        assertEquals("Ada Byron", updated.displayName());
        assertEquals("456", updated.phone());
        assertEquals("updated", updated.notes());

        long updateOperations = handleA.data.localOperationTree.values().stream()
                .filter(operation -> kind(operation) == Sync.UserOperationProto.Kind.CONTACT_UPDATE)
                .count();
        assertTrue(updateOperations >= 3, "expected one operation per changed field");

        handleA.deleteContact(contact.contactId());
        assertTrue(handleA.listContacts().isEmpty());
    }

    /**
     * Verifies a linked user can appear in at most one contact and that the owner
     * cannot be used as a contact user ID.
     */
    @Test
    void userIdsAreUniqueAndMustNotReferenceTheOwner() {
        var userId = UserId.fromPublicKey(Ed25519PrivateKey.generateKeyPair().publicKey());
        handleA.createContact(userId, "First", "", "", "", "");
        assertThrows(IllegalArgumentException.class,
                () -> handleA.createContact(userId, "Second", "", "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> handleA.createContact(handleA.data.userId(), "Self", "", "", "", ""));

        var unlinked = handleA.createContact(null, "Unlinked", "", "", "", "");
        assertThrows(IllegalArgumentException.class,
                () -> handleA.updateContact(unlinked.contactId(), userId, "Unlinked", "", "", "", ""));
    }

    /**
     * Verifies contacts replicate between nodes hosting the same user and survive
     * stable-log truncation through the user snapshot.
     */
    @Test
    void contactsReplicateAndSurviveTruncation() {
        var handleB = nodeB.join(handleA.data.userKey);
        var contact = handleA.createContact(null, "Grace", "Hopper", "grace@example.org", "", "");
        handleA.modify(root -> root.userStatusMessage = "after contact");

        handleB.syncFrom(nodeA.peerId);
        assertEquals(contact, handleB.listContacts().get(0));
        handleB.modify(root -> root.userStatusMessage = "from B");
        handleA.syncFrom(nodeB.peerId);

        assertTrue(handleA.truncateStableLog());
        assertTrue(handleA.data.operationTree.values().stream()
                .noneMatch(operation -> kind(operation) == Sync.UserOperationProto.Kind.CONTACT_CREATE));
        assertEquals("Grace Hopper", handleA.listContacts().get(0).displayName());
    }

    /**
     * Verifies that the mutable root view used by {@code modify} also applies
     * contact additions, edits and removals instead of silently discarding them.
     */
    @Test
    void modifyAppliesContactChanges() {
        var contact = handleA.createContact(null, "Ada", "Lovelace", "", "", "");
        String newContactId = java.util.HexFormat.of().formatHex(nodeA.nextEntityId(handleA.data));

        handleA.modify(root -> root.contacts = List.of(
                contact.withField(Contact.Field.FIRST_NAME, "Grace", null),
                new Contact(newContactId, null, "New", "", "", "", "")));

        assertEquals(List.of("Grace Lovelace", "New"),
                handleA.listContacts().stream().map(Contact::displayName).sorted().toList());

        handleA.modify(root -> root.contacts = List.of(contact.withField(Contact.Field.FIRST_NAME, "Grace", null)));
        assertEquals(1, handleA.listContacts().size());

        handleA.modify(root -> root.contacts = List.of());
        assertTrue(handleA.listContacts().isEmpty());
    }

    /**
     * Verifies replaying the same contact operations does not duplicate or
     * otherwise
     * change the materialized list.
     */
    @Test
    void contactReplayIsIdempotent() {
        var handleB = nodeB.join(handleA.data.userKey);
        handleA.createContact(null, "Idempotent", "", "", "", "");
        handleB.syncFrom(nodeA.peerId);
        handleB.syncFrom(nodeA.peerId);
        handleB.syncFrom(nodeA.peerId);
        assertEquals(1, handleB.listContacts().size());
        assertEquals("Idempotent", handleB.listContacts().get(0).displayName());
    }

    /**
     * Verifies concurrent assignments of the same user ID converge
     * deterministically
     * and leave exactly one linked contact.
     */
    @Test
    void concurrentDuplicateUserIdsConverge() {
        var handleB = nodeB.join(handleA.data.userKey);
        var first = handleA.createContact(null, "First", "", "", "", "");
        var second = handleA.createContact(null, "Second", "", "", "", "");
        handleB.syncFrom(nodeA.peerId);

        var userId = UserId.fromPublicKey(Ed25519PrivateKey.generateKeyPair().publicKey());
        handleA.updateContact(first.contactId(), userId, "First", "", "", "", "");
        handleB.updateContact(second.contactId(), userId, "Second", "", "", "", "");

        for (int round = 0; round < 3; round++) {
            handleA.syncFrom(nodeB.peerId);
            handleB.syncFrom(nodeA.peerId);
        }

        assertEquals(linkedContactIds(handleA), linkedContactIds(handleB));
        assertEquals(1, linkedContactIds(handleA).size());
        assertEquals(handleA.listContacts(), handleB.listContacts());
    }

    /**
     * Verifies contacts are persisted with the user snapshot across a node restart.
     */
    @Test
    void contactsSurviveNodeRestart() {
        Path dataFile = tempDir.resolve("node.dat");
        var node = new Node(network, dataFile);
        var handle = node.createNewUser();
        var contact = handle.createContact(null, "Katherine", "Johnson", "", "", "");
        node.close();

        var restoredNode = new Node(network, dataFile);
        try {
            var restored = restoredNode.getUser(handle.data.userId());
            assertEquals(contact, restored.listContacts().get(0));
        } finally {
            restoredNode.close();
        }
    }

    private static List<String> linkedContactIds(NodeUserHandle handle) {
        return handle.listContacts().stream().filter(contact -> contact.userId() != null)
                .map(Contact::contactId).toList();
    }

    private static Sync.UserOperationProto.Kind kind(Node.Operation operation) {
        try {
            return Sync.UserOperationProto.parseFrom(operation.payload).getKind();
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
