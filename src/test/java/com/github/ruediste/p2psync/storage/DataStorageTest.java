package com.github.ruediste.p2psync.storage;

import static com.github.ruediste.p2psync.storage.DataStorageTestUtils.containsBlock;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test suite for DataStorage verifying Copy-on-Write (CoW) transactional
 * semantics,
 * atomic superblock commits, deferred free space reclamation, and crash
 * rollback behavior.
 */
public class DataStorageTest {

    @TempDir
    Path tempDir;

    private String dbPath;
    private DataStorage storage;

    private static class ReferenceBlock implements BlockType.BlockInstance<ReferenceBlock> {
        static final BlockType<ReferenceBlock> TYPE = new BlockType<>() {
            @Override
            public java.util.List<BlockType.BlockRef> getReferencedBlocks(ReferenceBlock value,
                    DataStorage storage) {
                if (value.childOffset == 0)
                    return java.util.List.of();
                return java.util.List.of(new BlockType.BlockRef(value.childOffset, DataStorage.Area.DATA, this));
            }

            @Override
            public java.nio.ByteBuffer serialize(ReferenceBlock value) {
                return java.nio.ByteBuffer.allocate(Long.BYTES).putLong(value.childOffset).flip();
            }

            @Override
            public ReferenceBlock deserialize(java.nio.ByteBuffer buffer) {
                return new ReferenceBlock(buffer.getLong());
            }
        };

        long childOffset;

        ReferenceBlock(long childOffset) {
            this.childOffset = childOffset;
        }

        @Override
        public BlockType<ReferenceBlock> getBlockType() {
            return TYPE;
        }
    }

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test-storage.db").toString();
        storage = new DataStorage();
        storage.open(dbPath);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    /**
     * Verifies allocation starts unreferenced and writing a parent adds exactly
     * one child reference, including direct writes.
     */
    @Test
    void allocationsAreReferencedOnlyByTheirParents() {
        long child = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        assertEquals(0, storage.refCountUpdates.get(child).count);

        long parent = storage.writeNewBlockDirect(DataStorage.Area.DATA, new ReferenceBlock(child));
        assertEquals(1, storage.refCountUpdates.get(child).count);
        assertEquals(0, storage.refCountUpdates.get(parent).count);
    }

    /**
     * Uses non-tree blocks to verify child-mode selection sees pending counts,
     * SHARE preserves modified sources, and TRANSFER reuses only singly owned
     * blocks.
     */
    @Test
    void genericBlockReplacementUsesLiveOwnership() {
        var area = DataStorage.Area.DATA;
        long offset = storage.writeNewBlock(area, new ReferenceBlock(0), RefMode.TRANSFER);
        var ref = new BlockType.BlockRef(offset, area, ReferenceBlock.TYPE);
        storage.incrementRefCount(ref);

        assertEquals(1, storage.getRefCount(offset));
        assertEquals(RefMode.TRANSFER, storage.childRefMode(offset, RefMode.TRANSFER));
        assertEquals(RefMode.SHARE, storage.childRefMode(offset, RefMode.SHARE));
        assertEquals(RefMode.EXCLUSIVE, storage.childRefMode(offset, RefMode.EXCLUSIVE));
        assertEquals(offset, storage.mutate(
                () -> storage.replaceBlock(offset, area, new ReferenceBlock(0), RefMode.TRANSFER)).longValue());

        long child = storage.writeNewBlock(area, new ReferenceBlock(0));
        long shared = storage.mutate(
                () -> storage.replaceBlock(offset, area, new ReferenceBlock(child), RefMode.SHARE));
        assertNotEquals(offset, shared);
        assertEquals(0, storage.readBlock(offset, area, ReferenceBlock.TYPE).childOffset);
        assertEquals(child, storage.readBlock(shared, area, ReferenceBlock.TYPE).childOffset);
        assertEquals(1, storage.getRefCount(child));

        storage.incrementRefCount(ref);
        assertEquals(0, storage.getRefPersistedCount(offset));
        assertEquals(2, storage.getRefCount(offset));
        assertEquals(RefMode.SHARE, storage.childRefMode(offset, RefMode.TRANSFER));
        long transferred = storage.mutate(
                () -> storage.replaceBlock(offset, area, new ReferenceBlock(0), RefMode.TRANSFER));
        assertNotEquals(offset, transferred);
    }

