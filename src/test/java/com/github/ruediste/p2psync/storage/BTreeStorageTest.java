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
    /**
     * Verifies the entry adapter serializes only the value and leaves the decoded
     * key null.
     */
    @Test
    void entryTypeDeserializesWithoutKey() {
        var type = BTree.BYTE_ARRAY_ENTRY_TYPE;
        var entry = type.deserialize(type.serialize(new BTree.Entry<>(key(1), key(257))));
        org.junit.jupiter.api.Assertions.assertNull(entry.key);
        assertArrayEquals(key(257), entry.value);
    }

    /**
     * Verifies fixed-size byte keys round-trip and reject an invalid serialized
     * size.
     */
    @Test
    void byteKeySerializerEnforcesFixedSize() {
        var serializer = BTree.KeySerializer.bytes(2);
        assertArrayEquals(key(257), serializer.deserialize(serializer.serialize(key(257))));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> serializer.serialize(new byte[1]));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(java.nio.ByteBuffer.allocate(3)));
    }

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
            updateChildReferences(data, 1);
            blocks.put(blockOffset, data.data.clone());
            updateChildReferences(blockOffset, previousData, -1);
        }

        @Override
        public long writeNewBlock(BTree.NodeBlock data) {
            assertEquals(blockSize(), data.data.length);
            nodeType = data.getBlockType();
            long offset = nextOffset++;
            blocks.put(offset, data.data.clone());
            references.put(offset, 1);
            modified.add(offset);
            updateChildReferences(data, 1);
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
                updateChildReferences(blockOffset, blocks.get(blockOffset), -1);
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

        private void updateChildReferences(long blockOffset, byte[] data, int referenceChange) {
            if (data == null)
                return;
            updateChildReferences(new BTree.NodeBlock(data, nodeType), referenceChange);
        }

        private void updateChildReferences(BTree.NodeBlock block, int referenceChange) {
            for (var reference : nodeType.getReferencedBlocks(block, null)) {
                if (referenceChange > 0)
                    retain(reference.blockOffset());
                else
                    release(reference.blockOffset());
                if (referenceChange > 0 && unclaimed.remove(reference.blockOffset()))
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

    private static void put(MemoryStorage storage, BTree<byte[], byte[]> tree, byte[] key, byte[] value) {
        long oldRoot = tree.rootOffset;
        tree.put(key, value);
        storage.publishRoot(oldRoot, tree.rootOffset);
    }

    private static boolean remove(MemoryStorage storage, BTree<byte[], byte[]> tree, byte[] key) {
        long oldRoot = tree.rootOffset;
        boolean result = tree.remove(key);
        storage.publishRoot(oldRoot, tree.rootOffset);
        return result;
    }

    /**
     * Replaces and removes an entry while its leaf is still transaction-modified.
     * The leaf should be reused, absent-key removal should do nothing, and the
     * final removal should reclaim the empty leaf.
     */
    @Test
    void modifiedLeafIsReusedAndReclaimedWhenEmpty() {
        MemoryStorage storage = new MemoryStorage();
        BTree<byte[], byte[]> tree = new BTree<>(storage, BTree.KeySerializer.bytes(2), BTree.BYTE_ARRAY_ENTRY_TYPE);
        put(storage, tree, key(1), new byte[] { 10 });
        long root = tree.rootOffset;
        put(storage, tree, key(1), new byte[] { 20 });
        assertEquals(root, tree.rootOffset);
        assertFalse(remove(storage, tree, key(2)));
        assertTrue(remove(storage, tree, key(1)));
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
        BTree<byte[], byte[]> tree = new BTree<>(storage, BTree.KeySerializer.bytes(2), BTree.BYTE_ARRAY_ENTRY_TYPE);
        for (int index = 0; index < 100; index++)
            put(storage, tree, key(index), key(index));
        long oldRoot = tree.rootOffset;
        storage.retainRoot(oldRoot);
        storage.modified.clear();
        BTree<byte[], byte[]> snapshot = new BTree<>(storage, BTree.KeySerializer.bytes(2), BTree.BYTE_ARRAY_ENTRY_TYPE,
                oldRoot);

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
        BTree<byte[], byte[]> tree = new BTree<>(storage, BTree.KeySerializer.bytes(2), BTree.BYTE_ARRAY_ENTRY_TYPE);
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