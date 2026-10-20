package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            public java.util.List<BlockType.BlockRef> getReferencedBlocks(ReferenceBlock value) {
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
        assertEquals(0, storage.refCountAdjustments.get(child).adjustment);

        long parent = storage.writeNewBlockDirect(DataStorage.Area.DATA, new ReferenceBlock(child));
        assertEquals(1, storage.refCountAdjustments.get(child).adjustment);
        assertEquals(0, storage.refCountAdjustments.get(parent).adjustment);
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
            storage.incrementRefCount(parent, DataStorage.Area.DATA);

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
            assertTrue(!storage.refCountAdjustments.containsKey(firstChild));
            assertEquals(1, storage.refCountAdjustments.get(secondChild).adjustment);

            storage.decrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));
            assertTrue(!storage.refCountAdjustments.containsKey(secondChild));
        }
    }

    /**
     * Replaces a typed parent with raw direct bytes and verifies its old child
     * edge is released immediately rather than reconstructed from the new bytes.
     */
    @Test
    void directOverwriteReleasesPreviousReferences() {
        long child = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(0));
        long parent = storage.writeNewBlock(DataStorage.Area.DATA, new ReferenceBlock(child));
        storage.incrementRefCount(parent, DataStorage.Area.DATA);

        storage.writeBlockDirect(parent, DataStorage.Area.DATA, new byte[storage.blockSize(DataStorage.Area.DATA)]);

        assertTrue(!storage.refCountAdjustments.containsKey(child));
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

        storage.commit();

        assertEquals(0, storage.getRefCount(parent));
        assertEquals(0, storage.getRefCount(child));
        assertTrue(storage.freeBlocks.get(DataStorage.Area.DATA).contains(parent));
        assertTrue(storage.freeBlocks.get(DataStorage.Area.DATA).contains(child));
    }

    /**
     * Replaces a persisted tree root repeatedly, then reopens the file to verify
     * parent-owned tree blocks survive commits without allocation references.
     */
    @Test
    void parentOwnedTreeSurvivesRepeatedCommitsAndReopen() {
        var tree = new BTree(storage.new BTreeStorage(DataStorage.Area.META), Long.BYTES, null);
        for (int round = 0; round < 12; round++) {
            tree.put(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(round).array(), new byte[] { (byte) round });
            storage.rootBlock.systemKvTree.blockOffset = tree.rootOffset;
            storage.commit();
        }

        storage.close();
        storage.open(dbPath);
        var reopenedTree = new BTree(storage.new BTreeStorage(DataStorage.Area.META), Long.BYTES, null,
                storage.rootBlock.systemKvTree.blockOffset);
        for (int round = 0; round < 12; round++)
            assertArrayEquals(new byte[] { (byte) round },
                    reopenedTree.get(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(round).array()));
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
        storage.incrementRefCount(parent, DataStorage.Area.DATA);
        storage.writeBlock(parent, DataStorage.Area.DATA, new ReferenceBlock(secondChild));

        storage.commit();
        assertEquals(0, storage.getRefCount(firstChild));
        assertEquals(1, storage.getRefCount(secondChild));
        assertEquals(1, storage.getRefCount(parent));

        storage.decrementRefCount(new BlockType.BlockRef(parent, DataStorage.Area.DATA, ReferenceBlock.TYPE));
        storage.commit();

        assertEquals(0, storage.getRefCount(parent));
        assertEquals(0, storage.getRefCount(secondChild));
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
            storage.incrementRefCount(blockOff, DataStorage.Area.DATA);

            // Prior to commit, refcount tree on disk should not contain the block
            assertEquals(0, storage.getRefCount(blockOff));

            storage.commit();

            // After commit, reference count should be updated to 1
            assertEquals(1, storage.getRefCount(blockOff));
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
            storage.incrementRefCount(b1, DataStorage.Area.DATA);

            byte[] data2 = new byte[storage.blockSize(DataStorage.Area.META)];
            Arrays.fill(data2, (byte) 0x22);
            long b2 = storage.writeNewBlock(DataStorage.Area.META, data2);
            storage.incrementRefCount(b2, DataStorage.Area.META);

            storage.commit();

            long newRoot = storage.superBlock.rootBlock;
            assertNotEquals(oldRoot, newRoot, "Commit must produce a new root block via CoW");
            assertEquals(0, storage.getRefCount(oldRoot),
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
            storage.incrementRefCount(b1, DataStorage.Area.DATA);
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
            assertEquals(1, recovered.getRefCount(b1), "Committed block b1 must retain reference count of 1");
            assertEquals(0, recovered.getRefCount(b2), "Uncommitted block b2 must not exist in refcount tree");
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
            storage.incrementRefCount(blockOff, DataStorage.Area.DATA);
            storage.commit();

            assertEquals(1, storage.getRefCount(blockOff));

            // Drop the final reference; its deletion defers returning the block to free
            // space.
            storage.decrementRefCount(new BlockType.BlockRef(blockOff, DataStorage.Area.DATA,
                    DataStorage.BYTE_ARRAY_BLOCK_TYPE));

            // Before commit, refcount remains 1 in persisted state
            assertEquals(1, storage.getRefCount(blockOff));

            storage.commit();

            // After commit, block refcount drops to 0 and space is returned to free tree
            assertEquals(0, storage.getRefCount(blockOff));
        }
    }
}
