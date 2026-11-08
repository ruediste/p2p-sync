package com.github.ruediste.jreact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Verifies hook identity and lifecycle by rerendering, reordering, and
 * unmounting components.
 */
class RuntimeTest {
    /**
     * Verifies uppercase defaults, annotation overrides, and annotation-required
     * matching.
     */
    @Test
    void componentMatcherHonorsNamesAndAnnotationOverrides() throws Exception {
        var automaticType = new TypeDescription.ForLoadedType(AgentTargets.Automatic.class);
        var automaticMethod = method(AgentTargets.Automatic.class, "Child");
        assertTrue(JReactAgent.shouldTransform(automaticType, automaticMethod, false));
        assertFalse(JReactAgent.shouldTransform(automaticType, automaticMethod, true));

        var disabledType = new TypeDescription.ForLoadedType(AgentTargets.DisabledClass.class);
        assertFalse(JReactAgent.shouldTransform(disabledType,
                method(AgentTargets.DisabledClass.class, "Child"), false));
        assertFalse(JReactAgent.shouldTransform(disabledType,
                method(AgentTargets.DisabledClass.class, "optedIn"), false));

        var explicitType = new TypeDescription.ForLoadedType(AgentTargets.ExplicitClass.class);
        assertTrue(JReactAgent.shouldTransform(explicitType,
                method(AgentTargets.ExplicitClass.class, "child"), true));
        assertTrue(JReactAgent.shouldTransform(explicitType,
                method(AgentTargets.ExplicitClass.class, "optedIn"), true));
        assertFalse(JReactAgent.shouldTransform(explicitType,
                method(AgentTargets.ExplicitClass.class, "Child"), false));

        var methodType = new TypeDescription.ForLoadedType(AgentTargets.MethodAnnotations.class);
        assertTrue(JReactAgent.shouldTransform(methodType,
                method(AgentTargets.MethodAnnotations.class, "optedIn"), true));
        assertFalse(JReactAgent.shouldTransform(methodType,
                method(AgentTargets.MethodAnnotations.class, "Child"), false));
    }

    private static MethodDescription method(Class<?> owner, String name) throws NoSuchMethodException {
        return new MethodDescription.ForLoadedMethod(owner.getDeclaredMethod(name));
    }

    @SuppressWarnings("unused")
    private static final class AgentTargets {
        static class Automatic {
            static void Child() {
            }
        }

        @NoJReactComponent
        static class DisabledClass {
            static void Child() {
            }

            @JReactComponent
            static void optedIn() {
            }
        }

        @JReactComponent
        static class ExplicitClass {
            static void child() {
            }

            @JReactComponent
            static void optedIn() {
            }

            @NoJReactComponent
            static void Child() {
            }
        }

        static class MethodAnnotations {
            @JReactComponent
            static void optedIn() {
            }

            @NoJReactComponent
            static void Child() {
            }
        }
    }

    @Test
    void stateRetainsValueAndOnlyInvalidatesOnChange() {
        var state = new AtomicReference<State<Integer>>();
        var invalidations = new AtomicInteger();
        try (var root = new JReactRoot(() -> state.set(Hooks.useState(0)), invalidations::incrementAndGet)) {
            root.render();
            state.get().set(0);
            assertEquals(0, invalidations.get());
            state.get().update(value -> value + 2);
            assertTrue(root.isDirty());
            root.render();
            assertEquals(2, state.get().get());
            assertEquals(1, invalidations.get());
        }
        state.get().set(3);
        assertEquals(1, invalidations.get());
    }

    @Test
    void lazyInitializerRunsOnce() {
        var calls = new AtomicInteger();
        try (var root = new JReactRoot(() -> Hooks.useStateLazy(calls::incrementAndGet), () -> {
        })) {
            root.render();
            root.render();
            assertEquals(1, calls.get());
        }
    }

