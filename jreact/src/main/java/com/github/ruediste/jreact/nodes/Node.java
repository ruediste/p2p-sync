package com.github.ruediste.jreact.nodes;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.antepod.taffy.tree.Layout;
import com.github.ruediste.jreact.EventListener;
import com.github.ruediste.jreact.JReactEvent;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

public class Node {

    public Node parent;
    public int depth;
    public Object key;

    public final Style inline = new Style();
    public Style style = new Style();

    public final List<Styles.NodeStyleRules> declarations = new ArrayList<>();

    public boolean hover;
    public boolean active;
    public boolean focus;
    public boolean disabled;
    public boolean focusable;
    public String accessibleName;

    public final Rectangle2D.Double bounds = new Rectangle2D.Double();
    public final List<Line> lines = new ArrayList<>();

    private final Map<Class<? extends JReactEvent>, List<EventListener<?>>> captureListeners = new HashMap<>();
    private final Map<Class<? extends JReactEvent>, List<EventListener<?>>> bubbleListeners = new HashMap<>();

    private final List<Node> children = new ArrayList<>();
    private Layout layout = Layout.zero();

    public Node() {
    }

    public Node(Object key) {
        this.key = key;
    }

    public Node setKey(Object key) {
        this.key = key;
        return this;
    }

    public Identity identity() {
        return new Identity(identityType(), key);
    }

    public Object identityType() {
        return getClass();
    }

    public boolean sameIdentity(Node other) {
        return other != null && identity().equals(other.identity());
    }

    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    public Node add(Node child) {
        Objects.requireNonNull(child);
        if (child == this)
            throw new IllegalArgumentException("A node cannot be its own child");
        if (child.parent != null && child.parent != this)
            throw new IllegalArgumentException("Node already has a parent");
        if (child.parent == this) {
            children.remove(child);
        }
        child.parent = this;
        children.add(child);
        child.updateDepth(depth + 1);
        return this;
    }

    public Node remove(Node child) {
        if (children.remove(child) && child.parent == this) {
            child.parent = null;
            child.updateDepth(0);
        }
        return this;
    }

    public Node clearChildren() {
        for (Node child : children) {
            if (child.parent == this) {
                child.parent = null;
                child.updateDepth(0);
            }
        }
        children.clear();
        return this;
    }

    private void updateDepth(int newDepth) {
        if (depth == newDepth)
            return;
        depth = newDepth;
        for (Node child : children)
            child.updateDepth(newDepth + 1);
    }

    public Node replaceChildren(List<? extends Node> replacement) {
        Objects.requireNonNull(replacement);
        clearChildren();
        for (Node child : replacement)
            add(child);
        return this;
    }

    public Node addDeclaration(Styles.NodeStyleRules declaration) {
        declarations.add(Objects.requireNonNull(declaration));
        return this;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
    }

    public void applyLayoutDefaults() {
    }

    public <T extends JReactEvent> Node on(Class<T> eventType, EventListener<T> listener) {
        bubbleListeners.computeIfAbsent(Objects.requireNonNull(eventType), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(listener));
        return this;
    }

    public <T extends JReactEvent> Node capture(Class<T> eventType, EventListener<T> listener) {
        captureListeners.computeIfAbsent(Objects.requireNonNull(eventType), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(listener));
        return this;
    }

    @SuppressWarnings("unchecked")
    public void dispatchCapture(JReactEvent event) {
        for (EventListener<?> listener : captureListeners.getOrDefault(event.getClass(), List.of()))
            ((EventListener<JReactEvent>) listener).handle(event);
    }

    @SuppressWarnings("unchecked")
    public void dispatchBubble(JReactEvent event) {
        for (EventListener<?> listener : bubbleListeners.getOrDefault(event.getClass(), List.of()))
            ((EventListener<JReactEvent>) listener).handle(event);
    }

    public record Identity(Object type, Object key) {
        public Identity {
            Objects.requireNonNull(type);
        }
    }

    public record Line(String text, float x, float baseline) {
        public Line {
            Objects.requireNonNull(text);
        }
    }
}
