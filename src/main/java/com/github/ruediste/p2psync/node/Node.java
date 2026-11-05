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

import com.github.ruediste.p2psync.identity.NodeId;
import com.github.ruediste.p2psync.identity.UserId;
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

public class Node implements AutoCloseable {
    private static final String USER_KEY_CLIPBOARD_PREFIX = "p2p-sync-user-key:v1:";
    private static final int BLOB_ROOT_HANDLE_ID = 1;
    private static final int SYNC_UNIT_COUNTER_HANDLE_ID = 3;
    public PeerId peerId;
    private PrivKey peerPrivateKey;
    public DataStorage dataStorage;
    public BlobStorage storage;
    private UnitRootStore unitRoots;
    private DataStorage.RootLongHandle syncUnitCounter;
    public Map<UserId, NodeUserData> users = new HashMap<>();
    final Map<SyncUnitId, SyncUnitData> syncUnits = new HashMap<>();
    public Network network;
    long clock;
    private NodeId nodeId;
    private String nodeKey;
    private Path temporaryDataFile;
    private boolean restoringState;
    private Host remoteHost;
    private UserSyncProtocol remoteSyncProtocol;
    private String remoteListenAddress;
    private boolean remoteDiscoverMdns;
    private boolean remotePaused;
    private final Map<PeerId, List<Multiaddr>> remoteAddresses = new HashMap<>();

    static final class OperationId implements Comparable<OperationId> {
        final long clock;
        final NodeId nodeId;
        final String nodeKey;

        OperationId(long clock, NodeId nodeId) {
            this.clock = clock;
            this.nodeId = nodeId;
            this.nodeKey = nodeId.hex();
        }

        @Override
        public int compareTo(OperationId other) {
            int result = Long.compare(clock, other.clock);
            return result != 0 ? result : nodeId.compareTo(other.nodeId);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OperationId other && clock == other.clock
                    && nodeId.equals(other.nodeId);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(clock) + nodeId.hashCode();
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

    static final class ContentTransfer {
        final SyncUnitId unitId;
        final OperationId id;
        final PubKey author;
        final SyncUnitContentOperation.Kind kind;
        final String key;
        final int totalLength;
        final int offset;
        final byte[] bytes;
        final byte[] signature;
        final boolean complete;

        ContentTransfer(SyncUnitId unitId, OperationId id, PubKey author, SyncUnitContentOperation.Kind kind,
                String key, int totalLength, int offset, byte[] bytes, byte[] signature, boolean complete) {
            this.unitId = unitId;
            this.id = id;
            this.author = author;
            this.kind = kind;
            this.key = key;
            this.totalLength = totalLength;
            this.offset = offset;
            this.bytes = bytes.clone();
            this.signature = signature.clone();
            this.complete = complete;
        }
    }

    static final class PartialContentOperation {
        final SyncUnitId unitId;
        final OperationId id;
        final PubKey author;
        final SyncUnitContentOperation.Kind kind;
        final String key;
        final byte[] payload;
        final byte[] signature;
        int receivedLength;

        PartialContentOperation(SyncUnitId unitId, OperationId id, PubKey author,
                SyncUnitContentOperation.Kind kind, String key, int totalLength, byte[] signature) {
            this.unitId = unitId;
            this.id = id;
            this.author = author;
            this.kind = kind;
            this.key = key;
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

    /**
     * Registers the root handles of a node data file on the given storage, so
     * that read-only inspection tools can traverse its contents.
     */
    public static void registerStorageCheckHandles(DataStorage storage) {
        new BlobStorage(storage, BLOB_ROOT_HANDLE_ID);
        new UnitRootStore(storage);
        storage.getRootLongHandle(SYNC_UNIT_COUNTER_HANDLE_ID);
    }

    private Node(Network network, Path dataFile, boolean temporary) {
        this.network = java.util.Objects.requireNonNull(network);
        peerPrivateKey = Ed25519PrivateKey.generateKeyPair();
        peerId = PeerId.fromPubKey(peerPrivateKey.publicKey());
        nodeId = NodeId.fromPublicKey(peerPrivateKey.publicKey());
        nodeKey = nodeId.hex();
        temporaryDataFile = temporary ? dataFile : null;
        dataStorage = new DataStorage();
        storage = new BlobStorage(dataStorage, BLOB_ROOT_HANDLE_ID);
        unitRoots = new UnitRootStore(dataStorage);
        syncUnitCounter = dataStorage.getRootLongHandle(SYNC_UNIT_COUNTER_HANDLE_ID);
        try {
            dataStorage.open(dataFile.toString());
            storage.restoreRoot();
            unitRoots.restoreRoot();
            var stateBytes = storage.get(BlobStorage.NODE_STATE_ID);
            if (stateBytes.isEmpty()) {
                network.nodes.put(peerId, this);
                persistState();
            } else {
                restoreState(Sync.NodeStateProto.parseFrom(stateBytes.get()));
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
        remoteListenAddress = listenAddress;
        remoteDiscoverMdns = discoverMdns;
        remotePaused = false;
        return buildAndStartRemote();
    }

    private List<Multiaddr> buildAndStartRemote() {
        remoteSyncProtocol = new UserSyncProtocol(this::handleRemoteSyncRequest);
        var builder = new HostBuilder()
                .privateKey(peerPrivateKey)
                .listenAddress(remoteListenAddress)
                .protocolHandler(remoteSyncProtocol)
                .discoverMdns(remoteDiscoverMdns);
        remoteHost = builder.build();
        remoteHost.start().join();
        remoteAddresses.forEach((peer, addresses) -> addresses.forEach(
                address -> remoteHost.addressBook().addAddrs(peer, 86_400_000L, address)));
        return remoteHost.network().listenAddresses();
    }

    public synchronized void pauseRemote() {
        if (remoteListenAddress == null)
            throw new IllegalStateException("Remote networking has not been started");
        if (remotePaused)
            return;
        remotePaused = true;
        if (remoteHost != null) {
            remoteHost.stop().join();
            remoteHost = null;
        }
        remoteSyncProtocol = null;
    }

    public synchronized List<Multiaddr> resumeRemote() {
        if (remoteListenAddress == null)
            throw new IllegalStateException("Remote networking has not been started");
        if (!remotePaused)
            return remoteHost == null ? List.of() : remoteHost.network().listenAddresses();
        remotePaused = false;
        return buildAndStartRemote();
    }

    public synchronized boolean isRemotePaused() {
        return remotePaused;
    }

    public synchronized boolean isRemoteStarted() {
        return remoteListenAddress != null;
    }

    public synchronized List<Multiaddr> remoteListenAddresses() {
        return remoteHost == null ? List.of() : List.copyOf(remoteHost.network().listenAddresses());
    }

    public synchronized void addRemoteAddress(PeerId peerId, Multiaddr address) {
        if (remoteHost == null && !remotePaused)
            throw new IllegalStateException("Start remote networking before adding peer addresses");
        remoteAddresses.compute(peerId, (key, previous) -> {
            var result = new java.util.ArrayList<>(previous == null ? List.of() : previous);
            if (!result.contains(address))
                result.add(address);
            if (remoteHost != null)
                remoteHost.addressBook().addAddrs(peerId, 86_400_000L, address);
            return List.copyOf(result);
        });
    }

    public synchronized void addRemotePeer(PeerId peerId) {
        if (remoteHost == null && !remotePaused)
            throw new IllegalStateException("Start remote networking before adding peers");
        remoteAddresses.putIfAbsent(peerId, List.of());
    }

    Sync.RemoteSyncResponseProto exchangeRemote(PeerId peerId, Sync.RemoteSyncRequestProto request) {
        if (remoteHost == null)
            throw new IllegalStateException("Remote networking has not been started");
        var addresses = remoteHost.addressBook().getAddrs(peerId);
        if (addresses.isEmpty())
            addresses = remoteAddressesFor(peerId);
        if (addresses.isEmpty())
            throw new IllegalArgumentException("No known address for remote peer " + peerId);
        Multiaddr[] dialAddresses = addresses.toArray(Multiaddr[]::new);
        Connection connection = remoteHost.network().connections().stream()
                .filter(existing -> existing.secureSession().getRemoteId().equals(peerId))
                .findFirst()
                .orElseGet(() -> remoteHost.network().connect(peerId, dialAddresses).join());
        try (var controller = remoteHost.newStream(List.of(remoteSyncProtocol), connection)) {
            try {
                return Sync.RemoteSyncResponseProto.parseFrom(controller.exchange(request.toByteArray()));
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid response from remote sync peer", e);
            }
        }
    }

    private byte[] handleRemoteSyncRequest(PeerId remotePeerId, byte[] bytes) {
        Sync.RemoteSyncResponseProto response;
        synchronized (this) {
            response = buildRemoteSyncResponse(remotePeerId, bytes);
        }
        return response.toByteArray();
    }

    private Sync.RemoteSyncResponseProto buildRemoteSyncResponse(PeerId remotePeerId, byte[] bytes) {
        try {
            var request = Sync.RemoteSyncRequestProto.parseFrom(bytes);
            String remoteNodeKey = nodeKeyFor(remotePeerId);
            NodeId remoteNodeId = NodeId.fromBytes(parseNodeKey(remoteNodeKey));

            if (!request.getJoiningNodeId().isEmpty()) {
                Node.parseNodeKey(request.getJoiningNodeId());
                if (!request.getJoiningNodeId().equals(remoteNodeKey)) {
                    throw new IllegalArgumentException("joiningNodeId does not match authenticated peer");
                }
            }

            if (request.hasDirectMessage()) {
                var delivery = request.getDirectMessage();
                var senderId = UserId.fromBytes(delivery.getSenderUserId().toByteArray());
                var senderPublicKey = PubKey.from(request.getRequesterUserPublicKey().toByteArray());
                if (!senderId.equals(UserId.fromPublicKey(senderPublicKey))
                        || !hasValidRequesterSignature(request, senderPublicKey)) {
                    return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock)
                            .setDirectMessageStored(false).build();
                }
                var stored = delivery.getOperation();
                var operationId = new OperationId(stored.getLamportClock(),
                        NodeId.fromBytes(stored.getNodeId().toByteArray()));
                var operation = new Operation(operationId, stored.getPayload().toByteArray(),
                        stored.getSignature().toByteArray());
                boolean messageStored = storeIncomingMessage(operation, senderPublicKey, senderId);
                return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock)
                        .setDirectMessageStored(messageStored).build();
            }
            if (!request.getSyncUnitId().isEmpty()) {
                var id = SyncUnitId.fromBytes(request.getSyncUnitId().toByteArray());
                var requesterPublicKey = PubKey.from(request.getRequesterUserPublicKey().toByteArray());
                var requesterId = UserId.fromPublicKey(requesterPublicKey);
                if (!hasValidRequesterSignature(request, requesterPublicKey))
                    return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock).build();
                var unit = syncUnits.get(id);
                boolean knownMember = unit != null
                        && unit.authorization.members().containsKey(requesterId);
                boolean invited = unit != null
                        && unit.authorization.pendingInvitations().containsKey(requesterId);
                if (unit == null)
                    return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock).build();
                if (!knownMember && !invited)
                    return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock).setSyncUnitRevoked(true)
                            .build();
                if (request.hasSyncUnitUpdate()) {
                    try {
                        var update = deserializeSyncUnit(request.getSyncUnitUpdate());
                        if (!update.id.equals(id) || update.type != unit.type || !update.creator.equals(unit.creator))
                            throw new IllegalArgumentException("Remote SyncUnit update metadata does not match");
                        mergeSyncUnit(update, remoteNodeId);
                        unit = syncUnits.get(id);
                        knownMember = unit.authorization.members().containsKey(requesterId);
                    } catch (IllegalArgumentException | IllegalStateException invalidUpdate) {
                    }
                }
                var fromClock = new TreeMap<String, Long>();
                request.getFromClockList()
                        .forEach(entry -> fromClock.merge(entry.getNodeId(), entry.getClock(), Math::max));
                var contentOffsets = new HashMap<String, Integer>();
                request.getContentOffsetsList().forEach(
                        entry -> contentOffsets.put(entry.getNodeId() + ":" + entry.getLamportClock(),
                                entry.getOffset()));
                int contentBudget = request.getByteBudget() <= 0 ? 1_000_000 : request.getByteBudget();
                var responseUnit = persist(unit).toBuilder();
                var authorizationTransfer = unit.serveAuthorization(fromClock);
                if (authorizationTransfer != null)
                    responseUnit.setAuthorizationOperation(persistAuthOperation(
                            parseAuthOperation(authorizationTransfer.bytes)));
                var response = Sync.RemoteSyncResponseProto.newBuilder()
                        .setNodeClock(clock)
                        .setSyncUnitFound(true)
                        .setSyncUnit(responseUnit);
                if (knownMember && authorizationTransfer == null) {
                    var transfer = unit.serveContent(fromClock, contentOffsets, contentBudget);
                    if (transfer != null)
                        response.setContentTransfer(toProto(transfer));
                }
                return response.build();
            }
            UserId userId = UserId.fromBytes(request.getUserId().toByteArray());
            if (request.getUserLookup())
                return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock)
                        .setUserFound(users.containsKey(userId)).build();
            NodeUserData data = users.get(userId);
            PubKey requesterPublicKey = PubKey.from(request.getRequesterUserPublicKey().toByteArray());
            if (!userId.equals(UserId.fromPublicKey(requesterPublicKey))
                    || !hasValidRequesterSignature(request, requesterPublicKey))
                return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock).setUserFound(false).build();
            var response = Sync.RemoteSyncResponseProto.newBuilder().setUserFound(data != null).setNodeClock(clock);
            if (data == null)
                return response.build();
            if (request.getJoiningNodeId().isEmpty()
                    && !data.activeNodeSet.nodes.contains(remoteNodeId)) {
                return Sync.RemoteSyncResponseProto.newBuilder().setNodeClock(clock).setUserFound(false).build();
            }
            response.setActiveNodeSet(persist(data.activeNodeSet));
            if (data.homeSyncUnitId != null)
                response.setHomeSyncUnitId(com.google.protobuf.ByteString.copyFrom(data.homeSyncUnitId.bytes()));
            data.maxOperationClock.forEach((source, clock) -> response.addMaxOperationClock(
                    Sync.OperationClockProto.newBuilder().setNodeId(source).setClock(clock)));
            if (request.getIncludeHistory()) {
                response.addAllContacts(data.contacts.toProto());
                try {
                    var treeState = Sync.UserTreeStateProto.parseFrom(data.fileSystem.serializeState());
                    response.setFileTreeRootId(treeState.getRootId())
                            .setFileTreeStatus(treeState.getStatusMessage())
                            .addAllFileTreeEntries(treeState.getEntriesList());
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new IllegalStateException("Invalid file-tree snapshot", e);
                }
            } else {
                var fromClock = new TreeMap<String, Long>();
                request.getFromClockList().forEach(entry -> fromClock.put(entry.getNodeId(), entry.getClock()));
                var offsets = new HashMap<String, Integer>();
                request.getOffsetsList().forEach(entry -> offsets.put(entry.getNodeId(), entry.getOffset()));
                var transfer = data.serveOne(fromClock, offsets, Math.max(1, request.getByteBudget()));
                if (transfer != null)
                    response.setTransfer(Sync.RemoteOperationTransferProto.newBuilder()
                            .setLamportClock(transfer.id.clock)
                            .setNodeId(com.google.protobuf.ByteString.copyFrom(transfer.id.nodeId.bytes()))
                            .setTotalLength(transfer.totalLength)
                            .setOffset(transfer.offset)
                            .setBytes(com.google.protobuf.ByteString.copyFrom(transfer.bytes))
                            .setSignature(com.google.protobuf.ByteString.copyFrom(transfer.signature))
                            .setComplete(transfer.complete));
            }
            if (!request.getJoiningNodeId().isEmpty()
                    && data.activeNodeSet.nodes.add(NodeId.fromBytes(parseNodeKey(request.getJoiningNodeId())))) {
                data.activeNodeSet.setVersion(nodeId, nextClock());
                persistState();
            }
            return response.build();
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

    public static class Network {
        public Map<PeerId, Node> nodes = new java.util.concurrent.ConcurrentHashMap<>();

        public Set<PeerId> getPeersForUser(UserId userId) {
            return nodes.values().stream().filter(x -> x.hostsUser(userId)).map(x -> x.peerId)
                    .collect(Collectors.toSet());
        }
    }

    public synchronized boolean hostsUser(UserId userId) {
        return users.containsKey(userId);
    }

    public synchronized boolean hostsSyncUnit(SyncUnitId id) {
        return syncUnits.containsKey(id);
    }

    private synchronized List<Multiaddr> remoteAddressesFor(PeerId peerId) {
        return remoteAddresses.getOrDefault(peerId, List.of());
    }

    private synchronized List<PeerId> configuredRemotePeers() {
        return List.copyOf(remoteAddresses.keySet());
    }

    private static class UnknownSyncUnitException extends IllegalArgumentException {
        UnknownSyncUnitException(String message) {
            super(message);
        }
    }

    private static class SyncUnitRevokedException extends IllegalArgumentException {
        SyncUnitRevokedException(String message) {
            super(message);
        }
    }

