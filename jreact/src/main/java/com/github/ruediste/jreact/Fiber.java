package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Retains one component's identity, hook slots, and child components across
 * renders, independently of the transient {@link Node} tree.
 *
 * <p>
 * Resets render cursors on entry, resolves hooks by call order, and rejects
 * changes in hook count or kind. Child fibers are reconciled by component type
 * and explicit key, or by type and sibling position when no key is supplied.
 * Finishing a render disposes children that were not visited; disposal disables
 * retained state and runs effect cleanup throughout the component subtree.
 *
 * <p>
 * This is runtime bookkeeping, not a layout or painting node. It is confined
 * to its owning {@link RenderRoot}'s thread.
 */
final class Fiber {
    /** Identifies a child component within its parent fiber. */
    record Identity(Object type, Object key) {
    }

    /** Keeps implicit sibling positions distinct from explicit user keys. */
    record Position(int index) {
    }

    final List<Object> hooks = new ArrayList<>();

    /**
     * Child fibers keyed by their identity within this fiber. These represent the
     * retained state of child components across renders. During render, existing
     * children are reused and new children are added to the map. All children not
     * seen during the render are disposed.
     */
    final Map<Identity, Fiber> children = new LinkedHashMap<>();

    /**
     * The ids of the child components which have been seen during the current
     * render
     */
    final Set<Identity> seen = new HashSet<>();
    int cursor;
    int childCursor;
    int nodeCursor;
    boolean rendered;

    void begin() {
        cursor = 0;
        childCursor = 0;
        nodeCursor = 0;
        seen.clear();
    }

    Object slot(Class<?> kind, Supplier<Object> initial) {
        if (cursor == hooks.size()) {
            if (rendered)
                throw new IllegalStateException("Hook count changed; hooks must be unconditional");
            hooks.add(initial.get());
        }
        Object slot = hooks.get(cursor++);
        if (!kind.isInstance(slot))
            throw new IllegalStateException("Hook order changed; expected " + slot.getClass().getSimpleName());
        return slot;
    }

    void finish() {
        if (cursor != hooks.size())
            throw new IllegalStateException("Hook count changed; hooks must be unconditional");
        rendered = true;
        RuntimeException failure = null;

        // Dispose children which have not been seen during this render
        var iterator = children.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (seen.contains(entry.getKey()))
                continue;
            iterator.remove();
            try {
                entry.getValue().dispose();
            } catch (RuntimeException exception) {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }
        if (failure != null)
            throw failure;
    }

    void dispose() {
        RuntimeException failure = null;
        for (Fiber child : children.values()) {
            try {
                child.dispose();
            } catch (RuntimeException exception) {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed(exception);
            }
        }
        for (Object slot : hooks) {
            if (slot instanceof State<?> state)
                state.unmount();
            if (slot instanceof Hooks.Effect effect) {
                try {
                    effect.clean();
                } catch (RuntimeException exception) {
                    if (failure == null)
                        failure = exception;
                    else
                        failure.addSuppressed(exception);
                }
            }
        }
        children.clear();
        if (failure != null)
            throw failure;
    }
}