package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.ruediste.jreact.nodes.ContainerNode;
import com.github.ruediste.jreact.nodes.DivNode;
import com.github.ruediste.jreact.nodes.IconNode;
import com.github.ruediste.jreact.nodes.InputNode;
import com.github.ruediste.jreact.nodes.SelectNode;
import com.github.ruediste.jreact.nodes.SliderNode;
import com.github.ruediste.jreact.nodes.SpanNode;
import com.github.ruediste.jreact.nodes.TextNode;

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
 * {@link JReactRoot} and fiber. Nested component rendering restores the
 * caller's
 * context on exit, including exceptional exit. Primitives, component calls, and
 * viewport queries require an active render; input and select nodes own native
 * Swing components that {@link JReactPanel} hosts.
 */
public final class Ui {
    static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /**
     * Connects the current component to its root, hook storage, and enclosing
     * style declarations for the duration of its render.
     */
    static final class Context {
        final JReactRoot runtime;
        final Fiber fiber;

        /** Style rules of the current component */
        final Styles.NodeStyleRules styleRules;

        /**
         * Style-rule scopes of all components enclosing this one, outermost
         * first, ending with this component's own {@link #styleRules}. Nodes
         * created in this scope copy the chain so that rule resolution can
         * consider every enclosing component and, at equal specificity, prefer
         * the nearest declaring component.
         * 
         * The list is not modified after construction; it is effectively immutable.
         */
        final List<Styles.NodeStyleRules> styleRuleChain;

        /** Creates the context of a root render without an enclosing component. */
        Context(JReactRoot runtime, Fiber fiber) {
            this(runtime, fiber, List.of());
        }

        /** Creates the context of a component nested in {@code parent}. */
        Context(Context parent, Fiber fiber) {
            this(parent.runtime, fiber, parent.styleRuleChain);
        }

        private Context(JReactRoot runtime, Fiber fiber, List<Styles.NodeStyleRules> parentDeclarations) {
            this.runtime = runtime;
            this.fiber = fiber;
            styleRules = new Styles.NodeStyleRules();
            styleRuleChain = new ArrayList<>(parentDeclarations);
            styleRuleChain.add(styleRules);
        }
    }

    private Ui() {
    }

    public static Context context() {
        Context result = CURRENT.get();
        if (result == null)
            throw new IllegalStateException("JReact primitives and hooks require a rendering component");
        return result;
    }

    public static DivNode div(Consumer<Style> style, Runnable children) {
        return container(DivNode::new, style, children);
    }

    public static DivNode div(Runnable children) {
        return div(style -> {
        }, children);
    }

    public static SpanNode span(Runnable children) {
        return span(style -> {
        }, children);
    }

    public static SpanNode span(Consumer<Style> style, Runnable children) {
        return container(SpanNode::new, style, children);
    }

    public static TextNode text(Object text) {
        return text(text, style -> {
        });
    }

    public static TextNode text(Object text, Consumer<Style> style) {
        return node((key, inline, declarations) -> new TextNode(key, String.valueOf(text), inline, declarations),
                style);
    }

    public static InputNode input(String value, Consumer<String> onChange, Consumer<Style> style) {
        InputNode node = node((key, inline, declarations) -> new InputNode(key, Objects.requireNonNull(value), inline,
                declarations, context().runtime::invalidate), style);
        node.onChange = Objects.requireNonNull(onChange);
        return node;
    }

    public static SelectNode select(String value, List<String> options, Consumer<String> onChange,
            Consumer<Style> style) {
        if (options.isEmpty() || !options.contains(value))
            throw new IllegalArgumentException("Select value must belong to its non-empty options");
        SelectNode node = node((key, inline, declarations) -> new SelectNode(key, value, options, inline, declarations,
                context().runtime::invalidate), style);
        node.onChange = Objects.requireNonNull(onChange);
        return node;
    }

    public static IconNode icon(javax.swing.Icon icon, Consumer<Style> style) {
        return node((key, inline, declarations) -> new IconNode(key, Objects.requireNonNull(icon), inline,
                declarations), style);
    }

    public static SliderNode slider(double fraction, java.awt.Color color, Consumer<Style> style, Runnable children) {
        SliderNode node = container(SliderNode::new, style, children);
        node.fraction = fraction;
        node.color = color;
        return node;
    }

    public static double viewportWidth() {
        return context().runtime.viewportWidth;
    }

    public static double viewportHeight() {
        return context().runtime.viewportHeight;
    }

    @FunctionalInterface
    private interface NodeFactory<T extends Node> {
        T create(Node.Key key, Style inline, List<Styles.NodeStyleRules> declarations);
    }

    private static <T extends ContainerNode> T container(NodeFactory<T> factory, Consumer<Style> style,
            Runnable children) {
        T node = node(factory, style);
        var runtime = context().runtime;
        Node previous = runtime.currentParentNode;
        runtime.currentParentNode = node;
        try {
            children.run();
        } finally {
            runtime.currentParentNode = previous;
        }
        return node;
    }

    private static <T extends Node> T node(NodeFactory<T> factory, Consumer<Style> configure) {
        var context = context();
        Style style = new Style();
        configure.accept(style);
        T node = factory.create(new Node.Key(context.fiber, context.fiber.nodeCursor++), style,
                context.styleRuleChain);
        Node parent = context.runtime.currentParentNode;
        if (parent instanceof SpanNode && !(node instanceof SpanNode) && !(node instanceof TextNode)
                && !(node instanceof IconNode))
            throw new IllegalArgumentException("Span accepts only inline text, spans, and icons");
        parent.addChild(node);
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

    /**
     * Opens the retained component boundary used by the runtime agent.
     *
     * @param type stable, non-null identity of the component implementation,
     *             compared by equality; not necessarily a Java class
     * @param key  stable identity of an instance among siblings of the same type,
     *             or null for positional identity
     * @return the component scope, which must be closed after the component body
     *         has been executed
     * @throws IllegalArgumentException if the identity has already been visited
     *                                  in the current parent render
     * @throws NullPointerException     if {@code type} is null
     */
    public static ComponentScope enterComponent(Object type, Object key) {
        var parentContext = context();
        int position = parentContext.fiber.childCursor++;
        var identity = new Fiber.Identity(Objects.requireNonNull(type),
                key == null ? new Fiber.Position(position) : key);
        if (!parentContext.fiber.seen.add(identity))
            throw new IllegalArgumentException("Duplicate component identity: " + identity);

        // get the existing fiber or create a new one if it doesn't exist
        Fiber childFiber = parentContext.fiber.children.computeIfAbsent(identity, ignored -> new Fiber());
        childFiber.begin();
        CURRENT.set(new Context(parentContext, childFiber));
        return new ComponentScope(childFiber, parentContext);
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