    public static class NodeUserData {
        public PrivKey userKey;
        public String localName;
        final Set<String> participatingSyncUnitIds = new TreeSet<>();
        OperationTree userOperationTree;
        OperationTree userLocalOperationTree;
        OperationTree userStagingTree;
        OperationTree homeOperationTree;
        OperationTree homeLocalOperationTree;
        OperationTree homeStagingTree;
        CombinedOperationTree operationTree;
        CombinedOperationTree localOperationTree;
        CombinedOperationTree stagingTree;
        final Map<String, PartialOperation> retrievalTree = new HashMap<>();
        final Map<String, Long> maxOperationClock = new HashMap<>();
        final Set<String> deliveredMessageIds = new HashSet<>();
        final Map<String, StoredUserMessage> inboxMessages = new java.util.TreeMap<>();
        ActiveNodeSet activeNodeSet = new ActiveNodeSet();
        byte[] rootDirectoryGuid;
        byte[] snapshotState;
        UserFileTree fileSystem;
        ContactList contacts;
        long clock;
        long entityCounter;
        SyncUnitId homeSyncUnitId;
        final Map<String, String> sharedFolderLabels = new TreeMap<>();
        long nextNodeId;
        long persistedFileTreeRootOffset = -1;
        long persistedSnapshotRootOffset = -1;
        GuidMapper guidMapper;

        public UserId userId() {
            return UserId.fromPublicKey(userKey.publicKey());
        }

        public SyncUnitId syncUnitId() {
            return SyncUnitId.forUser(userId());
        }

        SyncUnitId fileUnitId() {
            return homeSyncUnitId == null ? syncUnitId() : homeSyncUnitId;
        }

        CombinedOperationTree allOperations() {
            var trees = new OperationTree[] { userOperationTree, homeOperationTree,
                    userLocalOperationTree, homeLocalOperationTree, userStagingTree, homeStagingTree };
            return new CombinedOperationTree(java.util.Arrays.stream(trees)
                    .filter(java.util.Objects::nonNull).toArray(OperationTree[]::new));
        }

        Set<String> operationSources() {
            return allOperations().keySet().stream().map(id -> id.nodeKey).collect(Collectors.toSet());
        }

        Transfer serveOne(Map<String, Long> fromClock, Map<String, Integer> offsets, int byteBudget) {
            var operations = allOperations();
            for (var source : new TreeSet<>(fromClock.keySet())) {
                long clock = fromClock.get(source);
                boolean first = true;
                for (var operation : operations.values()) {
                    if (!operation.id.nodeKey.equals(source) || operation.id.clock <= clock)
                        continue;
                    int offset = first ? offsets.getOrDefault(source, 0) : 0;
                    first = false;
                    if (offset > operation.payload.length)
                        throw new IllegalArgumentException("Invalid operation transfer offset");
                    int count = Math.min(operation.payload.length - offset, byteBudget);
                    boolean complete = offset + count == operation.payload.length;
                    if (count == 0 && !complete)
                        return null;
                    return new Transfer(operation.id, operation.payload.length, offset,
                            Arrays.copyOfRange(operation.payload, offset, offset + count), operation.signature,
                            complete);
                }
            }
            return null;
        }

        void receive(Transfer transfer, Node node) {
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
                if (!verifyOperationSignature(userKey.publicKey(), transfer.id, payload, signature))
                    throw new IllegalArgumentException("Invalid user operation signature");
                requireUserUnitModification(userKey.publicKey());
                stagingTree.putIfAbsent(transfer.id, new Operation(transfer.id, payload, signature));
                retrievalTree.remove(source);
                maxOperationClock.merge(source, transfer.id.clock, Math::max);
                clock = Math.max(clock, transfer.id.clock);
                node.clock = Math.max(node.clock, transfer.id.clock);
                node.advanceUnitClock(this, payload, transfer.id.clock);
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

    static final class StoredUserMessage {
        final String messageId;
        final OperationId operationId;
        final byte[] operationPayload;
        final byte[] signature;
        final PubKey senderPublicKey;
        final Sync.UserMessageProto message;

        StoredUserMessage(String messageId, OperationId operationId, byte[] operationPayload, byte[] signature,
                PubKey senderPublicKey, Sync.UserMessageProto message) {
            this.messageId = messageId;
            this.operationId = operationId;
            this.operationPayload = operationPayload.clone();
            this.signature = signature.clone();
            this.senderPublicKey = senderPublicKey;
            this.message = message;
        }
    }

    static final class AuthorizationOperationStore
            extends java.util.AbstractCollection<SyncUnitAuthorization.Operation> {
        private final CombinedOperationTree tree;

        AuthorizationOperationStore(OperationTree tree) {
            this.tree = new CombinedOperationTree(tree);
        }

        @Override
        public java.util.Iterator<SyncUnitAuthorization.Operation> iterator() {
            var source = tree.values().iterator();
            return new java.util.Iterator<>() {
                @Override
                public boolean hasNext() {
                    return source.hasNext();
                }

                @Override
                public SyncUnitAuthorization.Operation next() {
                    return parseAuthOperation(source.next().payload);
                }
            };
        }

        @Override
        public int size() {
            return tree.size();
        }

        @Override
        public boolean add(SyncUnitAuthorization.Operation operation) {
            var id = new OperationId(operation.lamportClock(), operation.nodeId());
            tree.put(id, new Operation(id, persistAuthOperation(operation).toByteArray(), operation.signature()));
            return true;
        }

        SyncUnitAuthorization.Operation get(OperationId id) {
            var operation = tree.get(id);
            return operation == null ? null : parseAuthOperation(operation.payload);
        }

        SyncUnitAuthorization.Operation nextAfter(OperationId id) {
            var nextId = tree.nextIdAfter(id);
            return nextId == null ? null : get(nextId);
        }

        @Override
        public void clear() {
            tree.clear();
        }

        void remove(int index) {
            int size = size();
            if (index != size - 1)
                throw new UnsupportedOperationException("Only the most recent authorization operation can be removed");
            var last = tree.lastEntry();
            if (last != null)
                tree.remove(last.getKey());
        }

        long rootOffset() {
            return tree.rootOffset();
        }
    }

    static final class ContentOperationStore extends java.util.AbstractMap<OperationId, SyncUnitContentOperation> {
        private final CombinedOperationTree tree;

        ContentOperationStore(OperationTree tree) {
            this.tree = new CombinedOperationTree(tree);
        }

        @Override
        public SyncUnitContentOperation get(Object key) {
            var operation = tree.get(key);
            return operation == null ? null : parseContentOperation(operation.payload);
        }

        @Override
        public SyncUnitContentOperation put(OperationId id, SyncUnitContentOperation operation) {
            var previous = get(id);
            tree.put(id, new Operation(id, persistContentOperation(operation).toByteArray(), operation.signature()));
            return previous;
        }

        @Override
        public SyncUnitContentOperation putIfAbsent(OperationId id, SyncUnitContentOperation operation) {
            var previous = get(id);
            if (previous == null)
                put(id, operation);
            return previous;
        }

        @Override
        public SyncUnitContentOperation remove(Object key) {
            var previous = get(key);
            if (previous != null)
                tree.remove(key);
            return previous;
        }

        @Override
        public void clear() {
            tree.clear();
        }

        @Override
        public int size() {
            return tree.size();
        }

        @Override
        public boolean isEmpty() {
            return tree.isEmpty();
        }

        Node.OperationId lastKey() {
            return tree.lastKey();
        }

        long rootOffset() {
            return tree.rootOffset();
        }

        Map.Entry<OperationId, SyncUnitContentOperation> firstEntry() {
            var entry = tree.firstEntry();
            return entry == null ? null : Map.entry(entry.getKey(), parseContentOperation(entry.getValue().payload));
        }

        @Override
        public Set<Map.Entry<OperationId, SyncUnitContentOperation>> entrySet() {
            return new java.util.AbstractSet<>() {
                @Override
                public java.util.Iterator<Map.Entry<OperationId, SyncUnitContentOperation>> iterator() {
                    var source = tree.entrySet().iterator();
                    return new java.util.Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return source.hasNext();
                        }

                        @Override
                        public Map.Entry<OperationId, SyncUnitContentOperation> next() {
                            var entry = source.next();
                            return Map.entry(entry.getKey(), parseContentOperation(entry.getValue().payload));
                        }
                    };
                }

                @Override
                public int size() {
                    return ContentOperationStore.this.size();
                }
            };
        }
    }

    static final class SyncUnitData {
        final SyncUnitId id;
        final SyncUnitAuthorization.Type type;
        final UserId creator;
        AuthorizationOperationStore authorizationOperations;
        ContentOperationStore contentOperations;
        final Map<OperationId, PartialContentOperation> partialContentOperations = new TreeMap<>();
        long clock;
        long entityCounter;
        boolean home;
        long nextNodeId;
        GuidMapper guidMapper;
        ActiveNodeSet activeNodeSet = new ActiveNodeSet();
        final Map<String, byte[]> todoItems = new TreeMap<>();
        SyncUnitAuthorization.State authorization;

        SyncUnitData(SyncUnitId id, SyncUnitAuthorization.Type type, UserId creator) {
            this.id = id;
            this.type = type;
            this.creator = creator;
            authorization = SyncUnitAuthorization.replay(id, type, creator, List.of());
        }

        SyncUnitData(SyncUnitId id, SyncUnitAuthorization.Type type, UserId creator,
                OperationTree authorizationTree, OperationTree contentTree) {
            this(id, type, creator);
            authorizationOperations = new AuthorizationOperationStore(authorizationTree);
            contentOperations = new ContentOperationStore(contentTree);
        }

        void replayAuthorization() {
            authorization = SyncUnitAuthorization.replayOrdered(id, type, creator,
                    authorizationOperations == null ? List.of() : authorizationOperations);
        }

        Transfer serveAuthorization(Map<String, Long> fromClock) {
            for (var operation : authorizationOperations) {
                String source = "a:" + operation.nodeId().hex();
                if (operation.lamportClock() <= fromClock.getOrDefault(source, 0L))
                    continue;
                var operationId = new OperationId(operation.lamportClock(), operation.nodeId());
                byte[] payload = persistAuthOperation(operation).toByteArray();
                return new Transfer(operationId, payload.length, 0, payload, operation.signature(), true);
            }
            return null;
        }

        Map<String, Long> maxAuthorizationClocks() {
            var result = new TreeMap<String, Long>();
            for (var operation : authorizationOperations)
                result.merge("a:" + operation.nodeId().hex(), operation.lamportClock(), Math::max);
            return result;
        }

        void replayContent() {
            todoItems.clear();
            var authorizationReplay = SyncUnitAuthorization.newReplay(id, type, creator);
            java.util.Iterator<SyncUnitAuthorization.Operation> authorizationIterator = authorizationOperations == null
                    ? List.<SyncUnitAuthorization.Operation>of().iterator()
                    : authorizationOperations.iterator();
            var nextAuthorization = authorizationIterator.hasNext() ? authorizationIterator.next() : null;
            if (contentOperations != null)
                for (var operation : contentOperations.values()) {
                    if (!operation.unitId().equals(id))
                        throw new IllegalArgumentException("SyncUnit content operation belongs to "
                                + operation.unitId() + ", expected " + id);
                    if (!operation.hasValidSignature())
                        throw new IllegalArgumentException("Invalid SyncUnit content operation signature");
                    var action = switch (operation.kind()) {
                        case SHARED_FILE_SET, SHARED_FILE_DELETE, SHARED_DIRECTORY_CREATE,
                                SHARED_DIRECTORY_DELETE, TODO_SET, TODO_DELETE ->
                            SyncUnitAuthorization.Action.MODIFY;
                        case CHANNEL_MESSAGE -> SyncUnitAuthorization.Action.SEND_MESSAGE;
                    };
                    if (!contentKindMatchesType(type, operation.kind()))
                        throw new IllegalArgumentException("Content operation does not match SyncUnit type");
                    var operationId = new OperationId(operation.lamportClock(), operation.nodeId());
                    while (nextAuthorization != null && compare(nextAuthorization, operationId) <= 0) {
                        authorizationReplay.apply(nextAuthorization);
                        nextAuthorization = authorizationIterator.hasNext() ? authorizationIterator.next() : null;
                    }
                    if (!authorizationReplay.isAuthorized(operation.author(), action))
                        throw new IllegalArgumentException("User is not authorized for SyncUnit content operation");
                    switch (operation.kind()) {
                        case SHARED_FILE_SET, SHARED_FILE_DELETE, SHARED_DIRECTORY_CREATE,
                                SHARED_DIRECTORY_DELETE ->
                            {
                            }
                        case TODO_SET -> todoItems.put(operation.key(), operation.payload());
                        case TODO_DELETE -> todoItems.remove(operation.key());
                        case CHANNEL_MESSAGE -> {
                        }
                    }
                }
            while (nextAuthorization != null) {
                authorizationReplay.apply(nextAuthorization);
                nextAuthorization = authorizationIterator.hasNext() ? authorizationIterator.next() : null;
            }
            authorization = authorizationReplay.state();
        }

        ContentTransfer serveContent(Map<String, Long> fromClock, Map<String, Integer> offsets,
                int byteBudget) {
            for (var entry : contentOperations.entrySet()) {
                var operationId = entry.getKey();
                var operation = entry.getValue();
                String source = "c:" + operationId.nodeId.hex();
                if (operationId.clock <= fromClock.getOrDefault(source, 0L))
                    continue;
                byte[] payload = operation.payload();
                int offset = offsets.getOrDefault(contentOffsetKey(operationId), 0);
                if (offset < 0 || offset > payload.length)
                    throw new IllegalArgumentException("Invalid SyncUnit content transfer offset");
                int count = Math.min(payload.length - offset, Math.max(0, byteBudget));
                boolean complete = offset + count == payload.length;
                if (count == 0 && !complete)
                    return null;
                return new ContentTransfer(id, operationId, operation.author(), operation.kind(),
                        operation.key(), payload.length, offset,
                        Arrays.copyOfRange(payload, offset, offset + count), operation.signature(), complete);
            }
            return null;
        }

        void receiveContent(ContentTransfer transfer) {
            if (!transfer.unitId.equals(id))
                throw new IllegalArgumentException("SyncUnit content transfer has a mismatched unit");
            if (transfer.totalLength < 0 || transfer.offset < 0
                    || transfer.offset + transfer.bytes.length > transfer.totalLength)
                throw new IllegalArgumentException("Invalid SyncUnit content transfer");
            var partial = partialContentOperations.get(transfer.id);
            if (transfer.complete) {
                byte[] payload;
                byte[] signature;
                if (transfer.offset == 0) {
                    payload = transfer.bytes.clone();
                    signature = transfer.signature.clone();
                } else {
                    if (partial == null || !partial.id.equals(transfer.id)
                            || !partial.unitId.equals(transfer.unitId)
                            || !partial.author.equals(transfer.author) || partial.kind != transfer.kind
                            || !partial.key.equals(transfer.key)
                            || partial.payload.length != transfer.totalLength
                            || partial.receivedLength != transfer.offset
                            || !Arrays.equals(partial.signature, transfer.signature))
                        throw new IllegalArgumentException(
                                "SyncUnit content transfer does not continue partial data");
                    System.arraycopy(transfer.bytes, 0, partial.payload, transfer.offset,
                            transfer.bytes.length);
                    payload = partial.payload;
                    signature = partial.signature;
                }
                if (payload.length != transfer.totalLength)
                    throw new IllegalArgumentException("Incomplete SyncUnit content operation marked complete");
                var operation = new SyncUnitContentOperation(transfer.unitId, transfer.id.clock,
                        transfer.id.nodeId, transfer.author, transfer.kind, transfer.key, payload, signature);
                if (!operation.hasValidSignature())
                    throw new IllegalArgumentException("Invalid SyncUnit content operation signature");
                var previous = contentOperations.putIfAbsent(transfer.id, operation);
                if (previous != null && !sameContentOperation(previous, operation))
                    throw new IllegalArgumentException("Conflicting SyncUnit content operations share an ID");
                partialContentOperations.remove(transfer.id);
            } else {
                if (partial == null || !partial.id.equals(transfer.id)
                        || !partial.unitId.equals(transfer.unitId)
                        || !partial.author.equals(transfer.author) || partial.kind != transfer.kind
                        || !partial.key.equals(transfer.key)
                        || partial.payload.length != transfer.totalLength) {
                    if (transfer.offset != 0)
                        throw new IllegalArgumentException("Missing initial SyncUnit content transfer segment");
                    partial = new PartialContentOperation(transfer.unitId, transfer.id, transfer.author,
                            transfer.kind, transfer.key, transfer.totalLength, transfer.signature);
                    partialContentOperations.put(transfer.id, partial);
                }
                if (partial.receivedLength != transfer.offset
                        || !Arrays.equals(partial.signature, transfer.signature))
                    throw new IllegalArgumentException("SyncUnit content transfer segment is out of order");
                System.arraycopy(transfer.bytes, 0, partial.payload, transfer.offset, transfer.bytes.length);
                partial.receivedLength += transfer.bytes.length;
            }
            if (transfer.complete)
                replayContent();
        }

        private static int compare(SyncUnitAuthorization.Operation left, OperationId right) {
            int clockOrder = Long.compare(left.lamportClock(), right.clock);
            return clockOrder != 0 ? clockOrder : left.nodeId().compareTo(right.nodeId);
        }

