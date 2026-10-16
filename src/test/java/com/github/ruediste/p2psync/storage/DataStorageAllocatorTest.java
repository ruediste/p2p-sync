package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStorageAllocatorTest {

    @TempDir
    Path tempDir;

    /**
     * Allocates more than one refill batch in each area. The returned offsets
     * must stay ordered, unique, and recorded as allocated across refills.
     */
    @Test
    void allocatesAcrossBatchesWithoutDuplicatesInEveryArea() {
        for (DataStorage.Area area : DataStorage.Area.values()) {
            int blockSize = blockSize(area);
            long start = blockSize * 2L;
            try (var storage = newStorage("batch-" + area + ".db", start + 512L * blockSize)) {
                storage.freeSpaceTrees.put(area,
                        tree(blockSize, Map.of(start, 512L * blockSize)));

                Set<Long> allocated = new HashSet<>();
                for (int index = 0; index < 300; index++) {
                    long offset = storage.takeFreeBlock(area);
                    assertEquals(start + index * (long) blockSize, offset);
                    assertTrue(allocated.add(offset));
                    assertTrue(storage.allocatedBlocks.get(area).contains(offset));
                }
            }
        }
    }

    /**
     * Starts with two separated free extents. After exhausting the first, the
     * cursor must continue at the next extent without skipping or repeating blocks.
     */
    @Test
    void continuesFromCursorAcrossDisjointExtents() {
        int blockSize = 4096;
        long firstStart = 8192;
        long secondStart = 32768;
        try (var storage = newStorage("disjoint.db", secondStart + 3L * blockSize)) {
            storage.freeSpaceTrees.put(DataStorage.Area.DATA,
                    tree(blockSize, Map.of(firstStart, 2L * blockSize, secondStart, 3L * blockSize)));

            assertEquals(firstStart, storage.takeFreeBlock(DataStorage.Area.DATA));
            assertEquals(firstStart + blockSize, storage.takeFreeBlock(DataStorage.Area.DATA));
            assertEquals(secondStart, storage.takeFreeBlock(DataStorage.Area.DATA));
            assertEquals(secondStart + blockSize, storage.takeFreeBlock(DataStorage.Area.DATA));
            assertEquals(secondStart + 2L * blockSize, storage.takeFreeBlock(DataStorage.Area.DATA));
        }
    }

    /**
     * Starts with an empty free tree and a file end between DATA block
     * boundaries. Extension must align its start and growth before allocating.
     */
    @Test
    void extendsOnAlignedBoundaryWhenTreeHasNoFreeExtents() {
        DataStorage storage = newStorage("extension.db", 5000);
        storage.superBlock.dataBlockSize = 3000;
        storage.freeSpaceTrees.put(DataStorage.Area.DATA, tree(3000, Map.of()));

        try (storage) {
            long allocated = storage.takeFreeBlock(DataStorage.Area.DATA);

            assertEquals(6000, allocated);
            assertEquals(1_056_000, fileLength(storage));
            assertEquals(1_056_000, storage.rootBlock.fileLength);
        }
    }

    /**
     * Supplies an extent whose byte length is not a multiple of its area's block
     * size. Allocation must reject corrupted free-space metadata.
     */
    @Test
    void rejectsMisalignedExtents() {
        try (var storage = newStorage("misaligned.db", 16384)) {
            storage.freeSpaceTrees.put(DataStorage.Area.DATA,
                    tree(4096, Map.of(8192L, 4097L)));

            assertThrows(IllegalStateException.class, () -> storage.takeFreeBlock(DataStorage.Area.DATA));
        }
    }

    /**
     * Allocates through the META-reserved B-tree storage adapter. The block must
     * be reserved for allocator metadata, not exposed as an ordinary allocation.
     */
    @Test
    void metaReservedAllocationIsRemovedFromOrdinaryPool() {
        long start = 16384;
        try (var storage = newStorage("meta-reserved.db", start + 4L * 16384)) {
            storage.freeSpaceTrees.put(DataStorage.Area.META,
                    tree(storage.blockSize(DataStorage.Area.META), Map.of(start, 4L * 16384)));

            long reserved = storage.new BTreeStorage(DataStorage.Area.META).deferred()
                    .writeNewBlock(new byte[16384]);

            assertTrue(storage.deferredMetaFreeTreeRemoveBlocks.contains(reserved));
            assertFalse(storage.allocatedBlocks.get(DataStorage.Area.META).contains(reserved));
            assertFalse(storage.freeBlocks.get(DataStorage.Area.META).contains(reserved));
            assertEquals(1, storage.refCountAdjustments.get(reserved).adjustment());
        }
    }

    /**
     * Removes one normal DATA allocation and one META reservation from the middle
     * of their extents. The free ranges on both sides must remain, and a later
     * pass with the reservation marked old must not remove it again.
     */
    @Test
    void removesAllocatedBlocksBySplittingTheirExtents() {
        int dataBlockSize = 4096;
        int metaBlockSize = 16384;
        long dataStart = 8192;
        long metaStart = 32768;
        long dataAllocation = dataStart + 2L * dataBlockSize;
        long metaAllocation = metaStart + 2L * metaBlockSize;
        long fileLength = metaStart + 5L * metaBlockSize;

        try (var storage = newStorage("remove-allocated.db", fileLength)) {
            storage.rootBlock = new DataStorage.RootBlock();
            storage.freeSpaceTrees.put(DataStorage.Area.SYSTEM, tree(4096, Map.of()));
            storage.freeSpaceTrees.put(DataStorage.Area.DATA,
                    tree(dataBlockSize, Map.of(dataStart, 5L * dataBlockSize)));
            storage.freeSpaceTrees.put(DataStorage.Area.META,
                    tree(metaBlockSize, Map.of(metaStart, 5L * metaBlockSize)));
            storage.allocatedBlocks.get(DataStorage.Area.DATA).add(dataAllocation);
            storage.deferredMetaFreeTreeRemoveBlocks.add(metaAllocation);

            storage.removeAllocatedBlocksFromFreeTrees();
            storage.removeDeferredMetaBlocksFromFreeTree();

            assertEquals(2L * dataBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.DATA).get(u64(dataStart))));
            assertEquals(2L * dataBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.DATA).get(u64(dataAllocation + dataBlockSize))));
            assertEquals(2L * metaBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.META).get(u64(metaStart))));
            assertEquals(2L * metaBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.META).get(u64(metaAllocation + metaBlockSize))));

            storage.allocatedBlocks.get(DataStorage.Area.DATA).clear();
            storage.oldDeferredMetaFreeTreeRemoveBlocks.add(metaAllocation);
            storage.removeAllocatedBlocksFromFreeTrees();
        }
    }

    /**
     * Models a META free-space extent that still spans an old reserved block.
     * Refill must skip that offset so a normal allocation cannot reuse it while
     * the allocator is removing reserved blocks from the persistent tree.
     */
    @Test
    void doesNotRefillOldReservedMetaBlocksIntoFreePool() {
        int blockSize = 16384;
        long start = 32768;
        long reserved = start + 2L * blockSize;
        try (var storage = newStorage("old-meta-reserved.db", start + 5L * blockSize)) {
            storage.freeSpaceTrees.put(DataStorage.Area.META,
                    tree(blockSize, Map.of(start, 5L * blockSize)));
            storage.deferredMetaFreeTreeRemoveBlocks.add(reserved);
            storage.oldDeferredMetaFreeTreeRemoveBlocks.add(reserved);

            assertEquals(start, storage.takeFreeBlock(DataStorage.Area.META));
            assertEquals(start + blockSize, storage.takeFreeBlock(DataStorage.Area.META));
            // Skip the reserved block
            assertEquals(start + 3L * blockSize, storage.takeFreeBlock(DataStorage.Area.META));

            assertFalse(storage.freeBlocks.get(DataStorage.Area.META).contains(reserved));
        }
    }

    /**
     * Frees one block per area, commits, and reopens the file. Verifies each
     * deferred-free chain restores its blocks to the matching free-space tree.
     */
    @Test
    void deferredFreeListsRoundTripForEveryArea() {
        Path path = tempDir.resolve("deferred-free-areas.db");
        long[] offsets = new long[DataStorage.Area.values().length];
        try (var storage = newPersistentStorage(path)) {
            for (DataStorage.Area area : DataStorage.Area.values())
                offsets[area.ordinal()] = storage.writeNewBlock(area, new byte[storage.blockSize(area)]);
            storage.commit();

            for (DataStorage.Area area : DataStorage.Area.values()) {
                storage.addToDeferredFreeBlocks(offsets[area.ordinal()], area);
                assertFalse(containsBlock(storage.freeSpaceTrees.get(area), offsets[area.ordinal()]));
            }
            storage.commit();
        }

        try (var reopened = new DataStorage()) {
            reopened.open(path.toString());
            for (DataStorage.Area area : DataStorage.Area.values())
                assertTrue(containsBlock(reopened.freeSpaceTrees.get(area), offsets[area.ordinal()]));
        }
    }

    /**
     * Defers more offsets than fit in one SYSTEM list block, then reopens the
     * file. Verifies both ends of the linked list are reclaimed into DATA space.
     */
    @Test
    void deferredFreeListRollsOverAcrossSystemBlocks() {
        Path path = tempDir.resolve("deferred-free-rollover.db");
        int blockCount = 31;
        long[] offsets = new long[blockCount];
        try (var storage = newPersistentStorage(path, 256)) {
            for (int i = 0; i < blockCount; i++)
                offsets[i] = storage.takeFreeBlock(DataStorage.Area.DATA);
            storage.commit();

            for (long offset : offsets)
                storage.addToDeferredFreeBlocks(offset, DataStorage.Area.DATA);
            storage.commit();
        }

        try (var reopened = new DataStorage()) {
            reopened.open(path.toString());
            BTree tree = reopened.freeSpaceTrees.get(DataStorage.Area.DATA);
            assertTrue(containsBlock(tree, offsets[0]));
            assertTrue(containsBlock(tree, offsets[offsets.length - 1]));
        }
    }

    private DataStorage newStorage(String fileName, long fileLength) {
        DataStorage storage = new DataStorage();
        storage.superBlock = new DataStorage.SuperBlock();
        storage.superBlock.systemBlockSize = 4096;
        storage.superBlock.metaBlockSize = 16384;
        storage.superBlock.dataBlockSize = 4096;
        storage.rootBlock = new DataStorage.RootBlock();
        storage.file = openFile(tempDir.resolve(fileName), fileLength);
        return storage;
    }

    private DataStorage newPersistentStorage(Path path) {
        return newPersistentStorage(path, 4096);
    }

    private DataStorage newPersistentStorage(Path path, int systemBlockSize) {
        DataStorage storage = new DataStorage();
        storage.format(path.toString(), 512 * 1024, 8 * 1024 * 1024, 8 * 1024 * 1024,
                4096, 16384, systemBlockSize);
        return storage;
    }

    private static boolean containsBlock(BTree tree, long offset) {
        var iterator = tree.floorOrLowestIterator(u64(offset));
        if (!iterator.hasNext())
            return false;
        var extent = iterator.next();
        long start = toLong(extent.key);
        long end = start + toLong(extent.value);
        return offset >= start && offset < end;
    }

    private static int blockSize(DataStorage.Area area) {
        return switch (area) {
            case SYSTEM -> 4096;
            case META -> 16384;
            case DATA -> 4096;
        };
    }

    private static BTree tree(int blockSize, Map<Long, Long> extents) {
        BTree tree = new BTree(new MemoryTreeStorage(blockSize), Long.BYTES, null);
        extents.forEach((offset, size) -> tree.put(u64(offset), u64(size)));
        return tree;
    }

    private static byte[] u64(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static long toLong(byte[] value) {
        return ByteBuffer.wrap(value).getLong();
    }

    private RandomAccessFile openFile(Path path, long length) {
        try {
            RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
            file.setLength(length);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long fileLength(DataStorage storage) {
        try {
            return storage.file.length();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class MemoryTreeStorage implements BTree.Storage {
        private final int blockSize;
        private final Map<Long, byte[]> blocks = new HashMap<>();
        private final Set<Long> modified = new HashSet<>();
        private long nextOffset = 1;

        private MemoryTreeStorage(int blockSize) {
            this.blockSize = blockSize;
        }

        @Override
        public int blockSize() {
            return blockSize;
        }

        @Override
        public byte[] readBlock(long blockOffset) {
            return blocks.get(blockOffset).clone();
        }

        @Override
        public boolean isBlockModified(long blockOffset) {
            return modified.contains(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, byte[] data) {
            blocks.put(blockOffset, data.clone());
            modified.add(blockOffset);
        }

        @Override
        public long writeNewBlock(byte[] data) {
            long offset = nextOffset++;
            blocks.put(offset, data.clone());
            modified.add(offset);
            return offset;
        }

        @Override
        public void incrementRefCount(long blockOffset) {
        }

        @Override
        public void decrementRefCount(long blockOffset, java.util.function.Consumer<Long> onDelete) {
        }
    }
}