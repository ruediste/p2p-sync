package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class Fiber {

    private final List<Hooks.Hook> hooks = new ArrayList<>();
    private Fiber parent;
    private int hookCursor;
    private boolean dirty;
    private boolean subtreeDirty;

    void setParent(Fiber parent) {
        this.parent = parent;
    }

    void beginRender() {
        hookCursor = 0;
    }

    void markDirty() {
        dirty = true;
        for (Fiber fiber = this; fiber != null; fiber = fiber.parent)
            fiber.subtreeDirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    boolean isSubtreeDirty() {
        return subtreeDirty;
    }

    void clearAfterRender() {
        dirty = false;
        subtreeDirty = false;
    }

    <H extends Hooks.Hook> H use(Class<H> type, Supplier<H> factory) {
        int index = hookCursor++;
        if (index < hooks.size()) {
            Hooks.Hook existing = hooks.get(index);
            if (!type.isInstance(existing))
                throw new IllegalStateException("Hook order changed at slot " + index);
            return type.cast(existing);
        }
        H hook = Objects.requireNonNull(factory.get());
        hooks.add(hook);
        return hook;
    }

    void dispose() {
        RuntimeException failure = null;
        for (Hooks.Hook hook : hooks) {
            try {
                hook.close();
            } catch (RuntimeException exception) {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }
        hooks.clear();
        hookCursor = 0;
        dirty = false;
        subtreeDirty = false;
        parent = null;
        if (failure != null)
            throw failure;
    }

    int hookCount() {
        return hooks.size();
    }
}
