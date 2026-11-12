package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.Icon;

import com.github.ruediste.jreact.nodes.DivNode;
import com.github.ruediste.jreact.nodes.IconNode;
import com.github.ruediste.jreact.nodes.InputNode;
import com.github.ruediste.jreact.nodes.Node;
import com.github.ruediste.jreact.nodes.SelectNode;
import com.github.ruediste.jreact.nodes.SpanNode;
import com.github.ruediste.jreact.nodes.TextNode;

public final class Ui {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private Ui() {
    }

    static Context context() {
        Context context = CURRENT.get();
        if (context == null)
            throw new IllegalStateException("No active JReact render context");
        return context;
    }

    static Context installContext(Context context) {
        Objects.requireNonNull(context);
        Context previous = CURRENT.get();
        CURRENT.set(context);
        return previous;
    }

    static void restoreContext(Context previous) {
        if (previous == null)
            CURRENT.remove();
        else
            CURRENT.set(previous);
    }

    public static void key(Object key) {
        context().pendingKey = key;
    }

    public static float viewportWidth() {
        return context().root.viewportWidth();
    }

    public static float viewportHeight() {
        return context().root.viewportHeight();
    }

    public static void div(Consumer<Style> style, Runnable children) {
        div(style, null, children);
    }

    public static void div(Consumer<Style> style, Consumer<Node> configure, Runnable children) {
        DivNode node = new DivNode();
        applyStyle(node, style);
        if (configure != null)
            configure.accept(node);
        addNode(node);
        withContainer(node, children);
    }

    public static void div(Consumer<Style> style) {
        div(style, null, null);
    }

    public static void div(Runnable children) {
        div(null, null, children);
    }

    public static void div() {
        div(null, null, null);
    }

    public static void span(Consumer<Style> style, Runnable children) {
        span(style, null, children);
    }

    public static void span(Consumer<Style> style, Consumer<Node> configure, Runnable children) {
        SpanNode node = new SpanNode();
        applyStyle(node, style);
        if (configure != null)
            configure.accept(node);
        addNode(node);
        withContainer(node, children);
    }

    public static void span(Consumer<Style> style) {
        span(style, null, null);
    }

    public static void span(Runnable children) {
        span(null, null, children);
    }

    public static void span() {
        span(null, null, null);
    }

    public static void text(String text) {
        text(text, null);
    }

    public static void text(String text, Consumer<Style> style) {
        text(text, style, null);
    }

    public static void text(String text, Consumer<Style> style, Consumer<Node> configure) {
        TextNode node = new TextNode(Objects.requireNonNull(text));
        applyStyle(node, style);
        if (configure != null)
            configure.accept(node);
        addNode(node);
    }

    public static void icon(Icon icon) {
        icon(icon, null);
    }

    public static void icon(Icon icon, Consumer<Style> style) {
        icon(icon, style, null);
    }

    public static void icon(Icon icon, Consumer<Style> style, Consumer<Node> configure) {
        IconNode node = new IconNode(Objects.requireNonNull(icon));
        applyStyle(node, style);
        if (configure != null)
            configure.accept(node);
        addNode(node);
    }

    public static void input(String value, Consumer<String> onChange) {
        input(value, onChange, null);
    }

    public static void input(String value, Consumer<String> onChange, Consumer<Style> style) {
        input(value, onChange, style, false, null);
    }

    public static void input(String value, Consumer<String> onChange, Consumer<Style> style, boolean disabled,
            String accessibleName) {
        InputNode node = new InputNode(Objects.requireNonNull(value), Objects.requireNonNull(onChange));
        node.disabled = disabled;
        node.focusable = true;
        node.accessibleName = accessibleName;
        applyStyle(node, style);
        addNode(node);
    }

    public static void select(String value, List<String> options, Consumer<String> onChange) {
        select(value, options, onChange, null);
    }

    public static void select(String value, List<String> options, Consumer<String> onChange, Consumer<Style> style) {
        select(value, options, onChange, style, false, null);
    }

    public static void select(String value, List<String> options, Consumer<String> onChange, Consumer<Style> style,
            boolean disabled, String accessibleName) {
        SelectNode node = new SelectNode(value, options, onChange);
        node.disabled = disabled;
        node.focusable = true;
        node.accessibleName = accessibleName;
        applyStyle(node, style);
        addNode(node);
    }

    private static void applyStyle(Node node, Consumer<Style> style) {
        if (style != null)
            style.accept(node.inline);
    }

    private static void withContainer(Node node, Runnable children) {
        if (children == null)
            return;
        Context context = context();
        Consumer<Node> previousCallback = context.onNodeCreated;
        int previousKeyCounter = context.keyCounter;
        context.onNodeCreated = node::add;
        context.keyCounter = 0;
        try {
            children.run();
        } finally {
            context.onNodeCreated = previousCallback;
            context.keyCounter = previousKeyCounter;
        }
    }

    public static void addNode(Node node, Consumer<Style> style) {
        applyStyle(node, style);
        addNode(node);
    }

    public static void addNode(Node node) {
        Context context = context();
        if (context.pendingKey != null) {
            node.key = context.pendingKey;
            context.pendingKey = null;
        } else if (node.key == null) {
            node.key = new AutoKey(context.keyCounter++);
        }
        node.declarations.addAll(context.declarationChain);
        if (context.onNodeCreated == null)
            throw new IllegalStateException("No node creation callback is active");
        context.onNodeCreated.accept(node);
    }

    public static final class Context {
        public final JReactRoot root;
        public Consumer<Node> onNodeCreated;
        Fiber fiber;

        Styles.NodeStyleRules styleRules = new Styles.NodeStyleRules();
        final List<Styles.NodeStyleRules> declarationChain = new ArrayList<>();
        int keyCounter;
        private Object pendingKey;

        Context(JReactRoot root) {
            this.root = Objects.requireNonNull(root);
        }

        void resetKeyCounters() {
            keyCounter = 0;
            pendingKey = null;
        }
    }

    private record AutoKey(int index) {
    }
}