    @Test
    void keyedChildrenRetainStateOnReorderAndResetOnRemoval() {
        var keys = new ArrayList<>(List.of("a", "b"));
        var states = new java.util.HashMap<String, State<String>>();
        try (var root = new JReactRoot(() -> keys.forEach(key -> Ui.component("item", key,
                () -> states.put(key, Hooks.useState(key)))), () -> {
                })) {
            root.render();
            states.get("a").set("edited");
            java.util.Collections.reverse(keys);
            root.render();
            assertEquals("edited", states.get("a").get());
            keys.remove("a");
            root.render();
            keys.add("a");
            root.render();
            assertEquals("a", states.get("a").get());
        }
    }

    @Test
    void effectsRunAfterRenderAndCleanBeforeReplacementAndUnmount() {
        var events = new ArrayList<String>();
        var dependency = new AtomicInteger();
        var root = new JReactRoot(() -> {
            int value = dependency.get();
            Hooks.useEffect(() -> {
                events.add("effect" + value);
                return () -> events.add("clean" + value);
            }, value);
            events.add("render");
        }, () -> {
        });
        root.render();
        root.render();
        dependency.incrementAndGet();
        root.render();
        root.close();
        root.close();
        assertEquals(List.of("render", "effect0", "render", "render", "clean0", "effect1", "clean1"), events);
    }

    @Test
    void removedChildCleansEffect() {
        var visible = new AtomicReference<>(true);
        var cleans = new AtomicInteger();
        try (var root = new JReactRoot(() -> {
            if (visible.get())
                Ui.component("child", null, () -> Hooks.useEffect(() -> cleans::incrementAndGet));
        }, () -> {
        })) {
            root.render();
            visible.set(false);
            root.render();
            assertEquals(1, cleans.get());
        }
    }

    @Test
    void changedHookCountIsRejectedAndContextIsRestored() {
        var extra = new AtomicReference<>(false);
        try (var root = new JReactRoot(() -> {
            Hooks.useState(1);
            if (extra.get())
                Hooks.useState(2);
        }, () -> {
        })) {
            root.render();
            extra.set(true);
            assertThrows(IllegalStateException.class, root::render);
            assertThrows(IllegalStateException.class, () -> Hooks.useState(1));
        }
    }

    @Test
    void fewerHooksAndChangedKindsAreRejected() {
        var mode = new AtomicInteger();
        try (var root = new JReactRoot(() -> {
            if (mode.get() == 0)
                Hooks.useState(1);
            else if (mode.get() == 2)
                Hooks.useRef(1);
        }, () -> {
        })) {
            root.render();
            mode.set(1);
            assertThrows(IllegalStateException.class, root::render);
            mode.set(2);
            assertThrows(IllegalStateException.class, root::render);
        }
    }

    @Test
    void nestedComponentsRestoreParentCursor() {
        try (var root = new JReactRoot(() -> {
            assertEquals("first", Hooks.useState("first").get());
            Ui.component("child", null, () -> assertEquals("child", Hooks.useState("child").get()));
            assertEquals("last", Hooks.useState("last").get());
        }, () -> {
        })) {
            root.render();
            root.render();
        }
    }

    /** Verifies a failed explicit component restores its parent's hook context. */
    @Test
    void explicitComponentRestoresParentContextWhenBodyThrows() {
        var fail = new AtomicReference<>(true);
        var childFailure = new IllegalStateException("child");
        var parentState = new AtomicReference<State<String>>();
        try (var root = new JReactRoot(() -> {
            Hooks.useState("before");
            try {
                Ui.component("child", null, () -> {
                    Hooks.useState("child");
                    if (fail.get())
                        throw childFailure;
                });
            } catch (IllegalStateException exception) {
                assertSame(childFailure, exception);
            }
            parentState.set(Hooks.useState("after"));
        }, () -> {
        })) {
            root.render();
            assertEquals("after", parentState.get().get());
            fail.set(false);
            assertDoesNotThrow(root::render);
        }
    }

    @Test
    void duplicateKeysAreRejected() {
        try (var root = new JReactRoot(() -> {
            Ui.component("item", "same", () -> {
            });
            Ui.component("item", "same", () -> {
            });
        }, () -> {
        })) {
            assertThrows(IllegalArgumentException.class, root::render);
        }
    }

