package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.StyleProperties.*;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Objects;

import com.github.ruediste.jreact.StyleProperties.DoubleProperty;
import com.github.ruediste.jreact.StyleProperties.Property;

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

    final HashMap<Property<?>, Object> values = new HashMap<>();

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
        return put(DIRECTION, value);
    }

    public Style flexWrap(Wrap value) {
        return put(WRAP, value);
    }

    public Style order(int value) {
        return put(ORDER, value);
    }

    public Style flexGrow(double value) {
        return dimension(GROW, value);
    }

    public Style flexShrink(double value) {
        return dimension(SHRINK, value);
    }

    public Style justifyContent(Justify value) {
        return put(JUSTIFY, value);
    }

    public Style alignItems(Align value) {
        return put(ALIGN, value);
    }

    public Style width(double value) {
        return dimension(WIDTH, value);
    }

    public Style height(double value) {
        return dimension(HEIGHT, value);
    }

    public Style widthAuto() {
        return put(WIDTH, Double.NaN);
    }

    public Style heightAuto() {
        return put(HEIGHT, Double.NaN);
    }

    public Style margin(double value) {
        return margin(value, value, value, value);
    }

    public Style margin(double top, double right, double bottom, double left) {
        return put(MARGIN, new Insets(top, right, bottom, left));
    }

    public Style padding(double value) {
        return padding(value, value, value, value);
    }

    public Style padding(double top, double right, double bottom, double left) {
        return put(PADDING, new Insets(top, right, bottom, left));
    }

    public Style backgroundColor(Color value) {
        return put(BACKGROUND, value);
    }

    public Style color(Color value) {
        return put(COLOR, value);
    }

    public Style border(double width, Color color) {
        Edge edge = new Edge(width, color);
        return border(new Border(edge, edge, edge, edge));
    }

    public Style border(Border value) {
        return put(BORDER, value);
    }

    public Style borderRadius(double value) {
        return dimension(RADIUS, value);
    }

    public Style fontFamily(String value) {
        return put(FONT_FAMILY, value);
    }

    public Style fontSize(double value) {
        positive(value);
        return put(FONT_SIZE, value);
    }

    public Style fontWeight(boolean bold) {
        return put(BOLD, bold);
    }

    public Style fontStyle(boolean italic) {
        return put(ITALIC, italic);
    }

    public Style textAlign(TextAlign value) {
        return put(TEXT_ALIGN, value);
    }

    public Style lineHeight(double value) {
        positive(value);
        return put(LINE_HEIGHT, value);
    }

    public Style lineHeightNormal() {
        return put(LINE_HEIGHT, Double.NaN);
    }

    public Style boxShadow(Shadow value) {
        return put(BOX_SHADOW, value);
    }

    public Style textShadow(Shadow value) {
        return put(TEXT_SHADOW, value);
    }

    public Style opacity(double value) {
        nonNegative(value);
        if (value > 1)
            throw new IllegalArgumentException("Opacity must be between 0 and 1");
        return put(OPACITY, value);
    }

    private Style dimension(DoubleProperty property, double value) {
        nonNegative(value);
        return put(property, value);
    }

    private Style put(Property<?> property, Object value) {
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

    <T> T get(Property<T> property, T fallback) {
        return property.get(this, fallback);
    }

    double number(DoubleProperty property, double fallback) {
        return property.get(this, fallback);
    }

    public double width() {
        return number(WIDTH, Double.NaN);
    }

    public double height() {
        return number(HEIGHT, Double.NaN);
    }

    public Insets margin() {
        return get(MARGIN, new Insets(0, 0, 0, 0));
    }

    public Insets inset() {
        Insets padding = get(PADDING, new Insets(0, 0, 0, 0));
        Border border = get(BORDER, null);
        if (border == null)
            return padding;
        Insets widths = border.widths();
        return new Insets(padding.top + widths.top, padding.right + widths.right,
                padding.bottom + widths.bottom, padding.left + widths.left);
    }

    public Color color() {
        return get(COLOR, new Color(32, 36, 40));
    }

    public Font font() {
        int flags = (get(BOLD, false) ? Font.BOLD : 0) | (get(ITALIC, false) ? Font.ITALIC : 0);
        return new Font(get(FONT_FAMILY, Font.DIALOG), flags, 14)
                .deriveFont((float) number(FONT_SIZE, 14));
    }
}
