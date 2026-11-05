package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.identity.UserId;
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
        private final UserId creatorId = UserId.fromPublicKey(creator);
        private final PubKey viewer = viewerKey.publicKey();
        private final UserId viewerId = UserId.fromPublicKey(viewer);
        private final PubKey editor = editorKey.publicKey();
        private final UserId editorId = UserId.fromPublicKey(editor);
        private final PubKey manager = managerKey.publicKey();
        private final UserId managerId = UserId.fromPublicKey(manager);
        private final PubKey stranger = strangerKey.publicKey();
        private final UserId strangerId = UserId.fromPublicKey(stranger);
        private final Map<PubKey, PrivKey> privateKeys = Map.of(creator, creatorKey, viewer, viewerKey,
                        editor, editorKey, manager, managerKey, stranger, strangerKey);
        private final SyncUnitId userUnitId = SyncUnitId.forUser(creatorId);
        private final SyncUnitId sharedUnitId = SyncUnitId.create();

        /**
         * Verifies shared-folder and todo-list roles grant only their documented
         * actions.
         */
        @Test
        void sharedUnitRolesLimitActions() {
                var members = Map.of(
                                viewerId, SyncUnitAuthorization.Role.VIEWER,
                                editorId, SyncUnitAuthorization.Role.EDITOR,
                                managerId, SyncUnitAuthorization.Role.MANAGER);

                for (var type : new SyncUnitAuthorization.Type[] {
                                SyncUnitAuthorization.Type.SHARED_FOLDER,
                                SyncUnitAuthorization.Type.TODO_LIST }) {
                        assertTrue(authorized(type, creatorId, members, viewer, SyncUnitAuthorization.Action.VIEW));
                        assertFalse(authorized(type, creatorId, members, viewer, SyncUnitAuthorization.Action.MODIFY));
                        assertTrue(authorized(type, creatorId, members, editor, SyncUnitAuthorization.Action.MODIFY));
                        assertFalse(authorized(type, creatorId, members, editor,
                                        SyncUnitAuthorization.Action.MANAGE_MEMBERS));
                        assertTrue(authorized(type, creatorId, members, manager,
                                        SyncUnitAuthorization.Action.MANAGE_MEMBERS));
                        assertFalse(authorized(type, creatorId, members, stranger, SyncUnitAuthorization.Action.VIEW));
                }
        }

        /**
         * Verifies User SyncUnit writes require the user's identity and channels
         * require membership.
         */
        @Test
        void userAndChannelAuthorizationUseTheirOwnRules() {
                assertEquals(userUnitId, SyncUnitId.fromBytes(userUnitId.bytes()));
                assertEquals(userUnitId, SyncUnitId.forUser(creatorId));
                assertFalse(userUnitId.equals(sharedUnitId));

                assertTrue(SyncUnitAuthorization.isAuthorized(SyncUnitAuthorization.Type.USER, creatorId, Map.of(),
                                creator, SyncUnitAuthorization.Action.MODIFY));
                assertFalse(SyncUnitAuthorization.isAuthorized(SyncUnitAuthorization.Type.USER, creatorId, Map.of(),
                                editor, SyncUnitAuthorization.Action.MODIFY));

                var members = Map.of(creatorId, SyncUnitAuthorization.Role.MANAGER,
                                editorId, SyncUnitAuthorization.Role.MEMBER,
                                managerId, SyncUnitAuthorization.Role.MANAGER);
                assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, editor,
                                SyncUnitAuthorization.Action.SEND_MESSAGE));
                assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, manager,
                                SyncUnitAuthorization.Action.SEND_MESSAGE));
                assertFalse(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, editor,
                                SyncUnitAuthorization.Action.MODIFY));
                assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, creator,
                                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
                assertFalse(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, editor,
                                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
                assertTrue(authorized(SyncUnitAuthorization.Type.MESSAGE_CHANNEL, creatorId, members, manager,
                                SyncUnitAuthorization.Action.MANAGE_MEMBERS));
        }

        /**
         * Verifies channel managers can appoint, change, and remove other managers and
         * members.
         */
        @Test
        void channelManagersManageMembershipAndCannotRemoveTheLastManager() {
                var inviteMember = operation(1, creator, SyncUnitAuthorization.Action.INVITE, editorId,
                                SyncUnitAuthorization.Role.MEMBER);
                var acceptMember = operation(2, editor, SyncUnitAuthorization.Action.ACCEPT_INVITATION, editorId, null);
                var inviteManager = operation(3, creator, SyncUnitAuthorization.Action.INVITE, managerId,
                                SyncUnitAuthorization.Role.MANAGER);
                var acceptManager = operation(4, manager, SyncUnitAuthorization.Action.ACCEPT_INVITATION, managerId,
                                null);
                var managerRemovesOtherManager = operation(5, manager, SyncUnitAuthorization.Action.REMOVE_MEMBER,
                                creatorId, null);
                var removedManagerTriesToSend = operation(6, creator, SyncUnitAuthorization.Action.SEND_MESSAGE, null,
                                null);
                var memberTriesToInvite = operation(7, editor, SyncUnitAuthorization.Action.INVITE, strangerId,
                                SyncUnitAuthorization.Role.MANAGER);
                var lastManagerTriesToLeave = operation(8, manager, SyncUnitAuthorization.Action.REMOVE_MEMBER,
                                managerId, null);

                var state = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.MESSAGE_CHANNEL,
                                creatorId, List.of(inviteMember, acceptMember, inviteManager, acceptManager,
                                                managerRemovesOtherManager, removedManagerTriesToSend,
                                                memberTriesToInvite,
                                                lastManagerTriesToLeave));

                assertEquals(Map.of(editorId, SyncUnitAuthorization.Role.MEMBER,
                                managerId, SyncUnitAuthorization.Role.MANAGER), state.members());
                assertEquals(5, state.acceptedOperationCount());
                assertEquals(3, state.rejectedOperationCount());
        }

        /**
         * Verifies unauthorized operations fail with an explicit authorization error.
         */
        @Test
        void requireAuthorizedRejectsUnauthorizedAction() {
                assertThrows(IllegalArgumentException.class, () -> SyncUnitAuthorization.requireAuthorized(
                                SyncUnitAuthorization.Type.SHARED_FOLDER, creatorId,
                                Map.of(viewerId, SyncUnitAuthorization.Role.VIEWER), viewer,
                                SyncUnitAuthorization.Action.MODIFY));
        }

        /**
         * Verifies invitations, acceptance, role changes, and removal replay by
         * operation order.
         */
        @Test
        void membershipOperationsReplayDeterministicallyWhenReceivedOutOfOrder() {
                var invite = operation(1, creator, SyncUnitAuthorization.Action.INVITE, editorId,
                                SyncUnitAuthorization.Role.EDITOR);
                var accept = operation(2, editor, SyncUnitAuthorization.Action.ACCEPT_INVITATION, editorId, null);
                var modifyBeforeRoleChange = operation(3, editor, SyncUnitAuthorization.Action.MODIFY, null, null);
                var unauthorizedRoleChange = operation(4, editor, SyncUnitAuthorization.Action.CHANGE_ROLE, editorId,
                                SyncUnitAuthorization.Role.MANAGER);
                var demote = operation(5, creator, SyncUnitAuthorization.Action.CHANGE_ROLE, editorId,
                                SyncUnitAuthorization.Role.VIEWER);
                var modifyAfterDemotion = operation(6, editor, SyncUnitAuthorization.Action.MODIFY, null, null);
                var remove = operation(7, creator, SyncUnitAuthorization.Action.REMOVE_MEMBER, editorId, null);
                var viewAfterRemoval = operation(8, editor, SyncUnitAuthorization.Action.VIEW, null, null);
                var foreignUnitInvite = SyncUnitAuthorization.Operation.sign(SyncUnitId.create(), 0,
                                NodeId.fromBytes(new byte[32]), creatorKey, SyncUnitAuthorization.Action.INVITE,
                                strangerId,
                                SyncUnitAuthorization.Role.EDITOR);

                var inOrder = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.SHARED_FOLDER,
                                creatorId,
                                List.of(invite, accept, modifyBeforeRoleChange, unauthorizedRoleChange, demote,
                                                modifyAfterDemotion, remove, viewAfterRemoval, foreignUnitInvite));
                var outOfOrder = SyncUnitAuthorization.replay(sharedUnitId, SyncUnitAuthorization.Type.SHARED_FOLDER,
                                creatorId,
                                List.of(viewAfterRemoval, remove, modifyAfterDemotion, demote, unauthorizedRoleChange,
                                                modifyBeforeRoleChange, accept, invite));

                assertEquals(inOrder.members(), outOfOrder.members());
                assertEquals(inOrder.pendingInvitations(), outOfOrder.pendingInvitations());
                assertEquals(5, inOrder.acceptedOperationCount());
                assertEquals(3, inOrder.rejectedOperationCount());
                assertEquals(Map.of(creatorId, SyncUnitAuthorization.Role.MANAGER), outOfOrder.members());
        }

        private SyncUnitAuthorization.Operation operation(long clock, PubKey author,
                        SyncUnitAuthorization.Action action, UserId subject, SyncUnitAuthorization.Role role) {
                return SyncUnitAuthorization.Operation.sign(sharedUnitId, clock, NodeId.fromBytes(new byte[32]),
                                privateKeys.get(author),
                                action, subject, role);
        }

        private static boolean authorized(SyncUnitAuthorization.Type type, UserId creator,
                        Map<UserId, SyncUnitAuthorization.Role> members, PubKey author,
                        SyncUnitAuthorization.Action action) {
                return SyncUnitAuthorization.isAuthorized(type, creator, members, author, action);
        }
}