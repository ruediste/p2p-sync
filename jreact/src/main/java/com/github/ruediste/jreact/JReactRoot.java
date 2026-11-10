package com.github.ruediste.jreact;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.antepod.taffy.style.Display;
import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.events.KeyboardEvent;
import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.Node;
import com.github.ruediste.jreact.nodes.VisualNode;

public final class JReactRoot {

    private final Ui.Context context = new Ui.Context(this);
    private final Thread ownerThread = Thread.currentThread();
    private final Runnable rootRender;
    private final Fiber rootFiber = new Fiber();
    private final List<Hooks.Effect> layoutEffects = new ArrayList<>();
    private final List<Hooks.Effect> passiveEffects = new ArrayList<>();

    private ComponentNode rootNode;
    private float viewportWidth;
    private float viewportHeight;
    private boolean rendering;
    private Runnable invalidationCallback = () -> {
    };
    private List<Node.Identity> hoverPath = List.of();
    private List<Node.Identity> activePath = List.of();
    private List<Node.Identity> focusPath = List.of();
    private List<Node.Identity> pressPath;
    private double pointerX;
    private double pointerY;
    private boolean pointerInside;

    public JReactRoot(Runnable rootRender) {
        this.rootRender = Objects.requireNonNull(rootRender);
    }

    Ui.Context context() {
        return context;
    }

    public Node tree() {
        return rootNode;
    }

    public boolean needsRender() {
        return rootNode == null || rootFiber.isSubtreeDirty();
    }

    public void setInvalidationCallback(Runnable callback) {
        invalidationCallback = callback == null ? () -> {
        } : callback;
    }

    public void render() {
        refresh();
    }

    public RefreshResult refresh() {
        validateThread();
        if (rendering)
            throw new IllegalStateException("A render is already in progress");

        Ui.Context previousContext = Ui.installContext(context);
        try {
            beginRender();
            try {
                renderComponents();
            } finally {
                endRender();
            }

            applyInteractionState();
            Styles.resolve(rootNode);
            com.github.ruediste.jreact.layout.LayoutEngine.layout(rootNode, viewportWidth, viewportHeight);

            if (pointerInside) {
                List<Node.Identity> hit = hitPath(pointerX, pointerY);
                if (!Objects.equals(hit, hoverPath)) {
                    hoverPath = hit;
                    applyInteractionState();
                    Styles.resolve(rootNode);
                    com.github.ruediste.jreact.layout.LayoutEngine.layout(rootNode, viewportWidth, viewportHeight);
                }
            }

            runEffects(layoutEffects);

            Node focused = findByPath(focusPath);
            return new RefreshResult(rootNode, index(rootNode), focused == null ? null : focused.key,
                    new Rectangle2D.Double(0, 0, viewportWidth, viewportHeight));
        } finally {
            Ui.restoreContext(previousContext);
        }
    }

    public RefreshResult refresh(float viewportWidth, float viewportHeight) {
        setViewport(viewportWidth, viewportHeight);
        return refresh();
    }

    public void paintCompleted() {
        validateThread();
        Ui.Context previousContext = Ui.installContext(context);
        try {
            runEffects(passiveEffects);
        } finally {
            Ui.restoreContext(previousContext);
        }
    }

    public void close() {
        validateThread();
        Reconciler.disposeTree(rootNode);
        rootNode = null;
        layoutEffects.clear();
        passiveEffects.clear();
        invalidationCallback = () -> {
        };
    }

    private void renderComponents() {
        if (rootNode == null || rootFiber.isDirty()) {
            ComponentNode newRoot = new ComponentNode(JReactRoot.class);
            newRoot.render = rootRender;
            newRoot.fiber = rootFiber;
            Reconciler.renderRoot(newRoot, rootNode);
            rootNode = newRoot;
        } else if (rootFiber.isSubtreeDirty()) {
            Reconciler.reconcileDirty(rootNode, rootFiber);
        }
    }

