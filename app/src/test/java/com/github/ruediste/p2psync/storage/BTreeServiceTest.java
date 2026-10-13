package com.github.ruediste.p2psync.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.storage.DataStorage.BlockArea;

public class BTreeServiceTest {

    private Path dir;
    private Path file;
    private DataStorage storage;
    private BTreeService service;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("p2psync-btree");
        file = dir.resolve("data.bin");
        storage = new DataStorage();
        // Small metadata blocks force frequent splits.
        storage.format(file.toString(), 512, 1024, 512, 64 * 1024, 512 * 1024, 256 * 1024);
        service = new BTreeService(storage);
    }

    @After
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

    private BTreeService.Tree newTree() {
        return service.createTree(BlockArea.META, 8, 1);
    }

    @Test
    public void putAndGetSingle() {
        BTreeService.Tree tree = newTree();
        service.put(tree, key(5), bytes("hello"));
        assertArrayEquals(bytes("hello"), service.get(tree, key(5)));
        assertNull(service.get(tree, key(6)));
        assertFalse(service.contains(tree, key(6)));
        assertTrue(service.contains(tree, key(5)));
    }

    @Test
    public void updateExistingKey() {
        BTreeService.Tree tree = newTree();
        service.put(tree, key(1), bytes("a"));
        service.put(tree, key(1), bytes("bbbb"));
        assertArrayEquals(bytes("bbbb"), service.get(tree, key(1)));
        assertEquals(1, count(tree));
    }

    @Test
    public void insertManyAndReadAll() {
        BTreeService.Tree tree = newTree();
        Map<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long k = rnd.nextInt(5000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(120));
            service.put(tree, key(k), v);
            expected.put(k, v);
        }
        for (Map.Entry<Long, byte[]> e : expected.entrySet())
            assertArrayEquals(e.getValue(), service.get(tree, key(e.getKey())));
        assertEquals(expected.size(), count(tree));
    }

    @Test
    public void forEachReturnsSortedOrder() {
        BTreeService.Tree tree = newTree();
        Random rnd = new Random(7);
        TreeMap<Long, byte[]> expected = new TreeMap<>();
        for (int i = 0; i < 500; i++) {
            long k = rnd.nextInt(100000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(80));
            service.put(tree, key(k), v);
            expected.put(k, v);
        }
        List<Long> seen = new ArrayList<>();
        service.forEach(tree, (k, v) -> seen.add(longOf(k)));
        List<Long> expectedKeys = new ArrayList<>(expected.keySet());
        assertEquals(expectedKeys, seen);
    }

    @Test
    public void removeEntries() {
        BTreeService.Tree tree = newTree();
        for (int i = 0; i < 300; i++)
            service.put(tree, key(i), bytes("value-" + i));
        for (int i = 0; i < 300; i += 2)
            assertTrue(service.remove(tree, key(i)));
        for (int i = 0; i < 300; i++) {
            if (i % 2 == 0)
                assertNull(service.get(tree, key(i)));
            else
                assertArrayEquals(bytes("value-" + i), service.get(tree, key(i)));
        }
        assertEquals(150, count(tree));
    }

    @Test
    public void removeNonExistentReturnsFalse() {
        BTreeService.Tree tree = newTree();
        service.put(tree, key(1), bytes("a"));
        assertFalse(service.remove(tree, key(2)));
    }

    @Test
    public void removeAllLeavesEmptyTree() {
        BTreeService.Tree tree = newTree();
        for (int i = 0; i < 200; i++)
            service.put(tree, key(i), bytes("v" + i));
        for (int i = 0; i < 200; i++)
            assertTrue(service.remove(tree, key(i)));
        assertEquals(0, tree.rootOffset);
        assertEquals(0, count(tree));
    }

    @Test
    public void persistsAcrossReopen() {
        BTreeService.Tree tree = newTree();
        Map<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(11);
        for (int i = 0; i < 400; i++) {
            long k = rnd.nextInt(10000);
            byte[] v = randomBytes(rnd, 1 + rnd.nextInt(100));
            service.put(tree, key(k), v);
            expected.put(k, v);
        }
        long root = tree.rootOffset;
        storage.commit();
        storage.close();

        storage = new DataStorage();
        storage.open(file.toString());
        service = new BTreeService(storage);
        BTreeService.Tree reopened = service.createTree(BlockArea.META, 8, 1);
        reopened.rootOffset = root;
        for (Map.Entry<Long, byte[]> e : expected.entrySet())
            assertArrayEquals(e.getValue(), service.get(reopened, key(e.getKey())));
        assertEquals(expected.size(), count(reopened));
    }

    @Test
    public void copyOnWriteKeepsOldRootReadable() {
        BTreeService.Tree tree = newTree();
        for (int i = 0; i < 100; i++)
            service.put(tree, key(i), bytes("old-" + i));
        storage.commit();

        long oldRoot = tree.rootOffset;
        BTreeService.Tree snapshot = service.createTree(BlockArea.META, 8, 1);
        snapshot.rootOffset = oldRoot;

        service.put(tree, key(50), bytes("new-50"));
        assertTrue(tree.rootOffset != oldRoot);

        // The old version is still readable before the commit overwrites it.
        assertArrayEquals(bytes("old-50"), service.get(snapshot, key(50)));
        assertArrayEquals(bytes("new-50"), service.get(tree, key(50)));
    }

    @Test
    public void blockCountIsReasonable() {
        BTreeService.Tree tree = newTree();
        for (int i = 0; i < 1000; i++)
            service.put(tree, key(i), bytes("value-" + String.format("%04d", i)));
        assertEquals(1000, count(tree));
        storage.commit();
        assertTrue(storage.getRefCountEntryCount() > 1);
    }

    private int count(BTreeService.Tree tree) {
        int[] c = { 0 };
        service.forEach(tree, (k, v) -> c[0]++);
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
