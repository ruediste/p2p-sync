package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class NodeDesktopAppTest {
    /** Verifies background rounds repeat after failure without opening a window. */
    @Test
    void backgroundSyncRetriesAfterFailure() throws Exception {
        AtomicInteger rounds = new AtomicInteger();
        CountDownLatch repeated = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        try (var node = new Node(new Node.Network()) {
            @Override
            public void syncKnownPeers() {
                if (rounds.incrementAndGet() == 1)
                    throw new IllegalStateException("Temporary sync failure");
                repeated.countDown();
            }
        }) {
            var app = new NodeDesktopApp(node, Path.of("."), stopped::countDown, Duration.ofMillis(10));
            try {
                assertTrue(repeated.await(5, TimeUnit.SECONDS));
                assertTrue(rounds.get() >= 2);
            } finally {
                app.close();
                assertTrue(stopped.await(5, TimeUnit.SECONDS));
            }
        }
    }

    /** Verifies shutdown waits for the active round and cancels future rounds. */
    @Test
    void closeWaitsForActiveSync() throws Exception {
        AtomicInteger rounds = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        try (var node = new Node(new Node.Network()) {
            @Override
            public void syncKnownPeers() {
                rounds.incrementAndGet();
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
        }) {
            var app = new NodeDesktopApp(node, Path.of("."), stopped::countDown, Duration.ofMillis(10));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                app.close();
                app.close();
                assertEquals(1, stopped.getCount());
            } finally {
                release.countDown();
                app.close();
                assertTrue(stopped.await(5, TimeUnit.SECONDS));
            }
            assertEquals(1, rounds.get());
        }
    }
}