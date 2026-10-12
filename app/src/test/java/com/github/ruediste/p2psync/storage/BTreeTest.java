package com.github.ruediste.p2psync.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BTreeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    Path path;

    AtomicSpace space;
    BlockSpace blockSpace;
    BTree tree;

    private static final int KEY_SIZE = 8;

    @Before
    public void before() throws IOException {
        path = tmp.newFolder().toPath().resolve("space.bin");
        space = new AtomicSpace();
        space.open(path);
        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        tree = BTree.create(blockSpace, KEY_SIZE);
    }

    @After
    public void after() {
        space.close();
    }

    private byte[] key(long i) {
        return ByteBuffer.allocate(8).putLong(i).array();
    }

    private byte[] value(long i) {
        return ("value-" + i).getBytes();
    }

    @Test
    public void emptyTreeSearchReturnsNull() {
        assertNull(tree.search(key(1)));
    }

    @Test
    public void insertAndSearch() {
        for (long i = 0; i < 100; i++) {
            tree.insert(key(i), value(i));
        }
        for (long i = 0; i < 100; i++) {
            assertArrayEquals(value(i), tree.search(key(i)));
        }
        assertNull(tree.search(key(100)));
    }

    @Test
    public void updateExistingKey() {
        tree.insert(key(1), value(1));
        tree.insert(key(1), value(42));
        assertArrayEquals(value(42), tree.search(key(1)));
    }

    @Test
    public void insertCausesSplits() {
        // enough entries to force many leaf and inner node splits
        for (long i = 0; i < 5000; i++) {
            tree.insert(key(i), value(i));
            if (i % 50 == 0) {
                blockSpace.commit();
            }
        }
        for (long i = 0; i < 5000; i++) {
            assertArrayEquals(value(i), tree.search(key(i)));
        }
        assertNull(tree.search(key(5000)));
    }

    @Test
    public void deleteRemovesEntries() {
        for (long i = 0; i < 500; i++) {
            tree.insert(key(i), value(i));
            if (i % 50 == 0) {
                blockSpace.commit();
            }
        }
        for (long i = 0; i < 500; i += 2) {
            tree.delete(key(i));
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        for (long i = 0; i < 500; i++) {
            if (i % 2 == 0) {
                assertNull(tree.search(key(i)));
            } else {
                assertArrayEquals(value(i), tree.search(key(i)));
            }
        }
        // deleting a non existing key is a no-op
        tree.delete(key(500));
        assertNull(tree.search(key(500)));
        assertArrayEquals(value(1), tree.search(key(1)));
    }

    @Test
    public void deleteHeavyWorkloadReclaimsBlocks() {
        TreeMap<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(1234);
        long peakBlocks = 0;

        // build up a large tree
        for (long i = 0; i < 20000; i++) {
            byte[] v = value(rnd.nextLong());
            tree.insert(key(i), v);
            expected.put(i, v);
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        blockSpace.commit();
        peakBlocks = blockSpace.getBlockCount() - blockSpace.countFreeBlocks();

        // delete all but a handful, forcing many merges and block frees
        for (long i = 0; i < 20000; i++) {
            if (i % 1000 != 0) {
                tree.delete(key(i));
                expected.remove(i);
            }
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        blockSpace.commit();

        long remainingBlocks = blockSpace.getBlockCount() - blockSpace.countFreeBlocks();
        assertTrue("expected blocks to be reclaimed, peak=" + peakBlocks + " remaining=" + remainingBlocks,
                remainingBlocks < peakBlocks / 2);

        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), tree.search(key(entry.getKey())));
        }
        for (long i = 0; i < 20000; i++) {
            if (i % 1000 != 0) {
                assertNull(tree.search(key(i)));
            }
        }

        // the tree must still accept inserts after heavy deletion
        for (long i = 20000; i < 30000; i++) {
            byte[] v = value(i);
            tree.insert(key(i), v);
            expected.put(i, v);
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        blockSpace.commit();
        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), tree.search(key(entry.getKey())));
        }
    }

    @Test
    public void randomOperationsMatchTreeMap() {
        TreeMap<Long, byte[]> expected = new TreeMap<>();
        Random rnd = new Random(42);

        for (int i = 0; i < 10000; i++) {
            long k = rnd.nextInt(3000);
            if (rnd.nextInt(4) == 0) {
                tree.delete(key(k));
                expected.remove(k);
            } else {
                byte[] v = value(rnd.nextLong());
                tree.insert(key(k), v);
                expected.put(k, v);
            }
            if (i % 50 == 0) {
                blockSpace.commit();
            }
        }

        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), tree.search(key(entry.getKey())));
        }
        // no extra entries
        for (long k = 3000; k < 3010; k++) {
            assertNull(tree.search(key(k)));
        }
    }

    @Test
    public void survivesReopen() throws IOException {
        for (long i = 0; i < 3000; i++) {
            tree.insert(key(i), value(i));
            if (i % 50 == 0) {
                blockSpace.commit();
            }
        }
        blockSpace.commit();
        long rootBlock = tree.getRootBlock();
        space.close();

        space = new AtomicSpace();
        space.open(path);
        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        tree = new BTree(blockSpace, rootBlock, KEY_SIZE);

        for (long i = 0; i < 3000; i++) {
            assertArrayEquals(value(i), tree.search(key(i)));
        }
        assertNull(tree.search(key(3000)));
    }

    @Test
    public void invalidKeySizeFails() {
        try {
            tree.insert(new byte[4], value(1));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            tree.search(new byte[4]);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            tree.delete(new byte[4]);
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void keysAreSortedInLeaves() {
        Random rnd = new Random(7);
        TreeMap<Long, byte[]> expected = new TreeMap<>();
        for (int i = 0; i < 2000; i++) {
            long k = rnd.nextLong();
            tree.insert(key(k), value(i));
            expected.put(k, value(i));
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        // walk the leaves via the next leaf pointers and verify order
        long block = findLeftmostLeaf();
        long count = 0;
        byte[] prev = null;
        while (block != 0) {
            ByteBuffer buf = ByteBuffer.wrap(blockSpace.readBlock(block));
            assertEquals(0, buf.get(4)); // leaf
            int numKeys = buf.getShort(5) & 0xFFFF;
            int kvStart = 7 + 8 + 2 * numKeys;
            long next = buf.getLong(7);
            for (int i = 0; i < numKeys; i++) {
                int start = (i == 0) ? 0 : buf.getShort(7 + 8 + 2 * (i - 1)) & 0xFFFF;
                byte[] k = new byte[KEY_SIZE];
                ByteBuffer dup = buf.duplicate();
                dup.position(kvStart + start);
                dup.get(k);
                if (prev != null) {
                    assertTrue(Arrays.compareUnsigned(k, prev) > 0);
                }
                prev = k;
                count++;
            }
            block = next;
        }
        assertEquals(expected.size(), count);
    }

    private long findLeftmostLeaf() {
        long block = tree.getRootBlock();
        while (blockSpace.readBlock(block)[4] != 0) {
            block = ByteBuffer.wrap(blockSpace.readBlock(block)).getLong(7);
        }
        return block;
    }

    @Test
    public void variableValueSizes() {
        Random rnd = new Random(11);
        Map<Long, byte[]> expected = new HashMap<>();
        for (long i = 0; i < 1000; i++) {
            int len = rnd.nextInt(200);
            byte[] v = new byte[len];
            rnd.nextBytes(v);
            tree.insert(key(i), v);
            expected.put(i, v);
            if (i % 100 == 0) {
                blockSpace.commit();
            }
        }
        // update some with different sizes
        for (long i = 0; i < 1000; i += 3) {
            int len = rnd.nextInt(300);
            byte[] v = new byte[len];
            rnd.nextBytes(v);
            tree.insert(key(i), v);
            expected.put(i, v);
            if (i % 50 == 0) {
                blockSpace.commit();
            }
        }
        blockSpace.commit();
        for (Map.Entry<Long, byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), tree.search(key(e.getKey())));
        }
        assertEquals(1000, expected.size());
        for (long i = 0; i < 1000; i++) {
            if (i % 3 != 0) {
                // unchanged entries must match original values
                assertTrue(Arrays.equals(tree.search(key(i)), expected.get(i)));
            }
        }
    }
}
