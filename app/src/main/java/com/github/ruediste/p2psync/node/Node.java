package com.github.ruediste.p2psync.node;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.clock.LocalClock;
import com.github.ruediste.p2psync.clock.NodeNrMap;
import com.github.ruediste.p2psync.clock.VectorClock;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;

public class Node {
    public PeerId peerId = PeerId.random();
    public BlockStorage storage = new BlockStorage();
    public Map<PubKey, NodeUserData> users = new HashMap<>();
    public Network network;

    public Node(Network network) {
        this.network = network;
        network.nodes.put(peerId, this);
    }

    public byte[] getBlock(BlockId id) {
        return storage.getBlock(id).get();
    }

    public static class Network {
        public Map<PeerId, Node> nodes = new HashMap<>();

        public StorageUserRoot getStorageUserRoot(PeerId peerId, PubKey userId) {
            var peer = nodes.get(peerId);
            var rootId = peer.users.get(userId).rootId;
            return StorageUserRoot.from(getBlock(rootId));
        }

        public byte[] getBlock(BlockId id) {
            for (var node : nodes.values()) {
                var block = node.storage.getBlock(id);
                if (block.isPresent())
                    return block.get();
            }
            return null;
        }

        public Set<PeerId> getPeersForUser(PubKey userId) {
            return nodes.values().stream().filter(x -> x.users.containsKey(userId)).map(x -> x.peerId)
                    .collect(Collectors.toSet());
        }
    }

    public static class NodeUserData {
        public LocalClock localClock;
        public BlockId rootId;
        public JoinId joinId;
        public PrivKey userKey;

        public PubKey userId() {
            return userKey.publicKey();
        }
    }

    public PubKey createNewUser() {
        var map = new NodeNrMap();
        var joinId = map.join(peerId);
        var nr = map.getNr(joinId).get();

        var data = new NodeUserData();

        data.localClock = new LocalClock(nr, VectorClock.empty());
        data.localClock.prepareModify();

        var rootDir = FsDirectory.empty(data.localClock.get());

        var root = new DataUserRoot();
        root.rootDirectoryId = storage.store(rootDir.toProto());
        root.userStatusMessage = "initial";

        var storageRoot = new StorageUserRoot();
        storageRoot.clock = data.localClock.getAndClear();
        storageRoot.shardListId = new BlockId(new byte[] {});
        storageRoot.dataRootBlockId = storage.store(root.toProto());
        storageRoot.nodeNrMapId = storage.store(map.toProto());

        data.userKey = Ed25519PrivateKey.generateKeyPair();

        data.rootId = storage.store(storageRoot.toProto());
        users.put(data.userId(), data);
        return data.userId();
    }

    public void join(PubKey userId) {
        var peer = network.getPeersForUser(userId).stream().findFirst().get();
        var root = network.getStorageUserRoot(peer, userId);

        var data = new NodeUserData();
        var map = NodeNrMap.from(network.getBlock(root.nodeNrMapId));
        var joinId = map.join(peerId);
        var nr = map.getNr(joinId).get();

        data.localClock = new LocalClock(nr, root.clock);
        data.rootId = storage.store(root.toProto());
        users.put(userId, data);
    }

    public void modify(PubKey userId, Consumer<DataUserRoot> action) {
        var data = users.get(userId);
        data.localClock.prepareModify();
        var root = StorageUserRoot.from(network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(network.getBlock(root.dataRootBlockId));
        action.accept(dataRoot);
        root.dataRootBlockId = storage.store(dataRoot.toProto());
        root.clock = data.localClock.get().clone();
        data.rootId = storage.store(root.toProto());
    }

    public <T> T read(PubKey userId, Function<DataUserRoot, T> action) {
        var data = users.get(userId);
        var root = StorageUserRoot.from(network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(network.getBlock(root.dataRootBlockId));
        return action.apply(dataRoot);
    }

    public void syncFrom(PeerId otherId, PubKey userId) {
        var otherRoot = network.getStorageUserRoot(otherId, userId);

        var data = users.get(userId);
        var root = StorageUserRoot.from(network.getBlock(data.rootId));

        switch (root.clock.compare(otherRoot.clock)) {
            case EQUAL:
            case AFTER:
                // NOP, we already have the latest version
                break;
            case BEFORE:
                // just update
                data.rootId = storage.store(otherRoot.toProto());
                data.localClock.resetTo(otherRoot.clock);
                break;
            case CONCURRENT:
                throw new UnsupportedOperationException("Not yet implemented");
            default:
                break;
        }
    }

}
