package com.github.ruediste.jreact.layout;

import java.util.Map;

import com.antepod.taffy.geometry.Point;
import com.antepod.taffy.geometry.Rect;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;

/**
 * Computed layout for one node. Positions are in the parent's border-box
 * coordinate system, except for absolute/fixed nodes, whose positions are
 * reported relative to their containing block's border box.
 */
public final class Layout {

    /**
     * Memoized results of {@link LayoutNode#measure} for the current layout
     * pass, keyed by the exact measure inputs. Cleared at the start of each
     * pass so results are never reused across independent layout computations.
     */
    Map<MeasureKey, Size<java.lang.Float>> measuredCache;

    /**
     * Key for memoizing {@link LayoutNode#measure} results within one layout
     * pass.
     */
    static final class MeasureKey {
        final Size<AvailableSpace> knownDimensions;
        final Size<AvailableSpace> availableSpace;

        MeasureKey(Size<AvailableSpace> knownDimensions, Size<AvailableSpace> availableSpace) {
            this.knownDimensions = knownDimensions;
            this.availableSpace = availableSpace;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof MeasureKey other)) {
                return false;
            }
            return java.util.Objects.equals(knownDimensions, other.knownDimensions)
                    && java.util.Objects.equals(availableSpace, other.availableSpace);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(knownDimensions, availableSpace);
        }
    }

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
    public Rect<java.lang.Float> border = new Rect<>(0f, 0f, 0f, 0f);

    /** Resolved padding widths, in pixels. */
    public Rect<java.lang.Float> padding = new Rect<>(0f, 0f, 0f, 0f);

    /** Resolved margins, in pixels. */
    public Rect<java.lang.Float> margin = new Rect<>(0f, 0f, 0f, 0f);

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
        return new Point<>(x, y);
    }

    public Size<java.lang.Float> size() {
        return new Size<>(width, height);
    }

    public Size<java.lang.Float> contentSize() {
        return new Size<>(contentWidth, contentHeight);
    }

    public Size<java.lang.Float> scrollbarSize() {
        return new Size<>(scrollbarWidth, scrollbarHeight);
    }
}
