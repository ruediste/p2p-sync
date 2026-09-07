package com.github.ruediste.p2psync.clock;

import java.util.Optional;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.node.JoinId;
import com.github.ruediste.p2psync.proto.Sync;

/**
 * An entry of the {@link NodeNrMap}
 */
public class NodeNrEntry {
    public int nodeNr;
    public JoinId joinId;
    public PeerId peerId;
    public Optional<VectorClock> leavingSince;

    public NodeNrEntry(int nodeNr, JoinId joinId, PeerId peerId, Optional<VectorClock> leavingSince) {
        this.nodeNr = nodeNr;
        this.joinId = joinId;
        this.peerId = peerId;
        this.leavingSince = leavingSince;
    }

    public static NodeNrEntry from(Sync.NodeNrEntry proto) {
        return new NodeNrEntry(
                proto.getNodeNr(),
                new JoinId(proto.getJoinId().toByteArray()),
                new PeerId(proto.getPeerId().toByteArray()),
                proto.hasLeavingSince() ? Optional.of(VectorClock.from(proto.getLeavingSince())) : Optional.empty());
    }

    public Sync.NodeNrEntry toProto() {
        var builder = Sync.NodeNrEntry.newBuilder()
                .setNodeNr(nodeNr)
                .setJoinId(com.google.protobuf.ByteString.copyFrom(joinId.bytes()))
                .setPeerId(com.google.protobuf.ByteString.copyFrom(peerId.getBytes()));
        leavingSince.ifPresent(c -> builder.setLeavingSince(c.toProto()));
        return builder.build();
    }
}
