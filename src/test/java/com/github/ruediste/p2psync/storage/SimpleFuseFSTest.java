package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.Timespec;

class SimpleFuseFSTest {
    @TempDir
    Path tempDir;

    private Pointer buffer(byte[] bytes) {
        Pointer result = Memory.allocateDirect(Runtime.getSystemRuntime(), Math.max(1, bytes.length));
        result.put(0, bytes, 0, bytes.length);
        return result;
    }

    private Timespec timespec(long seconds, long nanoseconds) {
        Pointer memory = Memory.allocateDirect(Runtime.getSystemRuntime(), 2 * Long.BYTES);
        Timespec result = Timespec.of(memory);
        result.tv_sec.set(seconds);
        result.tv_nsec.set(nanoseconds);
        return result;
    }

    /**
     * Verifies the timestamp callback stores the requested modification time for
     * subsequent getattr calls.
     */
    @Test
    void updatesAndPersistsModificationTime() {
        Path backing = tempDir.resolve("files.db");
        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(0, fs.create("/file", 0644, null));
            assertEquals(0, fs.utimens("/file", new Timespec[] {
                    timespec(0, 0), timespec(1_700_000_000L, 123_456_789)
            }));

            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/file", stat));
            assertEquals(1_700_000_000L, stat.st_mtim.tv_sec.longValue());
            assertEquals(123_000_000L, stat.st_mtim.tv_nsec.longValue());
            assertEquals(0, fs.flush("/file", null));
        }