    private static void runEffects(List<Hooks.Effect> queue) {
        if (queue.isEmpty())
            return;
        List<Hooks.Effect> effects = new ArrayList<>(queue);
        queue.clear();

        RuntimeException failure = null;
        for (Hooks.Effect effect : effects) {
            try {
                effect.run();
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

    private static Map<Node.Identity, Node> index(Node root) {
        Map<Node.Identity, Node> result = new HashMap<>();
        if (root != null)
            index(root, result);
        return result;
    }

    private static void index(Node node, Map<Node.Identity, Node> result) {
        result.put(node.identity(), node);
        for (Node child : node.children())
            index(child, result);
    }

    public void pointerMoved(double x, double y) {
        validateThread();
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        updateHover();
    }

    public void pointerExited() {
        validateThread();
        pointerInside = false;
        if (!hoverPath.isEmpty()) {
            hoverPath = List.of();
            requestRefresh();
        }
    }

    public void pointerPressed(double x, double y) {
        validateThread();
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        List<Node.Identity> hit = hitPath(x, y);
        hoverPath = hit;
        pressPath = hit;
        activePath = hit;
        focusPath = hit;
        requestRefresh();
    }

    public void pointerReleased(double x, double y) {
        validateThread();
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        List<Node.Identity> hit = hitPath(x, y);
        hoverPath = hit;
        if (pressPath != null && pressPath.equals(hit)) {
            Node target = findByPath(pressPath);
            if (target != null)
                dispatch(target, new ClickEvent());
        }
        pressPath = null;
        activePath = List.of();
        requestRefresh();
    }

    public void keyPressed(int keyCode) {
        validateThread();
        Node target = findByPath(focusPath);
        if (target != null)
            dispatch(target, new KeyboardEvent(keyCode));
    }

    private void updateHover() {
        List<Node.Identity> hit = hitPath(pointerX, pointerY);
        if (!Objects.equals(hit, hoverPath)) {
            hoverPath = hit;
            requestRefresh();
        }
    }

    private List<Node.Identity> hitPath(double x, double y) {
        Node hit = hitTest(rootNode, x, y);
        if (hit == null)
            return List.of();
        List<Node.Identity> path = new ArrayList<>();
        for (Node node = hit; node != null; node = node.parent)
            path.add(node.identity());
        java.util.Collections.reverse(path);
        return path;
    }

    private static Node hitTest(Node node, double x, double y) {
        if (node == null)
            return null;
        if (node instanceof VisualNode visual && visual.style.display() == Display.NONE)
            return null;
        Node hit = null;
        for (Node child : node.children()) {
            Node childHit = hitTest(child, x, y);
            if (childHit != null)
                hit = childHit;
        }
        if (hit != null)
            return hit;
        if (node instanceof VisualNode && node.bounds.contains(x, y))
            return node;
        return null;
    }

    private void applyInteractionState() {
        if (rootNode != null)
            applyInteractionState(rootNode, new ArrayList<>());
    }

    private void applyInteractionState(Node node, List<Node.Identity> path) {
        path.add(node.identity());
        node.hover = path.equals(hoverPath);
        node.active = path.equals(activePath);
        node.focus = path.equals(focusPath);
        for (Node child : node.children())
            applyInteractionState(child, path);
        path.remove(path.size() - 1);
    }

    private Node findByPath(List<Node.Identity> path) {
        if (rootNode == null || path.isEmpty())
            return null;
        return findByPath(rootNode, path, 0);
    }

    private static Node findByPath(Node node, List<Node.Identity> path, int index) {
        if (!node.identity().equals(path.get(index)))
            return null;
        if (index == path.size() - 1)
            return node;
        for (Node child : node.children()) {
            Node found = findByPath(child, path, index + 1);
            if (found != null)
                return found;
        }
        return null;
    }

    private static void dispatch(Node target, JReactEvent event) {
        event.target = target;
        List<Node> path = new ArrayList<>();
        for (Node node = target; node != null; node = node.parent)
            path.add(node);
        java.util.Collections.reverse(path);
        for (int index = 0; index < path.size() - 1; index++) {
            path.get(index).dispatchCapture(event);
            if (event.isStopPropagation())
                return;
        }
        Node targetNode = path.getLast();
        targetNode.dispatchCapture(event);
        if (event.isStopPropagation())
            return;
        targetNode.dispatchBubble(event);
        if (event.isStopPropagation())
            return;
        for (int index = path.size() - 2; index >= 0; index--) {
            path.get(index).dispatchBubble(event);
            if (event.isStopPropagation())
                return;
        }
    }

    void enqueueLayoutEffect(Hooks.Effect effect) {
        layoutEffects.add(effect);
    }

    void enqueuePassiveEffect(Hooks.Effect effect) {
        passiveEffects.add(effect);
    }

    void requestRefresh() {
        invalidationCallback.run();
    }

    public float viewportWidth() {
        return viewportWidth;
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public void setViewport(float width, float height) {
        validateThread();
        if (!Float.isFinite(width) || width < 0)
            throw new IllegalArgumentException("Viewport width must be finite and non-negative");
        if (!Float.isFinite(height) || height < 0)
            throw new IllegalArgumentException("Viewport height must be finite and non-negative");
        if (Float.compare(viewportWidth, width) == 0 && Float.compare(viewportHeight, height) == 0)
            return;
        viewportWidth = width;
        viewportHeight = height;
        rootFiber.markDirty();
    }

    void validateStateWrite() {
        validateThread();
        if (rendering)
            throw new IllegalStateException("State must not change during component rendering");
    }

    void beginRender() {
        rendering = true;
    }

    void endRender() {
        rendering = false;
    }

    private void validateThread() {
        if (Thread.currentThread() != ownerThread)
            throw new IllegalStateException("JReactRoot must be accessed from its owning thread");
    }
}
