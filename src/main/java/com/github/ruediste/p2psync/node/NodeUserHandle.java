package com.github.ruediste.p2psync.node;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.node.Node.NodeUserData;
import com.github.ruediste.p2psync.proto.Sync;

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
        var root = StorageUserRoot.from(this.node.network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(this.node.network.getBlock(root.dataRootBlockId));
        String previousStatus = dataRoot.userStatusMessage;
        byte[] previousRootId = dataRoot.rootDirectoryGuid.clone();
        action.accept(dataRoot);
        if (!java.util.Arrays.equals(previousRootId, dataRoot.rootDirectoryGuid))
            throw new IllegalArgumentException("The root directory identity is managed by the filesystem API");
        if (!java.util.Objects.equals(previousStatus, dataRoot.userStatusMessage)) {
            var operation = Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.SET_STATUS)
                    .setStatusMessage(dataRoot.userStatusMessage)
                    .build();
            recordOperation(operation);
        }
    }

    public <T> T read(Function<DataUserRoot, T> action) {
        var root = StorageUserRoot.from(this.node.network.getBlock(data.rootId));
        var dataRoot = DataUserRoot.from(this.node.network.getBlock(root.dataRootBlockId));
        return action.apply(dataRoot);
    }

    public void syncFrom(PeerId otherId) {
        syncFrom(otherId, 100, 1_000_000);
    }

    public void syncFrom(PeerId otherId, int maxOperations, int byteBudget) {
        if (maxOperations <= 0 || byteBudget <= 0)
            throw new IllegalArgumentException("Transfer limits must be positive");
        var peer = node.network.nodes.get(otherId);
        if (peer == null) {
            syncFromRemote(otherId, maxOperations, byteBudget);
            return;
        }
        var peerData = peer.users.get(data.userId());
        if (peerData == null)
            throw new IllegalArgumentException("Peer does not host user " + data.userId());

        var sources = new java.util.TreeSet<String>();
        sources.addAll(data.maxOperationClock.keySet());
        sources.addAll(data.operationSources());
        sources.addAll(peerData.operationSources());
        var fromClock = new java.util.TreeMap<String, Long>();
        var offsets = new java.util.HashMap<String, Integer>();
        for (var source : sources) {
            fromClock.put(source, data.maxOperationClock.getOrDefault(source, 0L));
            var partial = data.retrievalTree.get(source);
            if (partial != null)
                offsets.put(source, partial.receivedLength);
        }
        var transfers = peerData.serve(fromClock, offsets, maxOperations, byteBudget);
        data.receive(transfers, node);
        mergeStaging();
        data.activeNodeSet.mergeFrom(peerData.activeNodeSet, node.nodeKey(), node::nextClock);
        data.activeNodeSet.updateLeaving(node.nodeKey(), data.maxOperationClock, node::nextClock);
        data.activeNodeSet.lastSeen.put(peer.nodeKey(), System.currentTimeMillis());
        node.persistState();
    }

    private void syncFromRemote(PeerId peerId, int maxOperations, int byteBudget) {
        var sources = new java.util.TreeSet<String>();
        sources.addAll(data.activeNodeSet.nodes);
        sources.addAll(data.maxOperationClock.keySet());
        sources.addAll(data.operationSources());
        var request = Sync.RemoteSyncRequest.newBuilder()
                .setUserId(com.google.protobuf.ByteString.copyFrom(data.userId().bytes()))
                .setMaxOperations(maxOperations)
                .setByteBudget(byteBudget);
        for (String source : sources) {
            request.addFromClock(Sync.OperationClock.newBuilder()
                    .setNodeId(source)
                    .setClock(data.maxOperationClock.getOrDefault(source, 0L)));
            var partial = data.retrievalTree.get(source);
            if (partial != null)
                request.addOffsets(Sync.OperationOffset.newBuilder()
                        .setNodeId(source)
                        .setOffset(partial.receivedLength));
        }
        var response = node.exchangeRemote(peerId, request.build());
        if (!response.getUserFound())
            throw new IllegalArgumentException("Remote peer does not host user " + data.userId());
        var transfers = new java.util.ArrayList<Node.Transfer>();
        for (var remote : response.getTransfersList()) {
            var id = new Node.OperationId(remote.getLamportClock(), remote.getNodeHash().toByteArray());
            transfers.add(new Node.Transfer(id, remote.getTotalLength(), remote.getOffset(),
                    remote.getBytes().toByteArray(), remote.getSignature().toByteArray(), remote.getComplete()));
        }
        data.receive(transfers, node);
        var activeNodeSet = new ActiveNodeSet();
        Node.loadActiveNodeSet(response.getActiveNodeSet(), activeNodeSet);
        mergeStaging();
        data.activeNodeSet.mergeFrom(activeNodeSet, node.nodeKey(), node::nextClock);
        data.activeNodeSet.updateLeaving(node.nodeKey(), data.maxOperationClock, node::nextClock);
        data.activeNodeSet.lastSeen.put(Node.nodeKeyFor(peerId), System.currentTimeMillis());
        node.persistState();
    }

    public record DirectoryEntry(String name, boolean directory) {
    }

    public java.util.List<DirectoryEntry> listDirectory(String path) {
        return data.fileSystem.listDirectory(path).stream()
                .map(entry -> new DirectoryEntry(entry.name(), entry.directory())).toList();
    }

    public void createDirectory(String path) {
        String parentId = data.fileSystem.resolveParent(path);
        String name = data.fileSystem.leafName(path);
        data.fileSystem.requireAvailableName(parentId, name, null);
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.CREATE_DIRECTORY)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(node.nextEntityId()))
                .setParentId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(parentId)))
                .setName(name)
                .build());
    }

    public void createFile(String path) {
        String parentId = data.fileSystem.resolveParent(path);
        String name = data.fileSystem.leafName(path);
        data.fileSystem.requireAvailableName(parentId, name, null);
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.CREATE_FILE)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(node.nextEntityId()))
                .setParentId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(parentId)))
                .setName(name)
                .build());
    }

    public byte[] readFile(String path) {
        return data.fileSystem.readFile(path);
    }

    public boolean exists(String path) {
        return data.fileSystem.exists(path);
    }

    public boolean isDirectory(String path) {
        return data.fileSystem.isDirectory(path);
    }

    public long fileSize(String path) {
        return data.fileSystem.fileSize(path);
    }

    public void writeFile(String path, long offset, byte[] bytes) {
        String entityId = data.fileSystem.resolveFile(path);
        if (offset < 0 || offset > Integer.MAX_VALUE || bytes.length > Integer.MAX_VALUE - offset)
            throw new IllegalArgumentException("File range exceeds the supported size");
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.WRITE_FILE)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(entityId)))
                .setOffset(offset)
                .setData(com.google.protobuf.ByteString.copyFrom(bytes))
                .build());
    }

    public void truncateFile(String path, long size) {
        String entityId = data.fileSystem.resolveFile(path);
        if (size < 0 || size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("File size exceeds the supported range");
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.TRUNCATE_FILE)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(entityId)))
                .setSize(size)
                .build());
    }

    public void rename(String path, String newName) {
        String entityId = data.fileSystem.resolve(path);
        String parentId = data.fileSystem.resolveParent(path);
        if (entityId.equals(java.util.HexFormat.of().formatHex(data.rootDirectoryGuid)))
            throw new IllegalArgumentException("Cannot rename the user root directory");
        data.fileSystem.requireAvailableName(parentId, newName, entityId);
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.RENAME)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(entityId)))
                .setNewName(newName)
                .build());
    }

    public void move(String path, String newPath) {
        String entityId = data.fileSystem.resolve(path);
        String newParentId = data.fileSystem.resolveParent(newPath);
        String newName = data.fileSystem.leafName(newPath);
        data.fileSystem.validateMove(entityId, newParentId, newName);
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.MOVE)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(entityId)))
                .setNewParentId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(newParentId)))
                .setNewName(newName)
                .build());
    }

    public void delete(String path) {
        String entityId = data.fileSystem.resolve(path);
        if (entityId.equals(java.util.HexFormat.of().formatHex(data.rootDirectoryGuid)))
            throw new IllegalArgumentException("Cannot delete the user root directory");
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.DELETE)
                .setEntityId(com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(entityId)))
                .build());
    }

    private void recordOperation(Sync.UserOperation operation) {
        node.recordLocalOperation(data, operation.toByteArray());
        rebuildCurrentState();
    }

    public void startLeaving(PeerId leavingPeerId) {
        var leavingNode = node.network.nodes.get(leavingPeerId);
        if (leavingNode == null)
            throw new IllegalArgumentException("Unknown peer " + leavingPeerId);
        if (leavingNode == node)
            throw new IllegalArgumentException("A node cannot initiate its own leave process");
        if (!data.activeNodeSet.nodes.contains(leavingNode.nodeKey()))
            throw new IllegalArgumentException("Peer is not active for this user");
        data.activeNodeSet.startLeaving(leavingNode.nodeKey(), UUID.randomUUID().toString(),
                data.maxOperationClock, node.nodeKey(), node::nextClock);
        node.persistState();
    }

    private void mergeStaging() {
        if (data.stagingTree.isEmpty() && data.localOperationTree.isEmpty())
            return;
        var merged = data.allOperations();
        var fileSystem = UserFileTree.replayFromState(data.snapshotState, merged.values());
        var root = StorageUserRoot.from(node.network.getBlock(data.rootId));
        var dataRoot = fileSystem.toDataUserRoot(node.storage);
        root.dataRootBlockId = node.storage.store(dataRoot.toProto());
        data.rootId = node.storage.store(root.toProto());
        data.fileSystem = fileSystem;
        data.operationTree.putAll(data.localOperationTree);
        data.operationTree.putAll(data.stagingTree);
        data.localOperationTree.clear();
        data.stagingTree.clear();
        node.persistState();
    }

    void rebuildCurrentState() {
        var fileSystem = UserFileTree.replayFromState(data.snapshotState, data.allOperations().values());
        var root = StorageUserRoot.from(node.network.getBlock(data.rootId));
        root.dataRootBlockId = node.storage.store(fileSystem.toDataUserRoot(node.storage).toProto());
        data.rootId = node.storage.store(root.toProto());
        data.fileSystem = fileSystem;
        node.persistState();
    }

    public boolean truncateStableLog() {
        Node.OperationId stable = null;
        for (String source : data.activeNodeSet.nodes) {
            var candidate = new Node.OperationId(data.maxOperationClock.getOrDefault(source, 0L),
                    java.util.HexFormat.of().parseHex(source));
            if (stable == null || candidate.compareTo(stable) < 0)
                stable = candidate;
        }
        if (stable == null)
            return false;
        var removable = new java.util.ArrayList<>(data.operationTree.headMap(stable, false).values());
        if (removable.isEmpty())
            return false;
        var snapshot = UserFileTree.replayFromState(data.snapshotState, removable);
        data.snapshotState = snapshot.serializeState();
        data.operationTree.headMap(stable, false).clear();
        rebuildCurrentState();
        return true;
    }
}