    @Test
    void memoAndRefRetainIdentity() {
        var dependency = new AtomicInteger();
        var calls = new AtomicInteger();
        var refs = new ArrayList<Hooks.Ref<String>>();
        try (var root = new JReactRoot(() -> {
            refs.add(Hooks.useRef("initial"));
            Hooks.useMemo(calls::incrementAndGet, dependency.get());
        }, () -> {
        })) {
            root.render();
            refs.getFirst().current = "changed";
            root.render();
            assertSame(refs.getFirst(), refs.getLast());
            assertEquals("changed", refs.getLast().current);
            assertEquals(1, calls.get());
            dependency.incrementAndGet();
            root.render();
            assertEquals(2, calls.get());
        }
    }

    @Test
    void closedAndReentrantRootsAreRejected() {
        var holder = new AtomicReference<JReactRoot>();
        var root = new JReactRoot(() -> holder.get().render(), () -> {
        });
        holder.set(root);
        assertThrows(IllegalStateException.class, root::render);
        root.close();
        assertThrows(IllegalStateException.class, root::render);
    }

    @Test
    void implicitPositionsDoNotCollideWithExplicitIntegerKeys() {
        try (var root = new JReactRoot(() -> {
            Ui.component("item", null, () -> Hooks.useState(1));
            Ui.component("item", 0, () -> Hooks.useState(2));
        }, () -> {
        })) {
            assertDoesNotThrow(root::render);
        }
    }

    @Test
    void renderPhaseUpdatesAreRejectedButEffectsMayUpdateState() {
        try (var root = new JReactRoot(() -> Hooks.useState(0).set(1), () -> {
        })) {
            assertThrows(IllegalStateException.class, root::render);
        }
        var state = new AtomicReference<State<Integer>>();
        try (var root = new JReactRoot(() -> {
            state.set(Hooks.useState(0));
            Hooks.useEffect(() -> {
                state.get().set(1);
                return null;
            });
        }, () -> {
        })) {
            root.render();
            assertTrue(root.isDirty());
            root.render();
            assertEquals(1, state.get().get());
        }
    }

    @Test
    void crossThreadUpdatesAreRejectedBeforeMutatingValue() throws InterruptedException {
        var state = new AtomicReference<State<Integer>>();
        var failure = new AtomicReference<Throwable>();
        try (var root = new JReactRoot(() -> state.set(Hooks.useState(0)), () -> {
        })) {
            root.render();
            var thread = new Thread(() -> {
                try {
                    state.get().set(1);
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            thread.start();
            thread.join();
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertEquals(0, state.get().get());
        }
    }

    @Test
    void cleanupFailureDoesNotPreventOtherCleanupsOrUnmountingState() {
        var state = new AtomicReference<State<Integer>>();
        var calls = new AtomicInteger();
        var root = new JReactRoot(() -> {
            Hooks.useEffect(() -> () -> {
                throw new IllegalArgumentException("cleanup");
            });
            state.set(Hooks.useState(0));
            Hooks.useEffect(() -> calls::incrementAndGet);
        }, () -> {
        });
        root.render();
        assertThrows(IllegalArgumentException.class, root::close);
        assertEquals(1, calls.get());
        state.get().set(1);
        assertEquals(0, state.get().get());
    }

    @Test
    void removedChildrenAreAllDisposedEvenIfOneCleanupFails() {
        var visible = new AtomicReference<>(true);
        var cleans = new AtomicInteger();
        try (var root = new JReactRoot(() -> {
            if (visible.get()) {
                Ui.component("first", null, () -> Hooks.useEffect(() -> () -> {
                    throw new IllegalArgumentException("cleanup");
                }));
                Ui.component("second", null, () -> Hooks.useEffect(() -> cleans::incrementAndGet));
            }
        }, () -> {
        })) {
            root.render();
            visible.set(false);
            assertThrows(IllegalArgumentException.class, root::render);
            assertEquals(1, cleans.get());
            assertDoesNotThrow(root::render);
        }
    }
}