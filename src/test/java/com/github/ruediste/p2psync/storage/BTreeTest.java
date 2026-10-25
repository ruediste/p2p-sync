package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BTreeTest {

    private static final BTree.KeySerializer<Long> LONG_KEYS = new BTree.KeySerializer<>() {
        @Override
        public int keySize() {
            return Long.BYTES;
        }

        @Override
        public ByteBuffer serialize(Long value) {
            return ByteBuffer.allocate(Long.BYTES).putLong(value).flip();
        }

        @Override
        public Long deserialize(ByteBuffer buffer) {
            return buffer.getLong();
        }
    };

    private Path dir;
    private Path file;
    private DataStorage storage;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("p2psync-btree");
        file = dir.resolve("data.bin");
        storage = new DataStorage();
        // Small metadata blocks force frequent splits.
        storage.format(file.toString(), 64 * 1024, 512 * 1024, 256 * 1024, 512, 1024, 512);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private BTree<byte[], byte[]> newTree() {
        return new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), BTree.KeySerializer.bytes(8),
                BTree.BYTE_ARRAY_ENTRY_TYPE);
    }

    /**
     * Inserts ascending fixed-size entries and checks leaf occupancy so repeated
     * splits cannot leave one-entry leaves behind.
     */
    @Test
    void ascendingInsertsKeepLeavesBalanced() {
        var tree = newTree();
        for (int index = 0; index < 200; index++)
            tree.put(key(index), key(index));

        var root = ByteBuffer.wrap(storage.readBlock(tree.rootOffset, DataStorage.Area.META))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, root.get(4));
        int leafCount = root.getInt(0);
        assertTrue(leafCount <= 10, "Ascending inserts should keep leaves at least half full");
        for (int index = 0; index < leafCount; index++) {
            long leafOffset = root.getLong(5 + index * 16 + Long.BYTES);
            var leaf = ByteBuffer.wrap(storage.readBlock(leafOffset, DataStorage.Area.META))
                    .order(ByteOrder.LITTLE_ENDIAN);
            assertTrue(leaf.getInt(0) >= 21, "Split leaves should contain at least 21 entries");
        }
        for (int index = 0; index < 200; index++)
            assertArrayEquals(key(index), tree.get(key(index)));
    }

    /**
     * Splits small entries followed by a large value and checks the split balances
     * serialized bytes rather than the number of entries, preserving all values.
     */
    @Test
    void leafSplitBalancesBytesForUnevenValues() {
        var tree = newTree();
        for (int index = 0; index < 18; index++)
            tree.put(key(index), key(index));
        byte[] largeValue = new byte[600];
        tree.put(key(18), largeValue);

        var root = ByteBuffer.wrap(storage.readBlock(tree.rootOffset, DataStorage.Area.META))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, root.get(4));
        assertEquals(2, root.getInt(0));
        var left = ByteBuffer.wrap(storage.readBlock(root.getLong(13), DataStorage.Area.META))
                .order(ByteOrder.LITTLE_ENDIAN);
        var right = ByteBuffer.wrap(storage.readBlock(root.getLong(29), DataStorage.Area.META))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(18, left.getInt(0));
        assertEquals(1, right.getInt(0));
        for (int index = 0; index < 18; index++)
            assertArrayEquals(key(index), tree.get(key(index)));
        assertArrayEquals(largeValue, tree.get(key(18)));
    }

    /**
     * Exercises typed keys and values through splits, floor lookup, iteration,
     * replacement and removal.
     */
    @Test
    void typedKeysAndValuesRoundTripThroughAllOperations() {
        BlockType<BTree.Entry<Long, String>> strings = new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(BTree.Entry<Long, String> entry, DataStorage storage) {
                org.junit.jupiter.api.Assertions.assertNotNull(entry.key);
                return List.of();
            }

            @Override
            public ByteBuffer serialize(BTree.Entry<Long, String> entry) {
                org.junit.jupiter.api.Assertions.assertNotNull(entry.key);
                byte[] data = bytes(entry.value);
                return ByteBuffer.allocate(data.length + 2).put((byte) 0).put(data).flip().position(1);
            }

            @Override
            public BTree.Entry<Long, String> deserialize(ByteBuffer buffer) {
                return new BTree.Entry<>(null, java.nio.charset.StandardCharsets.UTF_8.decode(buffer).toString());
            }
        };
        BTree<Long, String> tree = new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), LONG_KEYS, strings);
        for (long index = 100; index >= 1; index--)
            tree.put(index * 10, "value-" + index);
        assertEquals("value-45", tree.get(450L));
        assertNull(tree.get(451L));
        assertTrue(tree.contains(450L));
        tree.put(450L, "replaced");
        var floor = tree.floorIterator(455L).next();
        assertEquals(450L, floor.key);
        assertEquals("replaced", floor.value);
        assertFalse(tree.floorIterator(0L).hasNext());
        assertEquals(10L, tree.floorOrLowestIterator(0L).next().key);
        List<Long> seen = new ArrayList<>();
        tree.forEach((entryKey, value) -> seen.add(entryKey));
        assertEquals(java.util.stream.LongStream.rangeClosed(1, 100).map(index -> index * 10).boxed().toList(), seen);
        assertEquals(10L, tree.iterator().next().key);
        assertEquals("value-1", tree.removeFirst().orElseThrow().value);
        assertTrue(tree.remove(450L));
        assertFalse(tree.remove(450L));
        var reopened = new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), LONG_KEYS, strings,
                tree.rootOffset);
        assertEquals("value-99", reopened.get(990L));
    }

    /**
     * Keeps a snapshot while replacing a referenced value, then checks each DATA
     * block is freed with its last leaf.
     */
    @Test
    void leafValuesRetainReferencesUntilTheirLastSnapshotIsReleased() {
        BlockType<BTree.Entry<Long, Long>> references = referenceType();
        var nodeType = BTree.blockType(LONG_KEYS, DataStorage.Area.META, references);
        var tree = new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), LONG_KEYS, references);
        long firstBlock = storage.writeNewBlock(DataStorage.Area.DATA,
                new byte[storage.blockSize(DataStorage.Area.DATA)]);
        tree.put(1L, firstBlock);
        long snapshotRoot = tree.rootOffset;
        storage.incrementRefCount(new BlockType.BlockRef(snapshotRoot, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        assertEquals(1, storage.getRefCount(firstBlock));

        long secondBlock = storage.writeNewBlock(DataStorage.Area.DATA,
                new byte[storage.blockSize(DataStorage.Area.DATA)]);
        tree.put(1L, secondBlock);
        long liveRoot = tree.rootOffset;
        storage.incrementRefCount(new BlockType.BlockRef(liveRoot, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        var snapshot = new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), LONG_KEYS, references,
                snapshotRoot);
        assertEquals(firstBlock, snapshot.get(1L));
        assertEquals(secondBlock, tree.get(1L));
        assertEquals(1, storage.getRefCount(firstBlock));
        assertEquals(1, storage.getRefCount(secondBlock));

        assertTrue(tree.remove(1L));
        storage.decrementRefCount(new BlockType.BlockRef(liveRoot, DataStorage.Area.META, nodeType));
        storage.commit();
        assertEquals(0, storage.getRefCount(secondBlock));
        assertEquals(1, storage.getRefCount(firstBlock));
        storage.decrementRefCount(new BlockType.BlockRef(snapshotRoot, DataStorage.Area.META, nodeType));
        storage.commit();
        assertEquals(0, storage.getRefCount(firstBlock));
    }

    /**
     * Forces leaf splits and verifies moving references never frees
     * transaction-local DATA blocks.
     */
    @Test
    void leafSplitsPreserveReferencedBlocks() {
        BlockType<BTree.Entry<Long, Long>> references = referenceType();
        var tree = new BTree<>(storage.new BTreeStorage(DataStorage.Area.META), LONG_KEYS, references);
        List<Long> blocks = new ArrayList<>();
        for (long index = 0; index < 100; index++) {
            byte[] data = new byte[storage.blockSize(DataStorage.Area.DATA)];
            data[0] = (byte) index;
            long block = storage.writeNewBlock(DataStorage.Area.DATA, data);
            blocks.add(block);
            tree.put(index, block);
        }
        for (int index = 0; index < blocks.size(); index++) {
            assertEquals(blocks.get(index), tree.get((long) index));
            assertTrue(storage.isBlockModified(blocks.get(index)));
            assertEquals((byte) index, storage.readBlock(blocks.get(index), DataStorage.Area.DATA)[0]);
        }
        storage.incrementRefCount(new BlockType.BlockRef(tree.rootOffset, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        for (long block : blocks)
            assertEquals(1, storage.getRefCount(block));
    }

    private static BlockType<BTree.Entry<Long, Long>> referenceType() {
        return new BlockType<>() {
            @Override
            public List<BlockRef> getReferencedBlocks(BTree.Entry<Long, Long> entry, DataStorage storage) {
                org.junit.jupiter.api.Assertions.assertNotNull(entry.key);
                return List.of(new BlockRef(entry.value, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
            }

            @Override
            public ByteBuffer serialize(BTree.Entry<Long, Long> entry) {
                org.junit.jupiter.api.Assertions.assertNotNull(entry.key);
                return ByteBuffer.allocate(Long.BYTES).putLong(entry.value).flip();
            }

            @Override
            public BTree.Entry<Long, Long> deserialize(ByteBuffer buffer) {
                return new BTree.Entry<>(null, buffer.getLong());
            }
        };
    }

    @Test
    public void putAndGetSingle() {
        BTree<byte[], byte[]> tree = newTree();
        tree.put(key(5), bytes("hello"));
        assertArrayEquals(bytes("hello"), tree.get(key(5)));
        assertNull(tree.get(key(6)));
        assertFalse(tree.contains(key(6)));
        assertTrue(tree.contains(key(5)));
    }

    @Test
    public void updateExistingKey() {
        BTree<byte[], byte[]> tree = newTree();
        tree.put(key(1), bytes("a"));
        tree.put(key(1), bytes("bbbb"));
        assertArrayEquals(bytes("bbbb"), tree.get(key(1)));
        assertEquals(1, count(tree));
    }

    @Test
    public void insertManyAndReadAll() {
        BTree<byte[], byte[]> tree = newTree();
        Map<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long k = rnd.nextInt(5000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(120));
            tree.put(key(k), v);
            expected.put(k, v);
        }
        for (Map.Entry<Long, byte[]> e : expected.entrySet())
            assertArrayEquals(e.getValue(), tree.get(key(e.getKey())));
        assertEquals(expected.size(), count(tree));
    }

    @Test
    public void forEachReturnsSortedOrder() {
        BTree<byte[], byte[]> tree = newTree();
        Random rnd = new Random(7);
        TreeMap<Long, byte[]> expected = new TreeMap<>();
        for (int i = 0; i < 500; i++) {
            long k = rnd.nextInt(100000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(80));
            tree.put(key(k), v);
            expected.put(k, v);
        }
        List<Long> seen = new ArrayList<>();
        tree.forEach((k, v) -> seen.add(longOf(k)));
        List<Long> expectedKeys = new ArrayList<>(expected.keySet());
        assertEquals(expectedKeys, seen);
    }

    @Test
    public void floorIteratorStartsAtFloorAndContinuesAcrossLeaves() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 0; i < 100; i++)
            tree.put(key((i + 1) * 10), bytes("value-" + i));

        Iterator<BTree.Entry<byte[], byte[]>> iterator = tree.floorIterator(key(455));
        List<Long> seen = new ArrayList<>();
        while (iterator.hasNext())
            seen.add(longOf(iterator.next().key));
        List<Long> expected = new ArrayList<>();
        for (int i = 44; i < 100; i++)
            expected.add((long) (i + 1) * 10);
        assertEquals(expected, seen);

        assertFalse(tree.floorIterator(key(0)).hasNext());
        iterator = tree.floorIterator(key(1000));
        assertTrue(iterator.hasNext());
        assertEquals(1000, longOf(iterator.next().key));
        assertFalse(iterator.hasNext());
        assertFalse(newTree().floorIterator(key(0)).hasNext());
    }

    @Test
    public void iteratorStartsAtLowestKeyAndReturnsSortedEntries() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 99; i >= 0; i--)
            tree.put(key(i), bytes("value-" + i));

        List<Long> seen = new ArrayList<>();
        for (BTree.Entry<byte[], byte[]> entry : tree)
            seen.add(longOf(entry.key));
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            expected.add((long) i);
        assertEquals(expected, seen);
        assertFalse(newTree().iterator().hasNext());
    }

    @Test
    public void removeEntries() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 0; i < 300; i++)
            tree.put(key(i), bytes("value-" + i));
        for (int i = 0; i < 300; i += 2)
            assertTrue(tree.remove(key(i)));
        for (int i = 0; i < 300; i++) {
            if (i % 2 == 0)
                assertNull(tree.get(key(i)));
            else
                assertArrayEquals(bytes("value-" + i), tree.get(key(i)));
        }
        assertEquals(150, count(tree));
    }

    @Test
    public void removeFirstReturnsAndRemovesLowestEntry() {
        BTree<byte[], byte[]> tree = newTree();
        tree.put(key(20), bytes("twenty"));
        tree.put(key(0), bytes("zero"));
        tree.put(key(10), bytes("ten"));

        BTree.Entry<byte[], byte[]> removed = tree.removeFirst().orElseThrow();
        assertArrayEquals(key(0), removed.key);
        assertArrayEquals(bytes("zero"), removed.value);
        assertNull(tree.get(key(0)));
        assertEquals(2, count(tree));
        assertArrayEquals(key(10), tree.removeFirst().orElseThrow().key);
        assertArrayEquals(key(20), tree.removeFirst().orElseThrow().key);
        assertTrue(tree.removeFirst().isEmpty());
    }

    @Test
    public void removeNonExistentReturnsFalse() {
        BTree<byte[], byte[]> tree = newTree();
        tree.put(key(1), bytes("a"));
        assertFalse(tree.remove(key(2)));
    }

    @Test
    public void removeAllLeavesEmptyTree() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 0; i < 200; i++)
            tree.put(key(i), bytes("v" + i));
        for (int i = 0; i < 200; i++)
            assertTrue(tree.remove(key(i)));
        assertEquals(0, tree.rootOffset);
        assertEquals(0, count(tree));
    }

    @Test
    public void persistsAcrossReopen() {
        BTree<byte[], byte[]> tree = newTree();
        Map<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(11);
        for (int i = 0; i < 400; i++) {
            long k = rnd.nextInt(10000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(100));
            tree.put(key(k), v);
            expected.put(k, v);
        }
        long root = tree.rootOffset;
        storage.incrementRefCount(new BlockType.BlockRef(root, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        storage.close();

        storage = new DataStorage();
        storage.open(file.toString());
        BTree<byte[], byte[]> reopened = newTree();
        reopened.rootOffset = root;
        for (Map.Entry<Long, byte[]> e : expected.entrySet())
            assertArrayEquals(e.getValue(), reopened.get(key(e.getKey())));
        assertEquals(expected.size(), count(reopened));
    }

    @Test
    public void copyOnWriteKeepsOldRootReadable() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 0; i < 100; i++)
            tree.put(key(i), bytes("old-" + i));
        storage.incrementRefCount(new BlockType.BlockRef(tree.rootOffset, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();

        long oldRoot = tree.rootOffset;
        BTree<byte[], byte[]> snapshot = newTree();
        snapshot.rootOffset = oldRoot;

        tree.put(key(50), bytes("new-50"));
        assertTrue(tree.rootOffset != oldRoot);

        // The old version is still readable before the commit overwrites it.
        assertArrayEquals(bytes("old-50"), snapshot.get(key(50)));
        assertArrayEquals(bytes("new-50"), tree.get(key(50)));
    }

    @Test
    public void blockCountIsReasonable() {
        BTree<byte[], byte[]> tree = newTree();
        for (int i = 0; i < 1000; i++)
            tree.put(key(i), bytes("value-" + String.format("%04d", i)));
        assertEquals(1000, count(tree));
        storage.incrementRefCount(new BlockType.BlockRef(tree.rootOffset, DataStorage.Area.META,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        storage.commit();
        assertTrue(storage.refCountUpdates.entrySet().size() > 1);
    }

    private int count(BTree<byte[], byte[]> tree) {
        int[] c = { 0 };
        tree.forEach((k, v) -> c[0]++);
        return c[0];
    }

    private static byte[] key(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
    }

    private static long longOf(byte[] key) {
        return ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(Random rnd, int size) {
        byte[] b = new byte[size];
        rnd.nextBytes(b);
        return b;
    }
}
