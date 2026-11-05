package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.FileTree;
import com.github.ruediste.p2psync.storage.FileTree.FileMeta;
import com.github.ruediste.p2psync.util.ShortGuid;

/**
 * Persists each shared folder as its own {@link FileTree}. The tree root and
 * GUID mapping live in the unit's entries in {@link UnitRootStore}. Directories
 * are real tree entries, so empty directories survive persistence and
 * synchronization.
 */
final class SharedFolderPersistence {
    /** A file or directory below the shared-folder root. */
    record Entry(String path, boolean directory, long size) {
    }

    /** The result of stat-ing a shared-folder path. */
    record Stat(boolean directory, long size) {
    }

    private SharedFolderPersistence() {
    }

    static void putFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path, byte[] content) {
        var tree = open(storage, roots, mapper, unitId);
        ShortGuid parent = mapper.toShort(rootId(unitId));
        var components = components(path);
        if (components.isEmpty())
            throw new IllegalArgumentException("Shared-folder path must not be empty");
        var prefix = new StringBuilder();
        for (int index = 0; index < components.size(); index++) {
            String component = components.get(index);
            if (prefix.length() > 0)
                prefix.append('/');
            prefix.append(component);
            var existing = tree.lookup(parent, component);
            if (index == components.size() - 1) {
                if (existing != null && existing.directory())
                    throw new IllegalArgumentException("Cannot replace a shared-folder directory with a file");
                ShortGuid file = existing == null ? mapper.toShort(entityId(unitId, "f:" + prefix)) : existing.guid();
                if (existing == null)
                    tree.createFile(parent, file, component, new FileMeta());
                tree.write(file, 0, content);
                tree.truncate(file, content.length);
            } else {
                if (existing != null && !existing.directory())
                    throw new IllegalArgumentException("Shared-folder path crosses a file");
                ShortGuid directory = existing == null
                        ? mapper.toShort(entityId(unitId, "d:" + prefix))
                        : existing.guid();
                if (existing == null)
                    tree.createDirectory(parent, directory, component, new FileMeta());
                parent = directory;
            }
        }
        roots.put(unitId, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void createDirectory(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path) {
        var tree = open(storage, roots, mapper, unitId);
        ShortGuid parent = mapper.toShort(rootId(unitId));
        var components = components(path);
        var prefix = new StringBuilder();
        for (String component : components) {
            if (prefix.length() > 0)
                prefix.append('/');
            prefix.append(component);
            var existing = tree.lookup(parent, component);
            if (existing != null) {
                if (!existing.directory())
                    throw new IllegalArgumentException("Shared-folder path crosses a file");
                parent = existing.guid();
            } else {
                ShortGuid directory = mapper.toShort(entityId(unitId, "d:" + prefix));
                tree.createDirectory(parent, directory, component, new FileMeta());
                parent = directory;
            }
        }
        roots.put(unitId, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void deleteFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return;
        var components = components(path);
        if (components.isEmpty())
            return;
        ShortGuid parent = resolveDirectory(tree, mapper, unitId, components.subList(0, components.size() - 1));
        if (parent == null)
            return;
        var file = tree.lookup(parent, components.get(components.size() - 1));
        if (file == null || file.directory())
            return;
        tree.delete(file.guid());
        roots.put(unitId, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void deleteDirectory(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return;
        var components = components(path);
        if (components.isEmpty())
            return;
        ShortGuid parent = resolveDirectory(tree, mapper, unitId, components.subList(0, components.size() - 1));
        if (parent == null)
            return;
        var directory = tree.lookup(parent, components.get(components.size() - 1));
        if (directory == null || !directory.directory())
            return;
        tree.delete(directory.guid());
        roots.put(unitId, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static void deleteUnit(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return;
        tree.delete(mapper.toShort(rootId(unitId)));
        roots.put(unitId, UnitRootStore.FILE_TREE, tree.getRootOffset());
    }

    static Stat stat(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId, String path) {
        var components = components(path);
        if (components.isEmpty())
            return new Stat(true, 0);
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return null;
        ShortGuid current = mapper.toShort(rootId(unitId));
        for (int index = 0; index < components.size(); index++) {
            var entry = tree.lookup(current, components.get(index));
            if (entry == null)
                return null;
            if (index == components.size() - 1) {
                if (entry.directory())
                    return new Stat(true, 0);
                var meta = tree.getFileMeta(entry.guid());
                return new Stat(false, meta == null ? 0 : meta.size);
            }
            if (!entry.directory())
                return null;
            current = entry.guid();
        }
        return null;
    }

    static List<Entry> listDirectory(DataStorage storage, UnitRootStore roots, GuidMapper mapper,
            SyncUnitId unitId, String path) {
        var components = components(path);
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return components.isEmpty() ? List.of() : null;
        ShortGuid directory = resolveDirectory(tree, mapper, unitId, components);
        if (directory == null)
            return null;
        var result = new ArrayList<Entry>();
        String prefix = normalized(path);
        tree.forEachDirectory(directory, entry -> {
            String childPath = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            long size = entry.directory() ? 0 : fileSize(tree, entry.guid());
            result.add(new Entry(childPath, entry.directory(), size));
        });
        return result;
    }

    static byte[] readFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return null;
        var components = components(path);
        if (components.isEmpty())
            return null;
        ShortGuid parent = resolveDirectory(tree, mapper, unitId, components.subList(0, components.size() - 1));
        if (parent == null)
            return null;
        var file = tree.lookup(parent, components.get(components.size() - 1));
        if (file == null || file.directory())
            return null;
        return tree.read(file.guid());
    }

    static long fileSize(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            String path) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return -1;
        var components = components(path);
        if (components.isEmpty())
            return -1;
        ShortGuid parent = resolveDirectory(tree, mapper, unitId, components.subList(0, components.size() - 1));
        if (parent == null)
            return -1;
        var file = tree.lookup(parent, components.get(components.size() - 1));
        if (file == null || file.directory())
            return -1;
        return fileSize(tree, file.guid());
    }

    static void forEachFile(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            BiConsumer<String, Long> consumer) {
        forEachEntry(storage, roots, mapper, unitId,
                entry -> {
                    if (!entry.directory())
                        consumer.accept(entry.path(), entry.size());
                });
    }

    static void forEachEntry(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId,
            Consumer<Entry> consumer) {
        var tree = existingTree(storage, roots, mapper, unitId);
        if (tree == null)
            return;
        forEachEntry(tree, mapper.toShort(rootId(unitId)), "", consumer);
    }

    private static void forEachEntry(FileTree tree, ShortGuid directory, String prefix, Consumer<Entry> consumer) {
        tree.forEachDirectory(directory, entry -> {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.directory()) {
                consumer.accept(new Entry(path, true, 0));
                forEachEntry(tree, entry.guid(), path, consumer);
            } else {
                consumer.accept(new Entry(path, false, fileSize(tree, entry.guid())));
            }
        });
    }

    private static FileTree open(DataStorage storage, UnitRootStore roots, GuidMapper mapper, SyncUnitId unitId) {
        long rootOffset = roots.get(unitId, UnitRootStore.FILE_TREE);
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId(unitId));
        if (rootOffset == 0 || tree.getDirectoryMeta(root) == null)
            tree.createDirectory(root, root, "root", new FileMeta());
        return tree;
    }

    private static FileTree existingTree(DataStorage storage, UnitRootStore roots, GuidMapper mapper,
            SyncUnitId unitId) {
        long rootOffset = roots.get(unitId, UnitRootStore.FILE_TREE);
        if (rootOffset == 0)
            return null;
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId(unitId));
        return tree.getDirectoryMeta(root) == null ? null : tree;
    }

    private static ShortGuid resolveDirectory(FileTree tree, GuidMapper mapper, SyncUnitId unitId,
            List<String> components) {
        ShortGuid current = mapper.toShort(rootId(unitId));
        for (String component : components) {
            var entry = tree.lookup(current, component);
            if (entry == null || !entry.directory())
                return null;
            current = entry.guid();
        }
        return current;
    }

    private static long fileSize(FileTree tree, ShortGuid guid) {
        var meta = tree.getFileMeta(guid);
        return meta == null ? 0 : meta.size;
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
        return result;
    }

    private static String normalized(String path) {
        var components = components(path);
        return String.join("/", components);
    }

    private static byte[] rootId(SyncUnitId unitId) {
        return entityId(unitId, "root");
    }

    private static byte[] entityId(SyncUnitId unitId, String name) {
        byte[] unit = unitId.bytes();
        byte[] suffix = name.getBytes(StandardCharsets.UTF_8);
        byte[] input = ByteBuffer.allocate(unit.length + suffix.length).put(unit).put(suffix).array();
        byte[] counterInput = ByteBuffer.allocate(input.length + Long.BYTES).put(input).putLong(1).array();
        long counter = ByteBuffer.wrap(CryptoUtils.sha256(counterInput)).getLong();
        return ByteBuffer.allocate(40).put(CryptoUtils.sha256(input)).putLong(counter).array();
    }
}
