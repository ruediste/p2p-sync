package com.github.ruediste.p2psync.node;

import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Node.NodeUserData;
import com.github.ruediste.p2psync.proto.Sync;
import com.github.ruediste.p2psync.util.FullGuid;

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
        synchronized (node) {
            var dataRoot = currentView();
            String previousStatus = dataRoot.userStatusMessage;
            byte[] previousRootId = dataRoot.rootDirectoryGuid.clone();
            var previousContacts = new java.util.ArrayList<>(dataRoot.contacts);
            action.accept(dataRoot);
            if (!java.util.Arrays.equals(previousRootId, dataRoot.rootDirectoryGuid))
                throw new IllegalArgumentException("The root directory identity is managed by the filesystem API");
            if (!java.util.Objects.equals(previousStatus, dataRoot.userStatusMessage)) {
                data.fileSystem.statusMessage = dataRoot.userStatusMessage;
                var operation = Sync.UserOperation.newBuilder()
                        .setKind(Sync.UserOperation.Kind.SET_STATUS)
                        .setStatusMessage(dataRoot.userStatusMessage)
                        .build();
                recordOperation(operation);
            }
            applyContactChanges(previousContacts, dataRoot.contacts);
        }
    }

    private void applyContactChanges(List<Contact> previous, List<Contact> updated) {
        var removed = new java.util.LinkedHashMap<String, Contact>();
        for (var contact : previous)
            removed.put(contact.contactId(), contact);
        var updatedById = new java.util.LinkedHashMap<String, Contact>();
        for (var contact : updated)
            updatedById.put(contact.contactId(), contact);

        for (var contactId : previous.stream().map(Contact::contactId)
                .filter(id -> !updatedById.containsKey(id)).toList()) {
            deleteContact(contactId);
            removed.remove(contactId);
        }
        for (var contact : updatedById.values()) {
            var old = removed.get(contact.contactId());
            if (old == null) {
                createContactWithId(contact.contactId(), contact.userId(), contact.firstName(), contact.lastName(),
                        contact.email(), contact.phone(), contact.notes());
            } else if (!old.equals(contact)) {
                updateContact(contact.contactId(), contact.userId(), contact.firstName(), contact.lastName(),
                        contact.email(), contact.phone(), contact.notes());
            }
        }
    }

    public <T> T read(Function<DataUserRoot, T> action) {
        synchronized (node) {
            return action.apply(currentView());
        }
    }

    private DataUserRoot currentView() {
        var result = new DataUserRoot();
        result.userStatusMessage = data.fileSystem.statusMessage;
        result.rootDirectoryGuid = data.rootDirectoryGuid.clone();
        result.contacts = data.contacts.list();
        return result;
    }

    public String sendMessage(PubKey recipientUserId, Sync.UserMessage.Type type, String subject, String body,
            java.util.List<Sync.SyncUnitAttachment> attachments, SyncUnitId invitedSyncUnitId) {
        String messageId;
        synchronized (node) {
            java.util.Objects.requireNonNull(recipientUserId);
            java.util.Objects.requireNonNull(type);
            if (type == Sync.UserMessage.Type.UNRECOGNIZED)
                throw new IllegalArgumentException("Unknown user message type");
            boolean invitation = type == Sync.UserMessage.Type.SHARED_FOLDER_INVITATION
                    || type == Sync.UserMessage.Type.MESSAGE_CHANNEL_INVITATION
                    || type == Sync.UserMessage.Type.TODO_LIST_INVITATION;
            if (invitation != (invitedSyncUnitId != null))
                throw new IllegalArgumentException("Invitation messages require exactly one SyncUnit ID");
            if (invitation) {
                var unit = node.syncUnits.get(invitedSyncUnitId);
                var expectedType = switch (type) {
                    case SHARED_FOLDER_INVITATION -> SyncUnitAuthorization.Type.SHARED_FOLDER;
                    case TODO_LIST_INVITATION -> SyncUnitAuthorization.Type.TODO_LIST;
                    case MESSAGE_CHANNEL_INVITATION -> SyncUnitAuthorization.Type.MESSAGE_CHANNEL;
                    default -> throw new IllegalArgumentException("Not a SyncUnit invitation message");
                };
                if (unit == null || unit.type != expectedType
                        || !unit.authorization.pendingInvitations().containsKey(recipientUserId))
                    throw new IllegalArgumentException(
                            "Invitation message requires a pending invitation for its recipient");
                SyncUnitAuthorization.requireAuthorized(unit.type, unit.creator, unit.authorization.members(),
                        data.userId(), SyncUnitAuthorization.Action.INVITE);
            }
            messageId = UUID.randomUUID().toString();
            var message = Sync.UserMessage.newBuilder()
                    .setMessageId(messageId)
                    .setSenderUserId(com.google.protobuf.ByteString.copyFrom(data.userId().bytes()))
                    .setRecipientUserId(com.google.protobuf.ByteString.copyFrom(recipientUserId.bytes()))
                    .setType(type)
                    .setSubject(subject == null ? "" : subject)
                    .setBody(body == null ? "" : body)
                    .addAllAttachments(attachments == null ? java.util.List.of() : attachments);
            if (invitedSyncUnitId != null)
                message.setInvitedSyncUnitId(com.google.protobuf.ByteString.copyFrom(invitedSyncUnitId.bytes()));
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.DIRECT_MESSAGE)
                    .setMessageId(messageId)
                    .setRecipientUserId(com.google.protobuf.ByteString.copyFrom(recipientUserId.bytes()))
                    .setMessagePayload(message.build().toByteString())
                    .build());
        }
        node.deliverPendingMessages();
        return messageId;
    }

    public String sendChatMessage(PubKey recipientUserId, String body,
            java.util.List<Sync.SyncUnitAttachment> attachments) {
        return sendMessage(recipientUserId, Sync.UserMessage.Type.CHAT, "", body, attachments, null);
    }

    public String sendMailMessage(PubKey recipientUserId, String subject, String body,
            java.util.List<Sync.SyncUnitAttachment> attachments) {
        return sendMessage(recipientUserId, Sync.UserMessage.Type.MAIL, subject, body, attachments, null);
    }

    public void forEachSharedFile(SyncUnitId unitId, java.util.function.BiConsumer<String, Long> consumer) {
        node.forEachSharedFile(unitId, data.userId(), consumer);
    }

    public List<Node.SharedFileInfo> listSharedFileInfos(SyncUnitId unitId) {
        return node.listSharedFileInfos(unitId, data.userId());
    }

    public void setSharedFolderLabel(SyncUnitId unitId, String label) {
        node.setSharedFolderLabel(data.userId(), unitId, label);
    }

    public List<Node.RootEntry> listRoot() {
        return node.listRootEntries(data.userId());
    }

    public List<Contact> listContacts() {
        synchronized (node) {
            return data.contacts.list();
        }
    }

    public List<Contact> searchContacts(String query) {
        synchronized (node) {
            return data.contacts.search(query);
        }
    }

    public Optional<Contact> getContact(String contactId) {
        synchronized (node) {
            return data.contacts.get(contactId);
        }
    }

    public Optional<Contact> findContactByUserId(PubKey userId) {
        synchronized (node) {
            Objects.requireNonNull(userId);
            return data.contacts.findByUserId(userId);
        }
    }

    public Contact createContact(PubKey userId, String firstName, String lastName, String email, String phone,
            String notes) {
        synchronized (node) {
            return createContactWithId(HexFormat.of().formatHex(node.nextEntityId(data)), userId, firstName,
                    lastName, email, phone, notes);
        }
    }

    private Contact createContactWithId(String contactId, PubKey userId, String firstName, String lastName,
            String email, String phone, String notes) {
        validateContactUserId(userId, contactId);
        Contact contact = new Contact(contactId, userId, trim(firstName), trim(lastName), trim(email), trim(phone),
                trim(notes));
        if (contact.isEmpty())
            throw new IllegalArgumentException("A contact needs at least one field");
        recordOperation(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.CONTACT_CREATE)
                .setContact(ContactList.toProto(contact))
                .build());
        return data.contacts.get(contact.contactId())
                .orElseThrow(() -> new IllegalStateException("Created contact is missing from the materialized state"));
    }

    public Contact createContactFromUser(PubKey userId) {
        synchronized (node) {
            Objects.requireNonNull(userId);
            var existing = data.contacts.findByUserId(userId);
            if (existing.isPresent())
                return existing.get();
            return createContact(userId, "", "", "", "", "");
        }
    }

    public Contact updateContact(String contactId, PubKey userId, String firstName, String lastName, String email,
            String phone, String notes) {
        synchronized (node) {
            Contact existing = data.contacts.get(contactId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown contact " + contactId));
            validateContactUserId(userId, contactId);
            Contact updated = new Contact(contactId, userId, trim(firstName), trim(lastName), trim(email), trim(phone),
                    trim(notes));
            if (updated.isEmpty())
                throw new IllegalArgumentException("A contact needs at least one field");

            var operations = new java.util.ArrayList<Sync.UserOperation>();
            if (!Objects.equals(existing.userId(), updated.userId()))
                operations.add(Sync.UserOperation.newBuilder()
                        .setKind(Sync.UserOperation.Kind.CONTACT_UPDATE)
                        .setContactId(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(contactId)))
                        .setContactField(ContactList.protoField(Contact.Field.USER_ID))
                        .setContactUserId(updated.userId() == null ? com.google.protobuf.ByteString.EMPTY
                                : com.google.protobuf.ByteString.copyFrom(updated.userId().bytes()))
                        .build());
            addTextUpdate(operations, contactId, Contact.Field.FIRST_NAME, existing.firstName(),
                    updated.firstName());
            addTextUpdate(operations, contactId, Contact.Field.LAST_NAME, existing.lastName(), updated.lastName());
            addTextUpdate(operations, contactId, Contact.Field.EMAIL, existing.email(), updated.email());
            addTextUpdate(operations, contactId, Contact.Field.PHONE, existing.phone(), updated.phone());
            addTextUpdate(operations, contactId, Contact.Field.NOTES, existing.notes(), updated.notes());
            recordOperations(operations);
            return data.contacts.get(contactId)
                    .orElseThrow(() -> new IllegalStateException("Updated contact is missing from the materialized state"));
        }
    }

    public void deleteContact(String contactId) {
        synchronized (node) {
            if (data.contacts.get(contactId).isEmpty())
                return;
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.CONTACT_DELETE)
                    .setContactId(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(contactId)))
                    .build());
        }
    }

    public String contactLabel(PubKey userId) {
        synchronized (node) {
            if (data.userId().equals(userId))
                return "you";
            var contact = data.contacts.findByUserId(userId);
            if (contact.isPresent())
                return contact.get().displayName();
            var localUser = node.users.get(userId);
            if (localUser != null)
                return localUser.localName;
            return "User " + ContactList.shortKey(userId);
        }
    }

    private void addTextUpdate(List<Sync.UserOperation> operations, String contactId, Contact.Field field,
            String previous, String value) {
        if (Objects.equals(previous, value))
            return;
        operations.add(Sync.UserOperation.newBuilder()
                .setKind(Sync.UserOperation.Kind.CONTACT_UPDATE)
                .setContactId(com.google.protobuf.ByteString.copyFrom(HexFormat.of().parseHex(contactId)))
                .setContactField(ContactList.protoField(field))
                .setContactValue(value)
                .build());
    }

    private void validateContactUserId(PubKey userId, String exceptContactId) {
        if (userId == null)
            return;
        if (userId.equals(data.userId()))
            throw new IllegalArgumentException("A contact cannot reference the owner");
        var existing = data.contacts.findByUserId(userId);
        if (existing.isPresent() && !existing.get().contactId().equals(exceptContactId))
            throw new IllegalArgumentException("User is already linked to contact " + existing.get().displayName());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void recordOperations(List<Sync.UserOperation> operations) {
        if (operations.isEmpty())
            return;
        synchronized (node) {
            for (var operation : operations) {
                data.fileSystem.apply(operation);
                data.contacts.apply(operation);
                node.recordLocalOperation(data, operation.toByteArray());
            }
        }
    }

    public void syncFrom(PeerId otherId) {
        syncFrom(otherId, 100, 1_000_000);
    }

    public void syncFrom(PeerId otherId, int maxOperations, int byteBudget) {
        if (maxOperations <= 0 || byteBudget <= 0)
            throw new IllegalArgumentException("Transfer limits must be positive");
        var peer = node.network.nodes.get(otherId);
        if (peer == null) {
            Sync.RemoteSyncRequest request;
            synchronized (node) {
                request = buildRemoteSyncRequest(maxOperations, byteBudget);
            }
            var response = node.exchangeRemote(otherId, request);
            synchronized (node) {
                applyRemoteSyncResponse(otherId, response);
            }
            node.deliverPendingMessages();
            return;
        }
        synchronized (node) {
            syncFromLocal(peer, maxOperations, byteBudget);
        }
        node.deliverPendingMessages();
    }

    private void syncFromLocal(Node peer, int maxOperations, int byteBudget) {
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
        data.activeNodeSet.mergeFrom(peerData.activeNodeSet, node.nodeKey(), peer.nodeKey(), node::nextClock);
        data.activeNodeSet.updateLeaving(node.nodeKey(), data.maxOperationClock, node::nextClock);
        node.syncHomeActiveNodeSet(data, peerData.activeNodeSet, peer.nodeKey());
        node.persistState();
    }

    Sync.RemoteSyncRequest buildRemoteSyncRequest(int maxOperations, int byteBudget) {
        synchronized (node) {
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
            return Node.signRemoteRequest(request.build(), data.userKey);
        }
    }

    void applyRemoteSyncResponse(PeerId peerId, Sync.RemoteSyncResponse response) {
        synchronized (node) {
            applyRemoteSyncResponseLocked(peerId, response);
        }
    }

    private void applyRemoteSyncResponseLocked(PeerId peerId, Sync.RemoteSyncResponse response) {
        if (!response.getUserFound())
            throw new IllegalArgumentException("Remote peer does not host user " + data.userId());
        // The response advertises every source the remote knows, including nodes this
        // node has never seen. Seed them at clock zero so the next request asks for
        // their operations; a non-zero value would incorrectly claim they are already
        // present locally.
        for (var entry : response.getMaxOperationClockList())
            data.maxOperationClock.putIfAbsent(entry.getNodeId(), 0L);
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
        data.activeNodeSet.mergeFrom(activeNodeSet, node.nodeKey(), Node.nodeKeyFor(peerId), node::nextClock);
        data.activeNodeSet.updateLeaving(node.nodeKey(), data.maxOperationClock, node::nextClock);
        node.syncHomeActiveNodeSet(data, activeNodeSet, Node.nodeKeyFor(peerId));
        node.persistState();
    }

    public record DirectoryEntry(String name, boolean directory) {
    }

    public java.util.List<DirectoryEntry> listDirectory(String path) {
        synchronized (node) {
            var result = new java.util.ArrayList<DirectoryEntry>();
            forEachDirectory(path, result::add);
            return result;
        }
    }

    /**
     * Streams directory entries to the consumer without materializing the list.
     * The node lock is held while iterating; the consumer must not call back
     * into the node or perform blocking work.
     */
    public void forEachDirectory(String path, java.util.function.Consumer<DirectoryEntry> consumer) {
        synchronized (node) {
            data.fileSystem.forEachDirectory(path,
                    entry -> consumer.accept(new DirectoryEntry(entry.name(), entry.directory())));
        }
    }

    public void createDirectory(String path) {
        synchronized (node) {
            FullGuid parentId = data.fileSystem.resolveParent(path);
            String name = data.fileSystem.leafName(path);
            data.fileSystem.requireAvailableName(parentId, name, null);
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.CREATE_DIRECTORY)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(node.nextEntityId(data, data.fileUnitId())))
                    .setParentId(com.google.protobuf.ByteString.copyFrom(parentId.bytes()))
                    .setName(name)
                    .build());
        }
    }

    public void createFile(String path) {
        synchronized (node) {
            FullGuid parentId = data.fileSystem.resolveParent(path);
            String name = data.fileSystem.leafName(path);
            data.fileSystem.requireAvailableName(parentId, name, null);
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.CREATE_FILE)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(node.nextEntityId(data, data.fileUnitId())))
                    .setParentId(com.google.protobuf.ByteString.copyFrom(parentId.bytes()))
                    .setName(name)
                    .build());
        }
    }

    public byte[] readFile(String path) {
        synchronized (node) {
            return data.fileSystem.readFile(path);
        }
    }

    public boolean exists(String path) {
        synchronized (node) {
            return data.fileSystem.exists(path);
        }
    }

    public boolean isDirectory(String path) {
        synchronized (node) {
            return data.fileSystem.isDirectory(path);
        }
    }

    public long fileSize(String path) {
        synchronized (node) {
            return data.fileSystem.fileSize(path);
        }
    }

    public void writeFile(String path, long offset, byte[] bytes) {
        synchronized (node) {
            FullGuid entityId = data.fileSystem.resolveFile(path);
            if (offset < 0 || offset > Integer.MAX_VALUE || bytes.length > Integer.MAX_VALUE - offset)
                throw new IllegalArgumentException("File range exceeds the supported size");
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.WRITE_FILE)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(entityId.bytes()))
                    .setOffset(offset)
                    .setData(com.google.protobuf.ByteString.copyFrom(bytes))
                    .build());
        }
    }

    public void truncateFile(String path, long size) {
        synchronized (node) {
            FullGuid entityId = data.fileSystem.resolveFile(path);
            if (size < 0 || size > Integer.MAX_VALUE)
                throw new IllegalArgumentException("File size exceeds the supported range");
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.TRUNCATE_FILE)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(entityId.bytes()))
                    .setSize(size)
                    .build());
        }
    }

    public void rename(String path, String newName) {
        synchronized (node) {
            FullGuid entityId = data.fileSystem.resolve(path);
            FullGuid parentId = data.fileSystem.resolveParent(path);
            if (isRootDirectory(entityId))
                throw new IllegalArgumentException("Cannot rename the user root directory");
            data.fileSystem.requireAvailableName(parentId, newName, entityId);
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.RENAME)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(entityId.bytes()))
                    .setNewName(newName)
                    .build());
        }
    }

    public void move(String path, String newPath) {
        synchronized (node) {
            FullGuid entityId = data.fileSystem.resolve(path);
            FullGuid newParentId = data.fileSystem.resolveParent(newPath);
            String newName = data.fileSystem.leafName(newPath);
            data.fileSystem.validateMove(entityId, newParentId, newName);
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.MOVE)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(entityId.bytes()))
                    .setNewParentId(com.google.protobuf.ByteString.copyFrom(newParentId.bytes()))
                    .setNewName(newName)
                    .build());
        }
    }

    public void delete(String path) {
        synchronized (node) {
            FullGuid entityId = data.fileSystem.resolve(path);
            if (isRootDirectory(entityId))
                throw new IllegalArgumentException("Cannot delete the user root directory");
            recordOperation(Sync.UserOperation.newBuilder()
                    .setKind(Sync.UserOperation.Kind.DELETE)
                    .setEntityId(com.google.protobuf.ByteString.copyFrom(entityId.bytes()))
                    .build());
        }
    }

    private boolean isRootDirectory(FullGuid id) {
        return id.equals(FullGuid.fromBytes(data.rootDirectoryGuid));
    }

    private void recordOperation(Sync.UserOperation operation) {
        synchronized (node) {
            data.fileSystem.apply(operation);
            data.contacts.apply(operation);
            node.recordLocalOperation(data, operation.toByteArray());
        }
    }

    public void startLeaving(PeerId leavingPeerId) {
        synchronized (node) {
            var leavingNode = node.network.nodes.get(leavingPeerId);
            if (leavingNode == null)
                throw new IllegalArgumentException("Unknown peer " + leavingPeerId);
            if (leavingNode == node)
                throw new IllegalArgumentException("A node cannot initiate its own leave process");
            if (!data.activeNodeSet.nodes.contains(leavingNode.nodeKey()))
                throw new IllegalArgumentException("Peer is not active for this user");
            String processId = UUID.randomUUID().toString();
            if (data.activeNodeSet.startLeaving(leavingNode.nodeKey(), processId,
                    data.maxOperationClock, node.nodeKey(), node::nextClock))
                node.mirrorStartLeaving(data, leavingNode.nodeKey(), processId);
            node.persistState();
        }
    }

    private void mergeStaging() {
        if (data.stagingTree.isEmpty() && data.localOperationTree.isEmpty())
            return;
        var merged = data.allOperations();
        var state = UserState.replayFromState(node.dataStorage, data.guidMapper, data.snapshotState, data.userId(), merged.values());
        data.fileSystem = state.fileSystem;
        data.contacts = state.contacts;
        data.operationTree.putAll(data.localOperationTree);
        data.operationTree.putAll(data.stagingTree);
        data.localOperationTree.clear();
        data.stagingTree.clear();
        node.applyParticipationOperations(data);
        node.rebuildInboxMessages(data);
    }

    void rebuildCurrentState() {
        synchronized (node) {
            var state = UserState.replayFromState(node.dataStorage, data.guidMapper, data.snapshotState,
                    data.userId(), data.allOperations().values());
            data.fileSystem = state.fileSystem;
            data.contacts = state.contacts;
        }
    }

    public boolean truncateStableLog() {
        synchronized (node) {
            return truncateStableLogLocked();
        }
    }

    private boolean truncateStableLogLocked() {
        Node.OperationId stable = null;
        for (String source : data.activeNodeSet.nodes) {
            byte[] sourceBytes;
            try {
                sourceBytes = Node.parseNodeKey(source);
            } catch (IllegalArgumentException e) {
                continue;
            }
            var candidate = new Node.OperationId(data.maxOperationClock.getOrDefault(source, 0L), sourceBytes);
            if (stable == null || candidate.compareTo(stable) < 0)
                stable = candidate;
        }
        if (stable == null)
            return false;
        var removable = new java.util.ArrayList<Node.Operation>();
        for (var operation : data.operationTree.headMap(stable, false).values()) {
            try {
                var userOperation = Sync.UserOperation.parseFrom(operation.payload);
                if (userOperation.getKind() != Sync.UserOperation.Kind.DIRECT_MESSAGE
                        && userOperation.getKind() != Sync.UserOperation.Kind.RECEIVE_MESSAGE
                        && userOperation.getKind() != Sync.UserOperation.Kind.SET_SHARED_FOLDER_LABEL
                        && userOperation.getKind() != Sync.UserOperation.Kind.PARTICIPATE_SYNC_UNIT
                        && userOperation.getKind() != Sync.UserOperation.Kind.LEAVE_SYNC_UNIT)
                    removable.add(operation);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        if (removable.isEmpty())
            return false;
        var snapshot = UserState.replayFromState(node.dataStorage, data.guidMapper, data.snapshotState, data.userId(), removable);
        data.snapshotState = snapshot.serializeState();
        removable.forEach(operation -> data.operationTree.remove(operation.id));
        rebuildCurrentState();
        node.persistState();
        return true;
    }
}