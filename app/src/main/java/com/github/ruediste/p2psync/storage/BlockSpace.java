package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * BlockSpace manages a block-based storage on top of an AtomicSpace.
 * It maintains a free list for efficient allocation and deallocation of blocks.
 */
public class BlockSpace {
    /*
     * Header (padded to 4KB)
     * - Magic number (16 bytes)
     * - Fixed area size (8 bytes)
     * - Block count (8 bytes)
     * - Free list head block nr(8 bytes)
     * 
     * Free List Entry
     * - Next free block nr (8 bytes) (-1 for end of list)
     * - Free block count (8 bytes) (includes this block)
     */

    private static final byte[] MAGIC_NUMBER = new byte[] {
            (byte) 0x8a, (byte) 0xbd, (byte) 0x82, (byte) 0x61,
            (byte) 0x6d, (byte) 0xff, (byte) 0xc7, (byte) 0x00,
            (byte) 0x3c, (byte) 0x19, (byte) 0x3b, (byte) 0x1e,
            (byte) 0x7e, (byte) 0xd3, (byte) 0x44, (byte) 0x4b
    };

    public static final int BLOCK_SIZE = 4096;
    private static final int HEADER_SIZE = MAGIC_NUMBER.length + 8 + 8 + 8;
    private static final int HEADER_BLOCK_COUNT_POSITION = MAGIC_NUMBER.length + 8;
    private static final int HEADER_FREE_LIST_HEAD_POSITION = MAGIC_NUMBER.length + 8 + 8;

    private static final int FREE_LIST_ENTRY_SIZE = 8 + 8; // next free block nr + free block count
    private static final long END_OF_LIST = -1;

    private AtomicSpace space;
    private long fixedAreaSize;
    private long blockCount;
    private long blockOffset;
    private long freeListHead;

    public BlockSpace(AtomicSpace space, long fixedAreaSize) {
        this.space = space;
        this.fixedAreaSize = fixedAreaSize;
    }

    /**
     * Opens the block space for operations. The atomic space has
     * to be opened before calling this method.
     */
    public void open(Optional<Runnable> onInitialize) {
        blockOffset = BLOCK_SIZE + fixedAreaSize;

        // read and verify the magic number
        var header = space.readBuffer(0, HEADER_SIZE);
        byte[] magic = new byte[MAGIC_NUMBER.length];
        header.get(0, magic);
        if (Arrays.equals(magic, MAGIC_NUMBER)) {
            // load existing block space metadata
            header.position(MAGIC_NUMBER.length);
            if (fixedAreaSize != header.getLong()) {
                throw new IllegalStateException("Fixed area size does not match the header value");
            }
            blockCount = header.getLong();
            freeListHead = header.getLong();
        } else {
            // initialize new block space
            blockCount = 32;
            freeListHead = 0; // initialize free list head to the first block

            space.resize(BLOCK_SIZE + fixedAreaSize + BLOCK_SIZE * blockCount);
            header.position(0);
            header.put(MAGIC_NUMBER);
            header.putLong(fixedAreaSize);
            header.putLong(blockCount);
            header.putLong(freeListHead); // free list head block nr
            space.write(header.array(), 0);

            // initialize head of the free list
            var freeListEntry = ByteBuffer.allocate(FREE_LIST_ENTRY_SIZE);
            freeListEntry.putLong(END_OF_LIST); // next free block nr
            freeListEntry.putLong(blockCount); // free block count
            space.write(freeListEntry.array(), blockPosition(freeListHead));

            onInitialize.ifPresent(Runnable::run);

            space.commit(); // commit the initial block space setup
        }
    }

