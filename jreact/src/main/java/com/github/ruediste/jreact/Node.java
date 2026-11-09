package com.github.ruediste.jreact;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.tree.Layout;
import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.events.KeyboardEvent;
import com.github.ruediste.jreact.events.PointerEvent;
import com.github.ruediste.jreact.layout.LayoutEngine;
import com.github.ruediste.jreact.layout.LayoutNode;
import com.github.ruediste.jreact.nodes.ContainerNode;
import com.github.ruediste.jreact.nodes.SwingNode;

/**
 * Base class of the primitive node tree produced by a render.
 *
 * <p>
 * A node connects declarative content and callbacks to the downstream UI
 * stages: {@link Styles} resolves its inline and scoped styles, the
 * {@link LayoutEngine} fills its layout, and the node itself paints its box and
 * content. {@link JReactPanel} supplies interaction flags, dispatches
 * callbacks, and hosts the Swing components owned by {@link SwingNode}
 * subclasses.
 *
 * <p>
 * Nodes are mutable and recreated on each render. Their keys associate
 * corresponding primitives across renders. During reconciliation, a new node
 * receives the previous node with the same key and can transfer retained state
 * such as interaction flags, Swing components, and the last committed paint
 * state. This lets the node hierarchy own retention and repaint detection
 * instead of the host.
 *
 * <p>
 * Bounds use root-relative logical pixels; text-line coordinates are relative
 * to their containing node's bounds.
 */
public abstract class Node implements LayoutNode {
    /**
     * Associates a primitive with its owning fiber and its render-call position.
     *
     * <p>
     * Equality connects successive node descriptions for interaction state,
     * component retention, and repaint comparisons. This is positional identity
     * within a retained component, not matching by text, style ID, or node
     * type. Inserting earlier primitives can shift positions and associate
     * different output; use stable component boundaries when identity matters.
     */
    public record Key(Object component, int position) {
    }

    /** A measured text fragment with node-local horizontal and baseline offsets. */
    public record Line(String text, double x, double baseline) {
    }

    public final Key key;
    public final Style inline;
    public Style style;
    public Node parent;
    public final List<Node> children = new ArrayList<>();
    public final Rectangle2D.Double bounds = new Rectangle2D.Double();
    public Layout layout = Layout.zero();

    public boolean hover, active, focus, disabled;
    public boolean focusable;
    public String accessibleName;
    /**
     * Event listeners registered on this node, keyed by the exact event class.
     * There is at most one listener per event class; adding a listener for a
     * class that already has one replaces it. The {@code capture} flag selects
     * the phase: capture listeners fire while the event travels down to the
     * target, bubble listeners fire while it travels back up.
     */
    private final Map<Class<? extends JReactEvent>, ListenerRegistration<?>> listeners = new LinkedHashMap<>();

    /** A registered listener and the phase it fires in. */
    private record ListenerRegistration<T extends JReactEvent>(EventListener<T> listener, boolean capture) {
    }

    /**
     * Snapshot of the style-rule scopes of the component that created this
     * node and of all components enclosing it, outermost first.
     *
     * <p>
     * The last entry belongs to the component that rendered this node and
     * therefore owns it; earlier entries belong to enclosing components and can
     * style it only through {@link Styles.Scope#TRANSITIVE} rules.
     * {@link Styles#resolve} uses this chain to collect and order matching
     * rules. The list is copied when the node is created, so declarations added
     * during later renders do not change existing nodes.
     */
    final List<Styles.NodeStyleRules> declarations;

    private PaintState lastCommitted;

    protected Node(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        this.key = key;
        this.inline = inline;
        this.style = inline;
        this.declarations = declarations;
    }

    /**
     * Adds a listener for {@link ClickEvent}. There is at most one click
     * listener per node; calling this again replaces it. The node becomes
     * focusable so it can be activated with Enter/Space.
     */
    public Node onClick(EventListener<ClickEvent> listener) {
        focusable = true;
        addEventListener(ClickEvent.class, listener, false);
        return this;
    }

    /**
     * Adds a listener for {@link PointerEvent}. There is at most one pointer
     * listener per node; calling this again replaces it.
     */
    public Node onPointer(EventListener<PointerEvent> listener) {
        addEventListener(PointerEvent.class, listener, false);
        return this;
    }

