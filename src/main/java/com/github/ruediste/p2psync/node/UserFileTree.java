package com.github.ruediste.p2psync.node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

final class UserFileTree {
    record Entry(String name, boolean directory) {
    }

    private static final class Directory {
        final String id;
        String name;
        String parentId;
        final TreeMap<String, String> children = new TreeMap<>();

        Directory(String id, String parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }
    }

    private static final class File {
        final String id;
        String name;
        String parentId;
        byte[] content = new byte[0];

        File(String id, String parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }
    }

    final byte[] rootId;
    final Map<String, Directory> directories = new HashMap<>();
    final Map<String, File> files = new HashMap<>();
    String statusMessage = "initial";

    private UserFileTree(byte[] rootId) {
        this.rootId = rootId.clone();
        String id = key(rootId);
        directories.put(id, new Directory(id, id, ""));
    }

    static UserFileTree empty(byte[] rootId) {
        return new UserFileTree(rootId);
    }

    static UserFileTree fromState(byte[] state) {
        try {
            var stored = Sync.UserTreeState.parseFrom(state);
            var result = new UserFileTree(stored.getRootId().toByteArray());
            result.statusMessage = stored.getStatusMessage();
            var pending = new ArrayList<>(stored.getEntriesList());
            while (!pending.isEmpty()) {
                int previousSize = pending.size();
                pending.removeIf(entry -> {
                    String id = key(entry.getEntityId().toByteArray());
                    String parentId = key(entry.getParentId().toByteArray());
                    if (!result.directories.containsKey(parentId))
                        return false;
                    boolean created = entry.getDirectory()
                            ? result.createDirectory(id, parentId, entry.getName())
                            : result.createFile(id, parentId, entry.getName());
                    if (!created)
                        throw new IllegalArgumentException("Invalid persisted user tree entry");
                    if (!entry.getDirectory())
                        result.files.get(id).content = entry.getContent().toByteArray();
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

    static UserFileTree replay(byte[] rootId, Iterable<Node.Operation> operations) {
        var result = new UserFileTree(rootId);
        for (var operation : operations) {
            try {
                result.apply(Sync.UserOperation.parseFrom(operation.payload));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        return result;
    }

    static UserFileTree replayFromState(byte[] baseState, Iterable<Node.Operation> operations) {
        var result = fromState(baseState);
        for (var operation : operations) {
            try {
                result.apply(Sync.UserOperation.parseFrom(operation.payload));
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException("Invalid user operation payload", e);
            }
        }
        return result;
    }

    private void apply(Sync.UserOperation operation) {
        String entityId = key(operation.getEntityId().toByteArray());
        switch (operation.getKind()) {
            case SET_STATUS -> statusMessage = operation.getStatusMessage();
            case CREATE_DIRECTORY -> createDirectory(entityId, key(operation.getParentId().toByteArray()),
                    operation.getName());
            case CREATE_FILE -> {
                if (createFile(entityId, key(operation.getParentId().toByteArray()), operation.getName()))
                    files.get(entityId).content = operation.getData().toByteArray();
            }
            case WRITE_FILE -> writeFile(entityId, operation.getOffset(), operation.getData().toByteArray());
            case TRUNCATE_FILE -> truncateFile(entityId, operation.getSize());
            case MOVE -> move(entityId, key(operation.getNewParentId().toByteArray()), operation.getNewName());
            case RENAME -> rename(entityId, operation.getNewName());
            case DELETE -> delete(entityId);
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown user operation kind");
        }
    }

    boolean createDirectory(String id, String parentId, String name) {
        Directory parent = directories.get(parentId);
        if (parent == null || !validName(name) || parent.children.containsKey(name)
                || directories.containsKey(id) || files.containsKey(id))
            return false;
        parent.children.put(name, id);
        directories.put(id, new Directory(id, parentId, name));
        return true;
    }

    boolean createFile(String id, String parentId, String name) {
        Directory parent = directories.get(parentId);
        if (parent == null || !validName(name) || parent.children.containsKey(name)
                || directories.containsKey(id) || files.containsKey(id))
            return false;
        parent.children.put(name, id);
        files.put(id, new File(id, parentId, name));
        return true;
    }

    void writeFile(String id, long offset, byte[] bytes) {
        File file = files.get(id);
        if (file == null || offset < 0 || offset > Integer.MAX_VALUE
                || bytes.length > Integer.MAX_VALUE - offset)
            return;
        int end = (int) offset + bytes.length;
        if (end > file.content.length)
            file.content = Arrays.copyOf(file.content, end);
        System.arraycopy(bytes, 0, file.content, (int) offset, bytes.length);
    }

    void truncateFile(String id, long size) {
        File file = files.get(id);
        if (file != null && size >= 0 && size <= Integer.MAX_VALUE)
            file.content = Arrays.copyOf(file.content, (int) size);
    }

    boolean move(String id, String newParentId, String newName) {
        Directory target = directories.get(newParentId);
        Directory entityDirectory = directories.get(id);
        File entityFile = files.get(id);
        if (target == null || (!validName(newName)) || target.children.containsKey(newName)
                || (entityDirectory == null && entityFile == null) || id.equals(key(rootId)))
            return false;
        if (entityDirectory != null && (id.equals(newParentId) || isDescendant(newParentId, id)))
            return false;
        String oldParentId = entityDirectory != null ? entityDirectory.parentId : entityFile.parentId;
        String oldName = entityDirectory != null ? entityDirectory.name : entityFile.name;
        Directory oldParent = directories.get(oldParentId);
        if (oldParent == null)
            return false;
        oldParent.children.remove(oldName);
        target.children.put(newName, id);
        if (entityDirectory != null) {
            entityDirectory.parentId = newParentId;
            entityDirectory.name = newName;
        } else {
            entityFile.parentId = newParentId;
            entityFile.name = newName;
        }
        return true;
    }

    boolean rename(String id, String newName) {
        Directory directory = directories.get(id);
        File file = files.get(id);
        if (directory == null && file == null)
            return false;
        String parentId = directory != null ? directory.parentId : file.parentId;
        return move(id, parentId, newName);
    }

    boolean delete(String id) {
        if (id.equals(key(rootId)))
            return false;
        Directory directory = directories.get(id);
        if (directory != null) {
            for (String childId : new ArrayList<>(directory.children.values()))
                delete(childId);
            directories.remove(id);
            Directory parent = directories.get(directory.parentId);
            if (parent != null)
                parent.children.remove(directory.name);
            return true;
        }
        File file = files.remove(id);
        if (file == null)
            return false;
        Directory parent = directories.get(file.parentId);
        if (parent != null)
            parent.children.remove(file.name);
        return true;
    }

    List<Entry> listDirectory(String path) {
        Directory directory = directories.get(resolve(path));
        return directory.children.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), directories.containsKey(entry.getValue())))
                .toList();
    }

    byte[] readFile(String path) {
        File file = files.get(resolve(path));
        if (file == null)
            throw new IllegalArgumentException("Not a file: " + path);
        return file.content.clone();
    }

    boolean exists(String path) {
        try {
            resolve(path);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    boolean isDirectory(String path) {
        return directories.containsKey(resolve(path));
    }

    long fileSize(String path) {
        File file = files.get(resolveFile(path));
        return file.content.length;
    }

    String resolve(String path) {
        String current = key(rootId);
        for (String name : components(path)) {
            Directory directory = directories.get(current);
            if (directory == null)
                throw new IllegalArgumentException("Not a directory: " + path);
            current = directory.children.get(name);
            if (current == null)
                throw new IllegalArgumentException("No such user path: " + path);
        }
        return current;
    }

    String resolveDirectory(String path) {
        String id = resolve(path);
        if (!directories.containsKey(id))
            throw new IllegalArgumentException("Not a directory: " + path);
        return id;
    }

    String resolveFile(String path) {
        String id = resolve(path);
        if (!files.containsKey(id))
            throw new IllegalArgumentException("Not a file: " + path);
        return id;
    }

    String resolveParent(String path) {
        List<String> components = components(path);
        if (components.isEmpty())
            throw new IllegalArgumentException("Cannot create or move the user root");
        String parent = key(rootId);
        for (int index = 0; index < components.size() - 1; index++) {
            Directory directory = directories.get(parent);
            if (directory == null || !directory.children.containsKey(components.get(index)))
                throw new IllegalArgumentException("No such parent directory: " + path);
            parent = directory.children.get(components.get(index));
        }
        if (!directories.containsKey(parent))
            throw new IllegalArgumentException("Parent is not a directory: " + path);
        return parent;
    }

    String leafName(String path) {
        List<String> components = components(path);
        if (components.isEmpty())
            throw new IllegalArgumentException("Cannot name the user root");
        return components.get(components.size() - 1);
    }

    void requireAvailableName(String parentId, String name, String exceptId) {
        Directory parent = directories.get(parentId);
        if (parent == null)
            throw new IllegalArgumentException("Parent is not a directory");
        if (!validName(name))
            throw new IllegalArgumentException("Invalid filesystem name: " + name);
        String existing = parent.children.get(name);
        if (existing != null && !existing.equals(exceptId))
            throw new IllegalArgumentException("Filesystem name is already in use: " + name);
    }

    void validateMove(String entityId, String newParentId, String newName) {
        Directory target = directories.get(newParentId);
        Directory directory = directories.get(entityId);
        File file = files.get(entityId);
        if (target == null || (directory == null && file == null) || entityId.equals(key(rootId)))
            throw new IllegalArgumentException("Invalid move source or destination");
        requireAvailableName(newParentId, newName, entityId);
        if (directory != null && isDescendant(newParentId, entityId))
            throw new IllegalArgumentException("A directory cannot be moved into itself or a descendant");
    }

    private boolean isDescendant(String candidateId, String ancestorId) {
        String current = candidateId;
        while (true) {
            if (current.equals(ancestorId))
                return true;
            Directory directory = directories.get(current);
            if (directory == null || directory.parentId.equals(current))
                return false;
            current = directory.parentId;
        }
    }

    private BlockId storeDirectory(String id, BlockStorage storage) {
        Directory directory = directories.get(id);
        var builder = Sync.Directory.newBuilder();
        for (var child : directory.children.entrySet()) {
            String childId = child.getValue();
            byte[] entityId = HexFormat.of().parseHex(childId);
            Directory childDirectory = directories.get(childId);
            if (childDirectory != null) {
                builder.addDirectories(Sync.DirectoryEntry.newBuilder()
                        .setName(child.getKey())
                        .setEntityId(ByteString.copyFrom(entityId))
                        .setDirectoryId(storeDirectory(childId, storage).toProto()));
            } else {
                File file = files.get(childId);
                BlockId dataBlock = storage.store(Sync.StorageBlock.newBuilder()
                        .addDataBytes(ByteString.copyFrom(file.content)).build());
                builder.addFiles(Sync.FileEntry.newBuilder()
                        .setName(child.getKey())
                        .setEntityId(ByteString.copyFrom(entityId))
                        .setDataBlockId(dataBlock.toProto()));
            }
        }
        return storage.store(builder.build());
    }

    DataUserRoot toDataUserRoot(BlockStorage storage) {
        var result = new DataUserRoot();
        result.userStatusMessage = statusMessage;
        result.rootDirectoryGuid = rootId.clone();
        result.rootDirectoryId = storeDirectory(key(rootId), storage);
        return result;
    }

    byte[] serializeState() {
        var state = Sync.UserTreeState.newBuilder()
                .setRootId(ByteString.copyFrom(rootId))
                .setStatusMessage(statusMessage);
        directories.values().stream().filter(directory -> !directory.id.equals(key(rootId)))
                .sorted(java.util.Comparator.comparing(directory -> directory.id))
                .forEach(directory -> state.addEntries(Sync.UserTreeEntry.newBuilder()
                        .setEntityId(ByteString.copyFrom(HexFormat.of().parseHex(directory.id)))
                        .setParentId(ByteString.copyFrom(HexFormat.of().parseHex(directory.parentId)))
                        .setName(directory.name)
                        .setDirectory(true)));
        files.values().stream().sorted(java.util.Comparator.comparing(file -> file.id))
                .forEach(file -> state.addEntries(Sync.UserTreeEntry.newBuilder()
                        .setEntityId(ByteString.copyFrom(HexFormat.of().parseHex(file.id)))
                        .setParentId(ByteString.copyFrom(HexFormat.of().parseHex(file.parentId)))
                        .setName(file.name)
                        .setContent(ByteString.copyFrom(file.content))));
        return state.build().toByteArray();
    }

    static UserFileTree fromDataRoot(DataUserRoot dataRoot, Node.Network network, BlockStorage storage) {
        var result = new UserFileTree(dataRoot.rootDirectoryGuid);
        result.statusMessage = dataRoot.userStatusMessage;
        copyDirectory(result, key(dataRoot.rootDirectoryGuid), dataRoot.rootDirectoryId, network, storage);
        return result;
    }

    private static void copyDirectory(UserFileTree target, String parentId, BlockId directoryBlock,
            Node.Network network, BlockStorage storage) {
        byte[] directoryBytes = requireBlock(network, directoryBlock);
        storage.put(directoryBlock, directoryBytes);
        Sync.Directory directory;
        try {
            directory = Sync.Directory.parseFrom(directoryBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Invalid directory block", e);
        }
        for (var child : directory.getDirectoriesList()) {
            String childId = key(child.getEntityId().toByteArray());
            BlockId childBlock = BlockId.fromProto(child.getDirectoryId());
            if (!target.createDirectory(childId, parentId, child.getName()))
                throw new IllegalArgumentException("Invalid directory entry in snapshot");
            copyDirectory(target, childId, childBlock, network, storage);
        }
        for (var child : directory.getFilesList()) {
            String childId = key(child.getEntityId().toByteArray());
            if (!target.createFile(childId, parentId, child.getName()))
                throw new IllegalArgumentException("Invalid file entry in snapshot");
            BlockId blockId = BlockId.fromProto(child.getDataBlockId());
            byte[] dataBytes = requireBlock(network, blockId);
            storage.put(blockId, dataBytes);
            try {
                var stored = Sync.StorageBlock.parseFrom(dataBytes);
                var content = new java.io.ByteArrayOutputStream();
                stored.getDataBytesList().forEach(part -> {
                    try {
                        part.writeTo(content);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                target.files.get(childId).content = content.toByteArray();
            } catch (InvalidProtocolBufferException | java.io.UncheckedIOException e) {
                throw new IllegalArgumentException("Invalid file data block", e);
            }
        }
    }

    private static byte[] requireBlock(Node.Network network, BlockId id) {
        byte[] result = network.getBlock(id);
        if (result == null)
            throw new IllegalArgumentException("Required snapshot block is unavailable");
        return result;
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
        return name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && name.indexOf('/') < 0 && name.indexOf('\0') < 0;
    }

    private static String key(byte[] id) {
        return HexFormat.of().formatHex(id);
    }
}