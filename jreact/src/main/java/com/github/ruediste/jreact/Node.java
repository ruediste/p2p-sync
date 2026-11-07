package com.github.ruediste.jreact;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Represents one primitive in the node tree produced by a render.
 *
 * <p>
 * Connects declarative content and callbacks to the downstream UI stages:
 * {@link Styles} resolves its inline and scoped styles, layout fills its bounds
 * and text lines, and {@link Renderer} paints it. {@link JReactPanel} supplies
 * interaction flags, dispatches callbacks, and manages native input/select
 * editors described by nodes.
 *
 * <p>
 * Nodes are mutable and recreated on each render. Their keys associate
 * corresponding primitives across renders, but retained hook state belongs to
 * {@link Fiber}, not to this tree. The association lets {@link JReactPanel}
 * distinguish a new description of an existing UI element from unrelated
 * output:
 * <ul>
 * <li>Restore hover, pressed, and focus state onto the current node, so a
 * render
 * during an interaction does not by itself lose the interaction target.</li>
 * <li>Retain a compatible native input/select editor instead of replacing the
 * Swing widget on every render, avoiding unnecessary disruption of focus,
 * caret, and selection state.</li>
 * <li>Compare old and new paint snapshots, repainting changed bounds and
 * clearing
 * old or removed output rather than treating every new node object as new
 * UI.</li>
 * </ul>
 * The keys do not reuse node objects or skip component rendering. Current
 * content and callbacks come from the newly rendered tree; the host retains
 * only
 * the associated interaction state, native editors, and comparison snapshots.
 *
 * <p>
 * Bounds use root-relative logical pixels;
 * text-line coordinates are relative to their containing node's bounds.
 */
public final class Node {
    /** Distinguishes painted primitives from native-editor descriptions. */
    public enum Kind {
        DIV, SPAN, TEXT, INPUT, SELECT, ICON
    }

    /**
     * Associates a primitive with its owning fiber and its render-call position.
     *
     * <p>
     * Equality connects successive node descriptions for interaction state,
     * native-editor retention, and repaint comparisons. This is positional
     * identity within a retained component, not matching by text, style ID, or
     * node kind. Inserting earlier primitives can shift positions and associate
     * different output; use stable component boundaries when identity matters.
     * Native-editor reuse additionally requires a compatible node kind.
     */
    public record Key(Object component, int position) {
    }

    /** A measured text fragment with node-local horizontal and baseline offsets. */
    public record Line(String text, double x, double baseline) {
    }

    public final Kind kind;
    public final Key key;
    public final Style inline;
    public Style style;
    public final List<Node> children = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();
    public final Rectangle2D.Double bounds = new Rectangle2D.Double();
    public final String text;
    public Node parent;
    public boolean hover, active, focus, disabled;
    public boolean focusable;
    public String accessibleName;
    public Runnable onClick;
    public DoubleConsumer onPointer;
    public IntConsumer onKey;
    public Consumer<String> onChange;
    public List<String> options = List.of();
    public javax.swing.Icon icon;
    public Double sliderFraction;
    public java.awt.Color sliderColor;
    final List<Styles.NodeStyleRules> declarations;

    Node(Kind kind, Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations) {
        this.kind = kind;
        this.key = key;
        this.text = text;
        this.inline = inline;
        this.style = inline;
        this.declarations = declarations;
    }

    public Node onClick(Runnable action) {
        onClick = action;
        focusable = true;
        return this;
    }

    public Node onPointer(DoubleConsumer action) {
        onPointer = action;
        focusable = true;
        return this;
    }

    public Node disabled(boolean value) {
        disabled = value;
        return this;
    }

    public Node accessibleName(String value) {
        accessibleName = value;
        return this;
    }

    public List<Node> descendants() {
        var result = new ArrayList<Node>();
        result.add(this);
        children.forEach(child -> result.addAll(child.descendants()));
        return List.copyOf(result);
    }
}