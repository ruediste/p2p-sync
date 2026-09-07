package com.github.ruediste.p2psync.node;

import com.github.ruediste.p2psync.clock.VectorClock;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.InvalidProtocolBufferException;

public class StorageUserRoot {
    public VectorClock clock;
    public BlockId shardListId;
    public BlockId dataRootBlockId;
    public BlockId nodeNrMapId;

    public Sync.StorageUserRoot toProto() {
        return Sync.StorageUserRoot.newBuilder()
                .setClock(clock.toProto())
                .setShardListId(shardListId.toProto())
                .setDataRootBlockId(dataRootBlockId.toProto())
                .setNodeNrMapId(nodeNrMapId.toProto())
                .build();
    }

    public static StorageUserRoot from(Sync.StorageUserRoot proto) {
        StorageUserRoot res = new StorageUserRoot();
        res.clock = VectorClock.from(proto.getClock());
        res.shardListId = BlockId.fromProto(proto.getShardListId());
        res.dataRootBlockId = BlockId.fromProto(proto.getDataRootBlockId());
        res.nodeNrMapId = BlockId.fromProto(proto.getNodeNrMapId());
        return res;
    }

    public static StorageUserRoot from(byte[] bytes) {
        try {
            return from(Sync.StorageUserRoot.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

}