        private static boolean contentKindMatchesType(SyncUnitAuthorization.Type type,
                SyncUnitContentOperation.Kind kind) {
            return switch (kind) {
                case SHARED_FILE_SET, SHARED_FILE_DELETE, SHARED_DIRECTORY_CREATE,
                        SHARED_DIRECTORY_DELETE ->
                    type == SyncUnitAuthorization.Type.SHARED_FOLDER;
                case TODO_SET, TODO_DELETE -> type == SyncUnitAuthorization.Type.TODO_LIST;
                case CHANNEL_MESSAGE -> type == SyncUnitAuthorization.Type.MESSAGE_CHANNEL;
            };
        }
    }

    public record UserStatus(UserId userId, String localName, int activeNodeCount,
            int pendingLocalOperationCount, int pendingIncomingOperationCount) {
    }

    public synchronized NodeUserHandle createNewUser() {

        var userKey = Ed25519PrivateKey.generateKeyPair();
        return createNewUser(userKey, defaultUserName(userKey));
    }

    public synchronized NodeUserHandle createNewUser(String localName) {
        return createNewUser(Ed25519PrivateKey.generateKeyPair(), localName);
    }

    private synchronized NodeUserHandle createNewUser(PrivKey userKey, String localName) {
        validateLocalName(localName);
        ensureNameAvailable(localName, null);
        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.activeNodeSet.nodes.add(nodeId);
        data.activeNodeSet.setVersion(nodeId, nextClock());
        users.put(data.userId(), data);
        ensureUserSyncUnit(data);
        ensureHomeUnit(data);
        data.rootDirectoryGuid = nextEntityId(data, data.fileUnitId());
        data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
        data.fileSystem = UserFileTree.empty(dataStorage, data.guidMapper, data.rootDirectoryGuid);
        data.contacts = ContactList.empty(data.userId());
        data.snapshotState = new UserState(data.fileSystem, data.contacts).serializeState();
        var handle = new NodeUserHandle(this, data);
        persistState();
        return handle;
    }

    public synchronized NodeUserHandle join(PrivKey userKey) {
        return join(userKey, defaultUserName(userKey));
    }

    public synchronized NodeUserHandle join(PrivKey userKey, String localName) {
        validateLocalName(localName);
        ensureNameAvailable(localName, null);
        var userId = UserId.fromPublicKey(userKey.publicKey());
        if (users.containsKey(userId))
            throw new IllegalArgumentException("User is already configured on this node");
        var peer = network.getPeersForUser(userId).stream().min(PeerId::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("No node hosts user " + userId));
        var peerNode = network.nodes.get(peer);
        var peerData = peerNode.users.get(userId);
        clock = Math.max(clock, peerNode.clock);

        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.rootDirectoryGuid = peerData.rootDirectoryGuid.clone();
        data.homeSyncUnitId = peerData.homeSyncUnitId;
        initializeUserOperationTrees(data);
        data.snapshotState = UserState.transferableSnapshot(peerNode.dataStorage, peerData.guidMapper, userId,
                peerData.snapshotState);
        data.operationTree.putAll(peerData.operationTree);
        data.stagingTree.putAll(peerData.stagingTree);
        data.stagingTree.putAll(peerData.localOperationTree);
        data.maxOperationClock.putAll(peerData.maxOperationClock);
        data.clock = peerData.clock;
        data.activeNodeSet = peerData.activeNodeSet.copy();
        data.activeNodeSet.nodes.add(nodeId);
        data.activeNodeSet.setVersion(nodeId, nextClock());
        users.put(userId, data);
        ensureUserSyncUnit(data);
        ensureHomeUnit(data);
        data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
        data.snapshotState = UserState.materializeSnapshot(dataStorage, data.guidMapper, userId,
                data.snapshotState);
        var state = UserState.replayFromState(dataStorage, data.guidMapper, data.snapshotState, userId,
                data.allOperations().values());
        data.fileSystem = state.fileSystem;
        data.contacts = state.contacts;
        var handle = new NodeUserHandle(this, data);
        handle.syncFrom(peer);
        peerData.activeNodeSet.nodes.add(nodeId);
        peerData.activeNodeSet.setVersion(peerNode.nodeId, peerNode.nextClock());
        peerNode.persistState();
        persistState();
        return handle;
    }

    public synchronized NodeUserHandle joinRemote(PrivKey userKey, String localName, PeerId remotePeerId,
            Multiaddr address) {
        addRemoteAddress(remotePeerId, address);
        return joinRemote(userKey, localName, remotePeerId);
    }

