package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.FileTree;
import com.github.ruediste.p2psync.storage.FileTree.FileMeta;
import com.github.ruediste.p2psync.util.ShortGuid;

/**
 * Persists all shared-folder content in one FileTree. Keys are
 * {@code unitIdHex/path}; a reserved unit id roots the tree and its GUID map.
 *
 * <p>
 * The tree is updated in place for every accepted content operation. There is
 * no in-memory path/content map.
 * </p>
 */
final class SharedFolderPersistence {
    static final SyncUnitId SHARED_FOLDER_UNIT_ID = SyncUnitId.fromBytes(filled((byte) 0x5a));

    private SharedFolderPersistence() {
    }

    static void putFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, String unitIdHex,
            String path, byte[] content) {
        var tree = open(storage, roots, mapper);
        ShortGuid root = mapper.toShort(rootId());
        ShortGuid parent = ensureDirectory(tree, mapper, root, unitIdHex);
        var components = components(path);
        var prefix = new StringBuilder(unitIdHex);
        for (int index = 0; index < components.size(); index++) {
            String component = components.get(index);
            prefix.append('/').append(component);
            var existing = tree.lookup(parent, component);
            if (index == components.size() - 1) {
                if (existing != null && existing.directory())
                    throw new IllegalArgumentException("Cannot replace a shared-folder directory with a file");
                ShortGuid file = existing == null ? mapper.toShort(entityId("f:" + prefix)) : existing.guid();
                if (existing == null)
                    tree.createFile(parent, file, component, new FileMeta());
                tree.write(file, 0, content);
                tree.truncate(file, content.length);
            } else {
                if (existing != null && !existing.directory())
                    throw new IllegalArgumentException("Shared-folder path crosses a file");
                ShortGuid directory = existing == null
                        ? mapper.toShort(entityId("d:" + prefix))
                        : existing.guid();
                if (existing == null)
                    tree.createDirectory(parent, directory, component, new FileMeta());
                parent = directory;
            }
        }
        roots.put(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void deleteFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, String unitIdHex,
            String path) {
        long rootOffset = roots.get(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        if (rootOffset == 0)
            return;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId());
        if (tree.getDirectoryMeta(root) == null)
            return;
        var unit = tree.lookup(root, unitIdHex);
        if (unit == null || !unit.directory())
            return;
        var components = components(path);
        var directories = new ArrayList<ShortGuid>();
        ShortGuid parent = unit.guid();
        directories.add(parent);
        for (int index = 0; index < components.size() - 1; index++) {
            var entry = tree.lookup(parent, components.get(index));
            if (entry == null || !entry.directory())
                return;
            parent = entry.guid();
            directories.add(parent);
        }
        var file = tree.lookup(parent, components.get(components.size() - 1));
        if (file == null || file.directory())
            return;
        tree.delete(file.guid());
        for (int index = directories.size() - 1; index >= 1; index--) {
            ShortGuid directory = directories.get(index);
            if (!tree.listDirectory(directory).isEmpty())
                break;
            tree.delete(directory);
        }
        roots.put(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void deleteUnit(DataStorage storage, UnitRootStore roots, GuidMapper mapper, String unitIdHex) {
        long rootOffset = roots.get(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        if (rootOffset == 0)
            return;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId());
        if (tree.getDirectoryMeta(root) == null)
            return;
        var unit = tree.lookup(root, unitIdHex);
        if (unit == null || !unit.directory())
            return;
        tree.delete(unit.guid());
        roots.put(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static byte[] readFile(DataStorage storage, GuidMapper mapper, long rootOffset, String unitIdHex, String path) {
        if (rootOffset == 0)
            return null;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid current = mapper.toShort(rootId());
        if (tree.getDirectoryMeta(current) == null)
            return null;
        var unit = tree.lookup(current, unitIdHex);
        if (unit == null || !unit.directory())
            return null;
        current = unit.guid();
        for (String component : components(path)) {
            var entry = tree.lookup(current, component);
            if (entry == null)
                return null;
            current = entry.guid();
            if (entry.directory())
                continue;
            return tree.read(current);
        }
        return null;
    }

    static long fileSize(DataStorage storage, GuidMapper mapper, long rootOffset, String unitIdHex, String path) {
        if (rootOffset == 0)
            return -1;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid current = mapper.toShort(rootId());
        if (tree.getDirectoryMeta(current) == null)
            return -1;
        var unit = tree.lookup(current, unitIdHex);
        if (unit == null || !unit.directory())
            return -1;
        current = unit.guid();
        for (String component : components(path)) {
            var entry = tree.lookup(current, component);
            if (entry == null)
                return -1;
            current = entry.guid();
            if (entry.directory())
                continue;
            var meta = tree.getFileMeta(current);
            return meta == null ? 0 : meta.size;
        }
        return -1;
    }

    static void forEachFile(DataStorage storage, GuidMapper mapper, long rootOffset, String unitIdHex,
            java.util.function.BiConsumer<String, Long> consumer) {
        if (rootOffset == 0)
            return;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId());
        if (tree.getDirectoryMeta(root) == null)
            return;
        var unit = tree.lookup(root, unitIdHex);
        if (unit == null || !unit.directory())
            return;
        forEachFile(tree, unit.guid(), "", consumer);
    }

    private static void forEachFile(FileTree tree, ShortGuid directory, String prefix,
            java.util.function.BiConsumer<String, Long> consumer) {
        tree.forEachDirectory(directory, entry -> {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.directory())
                forEachFile(tree, entry.guid(), path, consumer);
            else {
                var meta = tree.getFileMeta(entry.guid());
                consumer.accept(path, meta == null ? 0 : meta.size);
            }
        });
    }

    private static FileTree open(DataStorage storage, UnitRootStore roots, GuidMapper mapper) {
        long rootOffset = roots.get(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE);
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId());
        if (rootOffset == 0 || tree.getDirectoryMeta(root) == null)
            tree.createDirectory(root, root, "root", new FileMeta());
        return tree;
    }

    private static ShortGuid ensureDirectory(FileTree tree, GuidMapper mapper, ShortGuid parent, String name) {
        var existing = tree.lookup(parent, name);
        if (existing != null) {
            if (!existing.directory())
                throw new IllegalArgumentException("Shared-folder path crosses a file");
            return existing.guid();
        }
        ShortGuid directory = mapper.toShort(entityId("d:" + name));
        tree.createDirectory(parent, directory, name, new FileMeta());
        return directory;
    }

    private static ArrayList<String> components(String path) {
        var result = new ArrayList<String>();
        for (String component : path.split("/")) {
            if (component.isEmpty())
                continue;
            if (component.equals(".") || component.equals(".."))
                throw new IllegalArgumentException("Invalid shared-folder path: " + path);
            result.add(component);
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("Shared-folder path must not be empty");
        return result;
    }

    private static byte[] rootId() {
        return entityId("root");
    }

    private static byte[] entityId(String name) {
        byte[] unit = SHARED_FOLDER_UNIT_ID.bytes();
        byte[] suffix = name.getBytes(StandardCharsets.UTF_8);
        byte[] input = ByteBuffer.allocate(unit.length + suffix.length).put(unit).put(suffix).array();
        byte[] counterInput = ByteBuffer.allocate(input.length + Long.BYTES).put(input).putLong(1).array();
        long counter = ByteBuffer.wrap(CryptoUtils.sha256(counterInput)).getLong();
        return ByteBuffer.allocate(40).put(CryptoUtils.sha256(input)).putLong(counter).array();
    }

    private static byte[] filled(byte value) {
        byte[] result = new byte[32];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
