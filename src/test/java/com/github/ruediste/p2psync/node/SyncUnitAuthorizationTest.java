package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;

class SyncUnitAuthorizationTest {
    private final PrivKey creatorKey = Ed25519PrivateKey.generateKeyPair();
    private final PrivKey viewerKey = Ed25519PrivateKey.generateKeyPair();
    private final PrivKey editorKey = Ed25519PrivateKey.generateKeyPair();
    private final PrivKey managerKey = Ed25519PrivateKey.generateKeyPair();
    private final PrivKey strangerKey = Ed25519PrivateKey.generateKeyPair();
    private final PubKey creator = creatorKey.publicKey();
    private final PubKey viewer = viewerKey.publicKey();
    private final PubKey editor = editorKey.publicKey();
    private final PubKey manager = managerKey.publicKey();
    private final PubKey stranger = strangerKey.publicKey();
    private final Map<PubKey, PrivKey> privateKeys = Map.of(
            creator, creatorKey,
            viewer, viewerKey,
            editor, editorKey,
            manager, managerKey,
            stranger, strangerKey);
    private final SyncUnitId userUnitId = SyncUnitId.forUser(creator);
    private final SyncUnitId sharedUnitId = SyncUnitId.create();

    /**
     * Verifies shared-folder and todo-list roles grant only their documented
     * actions.
     */
    @Test
    void sharedUnitRolesLimitActions() {
        var members = Map.of(
                viewer, SyncUnitAuthorization.Role.VIEWER,
                editor, SyncUnitAuthorization.Role.EDITOR,
                manager, SyncUnitAuthorization.Role.MANAGER);

        for (var type : new SyncUnitAuthorization.Type[] {
                SyncUnitAuthorization.Type.SHARED_FOLDER,
                SyncUnitAuthorization.Type.TODO_LIST }) {
            assertTrue(authorized(type, creator, members, viewer, SyncUnitAuthorization.Action.VIEW));
            assertFalse(authorized(type, creator, members, viewer, SyncUnitAuthorization.Action.MODIFY));
            assertTrue(authorized(type, creator, members, editor, SyncUnitAuthorization.Action.MODIFY));
            assertFalse(authorized(type, creator, members, editor,
                    SyncUnitAuthorization.Action.MANAGE_MEMBERS));
            assertTrue(authorized(type, creator, members, manager,
                    SyncUnitAuthorization.Action.MANAGE_MEMBERS));
            assertFalse(authorized(type, creator, members, stranger, SyncUnitAuthorization.Action.VIEW));
        }
    }