    public NodeUserHandle joinRemote(PrivKey userKey, String localName) {
        if (remoteHost == null)
            throw new IllegalStateException("Start remote networking before probing for a user");
        var candidates = new TreeSet<PeerId>(PeerId::compareTo);
        candidates.addAll(remoteHost.addressBook().getPeerIds());
        candidates.addAll(configuredRemotePeers());
        candidates.remove(peerId);
        RuntimeException lastFailure = null;
        for (PeerId candidate : candidates) {
            try {
                var response = exchangeRemote(candidate, signRemoteRequest(Sync.RemoteSyncRequestProto.newBuilder()
                        .setUserId(com.google.protobuf.ByteString.copyFrom(
                                UserId.fromPublicKey(userKey.publicKey()).bytes()))
                        .setRequesterUserPublicKey(
                                com.google.protobuf.ByteString.copyFrom(userKey.publicKey().bytes()))
                        .setIncludeHistory(true)
                        .setJoiningNodeId(nodeKey)
                        .build(), userKey));
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
        NodeUserHandle handle;
        synchronized (this) {
            validateLocalName(localName);
            ensureNameAvailable(localName, null);
            if (users.containsKey(UserId.fromPublicKey(userKey.publicKey())))
                throw new IllegalArgumentException("User is already configured on this node");
        }
        addRemotePeer(remotePeerId);
        var response = exchangeRemote(remotePeerId, signRemoteRequest(Sync.RemoteSyncRequestProto.newBuilder()
                .setUserId(com.google.protobuf.ByteString.copyFrom(
                        UserId.fromPublicKey(userKey.publicKey()).bytes()))
                .setRequesterUserPublicKey(
                        com.google.protobuf.ByteString.copyFrom(userKey.publicKey().bytes()))
                .setIncludeHistory(true)
                .setJoiningNodeId(nodeKey)
                .build(), userKey));
        synchronized (this) {
            if (!response.getUserFound())
                throw new IllegalArgumentException("Remote peer does not host this user");
            clock = Math.max(clock, response.getNodeClock());

            var data = new NodeUserData();
            data.userKey = userKey;
            data.localName = localName;
            data.snapshotState = UserState.snapshotWithFileTree(response.getContactsList(),
                    response.getFileTreeRootId().toByteArray(), response.getFileTreeStatus(),
                    response.getFileTreeEntriesList());
            if (response.getFileTreeRootId().isEmpty())
                throw new IllegalArgumentException("Remote user history has no file-tree root");
            data.rootDirectoryGuid = response.getFileTreeRootId().toByteArray();
            if (!response.getHomeSyncUnitId().isEmpty())
                data.homeSyncUnitId = SyncUnitId.fromBytes(response.getHomeSyncUnitId().toByteArray());
            initializeUserOperationTrees(data);
            for (var entry : response.getMaxOperationClockList()) {
                data.maxOperationClock.put(entry.getNodeId(), 0L);
                data.clock = Math.max(data.clock, entry.getClock());
                clock = Math.max(clock, entry.getClock());
            }
            loadActiveNodeSet(response.getActiveNodeSet(), data.activeNodeSet);
            data.activeNodeSet.nodes.add(nodeId);
            data.activeNodeSet.setVersion(nodeId, nextClock());
            users.put(data.userId(), data);
            ensureUserSyncUnit(data);
            ensureHomeUnit(data);
            data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
            data.snapshotState = UserState.materializeSnapshot(dataStorage, data.guidMapper, data.userId(),
                    data.snapshotState);
            var state = UserState.replayFromState(dataStorage, data.guidMapper, data.snapshotState,
                    data.userId(), data.allOperations().values());
            data.fileSystem = state.fileSystem;
            data.contacts = state.contacts;
            persistState();
            handle = new NodeUserHandle(this, data);
        }
        while (response.getMaxOperationClockList().stream().anyMatch(entry -> {
            synchronized (this) {
                return handle.data.maxOperationClock.getOrDefault(entry.getNodeId(), 0L) < entry.getClock();
            }
        }))
            handle.syncFrom(remotePeerId);
        return handle;
    }

    public synchronized void renameUser(UserId userId, String localName) {
        validateLocalName(localName);
        var data = users.get(userId);
        if (data == null)
            throw new IllegalArgumentException("User is not configured on this node");
        ensureNameAvailable(localName, userId);
        data.localName = localName;
        persistState();
    }

    public synchronized void setSharedFolderLabel(UserId userId, SyncUnitId unitId, String label) {
        var user = requireConfiguredUser(userId);
        requireSyncUnitType(unitId, SyncUnitAuthorization.Type.SHARED_FOLDER);
        setSharedFolderLabel(user, unitId, label);
    }

    private void setSharedFolderLabel(NodeUserData user, SyncUnitId unitId, String label) {
        if (label == null || label.isBlank() || label.equals("home") || label.equals(".")
                || label.equals("..") || label.indexOf('/') >= 0
                || label.indexOf('\\') >= 0 || label.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid shared-folder label");
        var operation = Sync.UserOperationProto.newBuilder()
                .setKind(Sync.UserOperationProto.Kind.SET_SHARED_FOLDER_LABEL)
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(unitId.bytes()))
                .setName(label)
                .build();
        recordLocalOperation(user, operation.toByteArray());
        user.sharedFolderLabels.put(unitId.toString(), label);
        persistState();
    }

    public record RootEntry(String label, SyncUnitId unitId, boolean home, boolean conflict) {
    }

    public synchronized List<RootEntry> listRootEntries(UserId userId) {
        var user = requireConfiguredUser(userId);
        var result = new java.util.ArrayList<RootEntry>();
        if (user.homeSyncUnitId != null)
            result.add(new RootEntry("home", user.homeSyncUnitId, true, false));
        var byLabel = new java.util.TreeMap<String, List<SyncUnitId>>();
        user.sharedFolderLabels.forEach((unitHex, label) -> {
            if (!user.participatingSyncUnitIds.contains(unitHex))
                return;
            byLabel.computeIfAbsent(label, ignored -> new java.util.ArrayList<>())
                    .add(SyncUnitId.fromBytes(HexFormat.of().parseHex(unitHex)));
        });
        for (var entry : byLabel.entrySet()) {
            entry.getValue().sort(java.util.Comparator.naturalOrder());
            result.add(new RootEntry(entry.getKey(), entry.getValue().get(0), false,
                    entry.getValue().size() > 1));
        }
        result.sort(java.util.Comparator.comparing(RootEntry::label));
        return List.copyOf(result);
    }

    public synchronized SyncUnitId createSyncUnit(UserId creatorId, SyncUnitAuthorization.Type type) {
        if (type == SyncUnitAuthorization.Type.USER)
            throw new IllegalArgumentException("User SyncUnits are created with createNewUser");
        var creator = requireConfiguredUser(creatorId);
        SyncUnitId id;
        do {
            id = SyncUnitId.create(nodeId, ++syncUnitCounter.value);
        } while (syncUnits.containsKey(id));
        var unit = new SyncUnitData(id, type, creatorId);
        unit.activeNodeSet.nodes.add(nodeId);
        unit.activeNodeSet.setVersion(nodeId, ++unit.clock);
        syncUnits.put(id, unit);
        initializeSyncUnitOperationTrees(unit);
        ensureUnitMapper(unit);
        recordSyncUnitParticipation(creator, id, true);
        return id;
    }

    public synchronized SyncUnitAuthorization.State getSyncUnitAuthorization(SyncUnitId id) {
        return requireSyncUnit(id).authorization;
    }

    public record SyncUnitStatus(SyncUnitId id, SyncUnitAuthorization.Type type,
            SyncUnitAuthorization.Role role, boolean pendingInvitation, int memberCount,
            int pendingInvitationCount) {
    }

    public record SyncUnitMemberStatus(UserId userId, SyncUnitAuthorization.Role role, boolean pendingInvitation) {
    }

    public synchronized List<SyncUnitStatus> listSyncUnits(UserId userId) {
        var user = requireConfiguredUser(userId);
        return syncUnits.values().stream()
                .filter(unit -> !unit.home)
                .filter(unit -> user.participatingSyncUnitIds.contains(unit.id.toString())
                        || unit.authorization.pendingInvitations().containsKey(userId))
                .map(unit -> new SyncUnitStatus(unit.id, unit.type, unit.authorization.members().get(userId),
                        unit.authorization.pendingInvitations().containsKey(userId),
                        unit.authorization.members().size(), unit.authorization.pendingInvitations().size()))
                .sorted(Comparator.comparing((SyncUnitStatus status) -> status.type().ordinal())
                        .thenComparing(status -> status.id().toString()))
                .toList();
    }

    public synchronized List<SyncUnitMemberStatus> listSyncUnitMembers(SyncUnitId id) {
        var unit = requireSyncUnit(id);
        var result = new java.util.ArrayList<SyncUnitMemberStatus>();
        unit.authorization.members()
                .forEach((userId, role) -> result.add(new SyncUnitMemberStatus(userId, role, false)));
        unit.authorization.pendingInvitations()
                .forEach((userId, role) -> result.add(new SyncUnitMemberStatus(userId, role, true)));
        result.sort(Comparator.comparing(member -> HexFormat.of().formatHex(member.userId().bytes())));
        return List.copyOf(result);
    }

    public synchronized List<Sync.UserMessageProto> getInboxMessages(UserId userId) {
        return requireConfiguredUser(userId).inboxMessages.values().stream()
                .map(message -> message.message).toList();
    }

    /**
     * A direct message together with its direction and the Lamport clock of the
     * signed operation that created it. The clock gives the desktop UI a stable,
     * causal ordering for a user's conversation history.
     */
    public record UserMessageEntry(Sync.UserMessageProto message, boolean outgoing, long lamportClock) {
    }

    /**
     * Returns the complete local view of a user's direct messages: everything in
     * the inbox plus every message authored by the user, delivered or still
     * queued. This is what the chat and mail views need to show a full history,
     * unlike {@link #getOutboxMessages(PubKey)} which only contains pending items.
     */
    public synchronized List<UserMessageEntry> getUserMessageLog(UserId userId) {
        var user = requireConfiguredUser(userId);
        var result = new java.util.ArrayList<UserMessageEntry>();
        for (var stored : user.inboxMessages.values())
            result.add(new UserMessageEntry(stored.message, false, stored.operationId.clock));
        for (var operation : user.allOperations().values()) {
            var message = parseOutgoingMessage(user.userId(), operation);
            if (message != null)
                result.add(new UserMessageEntry(message, true, operation.id.clock));
        }
        result.sort(Comparator.comparingLong(UserMessageEntry::lamportClock)
                .thenComparing(entry -> entry.message().getMessageId()));
        return List.copyOf(result);
    }

    public synchronized List<Sync.UserMessageProto> getOutboxMessages(UserId userId) {
        var user = requireConfiguredUser(userId);
        var result = new java.util.TreeMap<String, Sync.UserMessageProto>();
        for (var operation : user.allOperations().values()) {
            var message = parseOutgoingMessage(user.userId(), operation);
            if (message != null && !user.deliveredMessageIds.contains(message.getMessageId()))
                result.put(message.getMessageId(), message);
        }
        return List.copyOf(result.values());
    }

    void deliverPendingMessages() {
        var peersByRecipient = new HashMap<UserId, List<PeerId>>();
        for (var sender : userSnapshot()) {
            var delivered = deliveredMessages(sender);
            for (var operation : messageOperations(sender)) {
                var message = parseOutgoingMessage(sender.userId(), operation);
                if (message == null || delivered.contains(message.getMessageId()))
                    continue;
                var recipientId = UserId.fromBytes(message.getRecipientUserId().toByteArray());
                for (var recipientNode : List.copyOf(network.nodes.values())) {
                    if (!recipientNode.hostsUser(recipientId)
                            || !recipientNode.storeIncomingMessage(operation, sender.userKey.publicKey(),
                                    sender.userId()))
                        continue;
                    markMessageDelivered(sender, message.getMessageId());
                    delivered.add(message.getMessageId());
                    for (var recipientReplica : List.copyOf(network.nodes.values()))
                        if (recipientReplica.hostsUser(recipientId))
                            recipientReplica.copyInboxMessage(recipientNode, recipientId, message.getMessageId());
                    persistState();
                    break;
                }
                if (delivered.contains(message.getMessageId()))
                    continue;
                var remotePeers = peersByRecipient.computeIfAbsent(recipientId, this::resolveRecipientPeers);
                for (var remotePeer : remotePeers) {
                    if (network.nodes.containsKey(remotePeer))
                        continue;
                    try {
                        var response = exchangeRemote(remotePeer, directMessageRequest(sender, operation));
                        if (!response.getDirectMessageStored())
                            continue;
                        markMessageDelivered(sender, message.getMessageId());
                        delivered.add(message.getMessageId());
                        persistState();
                        break;
                    } catch (RuntimeException unavailablePeer) {
                        // Keep the message queued; the next sync retries delivery.
                    }
                }
            }
        }
    }

    private List<PeerId> resolveRecipientPeers(UserId recipientId) {
        var peers = new TreeSet<PeerId>(PeerId::compareTo);
        peers.addAll(network.getPeersForUser(recipientId));
        var candidates = new TreeSet<PeerId>(PeerId::compareTo);
        candidates.addAll(configuredRemotePeers());
        if (remoteHost != null)
            candidates.addAll(remoteHost.addressBook().getPeerIds());
        candidates.remove(peerId);
        for (var candidate : candidates) {
            if (network.nodes.containsKey(candidate))
                continue;
            try {
                var response = exchangeRemote(candidate, Sync.RemoteSyncRequestProto.newBuilder()
                        .setUserLookup(true)
                        .setUserId(com.google.protobuf.ByteString.copyFrom(recipientId.bytes()))
                        .build());
                if (response.getUserFound())
                    peers.add(candidate);
            } catch (RuntimeException unavailablePeer) {
                // An unreachable peer is retried on the next delivery round.
            }
        }
        return List.copyOf(peers);
    }

    private synchronized List<NodeUserData> userSnapshot() {
        return List.copyOf(users.values());
    }

    private synchronized java.util.Collection<Operation> messageOperations(NodeUserData sender) {
        return sender.allOperations().values();
    }

    private synchronized java.util.Set<String> deliveredMessages(NodeUserData sender) {
        return new HashSet<>(sender.deliveredMessageIds);
    }

    private synchronized void markMessageDelivered(NodeUserData sender, String messageId) {
        sender.deliveredMessageIds.add(messageId);
    }

    private synchronized Sync.RemoteSyncRequestProto directMessageRequest(NodeUserData sender, Operation operation) {
        var delivery = Sync.RemoteDirectMessageDeliveryProto.newBuilder()
                .setSenderUserId(com.google.protobuf.ByteString.copyFrom(sender.userId().bytes()))
                .setOperation(persist(operation));
        return signRemoteRequest(Sync.RemoteSyncRequestProto.newBuilder()
                .setDirectMessage(delivery)
                .setRequesterUserPublicKey(com.google.protobuf.ByteString.copyFrom(sender.userKey.publicKey().bytes()))
                .build(), sender.userKey);
    }

    private synchronized boolean storeIncomingMessage(Operation operation, PubKey senderPublicKey, UserId senderId) {
        if (!senderId.equals(UserId.fromPublicKey(senderPublicKey))
                || !verifyOperationSignature(senderPublicKey, operation.id, operation.payload, operation.signature))
            throw new IllegalArgumentException("Invalid direct message operation signature");
        Sync.UserOperationProto userOperation;
        Sync.UserMessageProto message;
        try {
            userOperation = Sync.UserOperationProto.parseFrom(operation.payload);
            if (userOperation.getKind() != Sync.UserOperationProto.Kind.DIRECT_MESSAGE)
                throw new IllegalArgumentException("Operation is not a direct message");
            message = Sync.UserMessageProto.parseFrom(userOperation.getMessagePayload());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid direct message payload", e);
        }
        var recipientId = UserId.fromBytes(userOperation.getRecipientUserId().toByteArray());
        if (!message.getMessageId().equals(userOperation.getMessageId())
                || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                || !message.getRecipientUserId().equals(userOperation.getRecipientUserId()))
            throw new IllegalArgumentException("Direct message identity does not match its signed operation");
        var recipient = users.get(recipientId);
        if (recipient == null)
            return false;
        var stored = new StoredUserMessage(message.getMessageId(), operation.id, operation.payload,
                operation.signature, senderPublicKey, message);
        var previous = recipient.inboxMessages.putIfAbsent(message.getMessageId(), stored);
        if (previous != null && (!Arrays.equals(previous.operationPayload, operation.payload)
                || !Arrays.equals(previous.signature, operation.signature)))
            throw new IllegalArgumentException("Conflicting direct messages share an ID");
        if (previous == null) {
            var receipt = Sync.UserOperationProto.newBuilder()
                    .setKind(Sync.UserOperationProto.Kind.RECEIVE_MESSAGE)
                    .setMessageId(message.getMessageId())
                    .setRecipientUserId(com.google.protobuf.ByteString.copyFrom(recipientId.bytes()))
                    .setMessagePayload(userOperation.getMessagePayload())
                    .setOriginalOperationPayload(com.google.protobuf.ByteString.copyFrom(operation.payload))
                    .setOriginalOperationSignature(com.google.protobuf.ByteString.copyFrom(operation.signature))
                    .setOriginalOperationLamportClock(operation.id.clock)
                    .setOriginalOperationNodeId(com.google.protobuf.ByteString.copyFrom(operation.id.nodeId.bytes()))
                    .setOriginalOperationAuthorPublicKey(
                            com.google.protobuf.ByteString.copyFrom(senderPublicKey.bytes()))
                    .build();
            recordLocalOperation(recipient, receipt.toByteArray());
            new NodeUserHandle(this, recipient).rebuildCurrentState();
            rebuildInboxMessages(recipient);
        }
        persistState();
        return true;
    }

    synchronized void rebuildInboxMessages(NodeUserData user) {
        for (var operation : user.allOperations().values()) {
            Sync.UserOperationProto receipt;
            try {
                receipt = Sync.UserOperationProto.parseFrom(operation.payload);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
            if (receipt.getKind() != Sync.UserOperationProto.Kind.RECEIVE_MESSAGE)
                continue;
            var senderOperationId = new OperationId(receipt.getOriginalOperationLamportClock(),
                    NodeId.fromBytes(receipt.getOriginalOperationNodeId().toByteArray()));
            byte[] senderOperationPayload = receipt.getOriginalOperationPayload().toByteArray();
            byte[] senderSignature = receipt.getOriginalOperationSignature().toByteArray();
            Sync.UserMessageProto receiptMessage;
            try {
                receiptMessage = Sync.UserMessageProto.parseFrom(receipt.getMessagePayload());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid recipient message receipt", e);
            }
            var senderPublicKey = PubKey.from(receipt.getOriginalOperationAuthorPublicKey().toByteArray());
            var senderUserId = UserId.fromBytes(receiptMessage.getSenderUserId().toByteArray());
            if (!senderUserId.equals(UserId.fromPublicKey(senderPublicKey)))
                throw new IllegalArgumentException("Recipient receipt has an invalid sender identity");
            if (!verifyOperationSignature(senderPublicKey, senderOperationId, senderOperationPayload, senderSignature))
                throw new IllegalArgumentException("Recipient receipt has an invalid sender signature");
            var message = parseOutgoingMessage(senderUserId,
                    new Operation(senderOperationId, senderOperationPayload, senderSignature));
            if (message == null || !message.getMessageId().equals(receipt.getMessageId())
                    || !message.equals(receiptMessage)
                    || !message.getRecipientUserId()
                            .equals(com.google.protobuf.ByteString.copyFrom(user.userId().bytes()))
                    || !receipt.getRecipientUserId()
                            .equals(com.google.protobuf.ByteString.copyFrom(user.userId().bytes())))
                throw new IllegalArgumentException("Invalid recipient message receipt");
            var stored = new StoredUserMessage(message.getMessageId(), senderOperationId,
                    senderOperationPayload, senderSignature, senderPublicKey, message);
            var previous = user.inboxMessages.putIfAbsent(message.getMessageId(), stored);
            if (previous != null && (!Arrays.equals(previous.operationPayload, senderOperationPayload)
                    || !Arrays.equals(previous.signature, senderSignature)))
                throw new IllegalArgumentException("Conflicting direct messages share an ID");
        }
    }

    private synchronized void copyInboxMessage(Node source, UserId recipientId, String messageId) {
        var sourceUser = source.users.get(recipientId);
        var recipient = users.get(recipientId);
        if (sourceUser == null || recipient == null)
            return;
        var message = sourceUser.inboxMessages.get(messageId);
        if (message != null) {
            recipient.inboxMessages.putIfAbsent(messageId, message);
            persistState();
        }
    }

    private static Sync.UserMessageProto parseOutgoingMessage(UserId senderId, Operation operation) {
        try {
            var userOperation = Sync.UserOperationProto.parseFrom(operation.payload);
            if (userOperation.getKind() != Sync.UserOperationProto.Kind.DIRECT_MESSAGE)
                return null;
            var message = Sync.UserMessageProto.parseFrom(userOperation.getMessagePayload());
            if (message.getType() == Sync.UserMessageProto.Type.UNRECOGNIZED)
                throw new IllegalArgumentException("Unknown user message type");
            boolean invitation = message.getType() == Sync.UserMessageProto.Type.SHARED_FOLDER_INVITATION
                    || message.getType() == Sync.UserMessageProto.Type.MESSAGE_CHANNEL_INVITATION
                    || message.getType() == Sync.UserMessageProto.Type.TODO_LIST_INVITATION;
            if (userOperation.getMessageId().isBlank()
                    || !message.getMessageId().equals(userOperation.getMessageId())
                    || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                    || !message.getRecipientUserId().equals(userOperation.getRecipientUserId())
                    || invitation != (message.getInvitedSyncUnitId().size() == SyncUnitId.LENGTH))
                throw new IllegalArgumentException("Direct message identity does not match its operation");
            return message;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid direct message operation", e);
        }
    }

    public synchronized void inviteToSyncUnit(SyncUnitId id, UserId actorId, UserId inviteeId,
            SyncUnitAuthorization.Role role) {
        if (requireSyncUnit(id).home)
            throw new IllegalArgumentException("The home SyncUnit cannot be shared");
        appendSyncUnitOperation(id, actorId, SyncUnitAuthorization.Action.INVITE, inviteeId, role);
        for (var peer : List.copyOf(network.nodes.values()))
            if (peer != this && peer.users.containsKey(inviteeId))
                peer.receiveSyncUnitInvitation(syncUnits.get(id));
    }

    public synchronized void acceptSyncUnitInvitation(SyncUnitId id, UserId inviteeId) {
        var unit = requireSyncUnit(id);
        if (!unit.authorization.pendingInvitations().containsKey(inviteeId))
            throw new IllegalArgumentException("User has no pending invitation to this SyncUnit");
        appendSyncUnitOperation(id, inviteeId, SyncUnitAuthorization.Action.ACCEPT_INVITATION, inviteeId, null);
        var user = users.get(inviteeId);
        if (user != null) {
            recordSyncUnitParticipation(user, id, true);
        }
        persistState();
        for (var peer : List.copyOf(network.nodes.values())) {
            if (peer == this || !peer.users.containsKey(inviteeId))
                continue;
            peer.installSyncUnit(syncUnits.get(id), true, false);
        }
    }

    public synchronized void changeSyncUnitRole(SyncUnitId id, UserId actorId, UserId subject,
            SyncUnitAuthorization.Role role) {
        appendSyncUnitOperation(id, actorId, SyncUnitAuthorization.Action.CHANGE_ROLE, subject, role);
    }

    public synchronized void removeFromSyncUnit(SyncUnitId id, UserId actorId, UserId subject) {
        appendSyncUnitOperation(id, actorId, SyncUnitAuthorization.Action.REMOVE_MEMBER, subject, null);
        var user = users.get(subject);
        if (user != null)
            recordSyncUnitParticipation(user, id, false);
        persistState();
        for (var peer : List.copyOf(network.nodes.values())) {
            if (peer == this || !peer.users.containsKey(subject))
                continue;
            peer.recordSyncUnitParticipation(peer.users.get(subject), id, false);
            var removed = peer.syncUnits.remove(id);
            if (removed != null && removed.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
                peer.deleteSharedFolderUnit(id);
            peer.persistState();
        }
    }

    /**
     * Accepts a pending SyncUnit invitation by probing known peers for a node
     * that hosts the invitation.
     */
    public void joinSyncUnit(SyncUnitId id, UserId inviteeId) {
        synchronized (this) {
            requireConfiguredUser(inviteeId);
        }
        RuntimeException lastFailure = null;
        for (var peer : List.copyOf(network.nodes.values())) {
            if (peer == this || !peer.hostsSyncUnit(id))
                continue;
            try {
                joinSyncUnit(peer.peerId, id, inviteeId);
                return;
            } catch (RuntimeException error) {
                lastFailure = error;
            }
        }
        var candidates = new TreeSet<PeerId>(PeerId::compareTo);
        if (remoteHost != null)
            candidates.addAll(remoteHost.addressBook().getPeerIds());
        candidates.addAll(configuredRemotePeers());
        candidates.remove(peerId);
        for (PeerId candidate : candidates) {
            try {
                joinSyncUnit(candidate, id, inviteeId);
                return;
            } catch (RuntimeException error) {
                lastFailure = error;
            }
        }
        // A single node has no peer to probe, so accept an invitation it hosts
        // itself. This also covers a locally replicated invitation when all
        // remote candidates are unreachable.
        if (hostsSyncUnit(id)) {
            try {
                joinLocalSyncUnit(this, id, inviteeId);
                return;
            } catch (RuntimeException error) {
                lastFailure = error;
            }
        }
        var error = new IllegalArgumentException("No known peer hosts this SyncUnit invitation");
        if (lastFailure != null)
            error.addSuppressed(lastFailure);
        throw error;
    }

    public void joinSyncUnit(PeerId sourcePeerId, SyncUnitId id, UserId inviteeId) {
        var source = network.nodes.get(sourcePeerId);
        synchronized (this) {
            requireConfiguredUser(inviteeId);
        }
        if (source != null) {
            synchronized (this) {
                joinLocalSyncUnit(source, id, inviteeId);
            }
            return;
        }
        SyncUnitData sourceUnit = null;
        Sync.RemoteSyncResponseProto response;
        do {
            Sync.RemoteSyncRequestProto request;
            synchronized (this) {
                request = buildRemoteSyncUnitRequest(id, inviteeId, sourceUnit, null);
            }
            response = exchangeRemote(sourcePeerId, request);
            synchronized (this) {
                var result = applyRemoteSyncUnitResponse(response, sourceUnit);
                sourceUnit = result.unit();
                if (sourceUnit == null)
                    throw new UnknownSyncUnitException("Remote peer does not host an invitation to this SyncUnit");
            }
        } while (response.getSyncUnit().hasAuthorizationOperation());
        synchronized (this) {
            syncUnits.putIfAbsent(id, sourceUnit);
            var localUnit = requireSyncUnit(id);
            if (!localUnit.authorization.pendingInvitations().containsKey(inviteeId))
                throw new IllegalArgumentException("User has no pending invitation to this SyncUnit");
            localUnit.clock = Math.max(localUnit.clock, maxSyncUnitClock(sourceUnit));
            clock = Math.max(clock, maxSyncUnitClock(sourceUnit));
            acceptSyncUnitInvitation(id, inviteeId);
        }
        syncSyncUnitFrom(sourcePeerId, id);
    }

    private void joinLocalSyncUnit(Node source, SyncUnitId id, UserId inviteeId) {
        var sourceUnit = source.requireSyncUnit(id);
        receiveSyncUnitInvitation(sourceUnit);
        var localUnit = requireSyncUnit(id);
        if (!localUnit.authorization.pendingInvitations().containsKey(inviteeId))
            throw new IllegalArgumentException("User has no pending invitation to this SyncUnit");
        localUnit.clock = Math.max(localUnit.clock, maxSyncUnitClock(sourceUnit));
        clock = Math.max(clock, maxSyncUnitClock(sourceUnit));
        acceptSyncUnitInvitation(id, inviteeId);
        mergeSyncUnit(sourceUnit, source.nodeId());
    }

    public void syncSyncUnitFrom(PeerId sourcePeerId, SyncUnitId id) {
        var source = network.nodes.get(sourcePeerId);
        if (source != null) {
            synchronized (this) {
                syncLocalSyncUnit(source, id);
            }
            return;
        }
        OperationId authorizationCursor = null;
        boolean moreAuthorization;
        do {
            Sync.RemoteSyncRequestProto request;
            SyncUnitData update;
            UserId localMember;
            SyncUnitAuthorization.Operation outgoingAuthorization;
            synchronized (this) {
                update = requireSyncUnit(id);
                localMember = localMemberId(update);
                outgoingAuthorization = update.authorizationOperations.nextAfter(authorizationCursor);
                if (outgoingAuthorization != null)
                    authorizationCursor = new OperationId(outgoingAuthorization.lamportClock(),
                            outgoingAuthorization.nodeId());
                request = buildRemoteSyncUnitRequest(id, localMember, update, outgoingAuthorization);
            }
            var response = exchangeRemote(sourcePeerId, request);
            synchronized (this) {
                applyRemoteSyncUnitResponseAndMerge(sourcePeerId, id, response, update, localMember);
            }
            moreAuthorization = outgoingAuthorization != null
                    || response.getSyncUnitFound() && response.getSyncUnit().hasAuthorizationOperation()
                    || response.hasContentTransfer();
        } while (moreAuthorization);
    }

    private void syncLocalSyncUnit(Node source, SyncUnitId id) {
        var unit = requireSyncUnit(id);
        var localMember = localMemberId(unit);
        if (!unit.authorization.members().containsKey(localMember))
            throw new IllegalArgumentException("No configured user participates in this SyncUnit");
        var sourceUnit = source.syncUnits.get(id);
        if (sourceUnit == null)
            throw new UnknownSyncUnitException("Peer does not host this SyncUnit");
        mergeSyncUnit(sourceUnit, source.nodeId());
        updateUnitLeaving(unit);
    }

    private UserId localMemberId(SyncUnitData unit) {
        return users.values().stream()
                .filter(user -> unit.authorization.members().containsKey(user.userId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "No configured user participates in this SyncUnit"))
                .userId();
    }

    private void applyRemoteSyncUnitResponseAndMerge(PeerId sourcePeerId, SyncUnitId id,
            Sync.RemoteSyncResponseProto response, SyncUnitData update, UserId localMember) {
        var result = applyRemoteSyncUnitResponse(response, update);
        if (result.revoked()) {
            var removed = syncUnits.remove(id);
            if (removed != null && removed.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
                deleteSharedFolderUnit(id);
            var localUser = users.get(localMember);
            if (localUser != null)
                recordSyncUnitParticipation(localUser, id, false);
            persistState();
            throw new SyncUnitRevokedException("SyncUnit membership was revoked");
        }
        if (result.unit() == null)
            throw new UnknownSyncUnitException("Remote peer does not host this SyncUnit");
        mergeSyncUnit(result.unit(), nodeIdFor(sourcePeerId));
        var localUnit = requireSyncUnit(id);
        localUnit.partialContentOperations.keySet().removeAll(result.unit().contentOperations.keySet());
        localUnit.partialContentOperations.putAll(result.unit().partialContentOperations);
        updateUnitLeaving(localUnit);
        persistState();
    }

    /**
     * Advances a non-user SyncUnit's leaving protocol. Content operations are
     * keyed by source node, so the per-source content clock is the clock used to
     * confirm that a leaving node's operations have been retrieved.
     */
    private void updateUnitLeaving(SyncUnitData unit) {
        if (unit.home || unit.type == SyncUnitAuthorization.Type.USER)
            return;
        var clocks = new TreeMap<String, Long>();
        for (var operation : unit.contentOperations.values())
            clocks.merge(operation.nodeId().hex(), operation.lamportClock(), Math::max);
        if (unit.activeNodeSet.updateLeaving(nodeId, clocks, () -> ++unit.clock))
            clock = Math.max(clock, unit.clock);
    }

    private record RemoteSyncUnitResult(SyncUnitData unit, boolean revoked) {
    }

    private Sync.RemoteSyncRequestProto buildRemoteSyncUnitRequest(SyncUnitId id, UserId requesterId,
            SyncUnitData update, SyncUnitAuthorization.Operation authorizationOperation) {
        var requester = requireConfiguredUser(requesterId);
        var request = Sync.RemoteSyncRequestProto.newBuilder()
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(id.bytes()))
                .setRequesterUserPublicKey(
                        com.google.protobuf.ByteString.copyFrom(requester.userKey.publicKey().bytes()));
        if (update != null) {
            var storedUpdate = persist(update).toBuilder();
            if (authorizationOperation != null)
                storedUpdate.setAuthorizationOperation(persistAuthOperation(authorizationOperation));
            request.setSyncUnitUpdate(storedUpdate);
            for (var clock : maxOperationClocks(update).entrySet())
                request.addFromClock(Sync.OperationClockProto.newBuilder()
                        .setNodeId(clock.getKey()).setClock(clock.getValue()));
            for (var clock : update.maxAuthorizationClocks().entrySet())
                request.addFromClock(Sync.OperationClockProto.newBuilder()
                        .setNodeId(clock.getKey()).setClock(clock.getValue()));
            for (var partial : update.partialContentOperations.values()) {
                request.addContentOffsets(Sync.SyncUnitContentOffsetProto.newBuilder()
                        .setNodeId(partial.id.nodeId.hex())
                        .setLamportClock(partial.id.clock)
                        .setOffset(partial.receivedLength));
            }
        }
        return signRemoteRequest(request.build(), requester.userKey);
    }

    private RemoteSyncUnitResult applyRemoteSyncUnitResponse(Sync.RemoteSyncResponseProto response,
            SyncUnitData update) {
        SyncUnitData unit = response.getSyncUnitFound()
                ? deserializeSyncUnit(response.getSyncUnit())
                : null;
        if (unit != null && update != null) {
            for (var operation : update.authorizationOperations) {
                var id = new OperationId(operation.lamportClock(), operation.nodeId());
                var previous = unit.authorizationOperations.get(id);
                if (previous != null && !sameAuthorizationOperation(previous, operation))
                    throw new IllegalArgumentException("Conflicting authorization operations share an ID");
                if (previous == null)
                    unit.authorizationOperations.add(operation);
            }
            unit.replayAuthorization();
            unit.partialContentOperations.putAll(update.partialContentOperations);
        }
        if (unit != null && response.hasContentTransfer())
            unit.receiveContent(fromProto(response.getContentTransfer()));
        return new RemoteSyncUnitResult(unit, response.getSyncUnitRevoked());
    }

    private void receiveSyncUnitInvitation(SyncUnitData source) {
        var existing = syncUnits.get(source.id);
        if (existing != null)
            return;
        var invitation = copySyncUnit(source, false);
        syncUnits.put(invitation.id, invitation);
        persistState();
    }

    private void installSyncUnit(SyncUnitData source, boolean includeContent) {
        installSyncUnit(source, includeContent, includeContent);
    }

    private void installSyncUnit(SyncUnitData source, boolean includeContent, boolean includePartialContent) {
        var copy = copySyncUnit(source, includeContent, includePartialContent);
        if (!copy.id.equals(SyncUnitId.forUser(copy.creator))) {
            copy.activeNodeSet.nodes.add(nodeId);
            copy.activeNodeSet.setVersion(nodeId, ++copy.clock);
        }
        syncUnits.put(copy.id, copy);
        ensureUnitMapper(copy);
        for (var user : users.values())
            if (copy.authorization.members().containsKey(user.userId()))
                user.participatingSyncUnitIds.add(copy.id.toString());
        persistState();
    }

    private SyncUnitData copySyncUnit(SyncUnitData source, boolean includeContent) {
        return copySyncUnit(source, includeContent, includeContent);
    }

    private SyncUnitData copySyncUnit(SyncUnitData source, boolean includeContent,
            boolean includePartialContent) {
        var copy = new SyncUnitData(source.id, source.type, source.creator,
                new OperationTree(dataStorage), new OperationTree(dataStorage));
        copy.clock = source.clock;
        copy.entityCounter = source.entityCounter;
        copy.nextNodeId = source.nextNodeId;
        if (!source.id.equals(SyncUnitId.forUser(source.creator)))
            copy.activeNodeSet = source.activeNodeSet.copy();
        copy.authorizationOperations.addAll(source.authorizationOperations);
        copy.replayAuthorization();
        if (includeContent)
            copy.contentOperations.putAll(source.contentOperations);
        if (includePartialContent)
            copy.partialContentOperations.putAll(source.partialContentOperations);
        copy.replayContent();
        return copy;
    }

    private void mergeSyncUnit(SyncUnitData incoming, NodeId senderNodeId) {
        var local = syncUnits.get(incoming.id);
        if (local == null) {
            installSyncUnit(incoming, false);
            local = requireSyncUnit(incoming.id);
        }
        if (local.type != incoming.type || !local.creator.equals(incoming.creator))
            throw new IllegalArgumentException("SyncUnit identity metadata does not match");
        var validation = new SyncUnitData(local.id, local.type, local.creator,
                new OperationTree(dataStorage), new OperationTree(dataStorage));
        validation.authorizationOperations.addAll(local.authorizationOperations);
        for (var operation : incoming.authorizationOperations) {
            var id = new OperationId(operation.lamportClock(), operation.nodeId());
            var previous = validation.authorizationOperations.get(id);
            if (previous != null && !sameAuthorizationOperation(previous, operation))
                throw new IllegalArgumentException("Conflicting authorization operations share an ID");
            if (previous == null)
                validation.authorizationOperations.add(operation);
        }
        validation.replayAuthorization();
        boolean member = users.values().stream()
                .anyMatch(user -> validation.authorization.members().containsKey(user.userId()));
        if (!member)
            throw new IllegalArgumentException("This node is not a member of the SyncUnit");
        validation.contentOperations.putAll(local.contentOperations);
        for (var entry : incoming.contentOperations.entrySet()) {
            var previous = validation.contentOperations.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null && !sameContentOperation(previous, entry.getValue()))
                throw new IllegalArgumentException("Conflicting content operations share an ID");
        }
        validation.replayContent();

        for (var operation : validation.authorizationOperations) {
            var id = new OperationId(operation.lamportClock(), operation.nodeId());
            if (local.authorizationOperations.get(id) == null)
                local.authorizationOperations.add(operation);
        }
        local.replayAuthorization();
        for (var entry : validation.contentOperations.entrySet()) {
            if (local.contentOperations.putIfAbsent(entry.getKey(), entry.getValue()) == null
                    && local.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
                applySharedFolderContent(entry.getValue());
        }
        local.replayContent();
        local.clock = Math.max(local.clock, incoming.clock);
        var targetUnit = local;
        if (!targetUnit.id.equals(SyncUnitId.forUser(targetUnit.creator)))
            targetUnit.activeNodeSet.mergeFrom(incoming.activeNodeSet, nodeId, senderNodeId,
                    () -> ++targetUnit.clock);
        clock = Math.max(clock, maxSyncUnitClock(incoming));
        installSyncUnit(local, true, true);
    }

    private static Map<String, Long> maxOperationClocks(SyncUnitData unit) {
        var result = new TreeMap<String, Long>();
        for (var operation : unit.contentOperations.values())
            result.merge("c:" + operation.nodeId().hex(), operation.lamportClock(), Math::max);
        return result;
    }

    private static long maxSyncUnitClock(SyncUnitData unit) {
        long result = 0;
        for (var operation : unit.authorizationOperations)
            result = Math.max(result, operation.lamportClock());
        if (!unit.contentOperations.isEmpty())
            result = Math.max(result, unit.contentOperations.lastKey().clock);
        return result;
    }

    private static boolean sameAuthorizationOperation(SyncUnitAuthorization.Operation left,
            SyncUnitAuthorization.Operation right) {
        return left.unitId().equals(right.unitId()) && left.author().equals(right.author())
                && left.action() == right.action() && java.util.Objects.equals(left.subject(), right.subject())
                && left.role() == right.role() && Arrays.equals(left.signature(), right.signature());
    }

    private static boolean sameContentOperation(SyncUnitContentOperation left,
            SyncUnitContentOperation right) {
        return left.unitId().equals(right.unitId()) && left.author().equals(right.author())
                && left.kind() == right.kind() && left.key().equals(right.key())
                && Arrays.equals(left.payload(), right.payload()) && Arrays.equals(left.signature(), right.signature());
    }

    public synchronized void putSharedFile(SyncUnitId id, UserId authorId, String path, byte[] content) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_FILE_SET,
                validateSyncUnitKey(path), content);
    }

    public synchronized byte[] readSharedFile(SyncUnitId id, UserId readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        return SharedFolderPersistence.readFile(dataStorage, unitRoots, unit.guidMapper, unit.id,
                validateSyncUnitKey(path));
    }

    public synchronized void forEachSharedFile(SyncUnitId id, UserId readerId,
            java.util.function.BiConsumer<String, Long> consumer) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        SharedFolderPersistence.forEachFile(dataStorage, unitRoots, unit.guidMapper, unit.id, consumer);
    }

    public List<SharedFileInfo> listSharedFileInfos(SyncUnitId id, UserId readerId) {
        var result = new java.util.ArrayList<SharedFileInfo>();
        forEachSharedFile(id, readerId, (path, size) -> result.add(new SharedFileInfo(path, size)));
        result.sort(Comparator.comparing(SharedFileInfo::path));
        return List.copyOf(result);
    }

    synchronized long sharedFileSize(SyncUnitId id, UserId readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        return SharedFolderPersistence.fileSize(dataStorage, unitRoots, unit.guidMapper, unit.id,
                validateSyncUnitKey(path));
    }

    public synchronized SharedEntry sharedEntry(SyncUnitId id, UserId readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        var stat = SharedFolderPersistence.stat(dataStorage, unitRoots, unit.guidMapper, unit.id,
                validateSyncUnitKey(path));
        return stat == null ? null : new SharedEntry(stat.directory(), stat.size());
    }

    public synchronized List<SharedEntryInfo> listSharedDirectory(SyncUnitId id, UserId readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        var entries = SharedFolderPersistence.listDirectory(dataStorage, unitRoots, unit.guidMapper, unit.id,
                path);
        if (entries == null)
            return null;
        return entries.stream()
                .map(entry -> new SharedEntryInfo(entry.path(), entry.directory(), entry.size()))
                .toList();
    }

    public synchronized List<SharedEntryInfo> listSharedEntries(SyncUnitId id, UserId readerId) {
        var result = new java.util.ArrayList<SharedEntryInfo>();
        forEachSharedEntry(id, readerId, result::add);
        result.sort(Comparator.comparing(SharedEntryInfo::path));
        return List.copyOf(result);
    }

    public synchronized void forEachSharedEntry(SyncUnitId id, UserId readerId,
            java.util.function.Consumer<SharedEntryInfo> consumer) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureUnitMapper(unit);
        SharedFolderPersistence.forEachEntry(dataStorage, unitRoots, unit.guidMapper, unit.id,
                entry -> consumer.accept(new SharedEntryInfo(entry.path(), entry.directory(), entry.size())));
    }

