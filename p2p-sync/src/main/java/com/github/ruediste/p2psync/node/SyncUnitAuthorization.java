package com.github.ruediste.p2psync.node;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

public final class SyncUnitAuthorization {
    public enum Type {
        USER,
        SHARED_FOLDER,
        TODO_LIST,
        MESSAGE_CHANNEL
    }

    public enum Role {
        OWNER,
        VIEWER,
        EDITOR,
        MANAGER,
        MEMBER
    }

    public enum Action {
        VIEW,
        MODIFY,
        MANAGE_MEMBERS,
        SEND_MESSAGE,
        INVITE,
        ACCEPT_INVITATION,
        CHANGE_ROLE,
        REMOVE_MEMBER
    }

    public record Operation(SyncUnitId unitId, long lamportClock, NodeId nodeId, PubKey author, Action action,
            UserId subject, Role role, byte[] signature) {
        public Operation {
            Objects.requireNonNull(unitId);
            if (lamportClock < 0)
                throw new IllegalArgumentException("Lamport clock must not be negative");
            Objects.requireNonNull(nodeId);
            Objects.requireNonNull(author);
            Objects.requireNonNull(action);
            Objects.requireNonNull(signature);
            signature = signature.clone();
        }

        @Override
        public byte[] signature() {
            return signature.clone();
        }

        public static Operation sign(SyncUnitId unitId, long lamportClock, NodeId nodeId, PrivKey author,
                Action action, UserId subject, Role role) {
            var unsigned = new Operation(unitId, lamportClock, nodeId, author.publicKey(), action,
                    subject, role, new byte[0]);
            return new Operation(unitId, lamportClock, nodeId, author.publicKey(), action, subject, role,
                    author.sign(unsigned.signingBytes()));
        }

        public boolean hasValidSignature() {
            try {
                return author.verify(signingBytes(), signature);
            } catch (RuntimeException invalidSignature) {
                return false;
            }
        }

        private byte[] signingBytes() {
            try {
                var bytes = new ByteArrayOutputStream();
                var output = new DataOutputStream(bytes);
                output.write(unitId.bytes());
                output.writeLong(lamportClock);
                output.write(nodeId.bytes());
                writeBytes(output, author.bytes());
                output.writeInt(action.ordinal());
                output.writeBoolean(subject != null);
                if (subject != null)
                    writeBytes(output, subject.bytes());
                output.writeInt(role == null ? 0 : role.ordinal() + 1);
                output.flush();
                return bytes.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Could not encode SyncUnit authorization operation", e);
            }
        }

        private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
            output.writeInt(bytes.length);
            output.write(bytes);
        }
    }

    /** Raised when a user lacks the role required for an action. */
    public static final class NotAuthorizedException extends IllegalArgumentException {
        public NotAuthorizedException(String message) {
            super(message);
        }
    }

    public record State(Map<UserId, Role> members, Map<UserId, Role> pendingInvitations,
            int acceptedOperationCount, int rejectedOperationCount, boolean lastOperationAccepted) {
        public State {
            members = Map.copyOf(members);
            pendingInvitations = Map.copyOf(pendingInvitations);
        }
    }

    private SyncUnitAuthorization() {
    }

    public static boolean isAuthorized(Type type, UserId creator, Map<UserId, Role> members,
            PubKey author, Action action) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(creator);
        Objects.requireNonNull(members);
        Objects.requireNonNull(author);
        Objects.requireNonNull(action);

        UserId authorId = UserId.fromPublicKey(author);
        if (type == Type.USER)
            return creator.equals(authorId) && (action == Action.VIEW || action == Action.MODIFY);

        Role role = members.get(authorId);
        if (role == null)
            return false;

        return switch (type) {
            case USER -> throw new AssertionError("USER handled above");
            case SHARED_FOLDER, TODO_LIST -> switch (action) {
                case VIEW -> role == Role.VIEWER || role == Role.EDITOR || role == Role.MANAGER;
                case MODIFY -> role == Role.EDITOR || role == Role.MANAGER;
                case MANAGE_MEMBERS, INVITE, CHANGE_ROLE, REMOVE_MEMBER -> role == Role.MANAGER;
                case SEND_MESSAGE, ACCEPT_INVITATION -> false;
            };
            case MESSAGE_CHANNEL -> switch (action) {
                case VIEW, SEND_MESSAGE -> role == Role.MEMBER || role == Role.MANAGER;
                case MANAGE_MEMBERS, INVITE, CHANGE_ROLE, REMOVE_MEMBER -> role == Role.MANAGER;
                case MODIFY, ACCEPT_INVITATION -> false;
            };
        };
    }

    /** Replays authenticated operations in deterministic unit order. */
    public static State replay(SyncUnitId unitId, Type type, UserId creator, Iterable<Operation> operations) {
        return replay(unitId, type, creator, operations, false);
    }

    /** Replays an already ordered storage cursor in one pass. */
    static State replayOrdered(SyncUnitId unitId, Type type, UserId creator, Iterable<Operation> operations) {
        return replay(unitId, type, creator, operations, true);
    }

    private static State replay(SyncUnitId unitId, Type type, UserId creator, Iterable<Operation> operations,
            boolean ordered) {
        Objects.requireNonNull(unitId);
        Objects.requireNonNull(type);
        var replay = new Replay(unitId, type, creator);
        Operation previous = null;
        var orderedIterator = ordered ? operations.iterator() : null;
        while (true) {
            Operation operation = null;
            if (ordered) {
                while (orderedIterator.hasNext()) {
                    var candidate = orderedIterator.next();
                    if (candidate.unitId().equals(unitId)) {
                        operation = candidate;
                        break;
                    }
                }
            } else {
                for (var candidate : operations) {
                    if (!candidate.unitId().equals(unitId)
                            || previous != null && compare(candidate, previous) <= 0)
                        continue;
                    if (operation == null || compare(candidate, operation) < 0) {
                        operation = candidate;
                    } else if (compare(candidate, operation) == 0 && !sameOperation(operation, candidate)) {
                        throw new IllegalArgumentException("Conflicting authorization operations share an ID");
                    }
                }
            }
            if (operation == null)
                break;
            if (!ordered)
                previous = operation;
            replay.apply(operation);
        }
        return replay.state();
    }

    static Replay newReplay(SyncUnitId unitId, Type type, UserId creator) {
        return new Replay(unitId, type, creator);
    }

    static final class Replay {
        private final SyncUnitId unitId;
        private final Type type;
        private final UserId creator;
        private final LinkedHashMap<UserId, Role> members = new LinkedHashMap<>();
        private final LinkedHashMap<UserId, Role> pending = new LinkedHashMap<>();
        private int acceptedCount;
        private int rejectedCount;
        private boolean lastAccepted = true;

        Replay(SyncUnitId unitId, Type type, UserId creator) {
            this.unitId = Objects.requireNonNull(unitId);
            this.type = Objects.requireNonNull(type);
            this.creator = Objects.requireNonNull(creator);
            members.put(creator, initialRole(type));
        }

        boolean apply(Operation operation) {
            if (!operation.unitId().equals(unitId))
                return true;
            UserId subject = operation.subject();
            if (!operation.hasValidSignature()) {
                reject();
                return false;
            }
            boolean authorized = switch (operation.action()) {
                case INVITE ->
                    SyncUnitAuthorization.isAuthorized(type, creator, members, operation.author(), Action.INVITE)
                            && subject != null && operation.role() != null
                            && isAssignableRole(type, operation.role())
                            && !members.containsKey(subject) && !pending.containsKey(subject);
                case ACCEPT_INVITATION -> subject != null && subject.equals(UserId.fromPublicKey(operation.author()))
                        && pending.containsKey(subject);
                case CHANGE_ROLE ->
                    SyncUnitAuthorization.isAuthorized(type, creator, members, operation.author(), Action.CHANGE_ROLE)
                            && subject != null && operation.role() != null && members.containsKey(subject)
                            && (type == Type.MESSAGE_CHANNEL || !subject.equals(creator))
                            && isAssignableRole(type, operation.role())
                            && !wouldRemoveLastManager(type, members, subject, operation.role());
                case REMOVE_MEMBER ->
                    SyncUnitAuthorization.isAuthorized(type, creator, members, operation.author(), Action.REMOVE_MEMBER)
                            && subject != null && members.containsKey(subject)
                            && (type == Type.MESSAGE_CHANNEL || !subject.equals(creator))
                            && !wouldRemoveLastManager(type, members, subject, null);
                default -> SyncUnitAuthorization.isAuthorized(type, creator, members, operation.author(),
                        operation.action());
            };
            if (!authorized) {
                reject();
                return false;
            }
            switch (operation.action()) {
                case INVITE -> pending.put(subject, operation.role());
                case ACCEPT_INVITATION -> members.put(subject, pending.remove(subject));
                case CHANGE_ROLE -> members.put(subject, operation.role());
                case REMOVE_MEMBER -> {
                    members.remove(subject);
                    pending.remove(subject);
                }
                default -> {
                }
            }
            acceptedCount++;
            lastAccepted = true;
            return true;
        }

        private void reject() {
            rejectedCount++;
            lastAccepted = false;
        }

        boolean isAuthorized(PubKey author, Action action) {
            return SyncUnitAuthorization.isAuthorized(type, creator, members, author, action);
        }

        State state() {
            return new State(members, pending, acceptedCount, rejectedCount, lastAccepted);
        }
    }

    private static int compare(Operation left, Operation right) {
        int clockOrder = Long.compare(left.lamportClock(), right.lamportClock());
        return clockOrder != 0 ? clockOrder : left.nodeId().compareTo(right.nodeId());
    }

    private static Role initialRole(Type type) {
        return switch (type) {
            case USER -> Role.OWNER;
            case SHARED_FOLDER, TODO_LIST -> Role.MANAGER;
            case MESSAGE_CHANNEL -> Role.MANAGER;
        };
    }

    private static boolean isAssignableRole(Type type, Role role) {
        return switch (type) {
            case USER -> false;
            case SHARED_FOLDER, TODO_LIST -> role == Role.VIEWER || role == Role.EDITOR || role == Role.MANAGER;
            case MESSAGE_CHANNEL -> role == Role.MEMBER || role == Role.MANAGER;
        };
    }

    private static boolean wouldRemoveLastManager(Type type, Map<UserId, Role> members, UserId subject,
            Role replacementRole) {
        if (type != Type.MESSAGE_CHANNEL || members.get(subject) != Role.MANAGER
                || replacementRole == Role.MANAGER)
            return false;
        return members.values().stream().filter(role -> role == Role.MANAGER).count() <= 1;
    }

    private static boolean sameOperation(Operation left, Operation right) {
        return left.unitId().equals(right.unitId()) && left.author().equals(right.author())
                && left.action() == right.action() && Objects.equals(left.subject(), right.subject())
                && left.role() == right.role() && Arrays.equals(left.signature(), right.signature());
    }

    public static void requireAuthorized(Type type, UserId creator, Map<UserId, Role> members,
            PubKey author, Action action) {
        if (!isAuthorized(type, creator, members, author, action))
            throw new NotAuthorizedException("User is not authorized to " + action + " this " + type);
    }
}