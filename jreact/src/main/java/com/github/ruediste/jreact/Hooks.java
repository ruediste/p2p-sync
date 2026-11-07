package com.github.ruediste.jreact;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Provides component-local state, effects, references, and memoized values.
 *
 * <p>
 * Each call resolves the next slot in the active {@link Fiber}. Hooks require
 * a rendering component and must be called in the same order and with the same
 * kinds on every render. They do not create a component boundary themselves.
 *
 * <p>
 * State changes invalidate the owning {@link RenderRoot}; reference changes
 * do not. Memo factories run during rendering, whereas effect setup runs after
 * the node tree is published. Effect cleanup runs before replacement setup and
 * on unmount. Memo and effect dependencies are compared with
 * {@link Arrays#deepEquals(Object[], Object[])}; callers should treat
 * dependency
 * objects as immutable. An empty dependency list retains a memo or effect for
 * the component's mounted lifetime.
 */
public final class Hooks {
    private Hooks() {
    }

    public static <T> State<T> useState(T initial) {
        return useStateLazy(() -> initial);
    }

    @SuppressWarnings("unchecked")
    public static <T> State<T> useStateLazy(Supplier<T> initial) {
        var context = Ui.context();
        return (State<T>) context.fiber.slot(State.class,
                () -> new State<>(initial.get(), context.runtime::invalidate, context.runtime::checkUpdate));
    }

    public static void useEffect(Supplier<Runnable> effect, Object... dependencies) {
        var context = Ui.context();
        var slot = (Effect) context.fiber.slot(Effect.class, Effect::new);
        if (!slot.initialized || !Arrays.deepEquals(slot.dependencies, dependencies)) {
            Object[] snapshot = dependencies.clone();
            context.runtime.effects.add(() -> {
                slot.clean();
                slot.dependencies = snapshot;
                slot.initialized = true;
                slot.cleanup = effect.get();
            });
        }
    }

    public static <T> Ref<T> useRef(T initial) {
        var context = Ui.context();
        @SuppressWarnings("unchecked")
        var ref = (Ref<T>) context.fiber.slot(Ref.class, () -> new Ref<>(initial));
        return ref;
    }

    public static <T> T useMemo(Supplier<T> factory, Object... dependencies) {
        var context = Ui.context();
        var memo = (Memo) context.fiber.slot(Memo.class, Memo::new);
        if (!memo.initialized || !Arrays.deepEquals(memo.dependencies, dependencies)) {
            memo.value = factory.get();
            memo.dependencies = dependencies.clone();
            memo.initialized = true;
        }
        @SuppressWarnings("unchecked")
        T result = (T) memo.value;
        return result;
    }

    /**
     * Retains a mutable value across component renders without invalidating the
     * render root when {@link #current} changes.
     *
     * @param <T> the referenced value type
     */
    public static final class Ref<T> {
        public T current;

        Ref(T value) {
            current = value;
        }
    }

    /** Stores a memoized result and the dependencies used to compute it. */
    static final class Memo {
        Object value;
        Object[] dependencies;
        boolean initialized;
    }

    /** Stores an effect's dependencies and owns its current cleanup callback. */
    static final class Effect {
        Runnable cleanup;
        Object[] dependencies;
        boolean initialized;

        void clean() {
            var previous = cleanup;
            cleanup = null;
            if (previous != null)
                previous.run();
        }
    }
}