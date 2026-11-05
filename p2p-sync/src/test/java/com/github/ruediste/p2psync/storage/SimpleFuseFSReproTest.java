package com.github.ruediste.p2psync.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;

@Disabled
class SimpleFuseFSReproTest {
    @TempDir
    Path tempDir;

    private Pointer buffer(byte[] bytes) {
        Pointer result = Memory.allocateDirect(Runtime.getSystemRuntime(), Math.max(1, bytes.length));
        result.put(0, bytes, 0, bytes.length);
        return result;
    }

    private void write(SimpleFuseFS fs, String path, int size, long offset) {
        byte[] data = new byte[size];
        new Random(offset).nextBytes(data);
        assertEquals(size, fs.write(path, buffer(data), size, offset, null), "write " + size + "@" + offset);
    }

    @Test
    void appendSmall() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("a.db"))) {
            fs.create("/f", 0644, null);
            for (int i = 0; i < 200; i++)
                write(fs, "/f", 100, i * 100L);
        }
    }

    @Test
    void appendBlocks() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("b.db"))) {
            fs.create("/f", 0644, null);
            for (int i = 0; i < 100; i++)
                write(fs, "/f", 4096, i * 4096L);
        }
    }

    @Test
    void overwriteSame() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("c.db"))) {
            fs.create("/f", 0644, null);
            write(fs, "/f", 40000, 0);
            for (int i = 0; i < 20; i++)
                write(fs, "/f", 10000, 1000);
        }
    }

    @Test
    void truncateAndRewrite() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("d.db"))) {
            fs.create("/f", 0644, null);
            for (int i = 0; i < 10; i++) {
                write(fs, "/f", 40000, 0);
                assertEquals(0, fs.truncate("/f", 0));
                write(fs, "/f", 10, 0);
                assertEquals(0, fs.truncate("/f", 12345));
            }
        }
    }

    @Test
    void randomWithReopen() {
        Path backing = tempDir.resolve("g.db");
        Random r = new Random(2);
        for (int round = 0; round < 6; round++) {
            try (var fs = new SimpleFuseFS(backing)) {
                if (round == 0)
                    assertEquals(0, fs.mkdir("/test", 0755));
                for (int i = 0; i < 60; i++) {
                    String name = "/test/f" + r.nextInt(4);
                    int op = r.nextInt(7);
                    String ctx = "round " + round + " op " + i + " " + op + " " + name;
                    if (op == 0)
                        fs.create(name, 0644, null);
                    else if (op == 1)
                        fs.unlink(name);
                    else if (op == 2)
                        fs.rename(name, "/test/f" + r.nextInt(4));
                    else if (op == 3) {
                        int res = fs.truncate(name, r.nextInt(50000));
                        org.junit.jupiter.api.Assertions
                                .assertTrue(res == 0 || res == -ru.serce.jnrfuse.ErrorCodes.ENOENT(), ctx + " " + res);
                    } else {
                        int size = 1 + r.nextInt(20000);
                        byte[] data = new byte[size];
                        int res = fs.write(name, buffer(data), size, r.nextInt(50000), null);
                        org.junit.jupiter.api.Assertions.assertTrue(
                                res == size || res == -ru.serce.jnrfuse.ErrorCodes.ENOENT(), ctx + " " + res);
                    }
                    assertEquals(0, fs.getattr("/", new ru.serce.jnrfuse.struct.FileStat(Runtime.getSystemRuntime())),
                            ctx);
                }
            }
        }
    }

    @Test
    void largeAppend() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("h.db"))) {
            fs.mkdir("/test", 0755);
            fs.create("/test/f", 0644, null);
            for (int i = 0; i < 800; i++)
                write(fs, "/test/f", 131072, i * 131072L);
        }
    }

    @Test
    void largeAppendDirect() {
        var storage = new DataStorage();
        storage.open(tempDir.resolve("i.db").toString());
        try {
            var tree = new FileTree(storage);
            var root = new com.github.ruediste.p2psync.util.ShortGuid();
            root.counter = 1;
            var file = new com.github.ruediste.p2psync.util.ShortGuid();
            file.counter = 2;
            tree.createDirectory(root, root, "root", new FileTree.FileMeta());
            tree.createFile(root, file, "f", new FileTree.FileMeta());
            var handle = storage.getRootRefHandle(1, DataStorage.Area.META, FileTree.BLOCK_TYPE);
            for (int i = 0; i < 800; i++) {
                tree.write(file, i * 131072L, new byte[131072]);
                handle.blockOffset = tree.getRootOffset();
                System.out.println("commit " + i);
                storage.commit();
            }
        } finally {
            storage.close();
        }
    }

    @Test
    void random() {
        try (var fs = new SimpleFuseFS(tempDir.resolve("e.db"))) {
            fs.create("/f", 0644, null);
            Random r = new Random(1);
            for (int i = 0; i < 300; i++) {
                if (r.nextInt(5) == 0)
                    assertEquals(0, fs.truncate("/f", r.nextInt(100000)), "truncate " + i);
                else
                    write(fs, "/f", 1 + r.nextInt(20000), r.nextInt(100000));
            }
        }
    }
}