    public record SharedFileInfo(String path, long size) {
    }

    public record SharedEntry(boolean directory, long size) {
    }

    public record SharedEntryInfo(String path, boolean directory, long size) {
    }

    private void applySharedFolderContent(SyncUnitContentOperation operation) {
        if (operation.kind() != SyncUnitContentOperation.Kind.SHARED_FILE_SET
                && operation.kind() != SyncUnitContentOperation.Kind.SHARED_FILE_DELETE
                && operation.kind() != SyncUnitContentOperation.Kind.SHARED_DIRECTORY_CREATE
                && operation.kind() != SyncUnitContentOperation.Kind.SHARED_DIRECTORY_DELETE)
            return;
        var unit = requireSyncUnit(operation.unitId());
        ensureUnitMapper(unit);
        switch (operation.kind()) {
            case SHARED_FILE_SET -> SharedFolderPersistence.putFile(dataStorage, unitRoots, unit.guidMapper,
                    unit.id, operation.key(), operation.payload());
            case SHARED_FILE_DELETE -> SharedFolderPersistence.deleteFile(dataStorage, unitRoots,
                    unit.guidMapper, unit.id, operation.key());
            case SHARED_DIRECTORY_CREATE -> SharedFolderPersistence.createDirectory(dataStorage, unitRoots,
                    unit.guidMapper, unit.id, operation.key());
            case SHARED_DIRECTORY_DELETE -> SharedFolderPersistence.deleteDirectory(dataStorage, unitRoots,
                    unit.guidMapper, unit.id, operation.key());
            default -> throw new IllegalStateException("Not a shared-folder content operation");
        }
    }

    private void deleteSharedFolderUnit(SyncUnitId id) {
        var unit = syncUnits.get(id);
        GuidMapper mapper;
        if (unit != null) {
            ensureUnitMapper(unit);
            mapper = unit.guidMapper;
        } else {
            // The unit may already have been removed from the registry. Its persisted
            // GUID mapping is still available, which is all deleting the tree needs.
            mapper = new GuidMapper(dataStorage, unitRoots, id, 0);
        }
        SharedFolderPersistence.deleteUnit(dataStorage, unitRoots, mapper, id);
    }

