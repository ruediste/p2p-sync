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

import org.junit.jupiter.api.Test;

class BTreeStorageTest {
    private static class MemoryStorage implements BTree.Storage {
        final Map<Long, byte[]> blocks = new HashMap<>();
        final Map<Long, Integer> references = new HashMap<>();
        final Set<Long> modified = new HashSet<>();
        BlockType<BTree.NodeBlock> nodeType;
        long nextOffset = 1;

        @Override
        public int blockSize() {
            return 64;
        }

        @Override
        public DataStorage.Area area() {
            return DataStorage.Area.META;
        }

        @Override
        public BTree.NodeBlock readBlock(long blockOffset, BlockType<BTree.NodeBlock> type) {
            nodeType = type;
            return new BTree.NodeBlock(java.util.Objects.requireNonNull(blocks.get(blockOffset)), type);
        }

        @Override
        public boolean isBlockModified(long blockOffset) {
            return modified.contains(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, BTree.NodeBlock data) {
            assertTrue(modified.contains(blockOffset));
            byte[] previousData = blocks.get(blockOffset);
            adjustChildReferences(data, 1);
            blocks.put(blockOffset, data.data.clone());
            adjustChildReferences(blockOffset, previousData, -1);
        }

        @Override
        public long writeNewBlock(BTree.NodeBlock data) {
            assertEquals(blockSize(), data.data.length);
            nodeType = data.getBlockType();
            long offset = nextOffset++;
            blocks.put(offset, data.data.clone());
            references.put(offset, 1);
            modified.add(offset);
            adjustChildReferences(data, 1);
            unclaimed.add(offset);
            return offset;
        }

        final Set<Long> unclaimed = new HashSet<>();

        private void retain(long blockOffset) {
            if (blockOffset != 0) {
                Integer current = references.get(blockOffset);
                if (current == null)
                    throw new IllegalStateException("Increment of unreferenced block " + blockOffset
                            + "; allocated=" + blocks.containsKey(blockOffset));
                references.put(blockOffset, current + 1);
            }
        }

        private void release(long blockOffset) {
            if (blockOffset == 0)
                return;
            int remaining = references.get(blockOffset) - 1;
            assertTrue(remaining >= 0);
            references.put(blockOffset, remaining);
            if (remaining == 0) {
                adjustChildReferences(blockOffset, blocks.get(blockOffset), -1);
                blocks.remove(blockOffset);
                references.remove(blockOffset);
                modified.remove(blockOffset);
                unclaimed.remove(blockOffset);
            }
        }

        void publishRoot(long oldOffset, long newOffset) {
            if (oldOffset == newOffset)
                return;
            if (newOffset != 0 && !unclaimed.remove(newOffset))
                retain(newOffset);
            unclaimed.remove(oldOffset);
            release(oldOffset);
        }

        void retainRoot(long blockOffset) {
            retain(blockOffset);
        }

        void releaseRoot(long blockOffset) {
            release(blockOffset);
        }

        private void adjustChildReferences(long blockOffset, byte[] data, int adjustment) {
            if (data == null)
                return;
            adjustChildReferences(new BTree.NodeBlock(data, nodeType), adjustment);
        }

        private void adjustChildReferences(BTree.NodeBlock block, int adjustment) {
            for (var reference : nodeType.getReferencedBlocks(block)) {
                if (adjustment > 0)
                    retain(reference.blockOffset());
                else
                    release(reference.blockOffset());
                if (adjustment > 0 && unclaimed.remove(reference.blockOffset()))
                    release(reference.blockOffset());
            }
        }

        @Override
        public boolean prepareFreeSpace(int blockCount) {
            return true;
        }
    }

    private static byte[] key(int value) {
        return new byte[] { (byte) (value >>> 8), (byte) value };
    }

    private static void put(MemoryStorage storage, BTree tree, byte[] key, byte[] value) {
        long oldRoot = tree.rootOffset;
        tree.put(key, value);
        storage.publishRoot(oldRoot, tree.rootOffset);
    }

    private static boolean remove(MemoryStorage storage, BTree tree, byte[] key) {
        long oldRoot = tree.rootOffset;
        boolean result = tree.remove(key);
        storage.publishRoot(oldRoot, tree.rootOffset);
        return result;
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
        put(storage, tree, key(1), new byte[] { 10 });
        long root = tree.rootOffset;
        put(storage, tree, key(1), new byte[] { 20 });
        assertEquals(root, tree.rootOffset);
        assertFalse(remove(storage, tree, key(2)));
        assertTrue(remove(storage, tree, key(1)));
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
            put(storage, tree, key(index), key(index));
        long oldRoot = tree.rootOffset;
        storage.retainRoot(oldRoot);
        storage.modified.clear();
        BTree snapshot = new BTree(storage, 2, null, oldRoot);

        put(storage, tree, key(50), new byte[] { 42 });
        assertNotEquals(oldRoot, tree.rootOffset);
        assertArrayEquals(key(50), snapshot.get(key(50)));
        assertArrayEquals(new byte[] { 42 }, tree.get(key(50)));
        for (int index = 0; index < 100; index++)
            assertTrue(remove(storage, tree, key(index)));
        assertEquals(0, tree.rootOffset);
        for (int index = 0; index < 100; index++)
            assertArrayEquals(key(index), snapshot.get(key(index)));

        storage.releaseRoot(oldRoot);
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
                put(storage, tree, key(entryKey), value);
                expected.put(entryKey, value);
            } else {
                assertEquals(expected.remove(entryKey) != null, remove(storage, tree, key(entryKey)));
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
            storage.releaseRoot(tree.rootOffset);
        assertTrue(storage.blocks.isEmpty());
    }
}