package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.github.ruediste.jreact.nodes.DivNode;

/**
 * Owns a component tree's synchronous rendering and lifecycle.
 *
 * <p>
 * Retains the root {@link Fiber} across renders, builds a fresh {@link Node}
 * tree, resolves its styles, and publishes it before running queued effects.
 * State and viewport changes mark the root dirty and notify the host through
 * the
 * invalidation callback; the host decides when to call {@link #render()}.
 * Layout is the responsibility of {@link com.github.ruediste.jreact.layout.LayoutEngine};
 * painting lives in the {@link Node} hierarchy.
 *
 * <p>
 * Rendering, viewport changes, state updates, and disposal belong to the
 * creating thread. A root hosted by {@link JReactPanel} therefore belongs to
 * the
 * Swing event dispatch thread. Closing the root unmounts its fibers and cleans
 * up their effects.
 */
public final class RenderRoot implements AutoCloseable {
    private final Fiber rootFiber = new Fiber();
    private final Thread owner = Thread.currentThread();
    private final Runnable body;
    private final Runnable onInvalidated;
    final List<Runnable> effects = new ArrayList<>();
    private Node tree;

    /**
     * Node that receives primitives emitted by the component body currently
     * being rendered.
     *
     * <p>
     * {@link #render()} initializes it to the synthetic root node before
     * running the body and resets it to {@code null} afterwards. While a
     * container primitive renders its children, it temporarily replaces this
     * with its own node, and each primitive creates its node under the current
     * value. Nested component calls share the same node parent, so component
     * boundaries do not introduce visual containers.
     */
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
        Node nextTreeRoot = new DivNode(new Node.Key(rootFiber, -1), new Style(), List.of());

        // initialize the parent node to the synthetic root node
        parent = nextTreeRoot;

        try {
            Ui.Context previous = Ui.CURRENT.get();
            rootFiber.begin();
            Ui.CURRENT.set(new Ui.Context(this, rootFiber));
            try {
                body.run();
                rootFiber.finish();
            } finally {
                if (previous == null)
                    Ui.CURRENT.remove();
                else
                    Ui.CURRENT.set(previous);
            }
            Styles.resolve(nextTreeRoot);
            tree = nextTreeRoot;
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
            rootFiber.dispose();
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