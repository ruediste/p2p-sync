package com.github.ruediste.jreact;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.github.ruediste.jreact.layout.AlignContent;
import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.AlignSelf;
import com.github.ruediste.jreact.layout.BoxSizing;
import com.github.ruediste.jreact.layout.Clear;
import com.github.ruediste.jreact.layout.Dimension;
import com.github.ruediste.jreact.layout.Direction;
import com.github.ruediste.jreact.layout.Display;
import com.github.ruediste.jreact.layout.FlexDirection;
import com.github.ruediste.jreact.layout.FlexWrap;
import com.github.ruediste.jreact.layout.Float;
import com.github.ruediste.jreact.layout.GridAutoFlow;
import com.github.ruediste.jreact.layout.GridPlacement;
import com.github.ruediste.jreact.layout.GridTemplateArea;
import com.github.ruediste.jreact.layout.GridTemplateComponent;
import com.github.ruediste.jreact.layout.JustifyContent;
import com.github.ruediste.jreact.layout.LengthPercentage;
import com.github.ruediste.jreact.layout.LengthPercentageAuto;
import com.github.ruediste.jreact.layout.Line;
import com.github.ruediste.jreact.layout.Overflow;
import com.github.ruediste.jreact.layout.Point;
import com.github.ruediste.jreact.layout.Position;
import com.github.ruediste.jreact.layout.Rect;
import com.github.ruediste.jreact.layout.Size;
import com.github.ruediste.jreact.layout.TextAlign;
import com.github.ruediste.jreact.layout.TrackSizingFunction;

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
    public static final Property<Style.Border> BORDER = new ValueProperty<>("border");
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
    public static final Property<TextAlign> LAYOUT_TEXT_ALIGN = new ValueProperty<>("layout-text-align");
    public static final Property<AlignItems> ALIGN_ITEMS = new ValueProperty<>("align-items");
    public static final Property<AlignSelf> ALIGN_SELF = new ValueProperty<>("align-self");
    public static final Property<AlignContent> ALIGN_CONTENT = new ValueProperty<>("align-content");
    public static final Property<JustifyContent> JUSTIFY_CONTENT = new ValueProperty<>("justify-content");
    public static final Property<AlignItems> JUSTIFY_ITEMS = new ValueProperty<>("justify-items");
    public static final Property<AlignSelf> JUSTIFY_SELF = new ValueProperty<>("justify-self");
    public static final Property<FlexDirection> FLEX_DIRECTION = new ValueProperty<>("flex-direction");
    public static final Property<FlexWrap> FLEX_WRAP = new ValueProperty<>("flex-wrap");
    public static final Property<LengthPercentageAuto> FLEX_BASIS = new ValueProperty<>("flex-basis");
    public static final Property<Size<Dimension>> SIZE = new ValueProperty<>("size");
    public static final Property<Size<Dimension>> MIN_SIZE = new ValueProperty<>("min-size");
    public static final Property<Size<Dimension>> MAX_SIZE = new ValueProperty<>("max-size");
    public static final Property<java.lang.Float> ASPECT_RATIO = new ValueProperty<>("aspect-ratio");
    public static final Property<Size<LengthPercentage>> GAP = new ValueProperty<>("gap");
    public static final Property<Rect<LengthPercentageAuto>> LAYOUT_MARGIN = new ValueProperty<>("layout-margin");
    public static final Property<Rect<LengthPercentage>> LAYOUT_PADDING = new ValueProperty<>("layout-padding");
    public static final Property<Rect<LengthPercentage>> LAYOUT_BORDER = new ValueProperty<>("layout-border");
    public static final Property<Rect<LengthPercentageAuto>> LAYOUT_INSET = new ValueProperty<>("layout-inset");
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
