package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStorageInitializationTest {

    @TempDir
    Path tempDir;

    /**
     * Construction must not inspect block sizes before a file has been opened or
     * formatted; those sizes are supplied by the superblock later.
     */
    @Test
    void constructorDoesNotRequireSuperblock() {
        assertDoesNotThrow(DataStorage::new);
    }

    /**
     * Uses a full-sized but otherwise zeroed header to ensure open rejects a file
     * with the wrong magic instead of treating it as a valid store.
     */
    @Test
    void rejectsInvalidMagic() {
        Path path = tempDir.resolve("invalid-magic.db");
        writeFile(path, new byte[DataStorage.SUPERBLOCK_SIZE]);

        var exception = openExpectingFailure(path);

        assertEquals("Not a p2p-sync data file (bad magic)", exception.getCause().getMessage());
    }

    /**
     * Keeps the magic and header shape valid but changes the format version, so
     * open must reject files whose on-disk layout it cannot interpret.
     */
    @Test
    void rejectsUnsupportedFormatVersion() {
        Path path = tempDir.resolve("unsupported-version.db");
        ByteBuffer header = ByteBuffer.allocate(DataStorage.SUPERBLOCK_SIZE);
        header.put(DataStorage.MAGIC);
        header.putLong(DataStorage.FORMAT_VERSION + 1);
        header.putLong(DataStorage.SUPERBLOCK_SIZE);
        header.putInt(4096);
        header.putInt(16384);
        header.putInt(4096);
        writeFile(path, header.array());

        var exception = openExpectingFailure(path);

        assertEquals("Unsupported data storage format version: " + (DataStorage.FORMAT_VERSION + 1),
                exception.getCause().getMessage());
    }

    /**
     * Encodes a valid-version header with a zero DATA block size. Open must reject
     * invalid geometry before using it to load blocks.
     */
    @Test
    void rejectsInvalidBlockSizes() {
        Path path = tempDir.resolve("invalid-block-size.db");
        writeFile(path, superblock(DataStorage.FORMAT_VERSION, DataStorage.SUPERBLOCK_SIZE, 0, 16384, 4096));

        var exception = openExpectingFailure(path);

        assertEquals("Invalid data storage block sizes", exception.getCause().getMessage());
    }

    /**
     * Points the root at the end of a superblock-only file. The offset is
     * nonzero, but there is no room for a complete SYSTEM root block there.
     */
    @Test
    void rejectsRootOffsetOutsideFile() {
        Path path = tempDir.resolve("invalid-root-offset.db");
        writeFile(path, superblock(DataStorage.FORMAT_VERSION, DataStorage.SUPERBLOCK_SIZE, 4096, 16384, 4096));

        var exception = openExpectingFailure(path);

        assertEquals("Invalid root block offset: " + DataStorage.SUPERBLOCK_SIZE,
                exception.getCause().getMessage());
    }

    /**
     * Creates a nonempty file shorter than one superblock. Open must report it as
     * truncated and leave its one-byte contents intact rather than formatting it.
     */
    @Test
    void rejectsTruncatedFileWithoutFormattingOverIt() {
        Path path = tempDir.resolve("truncated.db");
        writeFile(path, new byte[] { 1 });

        var exception = openExpectingFailure(path);

        assertEquals("Truncated data storage superblock", exception.getCause().getMessage());
        assertEquals(1, fileSize(path));
    }

    /**
     * Opens a minimal valid on-disk fixture, contaminates transaction-local
     * caches, then reopens it. Reopen must clear those caches and rebuild the
     * persistent trees and info block from disk.
     */
    @Test
    void reopeningResetsTransactionStateAndLoadsTrees() {
        Path path = tempDir.resolve("valid-empty.db");
        writeMinimalValidFile(path);

        try (var storage = new DataStorage()) {
            storage.open(path.toString());
            storage.modifiedBlocks.put(123L, new byte[storage.blockSize(DataStorage.Area.DATA)]);
            storage.freeBlocks.get(DataStorage.Area.DATA).add(16384L);

            storage.open(path.toString());

            assertTrue(storage.modifiedBlocks.isEmpty());
            assertTrue(storage.freeBlocks.get(DataStorage.Area.DATA).isEmpty());
            assertNotNull(storage.refCountTree);
            assertEquals(16384, storage.infoBlock.getInlineDataThreshold());
        }
    }

    /**
     * Leaves extra bytes after a valid root's committed length. Open must discard
     * the uncommitted tail while preserving the root and info blocks.
     */
    @Test
    void reopeningTruncatesTrailingBytesToRootFileLength() {
        Path path = tempDir.resolve("trailing-data.db");
        long committedLength = 3L * 4096;
        writeMinimalValidFile(path, 5L * 4096, committedLength);

        try (var storage = new DataStorage()) {
            storage.open(path.toString());

            assertEquals(committedLength, fileSize(path));
            assertEquals(16384, storage.infoBlock.getInlineDataThreshold());
        }
    }

    /**
     * A root without a committed file length is invalid in the current format;
     * open must not guess the intended boundary from the physical file size.
     */
    @Test
    void rejectsMissingRootFileLength() {
        Path path = tempDir.resolve("missing-root-file-length.db");
        writeMinimalValidFile(path, 3L * 4096, 0);

        var exception = openExpectingFailure(path);

        assertEquals("Invalid committed file length: 0", exception.getCause().getMessage());
    }

    private static void writeMinimalValidFile(Path path) {
        writeMinimalValidFile(path, 3L * 4096, 3L * 4096);
    }

    private static void writeMinimalValidFile(Path path, long physicalLength, long committedLength) {
        int blockSize = 4096;
        long rootOffset = DataStorage.SUPERBLOCK_SIZE;
        long infoOffset = rootOffset + blockSize;
        ByteBuffer file = ByteBuffer.allocate(Math.toIntExact(physicalLength));

        file.put(DataStorage.MAGIC);
        file.putLong(DataStorage.FORMAT_VERSION);
        file.putLong(rootOffset);
        file.putInt(blockSize);
        file.putInt(16384);
        file.putInt(blockSize);

        file.position((int) rootOffset + 5 * Long.BYTES);
        file.putLong(infoOffset);
        file.position((int) rootOffset + 14 * Long.BYTES);
        file.putLong(committedLength);
        file.position((int) infoOffset);
        file.putInt(16384);
        writeFile(path, file.array());
    }

    private static byte[] superblock(long version, long rootOffset, int dataBlockSize, int metaBlockSize,
            int systemBlockSize) {
        ByteBuffer header = ByteBuffer.allocate(DataStorage.SUPERBLOCK_SIZE);
        header.put(DataStorage.MAGIC);
        header.putLong(version);
        header.putLong(rootOffset);
        header.putInt(dataBlockSize);
        header.putInt(metaBlockSize);
        header.putInt(systemBlockSize);
        return header.array();
    }

    private UncheckedIOException openExpectingFailure(Path path) {
        try (var storage = new DataStorage()) {
            return assertThrows(UncheckedIOException.class, () -> storage.open(path.toString()));
        }
    }

    private static void writeFile(Path path, byte[] contents) {
        try {
            Files.write(path, contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}