package com.github.ruediste.jreact.layout;

/**
 * Computed layout for one node. Positions are in the parent's border-box
 * coordinate system, except for absolute/fixed nodes, whose positions are
 * reported relative to their containing block's border box.
 */
public final class Layout {

    public int order;
    public float x;
    public float y;
    public float width;
    public float height;

    /** Reachable content extent from the scroll origin, in pixels. */
    public float contentWidth;
    public float contentHeight;

    public float scrollbarWidth;
    public float scrollbarHeight;

    public Rect<java.lang.Float> border = Rect.all(0f);
    public Rect<java.lang.Float> padding = Rect.all(0f);
    public Rect<java.lang.Float> margin = Rect.all(0f);

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

    /** Batch property read. Returns a single value for one key, an array otherwise. */
    public Object get(String... keys) {
        if (keys.length == 0) {
            return null;
        }
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = getProperty(keys[i]);
        }
        return values.length == 1 ? values[0] : values;
    }

    private Object getProperty(String key) {
        return switch (key) {
            case "order" -> order;
            case "position" -> position();
            case "x" -> x;
            case "y" -> y;
            case "size" -> size();
            case "width" -> width;
            case "height" -> height;
            case "contentSize" -> contentSize();
            case "contentWidth" -> contentWidth;
            case "contentHeight" -> contentHeight;
            case "scrollbarSize" -> scrollbarSize();
            case "scrollbarWidth" -> scrollbarWidth;
            case "scrollbarHeight" -> scrollbarHeight;
            case "border" -> border;
            case "borderLeft" -> border.left;
            case "borderRight" -> border.right;
            case "borderTop" -> border.top;
            case "borderBottom" -> border.bottom;
            case "padding" -> padding;
            case "paddingLeft" -> padding.left;
            case "paddingRight" -> padding.right;
            case "paddingTop" -> padding.top;
            case "paddingBottom" -> padding.bottom;
            case "margin" -> margin;
            case "marginLeft" -> margin.left;
            case "marginRight" -> margin.right;
            case "marginTop" -> margin.top;
            case "marginBottom" -> margin.bottom;
            default -> throw new IllegalArgumentException("Unknown property path: " + key);
        };
    }
}
