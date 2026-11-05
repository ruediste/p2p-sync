package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.github.ruediste.p2psync.proto.Sync;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.FileTree;
import com.github.ruediste.p2psync.util.FullGuid;
import com.github.ruediste.p2psync.util.ShortGuid;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * User-visible file tree backed directly by {@link FileTree}. Full entity ids
 * are translated to short GUIDs through the owning SyncUnit's
 * {@link GuidMapper}.
 */
final class UserFileTree {
    record Entry(String name, boolean directory) {
    }

    final byte[] rootId;
    final DataStorage storage;
    final GuidMapper mapper;
    private final FileTree tree;
    private final ShortGuid root;
    String statusMessage = "initial";

    UserFileTree(DataStorage storage, GuidMapper mapper, byte[] rootId, long rootOffset) {
        this(storage, mapper, rootId, rootOffset, com.github.ruediste.p2psync.storage.RefMode.TRANSFER);
    }

    private UserFileTree(DataStorage storage, GuidMapper mapper, byte[] rootId, long rootOffset,
            com.github.ruediste.p2psync.storage.RefMode refMode) {
        this.storage = storage;
        this.mapper = mapper;
        this.rootId = rootId.clone();
        this.tree = new FileTree(storage, rootOffset, refMode);
        this.root = mapper.toShort(rootId);
        if (rootOffset == 0)
            tree.createDirectory(root, root, "root", new FileTree.FileMeta());
    }

    String rootIdHex() {
        return FullGuid.fromBytes(rootId).hex();
    }

    long rootOffset() {
        return tree.getRootOffset();
    }

    static UserFileTree empty(DataStorage storage, GuidMapper mapper, byte[] rootId) {
        return new UserFileTree(storage, mapper, rootId, 0);
    }

    static UserFileTree fromState(DataStorage storage, GuidMapper mapper, byte[] state) {
        try {
            var stored = Sync.UserTreeStateProto.parseFrom(state);
            var result = new UserFileTree(storage, mapper, stored.getRootId().toByteArray(), 0);
            result.statusMessage = stored.getStatusMessage();
            var pending = new ArrayList<>(stored.getEntriesList());
            while (!pending.isEmpty()) {
                int previousSize = pending.size();
                pending.removeIf(entry -> {
                    FullGuid id = FullGuid.fromBytes(entry.getEntityId().toByteArray());
                    FullGuid parentId = FullGuid.fromBytes(entry.getParentId().toByteArray());
                    if (result.tree.getDirectoryMeta(result.guid(parentId)) == null)
                        return false;
                    boolean created = entry.getDirectory()
                            ? result.createDirectory(id, parentId, entry.getName())
                            : result.createFile(id, parentId, entry.getName());
                    if (!created)
                        throw new IllegalArgumentException("Invalid persisted user tree entry");
                    if (!entry.getDirectory())
                        result.writeFile(id, 0, entry.getContent().toByteArray());
                    return true;
                });
                if (pending.size() == previousSize)
                    throw new IllegalArgumentException("Persisted user tree contains an invalid parent cycle");
            }
            return result;
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid persisted user tree", e);
        }
    }

    static UserFileTree fromRoot(DataStorage storage, GuidMapper mapper, byte[] state, long rootOffset) {
        try {
            var stored = Sync.UserTreeStateProto.parseFrom(state);
            var result = new UserFileTree(storage, mapper, stored.getRootId().toByteArray(), rootOffset,
                    com.github.ruediste.p2psync.storage.RefMode.SHARE);
            result.statusMessage = stored.getStatusMessage();
            if (result.tree.getDirectoryMeta(result.root) == null)
                throw new IllegalArgumentException("Persisted user tree root is missing");
            return result;
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid persisted user tree", e);
        }
    }

