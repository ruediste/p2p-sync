package com.github.ruediste.jreact;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public final class Hooks {

    private Hooks() {
    }

    public interface Hook extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface EffectSetup {
        Runnable setup();
    }

    @SuppressWarnings("unchecked")
    public static <T> State<T> useState(T initialValue) {
        Fiber fiber = fiber();
        JReactRoot root = Ui.context().root;
        return (State<T>) fiber.use(State.class,
                () -> new State<Object>(initialValue, () -> {
                    fiber.markDirty();
                    root.requestRefresh();
                }, root::validateStateWrite));
    }

    @SuppressWarnings("unchecked")
    public static <T> T useMemo(Supplier<T> factory, Object... dependencies) {
        Objects.requireNonNull(factory);
        Objects.requireNonNull(dependencies);
        Memo<T> memo = (Memo<T>) use(Memo.class, Memo::new);
        if (!memo.initialized || !Arrays.equals(memo.dependencies, dependencies)) {
            memo.value = factory.get();
            memo.dependencies = dependencies.clone();
            memo.initialized = true;
        }
        return memo.value;
    }

    public static void useCleanup(Runnable cleanup) {
        Objects.requireNonNull(cleanup);
        Cleanup hook = use(Cleanup.class, Cleanup::new);
        hook.replace(cleanup);
    }

    public static void useEffect(Runnable setup, Object... dependencies) {
        Objects.requireNonNull(setup);
        useEffect(() -> {
            setup.run();
            return null;
        }, false, dependencies);
    }

    public static void useEffect(EffectSetup setup, Object... dependencies) {
        useEffect(setup, false, dependencies);
    }

    public static void useLayoutEffect(Runnable setup, Object... dependencies) {
        Objects.requireNonNull(setup);
        useEffect(() -> {
            setup.run();
            return null;
        }, true, dependencies);
    }

    public static void useLayoutEffect(EffectSetup setup, Object... dependencies) {
        useEffect(setup, true, dependencies);
    }

    private static void useEffect(EffectSetup setup, boolean layout, Object[] dependencies) {
        Objects.requireNonNull(setup);
        Objects.requireNonNull(dependencies);
        JReactRoot root = Ui.context().root;
        Effect hook;
        if (layout)
            hook = use(LayoutEffect.class, LayoutEffect::new);
        else
            hook = use(PassiveEffect.class, PassiveEffect::new);
        hook.update(setup, dependencies, root);
    }

    static <H extends Hook> H use(Class<H> type, Supplier<H> factory) {
        return fiber().use(type, factory);
    }

    static Fiber fiber() {
        Fiber fiber = Ui.context().fiber;
        if (fiber == null)
            throw new IllegalStateException("No active component fiber");
        return fiber;
    }

    private static final class Memo<T> implements Hook {
        private T value;
        private Object[] dependencies;
        private boolean initialized;

        @Override
        public void close() {
            value = null;
            dependencies = null;
            initialized = false;
        }
    }

    private static final class Cleanup implements Hook {
        private Runnable cleanup;

        void replace(Runnable next) {
            Runnable previous = cleanup;
            cleanup = next;
            if (previous != null)
                previous.run();
        }

        @Override
        public void close() {
            Runnable previous = cleanup;
            cleanup = null;
            if (previous != null)
                previous.run();
        }
    }

    abstract static class Effect implements Hook {
        private final boolean layout;
        private EffectSetup setup;
        private Object[] dependencies;
        private Runnable cleanup;
        private boolean pending;
        private boolean mounted = true;

        Effect(boolean layout) {
            this.layout = layout;
        }

        void update(EffectSetup setup, Object[] dependencies, JReactRoot root) {
            if (Arrays.equals(this.dependencies, dependencies))
                return;
            this.setup = setup;
            this.dependencies = dependencies.clone();
            boolean wasPending = pending;
            pending = true;
            if (wasPending)
                return;
            if (layout)
                root.enqueueLayoutEffect(this);
            else
                root.enqueuePassiveEffect(this);
        }

        void run() {
            if (!mounted || !pending)
                return;
            pending = false;
            runCleanup();
            if (!mounted)
                return;
            Runnable next = setup.setup();
            if (mounted)
                cleanup = next;
            else if (next != null)
                next.run();
        }

        @Override
        public void close() {
            mounted = false;
            pending = false;
            runCleanup();
        }

        private void runCleanup() {
            Runnable previous = cleanup;
            cleanup = null;
            if (previous != null)
                previous.run();
        }
    }

    private static final class LayoutEffect extends Effect {
        LayoutEffect() {
            super(true);
        }
    }

    private static final class PassiveEffect extends Effect {
        PassiveEffect() {
            super(false);
        }
    }
}
