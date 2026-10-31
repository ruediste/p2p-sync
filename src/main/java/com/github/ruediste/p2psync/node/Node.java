package com.github.ruediste.p2psync.node;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.libp2p.host.HostBuilder;
import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.proto.Sync;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

public class Node implements AutoCloseable {
    private static final String USER_KEY_CLIPBOARD_PREFIX = "p2p-sync-user-key:v1:";
    public PeerId peerId;
    private PrivKey peerPrivateKey;
    public DataStorage dataStorage;
    public BlockStorage storage;
    public Map<PubKey, NodeUserData> users = new HashMap<>();
    public Network network;
    long clock;
    long guidCounter;
    private byte[] nodeHash;
    private String nodeKey;
    private DataStorage.RootRefHandle<DataStorage.ByteArrayBlock> stateRoot;
    private Path temporaryDataFile;
    private boolean restoringState;
    private Host remoteHost;
    private UserSyncProtocol remoteSyncProtocol;
    private final Map<PeerId, List<Multiaddr>> remoteAddresses = new HashMap<>();

    static final class OperationId implements Comparable<OperationId> {
        final long clock;
        final byte[] nodeHash;
        final String nodeKey;

        OperationId(long clock, byte[] nodeHash) {
            this.clock = clock;
            this.nodeHash = nodeHash.clone();
            this.nodeKey = HexFormat.of().formatHex(nodeHash);
        }

        @Override
        public int compareTo(OperationId other) {
            int result = Long.compare(clock, other.clock);
            return result != 0 ? result : Arrays.compareUnsigned(nodeHash, other.nodeHash);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OperationId other && clock == other.clock
                    && Arrays.equals(nodeHash, other.nodeHash);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(clock) + Arrays.hashCode(nodeHash);
        }
    }

    static final class Operation {
        final OperationId id;
        final byte[] payload;
        final byte[] signature;

        Operation(OperationId id, byte[] payload, byte[] signature) {
            this.id = id;
            this.payload = payload.clone();
            this.signature = signature.clone();
        }
    }

    static final class Transfer {
        final OperationId id;
        final int totalLength;
        final int offset;
        final byte[] bytes;
        final byte[] signature;
        final boolean complete;

        Transfer(OperationId id, int totalLength, int offset, byte[] bytes, byte[] signature, boolean complete) {
            this.id = id;
            this.totalLength = totalLength;
            this.offset = offset;
            this.bytes = bytes;
            this.signature = signature.clone();
            this.complete = complete;
        }
    }

    static final class PartialOperation {
        final OperationId id;
        final byte[] payload;
        final byte[] signature;
        int receivedLength;

        PartialOperation(OperationId id, int totalLength, byte[] signature) {
            this.id = id;
            this.payload = new byte[totalLength];
            this.signature = signature.clone();
        }
    }

    public Node(Network network) {
        this(network, createTemporaryDataFile(), true);
    }

    public Node(Network network, Path dataFile) {
        this(network, requireDataFile(dataFile), false);
    }