    static UserFileTree replay(DataStorage storage, GuidMapper mapper, byte[] rootId,
            Iterable<Node.Operation> operations) {
        var result = new UserFileTree(storage, mapper, rootId, 0);
        for (var operation : operations) {
            try {
                result.apply(Sync.UserOperationProto.parseFrom(operation.payload));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        return result;
    }

    void apply(Sync.UserOperationProto operation) {
        switch (operation.getKind()) {
            case SET_STATUS -> statusMessage = operation.getStatusMessage();
            case CREATE_DIRECTORY -> createDirectory(full(operation.getEntityId()),
                    full(operation.getParentId()), operation.getName());
            case CREATE_FILE -> {
                if (createFile(full(operation.getEntityId()), full(operation.getParentId()),
                        operation.getName())
                        && operation.getData().size() > 0)
                    writeFile(full(operation.getEntityId()), 0, operation.getData().toByteArray());
            }
            case WRITE_FILE -> writeFile(full(operation.getEntityId()), operation.getOffset(),
                    operation.getData().toByteArray());
            case TRUNCATE_FILE -> truncateFile(full(operation.getEntityId()), operation.getSize());
            case MOVE -> move(full(operation.getEntityId()), full(operation.getNewParentId()),
                    operation.getNewName());
            case RENAME -> rename(full(operation.getEntityId()), operation.getNewName());
            case DELETE -> delete(full(operation.getEntityId()));
            case DIRECT_MESSAGE, PARTICIPATE_SYNC_UNIT, LEAVE_SYNC_UNIT, RECEIVE_MESSAGE,
                    SET_SHARED_FOLDER_LABEL,
                    CONTACT_CREATE, CONTACT_UPDATE, CONTACT_DELETE ->
                {
                }
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown user operation kind");
        }
    }

    boolean createDirectory(FullGuid id, FullGuid parentId, String name) {
        ShortGuid parent = guid(parentId);
        ShortGuid entity = guid(id);
        if (!validName(name) || !isDirectoryGuid(parent) || tree.getDirectoryMeta(entity) != null
                || tree.getFileMeta(entity) != null || tree.lookup(parent, name) != null)
            return false;
        tree.createDirectory(parent, entity, name, new FileTree.FileMeta());
        return true;
    }

    boolean createFile(FullGuid id, FullGuid parentId, String name) {
        ShortGuid parent = guid(parentId);
        ShortGuid entity = guid(id);
        if (!validName(name) || !isDirectoryGuid(parent) || tree.getDirectoryMeta(entity) != null
                || tree.getFileMeta(entity) != null || tree.lookup(parent, name) != null)
            return false;
        tree.createFile(parent, entity, name, new FileTree.FileMeta());
        return true;
    }

    void writeFile(FullGuid id, long offset, byte[] bytes) {
        tree.write(guid(id), offset, bytes);
    }

    void truncateFile(FullGuid id, long size) {
        tree.truncate(guid(id), size);
    }

    boolean move(FullGuid id, FullGuid newParentId, String newName) {
        ShortGuid entity = guid(id);
        ShortGuid parent = guid(newParentId);
        if (isRoot(id) || !isDirectoryGuid(parent))
            return false;
        if (tree.getDirectoryMeta(entity) == null && tree.getFileMeta(entity) == null)
            return false;
        if (tree.getDirectoryMeta(entity) != null
                && (sameGuid(entity, parent) || isDescendantGuid(parent, entity)))
            return false;
        tree.move(entity, parent);
        tree.rename(entity, newName);
        return true;
    }

    boolean rename(FullGuid id, String newName) {
        ShortGuid entity = guid(id);
        if (isRoot(id) || !validName(newName))
            return false;
        if (tree.getDirectoryMeta(entity) == null && tree.getFileMeta(entity) == null)
            return false;
        tree.rename(entity, newName);
        return true;
    }

    boolean delete(FullGuid id) {
        if (isRoot(id))
            return false;
        ShortGuid entity = guid(id);
        if (tree.getDirectoryMeta(entity) == null && tree.getFileMeta(entity) == null)
            return false;
        return tree.delete(entity);
    }

    List<Entry> listDirectory(String path) {
        var result = new ArrayList<Entry>();
        forEachDirectory(path, result::add);
        result.sort(Comparator.comparing(Entry::name));
        return result;
    }

    /** Streams directory entries without building an intermediate list. */
    void forEachDirectory(String path, java.util.function.Consumer<Entry> consumer) {
        ShortGuid directory = guid(resolve(path));
        tree.forEachDirectory(directory,
                entry -> consumer.accept(new Entry(entry.name(), entry.directory())));
    }

    byte[] readFile(String path) {
        return tree.read(guid(resolveFile(path)));
    }

    boolean exists(String path) {
        return tryResolve(path) != null;
    }

    boolean isDirectory(String path) {
        return isDirectoryGuid(guid(resolve(path)));
    }

    long fileSize(String path) {
        var meta = tree.getFileMeta(guid(resolveFile(path)));
        return meta == null ? 0 : meta.size;
    }

    FullGuid resolve(String path) {
        var result = tryResolve(path);
        if (result == null)
            throw new IllegalArgumentException("Path does not exist: " + path);
        return result;
    }

    private FullGuid tryResolve(String path) {
        ShortGuid current = root;
        for (String name : components(path)) {
            var entry = tree.lookup(current, name);
            if (entry == null)
                return null;
            current = entry.guid();
        }
        return mapper.toFull(current);
    }

    FullGuid resolveDirectory(String path) {
        FullGuid id = resolve(path);
        if (!isDirectoryGuid(guid(id)))
            throw new IllegalArgumentException("Not a directory: " + path);
        return id;
    }

    FullGuid resolveFile(String path) {
        FullGuid id = resolve(path);
        if (tree.getFileMeta(guid(id)) == null)
            throw new IllegalArgumentException("Not a file: " + path);
        return id;
    }

    FullGuid resolveParent(String path) {
        List<String> components = components(path);
        if (components.isEmpty())
            throw new IllegalArgumentException("The root has no parent");
        String parentPath = "/" + String.join("/", components.subList(0, components.size() - 1));
        return resolveDirectory(parentPath);
    }

    String leafName(String path) {
        List<String> components = components(path);
        if (components.isEmpty())
            throw new IllegalArgumentException("The root has no name");
        return components.get(components.size() - 1);
    }

    void requireAvailableName(FullGuid parentId, String name, FullGuid exceptId) {
        if (!validName(name))
            throw new IllegalArgumentException("Invalid file name");
        ShortGuid parent = guid(parentId);
        if (!isDirectoryGuid(parent))
            throw new IllegalArgumentException("Parent is not a directory");
        var entry = tree.lookup(parent, name);
        if (entry != null && (exceptId == null || !sameGuid(entry.guid(), guid(exceptId))))
            throw new IllegalArgumentException("Directory entry already exists: " + name);
    }

    void validateMove(FullGuid entityId, FullGuid newParentId, String newName) {
        if (isRoot(entityId))
            throw new IllegalArgumentException("Cannot move the user root directory");
        if (!validName(newName))
            throw new IllegalArgumentException("Invalid file name");
        ShortGuid parent = guid(newParentId);
        if (!isDirectoryGuid(parent))
            throw new IllegalArgumentException("Target parent is not a directory");
        ShortGuid entity = guid(entityId);
        if (tree.getDirectoryMeta(entity) == null && tree.getFileMeta(entity) == null)
            throw new IllegalArgumentException("Entity does not exist");
        if (tree.getDirectoryMeta(entity) != null
                && (sameGuid(entity, parent) || isDescendantGuid(parent, entity)))
            throw new IllegalArgumentException("Cannot move a directory into itself");
        requireAvailableName(newParentId, newName, entityId);
    }

    byte[] serializeState() {
        var state = Sync.UserTreeStateProto.newBuilder()
                .setRootId(ByteString.copyFrom(rootId))
                .setStatusMessage(statusMessage);
        addEntries(state, root);
        return state.build().toByteArray();
    }

    byte[] serializeShallowState() {
        return Sync.UserTreeStateProto.newBuilder()
                .setRootId(ByteString.copyFrom(rootId))
                .setStatusMessage(statusMessage)
                .build().toByteArray();
    }

    private void addEntries(Sync.UserTreeStateProto.Builder state, ShortGuid directory) {
        for (var entry : tree.listDirectory(directory)) {
            FullGuid entityId = mapper.toFull(entry.guid());
            FullGuid parentId = mapper.toFull(directory);
            if (entry.directory()) {
                state.addEntries(Sync.UserTreeEntryProto.newBuilder()
                        .setEntityId(ByteString.copyFrom(entityId.bytes()))
                        .setParentId(ByteString.copyFrom(parentId.bytes()))
                        .setName(entry.name())
                        .setDirectory(true));
                addEntries(state, entry.guid());
            } else {
                state.addEntries(Sync.UserTreeEntryProto.newBuilder()
                        .setEntityId(ByteString.copyFrom(entityId.bytes()))
                        .setParentId(ByteString.copyFrom(parentId.bytes()))
                        .setName(entry.name())
                        .setContent(ByteString.copyFrom(tree.read(entry.guid()))));
            }
        }
    }

    private boolean isDescendantGuid(ShortGuid candidate, ShortGuid ancestor) {
        for (var entry : tree.listDirectory(ancestor)) {
            if (!entry.directory())
                continue;
            if (sameGuid(entry.guid(), candidate) || isDescendantGuid(candidate, entry.guid()))
                return true;
        }
        return false;
    }

    private boolean isDirectoryGuid(ShortGuid guid) {
        return tree.getDirectoryMeta(guid) != null;
    }

    private ShortGuid guid(FullGuid id) {
        return mapper.toShort(id);
    }

    private static FullGuid full(ByteString bytes) {
        return FullGuid.fromBytes(bytes.toByteArray());
    }

    private boolean isRoot(FullGuid id) {
        return id.equals(FullGuid.fromBytes(rootId));
    }

    private static boolean sameGuid(ShortGuid left, ShortGuid right) {
        return left.shortNodeId == right.shortNodeId && left.counter == right.counter;
    }

    private static List<String> components(String path) {
        if (path == null)
            throw new IllegalArgumentException("Path must not be null");
        var result = new ArrayList<String>();
        for (String component : path.split("/")) {
            if (component.isEmpty())
                continue;
            if (!validName(component))
                throw new IllegalArgumentException("Invalid user path: " + path);
            result.add(component);
        }
        return result;
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !name.equals(".") && !name.equals("..")
                && name.indexOf('/') < 0 && name.indexOf('\0') < 0;
    }
}