        try (var fs = new SimpleFuseFS(backing)) {
            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/file", stat));
            assertEquals(1_700_000_000L, stat.st_mtim.tv_sec.longValue());
            assertEquals(123_000_000L, stat.st_mtim.tv_nsec.longValue());
        }
    }

    /**
     * Verifies UTIME_NOW updates mtime, UTIME_OMIT preserves it, and invalid
     * requests return errors without poisoning subsequent operations.
     */
    @Test
    void utimensSupportsNowOmitAndValidation() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("files.db"))) {
            assertEquals(0, fs.create("/file", 0644, null));
            assertEquals(0, fs.utimens("/file", new Timespec[] {
                    timespec(0, 0), timespec(1_700_000_000L, 0)
            }));

            assertEquals(0, fs.utimens("/file", new Timespec[] {
                    timespec(0, 0), timespec(0, (1L << 30) - 2)
            }));
            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/file", stat));
            assertEquals(1_700_000_000L, stat.st_mtim.tv_sec.longValue());

            long beforeUpdate = System.currentTimeMillis();
            assertEquals(0, fs.utimens("/file", new Timespec[] {
                    timespec(0, 0), timespec(0, (1L << 30) - 1)
            }));
            long afterUpdate = System.currentTimeMillis();
            assertEquals(0, fs.getattr("/file", stat));
            long actualMtime = stat.st_mtim.tv_sec.longValue() * 1000
                    + stat.st_mtim.tv_nsec.longValue() / 1_000_000;
            assertTrue(actualMtime >= beforeUpdate && actualMtime <= afterUpdate);

            assertEquals(-ErrorCodes.EINVAL(), fs.utimens("/file", new Timespec[] {
                    timespec(0, 0), timespec(0, 1_000_000_000L)
            }));
            assertEquals(-ErrorCodes.ENOENT(), fs.utimens("/missing", null));
            assertEquals(0, fs.getattr("/file", stat));
        }
    }

    /**
     * Writes inline and extent data, then reopens the backing file to verify
     * committed roots and bytes.
     */
    @Test
    void persistsFilesAcrossReopens() {
        Path backing = tempDir.resolve("files.db");
        byte[] content = new byte[40_000];
        for (int index = 0; index < content.length; index++)
            content[index] = (byte) (index * 31);
        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(0, fs.mkdir("/folder", 0755));
            assertEquals(0, fs.create("/folder/data", 0644, null));
            assertEquals(content.length, fs.write("/folder/data", buffer(content), content.length, 0, null));
            assertEquals(0, fs.create("/small", 0644, null));
            assertEquals(2, fs.write("/small", buffer(new byte[] { 7, 8 }), 2, 2, null));
            assertEquals(0, fs.fsync("/folder/data", 0, null));
        }
        try (var fs = new SimpleFuseFS(backing)) {
            Pointer result = buffer(new byte[content.length]);
            assertEquals(content.length, fs.read("/folder/data", result, content.length, 0, null));
            byte[] actual = new byte[content.length];
            result.get(0, actual, 0, actual.length);
            assertArrayEquals(content, actual);
            assertEquals(4, fs.read("/small", result, 100, 0, null));
            byte[] small = new byte[4];
            result.get(0, small, 0, small.length);
            assertArrayEquals(new byte[] { 0, 0, 7, 8 }, small);
            assertEquals(0, fs.read("/small", result, 10, 4, null));
            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/folder/data", stat));
            assertEquals(content.length, stat.st_size.longValue());
            assertEquals(FileStat.S_IFREG | 0644, stat.st_mode.intValue());
            assertEquals(0, fs.getattr("/", stat));
            assertEquals(FileStat.S_IFDIR | 0755, stat.st_mode.intValue());
        }
    }

    /**
     * Shrinks and grows a file, checking zero filling and invalid ranges without
     * poisoning later operations.
     */
    @Test
    void truncatesAndValidatesIoRanges() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("files.db"))) {
            assertEquals(0, fs.create("/file", 0644, null));
            assertEquals(3, fs.write("/file", buffer(new byte[] { 1, 2, 3 }), 3, 0, null));
            assertEquals(0, fs.truncate("/file", 1));
            assertEquals(0, fs.truncate("/file", 5));
            Pointer result = buffer(new byte[10]);
            assertEquals(5, fs.read("/file", result, 10, 0, null));
            byte[] actual = new byte[5];
            result.get(0, actual, 0, actual.length);
            assertArrayEquals(new byte[] { 1, 0, 0, 0, 0 }, actual);
            assertEquals(-ErrorCodes.EINVAL(), fs.truncate("/file", -1));
            assertEquals(-ErrorCodes.EINVAL(), fs.read("/file", result, 1, -1, null));
            assertEquals(-ErrorCodes.EFBIG(), fs.write("/file", result, 1, Long.MAX_VALUE, null));
            assertEquals(0, fs.open("/file", null));
        }
    }

    /**
     * Checks namespace errno values and verifies rmdir never recursively removes a
     * nonempty directory.
     */
    @Test
    void rejectsInvalidNamespaceOperations() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("files.db"))) {
            assertEquals(-ErrorCodes.ENOENT(), fs.create("/missing/file", 0644, null));
            assertEquals(0, fs.mkdir("/dir", 0755));
            assertEquals(0, fs.create("/dir/file", 0644, null));
            assertEquals(-ErrorCodes.EEXIST(), fs.mkdir("/dir", 0755));
            assertEquals(-ErrorCodes.ENOTDIR(), fs.create("/dir/file/child", 0644, null));
            assertEquals(-ErrorCodes.EISDIR(), fs.open("/dir", null));
            assertEquals(-ErrorCodes.EISDIR(), fs.unlink("/dir"));
            assertEquals(-ErrorCodes.ENOTDIR(), fs.rmdir("/dir/file"));
            assertEquals(-ErrorCodes.ENOTEMPTY(), fs.rmdir("/dir"));
            assertEquals(-ErrorCodes.EBUSY(), fs.rmdir("/"));
            assertEquals(-ErrorCodes.EINVAL(), fs.rename("/dir", "/dir/child"));
            assertEquals(0, fs.unlink("/dir/file"));
            assertEquals(0, fs.rmdir("/dir"));
            assertEquals(-ErrorCodes.ENOENT(), fs.open("/dir/file", null));
        }
    }

    /**
     * Moves and replaces files despite colliding intermediate names, then checks
     * the renamed data after reopen.
     */
    @Test
    void renamesAcrossDirectoriesAndReplacesTargets() {
        Path backing = tempDir.resolve("files.db");
        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(0, fs.mkdir("/from", 0755));
            assertEquals(0, fs.mkdir("/to", 0755));
            assertEquals(0, fs.create("/from/source", 0644, null));
            assertEquals(0, fs.create("/from/target", 0644, null));
            assertEquals(0, fs.create("/to/source", 0644, null));
            assertEquals(0, fs.create("/to/target", 0644, null));
            assertEquals(1, fs.write("/from/source", buffer(new byte[] { 42 }), 1, 0, null));
            assertEquals(0, fs.rename("/from/source", "/to/target"));
            assertEquals(-ErrorCodes.ENOENT(), fs.open("/from/source", null));
            assertEquals(0, fs.open("/from/target", null));
            assertEquals(0, fs.open("/to/source", null));
            assertEquals(0, fs.rename("/to/target", "/to/target"));
            assertEquals(-ErrorCodes.EISDIR(), fs.rename("/to/target", "/from"));
            assertEquals(-ErrorCodes.ENOTDIR(), fs.rename("/from", "/to/target"));
            assertEquals(-ErrorCodes.ENOTEMPTY(), fs.rename("/from", "/to"));
            assertEquals(0, fs.flush("/to/target", null));
        }
        try (var fs = new SimpleFuseFS(backing)) {
            Pointer result = buffer(new byte[1]);
            assertEquals(1, fs.read("/to/target", result, 1, 0, null));
            assertEquals(42, result.getByte(0));
        }
    }

    /**
     * Stops a full directory buffer and resumes from its cookie without duplicating
     * or omitting entries.
     */
    @Test
    void resumesDirectoryListing() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("files.db"))) {
            assertEquals(0, fs.create("/file", 0644, null));
            List<String> names = new ArrayList<>();
            List<Long> offsets = new ArrayList<>();
            FuseFillDir filler = (buffer, name, stat, offset) -> {
                if (names.size() == 2)
                    return 1;
                String decoded = StandardCharsets.UTF_8.decode(name).toString();
                int end = decoded.indexOf('\0');
                names.add(end < 0 ? decoded : decoded.substring(0, end));
                offsets.add(offset);
                return 0;
            };
            assertEquals(0, fs.readdir("/", null, filler, 0, null));
            assertEquals(List.of(".", ".."), names);
            long resumeOffset = offsets.getLast();
            names.clear();
            assertEquals(0, fs.readdir("/", null, filler, resumeOffset, null));
            assertEquals(List.of("file"), names);
            assertEquals(-ErrorCodes.ENOTDIR(), fs.readdir("/file", null, filler, 0, null));
        }
    }

    /**
     * Verifies close discards uncommitted changes and flush, fsync, and the timer
     * each publish successful mutations.
     */
    @Test
    void commitsOnFlushFsyncAndPeriodically() throws InterruptedException {
        Path backing = tempDir.resolve("commit-triggers.db");
        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(0, fs.create("/discarded", 0644, null));
        }

        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(-ErrorCodes.ENOENT(), fs.open("/discarded", null));
            assertEquals(0, fs.create("/flushed", 0644, null));
            assertEquals(0, fs.flush("/flushed", null));
            assertEquals(0, fs.create("/synced", 0644, null));
            assertEquals(0, fs.fsync("/synced", 0, null));
            assertEquals(0, fs.create("/periodic", 0644, null));
            Thread.sleep(5_500);
        }

        try (var fs = new SimpleFuseFS(backing)) {
            assertEquals(0, fs.open("/flushed", null));
            assertEquals(0, fs.open("/synced", null));
            assertEquals(0, fs.open("/periodic", null));
        }
    }
}