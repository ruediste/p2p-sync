package com.github.ruediste.p2psync.node;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

public record SyncUnitContentOperation(SyncUnitId unitId, long lamportClock, NodeId nodeId,
        PubKey author, Kind kind, String key, byte[] payload, byte[] signature) {
    public enum Kind {
        SHARED_FILE_SET,
        SHARED_FILE_DELETE,
        TODO_SET,
        TODO_DELETE,
        CHANNEL_MESSAGE,
        SHARED_DIRECTORY_CREATE,
        SHARED_DIRECTORY_DELETE
    }

    public SyncUnitContentOperation {
        Objects.requireNonNull(unitId);
        if (lamportClock < 0)
            throw new IllegalArgumentException("Lamport clock must not be negative");
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(author);
        Objects.requireNonNull(kind);
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Content operation key must not be blank");
        Objects.requireNonNull(payload);
        payload = payload.clone();
        Objects.requireNonNull(signature);
        signature = signature.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public static SyncUnitContentOperation sign(SyncUnitId unitId, long lamportClock, NodeId nodeId,
            PrivKey author, Kind kind, String key, byte[] payload) {
        var unsigned = new SyncUnitContentOperation(unitId, lamportClock, nodeId, author.publicKey(), kind,
                key, payload, new byte[0]);
        return new SyncUnitContentOperation(unitId, lamportClock, nodeId, author.publicKey(), kind,
                key, payload, author.sign(unsigned.signingBytes()));
    }

    public boolean hasValidSignature() {
        try {
            return author.verify(signingBytes(), signature);
        } catch (RuntimeException invalidSignature) {
            return false;
        }
    }

    byte[] signingBytes() {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.write(unitId.bytes());
            output.writeLong(lamportClock);
            output.write(nodeId.bytes());
            writeBytes(output, author.bytes());
            output.writeInt(kind.ordinal());
            writeBytes(output, key.getBytes(StandardCharsets.UTF_8));
            writeBytes(output, payload);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode SyncUnit content operation", e);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
