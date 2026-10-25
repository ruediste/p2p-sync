package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.storage.DataStorage.Area;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;

class StorageCheckTest {
    @TempDir
    Path tempDir;

    private static DataStorage fuseStorage() {
        var storage = new DataStorage();
        storage.getRootRefHandle(SimpleFuseFS.TREE_ROOT_HANDLE_ID, Area.META, FileTree.BLOCK_TYPE);
        return storage;
    }

    private static void write(SimpleFuseFS fs, String path, byte[] data) {
        Pointer buffer = Memory.allocateDirect(Runtime.getSystemRuntime(), data.length);
        buffer.put(0, data, 0, data.length);
        assertEquals(0, fs.create(path, 0644, null));
        assertEquals(data.length, fs.write(path, buffer, data.length, 0, null));
    }

    /**
     * Verifies a freshly formatted storage passes the check and every block is
     * accounted for in the statistics.
     */
    @Test
    void freshStoragePasses() {
        Path file = tempDir.resolve("fresh.db");
        new DataStorage().format(file.toString());

        var report = StorageCheck.check(new DataStorage(), file.toString());

        assertTrue(report.isOk(), () -> String.join("\n", report.errors));
        assertTrue(report.stats.get(Area.SYSTEM).allocatedBlocks() > 0);
        assertTrue(report.stats.get(Area.DATA).freeBlocks > 0);
    }

    /**
     * Verifies a SimpleFuseFS file with inline, extent-backed and deleted files,
     * written across two sessions (exercising reopen), passes the check, that
     * the referenced data blocks show up as allocated, and that the main B-trees
     * appear in the tree statistics.
     */
    @Test
    void fuseStoragePasses() {
        Path file = tempDir.resolve("fs.db");
        try (var fs = new SimpleFuseFS(file)) {
            write(fs, "/small", "hello".getBytes(StandardCharsets.UTF_8));
            write(fs, "/large", new byte[100_000]);
            write(fs, "/deleted", new byte[50_000]);
            assertEquals(0, fs.unlink("/deleted"));
        }
        try (var fs = new SimpleFuseFS(file)) {
            write(fs, "/second", new byte[70_000]);
            assertEquals(0, fs.unlink("/large"));
        }

        var report = StorageCheck.check(fuseStorage(), file.toString());

        assertTrue(report.isOk(), () -> String.join("\n", report.errors));
        assertTrue(report.stats.get(Area.DATA).allocatedBlocks() > 0);
        for (String tree : List.of("root handle 1", "ref count tree", "free space tree DATA")) {
            var stats = report.treeStats.get(tree);
            assertTrue(stats != null && stats.trees == 1 && stats.entries > 0, tree);
        }
    }

    /**
     * Verifies a stored reference count which is higher than the number of
     * references is reported: a block referenced once from a root handle gets an
     * additional increment.
     */
    @Test
    void detectsRefCountMismatch() {
        Path file = tempDir.resolve("bad.db");
        var storage = new DataStorage();
        var handle = storage.getRootRefHandle(1, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        storage.format(file.toString());
        handle.write(new DataStorage.ByteArrayBlock(new byte[storage.blockSize(Area.DATA)]));
        storage.incrementRefCount(new BlockRef(handle.blockOffset, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        storage.close();

        var checkStorage = new DataStorage();
        checkStorage.getRootRefHandle(1, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        var report = StorageCheck.check(checkStorage, file.toString());

        assertFalse(report.isOk());
        assertTrue(report.errors.stream().anyMatch(e -> e.startsWith("Ref count mismatch for DATA block "
                + handle.blockOffset)), () -> String.join("\n", report.errors));
    }

    /**
     * Verifies deferred META lists spanning several blocks are persisted
     * completely. Tiny META blocks plus many root handles make the commit leave
     * more pending META free-tree removals and ref-count adjustments than fit
     * into one list block; any list block lost from the chain shows up as
     * unaccounted space, also after reopening and committing again.
     */
    @Test
    void multiBlockMetaListsArePersisted() {
        Path file = tempDir.resolve("lists.db");
        int handleCount = 150;
        var storage = new DataStorage();
        List<DataStorage.RootRefHandle<DataStorage.ByteArrayBlock>> handles = new ArrayList<>();
        for (int id = 1; id <= handleCount; id++)
            handles.add(storage.getRootRefHandle(id, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.format(file.toString(), 64 * 1024, 256 * 1024, 1024 * 1024, 4096, 512, 4096);
        for (var handle : handles)
            handle.write(new DataStorage.ByteArrayBlock(new byte[storage.blockSize(Area.DATA)]));
        storage.commit();
        assertCheckPasses(file, handleCount);

        storage.open(file.toString());
        for (var handle : handles.subList(0, handleCount / 2))
            handle.write(new DataStorage.ByteArrayBlock(new byte[storage.blockSize(Area.DATA)]));
        storage.commit();
        storage.close();
        assertCheckPasses(file, handleCount);
    }

    private static void assertCheckPasses(Path file, int handleCount) {
        var checkStorage = new DataStorage();
        for (int id = 1; id <= handleCount; id++)
            checkStorage.getRootRefHandle(id, Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        var report = StorageCheck.check(checkStorage, file.toString());
        assertTrue(report.isOk(), () -> String.join("\n", report.errors));
    }
}
