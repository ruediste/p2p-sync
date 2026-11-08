package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
 * Layout is the responsibility of
 * {@link com.github.ruediste.jreact.layout.LayoutEngine};
 * painting lives in the {@link Node} hierarchy.
 *
 * <p>
 * Rendering, viewport changes, state updates, and disposal belong to the
 * creating thread. A root hosted by {@link JReactPanel} therefore belongs to
 * the
 * Swing event dispatch thread. Closing the root unmounts its fibers and cleans
 * up their effects.
 */
public final class JReactRoot implements AutoCloseable {
    private final Fiber rootFiber = new Fiber();
    private final Thread owner = Thread.currentThread();
    private final Runnable body;
    private final Runnable onInvalidated;
    final List<Runnable> pendingEffects = new ArrayList<>();
    private Node treeRoot;
    public Style rootStyle = new Style();

    private LinkedHashSet<Consumer<Runnable>> overlays = new LinkedHashSet<>();

    /**
     * Node that receives primitives emitted by the component body currently
     * being rendered. While rendering the component body, this points to the
     * current parent node under which new nodes are added.
     * 
     * todo: likely a good idea to replace with a callback receiving the nodes being
     * created
     */
    Node currentParentNode;

    double viewportWidth, viewportHeight;
    private boolean dirty = true;
    private boolean rendering;
    private boolean closed;

    public JReactRoot(Runnable body, Runnable onInvalidated) {
        this.body = Objects.requireNonNull(body);
        this.onInvalidated = Objects.requireNonNull(onInvalidated);
    }

    public boolean isDirty() {
        return dirty;
    }

    public Node tree() {
        return treeRoot;
    }

    public Runnable addOverlay(Consumer<Runnable> overlay) {
        overlays.add(overlay);
        return () -> overlays.remove(overlay);
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
        pendingEffects.clear();
        dirty = false;

        var context = new Ui.Context(this, rootFiber);
        Node nextTreeRoot = new DivNode(new Node.Key(rootFiber, -1), rootStyle, context.styleRuleChain);

        // initialize the parent node to the synthetic root node
        currentParentNode = nextTreeRoot;

        try {
            Ui.Context previous = Ui.CURRENT.get();
            rootFiber.begin();

            Ui.CURRENT.set(new Ui.Context(this, rootFiber));
            try {
                body.run();
                for (Consumer<Runnable> overlay : overlays) {
                    overlay.accept(() -> overlays.remove(overlay));
                }
                rootFiber.finish();
            } finally {
                if (previous == null)
                    Ui.CURRENT.remove();
                else
                    Ui.CURRENT.set(previous);
            }
            Styles.resolve(nextTreeRoot);
            treeRoot = nextTreeRoot;
            List.copyOf(pendingEffects).forEach(Runnable::run);
        } catch (RuntimeException | Error exception) {
            dirty = true;
            throw exception;
        } finally {
            pendingEffects.clear();
            currentParentNode = null;
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