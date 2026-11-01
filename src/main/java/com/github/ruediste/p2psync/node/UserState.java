package com.github.ruediste.p2psync.node;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

final class UserState {
    final UserFileTree fileSystem;
    final ContactList contacts;

    UserState(UserFileTree fileSystem, ContactList contacts) {
        this.fileSystem = fileSystem;
        this.contacts = contacts;
    }

    static UserState empty(byte[] rootId, PubKey owner) {
        return new UserState(UserFileTree.empty(rootId), ContactList.empty(owner));
    }

    static UserState from(byte[] state, PubKey owner) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            return new UserState(UserFileTree.fromState(stored.getFileTreeState().toByteArray()),
                    ContactList.from(owner, stored.getContactsList()));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user snapshot", e);
        }
    }

    static UserState replayFromState(byte[] state, PubKey owner, Iterable<Node.Operation> operations) {
        try {
            var stored = Sync.UserSnapshot.parseFrom(state);
            var fileSystem = UserFileTree.replayFromState(stored.getFileTreeState().toByteArray(), operations);
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

    byte[] serializeState() {
        return Sync.UserSnapshot.newBuilder()
                .setFileTreeState(ByteString.copyFrom(fileSystem.serializeState()))
                .addAllContacts(contacts.toProto())
                .build().toByteArray();
    }

    DataUserRoot toDataUserRoot(BlockStorage storage) {
        var result = fileSystem.toDataUserRoot(storage);
        result.contacts = contacts.list();
        return result;
    }
}
