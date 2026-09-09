package com.github.ruediste.p2psync.node;

import java.util.function.Consumer;
import java.util.function.Function;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.node.Node.NodeUserData;

public class NodeUserHandle {
    private final Node node;
    public NodeUserData data;

    public Node getNode() {
        return node;
    }

    public NodeUserHandle(Node node, NodeUserData data) {
        this.node = node;
        this.data = data;
    }

    public void modify(Consumer<DataUserRoot> action) {
        data.localClock.prepareModify();
        var root = StorageUserRoot.from(this.node.network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(this.node.network.getBlock(root.dataRootBlockId));
        action.accept(dataRoot);
        root.dataRootBlockId = this.node.storage.store(dataRoot.toProto());
        root.clock = data.localClock.get().clone();
        data.rootId = this.node.storage.store(root.toProto());
    }

    public <T> T read(Function<DataUserRoot, T> action) {
        var root = StorageUserRoot.from(this.node.network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(this.node.network.getBlock(root.dataRootBlockId));
        return action.apply(dataRoot);
    }

    public void syncFrom(PeerId otherId) {
        var otherRoot = this.node.network.getStorageUserRoot(otherId, data.userId());

        var root = StorageUserRoot.from(this.node.network.getBlock(data.rootId));

        switch (root.clock.compare(otherRoot.clock)) {
            case EQUAL:
            case AFTER:
                // NOP, we already have the latest version
                break;
            case BEFORE:
                // just update
                data.rootId = this.node.storage.store(otherRoot.toProto());
                data.localClock.resetTo(otherRoot.clock);
                break;
            case CONCURRENT:
                throw new UnsupportedOperationException("Not yet implemented");
            default:
                break;
        }
    }
}