    /**
     * Verifies User SyncUnit writes require the user's identity and channels
     * require membership.
     */
    @Test
    void userAndChannelAuthorizationUseTheirOwnRules() {
        assertEquals(userUnitId, SyncUnitId.fromBytes(userUnitId.bytes()));
        assertEquals(userUnitId, SyncUnitId.forUser(creator));
        assertFalse(userUnitId.equals(sharedUnitId));

        assertTrue(SyncUnitAuthorization.isAuthorized(SyncUnitAuthorization.Type.USER, creator, Map.of(),
                creator, SyncUnitAuthorization.Action.MODIFY));
        assertFalse(SyncUnitAuthorization.isAuthorized(SyncUnitAuthorization.Type.USER, creator, Map.of(),
                editor, SyncUnitAuthorization.Action.MODIFY));

        var members = Map.of(creator, SyncUnitAuthorization.Role.MANAGER,
                editor, SyncUnitAuthorization.Role.MEMBER,
                manager, SyncUnitAuthorization.Role.MANAGER);
        assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, editor,
                SyncUnitAuthorization.Action.SEND_MESSAGE));
        assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, manager,
                SyncUnitAuthorization.Action.SEND_MESSAGE));
        assertFalse(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, editor,
                SyncUnitAuthorization.Action.MODIFY));
        assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, creator,
                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
        assertFalse(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, editor,
                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
        assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creator, members, manager,
                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
    }

    /**
     * Verifies channel managers can appoint, change, and remove other managers and
     * members.
     */
    @Test
    void channelManagersManageMembershipAndCannotRemoveTheLastManager() {
        var inviteMember = operation(1, creator, SyncUnitAuthorization.Action.INVITE, editor,
                SyncUnitAuthorization.Role.MEMBER);
        var acceptMember = operation(2, editor, SyncUnitAuthorization.Action.ACCEPT_INVITATION, editor, null);
        var inviteManager = operation(3, creator, SyncUnitAuthorization.Action.INVITE, manager,
                SyncUnitAuthorization.Role.MANAGER);
        var acceptManager = operation(4, manager, SyncUnitAuthorization.Action.ACCEPT_INVITATION, manager, null);
        var managerRemovesOtherManager = operation(5, manager, SyncUnitAuthorization.Action.REMOVE_MEMBER,
                creator, null);
        var removedManagerTriesToSend = operation(6, creator, SyncUnitAuthorization.Action.SEND_MESSAGE, null, null);
        var memberTriesToInvite = operation(7, editor, SyncUnitAuthorization.Action.INVITE, stranger,
                SyncUnitAuthorization.Role.MANAGER);
        var lastManagerTriesToLeave = operation(8, manager, SyncUnitAuthorization.Action.REMOVE_MEMBER,
                manager, null);

        var state = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.MESSAGE_CHANNEL,
                creator, List.of(inviteMember, acceptMember, inviteManager, acceptManager,
                        managerRemovesOtherManager, removedManagerTriesToSend, memberTriesToInvite,
                        lastManagerTriesToLeave));

        assertEquals(Map.of(editor, SyncUnitAuthorization.Role.MEMBER,
                manager, SyncUnitAuthorization.Role.MANAGER), state.members());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), state.acceptedOperations().stream()
                .map(SyncUnitAuthorization.Operation::lamportClock).toList());
        assertEquals(List.of(6L, 7L, 8L), state.rejectedOperations().stream()
                .map(SyncUnitAuthorization.Operation::lamportClock).toList());
    }

    /**
     * Verifies unauthorized operations fail with an explicit authorization error.
     */
    @Test
    void requireAuthorizedRejectsUnauthorizedAction() {
        assertThrows(IllegalArgumentException.class, () -> SyncUnitAuthorization.requireAuthorized(
                SyncUnitAuthorization.Type.SHARED_FOLDER, creator,
                Map.of(viewer, SyncUnitAuthorization.Role.VIEWER), viewer,
                SyncUnitAuthorization.Action.MODIFY));
    }

    /**
     * Verifies invitations, acceptance, role changes, and removal replay by
     * operation order.
     */
    @Test
    void membershipOperationsReplayDeterministicallyWhenReceivedOutOfOrder() {
        var invite = operation(1, creator, SyncUnitAuthorization.Action.INVITE, editor,
                SyncUnitAuthorization.Role.EDITOR);
        var accept = operation(2, editor, SyncUnitAuthorization.Action.ACCEPT_INVITATION, editor, null);
        var modifyBeforeRoleChange = operation(3, editor, SyncUnitAuthorization.Action.MODIFY, null, null);
        var unauthorizedRoleChange = operation(4, editor, SyncUnitAuthorization.Action.CHANGE_ROLE, editor,
                SyncUnitAuthorization.Role.MANAGER);
        var demote = operation(5, creator, SyncUnitAuthorization.Action.CHANGE_ROLE, editor,
                SyncUnitAuthorization.Role.VIEWER);
        var modifyAfterDemotion = operation(6, editor, SyncUnitAuthorization.Action.MODIFY, null, null);
        var remove = operation(7, creator, SyncUnitAuthorization.Action.REMOVE_MEMBER, editor, null);
        var viewAfterRemoval = operation(8, editor, SyncUnitAuthorization.Action.VIEW, null, null);
        var foreignUnitInvite = SyncUnitAuthorization.Operation.sign(SyncUnitId.create(), 0, new byte[32],
                creatorKey, SyncUnitAuthorization.Action.INVITE, stranger, SyncUnitAuthorization.Role.EDITOR);

        var inOrder = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.SHARED_FOLDER, creator,
                List.of(invite, accept, modifyBeforeRoleChange, unauthorizedRoleChange, demote,
                        modifyAfterDemotion, remove, viewAfterRemoval, foreignUnitInvite));
        var outOfOrder = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.SHARED_FOLDER, creator,
                List.of(viewAfterRemoval, remove, modifyAfterDemotion, demote, unauthorizedRoleChange,
                        modifyBeforeRoleChange, accept, invite));

        assertEquals(inOrder.members(), outOfOrder.members());
        assertEquals(inOrder.pendingInvitations(), outOfOrder.pendingInvitations());
        assertEquals(List.of(1L, 2L, 3L, 5L, 7L), inOrder.acceptedOperations().stream()
                .map(SyncUnitAuthorization.Operation::lamportClock).toList());
        assertEquals(List.of(4L, 6L, 8L), inOrder.rejectedOperations().stream()
                .map(SyncUnitAuthorization.Operation::lamportClock).toList());
        assertEquals(Map.of(creator, SyncUnitAuthorization.Role.MANAGER), outOfOrder.members());
    }

    private SyncUnitAuthorization.Operation operation(long clock, PubKey author,
            SyncUnitAuthorization.Action action, PubKey subject, SyncUnitAuthorization.Role role) {
        return SyncUnitAuthorization.Operation.sign(sharedUnitId, clock, new byte[32], privateKeys.get(author),
                action, subject, role);
    }

    private static boolean authorized(SyncUnitAuthorization.Type type, PubKey creator,
            Map<PubKey, SyncUnitAuthorization.Role> members, PubKey author,
            SyncUnitAuthorization.Action action) {
        return SyncUnitAuthorization.isAuthorized(type, creator, members, author, action);
    }
}