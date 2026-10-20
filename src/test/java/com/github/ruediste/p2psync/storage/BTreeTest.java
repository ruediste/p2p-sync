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

    private BTree newTree() {
        return new BTree(storage.new BTreeStorage(DataStorage.Area.META), 8, null);
    }

    @Test
    public void putAndGetSingle() {
        BTree tree = newTree();
        tree.put(key(5), bytes("hello"));
        assertArrayEquals(bytes("hello"), tree.get(key(5)));
        assertNull(tree.get(key(6)));
        assertFalse(tree.contains(key(6)));
        assertTrue(tree.contains(key(5)));
    }

    @Test
    public void updateExistingKey() {
        BTree tree = newTree();
        tree.put(key(1), bytes("a"));
        tree.put(key(1), bytes("bbbb"));
        assertArrayEquals(bytes("bbbb"), tree.get(key(1)));
        assertEquals(1, count(tree));
    }

    @Test
    public void insertManyAndReadAll() {
        BTree tree = newTree();
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
        BTree tree = newTree();
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
        BTree tree = newTree();
        for (int i = 0; i < 100; i++)
            tree.put(key((i + 1) * 10), bytes("value-" + i));

        Iterator<BTree.Entry> iterator = tree.floorIterator(key(455));
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
        BTree tree = newTree();
        for (int i = 99; i >= 0; i--)
            tree.put(key(i), bytes("value-" + i));

        List<Long> seen = new ArrayList<>();
        for (BTree.Entry entry : tree)
            seen.add(longOf(entry.key));
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            expected.add((long) i);
        assertEquals(expected, seen);
        assertFalse(newTree().iterator().hasNext());
    }

    @Test
    public void removeEntries() {
        BTree tree = newTree();
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
        BTree tree = newTree();
        tree.put(key(20), bytes("twenty"));
        tree.put(key(0), bytes("zero"));
        tree.put(key(10), bytes("ten"));

        BTree.Entry removed = tree.removeFirst().orElseThrow();
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
        BTree tree = newTree();
        tree.put(key(1), bytes("a"));
        assertFalse(tree.remove(key(2)));
    }

    @Test
    public void removeAllLeavesEmptyTree() {
        BTree tree = newTree();
        for (int i = 0; i < 200; i++)
            tree.put(key(i), bytes("v" + i));
        for (int i = 0; i < 200; i++)
            assertTrue(tree.remove(key(i)));
        assertEquals(0, tree.rootOffset);
        assertEquals(0, count(tree));
    }

    @Test
    public void persistsAcrossReopen() {
        BTree tree = newTree();
        Map<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(11);
        for (int i = 0; i < 400; i++) {
            long k = rnd.nextInt(10000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(100));
            tree.put(key(k), v);
            expected.put(k, v);
        }
        long root = tree.rootOffset;
        storage.incrementRefCount(root, DataStorage.Area.META);
        storage.commit();
        storage.close();

        storage = new DataStorage();
        storage.open(file.toString());
        BTree reopened = newTree();
        reopened.rootOffset = root;
        for (Map.Entry<Long, byte[]> e : expected.entrySet())
            assertArrayEquals(e.getValue(), reopened.get(key(e.getKey())));
        assertEquals(expected.size(), count(reopened));
    }

    @Test
    public void copyOnWriteKeepsOldRootReadable() {
        BTree tree = newTree();
        for (int i = 0; i < 100; i++)
            tree.put(key(i), bytes("old-" + i));
        storage.incrementRefCount(tree.rootOffset, DataStorage.Area.META);
        storage.commit();

        long oldRoot = tree.rootOffset;
        BTree snapshot = newTree();
        snapshot.rootOffset = oldRoot;

        tree.put(key(50), bytes("new-50"));
        assertTrue(tree.rootOffset != oldRoot);

        // The old version is still readable before the commit overwrites it.
        assertArrayEquals(bytes("old-50"), snapshot.get(key(50)));
        assertArrayEquals(bytes("new-50"), tree.get(key(50)));
    }

    @Test
    public void blockCountIsReasonable() {
        BTree tree = newTree();
        for (int i = 0; i < 1000; i++)
            tree.put(key(i), bytes("value-" + String.format("%04d", i)));
        assertEquals(1000, count(tree));
        storage.incrementRefCount(tree.rootOffset, DataStorage.Area.META);
        storage.commit();
        assertTrue(storage.refCountAdjustments.size() > 1);
    }

    private int count(BTree tree) {
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
