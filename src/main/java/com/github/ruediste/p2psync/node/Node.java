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
    private GuidMapper sharedFolderMapper;
    private long sharedFolderNextNodeId;
    public Map<PubKey, NodeUserData> users = new HashMap<>();
    final Map<SyncUnitId, SyncUnitData> syncUnits = new HashMap<>();
    public Network network;
    long clock;
    private byte[] nodeHash;
    private String nodeKey;
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
        nodeHash = CryptoUtils.sha256(peerId.getBytes());
        nodeKey = HexFormat.of().formatHex(nodeHash);
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
                restoreState(Sync.NodeState.parseFrom(stateBytes.get()));
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
                .filter(existing -> existing.secureSession().getRemoteId().equals(peerId))
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

    private byte[] handleRemoteSyncRequest(PeerId remotePeerId, byte[] bytes) {
        try {
            var request = Sync.RemoteSyncRequest.parseFrom(bytes);
            String remoteNodeKey = nodeKeyFor(remotePeerId);

            // TODO 3: Validate joiningNodeId against the authenticated peer identity
            if (!request.getJoiningNodeId().isEmpty()) {
                try {
                    java.util.HexFormat.of().parseHex(request.getJoiningNodeId());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Malformed joiningNodeId", e);
                }
                if (!request.getJoiningNodeId().equals(remoteNodeKey)) {
                    throw new IllegalArgumentException("joiningNodeId does not match authenticated peer");
                }
            }

            if (request.hasDirectMessage()) {
                var delivery = request.getDirectMessage();
                var senderId = PubKey.from(delivery.getSenderUserId().toByteArray());
                var stored = delivery.getOperation();
                var operationId = new OperationId(stored.getLamportClock(), stored.getNodeHash().toByteArray());
                var operation = new Operation(operationId, stored.getPayload().toByteArray(),
                        stored.getSignature().toByteArray());
                boolean messageStored = storeIncomingMessage(operation, senderId);
                return Sync.RemoteSyncResponse.newBuilder().setNodeClock(clock)
                        .setDirectMessageStored(messageStored).build().toByteArray();
            }
            if (!request.getSyncUnitId().isEmpty()) {
                var id = SyncUnitId.fromBytes(request.getSyncUnitId().toByteArray());
                var requesterId = PubKey.from(request.getRequesterUserPublicKey().toByteArray());
                var unit = syncUnits.get(id);
                boolean knownMember = unit != null
                        && unit.authorization.members().containsKey(requesterId);
                boolean invited = unit != null
                        && unit.authorization.pendingInvitations().containsKey(requesterId);
                if (unit == null)
                    return Sync.RemoteSyncResponse.newBuilder().setNodeClock(clock).build().toByteArray();
                // TODO 1: Authenticate requester against the Noise-authenticated peer
                boolean authorized = false;
                var requesterData = users.get(requesterId);
                if (requesterData != null) {
                    authorized = requesterData.activeNodeSet.nodes.contains(remoteNodeKey);
                }
                if (!authorized && !knownMember && !invited)
                    return Sync.RemoteSyncResponse.newBuilder().setNodeClock(clock).setSyncUnitRevoked(true)
                            .build().toByteArray();
                if (request.hasSyncUnitUpdate()) {
                    if (authorized && knownMember) {
                        try {
                            var update = deserializeSyncUnit(request.getSyncUnitUpdate());
                            if (!update.id.equals(id) || update.type != unit.type || !update.creator.equals(unit.creator))
                                throw new IllegalArgumentException("Remote SyncUnit update metadata does not match");
                            mergeSyncUnit(update, remoteNodeKey);
                            unit = syncUnits.get(id);
                            knownMember = unit.authorization.members().containsKey(requesterId);
                        } catch (IllegalArgumentException | IllegalStateException invalidUpdate) {
                        }
                    }
                }
                var fromClock = new TreeMap<String, Long>();
                request.getFromClockList().forEach(entry -> fromClock.merge(entry.getNodeId(), entry.getClock(), Math::max));
                var contentOffsets = new HashMap<String, Integer>();
                request.getContentOffsetsList().forEach(
                        entry -> contentOffsets.put(entry.getNodeId() + ":" + entry.getLamportClock(),
                                entry.getOffset()));
                int contentBudget = request.getByteBudget() <= 0 ? 1_000_000 : request.getByteBudget();
                var response = Sync.RemoteSyncResponse.newBuilder()
                        .setNodeClock(clock)
                        .setSyncUnitFound(true)
                        .setSyncUnit(persist(unit));
                if (knownMember) {
                    unit.serveContent(fromClock, contentOffsets, contentBudget)
                            .forEach(transfer -> response.addContentTransfers(toProto(transfer)));
                }
                return response.build().toByteArray();
            }
            PubKey userId = PubKey.from(request.getUserId().toByteArray());
            NodeUserData data = users.get(userId);
            var response = Sync.RemoteSyncResponse.newBuilder().setUserFound(data != null).setNodeClock(clock);
            if (data == null)
                return response.build().toByteArray();
            // TODO 1: Authenticate user sync request against the Noise-authenticated peer
            if (!data.activeNodeSet.nodes.contains(remoteNodeKey)) {
                return Sync.RemoteSyncResponse.newBuilder().setNodeClock(clock).setUserFound(false).build().toByteArray();
            }
            response.setActiveNodeSet(persist(data.activeNodeSet));
            if (data.homeSyncUnitId != null)
                response.setHomeSyncUnitId(com.google.protobuf.ByteString.copyFrom(data.homeSyncUnitId.bytes()));
            data.maxOperationClock.forEach((source, clock) -> response.addMaxOperationClock(
                    Sync.OperationClock.newBuilder().setNodeId(source).setClock(clock)));
            if (request.getIncludeHistory()) {
                response.addAllContacts(data.contacts.toProto());
                try {
                    var treeState = Sync.UserTreeState.parseFrom(data.fileSystem.serializeState());
                    response.setFileTreeRootId(treeState.getRootId())
                            .setFileTreeStatus(treeState.getStatusMessage())
                            .addAllFileTreeEntries(treeState.getEntriesList());
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new IllegalStateException("Invalid file-tree snapshot", e);
                }
                data.operationTree.values().forEach(operation -> response.addHistory(persist(operation)));
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

    public static class Network {
        public Map<PeerId, Node> nodes = new HashMap<>();

        public Set<PeerId> getPeersForUser(PubKey userId) {
            return nodes.values().stream().filter(x -> x.users.containsKey(userId)).map(x -> x.peerId)
                    .collect(Collectors.toSet());
        }
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
        final TreeMap<OperationId, Operation> userOperationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> userLocalOperationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> userStagingTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> homeOperationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> homeLocalOperationTree = new TreeMap<>();
        final TreeMap<OperationId, Operation> homeStagingTree = new TreeMap<>();
        final CombinedOperationTree operationTree = new CombinedOperationTree(userOperationTree, homeOperationTree);
        final CombinedOperationTree localOperationTree = new CombinedOperationTree(userLocalOperationTree,
                homeLocalOperationTree);
        final CombinedOperationTree stagingTree = new CombinedOperationTree(userStagingTree, homeStagingTree);
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
        GuidMapper guidMapper;

        public PubKey userId() {
            return userKey.publicKey();
        }

        public SyncUnitId syncUnitId() {
            return SyncUnitId.forUser(userId());
        }

        SyncUnitId fileUnitId() {
            return homeSyncUnitId == null ? syncUnitId() : homeSyncUnitId;
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
                    if (!verifyOperationSignature(userKey.publicKey(), transfer.id, payload, signature))
                        throw new IllegalArgumentException("Invalid user operation signature");
                    requireUserUnitModification(userKey.publicKey());
                    stagingTree.putIfAbsent(transfer.id, new Operation(transfer.id, payload, signature));
                    retrievalTree.remove(source);
                    maxOperationClock.merge(source, transfer.id.clock, Math::max);
                    clock = Math.max(clock, transfer.id.clock);
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

    static final class StoredUserMessage {
        final String messageId;
        final OperationId operationId;
        final byte[] operationPayload;
        final byte[] signature;
        final Sync.UserMessage message;

        StoredUserMessage(String messageId, OperationId operationId, byte[] operationPayload, byte[] signature,
                Sync.UserMessage message) {
            this.messageId = messageId;
            this.operationId = operationId;
            this.operationPayload = operationPayload.clone();
            this.signature = signature.clone();
            this.message = message;
        }
    }

    static final class SyncUnitData {
        final SyncUnitId id;
        final SyncUnitAuthorization.Type type;
        final PubKey creator;
        final List<SyncUnitAuthorization.Operation> authorizationOperations = new java.util.ArrayList<>();
        final TreeMap<OperationId, SyncUnitContentOperation> contentOperations = new TreeMap<>();
        final Map<OperationId, PartialContentOperation> partialContentOperations = new TreeMap<>();
        long clock;
        long entityCounter;
        boolean home;
        long nextNodeId;
        GuidMapper guidMapper;
        ActiveNodeSet activeNodeSet = new ActiveNodeSet();
        final Map<String, byte[]> todoItems = new TreeMap<>();
        final List<SyncUnitContentOperation> channelMessages = new java.util.ArrayList<>();
        SyncUnitAuthorization.State authorization;

        SyncUnitData(SyncUnitId id, SyncUnitAuthorization.Type type, PubKey creator) {
            this.id = id;
            this.type = type;
            this.creator = creator;
            authorization = SyncUnitAuthorization.replay(id, type, creator, authorizationOperations);
        }

        void replayAuthorization() {
            authorization = SyncUnitAuthorization.replay(id, type, creator, authorizationOperations);
        }

        void replayContent() {
            todoItems.clear();
            channelMessages.clear();
            for (var operation : contentOperations.values()) {
                if (!operation.unitId().equals(id) || !operation.hasValidSignature())
                    throw new IllegalArgumentException("Invalid SyncUnit content operation signature");
                var action = switch (operation.kind()) {
                    case SHARED_FILE_SET, SHARED_FILE_DELETE, TODO_SET, TODO_DELETE ->
                        SyncUnitAuthorization.Action.MODIFY;
                    case CHANNEL_MESSAGE -> SyncUnitAuthorization.Action.SEND_MESSAGE;
                };
                if (!contentKindMatchesType(type, operation.kind()))
                    throw new IllegalArgumentException("Content operation does not match SyncUnit type");
                var operationId = new OperationId(operation.lamportClock(), operation.nodeHash());
                var precedingAuthorization = authorizationOperations.stream()
                        .filter(value -> compare(value, operationId) <= 0).toList();
                var stateAtOperation = SyncUnitAuthorization.replay(id, type, creator, precedingAuthorization);
                if (!SyncUnitAuthorization.isAuthorized(type, creator, stateAtOperation.members(),
                        operation.author(), action))
                    throw new IllegalArgumentException("User is not authorized for SyncUnit content operation");
                switch (operation.kind()) {
                    case SHARED_FILE_SET, SHARED_FILE_DELETE -> {
                    }
                    case TODO_SET -> todoItems.put(operation.key(), operation.payload());
                    case TODO_DELETE -> todoItems.remove(operation.key());
                    case CHANNEL_MESSAGE -> channelMessages.add(operation);
                }
            }
        }

        java.util.List<ContentTransfer> serveContent(Map<String, Long> fromClock, Map<String, Integer> offsets,
                int byteBudget) {
            var result = new java.util.ArrayList<ContentTransfer>();
            int bytesSent = 0;
            for (var entry : contentOperations.entrySet()) {
                var operationId = entry.getKey();
                var operation = entry.getValue();
                String source = "c:" + HexFormat.of().formatHex(operationId.nodeHash);
                if (operationId.clock <= fromClock.getOrDefault(source, 0L))
                    continue;
                byte[] payload = operation.payload();
                int offset = offsets.getOrDefault(contentOffsetKey(operationId), 0);
                if (offset < 0 || offset > payload.length)
                    throw new IllegalArgumentException("Invalid SyncUnit content transfer offset");
                int available = Math.max(0, byteBudget - bytesSent);
                int count = Math.min(payload.length - offset, available);
                boolean complete = offset + count == payload.length;
                result.add(new ContentTransfer(id, operationId, operation.author(), operation.kind(),
                        operation.key(), payload.length, offset,
                        Arrays.copyOfRange(payload, offset, offset + count), operation.signature(), complete));
                bytesSent += count;
                if (!complete)
                    return result;
            }
            return result;
        }

        void receiveContent(java.util.List<ContentTransfer> transfers) {
            boolean completedAny = false;
            for (var transfer : transfers) {
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
                            transfer.id.nodeHash, transfer.author, transfer.kind, transfer.key, payload, signature);
                    if (!operation.hasValidSignature())
                        throw new IllegalArgumentException("Invalid SyncUnit content operation signature");
                    var previous = contentOperations.putIfAbsent(transfer.id, operation);
                    if (previous != null && !sameContentOperation(previous, operation))
                        throw new IllegalArgumentException("Conflicting SyncUnit content operations share an ID");
                    partialContentOperations.remove(transfer.id);
                    completedAny = true;
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
            }
            if (completedAny)
                replayContent();
        }

        private static int compare(SyncUnitAuthorization.Operation left, OperationId right) {
            int clockOrder = Long.compare(left.lamportClock(), right.clock);
            return clockOrder != 0 ? clockOrder : Arrays.compareUnsigned(left.nodeHash(), right.nodeHash);
        }

        private static boolean contentKindMatchesType(SyncUnitAuthorization.Type type,
                SyncUnitContentOperation.Kind kind) {
            return switch (kind) {
                case SHARED_FILE_SET, SHARED_FILE_DELETE -> type == SyncUnitAuthorization.Type.SHARED_FOLDER;
                case TODO_SET, TODO_DELETE -> type == SyncUnitAuthorization.Type.TODO_LIST;
                case CHANNEL_MESSAGE -> type == SyncUnitAuthorization.Type.MESSAGE_CHANNEL;
            };
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
        data.rootDirectoryGuid = nextEntityId(data);
        users.put(data.userId(), data);
        ensureUserSyncUnit(data);
        ensureHomeUnit(data);
        data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
        data.fileSystem = UserFileTree.empty(dataStorage, data.guidMapper, data.rootDirectoryGuid);
        data.contacts = ContactList.empty(data.userId());
        data.snapshotState = new UserState(data.fileSystem, data.contacts).serializeState();
        var handle = new NodeUserHandle(this, data);
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
        var peerNode = network.nodes.get(peer);
        var peerData = peerNode.users.get(userId);
        clock = Math.max(clock, peerNode.clock);

        var data = new NodeUserData();
        data.userKey = userKey;
        data.localName = localName;
        data.rootDirectoryGuid = peerData.rootDirectoryGuid.clone();
        data.homeSyncUnitId = peerData.homeSyncUnitId;
        data.snapshotState = peerData.snapshotState.clone();
        data.operationTree.putAll(peerData.operationTree);
        data.stagingTree.putAll(peerData.stagingTree);
        data.stagingTree.putAll(peerData.localOperationTree);
        data.maxOperationClock.putAll(peerData.maxOperationClock);
        data.clock = peerData.clock;
        data.activeNodeSet = peerData.activeNodeSet.copy();
        data.activeNodeSet.nodes.add(nodeKey);
        data.activeNodeSet.setVersion(nodeKey, nextClock());
        users.put(userId, data);
        ensureUserSyncUnit(data);
        ensureHomeUnit(data);
        data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
        var state = UserState.replayFromState(dataStorage, data.guidMapper, data.snapshotState, userId,
                data.allOperations().values());
        data.fileSystem = state.fileSystem;
        data.contacts = state.contacts;
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
        data.snapshotState = UserState.snapshotWithFileTree(response.getContactsList(),
                response.getFileTreeRootId().toByteArray(), response.getFileTreeStatus(),
                response.getFileTreeEntriesList());
        if (response.getFileTreeRootId().isEmpty())
            throw new IllegalArgumentException("Remote user history has no file-tree root");
        data.rootDirectoryGuid = response.getFileTreeRootId().toByteArray();
        if (!response.getHomeSyncUnitId().isEmpty())
            data.homeSyncUnitId = SyncUnitId.fromBytes(response.getHomeSyncUnitId().toByteArray());
        for (var stored : response.getHistoryList()) {
            var id = new OperationId(stored.getLamportClock(), stored.getNodeHash().toByteArray());
            var payload = stored.getPayload().toByteArray();
            var signature = stored.getSignature().toByteArray();
            if (!verifyOperationSignature(userKey.publicKey(), id, payload, signature))
                throw new IllegalArgumentException("Remote history contains an invalid user signature");
            requireUserUnitModification(userKey.publicKey());
            data.operationTree.put(id, new Operation(id, payload, signature));
        }
        for (var entry : response.getMaxOperationClockList()) {
            data.maxOperationClock.put(entry.getNodeId(), entry.getClock());
            data.clock = Math.max(data.clock, entry.getClock());
            clock = Math.max(clock, entry.getClock());
        }
        loadActiveNodeSet(response.getActiveNodeSet(), data.activeNodeSet);
        data.activeNodeSet.nodes.add(nodeKey);
        data.activeNodeSet.setVersion(nodeKey, nextClock());
        users.put(data.userId(), data);
        ensureUserSyncUnit(data);
        ensureHomeUnit(data);
        data.guidMapper = new GuidMapper(dataStorage, unitRoots, data.fileUnitId(), 0);
        var state = UserState.replayFromState(dataStorage, data.guidMapper, data.snapshotState,
                userKey.publicKey(), data.operationTree.values());
        data.fileSystem = state.fileSystem;
        data.contacts = state.contacts;
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

    public void setSharedFolderLabel(PubKey userId, SyncUnitId unitId, String label) {
        var user = requireConfiguredUser(userId);
        requireSyncUnitType(unitId, SyncUnitAuthorization.Type.SHARED_FOLDER);
        setSharedFolderLabel(user, unitId, label);
    }

    private void setSharedFolderLabel(NodeUserData user, SyncUnitId unitId, String label) {
        if (label == null || label.isBlank() || label.equals("home") || label.equals(".")
                || label.equals("..") || label.indexOf('/') >= 0
                || label.indexOf('\\') >= 0 || label.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid shared-folder label");
        var operation = Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.SET_SHARED_FOLDER_LABEL)
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(unitId.bytes()))
                .setName(label)
                .build();
        recordLocalOperation(user, operation.toByteArray());
        user.sharedFolderLabels.put(unitId.toString(), label);
        persistState();
    }

    public record RootEntry(String label, SyncUnitId unitId, boolean home, boolean conflict) {
    }

    public List<RootEntry> listRootEntries(PubKey userId) {
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

    public SyncUnitId createSyncUnit(PubKey creatorId, SyncUnitAuthorization.Type type) {
        if (type == SyncUnitAuthorization.Type.USER)
            throw new IllegalArgumentException("User SyncUnits are created with createNewUser");
        var creator = requireConfiguredUser(creatorId);
        SyncUnitId id;
        do {
            id = SyncUnitId.create(nodeHash, ++syncUnitCounter.value);
        } while (syncUnits.containsKey(id));
        var unit = new SyncUnitData(id, type, creatorId);
        unit.activeNodeSet.nodes.add(nodeKey);
        unit.activeNodeSet.setVersion(nodeKey, ++unit.clock);
        syncUnits.put(id, unit);
        ensureUnitMapper(unit);
        recordSyncUnitParticipation(creator, id, true);
        return id;
    }

    public SyncUnitAuthorization.State getSyncUnitAuthorization(SyncUnitId id) {
        return requireSyncUnit(id).authorization;
    }

    public record SyncUnitStatus(SyncUnitId id, SyncUnitAuthorization.Type type,
            SyncUnitAuthorization.Role role, boolean pendingInvitation, int memberCount,
            int pendingInvitationCount) {
    }

    public record SyncUnitMemberStatus(PubKey userId, SyncUnitAuthorization.Role role, boolean pendingInvitation) {
    }

    public List<SyncUnitStatus> listSyncUnits(PubKey userId) {
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

    public List<SyncUnitMemberStatus> listSyncUnitMembers(SyncUnitId id) {
        var unit = requireSyncUnit(id);
        var result = new java.util.ArrayList<SyncUnitMemberStatus>();
        unit.authorization.members()
                .forEach((userId, role) -> result.add(new SyncUnitMemberStatus(userId, role, false)));
        unit.authorization.pendingInvitations()
                .forEach((userId, role) -> result.add(new SyncUnitMemberStatus(userId, role, true)));
        result.sort(Comparator.comparing(member -> HexFormat.of().formatHex(member.userId().bytes())));
        return List.copyOf(result);
    }

    public List<Sync.UserMessage> getInboxMessages(PubKey userId) {
        return requireConfiguredUser(userId).inboxMessages.values().stream()
                .map(message -> message.message).toList();
    }

    /**
     * A direct message together with its direction and the Lamport clock of the
     * signed operation that created it. The clock gives the desktop UI a stable,
     * causal ordering for a user's conversation history.
     */
    public record UserMessageEntry(Sync.UserMessage message, boolean outgoing, long lamportClock) {
    }

    /**
     * Returns the complete local view of a user's direct messages: everything in
     * the inbox plus every message authored by the user, delivered or still
     * queued. This is what the chat and mail views need to show a full history,
     * unlike {@link #getOutboxMessages(PubKey)} which only contains pending items.
     */
    public List<UserMessageEntry> getUserMessageLog(PubKey userId) {
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

    public List<Sync.UserMessage> getOutboxMessages(PubKey userId) {
        var user = requireConfiguredUser(userId);
        var result = new java.util.TreeMap<String, Sync.UserMessage>();
        for (var operation : user.allOperations().values()) {
            var message = parseOutgoingMessage(user.userId(), operation);
            if (message != null && !user.deliveredMessageIds.contains(message.getMessageId()))
                result.put(message.getMessageId(), message);
        }
        return List.copyOf(result.values());
    }

    void deliverPendingMessages() {
        for (var sender : List.copyOf(users.values())) {
            for (var operation : sender.allOperations().values()) {
                var message = parseOutgoingMessage(sender.userId(), operation);
                if (message == null || sender.deliveredMessageIds.contains(message.getMessageId()))
                    continue;
                var recipientId = PubKey.from(message.getRecipientUserId().toByteArray());
                for (var recipientNode : List.copyOf(network.nodes.values())) {
                    if (!recipientNode.users.containsKey(recipientId)
                            || !recipientNode.storeIncomingMessage(operation, sender.userId()))
                        continue;
                    sender.deliveredMessageIds.add(message.getMessageId());
                    for (var recipientReplica : List.copyOf(network.nodes.values()))
                        if (recipientReplica.users.containsKey(recipientId))
                            recipientReplica.copyInboxMessage(recipientNode, recipientId, message.getMessageId());
                    persistState();
                    break;
                }
                if (sender.deliveredMessageIds.contains(message.getMessageId()))
                    continue;
                var remotePeers = new TreeSet<PeerId>(PeerId::compareTo);
                // TODO 2: Resolve the recipient's peers first instead of broadcasting to all address-book peers
                remotePeers.addAll(network.getPeersForUser(recipientId));
                remotePeers.remove(peerId);
                for (var remotePeer : remotePeers) {
                    if (network.nodes.containsKey(remotePeer))
                        continue;
                    try {
                        var delivery = Sync.RemoteDirectMessageDelivery.newBuilder()
                                .setSenderUserId(com.google.protobuf.ByteString.copyFrom(sender.userId().bytes()))
                                .setOperation(persist(operation));
                        var response = exchangeRemote(remotePeer, Sync.RemoteSyncRequest.newBuilder()
                                .setDirectMessage(delivery).build());
                        if (!response.getDirectMessageStored())
                            continue;
                        sender.deliveredMessageIds.add(message.getMessageId());
                        persistState();
                        break;
                    } catch (RuntimeException unavailablePeer) {
                        // Keep the message queued; the next sync retries delivery.
                    }
                }
            }
        }
    }

    private boolean storeIncomingMessage(Operation operation, PubKey senderId) {
        if (!verifyOperationSignature(senderId, operation.id, operation.payload, operation.signature))
            throw new IllegalArgumentException("Invalid direct message operation signature");
        Sync.UserOperation userOperation;
        Sync.UserMessage message;
        try {
            userOperation = Sync.UserOperation.parseFrom(operation.payload);
            if (userOperation.getKind() != Sync.UserOperation.Kind.DIRECT_MESSAGE)
                throw new IllegalArgumentException("Operation is not a direct message");
            message = Sync.UserMessage.parseFrom(userOperation.getMessagePayload());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid direct message payload", e);
        }
        var recipientId = PubKey.from(userOperation.getRecipientUserId().toByteArray());
        if (!message.getMessageId().equals(userOperation.getMessageId())
                || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                || !message.getRecipientUserId().equals(userOperation.getRecipientUserId()))
            throw new IllegalArgumentException("Direct message identity does not match its signed operation");
        var recipient = users.get(recipientId);
        if (recipient == null)
            return false;
        var stored = new StoredUserMessage(message.getMessageId(), operation.id, operation.payload,
                operation.signature, message);
        var previous = recipient.inboxMessages.putIfAbsent(message.getMessageId(), stored);
        if (previous != null && (!Arrays.equals(previous.operationPayload, operation.payload)
                || !Arrays.equals(previous.signature, operation.signature)))
            throw new IllegalArgumentException("Conflicting direct messages share an ID");
        if (previous == null) {
            var receipt = Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.RECEIVE_MESSAGE)
                    .setMessageId(message.getMessageId())
                    .setRecipientUserId(com.google.protobuf.ByteString.copyFrom(recipientId.bytes()))
                    .setMessagePayload(userOperation.getMessagePayload())
                    .setOriginalOperationPayload(com.google.protobuf.ByteString.copyFrom(operation.payload))
                    .setOriginalOperationSignature(com.google.protobuf.ByteString.copyFrom(operation.signature))
                    .setOriginalOperationLamportClock(operation.id.clock)
                    .setOriginalOperationNodeHash(com.google.protobuf.ByteString.copyFrom(operation.id.nodeHash))
                    .build();
            recordLocalOperation(recipient, receipt.toByteArray());
            new NodeUserHandle(this, recipient).rebuildCurrentState();
            rebuildInboxMessages(recipient);
        }
        persistState();
        return true;
    }

    void rebuildInboxMessages(NodeUserData user) {
        for (var operation : user.allOperations().values()) {
            Sync.UserOperation receipt;
            try {
                receipt = Sync.UserOperation.parseFrom(operation.payload);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
            if (receipt.getKind() != Sync.UserOperation.Kind.RECEIVE_MESSAGE)
                continue;
            var senderOperationId = new OperationId(receipt.getOriginalOperationLamportClock(),
                    receipt.getOriginalOperationNodeHash().toByteArray());
            byte[] senderOperationPayload = receipt.getOriginalOperationPayload().toByteArray();
            byte[] senderSignature = receipt.getOriginalOperationSignature().toByteArray();
            Sync.UserMessage receiptMessage;
            try {
                receiptMessage = Sync.UserMessage.parseFrom(receipt.getMessagePayload());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid recipient message receipt", e);
            }
            var message = parseOutgoingMessage(PubKey.from(receiptMessage.getSenderUserId().toByteArray()),
                    new Operation(senderOperationId, senderOperationPayload, senderSignature));
            if (message == null || !message.getMessageId().equals(receipt.getMessageId())
                    || !message.equals(receiptMessage)
                    || !message.getRecipientUserId()
                            .equals(com.google.protobuf.ByteString.copyFrom(user.userId().bytes()))
                    || !receipt.getRecipientUserId()
                            .equals(com.google.protobuf.ByteString.copyFrom(user.userId().bytes())))
                throw new IllegalArgumentException("Invalid recipient message receipt");
            var stored = new StoredUserMessage(message.getMessageId(), senderOperationId,
                    senderOperationPayload, senderSignature, message);
            var previous = user.inboxMessages.putIfAbsent(message.getMessageId(), stored);
            if (previous != null && (!Arrays.equals(previous.operationPayload, senderOperationPayload)
                    || !Arrays.equals(previous.signature, senderSignature)))
                throw new IllegalArgumentException("Conflicting direct messages share an ID");
        }
    }

    private void copyInboxMessage(Node source, PubKey recipientId, String messageId) {
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

    private static Sync.UserMessage parseOutgoingMessage(PubKey senderId, Operation operation) {
        try {
            var userOperation = Sync.UserOperation.parseFrom(operation.payload);
            if (userOperation.getKind() != Sync.UserOperation.Kind.DIRECT_MESSAGE)
                return null;
            var message = Sync.UserMessage.parseFrom(userOperation.getMessagePayload());
            if (message.getType() == Sync.UserMessage.Type.UNRECOGNIZED)
                throw new IllegalArgumentException("Unknown user message type");
            boolean invitation = message.getType() == Sync.UserMessage.Type.SHARED_FOLDER_INVITATION
                    || message.getType() == Sync.UserMessage.Type.MESSAGE_CHANNEL_INVITATION
                    || message.getType() == Sync.UserMessage.Type.TODO_LIST_INVITATION;
            if (userOperation.getMessageId().isBlank()
                    || !message.getMessageId().equals(userOperation.getMessageId())
                    || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                    || !message.getRecipientUserId().equals(userOperation.getRecipientUserId())
                    || invitation != (message.getInvitedSyncUnitId().size() == 32))
                throw new IllegalArgumentException("Direct message identity does not match its operation");
            return message;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid direct message operation", e);
        }
    }

    public void inviteToSyncUnit(SyncUnitId id, PubKey actorId, PubKey inviteeId,
            SyncUnitAuthorization.Role role) {
        if (requireSyncUnit(id).home)
            throw new IllegalArgumentException("The home SyncUnit cannot be shared");
        appendSyncUnitOperation(id, actorId, SyncUnitAuthorization.Action.INVITE, inviteeId, role);
        for (var peer : List.copyOf(network.nodes.values()))
            if (peer != this && peer.users.containsKey(inviteeId))
                peer.receiveSyncUnitInvitation(syncUnits.get(id));
    }

    public void acceptSyncUnitInvitation(SyncUnitId id, PubKey inviteeId) {
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

    public void changeSyncUnitRole(SyncUnitId id, PubKey actorId, PubKey subject,
            SyncUnitAuthorization.Role role) {
        appendSyncUnitOperation(id, actorId, SyncUnitAuthorization.Action.CHANGE_ROLE, subject, role);
    }

    public void removeFromSyncUnit(SyncUnitId id, PubKey actorId, PubKey subject) {
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

    public void joinSyncUnit(PeerId sourcePeerId, SyncUnitId id, PubKey inviteeId) {
        var source = network.nodes.get(sourcePeerId);
        requireConfiguredUser(inviteeId);
        SyncUnitData sourceUnit;
        if (source != null) {
            sourceUnit = source.requireSyncUnit(id);
            receiveSyncUnitInvitation(sourceUnit);
        } else {
            var result = requestRemoteSyncUnit(sourcePeerId, id, inviteeId, null);
            sourceUnit = result.unit();
            if (sourceUnit == null)
                throw new UnknownSyncUnitException("Remote peer does not host an invitation to this SyncUnit");
            syncUnits.putIfAbsent(id, sourceUnit);
        }
        var localUnit = requireSyncUnit(id);
        if (!localUnit.authorization.pendingInvitations().containsKey(inviteeId))
            throw new IllegalArgumentException("User has no pending invitation to this SyncUnit");
        localUnit.clock = Math.max(localUnit.clock, maxSyncUnitClock(sourceUnit));
        clock = Math.max(clock, maxSyncUnitClock(sourceUnit));
        acceptSyncUnitInvitation(id, inviteeId);
        if (source != null)
            mergeSyncUnit(sourceUnit, source.nodeKey());
        else
            syncSyncUnitFrom(sourcePeerId, id);
    }

    public void syncSyncUnitFrom(PeerId sourcePeerId, SyncUnitId id) {
        var source = network.nodes.get(sourcePeerId);
        var unit = requireSyncUnit(id);
        var localMember = users.values().stream()
                .filter(user -> unit.authorization.members().containsKey(user.userId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "No configured user participates in this SyncUnit"));
        if (source == null) {
            var result = requestRemoteSyncUnit(sourcePeerId, id, localMember.userId(), unit);
            if (result.revoked()) {
                var removed = syncUnits.remove(id);
                if (removed != null && removed.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
                    deleteSharedFolderUnit(id);
                recordSyncUnitParticipation(localMember, id, false);
                persistState();
                throw new SyncUnitRevokedException("SyncUnit membership was revoked");
            }
            if (result.unit() == null)
                throw new UnknownSyncUnitException("Remote peer does not host this SyncUnit");
            mergeSyncUnit(result.unit(), nodeKeyFor(sourcePeerId));
            var localUnit = requireSyncUnit(id);
            localUnit.partialContentOperations.keySet().removeAll(result.unit().contentOperations.keySet());
            localUnit.partialContentOperations.putAll(result.unit().partialContentOperations);
            persistState();
            return;
        }
        if (!unit.authorization.members().containsKey(localMember.userId()))
            throw new IllegalArgumentException("No configured user participates in this SyncUnit");
        var sourceUnit = source.syncUnits.get(id);
        if (sourceUnit == null)
            throw new UnknownSyncUnitException("Peer does not host this SyncUnit");
        mergeSyncUnit(sourceUnit, source.nodeKey());
    }

    private record RemoteSyncUnitResult(SyncUnitData unit, boolean revoked) {
    }

    private RemoteSyncUnitResult requestRemoteSyncUnit(PeerId peerId, SyncUnitId id, PubKey requesterId,
            SyncUnitData update) {
        var request = Sync.RemoteSyncRequest.newBuilder()
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(id.bytes()))
                .setRequesterUserPublicKey(com.google.protobuf.ByteString.copyFrom(requesterId.bytes()));
        if (update != null) {
            request.setSyncUnitUpdate(persist(update));
            for (var clock : maxOperationClocks(update).entrySet())
                request.addFromClock(Sync.OperationClock.newBuilder()
                        .setNodeId(clock.getKey()).setClock(clock.getValue()));
            for (var partial : update.partialContentOperations.values()) {
                request.addContentOffsets(Sync.SyncUnitContentOffset.newBuilder()
                        .setNodeId(HexFormat.of().formatHex(partial.id.nodeHash))
                        .setLamportClock(partial.id.clock)
                        .setOffset(partial.receivedLength));
            }
        }
        var response = exchangeRemote(peerId, request.build());
        SyncUnitData unit = response.getSyncUnitFound()
                ? deserializeSyncUnit(response.getSyncUnit())
                : null;
        if (unit != null && update != null)
            unit.partialContentOperations.putAll(update.partialContentOperations);
        if (unit != null && response.getContentTransfersCount() > 0) {
            var transfers = new java.util.ArrayList<ContentTransfer>();
            for (var transfer : response.getContentTransfersList())
                transfers.add(fromProto(transfer));
            unit.receiveContent(transfers);
        }
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
            copy.activeNodeSet.nodes.add(nodeKey);
            copy.activeNodeSet.setVersion(nodeKey, ++copy.clock);
        }
        syncUnits.put(copy.id, copy);
        ensureUnitMapper(copy);
        for (var user : users.values())
            if (copy.authorization.members().containsKey(user.userId()))
                user.participatingSyncUnitIds.add(copy.id.toString());
        persistState();
    }

    private static SyncUnitData copySyncUnit(SyncUnitData source, boolean includeContent) {
        return copySyncUnit(source, includeContent, includeContent);
    }

    private static SyncUnitData copySyncUnit(SyncUnitData source, boolean includeContent,
            boolean includePartialContent) {
        var copy = new SyncUnitData(source.id, source.type, source.creator);
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

    private void mergeSyncUnit(SyncUnitData incoming, String senderNodeId) {
        var local = syncUnits.get(incoming.id);
        if (local == null) {
            installSyncUnit(incoming, false);
            local = requireSyncUnit(incoming.id);
        }
        if (local.type != incoming.type || !local.creator.equals(incoming.creator))
            throw new IllegalArgumentException("SyncUnit identity metadata does not match");
        var authorizationById = new java.util.HashMap<String, SyncUnitAuthorization.Operation>();
        for (var operation : local.authorizationOperations)
            authorizationById.put(authorizationOperationKey(operation), operation);
        for (var operation : incoming.authorizationOperations) {
            var previous = authorizationById.putIfAbsent(authorizationOperationKey(operation), operation);
            if (previous != null && !sameAuthorizationOperation(previous, operation))
                throw new IllegalArgumentException("Conflicting authorization operations share an ID");
        }

        var validation = new SyncUnitData(local.id, local.type, local.creator);
        validation.authorizationOperations.addAll(authorizationById.values());
        validation.replayAuthorization();
        boolean member = users.values().stream()
                .anyMatch(user -> validation.authorization.members().containsKey(user.userId()));
        if (!member)
            throw new IllegalArgumentException("This node is not a member of the SyncUnit");
        var newContent = new java.util.ArrayList<SyncUnitContentOperation>();
        validation.contentOperations.putAll(local.contentOperations);
        for (var entry : incoming.contentOperations.entrySet()) {
            var previous = validation.contentOperations.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null && !sameContentOperation(previous, entry.getValue()))
                throw new IllegalArgumentException("Conflicting content operations share an ID");
            if (previous == null)
                newContent.add(entry.getValue());
        }
        validation.replayContent();

        local.authorizationOperations.clear();
        local.authorizationOperations.addAll(validation.authorizationOperations);
        local.replayAuthorization();
        local.contentOperations.putAll(incoming.contentOperations);
        local.replayContent();
        if (local.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
            newContent.forEach(this::applySharedFolderContent);
        local.clock = Math.max(local.clock, incoming.clock);
        var targetUnit = local;
        if (!targetUnit.id.equals(SyncUnitId.forUser(targetUnit.creator)))
            targetUnit.activeNodeSet.mergeFrom(incoming.activeNodeSet, nodeKey, senderNodeId,
                    () -> ++targetUnit.clock);
        clock = Math.max(clock, maxSyncUnitClock(incoming));
        installSyncUnit(local, true, true);
    }

    private static Map<String, Long> maxOperationClocks(SyncUnitData unit) {
        var result = new TreeMap<String, Long>();
        for (var operation : unit.contentOperations.values())
            result.merge("c:" + HexFormat.of().formatHex(operation.nodeHash()), operation.lamportClock(), Math::max);
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

    private static String authorizationOperationKey(SyncUnitAuthorization.Operation operation) {
        return operation.lamportClock() + ":" + HexFormat.of().formatHex(operation.nodeHash());
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

    public void putSharedFile(SyncUnitId id, PubKey authorId, String path, byte[] content) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_FILE_SET,
                validateSyncUnitKey(path), content);
    }

    public byte[] readSharedFile(SyncUnitId id, PubKey readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureSharedFolderMapper();
        long root = unitRoots.get(SharedFolderPersistence.SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        return SharedFolderPersistence.readFile(dataStorage, sharedFolderMapper, root, id.toString(),
                validateSyncUnitKey(path));
    }

    public void forEachSharedFile(SyncUnitId id, PubKey readerId,
            java.util.function.BiConsumer<String, Long> consumer) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureSharedFolderMapper();
        long root = unitRoots.get(SharedFolderPersistence.SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        SharedFolderPersistence.forEachFile(dataStorage, sharedFolderMapper, root, id.toString(), consumer);
    }

    public List<SharedFileInfo> listSharedFileInfos(SyncUnitId id, PubKey readerId) {
        var result = new java.util.ArrayList<SharedFileInfo>();
        forEachSharedFile(id, readerId, (path, size) -> result.add(new SharedFileInfo(path, size)));
        result.sort(Comparator.comparing(SharedFileInfo::path));
        return List.copyOf(result);
    }

    long sharedFileSize(SyncUnitId id, PubKey readerId, String path) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        ensureSharedFolderMapper();
        long root = unitRoots.get(SharedFolderPersistence.SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        return SharedFolderPersistence.fileSize(dataStorage, sharedFolderMapper, root, id.toString(),
                validateSyncUnitKey(path));
    }

    public record SharedFileInfo(String path, long size) {
    }

    private void ensureSharedFolderMapper() {
        if (sharedFolderMapper == null)
            sharedFolderMapper = new GuidMapper(dataStorage, unitRoots,
                    SharedFolderPersistence.SHARED_FOLDER_UNIT_ID, sharedFolderNextNodeId);
    }

    private void applySharedFolderContent(SyncUnitContentOperation operation) {
        if (operation.kind() != SyncUnitContentOperation.Kind.SHARED_FILE_SET
                && operation.kind() != SyncUnitContentOperation.Kind.SHARED_FILE_DELETE)
            return;
        ensureSharedFolderMapper();
        switch (operation.kind()) {
            case SHARED_FILE_SET -> SharedFolderPersistence.putFile(dataStorage, unitRoots, sharedFolderMapper,
                    operation.unitId().toString(), operation.key(), operation.payload());
            case SHARED_FILE_DELETE -> SharedFolderPersistence.deleteFile(dataStorage, unitRoots,
                    sharedFolderMapper, operation.unitId().toString(), operation.key());
            default -> throw new IllegalStateException("Not a shared-folder content operation");
        }
    }

    private void deleteSharedFolderUnit(SyncUnitId id) {
        ensureSharedFolderMapper();
        SharedFolderPersistence.deleteUnit(dataStorage, unitRoots, sharedFolderMapper, id.toString());
    }

    public void deleteSharedFile(SyncUnitId id, PubKey authorId, String path) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.SHARED_FOLDER);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.SHARED_FILE_DELETE,
                validateSyncUnitKey(path), new byte[0]);
    }

    public void putTodoItem(SyncUnitId id, PubKey authorId, String itemId, byte[] content) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.TODO_SET,
                validateSyncUnitKey(itemId), content);
    }

    public byte[] readTodoItem(SyncUnitId id, PubKey readerId, String itemId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        byte[] content = unit.todoItems.get(validateSyncUnitKey(itemId));
        return content == null ? null : content.clone();
    }

    public Map<String, byte[]> listTodoItems(SyncUnitId id, PubKey readerId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        var result = new TreeMap<String, byte[]>();
        unit.todoItems.forEach((itemId, content) -> result.put(itemId, content.clone()));
        return java.util.Collections.unmodifiableMap(result);
    }

    public void deleteTodoItem(SyncUnitId id, PubKey authorId, String itemId) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.TODO_LIST);
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.TODO_DELETE,
                validateSyncUnitKey(itemId), new byte[0]);
    }

    public String sendChannelMessage(SyncUnitId id, PubKey authorId, Sync.SyncUnitMessage message) {
        requireSyncUnitType(id, SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        String messageId = java.util.UUID.randomUUID().toString();
        recordSyncUnitContent(id, authorId, SyncUnitContentOperation.Kind.CHANNEL_MESSAGE,
                messageId, message.toByteArray());
        return messageId;
    }

    public List<ChannelMessageEntry> getChannelMessages(SyncUnitId id, PubKey readerId) {
        var unit = requireSyncUnitType(id, SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
        requireSyncUnitAccess(unit, readerId, SyncUnitAuthorization.Action.VIEW);
        return unit.channelMessages.stream().map(operation -> {
            try {
                return new ChannelMessageEntry(operation.key(), operation.author(), operation.lamportClock(),
                        Sync.SyncUnitMessage.parseFrom(operation.payload()));
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalStateException("Invalid persisted channel message", e);
            }
        }).sorted(Comparator.comparingLong(ChannelMessageEntry::lamportClock)
                .thenComparing(ChannelMessageEntry::messageId))
                .toList();
    }

    public record ChannelMessageEntry(String messageId, PubKey author, long lamportClock,
            Sync.SyncUnitMessage message) {
    }

    private SyncUnitContentOperation recordSyncUnitContent(SyncUnitId id, PubKey authorId,
            SyncUnitContentOperation.Kind kind, String key, byte[] payload) {
        var unit = requireSyncUnit(id);
        var author = requireConfiguredUser(authorId);
        var action = switch (kind) {
            case SHARED_FILE_SET, SHARED_FILE_DELETE, TODO_SET, TODO_DELETE ->
                SyncUnitAuthorization.Action.MODIFY;
            case CHANNEL_MESSAGE -> SyncUnitAuthorization.Action.SEND_MESSAGE;
        };
        requireSyncUnitAccess(unit, authorId, action);
        var operation = SyncUnitContentOperation.sign(id, ++unit.clock, nodeHash, author.userKey,
                kind, key, payload);
        unit.contentOperations.put(new OperationId(operation.lamportClock(), operation.nodeHash()), operation);
        unit.replayContent();
        if (unit.type == SyncUnitAuthorization.Type.SHARED_FOLDER)
            applySharedFolderContent(operation);
        persistState();
        return operation;
    }

    private void requireSyncUnitAccess(SyncUnitData unit, PubKey userId, SyncUnitAuthorization.Action action) {
        SyncUnitAuthorization.requireAuthorized(unit.type, unit.creator, unit.authorization.members(), userId, action);
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

    private SyncUnitAuthorization.Operation appendSyncUnitOperation(SyncUnitId id, PubKey actorId,
            SyncUnitAuthorization.Action action, PubKey subject, SyncUnitAuthorization.Role role) {
        var unit = requireSyncUnit(id);
        var actor = requireConfiguredUser(actorId);
        var operation = SyncUnitAuthorization.Operation.sign(id, ++unit.clock, nodeHash, actor.userKey,
                action, subject, role);
        unit.authorizationOperations.add(operation);
        unit.replayAuthorization();
        if (unit.authorization.rejectedOperations().contains(operation)) {
            unit.authorizationOperations.remove(unit.authorizationOperations.size() - 1);
            unit.replayAuthorization();
            throw new IllegalArgumentException("User is not authorized to " + action + " this " + unit.type);
        }
        persistState();
        return operation;
    }

    private NodeUserData requireConfiguredUser(PubKey userId) {
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
            var id = SyncUnitId.create(nodeHash, ++syncUnitCounter.value);
            user.homeSyncUnitId = id;
        }
        var id = user.homeSyncUnitId;
        var unit = syncUnits.get(id);
        if (unit == null) {
            unit = new SyncUnitData(id, SyncUnitAuthorization.Type.SHARED_FOLDER, user.userId());
            unit.home = true;
            unit.activeNodeSet = user.activeNodeSet;
            unit.clock = user.clock;
            syncUnits.put(id, unit);
        }
        unit.home = true;
        unit.activeNodeSet = user.activeNodeSet;
        user.participatingSyncUnitIds.add(id.toString());
    }

    private void ensureUnitMapper(SyncUnitData unit) {
        if (unit.type != SyncUnitAuthorization.Type.SHARED_FOLDER || unit.home || unit.guidMapper != null)
            return;
        unit.guidMapper = new GuidMapper(dataStorage, unitRoots, unit.id, unit.nextNodeId);
    }

    private void ensureUserSyncUnit(NodeUserData user) {
        var id = user.syncUnitId();
        var unit = syncUnits.putIfAbsent(id, new SyncUnitData(id, SyncUnitAuthorization.Type.USER, user.userId()));
        if (unit != null) {
            unit.clock = Math.max(unit.clock, user.clock);
            unit.activeNodeSet = user.activeNodeSet;
        }
        user.participatingSyncUnitIds.add(id.toString());
        applyParticipationOperations(user);
    }

    void applyParticipationOperations(NodeUserData user) {
        for (var operation : user.allOperations().values()) {
            try {
                var userOperation = Sync.UserOperation.parseFrom(operation.payload);
                if (userOperation.getKind() == Sync.UserOperation.Kind.SET_SHARED_FOLDER_LABEL) {
                    var unitIdBytes = userOperation.getSyncUnitId().toByteArray();
                    if (unitIdBytes.length != 32 || userOperation.getName().isBlank())
                        throw new IllegalArgumentException("Invalid shared-folder label operation");
                    var label = userOperation.getName();
                    if (label.equals("home"))
                        throw new IllegalArgumentException("The label 'home' is reserved");
                    user.sharedFolderLabels.put(SyncUnitId.fromBytes(unitIdBytes).toString(), label);
                    continue;
                }
                if (userOperation.getKind() != Sync.UserOperation.Kind.PARTICIPATE_SYNC_UNIT
                        && userOperation.getKind() != Sync.UserOperation.Kind.LEAVE_SYNC_UNIT)
                    continue;
                var unitIdBytes = userOperation.getSyncUnitId().toByteArray();
                if (unitIdBytes.length != 32)
                    throw new IllegalArgumentException("Invalid SyncUnit participation operation");
                var unitId = SyncUnitId.fromBytes(unitIdBytes).toString();
                if (userOperation.getKind() == Sync.UserOperation.Kind.PARTICIPATE_SYNC_UNIT)
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
        var operation = Sync.UserOperation.newBuilder()
                .setKind(participating ? Sync.UserOperation.Kind.PARTICIPATE_SYNC_UNIT
                        : Sync.UserOperation.Kind.LEAVE_SYNC_UNIT)
                .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(id.bytes()))
                .build();
        recordLocalOperation(user, operation.toByteArray());
        if (participating)
            user.participatingSyncUnitIds.add(id.toString());
        else
            user.participatingSyncUnitIds.remove(id.toString());
        new NodeUserHandle(this, user).rebuildCurrentState();
    }

    public NodeUserHandle getUserByName(String localName) {
        return users.values().stream().filter(user -> user.localName.equals(localName)).findFirst()
                .map(user -> new NodeUserHandle(this, user))
                .orElseThrow(() -> new IllegalArgumentException("Unknown local user name: " + localName));
    }

    public NodeUserHandle getUser(PubKey userId) {
        return new NodeUserHandle(this, requireConfiguredUser(userId));
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
        for (var user : List.copyOf(users.values())) {
            for (String unitIdHex : List.copyOf(user.participatingSyncUnitIds)) {
                var unitId = SyncUnitId.fromBytes(HexFormat.of().parseHex(unitIdHex));
                var unit = syncUnits.get(unitId);
                if (unit == null || unit.type == SyncUnitAuthorization.Type.USER)
                    continue;
                boolean restrictToActiveNodes = unit.activeNodeSet.nodes.size() > 1;
                for (PeerId remotePeerId : peers) {
                    if (restrictToActiveNodes && !unit.activeNodeSet.nodes.contains(nodeKeyFor(remotePeerId)))
                        continue;
                    Node localPeer = network.nodes.get(remotePeerId);
                    if (localPeer != null && !localPeer.syncUnits.containsKey(unitId))
                        continue;
                    try {
                        syncSyncUnitFrom(remotePeerId, unitId);
                    } catch (UnknownSyncUnitException | SyncUnitRevokedException ignored) {
                    }
                }
            }
        }
        deliverPendingMessages();
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
        requireUserUnitModification(data.userId());
        var id = new OperationId(++data.clock, nodeHash);
        clock = Math.max(clock, data.clock);
        var userUnit = syncUnits.get(data.syncUnitId());
        if (userUnit != null)
            userUnit.clock = Math.max(userUnit.clock, data.clock);
        byte[] signature = data.userKey.sign(operationSigningBytes(id, payload));
        data.localOperationTree.put(id, new Operation(id, payload, signature));
        data.maxOperationClock.put(nodeKey, id.clock);
        persistState();
    }

    private static void requireUserUnitModification(PubKey author) {
        SyncUnitAuthorization.requireAuthorized(SyncUnitAuthorization.Type.USER, author, Map.of(), author,
                SyncUnitAuthorization.Action.MODIFY);
    }

    private static byte[] operationSigningBytes(OperationId id, byte[] payload) {
        return ByteBuffer.allocate(Long.BYTES + id.nodeHash.length + payload.length)
                .putLong(id.clock).put(id.nodeHash).put(payload).array();
    }

    private static boolean verifyOperationSignature(PubKey author, OperationId id, byte[] payload,
            byte[] signature) {
        try {
            return author.verify(operationSigningBytes(id, payload), signature);
        } catch (RuntimeException invalidSignature) {
            return false;
        }
    }

    byte[] nextEntityId(NodeUserData user) {
        return ByteBuffer.allocate(nodeHash.length + Long.BYTES)
                .put(nodeHash).putLong(++user.entityCounter).array();
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

    private void persistUserFileTrees() {
        for (var user : users.values()) {
            if (user.guidMapper == null)
                user.guidMapper = new GuidMapper(dataStorage, unitRoots, user.fileUnitId(), user.nextNodeId);
            unitRoots.put(user.fileUnitId(), UnitRootStore.FILE_TREE, user.fileSystem.rootOffset());
            user.nextNodeId = user.guidMapper.nextNodeId();
            user.guidMapper.flush();
        }
        ensureSharedFolderMapper();
        sharedFolderNextNodeId = sharedFolderMapper.nextNodeId();
        sharedFolderMapper.flush();
    }

    private static Sync.UserOperation parseUserOperation(Operation operation) {
        try {
            return Sync.UserOperation.parseFrom(operation.payload);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid user operation payload", e);
        }
    }

    private void persistUserOperationTrees() {
        for (var user : users.values()) {
            persistOperationTree(user.syncUnitId(), UnitRootStore.ACCEPTED_OPERATIONS, user.userOperationTree);
            persistOperationTree(user.syncUnitId(), UnitRootStore.LOCAL_OPERATIONS, user.userLocalOperationTree);
            persistOperationTree(user.syncUnitId(), UnitRootStore.STAGING_OPERATIONS, user.userStagingTree);
            if (user.homeSyncUnitId != null) {
                persistOperationTree(user.homeSyncUnitId, UnitRootStore.ACCEPTED_OPERATIONS,
                        user.homeOperationTree);
                persistOperationTree(user.homeSyncUnitId, UnitRootStore.LOCAL_OPERATIONS,
                        user.homeLocalOperationTree);
                persistOperationTree(user.homeSyncUnitId, UnitRootStore.STAGING_OPERATIONS,
                        user.homeStagingTree);
            }
        }
    }

    private void persistOperationTree(SyncUnitId unitId, int kind, TreeMap<OperationId, Operation> operations) {
        var tree = new OperationTree(dataStorage, unitRoots.get(unitId, kind));
        for (var stored : tree.values()) {
            if (!operations.containsKey(stored.id))
                tree.remove(stored.id);
        }
        for (var operation : operations.values()) {
            if (!tree.contains(operation.id))
                tree.put(operation.id, operation);
        }
        unitRoots.put(unitId, kind, tree.rootOffset());
    }

    private void persistSyncUnitOperationTrees() {
        for (var unit : syncUnits.values()) {
            var authorization = new TreeMap<OperationId, Operation>();
            for (var operation : unit.authorizationOperations) {
                var id = new OperationId(operation.lamportClock(), operation.nodeHash());
                authorization.put(id, new Operation(id, persistAuthOperation(operation).toByteArray(),
                        operation.signature()));
            }
            persistOperationTree(unit.id, UnitRootStore.AUTHORIZATION_OPERATIONS, authorization);

            var content = new TreeMap<OperationId, Operation>();
            unit.contentOperations.forEach((id, operation) -> content.put(id,
                    new Operation(id, persistContentOperation(operation).toByteArray(), operation.signature())));
            persistOperationTree(unit.id, UnitRootStore.CONTENT_OPERATIONS, content);
        }
    }

    private void loadSyncUnitOperationTrees(SyncUnitData unit) {
        unit.authorizationOperations.clear();
        var authorization = new OperationTree(dataStorage, unitRoots.get(unit.id, UnitRootStore.AUTHORIZATION_OPERATIONS));
        for (var operation : authorization.values()) {
            var stored = parseAuthOperation(operation.payload);
            unit.authorizationOperations.add(stored);
        }
        unit.replayAuthorization();

        unit.contentOperations.clear();
        var content = new OperationTree(dataStorage, unitRoots.get(unit.id, UnitRootStore.CONTENT_OPERATIONS));
        for (var operation : content.values()) {
            var stored = parseContentOperation(operation.payload);
            unit.contentOperations.put(new OperationId(stored.lamportClock(), stored.nodeHash()), stored);
        }
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

    private void loadOperationTree(SyncUnitId unitId, int kind, PubKey author,
            TreeMap<OperationId, Operation> target) {
        target.clear();
        var tree = new OperationTree(dataStorage, unitRoots.get(unitId, kind));
        for (var operation : tree.values()) {
            if (!verifyOperationSignature(author, operation.id, operation.payload, operation.signature))
                throw new IllegalStateException("Persisted user operation has an invalid signature");
            target.put(operation.id, operation);
        }
    }

    void persistState() {
        if (restoringState)
            return;
        var state = Sync.NodeState.newBuilder()
                .setPeerId(com.google.protobuf.ByteString.copyFrom(peerId.getBytes()))
                .setPeerPrivateKey(com.google.protobuf.ByteString.copyFrom(peerPrivateKey.bytes()))
                .setClock(clock)
                .setSharedFolderNextNodeId(sharedFolderNextNodeId);
        for (var user : users.values()) {
            var storedUser = Sync.NodeUserState.newBuilder()
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
                    Sync.SharedFolderLabel.newBuilder()
                            .setSyncUnitId(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(unitId)))
                            .setLabel(label)));
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
            user.participatingSyncUnitIds.forEach(id -> storedUser.addParticipatingSyncUnitIds(
                    com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(id))));
            user.deliveredMessageIds.forEach(storedUser::addDeliveredMessageIds);
            user.inboxMessages.values().forEach(message -> storedUser.addInboxMessages(
                    Sync.StoredUserMessage.newBuilder()
                            .setMessageId(com.google.protobuf.ByteString.copyFromUtf8(message.messageId))
                            .setSenderUserId(message.message.getSenderUserId())
                            .setLamportClock(message.operationId.clock)
                            .setNodeHash(com.google.protobuf.ByteString.copyFrom(message.operationId.nodeHash))
                            .setOperationPayload(com.google.protobuf.ByteString.copyFrom(message.operationPayload))
                            .setSignature(com.google.protobuf.ByteString.copyFrom(message.signature))));
            state.addUsers(storedUser);
        }
        persistUserOperationTrees();
        persistUserFileTrees();
        persistSyncUnitOperationTrees();
        syncUnits.values().forEach(unit -> state.addSyncUnits(persistMetadata(unit)));
        storage.put(BlobStorage.NODE_STATE_ID, state.build());
        unitRoots.flush();
        storage.commit();
    }

    private static Sync.PersistedOperation persist(Operation operation) {
        return Sync.PersistedOperation.newBuilder()
                .setLamportClock(operation.id.clock)
                .setNodeHash(com.google.protobuf.ByteString.copyFrom(operation.id.nodeHash))
                .setPayload(com.google.protobuf.ByteString.copyFrom(operation.payload))
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature))
                .build();
    }

    private static Sync.RemoteSyncUnitContentTransfer toProto(ContentTransfer transfer) {
        return Sync.RemoteSyncUnitContentTransfer.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(transfer.unitId.bytes()))
                .setLamportClock(transfer.id.clock)
                .setNodeHash(com.google.protobuf.ByteString.copyFrom(transfer.id.nodeHash))
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

    private static ContentTransfer fromProto(Sync.RemoteSyncUnitContentTransfer transfer) {
        int kind = transfer.getKind();
        if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
            throw new IllegalArgumentException("Invalid SyncUnit content transfer kind");
        return new ContentTransfer(SyncUnitId.fromBytes(transfer.getUnitId().toByteArray()),
                new OperationId(transfer.getLamportClock(), transfer.getNodeHash().toByteArray()),
                PubKey.from(transfer.getAuthorPublicKey().toByteArray()),
                SyncUnitContentOperation.Kind.values()[kind], transfer.getKey(), transfer.getTotalLength(),
                transfer.getOffset(), transfer.getBytes().toByteArray(), transfer.getSignature().toByteArray(),
                transfer.getComplete());
    }

    private static String contentOffsetKey(OperationId id) {
        return HexFormat.of().formatHex(id.nodeHash) + ":" + id.clock;
    }

    private static Sync.PersistedSyncUnit persistMetadata(SyncUnitData unit) {
        var result = Sync.PersistedSyncUnit.newBuilder()
                .setId(com.google.protobuf.ByteString.copyFrom(unit.id.bytes()))
                .setType(unit.type.ordinal())
                .setCreatorPublicKey(com.google.protobuf.ByteString.copyFrom(unit.creator.bytes()))
                .setClock(unit.clock)
                .setEntityCounter(unit.entityCounter)
                .setActiveNodeSet(persist(unit.activeNodeSet))
                .setHome(unit.home)
                .setNextNodeId(unit.nextNodeId);
        unit.partialContentOperations.values().forEach(partial -> result.addPartialContentOperations(
                Sync.PartialSyncUnitContentOperationState.newBuilder()
                        .setUnitId(com.google.protobuf.ByteString.copyFrom(partial.unitId.bytes()))
                        .setLamportClock(partial.id.clock)
                        .setNodeHash(com.google.protobuf.ByteString.copyFrom(partial.id.nodeHash))
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
            Sync.PartialSyncUnitContentOperationState stored) {
        int kind = stored.getKind();
        if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
            throw new IllegalStateException("Invalid persisted partial SyncUnit content operation");
        byte[] received = stored.getReceivedBytes().toByteArray();
        if (received.length > stored.getTotalLength())
            throw new IllegalStateException("Invalid persisted partial SyncUnit content operation");
        var unitId = SyncUnitId.fromBytes(stored.getUnitId().toByteArray());
        var id = new OperationId(stored.getLamportClock(), stored.getNodeHash().toByteArray());
        var partial = new PartialContentOperation(unitId, id,
                PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                SyncUnitContentOperation.Kind.values()[kind], stored.getKey(), stored.getTotalLength(),
                stored.getSignature().toByteArray());
        System.arraycopy(received, 0, partial.payload, 0, received.length);
        partial.receivedLength = received.length;
        return partial;
    }

    private static SyncUnitData deserializeSyncUnitMetadata(Sync.PersistedSyncUnit stored) {
        if (stored.getType() < 0 || stored.getType() >= SyncUnitAuthorization.Type.values().length)
            throw new IllegalStateException("Invalid persisted SyncUnit type");
        var id = SyncUnitId.fromBytes(stored.getId().toByteArray());
        var unit = new SyncUnitData(id, SyncUnitAuthorization.Type.values()[stored.getType()],
                PubKey.from(stored.getCreatorPublicKey().toByteArray()));
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

    private static Sync.PersistedSyncUnitAuthorizationOperation persistAuthOperation(
            SyncUnitAuthorization.Operation operation) {
        return Sync.PersistedSyncUnitAuthorizationOperation.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(operation.unitId().bytes()))
                .setLamportClock(operation.lamportClock())
                .setNodeHash(com.google.protobuf.ByteString.copyFrom(operation.nodeHash()))
                .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(operation.author().bytes()))
                .setAction(operation.action().ordinal())
                .setSubjectPublicKey(operation.subject() == null ? com.google.protobuf.ByteString.EMPTY
                        : com.google.protobuf.ByteString.copyFrom(operation.subject().bytes()))
                .setRole(operation.role() == null ? 0 : operation.role().ordinal() + 1)
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature()))
                .build();
    }

    private static SyncUnitAuthorization.Operation parseAuthOperation(byte[] payload) {
        try {
            var stored = Sync.PersistedSyncUnitAuthorizationOperation.parseFrom(payload);
            int action = stored.getAction();
            int role = stored.getRole();
            if (action < 0 || action >= SyncUnitAuthorization.Action.values().length
                    || role < 0 || role > SyncUnitAuthorization.Role.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit authorization operation");
            var subject = stored.getSubjectPublicKey().toByteArray();
            return new SyncUnitAuthorization.Operation(
                    SyncUnitId.fromBytes(stored.getUnitId().toByteArray()),
                    stored.getLamportClock(), stored.getNodeHash().toByteArray(),
                    PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                    SyncUnitAuthorization.Action.values()[action],
                    subject.length == 0 ? null : PubKey.from(subject),
                    role == 0 ? null : SyncUnitAuthorization.Role.values()[role - 1],
                    stored.getSignature().toByteArray());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted SyncUnit authorization operation", e);
        }
    }

    private static Sync.PersistedSyncUnitContentOperation persistContentOperation(
            SyncUnitContentOperation operation) {
        return Sync.PersistedSyncUnitContentOperation.newBuilder()
                .setUnitId(com.google.protobuf.ByteString.copyFrom(operation.unitId().bytes()))
                .setLamportClock(operation.lamportClock())
                .setNodeHash(com.google.protobuf.ByteString.copyFrom(operation.nodeHash()))
                .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(operation.author().bytes()))
                .setKind(operation.kind().ordinal())
                .setKey(operation.key())
                .setPayload(com.google.protobuf.ByteString.copyFrom(operation.payload()))
                .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature()))
                .build();
    }

    private static SyncUnitContentOperation parseContentOperation(byte[] payload) {
        try {
            var stored = Sync.PersistedSyncUnitContentOperation.parseFrom(payload);
            int kind = stored.getKind();
            if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit content operation");
            return new SyncUnitContentOperation(
                    SyncUnitId.fromBytes(stored.getUnitId().toByteArray()),
                    stored.getLamportClock(), stored.getNodeHash().toByteArray(),
                    PubKey.from(stored.getAuthorPublicKey().toByteArray()),
                    SyncUnitContentOperation.Kind.values()[kind], stored.getKey(),
                    stored.getPayload().toByteArray(), stored.getSignature().toByteArray());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted SyncUnit content operation", e);
        }
    }

    private static Sync.PersistedSyncUnit persist(SyncUnitData unit) {
        var result = Sync.PersistedSyncUnit.newBuilder()
                .setId(com.google.protobuf.ByteString.copyFrom(unit.id.bytes()))
                .setType(unit.type.ordinal())
                .setCreatorPublicKey(com.google.protobuf.ByteString.copyFrom(unit.creator.bytes()))
                .setClock(unit.clock)
                .setEntityCounter(unit.entityCounter)
                .setActiveNodeSet(persist(unit.activeNodeSet))
                .setHome(unit.home)
                .setNextNodeId(unit.nextNodeId);
        unit.authorizationOperations.forEach(operation -> {
            result.addAuthorizationOperations(
                Sync.PersistedSyncUnitAuthorizationOperation.newBuilder()
                        .setUnitId(com.google.protobuf.ByteString.copyFrom(operation.unitId().bytes()))
                        .setLamportClock(operation.lamportClock())
                        .setNodeHash(com.google.protobuf.ByteString.copyFrom(operation.nodeHash()))
                        .setAuthorPublicKey(com.google.protobuf.ByteString.copyFrom(operation.author().bytes()))
                        .setAction(operation.action().ordinal())
                        .setSubjectPublicKey(operation.subject() == null ? com.google.protobuf.ByteString.EMPTY
                                : com.google.protobuf.ByteString.copyFrom(operation.subject().bytes()))
                        .setRole(operation.role() == null ? 0 : operation.role().ordinal() + 1)
                        .setSignature(com.google.protobuf.ByteString.copyFrom(operation.signature())));
        });
        return result.build();
    }

    static Sync.ActiveNodeSetState persist(ActiveNodeSet activeNodeSet) {
        var result = Sync.ActiveNodeSetState.newBuilder();
        if (activeNodeSet.version != null)
            result.setVersionNodeId(activeNodeSet.version.nodeId).setVersionClock(activeNodeSet.version.clock);
        activeNodeSet.nodes.forEach(result::addNodeIds);
        activeNodeSet.removedNodes.forEach(result::addRemovedNodeIds);
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
        sharedFolderNextNodeId = state.getSharedFolderNextNodeId();
        network.nodes.put(peerId, this);
        restoringState = true;
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
                    if (participatingId.size() != 32)
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
                else
                    user.fileSystem = UserFileTree.replayFromState(dataStorage, user.guidMapper,
                            user.snapshotState, user.allOperations().values());
                rebuildInboxMessages(user);
                if (!user.allOperations().isEmpty()) {
                    var replayed = UserState.contactsFromState(user.snapshotState, user.userId());
                    for (var operation : user.allOperations().values())
                        replayed.apply(parseUserOperation(operation));
                    user.contacts = replayed;
                }
            }
            sharedFolderMapper = new GuidMapper(dataStorage, unitRoots,
                    SharedFolderPersistence.SHARED_FOLDER_UNIT_ID, sharedFolderNextNodeId);
        } finally {
            restoringState = false;
        }
        persistState();
    }

    private void loadSyncUnit(Sync.PersistedSyncUnit stored) {
        var unit = deserializeSyncUnitMetadata(stored);
        if (syncUnits.putIfAbsent(unit.id, unit) != null)
            throw new IllegalStateException("Duplicate persisted SyncUnit ID");
        loadSyncUnitOperationTrees(unit);
        ensureUnitMapper(unit);
    }

    private static SyncUnitData deserializeSyncUnit(Sync.PersistedSyncUnit stored) {
        if (stored.getType() < 0 || stored.getType() >= SyncUnitAuthorization.Type.values().length)
            throw new IllegalStateException("Invalid persisted SyncUnit type");
        var id = SyncUnitId.fromBytes(stored.getId().toByteArray());
        var unit = new SyncUnitData(id, SyncUnitAuthorization.Type.values()[stored.getType()],
                PubKey.from(stored.getCreatorPublicKey().toByteArray()));
        unit.clock = stored.getClock();
        unit.entityCounter = stored.getEntityCounter();
        unit.home = stored.getHome();
        unit.nextNodeId = stored.getNextNodeId();
        loadActiveNodeSet(stored.getActiveNodeSet(), unit.activeNodeSet);
        for (var storedOperation : stored.getAuthorizationOperationsList()) {
            int action = storedOperation.getAction();
            int role = storedOperation.getRole();
            if (action < 0 || action >= SyncUnitAuthorization.Action.values().length
                    || role < 0 || role > SyncUnitAuthorization.Role.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit authorization operation");
            var subjectBytes = storedOperation.getSubjectPublicKey().toByteArray();
            var operation = new SyncUnitAuthorization.Operation(
                    SyncUnitId.fromBytes(storedOperation.getUnitId().toByteArray()),
                    storedOperation.getLamportClock(), storedOperation.getNodeHash().toByteArray(),
                    PubKey.from(storedOperation.getAuthorPublicKey().toByteArray()),
                    SyncUnitAuthorization.Action.values()[action],
                    subjectBytes.length == 0 ? null : PubKey.from(subjectBytes),
                    role == 0 ? null : SyncUnitAuthorization.Role.values()[role - 1],
                    storedOperation.getSignature().toByteArray());
            if (!operation.unitId().equals(id) || !operation.hasValidSignature())
                throw new IllegalStateException("Invalid persisted SyncUnit authorization signature");
            unit.authorizationOperations.add(operation);
        }
        unit.replayAuthorization();
        for (var storedOperation : stored.getContentOperationsList()) {
            int kind = storedOperation.getKind();
            if (kind < 0 || kind >= SyncUnitContentOperation.Kind.values().length)
                throw new IllegalStateException("Invalid persisted SyncUnit content operation");
            var operation = new SyncUnitContentOperation(
                    SyncUnitId.fromBytes(storedOperation.getUnitId().toByteArray()),
                    storedOperation.getLamportClock(), storedOperation.getNodeHash().toByteArray(),
                    PubKey.from(storedOperation.getAuthorPublicKey().toByteArray()),
                    SyncUnitContentOperation.Kind.values()[kind], storedOperation.getKey(),
                    storedOperation.getPayload().toByteArray(), storedOperation.getSignature().toByteArray());
            var operationId = new OperationId(operation.lamportClock(), operation.nodeHash());
            if (!operation.unitId().equals(id) || !operation.hasValidSignature()
                    || unit.contentOperations.putIfAbsent(operationId, operation) != null)
                throw new IllegalStateException("Invalid persisted SyncUnit content operation");
        }
        unit.replayContent();
        return unit;
    }

    private static StoredUserMessage loadStoredUserMessage(Sync.StoredUserMessage stored, PubKey recipientId) {
        String messageId = stored.getMessageId().toStringUtf8();
        var operationId = new OperationId(stored.getLamportClock(), stored.getNodeHash().toByteArray());
        byte[] operationPayload = stored.getOperationPayload().toByteArray();
        byte[] signature = stored.getSignature().toByteArray();
        Sync.UserOperation userOperation;
        Sync.UserMessage message;
        try {
            userOperation = Sync.UserOperation.parseFrom(operationPayload);
            if (userOperation.getKind() != Sync.UserOperation.Kind.DIRECT_MESSAGE)
                throw new IllegalStateException("Persisted inbox entry is not a direct message");
            message = Sync.UserMessage.parseFrom(userOperation.getMessagePayload());
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IllegalStateException("Invalid persisted inbox message", e);
        }
        var senderId = PubKey.from(stored.getSenderUserId().toByteArray());
        if (messageId.isBlank() || !messageId.equals(userOperation.getMessageId())
                || !messageId.equals(message.getMessageId())
                || !message.getRecipientUserId().equals(com.google.protobuf.ByteString.copyFrom(recipientId.bytes()))
                || !message.getSenderUserId().equals(com.google.protobuf.ByteString.copyFrom(senderId.bytes()))
                || !verifyOperationSignature(senderId, operationId, operationPayload, signature))
            throw new IllegalStateException("Invalid persisted inbox message identity or signature");
        return new StoredUserMessage(messageId, operationId, operationPayload, signature, message);
    }

    static void loadActiveNodeSet(Sync.ActiveNodeSetState stored, ActiveNodeSet target) {
        if (stored.getVersionClock() != 0 || !stored.getVersionNodeId().isEmpty())
            target.setVersion(stored.getVersionNodeId(), stored.getVersionClock());
        target.nodes.addAll(stored.getNodeIdsList());
        stored.getRemovedNodeIdsList().forEach(target.removedNodes::add);
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
