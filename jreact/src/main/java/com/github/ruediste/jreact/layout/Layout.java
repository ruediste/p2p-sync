package com.github.ruediste.jreact.layout;

/**
 * Computed layout for one node. Positions are in the parent's border-box
 * coordinate system, except for absolute/fixed nodes, whose positions are
 * reported relative to their containing block's border box.
 */
public final class Layout {

    /**
     * Horizontal position of the node's border box, in the parent's border-box
     * coordinate system. For absolutely or fixed positioned nodes the position
     * is relative to the containing block's border box instead.
     */
    public float x;

    /**
     * Vertical position of the node's border box, in the parent's border-box
     * coordinate system. For absolutely or fixed positioned nodes the position
     * is relative to the containing block's border box instead.
     */
    public float y;

    /** Width of the node's border box, in pixels. */
    public float width;

    /** Height of the node's border box, in pixels. */
    public float height;

    /** Reachable content extent from the scroll origin, in pixels. */
    public float contentWidth;

    /** Reachable content extent from the scroll origin, in pixels. */
    public float contentHeight;

    /** Width of the scrollbar reserved by the last layout pass, in pixels. */
    public float scrollbarWidth;

    /** Height of the scrollbar reserved by the last layout pass, in pixels. */
    public float scrollbarHeight;

    /** Resolved border widths, in pixels. */
    public Rect<java.lang.Float> border = Rect.all(0f);

    /** Resolved padding widths, in pixels. */
    public Rect<java.lang.Float> padding = Rect.all(0f);

    /** Resolved margins, in pixels. */
    public Rect<java.lang.Float> margin = Rect.all(0f);

    /**
     * Track and item details of the grid layout computed for this node, or
     * {@code null} if the node is not a grid container or no layout has been
     * computed yet.
     */
    public DetailedGridInfo detailedGridInfo;

    /**
     * Node used to resolve the insets and percentages of an absolutely or fixed
     * positioned node. Fixed nodes keep this {@code null} because they are
     * positioned relative to the viewport.
     */
    public LayoutNode containingBlock;

    /**
     * Absolute horizontal position of this node in root coordinates, valid
     * after layout has been computed.
     */
    public float absX;

    /**
     * Absolute vertical position of this node in root coordinates, valid after
     * layout has been computed.
     */
    public float absY;

    /**
     * Whether this node has already been placed by the out-of-flow pass during
     * the current layout run. Used internally by {@link LayoutEngine}.
     */
    boolean placedOutOfFlow;

    /** Content-box width used by the last layout pass, in pixels. */
    public float usedContentWidth;

    /** Content-box height used by the last layout pass, in pixels. */
    public float usedContentHeight;

    public Point<java.lang.Float> position() {
        return Point.of(x, y);
    }

    public Size<java.lang.Float> size() {
        return Size.of(width, height);
    }

    public Size<java.lang.Float> contentSize() {
        return Size.of(contentWidth, contentHeight);
    }

    public Size<java.lang.Float> scrollbarSize() {
        return Size.of(scrollbarWidth, scrollbarHeight);
    }
}
