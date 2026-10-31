package com.github.ruediste.p2psync.node;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public record Operation(SyncUnitId unitId, long lamportClock, byte[] nodeHash, PubKey author, Action action,
            PubKey subject, Role role, byte[] signature) {
        public Operation {
            Objects.requireNonNull(unitId);
            if (lamportClock < 0)
                throw new IllegalArgumentException("Lamport clock must not be negative");
            if (nodeHash == null || nodeHash.length != 32)
                throw new IllegalArgumentException("Node hash must contain 32 bytes");
            nodeHash = nodeHash.clone();
            Objects.requireNonNull(author);
            Objects.requireNonNull(action);
            Objects.requireNonNull(signature);
            signature = signature.clone();
        }

        @Override
        public byte[] nodeHash() {
            return nodeHash.clone();
        }

        @Override
        public byte[] signature() {
            return signature.clone();
        }

        public static Operation sign(SyncUnitId unitId, long lamportClock, byte[] nodeHash, PrivKey author,
                Action action, PubKey subject, Role role) {
            var unsigned = new Operation(unitId, lamportClock, nodeHash, author.publicKey(), action,
                    subject, role, new byte[0]);
            return new Operation(unitId, lamportClock, nodeHash, author.publicKey(), action, subject, role,
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
                output.write(nodeHash);
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

    public record State(Map<PubKey, Role> members, Map<PubKey, Role> pendingInvitations,
            List<Operation> acceptedOperations, List<Operation> rejectedOperations) {
        public State {
            members = Map.copyOf(members);
            pendingInvitations = Map.copyOf(pendingInvitations);
            acceptedOperations = List.copyOf(acceptedOperations);
            rejectedOperations = List.copyOf(rejectedOperations);
        }
    }

    private SyncUnitAuthorization() {
    }

    public static boolean isAuthorized(Type type, PubKey creator, Map<PubKey, Role> members,
            PubKey author, Action action) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(creator);
        Objects.requireNonNull(members);
        Objects.requireNonNull(author);
        Objects.requireNonNull(action);

        if (type == Type.USER)
            return creator.equals(author) && (action == Action.VIEW || action == Action.MODIFY);

        Role role = members.get(author);
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
    public static State replay(SyncUnitId unitId, Type type, PubKey creator, Iterable<Operation> operations) {
        Objects.requireNonNull(unitId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(creator);
        var uniqueOperations = new LinkedHashMap<String, Operation>();
        for (var operation : operations) {
            if (!operation.unitId().equals(unitId))
                continue;
            String id = operation.lamportClock() + ":" + HexFormat.of().formatHex(operation.nodeHash());
            var previous = uniqueOperations.putIfAbsent(id, operation);
            if (previous != null && !sameOperation(previous, operation))
                throw new IllegalArgumentException("Conflicting authorization operations share an ID");
        }

        var ordered = new ArrayList<>(uniqueOperations.values());
        ordered.sort((left, right) -> {
            int clockOrder = Long.compare(left.lamportClock(), right.lamportClock());
            return clockOrder != 0 ? clockOrder : Arrays.compareUnsigned(left.nodeHash(), right.nodeHash());
        });

        var members = new LinkedHashMap<PubKey, Role>();
        members.put(creator, initialRole(type));
        var pending = new LinkedHashMap<PubKey, Role>();
        var accepted = new ArrayList<Operation>();
        var rejected = new ArrayList<Operation>();

        for (var operation : ordered) {
            PubKey subject = operation.subject();
            if (!operation.hasValidSignature()) {
                rejected.add(operation);
                continue;
            }
            boolean authorized = switch (operation.action()) {
                case INVITE -> isAuthorized(type, creator, members, operation.author(), Action.INVITE)
                        && subject != null && operation.role() != null
                        && isAssignableRole(type, operation.role())
                        && !members.containsKey(subject) && !pending.containsKey(subject);
                case ACCEPT_INVITATION -> subject != null && subject.equals(operation.author())
                        && pending.containsKey(subject);
                case CHANGE_ROLE -> isAuthorized(type, creator, members, operation.author(), Action.CHANGE_ROLE)
                        && subject != null && operation.role() != null && members.containsKey(subject)
                        && (type == Type.MESSAGE_CHANNEL || !subject.equals(creator))
                        && isAssignableRole(type, operation.role())
                        && !wouldRemoveLastManager(type, members, subject, operation.role());
                case REMOVE_MEMBER -> isAuthorized(type, creator, members, operation.author(), Action.REMOVE_MEMBER)
                        && subject != null && members.containsKey(subject)
                        && (type == Type.MESSAGE_CHANNEL || !subject.equals(creator))
                        && !wouldRemoveLastManager(type, members, subject, null);
                default -> isAuthorized(type, creator, members, operation.author(), operation.action());
            };

            if (!authorized) {
                rejected.add(operation);
                continue;
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
            accepted.add(operation);
        }

        return new State(members, pending, accepted, rejected);
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

    private static boolean wouldRemoveLastManager(Type type, Map<PubKey, Role> members, PubKey subject,
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

    public static void requireAuthorized(Type type, PubKey creator, Map<PubKey, Role> members,
            PubKey author, Action action) {
        if (!isAuthorized(type, creator, members, author, action))
            throw new IllegalArgumentException("User is not authorized to " + action + " this " + type);
    }
}