    /**
     * Retires non-tree blocks in different areas inside nested scopes. Neither
     * block may return to its allocation pool until the outer scope publishes.
     */
    @Test
    void nestedGenericMutationsDeferRetirementAcrossAreas() {
        long data = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0), RefMode.EXCLUSIVE);
        long system = storage.writeNewBlock(DataStorage.Area.SYSTEM, new ReferenceBlock(0), RefMode.EXCLUSIVE);
        assertTrue(!storage.refCountUpdates.containsKey(data));
        assertTrue(!storage.refCountUpdates.containsKey(system));
        assertEquals(data, storage.mutate(() -> storage.replaceBlock(data, DataStorage.Area.DATA,
                new ReferenceBlock(0), RefMode.EXCLUSIVE)).longValue());

        storage.mutate(() -> {
            storage.retireExclusiveBlock(data, DataStorage.Area.DATA);
            storage.mutate(() -> {
                storage.retireExclusiveBlock(system, DataStorage.Area.SYSTEM);
                return null;
            });
            assertTrue(storage.modifiedBlocks.containsKey(data));
            assertTrue(storage.modifiedBlocks.containsKey(system));
            assertTrue(!storage.freeBlocks.get(DataStorage.Area.DATA).contains(data));
            assertTrue(!storage.freeBlocks.get(DataStorage.Area.SYSTEM).contains(system));
            return null;
        });

        assertTrue(!storage.modifiedBlocks.containsKey(data));
        assertTrue(!storage.modifiedBlocks.containsKey(system));
        assertTrue(storage.freeBlocks.get(DataStorage.Area.DATA).contains(data));
        assertTrue(storage.freeBlocks.get(DataStorage.Area.SYSTEM).contains(system));
    }

    /**
     * Replaces a committed non-tree exclusive block and verifies its old bytes
     * remain readable until commit and its storage is reclaimed afterwards.
     */
    @Test
    void genericExclusiveReplacementDefersCommittedBlockReuse() {
        var area = DataStorage.Area.DATA;
        long original = storage.writeNewBlock(area, new ReferenceBlock(0), RefMode.EXCLUSIVE);
        storage.commit();

        long replacement = storage.mutate(
                () -> storage.replaceBlock(original, area, new ReferenceBlock(0), RefMode.EXCLUSIVE));
        assertNotEquals(original, replacement);
        assertTrue(!containsBlock(storage.freeSpaceTrees.get(area), original));
        assertEquals(0, storage.readBlock(original, area, ReferenceBlock.TYPE).childOffset);
        assertTrue(!storage.refCountUpdates.containsKey(replacement));
        storage.commit();
        assertTrue(containsBlock(storage.freeSpaceTrees.get(area), original));
        storage.open(dbPath);
        assertEquals(0, storage.readBlock(replacement, area, ReferenceBlock.TYPE).childOffset);
    }

    /**
     * Updates an existing block in separate transactions and verifies pending
     * counts start from the tree value and survive commit and reopen.
     */
    @Test
    void existingBlockUpdatesStoreAbsoluteCounts() {
        long offset = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        var ref = new BlockType.BlockRef(offset, DataStorage.Area.DATA, ReferenceBlock.TYPE);
        storage.incrementRefCount(ref);
        storage.incrementRefCount(ref);
        storage.commit();

        storage.incrementRefCount(ref);
        assertEquals(3, storage.refCountUpdates.get(offset).count);
        assertEquals(2, storage.refCountUpdates.get(offset).initialCount);
        storage.decrementRefCount(ref);
        assertTrue(!storage.refCountUpdates.containsKey(offset));
        assertEquals(2, storage.getRefPersistedCount(offset));
        storage.commit();

        storage.decrementRefCount(ref);
        assertEquals(1, storage.refCountUpdates.get(offset).count);
        storage.commit();
        storage.close();
        storage = new DataStorage();
        storage.open(dbPath);
        assertEquals(1, storage.getRefPersistedCount(offset));

        storage.decrementRefCount(ref);
        assertEquals(0, storage.refCountUpdates.get(offset).count);
        storage.commit();
        assertEquals(0, storage.getRefPersistedCount(offset));
    }

    /**
     * Models a committed block whose live count is still deferred rather than in
     * the tree, then verifies releasing its last reference schedules reclamation.
     */
    @Test
    void committedDeferredCountReachingZeroIsReleased() {
        long offset = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        var ref = new BlockType.BlockRef(offset, DataStorage.Area.DATA, ReferenceBlock.TYPE);
        storage.incrementRefCount(ref);
        storage.commit();

        storage.refCountTree.remove(offset);
        var update = storage.refCountUpdates.getOrCreate(offset, DataStorage.Area.DATA);
        update.count = 1;
        assertEquals(0, update.initialCount);

        storage.decrementRefCount(ref);
        assertNotNull(storage.refCountUpdates.get(offset));
        assertEquals(0, storage.refCountUpdates.get(offset).count);
        storage.commit();

        assertEquals(0, storage.getRefPersistedCount(offset));
        assertTrue(containsBlock(storage.freeSpaceTrees.get(DataStorage.Area.DATA), offset));
    }

    /**
     * Counts tree reads while retaining and releasing an existing block to verify
     * pending updates reuse their initial count and cancel without another lookup.
     */
    @Test
    void refCountUpdatesReadInitialCountOnlyOnce() {
        int[] reads = { 0 };
        try (var countingStorage = new DataStorage() {
            @Override
            public long getRefPersistedCount(long blockOffset) {
                reads[0]++;
                return 2;
            }
        }) {
            var ref = new BlockType.BlockRef(4096, DataStorage.Area.DATA, ReferenceBlock.TYPE);
            countingStorage.incrementRefCount(ref);
            countingStorage.incrementRefCount(ref);
            countingStorage.decrementRefCount(ref);
            assertEquals(2, countingStorage.refCountUpdates.get(4096).initialCount);
            assertEquals(3, countingStorage.refCountUpdates.get(4096).count);
            assertEquals(1, reads[0]);

            countingStorage.decrementRefCount(ref);
            assertTrue(!countingStorage.refCountUpdates.containsKey(4096));
            assertEquals(1, reads[0]);
        }
    }

    /**
     * Mutates written objects and read results, then overwrites the parent to
     * verify
     * both buffered and direct writes retain their original bytes and child edges.
     */
    @Test
    void writesSnapshotBytesAndReferences() {
        for (boolean direct : new boolean[] { false, true }) {
            long firstChild = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
            long secondChild = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
            var value = new ReferenceBlock(firstChild);
            long parent = direct ? storage.writeNewBlockDirect(DataStorage.Area.DATA, value)
                    : storage.writeNewBlock(DataStorage.Area.DATA, value);
            storage.incrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));

            value.childOffset = secondChild;
            assertEquals(firstChild, storage.readBlock(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE).childOffset);
            var read = storage.readBlock(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE);
            read.childOffset = secondChild;
            byte[] bytes = storage.readBlock(parent, DataStorage.Area.DATA);
            Arrays.fill(bytes, (byte) 0);
            assertEquals(firstChild, storage.readBlock(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE).childOffset);

            storage.writeBlock(parent, DataStorage.Area.DATA, value);
            value.childOffset = 0;
            assertEquals(secondChild,
                    storage.readBlock(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE).childOffset);
            assertTrue(!storage.refCountUpdates.containsKey(firstChild));
            assertEquals(1, storage.refCountUpdates.get(secondChild).count);

            storage.decrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));
            assertTrue(!storage.refCountUpdates.containsKey(secondChild));
        }
    }

    /**
     * Replaces a typed parent with a direct typed block without children and
     * verifies its old child edge is released immediately.
     */
    @Test
    void directOverwriteReleasesPreviousReferences() {
        long child = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        long parent = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(child));
        storage.incrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));

        storage.writeBlockDirect(parent, DataStorage.Area.DATA, new ReferenceBlock(0));

        assertTrue(!storage.refCountUpdates.containsKey(child));
        assertEquals(0, storage.readBlock(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE).childOffset);
    }

    /**
     * Commits an orphaned parent and child and verifies both counts disappear
     * and their offsets can be allocated again.
     */
    @Test
    void commitReclaimsUnreferencedAllocations() {
        long child = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        long parent = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(child));

        assertEquals(1, storage.getRefPersistedCount(storage.superBlock.rootBlock),
                "The committed superblock root must retain its reference");
        storage.commit();

        assertEquals(0, storage.getRefPersistedCount(parent));
        assertEquals(0, storage.getRefPersistedCount(child));
        var freeTree = storage.freeSpaceTrees.get(DataStorage.Area.DATA);
        assertTrue(containsBlock(freeTree, parent));
        assertTrue(containsBlock(freeTree, child));
    }

    /**
     * Verifies a root reference handle allocates on first write and overwrites
     * that transaction-local block on subsequent writes.
     */
    @Test
    void rootRefHandleWritesReuseItsBlockWithinTransaction() {
        var handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        byte[] firstValue = new byte[] { 1, 2, 3 };
        byte[] secondValue = new byte[] { 4, 5, 6 };

        handle.write(new DataStorage.ByteArrayBlock(firstValue));
        long blockOffset = handle.blockOffset;
        handle.write(new DataStorage.ByteArrayBlock(secondValue));

        assertTrue(blockOffset > 0);
        assertEquals(blockOffset, handle.blockOffset);
        assertArrayEquals(secondValue, Arrays.copyOf(storage.readBlock(blockOffset, DataStorage.Area.DATA,
                DataStorage.BYTE_ARRAY_BLOCK_TYPE).data, secondValue.length));
    }

    /**
     * Commits a nonzero handle and reopens with registration before open,
     * verifying its offset, contents, and copy-on-write replacement survive.
     */
    @Test
    void rootRefHandleRestoresCommittedReferences() {
        var handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        handle.write(new DataStorage.ByteArrayBlock(new byte[] { 1, 2, 3 }));
        long firstOffset = handle.blockOffset;
        storage.commit();
        storage.close();

        storage = new DataStorage();
        handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        storage.open(dbPath);
        assertEquals(firstOffset, handle.blockOffset);
        assertArrayEquals(new byte[] { 1, 2, 3 }, Arrays.copyOf(storage.readBlock(handle.blockOffset,
                DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE).data, 3));
        handle.write(new DataStorage.ByteArrayBlock(new byte[] { 4, 5, 6 }));
        assertNotEquals(firstOffset, handle.blockOffset);
        storage.commit();
        storage.close();

        storage = new DataStorage();
        handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        storage.open(dbPath);
        storage.commit();
        assertArrayEquals(new byte[] { 4, 5, 6 }, Arrays.copyOf(storage.readBlock(handle.blockOffset,
                DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE).data, 3));
        storage.open(dbPath);
        assertArrayEquals(new byte[] { 4, 5, 6 }, Arrays.copyOf(storage.readBlock(handle.blockOffset,
                DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE).data, 3));
    }

    /**
     * Verifies root reference handle identifiers cannot be registered twice in
     * the same storage instance.
     */
    @Test
    void rootRefHandleRejectsDuplicateIds() {
        storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);

        assertThrows(IllegalStateException.class,
                () -> storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE));
    }

    /**
     * Verifies commit stores registered root reference handle IDs and offsets in
     * the root block, and that the metadata survives reopening the file.
     */
    @Test
    void commitPersistsRegisteredRootRefHandleMetadata() {
        storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);

        storage.commit();
        assertEquals(java.util.List.of(17), storage.rootBlock.refHandles.handleIds);
        assertEquals(java.util.List.of(0L), storage.rootBlock.refHandles.blockOffsets);

        storage.close();
        storage = new DataStorage();
        storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        storage.open(dbPath);

        assertEquals(java.util.List.of(17), storage.rootBlock.refHandles.handleIds);
        assertEquals(java.util.List.of(0L), storage.rootBlock.refHandles.blockOffsets);
    }

    /**
     * Verifies a registered root reference handle supplies the reference target
     * area and type to the special root slot.
     */
    @Test
    void rootRefHandleDeterminesReferenceTarget() {
        var handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        handle.write(new DataStorage.ByteArrayBlock(new byte[] { 1 }));
        storage.commit();

        var reference = RootBlock.getBlockType(storage).getReferencedBlocks(storage.rootBlock, storage).stream()
                .filter(ref -> ref.blockOffset() == handle.blockOffset)
                .findFirst().orElseThrow();

        assertEquals(DataStorage.Area.DATA, reference.area());
        assertEquals(DataStorage.BYTE_ARRAY_BLOCK_TYPE, reference.type());
    }

    /**
     * Verifies unregistered root references survive reopening and committing,
     * then can be restored when their handle is registered later.
     */
    @Test
    void unregisteredRootRefHandlesSurviveOpenAndCommit() {
        var handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        handle.write(new DataStorage.ByteArrayBlock(new byte[] { 1, 2, 3 }));
        long blockOffset = handle.blockOffset;
        storage.commit();
        storage.close();

        storage = new DataStorage();
        storage.open(dbPath);
        assertEquals(java.util.List.of(17), storage.rootBlock.refHandles.handleIds);
        assertEquals(java.util.List.of(blockOffset), storage.rootBlock.refHandles.blockOffsets);
        storage.commit();
        storage.close();

        storage = new DataStorage();
        handle = storage.getRootRefHandle(17, DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE);
        storage.open(dbPath);
        assertEquals(blockOffset, handle.blockOffset);
        assertArrayEquals(new byte[] { 1, 2, 3 }, Arrays.copyOf(storage.readBlock(handle.blockOffset,
                DataStorage.Area.DATA, DataStorage.BYTE_ARRAY_BLOCK_TYPE).data, 3));
    }

    /**
     * Verifies typed child-edge counts on allocation, replacement, and deletion.
     * A zero child offset must not create a reference to block zero.
     */
    @Test
    void typedWritesMaintainReferenceCounts() {
        long firstChild = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        long secondChild = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        long parent = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(firstChild));
        storage.incrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));
        storage.writeBlock(parent, DataStorage.Area.DATA, new ReferenceBlock(secondChild));

        storage.commit();
        assertEquals(0, storage.getRefPersistedCount(firstChild));
        assertEquals(1, storage.getRefPersistedCount(secondChild));
        assertEquals(1, storage.getRefPersistedCount(parent));

        storage.decrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));
        storage.commit();

        assertEquals(0, storage.getRefPersistedCount(parent));
        assertEquals(0, storage.getRefPersistedCount(secondChild));
    }

    @Nested
    @DisplayName("Initialization & Format Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should correctly format a new storage file with valid SuperBlock")
        void testFormatAndOpenNewFile() {
            assertNotNull(storage.superBlock);
            assertNotNull(storage.rootBlock);
            assertNotNull(storage.infoBlock);

            assertArrayEquals(DataStorage.MAGIC, storage.superBlock.magic);
            assertEquals(DataStorage.FORMAT_VERSION, storage.superBlock.version);
            assertTrue(storage.superBlock.rootBlock > 0, "Root block offset should be non-zero after format");

            assertEquals(4096, storage.blockSize(DataStorage.Area.SYSTEM));
            assertEquals(16384, storage.blockSize(DataStorage.Area.META));
            assertEquals(4096, storage.blockSize(DataStorage.Area.DATA));
        }

        @Test
        @DisplayName("Should reload existing storage file on open")
        void testReloadExistingStorage() {
            long initialRoot = storage.superBlock.rootBlock;
            storage.close();

            DataStorage reopened = new DataStorage();
            reopened.open(dbPath);

            assertEquals(initialRoot, reopened.superBlock.rootBlock);
            assertArrayEquals(DataStorage.MAGIC, reopened.superBlock.magic);
            reopened.close();
        }
    }

    @Nested
    @DisplayName("Copy-on-Write & Data Allocation Tests")
    class CowAllocationTests {

        @Test
        @DisplayName("Should write and read data blocks correctly in working memory")
        void testWriteAndReadBlock() {
            byte[] payload = new byte[storage.blockSize(DataStorage.Area.DATA)];
            Arrays.fill(payload, (byte) 0xAB);

            long blockOff = storage.writeNewBlock(DataStorage.Area.DATA, payload);
            assertTrue(blockOff > 0);
            assertTrue(storage.isBlockModified(blockOff));

            byte[] readBack = storage.readBlock(blockOff, DataStorage.Area.DATA);
            assertArrayEquals(payload, readBack);
        }

        @Test
        @DisplayName("Should defer reference count tree updates until commit")
        void testDeferredRefCountUpdates() {
            byte[] payload = new byte[storage.blockSize(DataStorage.Area.DATA)];
            long blockOff = storage.writeNewBlock(DataStorage.Area.DATA, payload);
            storage.incrementRefCount(new BlockType.BlockRef(blockOff, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            // Prior to commit, refcount tree on disk should not contain the block
            assertEquals(0, storage.getRefPersistedCount(blockOff));

            storage.commit();

            // After commit, reference count should be updated to 1
            assertEquals(1, storage.getRefPersistedCount(blockOff));
        }
    }

    @Nested
    @DisplayName("Transaction Commit & Atomic Persistence Tests")
    class TransactionCommitTests {

        @Test
        @DisplayName("Should persist modified data blocks and update root offset on commit")
        void testAtomicCommit() {
            long oldRoot = storage.superBlock.rootBlock;

            byte[] data1 = new byte[storage.blockSize(DataStorage.Area.DATA)];
            Arrays.fill(data1, (byte) 0x11);
            long b1 = storage.writeNewBlock(DataStorage.Area.DATA, data1);
            storage.incrementRefCount(new BlockType.BlockRef(b1, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            byte[] data2 = new byte[storage.blockSize(DataStorage.Area.META)];
            Arrays.fill(data2, (byte) 0x22);
            long b2 = storage.writeNewBlock(DataStorage.Area.META, data2);
            storage.incrementRefCount(new BlockType.BlockRef(b2, DataStorage.Area.META,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            storage.commit();

            long newRoot = storage.superBlock.rootBlock;
            assertNotEquals(oldRoot, newRoot, "Commit must produce a new root block via CoW");
            assertEquals(0, storage.getRefPersistedCount(oldRoot),
                    "The previous root must release its final reference after the new root is written");

            // Verify persistence by reopening from disk
            storage.close();
            DataStorage reopened = new DataStorage();
            reopened.open(dbPath);

            assertEquals(newRoot, reopened.superBlock.rootBlock);
            assertArrayEquals(data1, reopened.readBlock(b1, DataStorage.Area.DATA));
            assertArrayEquals(data2, reopened.readBlock(b2, DataStorage.Area.META));
            reopened.close();
        }
    }

    @Nested
    @DisplayName("Crash Recovery & Rollback Behavior Tests")
    class CrashRecoveryTests {

        @Test
        @DisplayName("Uncommitted dirty writes should be rolled back cleanly on crash")
        void testCrashRollbackUncommittedWrites() throws IOException {
            // Transaction 1: Commit initial data state
            byte[] v1Data = new byte[storage.blockSize(DataStorage.Area.DATA)];
            Arrays.fill(v1Data, (byte) 0x55);
            long b1 = storage.writeNewBlock(DataStorage.Area.DATA, v1Data);
            storage.incrementRefCount(new BlockType.BlockRef(b1, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));
            storage.commit();

            long committedRoot = storage.superBlock.rootBlock;

            // Transaction 2: Perform uncommitted dirty writes & direct disk writes without
            // commit
            byte[] uncommittedData = new byte[storage.blockSize(DataStorage.Area.DATA)];
            Arrays.fill(uncommittedData, (byte) 0x99);
            long b2 = storage.writeNewBlock(DataStorage.Area.DATA, uncommittedData);

            // Directly write to file to simulate dirty uncommitted disk modifications
            byte[] garbage = new byte[storage.blockSize(DataStorage.Area.DATA)];
            Arrays.fill(garbage, (byte) 0xFF);
            storage.writeBlockDirect(b2, DataStorage.Area.DATA, garbage);

            // SIMULATE CRASH: Abruptly close raw file handle without calling
            // storage.commit()
            storage.close();

            // RECOVERY: Reopen file from disk
            DataStorage recovered = new DataStorage();
            recovered.open(dbPath);

            // Verifications
            assertEquals(committedRoot, recovered.superBlock.rootBlock,
                    "Recovered state must point to last committed superblock root");
            assertEquals(1, recovered.getRefPersistedCount(b1), "Committed block b1 must retain reference count of 1");
            assertEquals(0, recovered.getRefPersistedCount(b2), "Uncommitted block b2 must not exist in refcount tree");
            assertArrayEquals(v1Data, recovered.readBlock(b1, DataStorage.Area.DATA), "Committed data must be intact");

            recovered.close();
        }
    }

    @Nested
    @DisplayName("Deferred Block Reuse & Free Space Tests")
    class DeferredFreeSpaceTests {

        @Test
        @DisplayName("Releasing the final reference should reclaim the block on commit")
        void testDeferredBlockReclamation() {
            byte[] payload = new byte[storage.blockSize(DataStorage.Area.DATA)];
            long blockOff = storage.writeNewBlock(DataStorage.Area.DATA, payload);
            storage.incrementRefCount(new BlockType.BlockRef(blockOff, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));
            storage.commit();

            assertEquals(1, storage.getRefPersistedCount(blockOff));

            // Drop the final reference; its deletion defers returning the block to free
            // space.
            storage.decrementRefCount(new BlockType.BlockRef(blockOff, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            // Before commit, refcount remains 1 in persisted state
            assertEquals(1, storage.getRefPersistedCount(blockOff));

            storage.commit();

            // After commit, block refcount drops to 0 and space is returned to free tree
            assertEquals(0, storage.getRefPersistedCount(blockOff));
        }
    }
}
