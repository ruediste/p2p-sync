package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Declarative entry points for creating primitives and invoking components.
 *
 * <p>
 * Primitive calls append nodes to the current render tree. Component calls
 * establish a retained {@link Fiber}, giving hooks their own call-order slots
 * and style rules their own declaration scope. Explicit component calls support
 * keyed instances; the runtime agent establishes equivalent boundaries around
 * eligible methods.
 *
 * <p>
 * A thread-local context connects these static methods to the active
 * {@link RenderRoot} and fiber. Nested component rendering restores the
 * caller's
 * context on exit, including exceptional exit. Primitives, component calls, and
 * viewport queries require an active render; input and select nodes describe
 * native editors that {@link JReactPanel} creates and manages.
 */
public final class Ui {
    static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /**
     * Connects the current component to its root, hook storage, and enclosing
     * style declarations for the duration of its render.
     */
    static final class Context {
        final RenderRoot runtime;
        final Fiber fiber;

        /** Style rules of the current component */
        final Styles.NodeStyleRules styleRules;

        /**
         * Style-rule scopes of all components enclosing this one, outermost
         * first, ending with this component's own {@link #styleRules}. Nodes
         * created in this scope copy the chain so that rule resolution can
         * consider every enclosing component and, at equal specificity, prefer
         * the nearest declaring component.
         */
        final List<Styles.NodeStyleRules> styleRuleChain;

        /** Creates the context of a root render without an enclosing component. */
        Context(RenderRoot runtime, Fiber fiber) {
            this(runtime, fiber, List.of());
        }

        /** Creates the context of a component nested in {@code parent}. */
        Context(Context parent, Fiber fiber) {
            this(parent.runtime, fiber, parent.styleRuleChain);
        }

        private Context(RenderRoot runtime, Fiber fiber, List<Styles.NodeStyleRules> parentDeclarations) {
            this.runtime = runtime;
            this.fiber = fiber;
            styleRules = new Styles.NodeStyleRules();
            styleRuleChain = new ArrayList<>(parentDeclarations);
            styleRuleChain.add(styleRules);
        }
    }

    private Ui() {
    }

    static Context context() {
        Context result = CURRENT.get();
        if (result == null)
            throw new IllegalStateException("JReact primitives and hooks require a rendering component");
        return result;
    }

    /**
     * Renders a child component identified by its type and sibling call position.
     *
     * <p>
     * The child receives its own retained {@link Fiber}, hook slots, and style
     * declaration scope. Repeating the same type at the same component-call
     * position under the same parent reuses that fiber. Changing the type or
     * position starts a different component lifetime, so use the keyed overload
     * for reorderable lists.
     *
     * <p>
     * This establishes a runtime boundary, not a visual container: primitives
     * emitted by the body are appended to the current node parent. An ordinary
     * helper method not transformed by the runtime agent shares the caller's
     * hook and style scope.
     *
     * @param type stable, non-null component identity token; not necessarily a
     *             Java class
     * @param body component render body, executed synchronously on every call
     * @throws IllegalStateException if no component render is active or the
     *                               body's hook count or kind changes
     * @throws NullPointerException  if {@code type} or {@code body} is null
     * @see #component(Object, Object, Runnable)
     */
    public static void component(Object type, Runnable body) {
        component(type, null, body);
    }

    public static Node div(Consumer<Style> style, Runnable children) {
        return container(Node.Kind.DIV, style, children);
    }

    public static Node div(Runnable children) {
        return div(style -> {
        }, children);
    }

    public static Node span(Runnable children) {
        return span(style -> {
        }, children);
    }

    public static Node span(Consumer<Style> style, Runnable children) {
        return container(Node.Kind.SPAN, style, children);
    }

    public static Node text(Object text) {
        return text(text, style -> {
        });
    }

    public static Node text(Object text, Consumer<Style> style) {
        return node(Node.Kind.TEXT, String.valueOf(text), style);
    }

    public static Node input(String value, Consumer<String> onChange, Consumer<Style> style) {
        Node node = node(Node.Kind.INPUT, Objects.requireNonNull(value), style);
        node.onChange = Objects.requireNonNull(onChange);
        node.focusable = true;
        return node;
    }

    public static Node select(String value, List<String> options, Consumer<String> onChange, Consumer<Style> style) {
        if (options.isEmpty() || !options.contains(value))
            throw new IllegalArgumentException("Select value must belong to its non-empty options");
        Node node = node(Node.Kind.SELECT, value, style);
        node.options = List.copyOf(options);
        node.onChange = Objects.requireNonNull(onChange);
        node.focusable = true;
        return node;
    }

    public static Node icon(javax.swing.Icon icon, Consumer<Style> style) {
        Node node = node(Node.Kind.ICON, null, style);
        node.icon = Objects.requireNonNull(icon);
        return node;
    }

    public static double viewportWidth() {
        return context().runtime.viewportWidth;
    }

    public static double viewportHeight() {
        return context().runtime.viewportHeight;
    }