    /**
     * Adds a listener for {@link KeyboardEvent}. There is at most one keyboard
     * listener per node; calling this again replaces it.
     */
    public Node onKey(EventListener<KeyboardEvent> listener) {
        addEventListener(KeyboardEvent.class, listener, false);
        return this;
    }

    /**
     * Registers a listener for the given event class. There can only be one
     * listener for a single event class per node; registering a new listener
     * replaces any existing listener for that class. Set {@code capture} to
     * {@code true} to fire the listener during the capture phase (root down to
     * this node's parent) instead of the bubble phase (this node's parent up to
     * the root). The listener on the target node itself always fires once.
     *
     * @param type     the exact event class the listener is registered for
     * @param listener the listener to invoke
     * @param capture  whether the listener fires during the capture phase
     * @param <T>      the event class
     */
    public <T extends JReactEvent> void addEventListener(Class<T> type, EventListener<T> listener,
            boolean capture) {
        listeners.put(type, new ListenerRegistration<>(listener, capture));
    }

    /**
     * Registers a bubble-phase listener for the given event class. There can
     * only be one listener for a single event class per node; registering a new
     * listener replaces any existing listener for that class.
     *
     * @param type     the exact event class the listener is registered for
     * @param listener the listener to invoke
     * @param <T>      the event class
     * @see #addEventListener(Class, EventListener, boolean)
     */
    public <T extends JReactEvent> void addEventListener(Class<T> type, EventListener<T> listener) {
        addEventListener(type, listener, false);
    }

    /**
     * Returns the listener registered for the given exact event class, or
     * {@code null} if none is registered.
     */
    @SuppressWarnings("unchecked")
    public <T extends JReactEvent> EventListener<T> getEventListener(Class<T> type) {
        var registration = listeners.get(type);
        return registration == null ? null : (EventListener<T>) registration.listener();
    }

    /** Whether a listener is registered for the given exact event class. */
    public boolean hasEventListener(Class<? extends JReactEvent> type) {
        return listeners.containsKey(type);
    }

    /**
     * Dispatches the given event through the capture, target, and bubble phases
     * along the path from the root to this node.
     *
     * <p>
     * Capture phase invokes capture listeners on the ancestors from the root
     * down to this node's parent. Target phase invokes the listener on this
     * node once. Bubble phase invokes bubble listeners on the ancestors from
     * this node's parent back up to the root. Listeners are matched by the
     * exact event class, not by subclasses. Calling
     * {@link JReactEvent#stopPropagation()} in a listener stops further phase
     * delivery.
     */
    public void dispatchEvent(JReactEvent event) {
        var path = new ArrayList<Node>();
        for (Node node = this; node != null; node = node.parent)
            path.add(node);
        Collections.reverse(path);

        int target = path.size() - 1;
        for (int index = 0; index < target && !event.isStopPropagation(); index++)
            invokeListener(path.get(index), event, Phase.CAPTURE);
        if (!event.isStopPropagation())
            invokeListener(this, event, Phase.TARGET);
        for (int index = target - 1; index >= 0 && !event.isStopPropagation(); index--)
            invokeListener(path.get(index), event, Phase.BUBBLE);
    }

    /** Delivery phase of an event. */
    private enum Phase {
        CAPTURE, TARGET, BUBBLE
    }

    @SuppressWarnings("unchecked")
    private <T extends JReactEvent> void invokeListener(Node node, JReactEvent event, Phase phase) {
        var registration = (ListenerRegistration<T>) node.listeners.get(event.getClass());
        if (registration != null && (phase == Phase.TARGET
                || (phase == Phase.CAPTURE && registration.capture())
                || (phase == Phase.BUBBLE && !registration.capture())))
            registration.listener().handle((T) event);
    }

    public Node disabled(boolean value) {
        disabled = value;
        return this;
    }

    public Node accessibleName(String value) {
        accessibleName = value;
        return this;
    }

    /** The text content, or {@code null} for non-textual nodes. */
    public String text() {
        return null;
    }

    public List<Node> descendants() {
        var result = new ArrayList<Node>();
        result.add(this);
        children.forEach(child -> result.addAll(child.descendants()));
        return List.copyOf(result);
    }

