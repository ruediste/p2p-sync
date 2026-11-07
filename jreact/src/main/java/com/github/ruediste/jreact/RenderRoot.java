package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns a component tree's synchronous rendering and lifecycle.
 *
 * <p>
 * Retains the root {@link Fiber} across renders, builds a fresh {@link Node}
 * tree, resolves its styles, and publishes it before running queued effects.
 * State and viewport changes mark the root dirty and notify the host through
 * the
 * invalidation callback; the host decides when to call {@link #render()}.
 * Layout and painting are separate responsibilities of {@link FlexLayout} and
 * {@link Renderer}.
 *
 * <p>
 * Rendering, viewport changes, state updates, and disposal belong to the
 * creating thread. A root hosted by {@link JReactPanel} therefore belongs to
 * the
 * Swing event dispatch thread. Closing the root unmounts its fibers and cleans
 * up their effects.
 */
public final class RenderRoot implements AutoCloseable {
    private final Fiber root = new Fiber();
    private final Thread owner = Thread.currentThread();
    private final Runnable body;
    private final Runnable onInvalidated;
    final List<Runnable> effects = new ArrayList<>();
    private Node tree;
    Node parent;
    double viewportWidth, viewportHeight;
    private boolean dirty = true;
    private boolean rendering;
    private boolean closed;

    public RenderRoot(Runnable body, Runnable onInvalidated) {
        this.body = Objects.requireNonNull(body);
        this.onInvalidated = Objects.requireNonNull(onInvalidated);
    }

    public boolean isDirty() {
        return dirty;
    }

    public Node tree() {
        return tree;
    }

    public void viewport(double width, double height) {
        checkThread();
        Style.nonNegative(width);
        Style.nonNegative(height);
        if (viewportWidth != width || viewportHeight != height) {
            viewportWidth = width;
            viewportHeight = height;
            invalidate();
        }
    }

    void invalidate() {
        if (!closed) {
            dirty = true;
            onInvalidated.run();
        }
    }

    public void render() {
        checkThread();
        if (closed)
            throw new IllegalStateException("Root is closed");
        if (rendering)
            throw new IllegalStateException("Cannot render reentrantly");
        rendering = true;
        effects.clear();
        dirty = false;
        Node next = new Node(Node.Kind.DIV, new Node.Key(root, -1), null, new Style(), List.of());
        parent = next;
        try {
            Ui.render(this, root, body);
            Styles.resolve(next);
            tree = next;
            List.copyOf(effects).forEach(Runnable::run);
        } catch (RuntimeException | Error exception) {
            dirty = true;
            throw exception;
        } finally {
            effects.clear();
            parent = null;
            rendering = false;
        }
    }

    @Override
    public void close() {
        checkThread();
        if (!closed) {
            closed = true;
            root.dispose();
        }
    }

    private void checkThread() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("RenderRoot and its state belong to their creating thread");
    }

    void checkUpdate() {
        checkThread();
        var context = Ui.CURRENT.get();
        if (context != null && context.runtime == this)
            throw new IllegalStateException("State cannot be updated during component rendering");
    }
}