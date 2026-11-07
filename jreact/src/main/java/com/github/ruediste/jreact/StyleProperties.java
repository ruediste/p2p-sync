package com.github.ruediste.jreact;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Typed declarations of the style properties supported by {@link Style}.
 *
 * <p>
 * Each property carries a stable name and stores its value in a {@link Style}'s
 * value map. {@link #properties} records every declared property in declaration
 * order.
 */
public abstract class StyleProperties {
    /** All declared properties, in declaration order. */
    public static final List<Property<?>> properties = new ArrayList<>();

    /** A single typed style property. */
    public abstract static class Property<T> {
        public final String name;

        Property(String name) {
            this.name = name;
            properties.add(this);
        }

        public String formatValue(T value) {
            return value != null ? value.toString() : "";
        }

        public boolean isSet(Style style) {
            return style.values.containsKey(this);
        }

        @SuppressWarnings("unchecked")
        public T get(Style style, T fallback) {
            return (T) style.values.getOrDefault(this, fallback);
        }

        public void set(Style style, T value) {
            style.values.put(this, Objects.requireNonNull(value));
        }
    }

    /** A property whose value is a double. */
    public static class DoubleProperty extends Property<Double> {
        DoubleProperty(String name) {
            super(name);
        }

        public double get(Style style, double fallback) {
            return super.get(style, fallback);
        }
    }

    private static final class ValueProperty<T> extends Property<T> {
        ValueProperty(String name) {
            super(name);
        }
    }

    public static final Property<Style.Direction> DIRECTION = new ValueProperty<>("direction");
    public static final Property<Style.Wrap> WRAP = new ValueProperty<>("wrap");
    public static final Property<Integer> ORDER = new ValueProperty<>("order");
    public static final DoubleProperty GROW = new DoubleProperty("grow");
    public static final DoubleProperty SHRINK = new DoubleProperty("shrink");
    public static final Property<Style.Justify> JUSTIFY = new ValueProperty<>("justify");
    public static final Property<Style.Align> ALIGN = new ValueProperty<>("align");
    public static final DoubleProperty WIDTH = new DoubleProperty("width");
    public static final DoubleProperty HEIGHT = new DoubleProperty("height");
    public static final Property<Style.Insets> MARGIN = new ValueProperty<>("margin");
    public static final Property<Style.Insets> PADDING = new ValueProperty<>("padding");
    public static final Property<Color> BACKGROUND = new ValueProperty<>("background");
    public static final Property<Color> COLOR = new ValueProperty<>("color");
    public static final Property<Style.Border> BORDER = new ValueProperty<>("border");
    public static final DoubleProperty RADIUS = new DoubleProperty("radius");
    public static final Property<String> FONT_FAMILY = new ValueProperty<>("font-family");
    public static final DoubleProperty FONT_SIZE = new DoubleProperty("font-size");
    public static final Property<Boolean> BOLD = new ValueProperty<>("bold");
    public static final Property<Boolean> ITALIC = new ValueProperty<>("italic");
    public static final Property<Style.TextAlign> TEXT_ALIGN = new ValueProperty<>("text-align");
    public static final DoubleProperty LINE_HEIGHT = new DoubleProperty("line-height");
    public static final Property<Style.Shadow> BOX_SHADOW = new ValueProperty<>("box-shadow");
    public static final Property<Style.Shadow> TEXT_SHADOW = new ValueProperty<>("text-shadow");
    public static final DoubleProperty OPACITY = new DoubleProperty("opacity");
}
