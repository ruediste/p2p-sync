package com.github.ruediste.p2psync.node;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.proto.Sync;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

final class UserState {
    final UserFileTree fileSystem;
    final ContactList contacts;

    UserState(UserFileTree fileSystem, ContactList contacts) {
        this.fileSystem = fileSystem;
        this.contacts = contacts;
    }

    static UserState empty(DataStorage storage, GuidMapper mapper, byte[] rootId, PubKey owner) {
        return new UserState(UserFileTree.empty(storage, mapper, rootId), ContactList.empty(owner));
    }

    static byte[] rootIdFromState(byte[] state) {
        try {
            var snapshot = Sync.UserSnapshot.parseFrom(state);
            return Sync.UserTreeState.parseFrom(snapshot.getFileTreeState()).getRootId().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static UserState replayFromState(DataStorage storage, GuidMapper mapper, byte[] state, PubKey owner,
            Iterable<Node.Operation> operations) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            var operationsList = new java.util.ArrayList<Node.Operation>();
            for (var operation : operations)
                operationsList.add(operation);
            long rootOffset = stored.getFileTreeRootOffset();
            var fileSystem = rootOffset != 0
                    ? UserFileTree.fromRoot(storage, mapper, stored.getFileTreeState().toByteArray(), rootOffset)
                    : UserFileTree.fromState(storage, mapper, stored.getFileTreeState().toByteArray());
            for (var operation : operationsList)
                applyOperation(fileSystem, operation);
            var contacts = ContactList.from(owner, stored.getContactsList());
            for (var operation : operationsList) {
                try {
                    contacts.apply(Sync.UserOperation.parseFrom(operation.payload));
                } catch (InvalidProtocolBufferException invalidOperation) {
                    throw new IllegalArgumentException("Invalid user operation payload", invalidOperation);
                }
            }
            return new UserState(fileSystem, contacts);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    private static void applyOperation(UserFileTree fileSystem, Node.Operation operation) {
        try {
            fileSystem.apply(Sync.UserOperation.parseFrom(operation.payload));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user operation payload", e);
        }
    }

    static byte[] transferableSnapshot(DataStorage storage, GuidMapper mapper, PubKey owner, byte[] state) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            if (stored.getFileTreeRootOffset() == 0)
                return state.clone();
            var base = UserFileTree.fromRoot(storage, mapper, stored.getFileTreeState().toByteArray(),
                    stored.getFileTreeRootOffset());
            return transferableSnapshot(ContactList.from(owner, stored.getContactsList()), base);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static byte[] transferableSnapshot(ContactList contacts, UserFileTree tree) {
        return Sync.UserSnapshot.newBuilder()
                .setFileTreeState(ByteString.copyFrom(tree.serializeState()))
                .addAllContacts(contacts.toProto())
                .build().toByteArray();
    }

    static byte[] materializeSnapshot(DataStorage storage, GuidMapper mapper, PubKey owner, byte[] state) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            if (stored.getFileTreeRootOffset() != 0)
                return state;
            var tree = UserFileTree.fromState(storage, mapper, stored.getFileTreeState().toByteArray());
            var contacts = ContactList.from(owner, stored.getContactsList());
            return new UserState(tree, contacts).serializeState();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static long snapshotRootOffset(byte[] state) {
        if (state == null)
            return 0;
        try {
            return Sync.UserSnapshot.parseFrom(state).getFileTreeRootOffset();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static ContactList contactsFromState(byte[] state, PubKey owner) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            return ContactList.from(owner, stored.getContactsList());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static byte[] snapshotWithFileTree(Iterable<Sync.Contact> contacts, byte[] rootId, String status,
            Iterable<Sync.UserTreeEntry> entries) {
        var tree = Sync.UserTreeState.newBuilder()
                .setRootId(ByteString.copyFrom(rootId))
                .setStatusMessage(status)
                .addAllEntries(entries);
        return Sync.UserSnapshot.newBuilder()
                .setFileTreeState(ByteString.copyFrom(tree.build().toByteArray()))
                .addAllContacts(contacts)
                .build().toByteArray();
    }

    byte[] serializeState() {
        return Sync.UserSnapshot.newBuilder()
                .setFileTreeState(ByteString.copyFrom(fileSystem.serializeShallowState()))
                .setFileTreeRootOffset(fileSystem.rootOffset())
                .addAllContacts(contacts.toProto())
                .build().toByteArray();
    }
}