    public synchronized void createSharedDirectory(SyncUnitId id, UserId authorId, String path) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_DIRECTORY_CREATE,
                validateSyncUnitKey(path), new byte[0]);
    }

    public synchronized void deleteSharedDirectory(SyncUnitId id, UserId authorId, String path) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_DIRECTORY_DELETE,
                validateSyncUnitKey(path), new byte[0]);
    }

    public synchronized void deleteSharedFile(SyncUnitId id, UserId authorId, String path) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_FILE_DELETE,
                validateSyncUnitKey(path), new byte[0]);
    }

    public synchronized void putTodoItem(SyncUnitId id, UserId authorId, String itemId, byte[] content) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.TODO_SET,
                validateSyncUnitKey(itemId), content);
    }

    public synchronized byte[] readTodoItem(SyncUnitId id, UserId readerId, String itemId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        byte[] content = unit.todoItems.get(validateSyncUnitKey(itemId));
        return content == null ? null : content.clone();
    }

    public synchronized Map<String, byte[]> listTodoItems(SyncUnitId id, UserId readerId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        var result = new TreeMap<String, byte[]>();
        unit.todoItems.forEach((itemId, content) -> result.put(itemId, content.clone()));
        return java.util.Collections.unmodifiableMap(result);
    }

    public synchronized void deleteTodoItem(SyncUnitId id, UserId authorId, String itemId) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.TODO_DELETE,
                validateSyncUnitKey(itemId), new byte[0]);
    }

    public synchronized String sendChannelMessage(SyncUnitId id, UserId authorId, Sync.SyncUnitMessageProto message) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        String messageId = java.util.UUID.randomUUID().toString();
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.CHANNEL_MESSAGE,
                messageId, message.toByteArray());
        return messageId;
    }

    public synchronized List<ChannelMessageEntry> getChannelMessages(SyncUnitId id, UserId readerId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        return unit.contentOperations.values().stream()
                .filter(operation -> operation.kind() == SyncUnitContentOperation.Kind.CHANNEL_MESSAGE)
                .map(operation -> {
                    try {
                        return new ChannelMessageEntry(operation.key(), UserId.fromPublicKey(operation.author()),
                                operation.lamportClock(),
                                Sync.SyncUnitMessageProto.parseFrom(operation.payload()));
                    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                        throw new IllegalStateException("Invalid persisted channel message", e);
                    }
                }).sorted(Comparator.comparingLong(ChannelMessageEntry::lamportClock)
                        .thenComparing(ChannelMessageEntry::messageId))
                .toList();
    }

    public record ChannelMessageEntry(String messageId, UserId author, long lamportClock,
            Sync.SyncUnitMessageProto message) {
    }

    private SyncUnitContentOperation recordSyncUnitContent(SyncUnitId id, UserId authorId,
            SyncUnitContentOperation.Kind kind, String key, byte[] payload) {
        var unit = requireSyncUnit(id);
        var author = requireConfiguredUser(authorId);
        var action = switch (kind) {
            case SHARED_FILE_SET, SHARED_FILE_DELETE, SHARED_DIRECTORY_CREATE,
                    SHARED_DIRECTORY_DELETE, TODO_SET, TODO_DELETE ->
                SyncUnitAuthorization.Action.MODIFY;
            case CHANNEL_MESSAGE -> SyncUnitAuthorization.Action.SEND_MESSAGE;
        };
        requireSyncUnitAccess(unit, authorId, action);
        var operation = SyncUnitContentOperation.sign(id, ++unit.clock, nodeId, author.userKey,
                kind, key, payload);
        unit.contentOperations.put(new OperationId(operation.lamportClock(), operation.nodeId()), operation);
        unit.replayContent();
        if (unit.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
            applySharedFolderContent(operation);
        persistState();
        return operation;
    }

    private void requireSyncUnitAccess(SyncUnitData unit, UserId userId, SyncUnitAuthorization.Action action) {
        var user = requireConfiguredUser(userId);
        SyncUnitAuthorization.requireAuthorized(unit.type, unit.creator, unit.authorization.members(),
                user.userKey.publicKey(), action);
    }

    private SyncUnitData requireSyncUnitType(SyncUnitId id, SyncUnitAuthorization.Type type) {
        var unit = requireSyncUnit(id);
        if (unit.type != type)
            throw new IllegalArgumentException("Expected " + type + " SyncUnit, got " + unit.type);
        return unit;
    }

    private static String validateSyncUnitKey(String key) {
        if (key == null || key.isBlank() || key.indexOf('\0') >= 0)
            throw new IllegalArgumentException("SyncUnit item key must not be blank or contain NUL");
        for (String component : key.split("/"))
            if (component.equals(".") || component.equals(".."))
                throw new IllegalArgumentException("SyncUnit item key must not traverse directories");
        return key;
    }

    private SyncUnitAuthorization.Operation appendSyncUnitOperation(SyncUnitId id, UserId actorId,
            SyncUnitAuthorization.Action action, UserId subject, SyncUnitAuthorization.Role role) {
        var unit = requireSyncUnit(id);
        var actor = requireConfiguredUser(actorId);
        var operation = SyncUnitAuthorization.Operation.sign(id, ++unit.clock, nodeId, actor.userKey,
                action, subject, role);
        unit.authorizationOperations.add(operation);
        unit.replayAuthorization();
        if (!unit.authorization.lastOperationAccepted()) {
            unit.authorizationOperations.remove(unit.authorizationOperations.size() - 1);
            unit.replayAuthorization();
            throw new IllegalArgumentException("User is not authorized to " + action + " this " + unit.type);
        }
        persistState();
        return operation;
    }

    private NodeUserData requireConfiguredUser(UserId userId) {
        var user = users.get(userId);
        if (user == null)
            throw new IllegalArgumentException("User is not configured on this node");
        return user;
    }

    private SyncUnitData requireSyncUnit(SyncUnitId id) {
        var unit = syncUnits.get(id);
        if (unit == null)
            throw new IllegalArgumentException("Unknown SyncUnit " + id);
        return unit;
    }

    private void ensureHomeUnit(NodeUserData user) {
        if (user.homeSyncUnitId == null) {
            var id = SyncUnitId.create(nodeId, ++syncUnitCounter.value);
            user.homeSyncUnitId = id;
        }
        var id = user.homeSyncUnitId;
        var unit = syncUnits.get(id);
        if (unit == null) {
            unit = new SyncUnitData(id, SyncUnitAuthorization.Type.SHARED_FOLDER, user.userId());
            unit.home = true;
            // The home unit has its own clock, counter and membership. It starts as a
            // copy of the user's state because the same nodes host the user's home.
            unit.activeNodeSet = user.activeNodeSet.copy();
            unit.clock = user.clock;
            unit.entityCounter = user.entityCounter;
            syncUnits.put(id, unit);
        }
        unit.home = true;
        unit.entityCounter = Math.max(unit.entityCounter, user.entityCounter);
        initializeSyncUnitOperationTrees(unit);
        user.participatingSyncUnitIds.add(id.toString());
        initializeUserOperationTrees(user);
    }

    private void initializeSyncUnitOperationTrees(SyncUnitData unit) {
        if (unit.authorizationOperations == null)
            unit.authorizationOperations = new AuthorizationOperationStore(new OperationTree(dataStorage,
                    unitRoots.get(unit.id, UnitRootStore.AUTHORIZATION_OPERATIONS)));
        if (unit.contentOperations == null)
            unit.contentOperations = new ContentOperationStore(new OperationTree(dataStorage,
                    unitRoots.get(unit.id, UnitRootStore.CONTENT_OPERATIONS)));
    }

    private void initializeUserOperationTrees(NodeUserData user) {
        var userUnit = user.syncUnitId();
        if (user.userOperationTree == null) {
            user.userOperationTree = new OperationTree(dataStorage, unitRoots.get(userUnit,
                    UnitRootStore.ACCEPTED_OPERATIONS));
            user.userLocalOperationTree = new OperationTree(dataStorage, unitRoots.get(userUnit,
                    UnitRootStore.LOCAL_OPERATIONS));
            user.userStagingTree = new OperationTree(dataStorage, unitRoots.get(userUnit,
                    UnitRootStore.STAGING_OPERATIONS));
        }
        if (user.homeSyncUnitId != null && user.homeOperationTree == null) {
            var homeUnit = user.homeSyncUnitId;
            user.homeOperationTree = new OperationTree(dataStorage, unitRoots.get(homeUnit,
                    UnitRootStore.ACCEPTED_OPERATIONS));
            user.homeLocalOperationTree = new OperationTree(dataStorage, unitRoots.get(homeUnit,
                    UnitRootStore.LOCAL_OPERATIONS));
            user.homeStagingTree = new OperationTree(dataStorage, unitRoots.get(homeUnit,
                    UnitRootStore.STAGING_OPERATIONS));
        }
        if (user.homeOperationTree != null) {
            user.operationTree = new CombinedOperationTree(user.userOperationTree, user.homeOperationTree);
            user.localOperationTree = new CombinedOperationTree(user.userLocalOperationTree,
                    user.homeLocalOperationTree);
            user.stagingTree = new CombinedOperationTree(user.userStagingTree, user.homeStagingTree);
        }
    }

    private void ensureUnitMapper(SyncUnitData unit) {
        if (unit.type != SyncUnitAuthorization.Type.SHARED_FOLDER || unit.home || unit.guidMapper != null)
            return;
        unit.guidMapper = new GuidMapper(dataStorage, unitRoots, unit.id, unit.nextNodeId);
    }

    private void ensureUserSyncUnit(NodeUserData user) {
        var id = user.syncUnitId();
        var unit = syncUnits.computeIfAbsent(id,
                ignored -> new SyncUnitData(id, SyncUnitAuthorization.Type.USER, user.userId()));
        unit.clock = Math.max(unit.clock, user.clock);
        unit.entityCounter = Math.max(unit.entityCounter, user.entityCounter);
        unit.activeNodeSet = user.activeNodeSet;
        initializeSyncUnitOperationTrees(unit);
        user.participatingSyncUnitIds.add(id.toString());
        initializeUserOperationTrees(user);
        applyParticipationOperations(user);
    }

    /**
     * Mirrors a locally started leave process into the home unit, which keeps its
     * own active-node set. User synchronization carries the home membership.
     */
    synchronized void mirrorStartLeaving(NodeUserData user, NodeId leavingNodeId, String processId) {
        if (user.homeSyncUnitId == null)
            return;
        var home = syncUnits.get(user.homeSyncUnitId);
        if (home == null || home.activeNodeSet == user.activeNodeSet)
            return;
        if (home.activeNodeSet.startLeaving(leavingNodeId, processId, user.maxOperationClock, nodeId,
                () -> ++home.clock))
            clock = Math.max(clock, home.clock);
    }

    /**
     * Applies a user active-node-set exchange to the separately tracked home unit.
     */
    synchronized void syncHomeActiveNodeSet(NodeUserData user, ActiveNodeSet remote, NodeId senderNodeId) {
        if (user.homeSyncUnitId == null)
            return;
        var home = syncUnits.get(user.homeSyncUnitId);
        if (home == null || home.activeNodeSet == user.activeNodeSet)
            return;
        home.activeNodeSet.mergeFrom(remote, nodeId, senderNodeId, () -> ++home.clock);
        home.activeNodeSet.updateLeaving(nodeId, user.maxOperationClock, () -> ++home.clock);
        clock = Math.max(clock, home.clock);
    }

    synchronized void applyParticipationOperations(NodeUserData user) {
        for (var operation : user.allOperations().values()) {
            try {
                var userOperation = Sync.UserOperationProto.parseFrom(operation.payload);
                if (userOperation.getKind() == Sync.UserOperationProto.Kind.SET_SHARED_FOLDER_LABEL) {
                    var unitIdBytes = userOperation.getSyncUnitId().toByteArray();
                    if (unitIdBytes.length != SyncUnitId.LENGTH || userOperation.getName().isBlank())
                        throw new IllegalArgumentException("Invalid shared-folder label operation");
                    var label = userOperation.getName();
                    if (label.equals("home"))
                        throw new IllegalArgumentException("The label 'home' is reserved");
                    user.sharedFolderLabels.put(SyncUnitId.fromBytes(unitIdBytes).toString(), label);
                    continue;
                }
                if (userOperation.getKind() != Sync.UserOperationProto.Kind.PARTICIPATE_SYNC_UNIT
                        && userOperation.getKind() != Sync.UserOperationProto.Kind.LEAVE_SYNC_UNIT)
                    continue;
                var unitIdBytes = userOperation.getSyncUnitId().toByteArray();
                if (unitIdBytes.length != SyncUnitId.LENGTH)
                    throw new IllegalArgumentException("Invalid SyncUnit participation operation");
                var unitId = SyncUnitId.fromBytes(unitIdBytes).toString();
                if (userOperation.getKind() == Sync.UserOperationProto.Kind.PARTICIPATE_SYNC_UNIT)
                    user.participatingSyncUnitIds.add(unitId);
                else
                    user.participatingSyncUnitIds.remove(unitId);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        user.participatingSyncUnitIds.add(user.syncUnitId().toString());
    }

    private void recordSyncUnitParticipation(NodeUserData user, SyncUnitId id, boolean participating) {
        var operation = Sync.UserOperationProto.newBuilder()
                .setKind(participating ? Sync.UserOperationProto.Kind.PARTICIPATE_SYNC_UNIT
                        : Sync.UserOperationProto.Kind.LEAVE_SYNC_UNIT)
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(id.bytes()))
                .build();
        recordLocalOperation(user, operation.toByteArray());
        if (participating)
            user.participatingSyncUnitIds.add(id.toString());
        else
            user.participatingSyncUnitIds.remove(id.toString());
    }

    public synchronized NodeUserHandle getUserByName(String localName) {
        return users.values().stream().filter(user -> user.localName.equals(localName)).findFirst()
                .map(user -> new NodeUserHandle(this, user))
                .orElseThrow(() -> new IllegalArgumentException("Unknown local user name: " + localName));
    }

    public synchronized NodeUserHandle getUser(UserId userId) {
        return new NodeUserHandle(this, requireConfiguredUser(userId));
    }

    public void exportUserKey(UserId userId, Path credentialFile, char[] password) {
        PrivKey userKey;
        synchronized (this) {
            var user = users.get(userId);
            if (user == null)
                throw new IllegalArgumentException("User is not configured on this node");
            userKey = user.userKey;
        }
        try {
            Files.write(credentialFile, createUserCredential(userKey, password).toByteArray());
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

    public String exportUserKeyToClipboard(UserId userId, char[] password) {
        PrivKey userKey;
        synchronized (this) {
            var user = users.get(userId);
            if (user == null)
                throw new IllegalArgumentException("User is not configured on this node");
            userKey = user.userKey;
        }
        return USER_KEY_CLIPBOARD_PREFIX + java.util.Base64.getEncoder()
                .encodeToString(createUserCredential(userKey, password).toByteArray());
    }

    private static Sync.UserCredentialProto createUserCredential(PrivKey userKey, char[] password) {
        try (var protection = new CredentialProtection(password)) {
            var credential = protection.encrypt(userKey.bytes());
            return Sync.UserCredentialProto.newBuilder()
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
        var credential = Sync.UserCredentialProto.parseFrom(bytes);
        try (var protection = new CredentialProtection(password)) {
            byte[] key = protection.decrypt(new CredentialProtection.EncryptedCredential(
                    credential.getCiphertext().toByteArray(),
                    credential.getSalt().toByteArray(),
                    credential.getIv().toByteArray()));
            return com.github.ruediste.p2psync.libp2p.crypto.Marshaling.unmarshalPrivateKey(key);
        }
    }

    public synchronized List<UserStatus> listUsers() {
        return users.values().stream()
                .map(user -> new UserStatus(user.userId(), user.localName, user.activeNodeSet.nodes.size(),
                        user.localOperationTree.size(), user.stagingTree.size() + user.retrievalTree.size()))
                .sorted(Comparator.comparing(UserStatus::localName))
                .toList();
    }

    public void syncKnownPeers() {
        record UserPeerSync(NodeUserData user, PeerId peerId) {
        }
        record UnitPeerSync(SyncUnitId unitId, PeerId peerId) {
        }
        var peers = new TreeSet<PeerId>(PeerId::compareTo);
        peers.addAll(network.nodes.keySet());
        peers.addAll(configuredRemotePeers());
        if (remoteHost != null)
            peers.addAll(remoteHost.addressBook().getPeerIds());
        peers.remove(peerId);

        var userWork = new java.util.ArrayList<UserPeerSync>();
        var unitWork = new java.util.ArrayList<UnitPeerSync>();
        synchronized (this) {
            for (var user : users.values()) {
                for (PeerId remotePeerId : peers) {
                    if (user.activeNodeSet.nodes.contains(nodeIdFor(remotePeerId)))
                        userWork.add(new UserPeerSync(user, remotePeerId));
                }
            }
            for (var user : users.values()) {
                for (String unitIdHex : user.participatingSyncUnitIds) {
                    var unitId = SyncUnitId.fromBytes(HexFormat.of().parseHex(unitIdHex));
                    var unit = syncUnits.get(unitId);
                    if (unit == null || unit.type == SyncUnitAuthorization.Type.USER || unit.home)
                        continue;
                    for (PeerId remotePeerId : peers) {
                        if (!shouldSyncUnitWithPeer(unit, nodeIdFor(remotePeerId)))
                            continue;
                        unitWork.add(new UnitPeerSync(unitId, remotePeerId));
                    }
                }
            }
        }

        for (var work : userWork) {
            Node localPeer = network.nodes.get(work.peerId());
            if (localPeer != null && !localPeer.hostsUser(work.user().userId()))
                continue;
            new NodeUserHandle(this, work.user()).syncFrom(work.peerId());
        }
        for (var work : unitWork) {
            Node localPeer = network.nodes.get(work.peerId());
            if (localPeer != null && !localPeer.hostsSyncUnit(work.unitId()))
                continue;
            try {
                syncSyncUnitFrom(work.peerId(), work.unitId());
            } catch (UnknownSyncUnitException | SyncUnitRevokedException ignored) {
            }
        }
        deliverPendingMessages();
    }

    /**
     * Decides whether a non-user SyncUnit should synchronize with a peer. While a
     * unit knows only its own node it is bootstrapping and probes every reachable
     * peer to discover the remaining members. Once the active node set contains
     * another node, only those nodes are synchronized.
     */
    private static boolean shouldSyncUnitWithPeer(SyncUnitData unit, NodeId remoteNodeId) {
        return unit.activeNodeSet.nodes.size() <= 1
                || unit.activeNodeSet.nodes.contains(remoteNodeId);
    }

    private void ensureNameAvailable(String localName, UserId exceptUser) {
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

    synchronized void recordLocalOperation(NodeUserData data, byte[] payload) {
        requireUserUnitModification(data.userKey.publicKey());
        var unit = unitForOperation(data, payload);
        long next = Math.max(data.clock, unit == null ? 0L : unit.clock) + 1;
        data.clock = next;
        if (unit != null)
            unit.clock = next;
        clock = Math.max(clock, data.clock);
        var id = new OperationId(next, nodeId);
        byte[] signature = data.userKey.sign(operationSigningBytes(id, payload));
        data.localOperationTree.put(id, new Operation(id, payload, signature));
        data.maxOperationClock.put(nodeKey, id.clock);
        persistState();
    }

    /**
     * Returns the SyncUnit that owns an operation: file-tree operations belong to
     * the user's home unit, all other user operations to the USER unit.
     */
    private SyncUnitData unitForOperation(NodeUserData user, byte[] payload) {
        boolean fileOperation;
        try {
            fileOperation = switch (Sync.UserOperationProto.parseFrom(payload).getKind()) {
                case SET_STATUS, CREATE_DIRECTORY, CREATE_FILE, WRITE_FILE, TRUNCATE_FILE, MOVE, RENAME,
                        DELETE ->
                    true;
                default -> false;
            };
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            fileOperation = false;
        }
        return syncUnits.get(fileOperation ? user.fileUnitId() : user.syncUnitId());
    }

    /** Advances the owning unit's Lamport clock after receiving an operation. */
    synchronized void advanceUnitClock(NodeUserData user, byte[] payload, long operationClock) {
        var unit = unitForOperation(user, payload);
        if (unit == null)
            return;
        if (operationClock > unit.clock)
            unit.clock = operationClock;
        clock = Math.max(clock, unit.clock);
    }

    private static void requireUserUnitModification(PubKey author) {
        SyncUnitAuthorization.requireAuthorized(SyncUnitAuthorization.Type.USER, UserId.fromPublicKey(author), Map.of(),
                author,
                SyncUnitAuthorization.Action.MODIFY);
    }

    private static byte[] operationSigningBytes(OperationId id, byte[] payload) {
        return ByteBuffer.allocate(Long.BYTES + NodeId.LENGTH + payload.length)
                .putLong(id.clock).put(id.nodeId.bytes()).put(payload).array();
    }

    private static boolean verifyOperationSignature(PubKey author, OperationId id, byte[] payload,
            byte[] signature) {
        try {
            return author.verify(operationSigningBytes(id, payload), signature);
        } catch (RuntimeException invalidSignature) {
            return false;
        }
    }

    synchronized byte[] nextEntityId(NodeUserData user) {
        return nextEntityId(user, user.syncUnitId());
    }

    synchronized byte[] nextEntityId(NodeUserData user, SyncUnitId unitId) {
        var unit = syncUnits.get(unitId);
        if (unit == null)
            throw new IllegalArgumentException("Unknown SyncUnit " + unitId);
        long counter = ++unit.entityCounter;
        user.entityCounter = Math.max(user.entityCounter, counter);
        return ByteBuffer.allocate(NodeId.LENGTH + Long.BYTES)
                .put(nodeId.bytes()).putLong(counter).array();
    }

    synchronized long nextClock() {
        return ++clock;
    }

    NodeId nodeId() {
        return nodeId;
    }

    String nodeKey() {
        return nodeKey;
    }

    NodeId nodeIdFor(PeerId peerId) {
        Node inMemoryPeer = network.nodes.get(peerId);
        if (inMemoryPeer != null)
            return inMemoryPeer.nodeId;
        if (remoteHost != null) {
            return remoteHost.network().connections().stream()
                    .filter(connection -> connection.secureSession().getRemoteId().equals(peerId))
                    .map(connection -> NodeId.fromPublicKey(connection.secureSession().getRemotePubKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No authenticated NodeId for peer " + peerId));
        }
        throw new IllegalArgumentException("No authenticated NodeId for peer " + peerId);
    }

    String nodeKeyFor(PeerId peerId) {
        return nodeIdFor(peerId).hex();
    }

    /** Parses a 32-byte NodeId encoded as hex. */
    static byte[] parseNodeKey(String nodeKey) {
        if (nodeKey == null || nodeKey.length() != 2 * 32)
            throw new IllegalArgumentException("Invalid node key length");
        return HexFormat.of().parseHex(nodeKey);
    }

    static Sync.RemoteSyncRequestProto signRemoteRequest(Sync.RemoteSyncRequestProto request, PrivKey key) {
        var unsigned = request.toBuilder().clearRequesterSignature().build();
        var signature = key.sign(unsigned.toByteArray());
        return unsigned.toBuilder()
                .setRequesterSignature(com.google.protobuf.ByteString.copyFrom(signature))
                .build();
    }

    private static boolean hasValidRequesterSignature(Sync.RemoteSyncRequestProto request, PubKey requester) {
        if (request.getRequesterSignature().isEmpty())
            return false;
        try {
            var unsigned = request.toBuilder().clearRequesterSignature().build();
            return requester.verify(unsigned.toByteArray(), request.getRequesterSignature().toByteArray());
        } catch (RuntimeException invalidSignature) {
            return false;
        }
    }

    private void persistUserFileTrees() {
        for (var user : users.values()) {
            if (user.guidMapper == null)
                user.guidMapper = new GuidMapper(dataStorage, unitRoots, user.fileUnitId(), user.nextNodeId);
            long fileTreeRoot = user.fileSystem.rootOffset();
            if (fileTreeRoot != user.persistedFileTreeRootOffset) {
                unitRoots.put(user.fileUnitId(), UnitRootStore.FILE_TREE, fileTreeRoot);
                user.persistedFileTreeRootOffset = fileTreeRoot;
            }
            long snapshotRoot = UserState.snapshotRootOffset(user.snapshotState);
            if (snapshotRoot != user.persistedSnapshotRootOffset) {
                unitRoots.put(user.fileUnitId(), UnitRootStore.SNAPSHOT_FILE_TREE, snapshotRoot);
                user.persistedSnapshotRootOffset = snapshotRoot;
            }
            user.nextNodeId = user.guidMapper.nextNodeId();
            user.guidMapper.flush();
        }
        for (var unit : syncUnits.values()) {
            if (unit.guidMapper == null)
                continue;
            unit.nextNodeId = unit.guidMapper.nextNodeId();
            unit.guidMapper.flush();
        }
    }

    private static Sync.UserOperationProto parseUserOperation(Operation operation) {
        try {
            return Sync.UserOperationProto.parseFrom(operation.payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user operation payload", e);
        }
    }

    private void persistUserOperationTrees() {
        for (var user : users.values()) {
            unitRoots.put(user.syncUnitId(), UnitRootStore.ACCEPTED_OPERATIONS,
                    user.userOperationTree.rootOffset());
            unitRoots.put(user.syncUnitId(), UnitRootStore.LOCAL_OPERATIONS,
                    user.userLocalOperationTree.rootOffset());
            unitRoots.put(user.syncUnitId(), UnitRootStore.STAGING_OPERATIONS,
                    user.userStagingTree.rootOffset());
            if (user.homeSyncUnitId != null) {
                unitRoots.put(user.homeSyncUnitId, UnitRootStore.ACCEPTED_OPERATIONS,
                        user.homeOperationTree.rootOffset());
                unitRoots.put(user.homeSyncUnitId, UnitRootStore.LOCAL_OPERATIONS,
                        user.homeLocalOperationTree.rootOffset());
                unitRoots.put(user.homeSyncUnitId, UnitRootStore.STAGING_OPERATIONS,
                        user.homeStagingTree.rootOffset());
            }
        }
    }

    private void persistSyncUnitOperationTrees() {
        for (var unit : syncUnits.values()) {
            unitRoots.put(unit.id, UnitRootStore.AUTHORIZATION_OPERATIONS, unit.authorizationOperations.rootOffset());
            unitRoots.put(unit.id, UnitRootStore.CONTENT_OPERATIONS, unit.contentOperations.rootOffset());
        }
    }

    private void loadSyncUnitOperationTrees(SyncUnitData unit) {
        initializeSyncUnitOperationTrees(unit);
        unit.replayAuthorization();
        unit.replayContent();
    }

    private void loadUserOperationTrees(NodeUserData user) {
        var unitId = user.syncUnitId();
        var author = user.userKey.publicKey();
        loadOperationTree(unitId, UnitRootStore.ACCEPTED_OPERATIONS, author, user.userOperationTree);
        loadOperationTree(unitId, UnitRootStore.LOCAL_OPERATIONS, author, user.userLocalOperationTree);
        loadOperationTree(unitId, UnitRootStore.STAGING_OPERATIONS, author, user.userStagingTree);
        if (user.homeSyncUnitId != null) {
            loadOperationTree(user.homeSyncUnitId, UnitRootStore.ACCEPTED_OPERATIONS, author,
                    user.homeOperationTree);
            loadOperationTree(user.homeSyncUnitId, UnitRootStore.LOCAL_OPERATIONS, author,
                    user.homeLocalOperationTree);
            loadOperationTree(user.homeSyncUnitId, UnitRootStore.STAGING_OPERATIONS, author,
                    user.homeStagingTree);
        }
    }

    private void loadOperationTree(SyncUnitId unitId, int kind, PubKey author, OperationTree target) {
        for (var operation : target.operations()) {
            if (!verifyOperationSignature(author, operation.id, operation.payload, operation.signature))
                throw new IllegalStateException("Persisted user operation has an invalid signature");
        }
    }

    synchronized void persistState() {
        if (restoringState)
            return;
        persistUserFileTrees();
        var state = Sync.NodeStateProto.newBuilder()
                .setNodeId(com.google.protobuf.ByteString.copyFrom(nodeId.bytes()))
                .setPeerPrivateKey(com.google.protobuf.ByteString.copyFrom(peerPrivateKey.bytes()))
                .setClock(clock);
        for (var user : users.values()) {
            var storedUser = Sync.NodeUserStateProto.newBuilder()
                    .setUserPrivateKey(com.google.protobuf.ByteString.copyFrom(user.userKey.bytes()))
                    .setLocalName(user.localName)
                    .setRootDirectoryGuid(com.google.protobuf.ByteString.copyFrom(user.rootDirectoryGuid))
                    .setSnapshotState(com.google.protobuf.ByteString.copyFrom(user.snapshotState))
                    .setNextNodeId(user.nextNodeId)
                    .setClock(user.clock)
                    .setEntityCounter(user.entityCounter)
                    .setHomeSyncUnitId(user.homeSyncUnitId == null ? com.google.protobuf.ByteString.EMPTY
                            : com.google.protobuf.ByteString.copyFrom(user.homeSyncUnitId.bytes()));
            user.sharedFolderLabels.forEach((unitId, label) -> storedUser.addSharedFolderLabels(
                    Sync.SharedFolderLabelProto.newBuilder()
                            .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(unitId)))
                            .setLabel(label)));
            user.maxOperationClock.forEach((source, value) -> storedUser.addMaxOperationClock(
                    Sync.OperationClockProto.newBuilder().setNodeId(source).setClock(value)));
            storedUser.setActiveNodeSet(persist(user.activeNodeSet));
            user.retrievalTree.forEach((source, partial) -> storedUser.addRetrievalTree(
                    Sync.PartialOperationStateProto.newBuilder()
                            .setSourceNodeId(source)
                            .setLamportClock(partial.id.clock)
                            .setNodeId(com.google.protobuf.ByteString.copyFrom(partial.id.nodeId.bytes()))
                            .setTotalLength(partial.payload.length)
                            .setReceivedBytes(com.google.protobuf.ByteString.copyFrom(partial.payload, 0,
                                    partial.receivedLength))
                            .setSignature(com.google.protobuf.ByteString.copyFrom(partial.signature))));
            user.participatingSyncUnitIds.forEach(id -> storedUser.addParticipatingSyncUnitIds(
                    com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(id))));
            user.deliveredMessageIds.forEach(storedUser::addDeliveredMessageIds);
            user.inboxMessages.values().forEach(message -> storedUser.addInboxMessages(
                    Sync.StoredUserMessageProto.newBuilder()
                            .setMessageId(com.google.protobuf.ByteString.copyFromUtf8(message.messageId))
                            .setSenderUserId(message.message.getSenderUserId())
                            .setLamportClock(message.operationId.clock)
                            .setNodeId(com.google.protobuf.ByteString.copyFrom(message.operationId.nodeId.bytes()))
                            .setOperationPayload(com.google.protobuf.ByteString.copyFrom(message.operationPayload))
                            .setSignature(com.google.protobuf.ByteString.copyFrom(message.signature))
                            .setSenderUserPublicKey(
                                    com.google.protobuf.ByteString.copyFrom(message.senderPublicKey.bytes()))));
            state.addUsers(storedUser);
        }
        persistUserOperationTrees();
        persistSyncUnitOperationTrees();
        syncUnits.values().forEach(unit -> state.addSyncUnits(persistMetadata(unit)));
        storage.put(BlobStorage.NODE_STATE_ID, state.build());
        unitRoots.flush();
        storage.commit();
    }

    private static Sync.PersistedOperationProto persist(Operation operation) {
        return Sync.PersistedOperationProto.newBuilder()
                .setLamportClock(operation.id.clock)
                .setNodeId(com.google.protobuf.ByteString.copyFrom(operation.id.nodeId.bytes()))
                .setPayload(com.google.protobuf.ByteString.copyFrom(operation.payload))
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature))
                .build();
    }

    private static Sync.RemoteSyncUnitContentTransferProto toProto(ContentTransfer transfer) {
        return Sync.RemoteSyncUnitContentTransferProto.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(transfer.unitId.bytes()))
                .setLamportClock(transfer.id.clock)
                .setNodeId(com.google.protobuf.ByteString.copyFrom(transfer.id.nodeId.bytes()))
                .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(transfer.author.bytes()))
                .setKind(transfer.kind.ordinal())
                .setKey(transfer.key)
                .setTotalLength(transfer.totalLength)
                .setOffset(transfer.offset)
                .setBytes(com.google.protobuf.ByteString.copyFrom(transfer.bytes))
                .setSignature(com.google.protobuf.ByteString.copyFrom(transfer.signature))
                .setComplete(transfer.complete)
                .build();
    }

    private static ContentTransfer fromProto(Sync.RemoteSyncUnitContentTransferProto transfer) {
        int kind = transfer.getKind();
        if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
            throw new IllegalArgumentException("Invalid SyncUnit content transfer kind");
        return new ContentTransfer(SyncUnitId.fromBytes(transfer.getUnitId().toByteArray()),
                new OperationId(transfer.getLamportClock(), NodeId.fromBytes(transfer.getNodeId().toByteArray())),
                PubKey.from(transfer.getAuthorPublicKey().toByteArray()),
                SyncUnitContentOperation.Kind.values()[kind], transfer.getKey(), transfer.getTotalLength(),
                transfer.getOffset(), transfer.getBytes().toByteArray(), transfer.getSignature().toByteArray(),
                transfer.getComplete());
    }

    private static String contentOffsetKey(OperationId id) {
        return id.nodeId.hex() + ":" + id.clock;
    }

    private static Sync.PersistedSyncUnitProto persistMetadata(SyncUnitData unit) {
        var result = Sync.PersistedSyncUnitProto.newBuilder()
                .setId(com.google.protobuf.ByteString.copyFrom(unit.id.bytes()))
                .setType(unit.type.ordinal())
                .setCreatorUserId(com.google.protobuf.ByteString.copyFrom(unit.creator.bytes()))
                .setClock(unit.clock)
                .setEntityCounter(unit.entityCounter)
                .setActiveNodeSet(persist(unit.activeNodeSet))
                .setHome(unit.home)
                .setNextNodeId(unit.nextNodeId);
        unit.partialContentOperations.values().forEach(partial -> result.addPartialContentOperations(
                Sync.PartialSyncUnitContentOperationStateProto.newBuilder()
                        .setUnitId(com.google.protobuf.ByteString.copyFrom(partial.unitId.bytes()))
                        .setLamportClock(partial.id.clock)
                        .setNodeId(com.google.protobuf.ByteString.copyFrom(partial.id.nodeId.bytes()))
                        .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(partial.author.bytes()))
                        .setKind(partial.kind.ordinal())
                        .setKey(partial.key)
                        .setTotalLength(partial.payload.length)
                        .setReceivedBytes(com.google.protobuf.ByteString.copyFrom(partial.payload, 0,
                                partial.receivedLength))
                        .setSignature(com.google.protobuf.ByteString.copyFrom(partial.signature))));
        return result.build();
    }

    private static PartialContentOperation loadPartialContentOperation(
            Sync.PartialSyncUnitContentOperationStateProto stored) {
        int kind = stored.getKind();
        if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
            throw new IllegalStateException("Invalid persisted partial SyncUnit content operation");
        byte[] received = stored.getReceivedBytes().toByteArray();
        if (received.length > stored.getTotalLength())
            throw new IllegalStateException("Invalid persisted partial SyncUnit content operation");
        var unitId = SyncUnitId.fromBytes(stored.getUnitId().toByteArray());
        var id = new OperationId(stored.getLamportClock(), NodeId.fromBytes(stored.getNodeId().toByteArray()));
        var partial = new PartialContentOperation(unitId, id,
                PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                SyncUnitContentOperation.Kind.values()[kind], stored.getKey(), stored.getTotalLength(),
                stored.getSignature().toByteArray());
        System.arraycopy(received, 0, partial.payload, 0, received.length);
        partial.receivedLength = received.length;
        return partial;
    }

    private static SyncUnitData deserializeSyncUnitMetadata(Sync.PersistedSyncUnitProto stored) {
        if (stored.getType() < 0 || stored.getType() >= SyncUnitAuthorization.Type.values().length)
            throw new IllegalStateException("Invalid persisted SyncUnit type");
        var id = SyncUnitId.fromBytes(stored.getId().toByteArray());
        var unit = new SyncUnitData(id, SyncUnitAuthorization.Type.values()[stored.getType()],
                UserId.fromBytes(stored.getCreatorUserId().toByteArray()));
        unit.clock = stored.getClock();
        unit.entityCounter = stored.getEntityCounter();
        unit.home = stored.getHome();
        unit.nextNodeId = stored.getNextNodeId();
        loadActiveNodeSet(stored.getActiveNodeSet(), unit.activeNodeSet);
        for (var partial : stored.getPartialContentOperationsList()) {
            var loaded = loadPartialContentOperation(partial);
            if (!loaded.unitId.equals(id) || unit.partialContentOperations.put(loaded.id, loaded) != null)
                throw new IllegalStateException("Invalid persisted partial SyncUnit content operation");
        }
        return unit;
    }

    private static Sync.PersistedSyncUnitAuthorizationOperationProto persistAuthOperation(
            SyncUnitAuthorization.Operation operation) {
        return Sync.PersistedSyncUnitAuthorizationOperationProto.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(operation.unitId().bytes()))
                .setLamportClock(operation.lamportClock())
                .setNodeId(com.google.protobuf.ByteString.copyFrom(operation.nodeId().bytes()))
                .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(operation.author().bytes()))
                .setAction(operation.action().ordinal())
                .setSubjectUserId(operation.subject() == null ? com.google.protobuf.ByteString.EMPTY
                        : com.google.protobuf.ByteString.copyFrom(operation.subject().bytes()))
                .setRole(operation.role() == null ? 0 : operation.role().ordinal() + 1)
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature()))
                .build();
    }

    private static SyncUnitAuthorization.Operation parseAuthOperation(byte[] payload) {
        try {
            var stored = Sync.PersistedSyncUnitAuthorizationOperationProto.parseFrom(payload);
            int action = stored.getAction();
            int role = stored.getRole();
            if (action < 0 || action >= SyncUnitAuthorization.Action.values().length
                    || role < 0 || role > SyncUnitAuthorization.Role.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit authorization operation");
            var subject = stored.getSubjectUserId().toByteArray();
            return new SyncUnitAuthorization.Operation(
                    SyncUnitId.fromBytes(stored.getUnitId().toByteArray()),
                    stored.getLamportClock(), NodeId.fromBytes(stored.getNodeId().toByteArray()),
                    PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                    SyncUnitAuthorization.Action.values()[action],
                    subject.length == 0 ? null : UserId.fromBytes(subject),
                    role == 0 ? null : SyncUnitAuthorization.Role.values()[role - 1],
                    stored.getSignature().toByteArray());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted SyncUnit authorization operation", e);
        }
    }

    private static Sync.PersistedSyncUnitContentOperationProto persistContentOperation(
            SyncUnitContentOperation operation) {
        return Sync.PersistedSyncUnitContentOperationProto.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(operation.unitId().bytes()))
                .setLamportClock(operation.lamportClock())
                .setNodeId(com.google.protobuf.ByteString.copyFrom(operation.nodeId().bytes()))
                .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(operation.author().bytes()))
                .setKind(operation.kind().ordinal())
                .setKey(operation.key())
                .setPayload(com.google.protobuf.ByteString.copyFrom(operation.payload()))
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature()))
                .build();
    }

    private static SyncUnitContentOperation parseContentOperation(byte[] payload) {
        try {
            var stored = Sync.PersistedSyncUnitContentOperationProto.parseFrom(payload);
            int kind = stored.getKind();
            if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit content operation");
            return new SyncUnitContentOperation(
                    SyncUnitId.fromBytes(stored.getUnitId().toByteArray()),
                    stored.getLamportClock(), NodeId.fromBytes(stored.getNodeId().toByteArray()),
                    PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                    SyncUnitContentOperation.Kind.values()[kind], stored.getKey(),
                    stored.getPayload().toByteArray(), stored.getSignature().toByteArray());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted SyncUnit content operation", e);
        }
    }

    private static Sync.PersistedSyncUnitProto persist(SyncUnitData unit) {
        var result = Sync.PersistedSyncUnitProto.newBuilder()
                .setId(com.google.protobuf.ByteString.copyFrom(unit.id.bytes()))
                .setType(unit.type.ordinal())
                .setCreatorUserId(com.google.protobuf.ByteString.copyFrom(unit.creator.bytes()))
                .setClock(unit.clock)
                .setEntityCounter(unit.entityCounter)
                .setActiveNodeSet(persist(unit.activeNodeSet))
                .setHome(unit.home)
                .setNextNodeId(unit.nextNodeId);
        return result.build();
    }

    static Sync.ActiveNodeSetStateProto persist(ActiveNodeSet activeNodeSet) {
        var result = Sync.ActiveNodeSetStateProto.newBuilder();
        if (activeNodeSet.version != null)
            result.setVersionNodeId(com.google.protobuf.ByteString.copyFrom(activeNodeSet.version.nodeId.bytes()))
                    .setVersionClock(activeNodeSet.version.clock);
        activeNodeSet.nodes.forEach(id -> result.addNodeIds(
                com.google.protobuf.ByteString.copyFrom(id.bytes())));
        activeNodeSet.removedNodes.forEach(id -> result.addRemovedNodeIds(
                com.google.protobuf.ByteString.copyFrom(id.bytes())));
        if (activeNodeSet.leavingProcess != null) {
            var process = activeNodeSet.leavingProcess;
            var saved = Sync.LeavingProcessStateProto.newBuilder()
                    .setPhase(process.phase.ordinal())
                    .setProcessId(process.processId)
                    .setLeavingNodeId(com.google.protobuf.ByteString.copyFrom(process.leavingNodeId.bytes()))
                    .setLastOperationClock(process.lastOperationClock)
                    .setLastOperationClockSourceNode(
                            com.google.protobuf.ByteString.copyFrom(process.lastOperationClockSourceNode.bytes()));
            process.confirmedBy.forEach(id -> saved.addConfirmedBy(
                    com.google.protobuf.ByteString.copyFrom(id.bytes())));
            result.setLeavingProcess(saved);
        }
        return result.build();
    }

    private void restoreState(Sync.NodeStateProto state) {
        network.nodes.remove(peerId, this);
        peerPrivateKey = com.github.ruediste.p2psync.libp2p.crypto.Marshaling
                .unmarshalPrivateKey(state.getPeerPrivateKey().toByteArray());
        peerId = PeerId.fromPubKey(peerPrivateKey.publicKey());
        nodeId = NodeId.fromPublicKey(peerPrivateKey.publicKey());
        if (!nodeId.equals(NodeId.fromBytes(state.getNodeId().toByteArray())))
            throw new IllegalStateException("Persisted node identity does not match its public key");
        nodeKey = nodeId.hex();
        clock = state.getClock();
        network.nodes.put(peerId, this);
        restoringState = true;
        boolean rebuiltFileTree = false;
        try {
            for (var storedUnit : state.getSyncUnitsList())
                loadSyncUnit(storedUnit);
            for (var storedUser : state.getUsersList()) {
                var user = new NodeUserData();
                user.userKey = com.github.ruediste.p2psync.libp2p.crypto.Marshaling.unmarshalPrivateKey(
                        storedUser.getUserPrivateKey().toByteArray());
                user.localName = storedUser.getLocalName();
                user.rootDirectoryGuid = storedUser.getRootDirectoryGuid().toByteArray();
                user.snapshotState = storedUser.getSnapshotState().toByteArray();
                if (!storedUser.getHomeSyncUnitId().isEmpty())
                    user.homeSyncUnitId = SyncUnitId.fromBytes(storedUser.getHomeSyncUnitId().toByteArray());
                for (var label : storedUser.getSharedFolderLabelsList())
                    user.sharedFolderLabels.put(
                            SyncUnitId.fromBytes(label.getSyncUnitId().toByteArray()).toString(), label.getLabel());
                user.nextNodeId = storedUser.getNextNodeId();
                user.clock = storedUser.getClock();
                user.entityCounter = storedUser.getEntityCounter();
                for (var entry : storedUser.getMaxOperationClockList())
                    user.maxOperationClock.put(entry.getNodeId(), entry.getClock());
                loadActiveNodeSet(storedUser.getActiveNodeSet(), user.activeNodeSet);
                loadPartialOperations(storedUser.getRetrievalTreeList(), user.retrievalTree);
                for (var participatingId : storedUser.getParticipatingSyncUnitIdsList()) {
                    if (participatingId.size() != SyncUnitId.LENGTH)
                        throw new IllegalStateException("Invalid persisted SyncUnit ID");
                    user.participatingSyncUnitIds.add(SyncUnitId.fromBytes(participatingId.toByteArray()).toString());
                }
                user.deliveredMessageIds.addAll(storedUser.getDeliveredMessageIdsList());
                for (var storedMessage : storedUser.getInboxMessagesList()) {
                    var message = loadStoredUserMessage(storedMessage, user.userId());
                    if (user.inboxMessages.putIfAbsent(message.messageId, message) != null)
                        throw new IllegalStateException("Duplicate persisted inbox message ID");
                }
                user.contacts = UserState.contactsFromState(user.snapshotState, user.userId());
                users.put(user.userId(), user);
                ensureUserSyncUnit(user);
                ensureHomeUnit(user);
                user.guidMapper = new GuidMapper(dataStorage, unitRoots, user.fileUnitId(), user.nextNodeId);
                loadUserOperationTrees(user);
                long fileTreeRoot = unitRoots.get(user.fileUnitId(), UnitRootStore.FILE_TREE);
                if (fileTreeRoot != 0)
                    user.fileSystem = new UserFileTree(dataStorage, user.guidMapper,
                            user.rootDirectoryGuid, fileTreeRoot);
                else {
                    user.fileSystem = UserState.replayFromState(dataStorage, user.guidMapper,
                            user.snapshotState, user.userId(), user.allOperations().values()).fileSystem;
                    rebuiltFileTree = true;
                }
                rebuildInboxMessages(user);
                if (!user.allOperations().isEmpty()) {
                    var replayed = UserState.contactsFromState(user.snapshotState, user.userId());
                    for (var operation : user.allOperations().values())
                        replayed.apply(parseUserOperation(operation));
                    user.contacts = replayed;
                }
            }
        } finally {
            restoringState = false;
        }
        if (rebuiltFileTree)
            persistState();
    }

    private void loadSyncUnit(Sync.PersistedSyncUnitProto stored) {
        var unit = deserializeSyncUnitMetadata(stored);
        if (syncUnits.putIfAbsent(unit.id, unit) != null)
            throw new IllegalStateException("Duplicate persisted SyncUnit ID");
        loadSyncUnitOperationTrees(unit);
        ensureUnitMapper(unit);
    }

    private SyncUnitData deserializeSyncUnit(Sync.PersistedSyncUnitProto stored) {
        if (stored.getType() < 0 || stored.getType() >= SyncUnitAuthorization.Type.values().length)
            throw new IllegalStateException("Invalid persisted SyncUnit type");
        var id = SyncUnitId.fromBytes(stored.getId().toByteArray());
        var unit = new SyncUnitData(id, SyncUnitAuthorization.Type.values()[stored.getType()],
                UserId.fromBytes(stored.getCreatorUserId().toByteArray()),
                new OperationTree(dataStorage), new OperationTree(dataStorage));
        unit.clock = stored.getClock();
        unit.entityCounter = stored.getEntityCounter();
        unit.home = stored.getHome();
        unit.nextNodeId = stored.getNextNodeId();
        loadActiveNodeSet(stored.getActiveNodeSet(), unit.activeNodeSet);
        if (stored.hasAuthorizationOperation()) {
            var storedOperation = stored.getAuthorizationOperation();
            int action = storedOperation.getAction();
            int role = storedOperation.getRole();
            if (action < 0 || action >= SyncUnitAuthorization.Action.values().length
                    || role < 0 || role > SyncUnitAuthorization.Role.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit authorization operation");
            var subjectBytes = storedOperation.getSubjectUserId().toByteArray();
            var operation = new SyncUnitAuthorization.Operation(
                    SyncUnitId.fromBytes(storedOperation.getUnitId().toByteArray()),
                    storedOperation.getLamportClock(), NodeId.fromBytes(storedOperation.getNodeId().toByteArray()),
                    PubKey.from(storedOperation.getAuthorPublicKey().toByteArray()),
                    SyncUnitAuthorization.Action.values()[action],
                    subjectBytes.length == 0 ? null : UserId.fromBytes(subjectBytes),
                    role == 0 ? null : SyncUnitAuthorization.Role.values()[role - 1],
                    storedOperation.getSignature().toByteArray());
            if (!operation.unitId().equals(id) || !operation.hasValidSignature())
                throw new IllegalStateException("Invalid persisted SyncUnit authorization signature");
            if (!operation.unitId().equals(id) || !operation.hasValidSignature())
                throw new IllegalStateException("Invalid persisted SyncUnit authorization signature");
            unit.authorizationOperations.add(operation);
        }
        unit.replayAuthorization();
        unit.replayContent();
        return unit;
    }

    private static StoredUserMessage loadStoredUserMessage(Sync.StoredUserMessageProto stored, UserId recipientId) {
        String messageId = stored.getMessageId().toStringUtf8();
        var operationId = new OperationId(stored.getLamportClock(), NodeId.fromBytes(stored.getNodeId().toByteArray()));
        byte[] operationPayload = stored.getOperationPayload().toByteArray();
        byte[] signature = stored.getSignature().toByteArray();
        Sync.UserOperationProto userOperation;
        Sync.UserMessageProto message;
        try {
            userOperation = Sync.UserOperationProto.parseFrom(operationPayload);
            if (userOperation.getKind() != Sync.UserOperationProto.Kind.DIRECT_MESSAGE)
                throw new IllegalStateException("Persisted inbox entry is not a direct message");
            message = Sync.UserMessageProto.parseFrom(userOperation.getMessagePayload());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted inbox message", e);
        }
        var senderId = UserId.fromBytes(stored.getSenderUserId().toByteArray());
        var senderPublicKey = PubKey.from(stored.getSenderUserPublicKey().toByteArray());
        if (messageId.isBlank() || !messageId.equals(userOperation.getMessageId())
                || !messageId.equals(message.getMessageId())
                || !message.getRecipientUserId().equals(com.google.protobuf.ByteString.copyFrom(recipientId.bytes()))
                || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                || !senderId.equals(UserId.fromPublicKey(senderPublicKey))
                || !verifyOperationSignature(senderPublicKey, operationId, operationPayload, signature))
            throw new IllegalStateException("Invalid persisted inbox message identity or signature");
        return new StoredUserMessage(messageId, operationId, operationPayload, signature, senderPublicKey, message);
    }

    static void loadActiveNodeSet(Sync.ActiveNodeSetStateProto stored, ActiveNodeSet target) {
        if (stored.getVersionClock() != 0 || !stored.getVersionNodeId().isEmpty())
            target.setVersion(NodeId.fromBytes(stored.getVersionNodeId().toByteArray()), stored.getVersionClock());
        stored.getNodeIdsList().forEach(id -> target.nodes.add(NodeId.fromBytes(id.toByteArray())));
        stored.getRemovedNodeIdsList().forEach(id -> target.removedNodes.add(NodeId.fromBytes(id.toByteArray())));
        if (stored.hasLeavingProcess()) {
            var saved = stored.getLeavingProcess();
            int phase = saved.getPhase();
            if (phase < 0 || phase >= ActiveNodeSet.Phase.values().length)
                throw new IllegalStateException("Invalid persisted leaving phase");
            var process = new ActiveNodeSet.LeavingProcess();
            process.phase = ActiveNodeSet.Phase.values()[phase];
            process.processId = saved.getProcessId();
            process.leavingNodeId = saved.getLeavingNodeId().isEmpty() ? null
                    : NodeId.fromBytes(saved.getLeavingNodeId().toByteArray());
            process.lastOperationClock = saved.getLastOperationClock();
            process.lastOperationClockSourceNode = saved.getLastOperationClockSourceNode().isEmpty() ? null
                    : NodeId.fromBytes(saved.getLastOperationClockSourceNode().toByteArray());
            saved.getConfirmedByList().forEach(id -> process.confirmedBy.add(NodeId.fromBytes(id.toByteArray())));
            target.leavingProcess = process;
        }
    }

    private static void loadPartialOperations(Iterable<Sync.PartialOperationStateProto> stored,
            Map<String, PartialOperation> target) {
        for (var partial : stored) {
            int totalLength = partial.getTotalLength();
            byte[] received = partial.getReceivedBytes().toByteArray();
            if (received.length > totalLength)
                throw new IllegalStateException("Invalid persisted partial operation");
            var id = new OperationId(partial.getLamportClock(), NodeId.fromBytes(partial.getNodeId().toByteArray()));
            var value = new PartialOperation(id, totalLength, partial.getSignature().toByteArray());
            System.arraycopy(received, 0, value.payload, 0, received.length);
            value.receivedLength = received.length;
            if (!partial.getSourceNodeId().equals(id.nodeKey))
                throw new IllegalStateException("Persisted partial operation source does not match its ID");
            target.put(partial.getSourceNodeId(), value);
        }
    }

    @Override
    public synchronized void close() {
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
