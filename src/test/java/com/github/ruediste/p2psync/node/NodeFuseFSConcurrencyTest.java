package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import ru.serce.jnrfuse.struct.FileStat;

class NodeFuseFSConcurrencyTest {

    private static Pointer buffer(byte[] bytes) {
        Pointer result = Memory.allocateDirect(Runtime.getSystemRuntime(), Math.max(1, bytes.length));
        result.put(0, bytes, 0, bytes.length);
        return result;
    }

    /**
     * Verifies that a FUSE request waits for the node state lock. While the test
     * holds the monitor, getattr must stay blocked; this is the property that
     * prevents a read from observing a write in progress.
     */
    @Test
    void fuseRequestsWaitForTheNodeStateLock() throws Exception {
        var node = new Node(new Node.Network());
        try {
            node.createNewUser("alice");
            var fs = new NodeFuseFS(node);
            var started = new CountDownLatch(1);
            Thread reader;
            synchronized (node) {
                reader = new Thread(() -> {
                    started.countDown();
                    fs.getattr("/alice/home", new FileStat(Runtime.getSystemRuntime()));
                }, "fuse-lock-test");
                reader.start();
                started.await();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (reader.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
                    Thread.onSpinWait();
                assertEquals(Thread.State.BLOCKED, reader.getState(),
                        "getattr must block while the node state lock is held");
            }
            reader.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(reader.isAlive());
        } finally {
            node.close();
        }
    }

    /**
     * Verifies that concurrent FUSE reads and writes through NodeFuseFS never
     * observe a half-updated file tree. Each FUSE request must hold the node
     * state lock; otherwise a getattr resolving names through GuidMapper can
     * race with a write allocating short GUIDs and fail with EIO ("Missing
     * reverse GUID mapping").
     */
    @Test
    void serializesConcurrentReadsAndWrites() throws Exception {
        var node = new Node(new Node.Network());
        try {
            node.createNewUser("alice");
            var fs = new NodeFuseFS(node);
            assertEquals(0, fs.mkdir("/alice/home/docs", 0755));
            assertEquals(0, fs.create("/alice/home/docs/note.txt", 0644, null));

            var errors = new AtomicInteger();
            var failures = new ConcurrentLinkedQueue<Throwable>();
            var start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(8);
            for (int thread = 0; thread < 8; thread++) {
                final int id = thread;
                pool.execute(() -> {
                    try {
                        start.await();
                        var stat = new FileStat(Runtime.getSystemRuntime());
                        if (id < 2) {
                            for (int i = 0; i < 40; i++) {
                                String path = "/alice/home/docs/new-" + id + "-" + i;
                                byte[] data = ("writer-" + id + "-" + i).getBytes(StandardCharsets.UTF_8);
                                if (fs.create(path, 0644, null) != 0
                                        || fs.write(path, buffer(data), data.length, 0, null) < 0
                                        || fs.unlink(path) != 0)
                                    errors.incrementAndGet();
                            }
                        } else {
                            for (int i = 0; i < 2000; i++) {
                                if (fs.getattr("/alice/home", stat) != 0
                                        || fs.getattr("/alice/home/docs", stat) != 0
                                        || fs.getattr("/alice/home/docs/note.txt", stat) != 0)
                                    errors.incrementAndGet();
                                if (fs.read("/alice/home/docs/note.txt", buffer(new byte[32]), 32, 0, null) < 0)
                                    errors.incrementAndGet();
                            }
                        }
                    } catch (Throwable error) {
                        failures.add(error);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
            assertTrue(failures.isEmpty(), () -> "Concurrent FUSE access failed: " + failures);
            assertEquals(0, errors.get(), "FUSE operations returned EIO or an unexpected error");
        } finally {
            node.close();
        }
    }
}