    /**
     * Adds a child. Only {@link ContainerNode} accepts children; leaves throw
     * {@link UnsupportedOperationException}.
     */
    public void addChild(Node child) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not accept children");
    }

    @Override
    public Style getStyle() {
        return style;
    }

    @Override
    public LayoutNode getParent() {
        return parent;
    }

    @Override
    public List<LayoutNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    @Override
    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    @Override
    public Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace) {
        return null;
    }

    /**
     * Computes the layout of this subtree for the given viewport size and copies
     * the result into the root-relative {@link #bounds} of every node.
     */
    public final void layout(double width, double height) {
        Style.nonNegative(width);
        Style.nonNegative(height);
        LayoutEngine.computeLayout(this, (float) width, (float) height);
        applyLayout();
    }

    private void applyLayout() {
        float x = 0, y = 0;
        for (Node n = this; n != null; n = n.parent) {
            x += n.layout.location.x;
            y += n.layout.location.y;
        }
        bounds.setRect(x, y, layout.size.width, layout.size.height);
        layoutApplied();
        for (Node child : children)
            child.applyLayout();
    }

    /** Called after {@link #bounds} has been updated by layout. */
    protected void layoutApplied() {
    }

    /**
     * Applies layout defaults that depend on the concrete node type. Called for
     * each node after style resolution and before layout.
     */
    public void applyLayoutDefaults() {
    }

    /**
     * Transfers retained state from the previous node with the same key.
     *
     * <p>
     * Subclasses extend this to transfer Swing components and other retained
     * state. The default transfers interaction flags and the last committed
     * paint state, which lets repaint detection compare the new node against
     * the state that was last presented.
     */
    public void reconcile(Node previous) {
        if (previous == null || previous == this)
            return;
        hover = previous.hover;
        active = previous.active;
        focus = previous.focus;
        lastCommitted = previous.lastCommitted;
    }

    /**
     * Paints this node and its subtree.
     *
     * <p>
     * The template paints the box (shadow, background, border), then
     * {@link #paintContent}, then children, then {@link #paintOverlay}, and
     * finally the focus outline. Subclasses customize the content and overlay
     * hooks rather than the template.
     */
    public final void paint(Graphics2D graphics, float inheritedOpacity) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float opacity = inheritedOpacity * (float) style.number(StyleProperties.OPACITY, 1);
            copy.setComposite(AlphaComposite.SrcOver.derive(opacity));
            Shape shape = PaintUtils.shape(this);
            Style.Shadow boxShadow = style.get(StyleProperties.BOX_SHADOW, null);
            if (boxShadow != null)
                PaintUtils.shadow(this, copy, boxShadow, layer -> layer.fill(shape));
            Color background = style.get(StyleProperties.BACKGROUND, null);
            if (background != null) {
                copy.setColor(background);
                copy.fill(shape);
            }
            PaintUtils.paintBorder(this, copy, shape);
            paintContent(copy);
            paintChildren(copy, opacity);
            paintOverlay(copy);
            if (focus && paintsFocusOutline()) {
                copy.setColor(new Color(0, 125, 150));
                copy.setStroke(new BasicStroke(2));
                copy.draw(new RoundRectangle2D.Double(bounds.x + 2, bounds.y + 2,
                        Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4), 6, 6));
            }
        } finally {
            copy.dispose();
        }
    }

    /** Paints node-specific content such as text or an icon. */
    protected void paintContent(Graphics2D graphics) {
    }

    /** Paints child nodes. Containers override this. */
    protected void paintChildren(Graphics2D graphics, float opacity) {
    }

    /** Paints decorations above the children, such as a slider thumb. */
    protected void paintOverlay(Graphics2D graphics) {
    }

    /** Whether the generic focus outline should be painted for this node. */
    protected boolean paintsFocusOutline() {
        return true;
    }

    /**
     * Immutable, paint-relevant snapshot of a node, used to detect what changed
     * between refreshes and to compute localized repaint regions.
     *
     * <p>
     * Each node subclass contributes the fields that affect its painted output.
     * {@link Node#basePaintState()} captures the resolved style values, bounds,
     * focus flag, effective opacity, and paint bounds. Subclasses wrap that base
     * state with their own payload, for example text and measured lines, an
     * icon, select options, or slider fraction/color. Equality of the concrete
     * record therefore means "this node would paint identically".
     *
     * <p>
     * Repaint works in two steps:
     * <ol>
     * <li>After a render, reconciliation transfers the previous node's last
     * committed state to the new node with the same key (see
     * {@link Node#reconcile(Node)}). The panel then applies interaction flags,
     * resolves styles, and lays out the tree.</li>
     * <li>{@link Node#dirtyRegion()} compares the current state with that
     * baseline. An unchanged node contributes nothing. A changed node contributes
     * the union of its old and current {@link #paintBounds() paint bounds}, which
     * covers moved nodes and shadow extents. New nodes contribute their current
     * bounds. The reconciler unions the results of all nodes and adds the last
     * committed bounds of nodes that disappeared; the panel repaints only the
     * resulting region.</li>
     * </ol>
     *
     * <p>
     * After the diff, {@link Node#commitPaintState()} stores the current state as
     * the baseline for the next refresh. Committing during refresh, before the
     * asynchronous Swing paint, makes repeated refreshes coalesce changes against
     * the last presented state. Because effective opacity is part of the state,
     * an ancestor opacity change invalidates its descendants even when their own
     * style is unchanged. Nodes whose state is unchanged avoid repaint entirely.
     */
    // todo: remove paint state, add method to Node to decide if a node needs
    // repaint based on its own logic
    public interface PaintState {
        /**
         * Bounds this state occupied when painted, including box and text
         * shadows. Used to clear the old area when a node changes or disappears.
         */
        Rectangle paintBounds();
    }

    protected record BasePaintState(Map<StyleProperties.Property<?>, Object> style, Rectangle2D bounds,
            boolean focus, float opacity, Rectangle paintBounds) implements PaintState {
    }

    /** Returns the paint-relevant state used for repaint detection. */
    protected PaintState paintState() {
        return basePaintState();
    }

    /** Captures the fields shared by all node types for repaint detection. */
    protected final BasePaintState basePaintState() {
        return new BasePaintState(Map.copyOf(style.values), (Rectangle2D) bounds.clone(), focus, effectiveOpacity(),
                paintBounds());
    }

    /**
     * Returns the region that must be repainted because this node's paint state
     * changed since the last commit, or {@code null} when nothing changed.
     *
     * <p>
     * New nodes have no baseline and return their current {@link #paintBounds()}.
     * Changed nodes return the union of the old and current paint bounds so that
     * moved nodes and removed shadows are cleared.
     */
    public Rectangle dirtyRegion() {
        PaintState current = paintState();
        if (lastCommitted == null)
            return paintBounds();
        if (current.equals(lastCommitted))
            return null;
        return paintBounds().union(lastCommitted.paintBounds());
    }

    /** Bounds including box and text shadows. */
    public Rectangle paintBounds() {
        Rectangle rectangle = bounds.getBounds();
        for (var property : List.of(StyleProperties.BOX_SHADOW, StyleProperties.TEXT_SHADOW)) {
            Style.Shadow shadow = style.get(property, null);
            if (shadow != null) {
                Rectangle shadowBounds = bounds.getBounds();
                shadowBounds.grow((int) Math.ceil(shadow.blur() * 2 + 2), (int) Math.ceil(shadow.blur() * 2 + 2));
                shadowBounds.translate((int) Math.floor(shadow.offsetX()), (int) Math.floor(shadow.offsetY()));
                rectangle = rectangle.union(shadowBounds);
            }
        }
        return rectangle;
    }

    /** Paint bounds of the last committed state, used for removed nodes. */
    public Rectangle lastCommittedBounds() {
        return lastCommitted == null ? paintBounds() : lastCommitted.paintBounds();
    }

    /** Records the current paint state as the baseline for the next diff. */
    public void commitPaintState() {
        lastCommitted = paintState();
        for (Node child : children)
            child.commitPaintState();
    }

    /** Effective opacity including all ancestors. */
    public float effectiveOpacity() {
        float opacity = 1;
        for (Node ancestor = this; ancestor != null; ancestor = ancestor.parent)
            opacity *= (float) ancestor.style.number(StyleProperties.OPACITY, 1);
        return opacity;
    }
}
