package com.github.ruediste.p2psync.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.storage.DataStorage.BlockArea;

public class DataStorageTest {

    private Path dir;
    private Path file;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("p2psync-storage");
        file = dir.resolve("data.bin");
    }

    @After
    public void tearDown() throws IOException {
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private DataStorage format() {
        DataStorage ds = new DataStorage();
        ds.format(file.toString(), 512, 1024, 512, 64 * 1024, 256 * 1024, 256 * 1024);
        return ds;
    }

    @Test
    public void formatAndReopen() {
        DataStorage ds = format();
        assertNotNull(ds.superBlock);
        assertArrayEquals(DataStorage.MAGIC, ds.superBlock.magic);
        assertEquals(DataStorage.FORMAT_VERSION, ds.superBlock.version);
        assertTrue(ds.getRootOffset() != 0);
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertArrayEquals(DataStorage.MAGIC, reopened.superBlock.magic);
        assertEquals(ds.getRootOffset(), reopened.getRootOffset());
        assertEquals(512, reopened.blockSize(BlockArea.DATA));
        assertEquals(1024, reopened.blockSize(BlockArea.META));
        assertEquals(512, reopened.blockSize(BlockArea.SYSTEM));
        reopened.close();
    }

    @Test
    public void writeAndReadBlockSurvivesReopen() {
        DataStorage ds = format();
        long offset = ds.writeNewBlock(BlockArea.META, fill(1024, 7));
        ds.commit();
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertArrayEquals(fill(1024, 7), reopened.readBlock(offset, BlockArea.META));
        reopened.close();
    }

    @Test
    public void modifiedBlockIsVisibleBeforeCommit() {
        DataStorage ds = format();
        long offset = ds.allocateBlock(BlockArea.DATA);
        byte[] data = fill(512, 3);
        ds.writeBlock(offset, BlockArea.DATA, data);
        assertTrue(ds.isBlockModified(offset));
        assertArrayEquals(data, ds.readBlock(offset, BlockArea.DATA));
        ds.close();
    }

    @Test
    public void allocationReducesFreeSpace() {
        DataStorage ds = format();
        long before = ds.getFreeBytes(BlockArea.DATA);
        ds.allocateBlock(BlockArea.DATA);
        assertEquals(before - 512, ds.getFreeBytes(BlockArea.DATA));
        ds.close();
    }

    @Test
    public void freedBlocksAreMerged() {
        DataStorage ds = format();
        long first = ds.allocateBlock(BlockArea.META);
        long second = ds.allocateBlock(BlockArea.META);

        // Allocations must be contiguous for the merge assertion below to be
        // meaningful, which is the case for a fresh region and first-fit.
        assertEquals(first + 1024, second);

        ds.freeBlock(first, BlockArea.META);
        ds.freeBlock(second, BlockArea.META);
        ds.commit();

        Map<Long, Long> free = ds.getFreeSpace().get(BlockArea.META);
        assertEquals(1, free.size());
        assertEquals(262144L, (long) free.get(first));
        ds.close();
    }

    @Test
    public void referenceCountTracksAllocation() {
        DataStorage ds = format();
        long offset = ds.writeNewBlock(BlockArea.DATA, fill(512, 1));
        ds.commit();
        assertEquals(1, ds.getRefCount(offset));

        ds.freeBlock(offset, BlockArea.DATA);
        ds.commit();
        assertEquals(0, ds.getRefCount(offset));
        ds.close();
    }

    @Test
    public void allocatorGrowsWhenRegionExhausted() {
        // A tiny region forces the allocator to extend the file.
        DataStorage ds = new DataStorage();
        ds.format(file.toString(), 512, 1024, 512, 16 * 1024, 16 * 1024, 16 * 1024);
        long initial = ds.getFreeBytes(BlockArea.DATA);
        long allocated = 0;
        for (int i = 0; i < 40; i++) {
            ds.writeNewBlock(BlockArea.DATA, fill(512, i));
            allocated += 512;
        }
        assertTrue(allocated > initial);
        ds.commit();
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertTrue(reopened.getFileSize() > 0);
        reopened.close();
    }

    @Test
    public void infoBlockChangesSurviveReopen() {
        DataStorage ds = format();
        ds.infoBlock.setInlineDataThreshold(1234);
        ds.commit();
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertEquals(1234, reopened.infoBlock.getInlineDataThreshold());
        reopened.close();
    }

    @Test
    public void freeSpaceSurvivesReopen() {
        DataStorage ds = format();
        long offset = ds.writeNewBlock(BlockArea.DATA, fill(512, 9));
        ds.freeBlock(offset, BlockArea.DATA);
        ds.commit();
        long freeBytes = ds.getFreeBytes(BlockArea.DATA);
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertEquals(freeBytes, reopened.getFreeBytes(BlockArea.DATA));
        reopened.close();
    }

    @Test
    public void manyCommitsKeepFileConsistent() {
        DataStorage ds = format();
        Random rnd = new Random(1);
        long[] offsets = new long[20];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = ds.writeNewBlock(BlockArea.DATA, fill(512, rnd.nextInt(256)));
            ds.commit();
        }
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        for (long off : offsets)
            assertEquals(1, reopened.getRefCount(off));
        reopened.close();
    }

    @Test
    public void repeatedInfoBlockUpdatesDoNotLeak() {
        DataStorage ds = format();
        for (int i = 0; i < 25; i++) {
            ds.infoBlock.setInlineDataThreshold(1000 + i);
            ds.commit();
        }
        long sizeAfterManyCommits = ds.getFileSize();
        assertEquals(1000 + 24, ds.infoBlock.getInlineDataThreshold());
        ds.close();

        // Committing the same (unchanged) info block must not keep growing the file.
        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        for (int i = 0; i < 25; i++)
            reopened.commit();
        assertTrue(reopened.getFileSize() <= sizeAfterManyCommits);
        reopened.close();
    }

    @Test
    public void allocatorStateIsStoredInBTrees() {
        DataStorage ds = format();
        assertNotNull(ds.getBTreeService());
        // An empty refcount tree has no root, so create one entry first.
        ds.writeNewBlock(BlockArea.DATA, fill(512, 1));
        ds.commit();
        assertTrue(ds.rootBlock.refCountTree != 0);
        assertTrue(ds.rootBlock.dataFreeSpaceTree != 0);
        assertTrue(ds.rootBlock.metaFreeSpaceTree != 0);
        assertTrue(ds.rootBlock.systemFreeSpaceTree != 0);
        ds.close();
    }

    @Test
    public void complexAllocationSurvivesReopen() {
        DataStorage ds = format();
        long[] data = new long[30];
        long[] meta = new long[10];
        for (int i = 0; i < data.length; i++)
            data[i] = ds.writeNewBlock(BlockArea.DATA, fill(512, i));
        for (int i = 0; i < meta.length; i++)
            meta[i] = ds.writeNewBlock(BlockArea.META, fill(1024, i));
        // Free every third block of both kinds.
        for (int i = 0; i < data.length; i += 3)
            ds.freeBlock(data[i], BlockArea.DATA);
        for (int i = 0; i < meta.length; i += 3)
            ds.freeBlock(meta[i], BlockArea.META);
        ds.commit();
        long dataFree = ds.getFreeBytes(BlockArea.DATA);
        long metaFree = ds.getFreeBytes(BlockArea.META);
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertEquals(dataFree, reopened.getFreeBytes(BlockArea.DATA));
        assertEquals(metaFree, reopened.getFreeBytes(BlockArea.META));
        for (int i = 0; i < data.length; i++)
            assertEquals(i % 3 == 0 ? 0 : 1, reopened.getRefCount(data[i]));
        for (int i = 0; i < meta.length; i++)
            assertEquals(i % 3 == 0 ? 0 : 1, reopened.getRefCount(meta[i]));
        reopened.close();
    }

    @Test
    public void randomizedAllocationSurvivesReopen() {
        DataStorage ds = format();
        Random rnd = new Random(99);
        java.util.TreeMap<Long, Integer> refs = new java.util.TreeMap<>();
        long[] offsets = new long[400];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = ds.writeNewBlock(BlockArea.DATA, fill(512, i));
            refs.put(offsets[i], 1);
            if (i % 3 == 0)
                ds.commit();
        }
        // Free a random half of the blocks.
        for (int i = 0; i < offsets.length; i++) {
            if (rnd.nextBoolean()) {
                ds.freeBlock(offsets[i], BlockArea.DATA);
                refs.put(offsets[i], 0);
            }
        }
        ds.commit();
        long dataFree = ds.getFreeBytes(BlockArea.DATA);
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertEquals(dataFree, reopened.getFreeBytes(BlockArea.DATA));
        for (int i = 0; i < offsets.length; i++)
            assertEquals(refs.get(offsets[i]).longValue(), reopened.getRefCount(offsets[i]));
        reopened.close();
    }

    @Test
    public void incrementalUpdatesSurviveManyReopens() {
        // A sequence of commit / reopen cycles, each mutating free space and
        // refcounts. The incrementally maintained trees must stay consistent.
        java.util.TreeMap<Long, Integer> expectedRefs = new java.util.TreeMap<>();
        Random rnd = new Random(5);
        DataStorage ds = format();
        long expectedDataFree;
        for (int cycle = 0; cycle < 6; cycle++) {
            ds.close();
            ds = new DataStorage();
            ds.open(file.toString());
            for (int i = 0; i < 20; i++) {
                long off = ds.writeNewBlock(BlockArea.DATA, fill(512, i + cycle));
                expectedRefs.put(off, 1);
            }
            for (Long off : new java.util.ArrayList<>(expectedRefs.keySet())) {
                if (expectedRefs.get(off) == 1 && rnd.nextBoolean()) {
                    ds.freeBlock(off, BlockArea.DATA);
                    expectedRefs.put(off, 0);
                }
            }
            ds.commit();
            expectedDataFree = ds.getFreeBytes(BlockArea.DATA);
            assertEquals(expectedDataFree, ds.getFreeBytes(BlockArea.DATA));
        }
        expectedDataFree = ds.getFreeBytes(BlockArea.DATA);
        ds.close();

        DataStorage reopened = new DataStorage();
        reopened.open(file.toString());
        assertEquals(expectedDataFree, reopened.getFreeBytes(BlockArea.DATA));
        for (java.util.Map.Entry<Long, Integer> e : expectedRefs.entrySet())
            assertEquals(e.getValue().longValue(), reopened.getRefCount(e.getKey()));
        reopened.close();
    }

    @Test
    public void metadataReservationKeepsFreeSpaceAvailable() {
        DataStorage ds = new DataStorage();
        ds.format(file.toString(), 512, 1024, 512, 16 * 1024, 16 * 1024, 16 * 1024);
        long reserved = 8 * 1024;
        try (DataStorage.Reservation r = ds.reserveMeta(reserved)) {
            assertEquals(reserved, ds.getMetaReserved());
            for (int i = 0; i < 100; i++)
                ds.allocateBlock(BlockArea.META);
            long metaFree = ds.getFreeBytes(BlockArea.META) + ds.getFreeBytes(BlockArea.SYSTEM);
            assertTrue("reserved watermark must remain free, but meta free = " + metaFree
                    + " < reserved = " + reserved, metaFree >= reserved);
        }
        assertEquals(0, ds.getMetaReserved());
        ds.close();
    }

    private static byte[] fill(int size, int seed) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) seed);
        return data;
    }
}
