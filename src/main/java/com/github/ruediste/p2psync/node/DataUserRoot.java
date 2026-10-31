package com.github.ruediste.p2psync.node;

import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class DataUserRoot {
    public String userStatusMessage;
    public BlockId rootDirectoryId;
    public byte[] rootDirectoryGuid;

    public Sync.DataUserRoot toProto() {
        return Sync.DataUserRoot.newBuilder()
                .setUserStatusMessage(userStatusMessage)
                .setRootDirectoryId(rootDirectoryId.toProto())
                .setRootDirectoryGuid(ByteString.copyFrom(rootDirectoryGuid))
                .build();
    }

    public static DataUserRoot from(Sync.DataUserRoot proto) {
        DataUserRoot res = new DataUserRoot();
        res.userStatusMessage = proto.getUserStatusMessage();
        res.rootDirectoryId = BlockId.fromProto(proto.getRootDirectoryId());
        res.rootDirectoryGuid = proto.getRootDirectoryGuid().toByteArray();
        return res;
    }

    public static DataUserRoot from(byte[] bytes) {
        try {
            return from(Sync.DataUserRoot.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
