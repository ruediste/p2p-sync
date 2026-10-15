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

            byte[] data2 = new byte[storage.blockSize(DataStorage.Area.META)];
            Arrays.fill(data2, (byte) 0x22);
            long b2 = storage.writeNewBlock(DataStorage.Area.META, data2);

            storage.commit();

            long newRoot = storage.superBlock.rootBlock;
            assertNotEquals(oldRoot, newRoot, "Commit must produce a new root block via CoW");

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
        @DisplayName("Freed blocks should be held in reserved set during transaction and reused after commit")
        void testDeferredBlockReclamation() {
            byte[] payload = new byte[storage.blockSize(DataStorage.Area.DATA)];
            long blockOff = storage.writeNewBlock(DataStorage.Area.DATA, payload);
            storage.commit();

            assertEquals(1, storage.getRefCount(blockOff));

            // Free the block in current transaction
            storage.addToDeferredFreeBlocks(blockOff, DataStorage.Area.DATA);

            // Before commit, refcount remains 1 in persisted state
            assertEquals(1, storage.getRefCount(blockOff));

            storage.commit();

            // After commit, block refcount drops to 0 and space is returned to free tree
            assertEquals(0, storage.getRefCount(blockOff));
        }
    }
}
