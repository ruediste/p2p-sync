package com.github.ruediste.jreact;

import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Mutable, typed declarations for JReact's supported layout and appearance
 * properties, plus node IDs and style-class names used by selectors.
 *
 * <p>
 * The fluent builder records explicitly supplied properties rather than
 * populating every default. This lets {@link Styles} combine matching rules
 * before applying inline overrides. Accessors supply defaults when properties
 * are absent; automatic CSS property inheritance is not implemented.
 *
 * <p>
 * Dimensions use logical pixels and numeric setters validate their ranges.
 * Auto dimensions and normal line height are represented internally by
 * {@link Double#NaN}. Layout interprets dimensions, insets, and flex
 * properties;
 * text measurement and rendering interpret fonts, colors, borders, and shadows.
 * This class does not parse CSS text or perform layout or painting itself.
 */
public final class Style {
    public enum Direction {
        ROW, COLUMN
    }

    public enum Wrap {
        NOWRAP, WRAP, WRAP_REVERSE
    }

    public enum Justify {
        START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY
    }

    public enum Align {
        START, CENTER, END, STRETCH
    }

    public enum TextAlign {
        LEFT, CENTER, RIGHT
    }

    enum Property {
        DIRECTION, WRAP, ORDER, GROW, SHRINK, JUSTIFY, ALIGN, WIDTH, HEIGHT, MARGIN, PADDING,
        BACKGROUND, COLOR, BORDER, RADIUS, FONT_FAMILY, FONT_SIZE, BOLD, ITALIC, TEXT_ALIGN,
        LINE_HEIGHT, BOX_SHADOW, TEXT_SHADOW, OPACITY
    }

    /** Non-negative per-side spacing in top, right, bottom, left order. */
    public record Insets(double top, double right, double bottom, double left) {
        public Insets {
            nonNegative(top);
            nonNegative(right);
            nonNegative(bottom);
            nonNegative(left);
        }

        public double horizontal() {
            return left + right;
        }

        public double vertical() {
            return top + bottom;
        }
    }

    /** Width and solid color of a single border edge. */
    public record Edge(double width, Color color) {
        public Edge {
            nonNegative(width);
            Objects.requireNonNull(color);
        }
    }

    /** Per-side solid borders whose widths contribute to content insets. */
    public record Border(Edge top, Edge right, Edge bottom, Edge left) {
        public Border {
            Objects.requireNonNull(top);
            Objects.requireNonNull(right);
            Objects.requireNonNull(bottom);
            Objects.requireNonNull(left);
        }

        public Insets widths() {
            return new Insets(top.width, right.width, bottom.width, left.width);
        }
    }

    /** One shadow with signed offsets, non-negative blur, and a solid color. */
    public record Shadow(double offsetX, double offsetY, double blur, Color color) {
        public Shadow {
            finite(offsetX);
            finite(offsetY);
            nonNegative(blur);
            Objects.requireNonNull(color);
        }
    }

    final EnumMap<Property, Object> values = new EnumMap<>(Property.class);
    String id;
    String className = "";

    public Style id(String value) {
        id = Objects.requireNonNull(value);
        return this;
    }

    public Style className(String value) {
        className = Objects.requireNonNull(value);
        return this;
    }

    public Style flexDirection(Direction value) {
        return put(Property.DIRECTION, value);
    }

    public Style flexWrap(Wrap value) {
        return put(Property.WRAP, value);
    }

    public Style order(int value) {
        return put(Property.ORDER, value);
    }

    public Style flexGrow(double value) {
        return dimension(Property.GROW, value);
    }

    public Style flexShrink(double value) {
        return dimension(Property.SHRINK, value);
    }

    public Style justifyContent(Justify value) {
        return put(Property.JUSTIFY, value);
    }

    public Style alignItems(Align value) {
        return put(Property.ALIGN, value);
    }

    public Style width(double value) {
        return dimension(Property.WIDTH, value);
    }

    public Style height(double value) {
        return dimension(Property.HEIGHT, value);
    }

    public Style widthAuto() {
        return put(Property.WIDTH, Double.NaN);
    }

    public Style heightAuto() {
        return put(Property.HEIGHT, Double.NaN);
    }

    public Style margin(double value) {
        return margin(value, value, value, value);
    }

    public Style margin(double top, double right, double bottom, double left) {
        return put(Property.MARGIN, new Insets(top, right, bottom, left));
    }

    public Style padding(double value) {
        return padding(value, value, value, value);
    }

    public Style padding(double top, double right, double bottom, double left) {
        return put(Property.PADDING, new Insets(top, right, bottom, left));
    }

    public Style backgroundColor(Color value) {
        return put(Property.BACKGROUND, value);
    }

    public Style color(Color value) {
        return put(Property.COLOR, value);
    }

    public Style border(double width, Color color) {
        Edge edge = new Edge(width, color);
        return border(new Border(edge, edge, edge, edge));
    }

    public Style border(Border value) {
        return put(Property.BORDER, value);
    }

    public Style borderRadius(double value) {
        return dimension(Property.RADIUS, value);
    }

    public Style fontFamily(String value) {
        return put(Property.FONT_FAMILY, value);
    }

    public Style fontSize(double value) {
        positive(value);
        return put(Property.FONT_SIZE, value);
    }

    public Style fontWeight(boolean bold) {
        return put(Property.BOLD, bold);
    }

    public Style fontStyle(boolean italic) {
        return put(Property.ITALIC, italic);
    }

    public Style textAlign(TextAlign value) {
        return put(Property.TEXT_ALIGN, value);
    }

    public Style lineHeight(double value) {
        positive(value);
        return put(Property.LINE_HEIGHT, value);
    }

    public Style lineHeightNormal() {
        return put(Property.LINE_HEIGHT, Double.NaN);
    }

    public Style boxShadow(Shadow value) {
        return put(Property.BOX_SHADOW, value);
    }

    public Style textShadow(Shadow value) {
        return put(Property.TEXT_SHADOW, value);
    }

    public Style opacity(double value) {
        nonNegative(value);
        if (value > 1)
            throw new IllegalArgumentException("Opacity must be between 0 and 1");
        return put(Property.OPACITY, value);
    }

    private Style dimension(Property property, double value) {
        nonNegative(value);
        return put(property, value);
    }

    private Style put(Property property, Object value) {
        values.put(property, Objects.requireNonNull(value));
        return this;
    }

    static void finite(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Value must be finite");
    }

    static void nonNegative(double value) {
        finite(value);
        if (value < 0)
            throw new IllegalArgumentException("Value must be non-negative");
    }

    static void positive(double value) {
        finite(value);
        if (value <= 0)
            throw new IllegalArgumentException("Value must be positive");
    }

    @SuppressWarnings("unchecked")
    <T> T get(Property property, T fallback) {
        return (T) values.getOrDefault(property, fallback);
    }

    double number(Property property, double fallback) {
        return get(property, fallback);
    }

    public double width() {
        return number(Property.WIDTH, Double.NaN);
    }

    public double height() {
        return number(Property.HEIGHT, Double.NaN);
    }

    public Insets margin() {
        return get(Property.MARGIN, new Insets(0, 0, 0, 0));
    }

    public Insets inset() {
        Insets padding = get(Property.PADDING, new Insets(0, 0, 0, 0));
        Border border = get(Property.BORDER, null);
        if (border == null)
            return padding;
        Insets widths = border.widths();
        return new Insets(padding.top + widths.top, padding.right + widths.right,
                padding.bottom + widths.bottom, padding.left + widths.left);
    }

    public Color color() {
        return get(Property.COLOR, new Color(32, 36, 40));
    }

    public Font font() {
        int flags = (get(Property.BOLD, false) ? Font.BOLD : 0) | (get(Property.ITALIC, false) ? Font.ITALIC : 0);
        return new Font(get(Property.FONT_FAMILY, Font.DIALOG), flags, 14)
                .deriveFont((float) number(Property.FONT_SIZE, 14));
    }
}