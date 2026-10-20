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
     * must stay ordered, unique, and recorded for free-tree removal across refills.
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
                    assertTrue(storage.pendingFreeTreeRemovals.get(area).contains(offset));
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
            assertEquals(1_056_000, storage.rootBlock.fileLength.value);
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
     * Allocates through the typed B-tree storage adapter. The block must use
     * the same pending-removal bookkeeping as other META allocations.
     */
    @Test
    void metaReservedAllocationIsRemovedFromOrdinaryPool() {
        long start = 16384;
        try (var storage = newStorage("meta-reserved.db", start + 4L * 16384)) {
            storage.freeSpaceTrees.put(DataStorage.Area.META,
                    tree(storage.blockSize(DataStorage.Area.META), Map.of(start, 4L * 16384)));

            var adapter = storage.new BTreeStorage(DataStorage.Area.META);
            var type = BTree.blockType(Long.BYTES, DataStorage.Area.META);
            long reserved = adapter.writeNewBlock(new BTree.NodeBlock(new byte[16384], type));

            assertTrue(storage.pendingFreeTreeRemovals.get(DataStorage.Area.META).contains(reserved));
            assertFalse(storage.freeBlocks.get(DataStorage.Area.META).contains(reserved));
            assertEquals(0, storage.refCountAdjustments.get(reserved).adjustment);
        }
    }

    /**
     * Verifies deferred-list sizing accounts for the list blocks' own entries,
     * including empty and exactly-full lists where an extra block was wasted.
     */
    @Test
    void deferredMetaListSizingDoesNotReserveUnusedBlocks() {
        assertEquals(0, DataStorage.requiredDeferredMetaListBlockCount(0, 0, 4));
        assertEquals(1, DataStorage.requiredDeferredMetaListBlockCount(3, 0, 4));
        assertEquals(1, DataStorage.requiredDeferredMetaListBlockCount(4, 1, 4));
        assertEquals(2, DataStorage.requiredDeferredMetaListBlockCount(4, 0, 4));
    }

    /**
     * Reads the committed deferred META-removal list and verifies every persisted
     * offset is queued for removal in the next in-memory transaction.
     */
    @Test
    void committedDeferredMetaRemovalsRemainPendingInMemory() {
        try (var storage = newPersistentStorage(tempDir.resolve("deferred-meta-removals.db"))) {
            long dataStart = storage.rootBlock.fileLength.value - 8L * 1024 * 1024;
            for (int index = 0; index < 1200; index++)
                storage.incrementRefCount(dataStart + index * (long) storage.blockSize(DataStorage.Area.DATA),
                        DataStorage.Area.DATA);
            storage.commit();

            var committedRoot = storage.readBlock(storage.superBlock.rootBlock,
                    DataStorage.Area.SYSTEM, DataStorage.RootBlock.TYPE);
            long listOffset = committedRoot.deferredMetaFreeTreeRemoveList.blockOffset;
            Set<Long> persistedRemovals = new HashSet<>();
            while (listOffset != 0) {
                ByteBuffer buffer = ByteBuffer.wrap(storage.readBlock(listOffset, DataStorage.Area.META));
                long next = buffer.getLong();
                long count = buffer.getLong();
                for (int index = 0; index < count; index++)
                    persistedRemovals.add(buffer.getLong());
                listOffset = next;
            }

            assertFalse(persistedRemovals.isEmpty());
            assertTrue(storage.pendingFreeTreeRemovals.get(DataStorage.Area.META).containsAll(persistedRemovals));
        }
    }

    /**
     * Removes pending DATA and META blocks from the middle of their extents. The
     * free ranges on both sides must remain after a shared removal pass.
     */
    @Test
    void removesPendingBlocksBySplittingTheirExtents() {
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
            storage.pendingFreeTreeRemovals.get(DataStorage.Area.DATA).add(dataAllocation);
            storage.pendingFreeTreeRemovals.get(DataStorage.Area.META).add(metaAllocation);

            storage.removePendingFreeTreeRemovals(true);

            assertEquals(2L * dataBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.DATA).get(u64(dataStart))));
            assertEquals(2L * dataBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.DATA).get(u64(dataAllocation + dataBlockSize))));
            assertEquals(2L * metaBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.META).get(u64(metaStart))));
            assertEquals(2L * metaBlockSize,
                    toLong(storage.freeSpaceTrees.get(DataStorage.Area.META).get(u64(metaAllocation + metaBlockSize))));

            assertTrue(storage.pendingFreeTreeRemovals.get(DataStorage.Area.DATA).isEmpty());
            assertTrue(storage.pendingFreeTreeRemovals.get(DataStorage.Area.META).isEmpty());
        }
    }

    /**
     * Models a META free-space extent that still spans a pending removal. Refill
     * must skip that offset so it cannot be reallocated before tree removal.
     */
    @Test
    void doesNotRefillOldReservedMetaBlocksIntoFreePool() {
        int blockSize = 16384;
        long start = 32768;
        long reserved = start + 2L * blockSize;
        try (var storage = newStorage("old-meta-reserved.db", start + 5L * blockSize)) {
            storage.freeSpaceTrees.put(DataStorage.Area.META,
                    tree(blockSize, Map.of(start, 5L * blockSize)));
            storage.pendingFreeTreeRemovals.get(DataStorage.Area.META).add(reserved);

            assertEquals(start, storage.takeFreeBlock(DataStorage.Area.META));
            assertEquals(start + blockSize, storage.takeFreeBlock(DataStorage.Area.META));
            // Skip the reserved block
            assertEquals(start + 3L * blockSize, storage.takeFreeBlock(DataStorage.Area.META));

            assertFalse(storage.freeBlocks.get(DataStorage.Area.META).contains(reserved));
        }
    }

    /**
     * Decrements a newly allocated block while its free-tree removal is in flight.
     * The block must be queued for reinsertion after the current batch completes.
     */
    @Test
    void putsBackBlockThatBecomesUnreferencedDuringRemoval() {
        long blockOffset = 32768;
        try (var storage = newStorage("in-flight-put-back.db", blockOffset + 16384)) {
            storage.superBlock.metaBlockSize = 16384;
            storage.modifiedBlocks.put(blockOffset,
                    new DataStorage.ModifiedBlock(new byte[16384], java.util.List.of()));
            storage.blocksBeingRemovedFromFreeTree.get(DataStorage.Area.META).add(blockOffset);
            var adjustment = DataStorage.RefCountAdjustment.create(DataStorage.Area.META);
            adjustment.equalsCount = true;
            adjustment.adjustment = 1;
            storage.refCountAdjustments.put(blockOffset, adjustment);

            storage.decrementRefCount(new BlockType.BlockRef(blockOffset, DataStorage.Area.META,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            assertTrue(storage.putBackBlocksToFreeTree.get(DataStorage.Area.META).contains(blockOffset));
            assertFalse(storage.freeBlocks.get(DataStorage.Area.META).contains(blockOffset));
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
            for (DataStorage.Area area : DataStorage.Area.values()) {
                offsets[area.ordinal()] = storage.writeNewBlock(area, new byte[storage.blockSize(area)]);
                storage.incrementRefCount(offsets[area.ordinal()], area);
            }
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
     * Verifies that each formatted free-space-tree root is owned by both the
     * committed root block and the in-memory working transaction.
     */
    @Test
    void formattedRootOwnsItsFreeSpaceTreeRoots() {
        try (var storage = newPersistentStorage(tempDir.resolve("root-tree-refs.db"))) {
            assertEffectiveRefCount(storage, storage.rootBlock.dataFreeSpaceTree.blockOffset, 1);
            assertEffectiveRefCount(storage, storage.rootBlock.metaFreeSpaceTree.blockOffset, 1);
            assertEffectiveRefCount(storage, storage.rootBlock.systemFreeSpaceTree.blockOffset, 1);
        }
    }

    /**
     * Drops a typed META block through its persisted block type, reopens the
     * storage, and verifies the zero refcount survives recovery.
     */
    @Test
    void typedDeleteHandlerIsAppliedWhenRefCountReachesZero() {
        Path path = tempDir.resolve("deferred-ref-count-action.db");
        long blockOffset;
        try (var storage = newPersistentStorage(path)) {
            blockOffset = storage.writeNewBlock(DataStorage.Area.META,
                    new byte[storage.blockSize(DataStorage.Area.META)]);
            storage.incrementRefCount(blockOffset, DataStorage.Area.META);
            storage.commit();
            assertEquals(1, storage.getRefCount(blockOffset));

            storage.decrementRefCount(new BlockType.BlockRef(blockOffset, DataStorage.Area.META,
                    BTree.blockType(Long.BYTES, DataStorage.Area.META)));
            storage.commit();

            assertEquals(0, storage.getRefCount(blockOffset));
        }

        try (var reopened = new DataStorage()) {
            reopened.open(path.toString());
            reopened.commit();

            assertEquals(0, reopened.getRefCount(blockOffset));
        }
    }

    /**
     * Applies a large batch of ref-count updates. After reopening, the first
     * and last counts must both be present.
     */
    @Test
    void refCountUpdatesRoundTripAcrossLargeBatch() {
        Path path = tempDir.resolve("deferred-ref-count-rollover.db");
        int adjustmentCount;
        long firstBlock;
        long lastBlock;
        try (var storage = newPersistentStorage(path)) {
            adjustmentCount = 1200;
            long dataStart = storage.rootBlock.fileLength.value - 8L * 1024 * 1024;
            firstBlock = dataStart;
            lastBlock = dataStart + (adjustmentCount - 1L) * storage.blockSize(DataStorage.Area.DATA);

            for (int index = 0; index < adjustmentCount; index++)
                storage.incrementRefCount(dataStart + index * (long) storage.blockSize(DataStorage.Area.DATA),
                        DataStorage.Area.DATA);

            storage.commit();

            assertEquals(1, storage.getRefCount(firstBlock));
            assertEquals(1, storage.getRefCount(lastBlock));
        }

        try (var reopened = new DataStorage()) {
            reopened.open(path.toString());

            assertEquals(1, reopened.getRefCount(firstBlock));
            assertEquals(1, reopened.getRefCount(lastBlock));
        }
    }

    /**
     * Creates a persisted adjustment-list self-cycle and verifies that opening
     * the file rejects the malformed chain.
     */
    @Test
    void rejectsCyclicDeferredRefCountAdjustmentList() throws IOException {
        Path path = tempDir.resolve("deferred-ref-count-cycle.db");
        long listHead;
        try (var storage = newPersistentStorage(path)) {
            listHead = storage.writeNewBlock(DataStorage.Area.META,
                    new byte[storage.blockSize(DataStorage.Area.META)]);
            storage.incrementRefCount(listHead, DataStorage.Area.META);
            storage.commit();

            long rootOffset = storage.superBlock.rootBlock;
            byte[] rootBytes = storage.readBlock(rootOffset, DataStorage.Area.SYSTEM);
            ByteBuffer.wrap(rootBytes).putLong(19 * Long.BYTES, listHead);
            storage.file.seek(listHead);
            storage.file.writeLong(listHead);
            storage.file.seek(rootOffset);
            storage.file.write(rootBytes);
        }

        try (var reopened = new DataStorage()) {
            var exception = assertThrows(IllegalStateException.class, () -> reopened.open(path.toString()));
            assertEquals("Cycle in deferred ref-count adjustment list", exception.getMessage());
        }
    }

    private static void assertEffectiveRefCount(DataStorage storage, long blockOffset, int expected) {
        var adjustment = storage.refCountAdjustments.get(blockOffset);
        long pending = adjustment == null ? 0 : adjustment.adjustment;
        assertEquals(expected, storage.getRefCount(blockOffset) + pending);
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
            for (int i = 0; i < blockCount; i++) {
                offsets[i] = storage.writeNewBlock(DataStorage.Area.DATA,
                        new byte[storage.blockSize(DataStorage.Area.DATA)]);
                storage.incrementRefCount(offsets[i], DataStorage.Area.DATA);
            }
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
        storage.rootBlock.fileLength.value = fileLength;
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
        public DataStorage.Area area() {
            return DataStorage.Area.META;
        }

        @Override
        public BTree.NodeBlock readBlock(long blockOffset, BlockType<BTree.NodeBlock> type) {
            return new BTree.NodeBlock(blocks.get(blockOffset), type);
        }

        @Override
        public boolean isBlockModified(long blockOffset) {
            return modified.contains(blockOffset);
        }

        @Override
        public void writeBlock(long blockOffset, BTree.NodeBlock data) {
            blocks.put(blockOffset, data.data.clone());
            modified.add(blockOffset);
        }

        @Override
        public long writeNewBlock(BTree.NodeBlock data) {
            long offset = nextOffset++;
            blocks.put(offset, data.data.clone());
            modified.add(offset);
            return offset;
        }

        @Override
        public boolean prepareFreeSpace(int blockCount) {
            return true;
        }
    }
}