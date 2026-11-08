package com.github.ruediste.jreact.layout;

import java.util.List;

import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.tree.Layout;
import com.github.ruediste.jreact.Style;

/**
 * One node of a layout tree.
 *
 * <p>
 * A node owns its style, children and measurement callback, i.e. the inputs to
 * layout. The computed results live in {@link #getLayout()} and the JReact
 * specific layout outputs below; the engine only computes and all persistent
 * state lives in the nodes.
 *
 * <p>
 * This interface is the read-only view used by the layout engine. The tree is
 * modified through {@link NodeImpl}.
 */
public interface LayoutNode {

    /** Layout properties of this node. */
    public Style getStyle();

    /**
     * Measurement callback for leaf nodes. It is only invoked for
     * nodes without children, with a non-{@code none} display and
     * without forced dimensions. Can return null if not implemented.
     */
    Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace);

    /** Parent node, or {@code null} if this node is a root. */
    public LayoutNode getParent();

    /**
     * Child nodes in layout order. Use the add and remove methods of
     * {@link NodeImpl} to keep the {@link #getParent() parent} links
     * consistent.
     */
    public List<LayoutNode> getChildren();

    /** Layout computed for this node by {@link LayoutEngine}. */
    public Layout getLayout();

    /** Replaces the computed Taffy layout of this node. */
    public void setLayout(Layout layout);
}
