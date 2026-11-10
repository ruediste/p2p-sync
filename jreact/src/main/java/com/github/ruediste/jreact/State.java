package com.github.ruediste.jreact;

import java.util.Objects;
import java.util.function.UnaryOperator;

import com.github.ruediste.jreact.Hooks.Hook;

/**
 * A mutable value retained by a component's state hook.
 *
 * <p>
 * Writes that differ according to {@link Objects#equals(Object, Object)} are
 * validated before assignment and mark the owning component's fiber dirty.
 * The root rejects changed-value writes from another thread or during component
 * rendering. This handle is not a scheduler: the host controls when rendering
 * starts again.
 *
 * <p>
 * Unmounting disables subsequent assignments through retained handles,
 * preventing stale callbacks from changing disposed component state.
 *
 * @param <T> the stored value type
 */
public final class State<T> implements Hook {
    private T value;
    private final Runnable invalidate;
    private final Runnable validate;
    private boolean mounted = true;

    State(T value, Runnable invalidate, Runnable validate) {
        this.value = value;
        this.invalidate = invalidate;
        this.validate = validate;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        if (mounted && !Objects.equals(this.value, value)) {
            validate.run();
            this.value = value;
            invalidate.run();
        }
    }

    public void update(UnaryOperator<T> update) {
        set(update.apply(value));
    }

    @Override
    public void close() {
        mounted = false;
    }
}