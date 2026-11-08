package com.github.ruediste.jreact;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.antepod.taffy.geometry.Line;
import com.antepod.taffy.geometry.Point;
import com.antepod.taffy.geometry.Rect;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AlignContent;
import com.antepod.taffy.style.AlignItems;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.style.BoxSizing;
import com.antepod.taffy.style.Clear;
import com.antepod.taffy.style.Dimension;
import com.antepod.taffy.style.Direction;
import com.antepod.taffy.style.Display;
import com.antepod.taffy.style.FlexDirection;
import com.antepod.taffy.style.FlexWrap;
import com.antepod.taffy.style.Float;
import com.antepod.taffy.style.GridAutoFlow;
import com.antepod.taffy.style.GridPlacement;
import com.antepod.taffy.style.GridTemplateArea;
import com.antepod.taffy.style.GridTemplateComponent;
import com.antepod.taffy.style.LengthPercentage;
import com.antepod.taffy.style.LengthPercentageAuto;
import com.antepod.taffy.style.Overflow;
import com.antepod.taffy.style.Position;
import com.antepod.taffy.style.TextAlign;
import com.antepod.taffy.style.TrackSizingFunction;

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

        public void setNullable(Style style, T value) {
            if (value == null) {
                style.values.remove(this);
            } else {
                style.values.put(this, value);
            }
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

    public static final DoubleProperty GROW = new DoubleProperty("grow");
    public static final DoubleProperty SHRINK = new DoubleProperty("shrink");
    public static final Property<Color> BACKGROUND = new ValueProperty<>("background");
    public static final Property<Color> COLOR = new ValueProperty<>("color");
    public static final Property<Style.Border> BORDER_DECORATION = new ValueProperty<>("border-decoration");
    public static final DoubleProperty RADIUS = new DoubleProperty("radius");
    public static final Property<String> FONT_FAMILY = new ValueProperty<>("font-family");
    public static final DoubleProperty FONT_SIZE = new DoubleProperty("font-size");
    public static final Property<Boolean> BOLD = new ValueProperty<>("bold");
    public static final Property<Boolean> ITALIC = new ValueProperty<>("italic");
    public static final DoubleProperty LINE_HEIGHT = new DoubleProperty("line-height");
    public static final Property<Style.Shadow> BOX_SHADOW = new ValueProperty<>("box-shadow");
    public static final Property<Style.Shadow> TEXT_SHADOW = new ValueProperty<>("text-shadow");
    public static final DoubleProperty OPACITY = new DoubleProperty("opacity");

    // CSS layout properties used by the layout engine.

    public static final Property<Display> DISPLAY = new ValueProperty<>("display");
    public static final Property<Position> POSITION = new ValueProperty<>("position");
    public static final Property<Direction> WRITING_DIRECTION = new ValueProperty<>("writing-direction");
    public static final Property<Float> FLOAT_MODE = new ValueProperty<>("float");
    public static final Property<Clear> CLEAR = new ValueProperty<>("clear");
    public static final Property<BoxSizing> BOX_SIZING = new ValueProperty<>("box-sizing");
    public static final Property<TextAlign> TEXT_ALIGN = new ValueProperty<>("text-align");
    public static final Property<AlignItems> ALIGN_ITEMS = new ValueProperty<>("align-items");
    public static final Property<AlignItems> ALIGN_SELF = new ValueProperty<>("align-self");
    public static final Property<AlignContent> ALIGN_CONTENT = new ValueProperty<>("align-content");
    public static final Property<AlignContent> JUSTIFY_CONTENT = new ValueProperty<>("justify-content");
    public static final Property<AlignItems> JUSTIFY_ITEMS = new ValueProperty<>("justify-items");
    public static final Property<AlignItems> JUSTIFY_SELF = new ValueProperty<>("justify-self");
    public static final Property<FlexDirection> FLEX_DIRECTION = new ValueProperty<>("flex-direction");
    public static final Property<FlexWrap> FLEX_WRAP = new ValueProperty<>("flex-wrap");
    public static final Property<Dimension> FLEX_BASIS = new ValueProperty<>("flex-basis");
    public static final Property<Size<Dimension>> SIZE = new ValueProperty<>("size");
    public static final Property<Size<Dimension>> MIN_SIZE = new ValueProperty<>("min-size");
    public static final Property<Size<Dimension>> MAX_SIZE = new ValueProperty<>("max-size");
    public static final Property<java.lang.Float> ASPECT_RATIO = new ValueProperty<>("aspect-ratio");
    public static final Property<Size<LengthPercentage>> GAP = new ValueProperty<>("gap");
    public static final Property<Rect<LengthPercentageAuto>> MARGIN = new ValueProperty<>("margin");
    public static final Property<Rect<LengthPercentage>> PADDING = new ValueProperty<>("padding");
    public static final Property<Rect<LengthPercentage>> BORDER = new ValueProperty<>("border");
    public static final Property<Rect<LengthPercentageAuto>> INSET = new ValueProperty<>("inset");
    public static final Property<Boolean> ITEM_IS_TABLE = new ValueProperty<>("item-is-table");
    public static final Property<Boolean> ITEM_IS_REPLACED = new ValueProperty<>("item-is-replaced");
    public static final Property<java.lang.Float> SCROLLBAR_WIDTH = new ValueProperty<>("scrollbar-width");
    public static final Property<Point<Overflow>> OVERFLOW = new ValueProperty<>("overflow");
    public static final Property<GridAutoFlow> GRID_AUTO_FLOW = new ValueProperty<>("grid-auto-flow");
    public static final Property<Line<GridPlacement>> GRID_ROW = new ValueProperty<>("grid-row");
    public static final Property<Line<GridPlacement>> GRID_COLUMN = new ValueProperty<>("grid-column");
    public static final Property<List<GridTemplateComponent>> GRID_TEMPLATE_ROWS = new ValueProperty<>(
            "grid-template-rows");
    public static final Property<List<GridTemplateComponent>> GRID_TEMPLATE_COLUMNS = new ValueProperty<>(
            "grid-template-columns");
    public static final Property<List<TrackSizingFunction>> GRID_AUTO_ROWS = new ValueProperty<>("grid-auto-rows");
    public static final Property<List<TrackSizingFunction>> GRID_AUTO_COLUMNS = new ValueProperty<>(
            "grid-auto-columns");
    public static final Property<List<List<String>>> GRID_TEMPLATE_ROW_NAMES = new ValueProperty<>(
            "grid-template-row-names");
    public static final Property<List<List<String>>> GRID_TEMPLATE_COLUMN_NAMES = new ValueProperty<>(
            "grid-template-column-names");
    public static final Property<List<GridTemplateArea>> GRID_TEMPLATE_AREAS = new ValueProperty<>(
            "grid-template-areas");
    public static final Property<Integer> GRID_TEMPLATE_AREA_ROW_COUNT = new ValueProperty<>(
            "grid-template-area-row-count");
    public static final Property<Integer> GRID_TEMPLATE_AREA_COLUMN_COUNT = new ValueProperty<>(
            "grid-template-area-column-count");
}