    /**
     * Allocates a number of blocks from the free list.
     * Returns the block numbers of the allocated blocks.
     * Grows the space automatically if there are not enough free blocks.
     */
    public long[] allocateBlocks(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        long[] result = new long[count];
        int allocated = 0;

        boolean headChanged = false;

        while (allocated < count) {
            long needed = count - allocated;
            if (freeListHead == END_OF_LIST) {
                grow(needed);
            }

            var entry = space.readBuffer(blockPosition(freeListHead), FREE_LIST_ENTRY_SIZE);
            long next = entry.getLong();
            long runCount = entry.getLong();
            if (runCount <= 0) {
                throw new IllegalStateException("Corrupt free list entry at block " + freeListHead);
            }

            if (runCount <= needed) {
                // take the whole run and remove the entry from the list
                for (long i = 0; i < runCount; i++) {
                    result[allocated++] = freeListHead + i;
                }
                freeListHead = next;
                headChanged = true;
            } else {
                // split the run: allocate from the end, keep the entry at the
                // head of the run
                long newCount = runCount - needed;
                for (long i = 0; i < needed; i++) {
                    result[allocated++] = freeListHead + newCount + i;
                }

                writeFreeListEntry(freeListHead, next, newCount);
                allocated = count;
            }
        }

        if (headChanged) {
            persistFreeListHead();
        }
        return result;
    }

    /**
     * Grows the block space so that at least the given number of additional
     * blocks is available.
     */
    private void grow(long additionalBlocks) {
        long oldBlockCount = blockCount;
        blockCount = blockCount + additionalBlocks + 100;

        space.resize(BLOCK_SIZE + fixedAreaSize + BLOCK_SIZE * blockCount);

        // add the new blocks to the free list as a single run
        writeFreeListEntry(oldBlockCount, freeListHead, blockCount - oldBlockCount);
        freeListHead = oldBlockCount;
        persistFreeListHead();

        // persist the new block count in the header
        var buf = ByteBuffer.allocate(8);
        buf.putLong(blockCount);
        space.write(buf.array(), HEADER_BLOCK_COUNT_POSITION);
    }

    /**
     * Frees the specified blocks and adds them back to the free list.
     * Consecutive blocks are coalesced into single free list entries.
     */
    public void freeBlocks(long[] blockNrs) {
        if (blockNrs.length == 0) {
            return;
        }

        long[] sorted = blockNrs.clone();
        Arrays.sort(sorted);
        for (long blockNr : sorted) {
            if (blockNr < 0 || blockNr >= blockCount) {
                throw new IllegalArgumentException("Block number out of range: " + blockNr);
            }
        }

        // prepend runs of consecutive blocks to the free list, from the last
        // run to the first so the entries end up in ascending order
        int start = sorted.length;
        while (start > 0) {
            int end = start;
            start--;
            while (start > 0 && sorted[start] == sorted[start - 1] + 1) {
                start--;
            }
            // run is sorted[start .. end)
            long runStart = sorted[start];
            long runLength = end - start;
            writeFreeListEntry(runStart, freeListHead, runLength);
            freeListHead = runStart;
        }
        persistFreeListHead();
    }

    private void writeFreeListEntry(long blockNr, long next, long count) {
        var entry = ByteBuffer.allocate(FREE_LIST_ENTRY_SIZE);
        entry.putLong(next);
        entry.putLong(count);
        space.write(entry.array(), blockPosition(blockNr));
    }

    private void persistFreeListHead() {
        var buf = ByteBuffer.allocate(8);
        buf.putLong(freeListHead);
        space.write(buf.array(), HEADER_FREE_LIST_HEAD_POSITION);
    }

    public long blockPosition(long blockNr) {
        return blockOffset + blockNr * BLOCK_SIZE;
    }

    public long getBlockCount() {
        return blockCount;
    }

    public int getBlockSize() {
        return BLOCK_SIZE;
    }

    public void commit() {
        space.commit();
    }

    public void rollback() {
        space.rollback();
    }

    /**
     * Writes the given data to the specified block. The data must be exactly
     * 4096 bytes long.
     */
    public void writeBlock(long blockNr, byte[] data) {
        if (data.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("Data must be exactly " + BLOCK_SIZE + " bytes");
        }
        space.write(data, blockPosition(blockNr));
    }

    public byte[] readBlock(long blockNr) {
        return space.read(blockPosition(blockNr), BLOCK_SIZE);
    }

    /**
     * Counts the free blocks by walking the free list.
     */
    public long countFreeBlocks() {
        long count = 0;
        long cur = freeListHead;
        while (cur != END_OF_LIST) {
            var entry = space.readBuffer(blockPosition(cur), FREE_LIST_ENTRY_SIZE);
            cur = entry.getLong();
            count += entry.getLong();
        }
        return count;
    }
}
