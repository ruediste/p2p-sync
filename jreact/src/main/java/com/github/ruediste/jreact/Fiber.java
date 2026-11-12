package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.github.ruediste.jreact.nodes.ComponentNode;

public final class Fiber {

    private final List<Hooks.Hook> hooks = new ArrayList<>();
    private int hookCursor;
    public boolean dirty;
    public ComponentNode component;

    void beginRender() {
        hookCursor = 0;
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
        component = null;
        if (failure != null)
            throw failure;
    }

    void clearAfterRender() {
        dirty = false;
    }

    int hookCount() {
        return hooks.size();
    }
}
