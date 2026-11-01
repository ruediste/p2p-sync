package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.github.ruediste.p2psync.proto.Sync;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.FileTree;
import com.github.ruediste.p2psync.util.ShortGuid;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * User-visible file tree backed directly by {@link FileTree}. Full entity ids
 * are translated to short GUIDs through the owning SyncUnit's {@link GuidMapper}.
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
        this.storage = storage;
        this.mapper = mapper;
        this.rootId = rootId.clone();
        this.tree = new FileTree(storage, rootOffset);
        this.root = mapper.toShort(rootId);
        if (rootOffset == 0)
            tree.createDirectory(root, root, "root", new FileTree.FileMeta());
    }

    String rootIdHex() {
        return key(rootId);
    }

    long rootOffset() {
        return tree.getRootOffset();
    }

    static UserFileTree empty(DataStorage storage, GuidMapper mapper, byte[] rootId) {
        return new UserFileTree(storage, mapper, rootId, 0);
    }

    static UserFileTree fromState(DataStorage storage, GuidMapper mapper, byte[] state) {
        try {
            var stored = Sync.UserTreeState.parseFrom(state);
            var result = new UserFileTree(storage, mapper, stored.getRootId().toByteArray(), 0);
            result.statusMessage = stored.getStatusMessage();
            var pending = new ArrayList<>(stored.getEntriesList());
            while (!pending.isEmpty()) {
                int previousSize = pending.size();
                pending.removeIf(entry -> {
                    String id = key(entry.getEntityId().toByteArray());
                    String parentId = key(entry.getParentId().toByteArray());
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

    static UserFileTree replay(DataStorage storage, GuidMapper mapper, byte[] rootId,
            Iterable<Node.Operation> operations) {
        var result = new UserFileTree(storage, mapper, rootId, 0);
        for (var operation : operations) {
            try {
                result.apply(Sync.UserOperation.parseFrom(operation.payload));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        return result;
    }

    static UserFileTree replayFromState(DataStorage storage, GuidMapper mapper, byte[] baseState,
            Iterable<Node.Operation> operations) {
        var result = fromState(storage, mapper, baseState);
        for (var operation : operations) {
            try {
                result.apply(Sync.UserOperation.parseFrom(operation.payload));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        return result;
    }

    void apply(Sync.UserOperation operation) {
        String entityId = key(operation.getEntityId().toByteArray());
        switch (operation.getKind()) {
            case SET_STATUS -> statusMessage = operation.getStatusMessage();
            case CREATE_DIRECTORY -> createDirectory(entityId, key(operation.getParentId().toByteArray()),
                    operation.getName());
            case CREATE_FILE -> {
                if (createFile(entityId, key(operation.getParentId().toByteArray()), operation.getName())
                        && operation.getData().size() > 0)
                    writeFile(entityId, 0, operation.getData().toByteArray());
            }
            case WRITE_FILE -> writeFile(entityId, operation.getOffset(), operation.getData().toByteArray());
            case TRUNCATE_FILE -> truncateFile(entityId, operation.getSize());
            case MOVE -> move(entityId, key(operation.getNewParentId().toByteArray()), operation.getNewName());
            case RENAME -> rename(entityId, operation.getNewName());
            case DELETE -> delete(entityId);
            case DIRECT_MESSAGE, PARTICIPATE_SYNC_UNIT, LEAVE_SYNC_UNIT, RECEIVE_MESSAGE,
                    CONTACT_CREATE, CONTACT_UPDATE, CONTACT_DELETE -> {
            }
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown user operation kind");
        }
    }

    boolean createDirectory(String id, String parentId, String name) {
        ShortGuid parent = guid(parentId);
        ShortGuid entity = guid(id);
        if (!validName(name) || !isDirectoryGuid(parent) || tree.getDirectoryMeta(entity) != null
                || tree.getFileMeta(entity) != null || tree.lookup(parent, name) != null)
            return false;
        tree.createDirectory(parent, entity, name, new FileTree.FileMeta());
        return true;
    }

    boolean createFile(String id, String parentId, String name) {
        ShortGuid parent = guid(parentId);
        ShortGuid entity = guid(id);
        if (!validName(name) || !isDirectoryGuid(parent) || tree.getDirectoryMeta(entity) != null
                || tree.getFileMeta(entity) != null || tree.lookup(parent, name) != null)
            return false;
        tree.createFile(parent, entity, name, new FileTree.FileMeta());
        return true;
    }

    void writeFile(String id, long offset, byte[] bytes) {
        tree.write(guid(id), offset, bytes);
    }

    void truncateFile(String id, long size) {
        tree.truncate(guid(id), size);
    }

    boolean move(String id, String newParentId, String newName) {
        ShortGuid entity = guid(id);
        ShortGuid parent = guid(newParentId);
        if (id.equals(key(rootId)) || !isDirectoryGuid(parent))
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

    boolean rename(String id, String newName) {
        ShortGuid entity = guid(id);
        if (id.equals(key(rootId)) || !validName(newName))
            return false;
        if (tree.getDirectoryMeta(entity) == null && tree.getFileMeta(entity) == null)
            return false;
        tree.rename(entity, newName);
        return true;
    }

    boolean delete(String id) {
        if (id.equals(key(rootId)))
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
        try {
            resolve(path);
            return true;
        } catch (IllegalArgumentException missing) {
            return false;
        }
    }

    boolean isDirectory(String path) {
        return isDirectoryGuid(guid(resolve(path)));
    }

    long fileSize(String path) {
        var meta = tree.getFileMeta(guid(resolveFile(path)));
        return meta == null ? 0 : meta.size;
    }

    String resolve(String path) {
        ShortGuid current = root;
        for (String name : components(path)) {
            var entry = tree.lookup(current, name);
            if (entry == null)
                throw new IllegalArgumentException("Path does not exist: " + path);
            current = entry.guid();
        }
        return key(mapper.toFull(current));
    }

    String resolveDirectory(String path) {
        String id = resolve(path);
        if (!isDirectoryGuid(guid(id)))
            throw new IllegalArgumentException("Not a directory: " + path);
        return id;
    }

    String resolveFile(String path) {
        String id = resolve(path);
        if (tree.getFileMeta(guid(id)) == null)
            throw new IllegalArgumentException("Not a file: " + path);
        return id;
    }

    String resolveParent(String path) {
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

    void requireAvailableName(String parentId, String name, String exceptId) {
        if (!validName(name))
            throw new IllegalArgumentException("Invalid file name");
        ShortGuid parent = guid(parentId);
        if (!isDirectoryGuid(parent))
            throw new IllegalArgumentException("Parent is not a directory");
        var entry = tree.lookup(parent, name);
        if (entry != null && (exceptId == null || !sameGuid(entry.guid(), guid(exceptId))))
            throw new IllegalArgumentException("Directory entry already exists: " + name);
    }

    void validateMove(String entityId, String newParentId, String newName) {
        String rootEntity = key(rootId);
        if (entityId.equals(rootEntity))
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
        var state = Sync.UserTreeState.newBuilder()
                .setRootId(ByteString.copyFrom(rootId))
                .setStatusMessage(statusMessage);
        addEntries(state, root);
        return state.build().toByteArray();
    }

    private void addEntries(Sync.UserTreeState.Builder state, ShortGuid directory) {
        for (var entry : tree.listDirectory(directory)) {
            byte[] entityId = mapper.toFull(entry.guid());
            byte[] parentId = mapper.toFull(directory);
            if (entry.directory()) {
                state.addEntries(Sync.UserTreeEntry.newBuilder()
                        .setEntityId(ByteString.copyFrom(entityId))
                        .setParentId(ByteString.copyFrom(parentId))
                        .setName(entry.name())
                        .setDirectory(true));
                addEntries(state, entry.guid());
            } else {
                state.addEntries(Sync.UserTreeEntry.newBuilder()
                        .setEntityId(ByteString.copyFrom(entityId))
                        .setParentId(ByteString.copyFrom(parentId))
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

    private ShortGuid guid(String id) {
        return mapper.toShort(HexFormat.of().parseHex(id));
    }

    private static boolean sameGuid(ShortGuid left, ShortGuid right) {
        return left.nodeId == right.nodeId && left.counter == right.counter;
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

    private static String key(byte[] id) {
        return HexFormat.of().formatHex(id);
    }
}