    private Node(Network network, Path dataFile, boolean temporary) {
        this.network = java.util.Objects.requireNonNull(network);
        peerPrivateKey = Ed25519PrivateKey.generateKeyPair();
        peerId = PeerId.fromPubKey(peerPrivateKey.publicKey());
        nodeHash = CryptoUtils.sha256(peerId.getBytes());
        nodeKey = HexFormat.of().formatHex(nodeHash);
        temporaryDataFile = temporary ? dataFile : null;
        dataStorage = new DataStorage();
        storage = new BlockStorage(dataStorage, 1);
        stateRoot = dataStorage.getRootRefHandle(2, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        try {
            dataStorage.open(dataFile.toString());
            storage.restoreRoot();
            if (stateRoot.blockOffset == 0) {
                network.nodes.put(peerId, this);
                persistState();
            } else {
                byte[] reference = dataStorage.readBlock(stateRoot.blockOffset, Area.DATA,
                        DataStorage.BYTE_ARRAY_BLOCK_TYPE).data();
                if (reference.length < 32)
                    throw new IllegalStateException("Invalid persisted node-state reference");
                byte[] stateBytes = storage.getBlock(new BlockId(Arrays.copyOf(reference, 32)))
                        .orElseThrow(() -> new IllegalStateException("Persisted node state block is missing"));
                restoreState(Sync.NodeState.parseFrom(stateBytes));
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            dataStorage.close();
            throw new IllegalStateException("Invalid persisted node state", e);
        } catch (RuntimeException | Error failure) {
            network.nodes.remove(peerId, this);
            dataStorage.close();
            throw failure;
        }
    }

    public synchronized List<Multiaddr> startRemote(String listenAddress, boolean discoverMdns) {
        if (remoteHost != null)
            return remoteHost.network().listenAddresses();
        remoteSyncProtocol = new UserSyncProtocol(this::handleRemoteSyncRequest);
        var builder = new HostBuilder()
                .privateKey(peerPrivateKey)
                .listenAddress(listenAddress)
                .protocolHandler(remoteSyncProtocol)
                .discoverMdns(discoverMdns);
        remoteHost = builder.build();
        remoteHost.start().join();
        return remoteHost.network().listenAddresses();
    }

    public void addRemoteAddress(PeerId peerId, Multiaddr address) {
        if (remoteHost == null)
            throw new IllegalStateException("Start remote networking before adding peer addresses");
        remoteAddresses.compute(peerId, (key, previous) -> {
            var result = new java.util.ArrayList<>(previous == null ? List.of() : previous);
            if (!result.contains(address))
                result.add(address);
            remoteHost.addressBook().addAddrs(peerId, 86_400_000L, address);
            return List.copyOf(result);
        });
    }

    public void addRemotePeer(PeerId peerId) {
        if (remoteHost == null)
            throw new IllegalStateException("Start remote networking before adding peers");
        remoteAddresses.putIfAbsent(peerId, List.of());
    }

    Sync.RemoteSyncResponse exchangeRemote(PeerId peerId, Sync.RemoteSyncRequest request) {
        if (remoteHost == null)
            throw new IllegalStateException("Remote networking has not been started");
        var addresses = remoteHost.addressBook().getAddrs(peerId);
        if (addresses.isEmpty())
            addresses = remoteAddresses.getOrDefault(peerId, List.of());
        if (addresses.isEmpty())
            throw new IllegalArgumentException("No known address for remote peer " + peerId);
        Multiaddr[] dialAddresses = addresses.toArray(Multiaddr[]::new);
        Connection connection = remoteHost.network().connections().stream()
                .filter(existing -> existing.getRemotePeerId().equals(peerId))
                .findFirst()
                .orElseGet(() -> remoteHost.network().connect(peerId, dialAddresses).join());
        try (var controller = remoteHost.newStream(List.of(remoteSyncProtocol), connection)) {
            try {
                return Sync.RemoteSyncResponse.parseFrom(controller.exchange(request.toByteArray()));
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid response from remote sync peer", e);
            }
        }
    }

    private byte[] handleRemoteSyncRequest(byte[] bytes) {
        try {
            var request = Sync.RemoteSyncRequest.parseFrom(bytes);
            PubKey userId = PubKey.from(request.getUserId().toByteArray());
            NodeUserData data = users.get(userId);
            var response = Sync.RemoteSyncResponse.newBuilder().setUserFound(data != null).setNodeClock(clock);
            if (data == null)
                return response.build().toByteArray();
            response.setActiveNodeSet(persist(data.activeNodeSet));
            data.maxOperationClock.forEach((source, clock) -> response.addMaxOperationClock(
                    Sync.OperationClock.newBuilder().setNodeId(source).setClock(clock)));
            if (request.getIncludeHistory()) {
                response.setSnapshotState(com.google.protobuf.ByteString.copyFrom(data.snapshotState));
                data.allOperations().values().forEach(operation -> response.addHistory(persist(operation)));
            } else {
                var fromClock = new TreeMap<String, Long>();
                request.getFromClockList().forEach(entry -> fromClock.put(entry.getNodeId(), entry.getClock()));
                var offsets = new HashMap<String, Integer>();
                request.getOffsetsList().forEach(entry -> offsets.put(entry.getNodeId(), entry.getOffset()));
                data.serve(fromClock, offsets, Math.max(1, request.getMaxOperations()),
                        Math.max(1, request.getByteBudget())).forEach(
                                transfer -> response.addTransfers(
                                        Sync.RemoteOperationTransfer.newBuilder()
                                                .setLamportClock(transfer.id.clock)
                                                .setNodeHash(
                                                        com.google.protobuf.ByteString.copyFrom(transfer.id.nodeHash))
                                                .setTotalLength(transfer.totalLength)
                                                .setOffset(transfer.offset)
                                                .setBytes(com.google.protobuf.ByteString.copyFrom(transfer.bytes))
                                                .setSignature(
                                                        com.google.protobuf.ByteString.copyFrom(transfer.signature))
                                                .setComplete(transfer.complete)));
            }
            if (!request.getJoiningNodeId().isEmpty()
                    && data.activeNodeSet.nodes.add(request.getJoiningNodeId())) {
                data.activeNodeSet.setVersion(nodeKey, nextClock());
                persistState();
            }
            return response.build().toByteArray();
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid remote sync request", e);
        }
    }

    private static Path createTemporaryDataFile() {
        try {
            return Files.createTempFile("p2p-sync-node-", ".db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path requireDataFile(Path dataFile) {
        if (dataFile == null)
            throw new IllegalArgumentException("A persistent data file is required");
        return dataFile;
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
        public BlockId rootId;
        public JoinId joinId;
        public PrivKey userKey;
        public String localName;
        final TreeMap<OperationId, Operation> operationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> localOperationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> stagingTree = new TreeMap<>();
        final Map<String, PartialOperation> retrievalTree = new HashMap<>();
        final Map<String, Long> maxOperationClock = new HashMap<>();
        ActiveNodeSet activeNodeSet = new ActiveNodeSet();
        byte[] rootDirectoryGuid;
        byte[] snapshotState;
        UserFileTree fileSystem;

        public PubKey userId() {
            return userKey.publicKey();
        }

        TreeMap<OperationId, Operation> allOperations() {
            var result = new TreeMap<>(operationTree);
            result.putAll(localOperationTree);
            result.putAll(stagingTree);
            return result;
        }

        Set<String> operationSources() {
            return allOperations().keySet().stream().map(id -> id.nodeKey).collect(Collectors.toSet());
        }

        java.util.List<Transfer> serve(Map<String, Long> fromClock, Map<String, Integer> offsets, int maxOperations,
                int byteBudget) {
            var result = new java.util.ArrayList<Transfer>();
            int bytesSent = 0;
            var operations = allOperations();
            for (var source : new TreeSet<>(fromClock.keySet())) {
                long clock = fromClock.get(source);
                boolean first = true;
                for (var operation : operations.values()) {
                    if (!operation.id.nodeKey.equals(source) || operation.id.clock <= clock)
                        continue;
                    if (result.size() >= maxOperations)
                        return result;
                    int offset = first ? offsets.getOrDefault(source, 0) : 0;
                    first = false;
                    if (offset > operation.payload.length)
                        throw new IllegalArgumentException("Invalid operation transfer offset");
                    int available = byteBudget - bytesSent;
                    int count = Math.min(operation.payload.length - offset, available);
                    boolean complete = offset + count == operation.payload.length;
                    result.add(new Transfer(operation.id, operation.payload.length, offset,
                            Arrays.copyOfRange(operation.payload, offset, offset + count), operation.signature,
                            complete));
                    bytesSent += count;
                    if (!complete)
                        return result;
                }
            }
            return result;
        }

        void receive(java.util.List<Transfer> transfers, Node node) {
            for (var transfer : transfers) {
                if (transfer.totalLength < 0 || transfer.offset < 0
                        || transfer.offset + transfer.bytes.length > transfer.totalLength)
                    throw new IllegalArgumentException("Invalid operation transfer");
                String source = transfer.id.nodeKey;
                if (transfer.complete) {
                    PartialOperation partial = retrievalTree.get(source);
                    byte[] payload;
                    byte[] signature;
                    if (transfer.offset == 0) {
                        payload = transfer.bytes;
                        signature = transfer.signature;
                    } else {
                        if (partial == null || !partial.id.equals(transfer.id)
                                || partial.receivedLength != transfer.offset
                                || !Arrays.equals(partial.signature, transfer.signature))
                            throw new IllegalArgumentException("Operation transfer does not continue partial data");
                        System.arraycopy(transfer.bytes, 0, partial.payload, transfer.offset, transfer.bytes.length);
                        payload = partial.payload;
                        signature = partial.signature;
                    }
                    if (payload.length != transfer.totalLength)
                        throw new IllegalArgumentException("Incomplete operation marked complete");
                    if (!userKey.publicKey().verify(operationSigningBytes(transfer.id, payload), signature))
                        throw new IllegalArgumentException("Invalid user operation signature");
                    stagingTree.putIfAbsent(transfer.id, new Operation(transfer.id, payload, signature));
                    retrievalTree.remove(source);
                    maxOperationClock.merge(source, transfer.id.clock, Math::max);
                    node.clock = Math.max(node.clock, transfer.id.clock);
                } else {
                    var partial = retrievalTree.get(source);
                    if (partial == null || !partial.id.equals(transfer.id)
                            || partial.payload.length != transfer.totalLength) {
                        if (transfer.offset != 0)
                            throw new IllegalArgumentException("Missing initial operation transfer segment");
                        partial = new PartialOperation(transfer.id, transfer.totalLength, transfer.signature);
                        retrievalTree.put(source, partial);
                    }
                    if (partial.receivedLength != transfer.offset
                            || !Arrays.equals(partial.signature, transfer.signature))
                        throw new IllegalArgumentException("Operation transfer segment is out of order");
                    System.arraycopy(transfer.bytes, 0, partial.payload, transfer.offset, transfer.bytes.length);
                    partial.receivedLength += transfer.bytes.length;
                }
            }
        }
    }

    public record UserStatus(PubKey userId, String localName, int activeNodeCount,
            int pendingLocalOperationCount, int pendingIncomingOperationCount) {
    }

    public NodeUserHandle createNewUser() {

        var userKey = Ed25519PrivateKey.generateKeyPair();
        return createNewUser(userKey, defaultUserName(userKey));
    }

    public NodeUserHandle createNewUser(String localName) {
        return createNewUser(Ed25519PrivateKey.generateKeyPair(), localName);
    }

    private NodeUserHandle createNewUser(PrivKey userKey, String localName) {
        validateLocalName(localName);
        ensureNameAvailable(localName, null);
        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.activeNodeSet.nodes.add(nodeKey);
        data.activeNodeSet.setVersion(nodeKey, nextClock());
        data.rootDirectoryGuid = nextEntityId();
        data.fileSystem = UserFileTree.empty(data.rootDirectoryGuid);
        data.snapshotState = data.fileSystem.serializeState();
        users.put(data.userId(), data);
        var handle = new NodeUserHandle(this, data);

        var root = data.fileSystem.toDataUserRoot(storage);
        var dataRootId = storage.store(root.toProto());

        var storageRoot = new StorageUserRoot();
        storageRoot.shardListId = storage.store(Sync.ShardList.newBuilder().build());
        storageRoot.dataRootBlockId = dataRootId;
        storageRoot.nodeNrMapId = storage.store(Sync.NodeNrMap.newBuilder().build());
        data.rootId = storage.store(storageRoot.toProto());
        persistState();
        return handle;
    }

    public NodeUserHandle join(PrivKey userKey) {
        return join(userKey, defaultUserName(userKey));
    }

    public NodeUserHandle join(PrivKey userKey, String localName) {
        validateLocalName(localName);
        ensureNameAvailable(localName, null);
        var userId = userKey.publicKey();
        if (users.containsKey(userId))
            throw new IllegalArgumentException("User is already configured on this node");
        var peer = network.getPeersForUser(userId).stream().min(PeerId::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("No node hosts user " + userId));
        var root = network.getStorageUserRoot(peer, userId);
        var peerNode = network.nodes.get(peer);
        var peerData = peerNode.users.get(userId);
        clock = Math.max(clock, peerNode.clock);

        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.rootDirectoryGuid = peerData.rootDirectoryGuid.clone();
        data.snapshotState = peerData.snapshotState.clone();
        data.operationTree.putAll(peerData.operationTree);
        data.stagingTree.putAll(peerData.stagingTree);
        data.stagingTree.putAll(peerData.localOperationTree);
        data.maxOperationClock.putAll(peerData.maxOperationClock);
        data.fileSystem = UserFileTree.replayFromState(data.snapshotState, data.allOperations().values());
        data.activeNodeSet = peerData.activeNodeSet.copy();
        data.activeNodeSet.nodes.add(nodeKey);
        data.activeNodeSet.setVersion(nodeKey, nextClock());
        root.shardListId = storage.store(Sync.ShardList.newBuilder().build());
        root.dataRootBlockId = storage.store(data.fileSystem.toDataUserRoot(storage).toProto());
        root.nodeNrMapId = storage.store(Sync.NodeNrMap.newBuilder().build());
        data.rootId = storage.store(root.toProto());
        users.put(userId, data);
        var handle = new NodeUserHandle(this, data);
        handle.syncFrom(peer);
        peerData.activeNodeSet.nodes.add(nodeKey);
        peerData.activeNodeSet.setVersion(peerNode.nodeKey, peerNode.nextClock());
        peerNode.persistState();
        persistState();
        return handle;
    }

    public NodeUserHandle joinRemote(PrivKey userKey, String localName, PeerId remotePeerId, Multiaddr address) {
        addRemoteAddress(remotePeerId, address);
        return joinRemote(userKey, localName, remotePeerId);
    }

    public NodeUserHandle joinRemote(PrivKey userKey, String localName) {
        if (remoteHost == null)
            throw new IllegalStateException("Start remote networking before probing for a user");
        var candidates = new TreeSet<PeerId>(PeerId::compareTo);
        candidates.addAll(remoteHost.addressBook().getPeerIds());
        candidates.addAll(remoteAddresses.keySet());
        candidates.remove(peerId);
        RuntimeException lastFailure = null;
        for (PeerId candidate : candidates) {
            try {
                var response = exchangeRemote(candidate, Sync.RemoteSyncRequest.newBuilder()
                        .setUserId(com.google.protobuf.ByteString.copyFrom(userKey.publicKey().bytes()))
                        .setIncludeHistory(true)
                        .build());
                if (response.getUserFound())
                    return joinRemote(userKey, localName, candidate);
            } catch (RuntimeException error) {
                lastFailure = error;
            }
        }
        var error = new IllegalArgumentException("No known peer hosts this user");
        if (lastFailure != null)
            error.addSuppressed(lastFailure);
        throw error;
    }

    public NodeUserHandle joinRemote(PrivKey userKey, String localName, PeerId remotePeerId) {
        validateLocalName(localName);
        ensureNameAvailable(localName, null);
        if (users.containsKey(userKey.publicKey()))
            throw new IllegalArgumentException("User is already configured on this node");
        addRemotePeer(remotePeerId);
        var response = exchangeRemote(remotePeerId, Sync.RemoteSyncRequest.newBuilder()
                .setUserId(com.google.protobuf.ByteString.copyFrom(userKey.publicKey().bytes()))
                .setIncludeHistory(true)
                .setJoiningNodeId(nodeKey)
                .build());
        if (!response.getUserFound())
            throw new IllegalArgumentException("Remote peer does not host this user");
        clock = Math.max(clock, response.getNodeClock());

        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.snapshotState = response.getSnapshotState().toByteArray();
        var snapshot = UserFileTree.fromState(data.snapshotState);
        data.rootDirectoryGuid = snapshot.rootId.clone();
        data.fileSystem = snapshot;
        for (var stored : response.getHistoryList()) {
            var id = new OperationId(stored.getLamportClock(), stored.getNodeHash().toByteArray());
            var payload = stored.getPayload().toByteArray();
            var signature = stored.getSignature().toByteArray();
            if (!userKey.publicKey().verify(operationSigningBytes(id, payload), signature))
                throw new IllegalArgumentException("Remote history contains an invalid user signature");
            data.operationTree.put(id, new Operation(id, payload, signature));
        }
        for (var entry : response.getMaxOperationClockList()) {
            data.maxOperationClock.put(entry.getNodeId(), entry.getClock());
            clock = Math.max(clock, entry.getClock());
        }
        loadActiveNodeSet(response.getActiveNodeSet(), data.activeNodeSet);
        data.activeNodeSet.nodes.add(nodeKey);
        data.activeNodeSet.setVersion(nodeKey, nextClock());
        data.fileSystem = UserFileTree.replayFromState(data.snapshotState, data.operationTree.values());
        var storageRoot = new StorageUserRoot();
        storageRoot.shardListId = storage.store(Sync.ShardList.newBuilder().build());
        storageRoot.dataRootBlockId = storage.store(data.fileSystem.toDataUserRoot(storage).toProto());
        storageRoot.nodeNrMapId = storage.store(Sync.NodeNrMap.newBuilder().build());
        data.rootId = storage.store(storageRoot.toProto());
        users.put(data.userId(), data);
        persistState();
        return new NodeUserHandle(this, data);
    }

    public void renameUser(PubKey userId, String localName) {
        validateLocalName(localName);
        var data = users.get(userId);
        if (data == null)
            throw new IllegalArgumentException("User is not configured on this node");
        ensureNameAvailable(localName, userId);
        data.localName = localName;
        persistState();
    }

    public NodeUserHandle getUserByName(String localName) {
        return users.values().stream().filter(user -> user.localName.equals(localName)).findFirst()
                .map(user -> new NodeUserHandle(this, user))
                .orElseThrow(() -> new IllegalArgumentException("Unknown local user name: " + localName));
    }

    public void exportUserKey(PubKey userId, Path credentialFile, char[] password) {
        var user = users.get(userId);
        if (user == null)
            throw new IllegalArgumentException("User is not configured on this node");
        try {
            Files.write(credentialFile, createUserCredential(user.userKey, password).toByteArray());
            try {
                Files.setPosixFilePermissions(credentialFile, java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String exportUserKeyToClipboard(PubKey userId, char[] password) {
        var user = users.get(userId);
        if (user == null)
            throw new IllegalArgumentException("User is not configured on this node");
        return USER_KEY_CLIPBOARD_PREFIX + java.util.Base64.getEncoder()
                .encodeToString(createUserCredential(user.userKey, password).toByteArray());
    }

    private static Sync.UserCredential createUserCredential(PrivKey userKey, char[] password) {
        try (var protection = new CredentialProtection(password)) {
            var credential = protection.encrypt(userKey.bytes());
            return Sync.UserCredential.newBuilder()
                    .setCiphertext(com.google.protobuf.ByteString.copyFrom(credential.ciphertext()))
                    .setSalt(com.google.protobuf.ByteString.copyFrom(credential.salt()))
                    .setIv(com.google.protobuf.ByteString.copyFrom(credential.iv()))
                    .build();
        }
    }

    public static PrivKey importUserKey(Path credentialFile, char[] password) {
        try {
            return importUserCredential(Files.readAllBytes(credentialFile), password);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user credential file", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PrivKey importUserKeyFromClipboard(String clipboardText, char[] password) {
        if (clipboardText == null || !clipboardText.startsWith(USER_KEY_CLIPBOARD_PREFIX))
            throw new IllegalArgumentException("Clipboard does not contain a p2p-sync user credential");
        try {
            byte[] credential = java.util.Base64.getDecoder()
                    .decode(clipboardText.substring(USER_KEY_CLIPBOARD_PREFIX.length()).trim());
            return importUserCredential(credential, password);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid p2p-sync clipboard credential", e);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid p2p-sync clipboard credential", e);
        }
    }

    private static PrivKey importUserCredential(byte[] bytes, char[] password)
            throws com.google.protobuf.InvalidProtocolBufferException {
        var credential = Sync.UserCredential.parseFrom(bytes);
        try (var protection = new CredentialProtection(password)) {
            byte[] key = protection.decrypt(new CredentialProtection.EncryptedCredential(
                    credential.getCiphertext().toByteArray(),
                    credential.getSalt().toByteArray(),
                    credential.getIv().toByteArray()));
            return com.github.ruediste.p2psync.libp2p.crypto.Marshaling.unmarshalPrivateKey(key);
        }
    }

    public List<UserStatus> listUsers() {
        return users.values().stream()
                .map(user -> new UserStatus(user.userId(), user.localName, user.activeNodeSet.nodes.size(),
                        user.localOperationTree.size(), user.stagingTree.size() + user.retrievalTree.size()))
                .sorted(Comparator.comparing(UserStatus::localName))
                .toList();
    }

    public void syncKnownPeers() {
        var peers = new HashSet<>(network.nodes.keySet());
        peers.addAll(remoteAddresses.keySet());
        if (remoteHost != null)
            peers.addAll(remoteHost.addressBook().getPeerIds());
        peers.remove(peerId);
        for (var user : List.copyOf(users.values())) {
            var handle = new NodeUserHandle(this, user);
            for (PeerId remotePeerId : peers) {
                if (!user.activeNodeSet.nodes.contains(nodeKeyFor(remotePeerId)))
                    continue;
                Node localPeer = network.nodes.get(remotePeerId);
                if (localPeer != null && !localPeer.users.containsKey(user.userId()))
                    continue;
                handle.syncFrom(remotePeerId);
            }
        }
    }

    private void ensureNameAvailable(String localName, PubKey exceptUser) {
        boolean used = users.values().stream()
                .anyMatch(user -> !user.userId().equals(exceptUser) && user.localName.equals(localName));
        if (used)
            throw new IllegalArgumentException("Local user name is already in use: " + localName);
    }

    private static void validateLocalName(String localName) {
        if (localName == null || localName.isBlank() || localName.equals(".") || localName.equals("..")
                || localName.indexOf('/') >= 0 || localName.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid local user name");
    }

    private static String defaultUserName(PrivKey userKey) {
        byte[] digest = CryptoUtils.sha256(userKey.publicKey().toProto().toByteArray());
        return "user-" + HexFormat.of().formatHex(digest, 0, 4);
    }

    void recordLocalOperation(NodeUserData data, byte[] payload) {
        var id = new OperationId(++clock, nodeHash);
        byte[] signature = data.userKey.sign(operationSigningBytes(id, payload));
        data.localOperationTree.put(id, new Operation(id, payload, signature));
        data.maxOperationClock.put(nodeKey, id.clock);
        persistState();
    }

    private static byte[] operationSigningBytes(OperationId id, byte[] payload) {
        return ByteBuffer.allocate(Long.BYTES + id.nodeHash.length + payload.length)
                .putLong(id.clock).put(id.nodeHash).put(payload).array();
    }

    byte[] nextEntityId() {
        return ByteBuffer.allocate(nodeHash.length + Long.BYTES)
                .put(nodeHash).putLong(++guidCounter).array();
    }

    long nextClock() {
        return ++clock;
    }

    String nodeKey() {
        return nodeKey;
    }

    static String nodeKeyFor(PeerId peerId) {
        return HexFormat.of().formatHex(CryptoUtils.sha256(peerId.getBytes()));
    }

    void persistState() {
        if (restoringState)
            return;
        var state = Sync.NodeState.newBuilder()
                .setPeerId(com.google.protobuf.ByteString.copyFrom(peerId.getBytes()))
                .setPeerPrivateKey(com.google.protobuf.ByteString.copyFrom(peerPrivateKey.bytes()))
                .setClock(clock)
                .setGuidCounter(guidCounter);
        for (var user : users.values()) {
            var storedUser = Sync.NodeUserState.newBuilder()
                    .setUserPrivateKey(com.google.protobuf.ByteString.copyFrom(user.userKey.bytes()))
                    .setLocalName(user.localName)
                    .setRootId(user.rootId.toProto())
                    .setRootDirectoryGuid(com.google.protobuf.ByteString.copyFrom(user.rootDirectoryGuid))
                    .setSnapshotState(com.google.protobuf.ByteString.copyFrom(user.snapshotState));
            user.operationTree.values().forEach(op -> storedUser.addOperationTree(persist(op)));
            user.localOperationTree.values().forEach(op -> storedUser.addLocalOperationTree(persist(op)));
            user.stagingTree.values().forEach(op -> storedUser.addStagingTree(persist(op)));
            user.maxOperationClock.forEach((source, value) -> storedUser.addMaxOperationClock(
                    Sync.OperationClock.newBuilder().setNodeId(source).setClock(value)));
            storedUser.setActiveNodeSet(persist(user.activeNodeSet));
            user.retrievalTree.forEach((source, partial) -> storedUser.addRetrievalTree(
                    Sync.PartialOperationState.newBuilder()
                            .setSourceNodeId(source)
                            .setLamportClock(partial.id.clock)
                            .setNodeHash(com.google.protobuf.ByteString.copyFrom(partial.id.nodeHash))
                            .setTotalLength(partial.payload.length)
                            .setReceivedBytes(com.google.protobuf.ByteString.copyFrom(partial.payload, 0,
                                    partial.receivedLength))
                            .setSignature(com.google.protobuf.ByteString.copyFrom(partial.signature))));
            state.addUsers(storedUser);
        }
        BlockId stateId = storage.store(state.build());
        stateRoot.write(new DataStorage.ByteArrayBlock(stateId.bytes()));
        storage.collectGarbage(collectReachableBlocks(stateId));
        storage.commit();
    }

    private Set<BlockId> collectReachableBlocks(BlockId stateId) {
        var reachable = new HashSet<BlockId>();
        reachable.add(stateId);
        for (NodeUserData user : users.values()) {
            reachable.add(user.rootId);
            var root = StorageUserRoot.from(storage.getBlock(user.rootId).orElseThrow());
            reachable.add(root.shardListId);
            reachable.add(root.nodeNrMapId);
            try {
                var shardList = Sync.ShardList.parseFrom(storage.getBlock(root.shardListId).orElseThrow());
                for (var shardId : shardList.getShardIdsList()) {
                    BlockId shardBlockId = BlockId.fromProto(shardId);
                    if (!reachable.add(shardBlockId))
                        continue;
                    var shard = Sync.StorageShard.parseFrom(storage.getBlock(shardBlockId).orElseThrow());
                    shard.getBlockIdsList().stream().map(BlockId::fromProto).forEach(reachable::add);
                }
                var dataRoot = DataUserRoot.from(storage.getBlock(root.dataRootBlockId).orElseThrow());
                reachable.add(root.dataRootBlockId);
                collectDirectoryBlocks(dataRoot.rootDirectoryId, reachable);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalStateException("Invalid block graph in node storage", e);
            }
        }
        return reachable;
    }

    private void collectDirectoryBlocks(BlockId directoryId, Set<BlockId> reachable)
            throws com.google.protobuf.InvalidProtocolBufferException {
        if (!reachable.add(directoryId))
            return;
        var directory = Sync.Directory.parseFrom(storage.getBlock(directoryId).orElseThrow());
        for (var child : directory.getDirectoriesList())
            collectDirectoryBlocks(BlockId.fromProto(child.getDirectoryId()), reachable);
        directory.getFilesList().stream().map(entry -> BlockId.fromProto(entry.getDataBlockId()))
                .forEach(reachable::add);
    }

    private static Sync.PersistedOperation persist(Operation operation) {
        return Sync.PersistedOperation.newBuilder()
                .setLamportClock(operation.id.clock)
                .setNodeHash(com.google.protobuf.ByteString.copyFrom(operation.id.nodeHash))
                .setPayload(com.google.protobuf.ByteString.copyFrom(operation.payload))
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature))
                .build();
    }

    private static Sync.ActiveNodeSetState persist(ActiveNodeSet activeNodeSet) {
        var result = Sync.ActiveNodeSetState.newBuilder();
        if (activeNodeSet.version != null)
            result.setVersionNodeId(activeNodeSet.version.nodeId).setVersionClock(activeNodeSet.version.clock);
        activeNodeSet.nodes.forEach(result::addNodeIds);
        activeNodeSet.lastSeen.forEach((id, timestamp) -> result.addLastSeen(
                Sync.ActiveNodeLastSeen.newBuilder().setNodeId(id).setTimestamp(timestamp)));
        if (activeNodeSet.leavingProcess != null) {
            var process = activeNodeSet.leavingProcess;
            var saved = Sync.LeavingProcessState.newBuilder()
                    .setPhase(process.phase.ordinal())
                    .setProcessId(process.processId)
                    .setLeavingNodeId(process.leavingNodeId)
                    .setLastOperationClock(process.lastOperationClock)
                    .setLastOperationClockSourceNode(process.lastOperationClockSourceNode);
            process.confirmedBy.forEach(saved::addConfirmedBy);
            result.setLeavingProcess(saved);
        }
        return result.build();
    }

    private void restoreState(Sync.NodeState state) {
        network.nodes.remove(peerId, this);
        peerPrivateKey = com.github.ruediste.p2psync.libp2p.crypto.Marshaling
                .unmarshalPrivateKey(state.getPeerPrivateKey().toByteArray());
        peerId = PeerId.fromPubKey(peerPrivateKey.publicKey());
        if (!peerId.equals(new PeerId(state.getPeerId().toByteArray())))
            throw new IllegalStateException("Persisted node identity does not match its public key");
        nodeHash = CryptoUtils.sha256(peerId.getBytes());
        nodeKey = HexFormat.of().formatHex(nodeHash);
        clock = state.getClock();
        guidCounter = state.getGuidCounter();
        network.nodes.put(peerId, this);
        restoringState = true;
        try {
            for (var storedUser : state.getUsersList()) {
                var user = new NodeUserData();
                user.userKey = com.github.ruediste.p2psync.libp2p.crypto.Marshaling.unmarshalPrivateKey(
                        storedUser.getUserPrivateKey().toByteArray());
                user.localName = storedUser.getLocalName();
                user.rootId = BlockId.fromProto(storedUser.getRootId());
                user.rootDirectoryGuid = storedUser.getRootDirectoryGuid().toByteArray();
                user.snapshotState = storedUser.getSnapshotState().toByteArray();
                loadOperations(storedUser.getOperationTreeList(), user.operationTree);
                loadOperations(storedUser.getLocalOperationTreeList(), user.localOperationTree);
                loadOperations(storedUser.getStagingTreeList(), user.stagingTree);
                for (var entry : storedUser.getMaxOperationClockList())
                    user.maxOperationClock.put(entry.getNodeId(), entry.getClock());
                loadActiveNodeSet(storedUser.getActiveNodeSet(), user.activeNodeSet);
                loadPartialOperations(storedUser.getRetrievalTreeList(), user.retrievalTree);
                user.fileSystem = UserFileTree.fromState(user.snapshotState);
                users.put(user.userId(), user);
                if (!user.allOperations().isEmpty())
                    new NodeUserHandle(this, user).rebuildCurrentState();
            }
        } finally {
            restoringState = false;
        }
        persistState();
    }

    private static void loadOperations(Iterable<Sync.PersistedOperation> stored,
            TreeMap<OperationId, Operation> target) {
        for (var operation : stored) {
            var id = new OperationId(operation.getLamportClock(), operation.getNodeHash().toByteArray());
            target.put(id, new Operation(id, operation.getPayload().toByteArray(),
                    operation.getSignature().toByteArray()));
        }
    }

    static void loadActiveNodeSet(Sync.ActiveNodeSetState stored, ActiveNodeSet target) {
        if (stored.getVersionClock() != 0 || !stored.getVersionNodeId().isEmpty())
            target.setVersion(stored.getVersionNodeId(), stored.getVersionClock());
        target.nodes.addAll(stored.getNodeIdsList());
        stored.getLastSeenList().forEach(value -> target.lastSeen.put(value.getNodeId(), value.getTimestamp()));
        if (stored.hasLeavingProcess()) {
            var saved = stored.getLeavingProcess();
            int phase = saved.getPhase();
            if (phase < 0 || phase >= ActiveNodeSet.Phase.values().length)
                throw new IllegalStateException("Invalid persisted leaving phase");
            var process = new ActiveNodeSet.LeavingProcess();
            process.phase = ActiveNodeSet.Phase.values()[phase];
            process.processId = saved.getProcessId();
            process.leavingNodeId = saved.getLeavingNodeId();
            process.lastOperationClock = saved.getLastOperationClock();
            process.lastOperationClockSourceNode = saved.getLastOperationClockSourceNode();
            process.confirmedBy.addAll(saved.getConfirmedByList());
            target.leavingProcess = process;
        }
    }

    private static void loadPartialOperations(Iterable<Sync.PartialOperationState> stored,
            Map<String, PartialOperation> target) {
        for (var partial : stored) {
            int totalLength = partial.getTotalLength();
            byte[] received = partial.getReceivedBytes().toByteArray();
            if (received.length > totalLength)
                throw new IllegalStateException("Invalid persisted partial operation");
            var id = new OperationId(partial.getLamportClock(), partial.getNodeHash().toByteArray());
            var value = new PartialOperation(id, totalLength, partial.getSignature().toByteArray());
            System.arraycopy(received, 0, value.payload, 0, received.length);
            value.receivedLength = received.length;
            if (!partial.getSourceNodeId().equals(id.nodeKey))
                throw new IllegalStateException("Persisted partial operation source does not match its ID");
            target.put(partial.getSourceNodeId(), value);
        }
    }

    @Override
    public void close() {
        persistState();
        if (remoteHost != null)
            remoteHost.stop().join();
        dataStorage.close();
        network.nodes.remove(peerId, this);
        if (temporaryDataFile != null) {
            try {
                Files.deleteIfExists(temporaryDataFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
