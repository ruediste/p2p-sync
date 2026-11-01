package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.FileTree;
import com.github.ruediste.p2psync.storage.FileTree.FileMeta;
import com.github.ruediste.p2psync.util.ShortGuid;

/**
 * Persists all shared-folder file maps in one FileTree. Keys are
 * {@code unitIdHex/path}; a reserved unit id roots the tree and its GUID map.
 */
final class SharedFolderPersistence {
    static final SyncUnitId SHARED_FOLDER_UNIT_ID = SyncUnitId.fromBytes(filled((byte) 0x5a));

    private SharedFolderPersistence() {
    }

    static long write(DataStorage storage, UnitRootStore roots, GuidMapper mapper, Map<String, byte[]> files) {
        var tree = new FileTree(storage, 0);
        ShortGuid root = mapper.toShort(rootId());
        tree.createDirectory(root, root, "root", new FileMeta());
        var paths = new ArrayList<>(files.keySet());
        paths.sort(Comparator.naturalOrder());
        for (String path : paths) {
            String[] components = path.split("/");
            ShortGuid parent = root;
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < components.length; i++) {
                String component = components[i];
                if (component.isEmpty() || component.equals(".") || component.equals(".."))
                    throw new IllegalArgumentException("Invalid shared-folder path: " + path);
                if (prefix.length() > 0)
                    prefix.append('/');
                prefix.append(component);
                var existing = tree.lookup(parent, component);
                if (i == components.length - 1) {
                    ShortGuid file = existing == null ? mapper.toShort(entityId("f:" + prefix)) : existing.guid();
                    if (existing == null)
                        tree.createFile(parent, file, component, new FileMeta());
                    byte[] content = files.get(path);
                    tree.write(file, 0, content);
                    tree.truncate(file, content.length);
                } else {
                    ShortGuid directory = existing == null
                            ? mapper.toShort(entityId("d:" + prefix))
                            : existing.guid();
                    if (existing == null)
                        tree.createDirectory(parent, directory, component, new FileMeta());
                    parent = directory;
                }
            }
        }
        roots.put(SHARED_FOLDER_UNIT_ID, UnitRootStore.FILE_TREE, tree.getRootOffset());
        return tree.getRootOffset();
    }

    static byte[] readFile(DataStorage storage, GuidMapper mapper, long rootOffset, String unitIdHex, String path) {
        var tree = new FileTree(storage, rootOffset);
        ShortGuid current = mapper.toShort(rootId());
        for (String component : path.split("/")) {
            if (component.isEmpty())
                continue;
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

    static void forEachFile(DataStorage storage, GuidMapper mapper, long rootOffset, String unitIdHex,
            java.util.function.BiConsumer<String, Long> consumer) {
        var tree = new FileTree(storage, rootOffset);
        ShortGuid root = mapper.toShort(rootId());
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

    static Map<String, byte[]> read(DataStorage storage, GuidMapper mapper, long rootOffset) {
        var result = new TreeMap<String, byte[]>();
        var tree = new FileTree(storage, rootOffset);
        read(tree, mapper.toShort(rootId()), "", result);
        return result;
    }

    private static void read(FileTree tree, ShortGuid directory, String prefix, Map<String, byte[]> result) {
        for (var entry : tree.listDirectory(directory)) {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.directory())
                read(tree, entry.guid(), path, result);
            else
                result.put(path, tree.read(entry.guid()));
        }
    }

    private static byte[] rootId() {
        return entityId("root");
    }

    private static byte[] entityId(String name) {
        byte[] unit = SHARED_FOLDER_UNIT_ID.bytes();
        byte[] suffix = name.getBytes(StandardCharsets.UTF_8);
        byte[] input = ByteBuffer.allocate(unit.length + suffix.length).put(unit).put(suffix).array();
        return ByteBuffer.allocate(40).put(CryptoUtils.sha256(input)).putLong(0).array();
    }

    private static byte[] filled(byte value) {
        byte[] result = new byte[32];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
