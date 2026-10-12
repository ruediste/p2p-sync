package com.github.ruediste.p2psync.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BlockSpaceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    Path path;

    AtomicSpace space;
    BlockSpace blockSpace;

    @Before
    public void before() throws IOException {
        path = tmp.newFolder().toPath().resolve("space.bin");
        space = new AtomicSpace();
        space.open(path);
        blockSpace = new BlockSpace(space, 0);
    }

    @After
    public void after() {
        space.close();
    }

    @Test
    public void initializeAndOpen() throws IOException {
        blockSpace.open(Optional.empty());
        assertEquals(32, blockSpace.getBlockCount());

        // reopen and check the metadata is persisted
        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        assertEquals(32, blockSpace.getBlockCount());
    }

    @Test
    public void wrongFixedSizeFails() throws IOException {
        blockSpace.open(Optional.empty());
        blockSpace = new BlockSpace(space, 100);
        try {
            blockSpace.open(Optional.empty());
            fail("expected exception");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void allocateAllBlocks() throws IOException {
        blockSpace.open(Optional.empty());
        long[] all = blockSpace.allocateBlocks((int) blockSpace.getBlockCount());
        for (long i = 0; i < blockSpace.getBlockCount(); i++) {
            assertEquals(i, all[(int) i]);
        }
        assertEquals(0, blockSpace.countFreeBlocks());
    }

    @Test
    public void growsWhenOutOfSpace() throws IOException {
        blockSpace.open(Optional.empty());
        long initialCount = blockSpace.getBlockCount();

        // allocating more than available grows the space
        long[] blocks = blockSpace.allocateBlocks((int) initialCount + 10);
        assertEquals(initialCount + 10, blocks.length);
        assertTrue(blockSpace.getBlockCount() >= initialCount + 10);
        assertEquals(blockSpace.getBlockCount() - initialCount - 10, blockSpace.countFreeBlocks());

        // all blocks are distinct
        assertEquals(initialCount + 10, Arrays.stream(blocks).distinct().count());

        // grow is persisted across reopen
        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        assertTrue(blockSpace.getBlockCount() >= initialCount + 10);
        assertEquals(blockSpace.getBlockCount() - initialCount - 10, blockSpace.countFreeBlocks());

        // freeing works on the grown space, and blocks are reusable
        blockSpace.freeBlocks(blocks);
        assertEquals(blockSpace.getBlockCount(), blockSpace.countFreeBlocks());
        long[] again = blockSpace.allocateBlocks((int) blockSpace.getBlockCount());
        Arrays.sort(again);
        for (long i = 0; i < blockSpace.getBlockCount(); i++) {
            assertEquals(i, again[(int) i]);
        }
    }

    @Test
    public void growIsReusedAfterReopen() throws IOException {
        blockSpace.open(Optional.empty());
        long[] allocated = blockSpace.allocateBlocks((int) blockSpace.getBlockCount() + 5);
        long grownCount = blockSpace.getBlockCount();

        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        assertEquals(grownCount, blockSpace.getBlockCount());

        blockSpace.freeBlocks(Arrays.copyOf(allocated, 3));
        assertEquals(grownCount - allocated.length + 3, blockSpace.countFreeBlocks());

        // allocating the freed blocks again must not grow further
        blockSpace.allocateBlocks(3);
        assertEquals(grownCount, blockSpace.getBlockCount());
    }

    @Test
    public void allocateFreeAndReallocate() throws IOException {
        blockSpace.open(Optional.empty());

        long[] a = blockSpace.allocateBlocks(5);
        long[] b = blockSpace.allocateBlocks(10);
        assertEquals(17, blockSpace.countFreeBlocks());

        blockSpace.freeBlocks(a);
        blockSpace.freeBlocks(b);
        assertEquals(32, blockSpace.countFreeBlocks());

        // all blocks should be allocatable again, each exactly once
        long[] all = blockSpace.allocateBlocks((int) blockSpace.getBlockCount());
        Arrays.sort(all);
        for (long i = 0; i < blockSpace.getBlockCount(); i++) {
            assertEquals(i, all[(int) i]);
        }
    }

    @Test
    public void allocatedBlocksAreDistinctAndWritable() throws IOException {
        blockSpace.open(Optional.empty());
        long[] blocks = blockSpace.allocateBlocks(20);
        assertEquals(20, Arrays.stream(blocks).distinct().count());

        // write and read back data in each block
        for (int i = 0; i < blocks.length; i++) {
            byte[] data = new byte[4096];
            new Random(i).nextBytes(data);
            blockSpace.writeBlock(blocks[i], data);
        }
        for (int i = 0; i < blocks.length; i++) {
            byte[] expected = new byte[4096];
            new Random(i).nextBytes(expected);
            assertArrayEquals(expected, blockSpace.readBlock(blocks[i]));
        }
    }

    @Test
    public void allocationSurvivesReopen() throws IOException {
        blockSpace.open(Optional.empty());
        long[] allocated = blockSpace.allocateBlocks(10);
        blockSpace.freeBlocks(Arrays.copyOf(allocated, 3));

        blockSpace = new BlockSpace(space, 0);
        blockSpace.open(Optional.empty());
        assertEquals(32 - 7, blockSpace.countFreeBlocks());

        long[] rest = blockSpace.allocateBlocks((int) (blockSpace.getBlockCount() - 7));
        // the freed blocks must be among the allocatable ones
        for (int i = 0; i < 3; i++) {
            final long nr = allocated[i];
            assertTrue(Arrays.stream(rest).anyMatch(x -> x == nr));
        }
    }

    @Test
    public void freeInvalidBlockFails() throws IOException {
        blockSpace.open(Optional.empty());
        try {
            blockSpace.freeBlocks(new long[] { blockSpace.getBlockCount() });
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            blockSpace.freeBlocks(new long[] { -1 });
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void emptyAllocateAndFree() throws IOException {
        blockSpace.open(Optional.empty());
        assertArrayEquals(new long[0], blockSpace.allocateBlocks(0));
        blockSpace.freeBlocks(new long[0]);
        assertEquals(32, blockSpace.countFreeBlocks());
    }
}