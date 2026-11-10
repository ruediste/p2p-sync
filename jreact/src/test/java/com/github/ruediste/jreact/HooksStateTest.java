package com.github.ruediste.jreact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class HooksStateTest {

    @Test
    void stateSlotsAreStableAndWritesAreValidated() throws Exception {
        JReactRoot root = new JReactRoot(() -> {
        });
        Ui.Context previous = Ui.installContext(root.context());
        try {
            Fiber fiber = new Fiber();
            root.context().fiber = fiber;

            fiber.beginRender();
            State<String> state = Hooks.useState("a");
            fiber.beginRender();
            assertSame(state, Hooks.useState("ignored"));

            fiber.clearAfterRender();
            state.set("b");
            assertTrue(fiber.isDirty());
            assertEquals("b", state.get());

            root.beginRender();
            try {
                assertThrows(IllegalStateException.class, () -> state.set("c"));
            } finally {
                root.endRender();
            }

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try {
                    state.set("d");
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            thread.start();
            thread.join();
            assertTrue(failure.get() instanceof IllegalStateException);

            Fiber disposed = new Fiber();
            root.context().fiber = disposed;
            disposed.beginRender();
            State<String> disposedState = Hooks.useState("x");
            disposed.dispose();
            disposedState.set("y");
            assertEquals("x", disposedState.get());
        } finally {
            Ui.restoreContext(previous);
        }
    }

    @Test
    void memoAndCleanupFollowHookOrder() {
        JReactRoot root = new JReactRoot(() -> {
        });
        Ui.Context previous = Ui.installContext(root.context());
        try {
            Fiber fiber = new Fiber();
            root.context().fiber = fiber;
            int[] calls = {0};

            fiber.beginRender();
            String first = Hooks.useMemo(() -> "v" + calls[0]++, "dep");
            fiber.beginRender();
            String second = Hooks.useMemo(() -> "v" + calls[0]++, "dep");
            fiber.beginRender();
            String third = Hooks.useMemo(() -> "v" + calls[0]++, "other");

            assertEquals(first, second);
            assertEquals("v1", third);
            assertEquals(2, calls[0]);

            int[] cleanups = {0};
            Fiber cleanupFiber = new Fiber();
            root.context().fiber = cleanupFiber;
            cleanupFiber.beginRender();
            Hooks.useCleanup(() -> cleanups[0]++);
            cleanupFiber.beginRender();
            Hooks.useCleanup(() -> cleanups[0]++);
            assertEquals(1, cleanups[0]);
            cleanupFiber.dispose();
            assertEquals(2, cleanups[0]);
        } finally {
            Ui.restoreContext(previous);
        }
    }
}
