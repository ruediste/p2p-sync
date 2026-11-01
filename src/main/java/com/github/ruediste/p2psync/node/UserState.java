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
            var fileSystem = UserFileTree.replayFromState(storage, mapper,
                    stored.getFileTreeState().toByteArray(), operations);
            var contacts = ContactList.from(owner, stored.getContactsList());
            for (var operation : operations) {
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

    static ContactList contactsFromState(byte[] state, PubKey owner) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            return ContactList.from(owner, stored.getContactsList());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static byte[] snapshotWithFileTree(byte[] baseSnapshot, byte[] rootId, String status,
            Iterable<Sync.UserTreeEntry> entries) {
        try {
            var base = Sync.UserSnapshot.parseFrom(baseSnapshot);
            var tree = Sync.UserTreeState.newBuilder()
                    .setRootId(ByteString.copyFrom(rootId))
                    .setStatusMessage(status)
                    .addAllEntries(entries);
            return Sync.UserSnapshot.newBuilder()
                    .setFileTreeState(ByteString.copyFrom(tree.build().toByteArray()))
                    .addAllContacts(base.getContactsList())
                    .build().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    byte[] serializeState() {
        return Sync.UserSnapshot.newBuilder()
                .setFileTreeState(ByteString.copyFrom(fileSystem.serializeState()))
                .addAllContacts(contacts.toProto())
                .build().toByteArray();
    }
}