    private static Node container(Node.Kind kind, Consumer<Style> style, Runnable children) {
        Node node = node(kind, null, style);
        var runtime = context().runtime;
        Node previous = runtime.parent;
        runtime.parent = node;
        try {
            children.run();
        } finally {
            runtime.parent = previous;
        }
        return node;
    }

    private static Node node(Node.Kind kind, String text, Consumer<Style> configure) {
        var context = context();
        Style style = new Style();
        configure.accept(style);
        Node node = new Node(kind, new Node.Key(context.fiber, context.fiber.nodeCursor++), text,
                style, List.copyOf(context.styleRuleChain));
        node.parent = context.runtime.parent;
        if (node.parent.kind == Node.Kind.SPAN && kind != Node.Kind.SPAN && kind != Node.Kind.TEXT
                && kind != Node.Kind.ICON)
            throw new IllegalArgumentException("Span accepts only inline text, spans, and icons");
        node.parent.children.add(node);
        return node;
    }

    /**
     * Renders a child component using an explicit identity within its parent.
     *
     * <p>
     * {@code type} answers "which component implementation?", while {@code key}
     * answers "which instance of that component?". For example:
     * 
     * <pre>{@code
     * Ui.component("UserRow", user.id(), () -> renderUser(user));
     * }</pre>
     * 
     * Here {@code "UserRow"} identifies the component implementation and
     * {@code user.id()} identifies the row whose state should survive reordering.
     * Keeping type separate prevents a different implementation from inheriting
     * that state: {@code ("UserRow", 42)} and {@code ("GroupRow", 42)} select
     * different fibers, even under the same parent. Keys only need to be unique
     * among siblings of the same type, not globally. The runtime trusts the
     * supplied type token; it does not infer component identity from the body.
     *
     * <p>
     * The pair {@code (type, key)} selects a retained child {@link Fiber}.
     * With a non-null key, moving the component among siblings preserves its
     * state as long as its parent and type remain unchanged. A null key selects
     * the current sibling component-call position instead. Type and key equality
     * and hash codes must remain stable, and each pair may appear only once in a
     * parent render. Every call advances the sibling cursor, including keyed
     * calls, so variable-length lists should have their own component boundary
     * when unkeyed siblings follow them.
     *
     * <p>
     * The body runs immediately with the child's hook cursor reset and its
     * own style declaration scope. Nested rendering restores the caller's
     * context even if the body throws. This does not create a {@link Node};
     * emitted primitives remain children of the current node parent.
     * The runtime agent uses equivalent retained fibers for method boundaries.
     *
     * <p>
     * Hooks must keep the same count and kinds on every render of the child.
     * If the parent later completes a render without visiting this identity,
     * the child is unmounted, its state handles are disabled, and its effects
     * are cleaned up. Reintroducing it after removal creates fresh state.
     *
     * @param type stable, non-null identity of the component implementation,
     *             compared by equality; not necessarily a Java class
     * @param key  stable identity of an instance among siblings of the same type,
     *             or null for
     *             positional identity
     * @param body component render body, executed synchronously on every call
     * @throws IllegalStateException    if no component render is active or the
     *                                  body's hook count or kind changes
     * @throws IllegalArgumentException if the identity has already been visited
     *                                  in the current parent render
     * @throws NullPointerException     if {@code type} or {@code body} is null
     */
    public static void component(Object type, Object key, Runnable body) {
        var scope = enterComponent(type, key);
        Throwable failure = null;
        try {
            body.run();
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            scope.close(failure);
        }
    }

    /** Opens the retained component boundary used by the runtime agent. */
    public static ComponentScope enterComponent(String type) {
        return enterComponent(type, null);
    }

    private static ComponentScope enterComponent(Object type, Object key) {
        var parent = context();
        int position = parent.fiber.childCursor++;
        var identity = new Fiber.Identity(Objects.requireNonNull(type),
                key == null ? new Fiber.Position(position) : key);
        if (!parent.fiber.seen.add(identity))
            throw new IllegalArgumentException("Duplicate component identity: " + identity);

        // get the existing fiber or create a new one if it doesn't exist
        Fiber child = parent.fiber.children.computeIfAbsent(identity, ignored -> new Fiber());
        child.begin();
        CURRENT.set(new Context(parent, child));
        return new ComponentScope(child, parent);
    }

    /** Closes a component boundary entered by {@link #enterComponent(String)}. */
    public static final class ComponentScope {
        private final Fiber fiber;
        private final Context previous;
        private boolean closed;

        private ComponentScope(Fiber fiber, Context previous) {
            this.fiber = fiber;
            this.previous = previous;
        }

        public void close(Throwable failure) {
            if (closed)
                throw new IllegalStateException("Component scope is already closed");
            closed = true;
            try {
                if (failure == null)
                    fiber.finish();
            } finally {
                if (previous == null)
                    CURRENT.remove();
                else
                    CURRENT.set(previous);
            }
        }
    }
}