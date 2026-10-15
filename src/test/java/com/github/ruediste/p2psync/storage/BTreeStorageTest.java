package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class BTreeStorageTest {
    private static class MemoryStorage implements BTree.Storage {
        final Map<Long, byte[]> blocks = new HashMap<>();
        final Map<Long, Integer> references = new HashMap<>();
        final Set<Long> modified = new HashSet<>();
        long nextOffset = 1;

        @Override
        public int blockSize() {
            return 64;
        }

        @Override
        public byte[] readBlock(long blockOffset) {
            return java.util.Objects.requireNonNull(blocks.get(blockOffset)).clone();
        }

        @Override
        public boolean isBlockModified(long blockOffset) {
            return modified.contains(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, byte[] data) {
            assertTrue(modified.contains(blockOffset));
            blocks.put(blockOffset, data.clone());
        }

        @Override
        public long writeNewBlock(byte[] data) {
            assertEquals(blockSize(), data.length);
            long offset = nextOffset++;
            blocks.put(offset, data.clone());
            references.put(offset, 1);
            modified.add(offset);
            return offset;
        }

        @Override
        public void incrementRefCount(long blockOffset) {
            references.put(blockOffset, references.get(blockOffset) + 1);
        }

        @Override
        public void decrementRefCount(long blockOffset, Consumer<Long> onDelete) {
            int remaining = references.get(blockOffset) - 1;
            assertTrue(remaining >= 0);
            references.put(blockOffset, remaining);
            if (remaining == 0) {
                onDelete.accept(blockOffset);
                blocks.remove(blockOffset);
                references.remove(blockOffset);
                modified.remove(blockOffset);
            }
        }
    }

    private static byte[] key(int value) {
        return new byte[] { (byte) (value >>> 8), (byte) value };
    }

    /**
     * Replaces and removes an entry while its leaf is still transaction-modified.
     * The leaf should be reused, absent-key removal should do nothing, and entry
     * deletion callbacks should report the replaced and finally removed values.
     */
    @Test
    void modifiedLeafIsReusedAndEntryDeletionIsReported() {
        MemoryStorage storage = new MemoryStorage();
        List<Integer> deleted = new ArrayList<>();
        BTree tree = new BTree(storage, 2, (entryKey, value) -> deleted.add((int) value[0]));
        tree.put(key(1), new byte[] { 10 });
        long root = tree.rootOffset;
        tree.put(key(1), new byte[] { 20 });
        assertEquals(root, tree.rootOffset);
        assertFalse(tree.remove(key(2)));
        assertTrue(tree.remove(key(1)));
        assertEquals(List.of(10, 20), deleted);
        assertTrue(storage.blocks.isEmpty());
    }

    /**
     * Keeps a snapshot root while mutating and deleting the live tree.
     * Copy-on-write
     * must preserve the snapshot, and recursively releasing its final reference
     * must reclaim every remaining node and leaf block.
     */
    @Test
    void copiedPathsPreserveSnapshotsAndRecursivelyReleaseAllBlocks() {
        MemoryStorage storage = new MemoryStorage();
        BTree tree = new BTree(storage, 2, null);
        for (int index = 0; index < 100; index++)
            tree.put(key(index), key(index));
        long oldRoot = tree.rootOffset;
        storage.incrementRefCount(oldRoot);
        storage.modified.clear();
        BTree snapshot = new BTree(storage, 2, null, oldRoot);

        tree.put(key(50), new byte[] { 42 });
        assertNotEquals(oldRoot, tree.rootOffset);
        assertArrayEquals(key(50), snapshot.get(key(50)));
        assertArrayEquals(new byte[] { 42 }, tree.get(key(50)));
        for (int index = 0; index < 100; index++)
            assertTrue(tree.remove(key(index)));
        assertEquals(0, tree.rootOffset);
        for (int index = 0; index < 100; index++)
            assertArrayEquals(key(index), snapshot.get(key(index)));

        storage.decrementRefCount(oldRoot, BTree.decrementRefCount(storage, 2));
        assertTrue(storage.blocks.isEmpty());
        assertTrue(storage.references.isEmpty());
    }

    /**
     * Applies deterministic random inserts and removals across simulated
     * transaction
     * boundaries. After every operation, iteration and values must match a
     * sorted-map
     * model; releasing the final root must leave no allocated blocks behind.
     */
    @Test
    void mixedMutationsAcrossTransactionsMatchSortedMapWithoutLeaks() {
        MemoryStorage storage = new MemoryStorage();
        BTree tree = new BTree(storage, 2, null);
        Map<Integer, byte[]> expected = new TreeMap<>();
        Random random = new Random(42);
        for (int iteration = 0; iteration < 1000; iteration++) {
            if (iteration % 17 == 0)
                storage.modified.clear();
            int entryKey = random.nextInt(100);
            if (random.nextBoolean()) {
                byte[] value = key(iteration);
                tree.put(key(entryKey), value);
                expected.put(entryKey, value);
            } else {
                assertEquals(expected.remove(entryKey) != null, tree.remove(key(entryKey)));
            }
            List<Integer> actualKeys = new ArrayList<>();
            tree.forEach((entry, value) -> {
                int actualKey = (entry[0] & 255) * 256 + (entry[1] & 255);
                actualKeys.add(actualKey);
                assertArrayEquals(expected.get(actualKey), value);
            });
            assertEquals(new ArrayList<>(expected.keySet()), actualKeys);
        }
        if (tree.rootOffset != 0)
            storage.decrementRefCount(tree.rootOffset, BTree.decrementRefCount(storage, 2));
        assertTrue(storage.blocks.isEmpty());
    }
}