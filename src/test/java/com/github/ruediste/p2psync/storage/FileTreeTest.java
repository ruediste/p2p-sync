package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.ruediste.p2psync.util.ShortGuid;

class FileTreeTest {
    @TempDir
    Path tempDir;

    private DataStorage storage;
    private FileTree tree;
    private ShortGuid root;
    private ShortGuid folder;
    private ShortGuid file;

    @BeforeEach
    void setUp() {
        storage = new DataStorage();
        storage.open(tempDir.resolve("files.db").toString());
        tree = new FileTree(storage);
        root = guid(1);
        folder = guid(2);
        file = guid(3);
        tree.createDirectory(root, root, "root", new FileTree.FileMeta());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    /**
     * Verifies directory creation, index ordering, rename and move updates, and
     * recursive deletion by observing the public directory and metadata APIs.
     */
    @Test
    void directoryOperationsUpdateIndexesAndHeaders() {
        tree.createDirectory(root, folder, "folder", new FileTree.FileMeta());
        tree.createFile(folder, file, "note.txt", new FileTree.FileMeta());
        assertEquals(List.of("folder"), tree.listDirectory(root).stream().map(FileTree.DirectoryEntry::name).toList());
        assertEquals("note.txt", tree.listDirectory(folder).get(0).name());
        assertEquals(file.counter, tree.lookup(folder, "note.txt").guid().counter);

        ShortGuid otherFolder = guid(4);
        tree.createDirectory(root, otherFolder, "other", new FileTree.FileMeta());
        tree.rename(file, "renamed.txt");
        tree.move(file, otherFolder);
        assertEquals(List.of(), tree.listDirectory(folder));
        assertEquals("renamed.txt", tree.listDirectory(otherFolder).get(0).name());
        assertEquals(file.counter, tree.lookup(otherFolder, "renamed.txt").guid().counter);

        tree.delete(otherFolder);
        assertEquals(List.of("folder"), tree.listDirectory(root).stream().map(FileTree.DirectoryEntry::name).toList());
        assertNull(tree.getFileMeta(file));
    }

    /**
     * Verifies sparse writes preserve existing bytes, zero-fill gaps, persist
     * through the tree root offset, and store/read data beyond the inline
     * threshold.
     */
    @Test
    void readsAndWritesInlineAndExtentData() {
        tree.createFile(root, file, "data.bin", new FileTree.FileMeta());
        tree.write(file, 2, new byte[] { 7, 8 });
        assertArrayEquals(new byte[] { 0, 0, 7, 8 }, tree.read(file));
        assertArrayEquals(new byte[] { 7, 8 }, tree.read(file, 2, 8));
        assertArrayEquals(new byte[] { 0, 7 }, tree.read(file, 1, 2));

        byte[] large = new byte[40_000];
        for (int index = 0; index < large.length; index++)
            large[index] = (byte) (index * 31);
        tree.write(file, 0, large);
        assertArrayEquals(large, tree.read(file));

        byte[] patch = new byte[] { 11, 12, 13 };
        tree.write(file, 8190, patch);
        System.arraycopy(patch, 0, large, 8190, patch.length);
        assertArrayEquals(large, tree.read(file));
        assertArrayEquals(Arrays.copyOfRange(large, 8188, 8198), tree.read(file, 8188, 10));

        tree.truncate(file, 25_000);
        large = Arrays.copyOf(large, 25_000);
        assertArrayEquals(large, tree.read(file));
        tree.truncate(file, 42_000);
        large = Arrays.copyOf(large, 42_000);
        assertArrayEquals(large, tree.read(file));

        tree.truncate(file, 9000);
        large = Arrays.copyOf(large, 9000);
        assertArrayEquals(large, tree.read(file));
        tree.truncate(file, 9005);
        large = Arrays.copyOf(large, 9005);
        assertArrayEquals(large, tree.read(file));

        FileTree reopened = new FileTree(storage, tree.getRootOffset());
        assertArrayEquals(large, reopened.read(file));
    }

    /**
     * Rejects invalid namespace changes and prevents moving a directory below
     * itself, which would create a parent cycle.
     */
    @Test
    void rejectsDuplicateNamesAndDirectoryCycles() {
        tree.createDirectory(root, folder, "folder", new FileTree.FileMeta());
        tree.createFile(root, file, "file", new FileTree.FileMeta());
        assertThrows(IllegalArgumentException.class,
                () -> tree.createFile(root, guid(5), "folder", new FileTree.FileMeta()));
        assertThrows(IllegalArgumentException.class, () -> tree.move(folder, folder));
        assertThrows(IllegalArgumentException.class, () -> tree.rename(file, "bad/name"));
    }

    /**
     * Persists two roots and reopens them to verify extent references preserve both
     * file versions.
     */
    @Test
    void extentSnapshotsSurviveCommitAndReopen() {
        tree.createFile(root, file, "snapshot.bin", new FileTree.FileMeta());
        byte[] original = new byte[40_000];
        Arrays.fill(original, (byte) 17);
        tree.write(file, 0, original);
        long snapshotRoot = tree.getRootOffset();
        storage.incrementRefCount(snapshotRoot, DataStorage.Area.META);
        storage.commit();

        byte[] changed = original.clone();
        changed[8190] = 42;
        tree.write(file, 8190, new byte[] { 42 });
        long liveRoot = tree.getRootOffset();
        storage.incrementRefCount(liveRoot, DataStorage.Area.META);
        storage.commit();
        storage.close();
        storage.open(tempDir.resolve("files.db").toString());

        assertArrayEquals(original, new FileTree(storage, snapshotRoot).read(file));
        assertArrayEquals(changed, new FileTree(storage, liveRoot).read(file));
    }

    /**
     * Adds enough names to exceed the inline directory-item limit, then verifies
     * spilled names remain searchable and are removed with their directory entry.
     */
    @Test
    void directoryNamesSpillAndRemainSearchable() {
        tree.createDirectory(root, folder, "folder", new FileTree.FileMeta());
        for (int index = 0; index < 240; index++) {
            String name = "entry-" + index;
            tree.createFile(folder, guid(1000 + index), name, new FileTree.FileMeta());
        }

        assertEquals(240, tree.listDirectory(folder).size());
        assertEquals("entry-239", tree.lookup(folder, "entry-239").name());
        tree.delete(guid(1239));
        assertNull(tree.lookup(folder, "entry-239"));
        assertEquals(239, tree.listDirectory(folder).size());
    }

    private static ShortGuid guid(long counter) {
        ShortGuid result = new ShortGuid();
        result.nodeId = 7;
        result.counter = counter;
        return result;
    }
}
