package com.github.ruediste.jreact;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import com.antepod.taffy.style.GridTemplateAreas;
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

    /** Updates a Taffy style builder for one explicitly declared property. */
    @FunctionalInterface
    public interface TaffyUpdater<T> {
        void update(Style style, T value, com.antepod.taffy.style.Style.Builder builder);
    }

    /** A single typed style property. */
    public abstract static class Property<T> {
        public final String name;
        private final TaffyUpdater<T> taffyUpdater;

        Property(String name) {
            this(name, null);
        }

        Property(String name, TaffyUpdater<T> taffyUpdater) {
            this.name = name;
            this.taffyUpdater = taffyUpdater;
            properties.add(this);
        }

        public String formatValue(T value) {
            return value != null ? value.toString() : "";
        }

        public boolean isSet(Style style) {
            return style.values.containsKey(this);
        }

        @SuppressWarnings("unchecked")
        void applyTaffy(Style style, Object value, com.antepod.taffy.style.Style.Builder builder) {
            if (taffyUpdater != null)
                taffyUpdater.update(style, (T) value, builder);
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
    public static class DoubleProperty extends Property<java.lang.Double> {
        DoubleProperty(String name) {
            super(name);
        }

        DoubleProperty(String name, TaffyUpdater<java.lang.Double> updater) {
            super(name, updater);
        }

        public double get(Style style, double fallback) {
            return super.get(style, fallback);
        }
    }

    private static final class ValueProperty<T> extends Property<T> {
        ValueProperty(String name) {
            super(name);
        }

        ValueProperty(String name, TaffyUpdater<T> updater) {
            super(name, updater);
        }
    }

    public static com.antepod.taffy.style.Style buildTaffyStyle(Style style) {
        var builder = com.antepod.taffy.style.Style.builder();
        for (var entry : style.values.entrySet())
            entry.getKey().applyTaffy(style, entry.getValue(), builder);
        return builder.build();
    }

    public static final DoubleProperty GROW = new DoubleProperty("grow",
            (style, value, builder) -> builder.flexGrow(value.floatValue()));
    public static final DoubleProperty SHRINK = new DoubleProperty("shrink",
            (style, value, builder) -> builder.flexShrink(value.floatValue()));
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

    public static final Property<Display> DISPLAY = new ValueProperty<>("display",
            (style, value, builder) -> builder.display(value));
    public static final Property<Position> POSITION = new ValueProperty<>("position",
            (style, value, builder) -> builder.position(value));
    public static final Property<Direction> WRITING_DIRECTION = new ValueProperty<>("writing-direction",
            (style, value, builder) -> builder.direction(value));
    public static final Property<Float> FLOAT_MODE = new ValueProperty<>("float",
            (style, value, builder) -> builder.floatValue(value));
    public static final Property<Clear> CLEAR = new ValueProperty<>("clear",
            (style, value, builder) -> builder.clear(value));
    public static final Property<BoxSizing> BOX_SIZING = new ValueProperty<>("box-sizing",
            (style, value, builder) -> builder.boxSizing(value));
    public static final Property<TextAlign> TEXT_ALIGN = new ValueProperty<>("text-align",
            (style, value, builder) -> builder.textAlign(value));
    public static final Property<AlignItems> ALIGN_ITEMS = new ValueProperty<>("align-items",
            (style, value, builder) -> builder.alignItems(Optional.of(value)));
    public static final Property<AlignItems> ALIGN_SELF = new ValueProperty<>("align-self",
            (style, value, builder) -> builder.alignSelf(Optional.of(value)));
    public static final Property<AlignContent> ALIGN_CONTENT = new ValueProperty<>("align-content",
            (style, value, builder) -> builder.alignContent(Optional.of(value)));
    public static final Property<AlignContent> JUSTIFY_CONTENT = new ValueProperty<>("justify-content",
            (style, value, builder) -> builder.justifyContent(Optional.of(value)));
    public static final Property<AlignItems> JUSTIFY_ITEMS = new ValueProperty<>("justify-items",
            (style, value, builder) -> builder.justifyItems(Optional.of(value)));
    public static final Property<AlignItems> JUSTIFY_SELF = new ValueProperty<>("justify-self",
            (style, value, builder) -> builder.justifySelf(Optional.of(value)));
    public static final Property<FlexDirection> FLEX_DIRECTION = new ValueProperty<>("flex-direction",
            (style, value, builder) -> builder.flexDirection(value));
    public static final Property<FlexWrap> FLEX_WRAP = new ValueProperty<>("flex-wrap",
            (style, value, builder) -> builder.flexWrap(value));
    public static final Property<Dimension> FLEX_BASIS = new ValueProperty<>("flex-basis",
            (style, value, builder) -> builder.flexBasis(value));
    public static final Property<Size<Dimension>> SIZE = new ValueProperty<>("size",
            (style, value, builder) -> builder.size(value));
    public static final Property<Size<Dimension>> MIN_SIZE = new ValueProperty<>("min-size",
            (style, value, builder) -> builder.minSize(value));
    public static final Property<Size<Dimension>> MAX_SIZE = new ValueProperty<>("max-size",
            (style, value, builder) -> builder.maxSize(value));
    public static final Property<java.lang.Float> ASPECT_RATIO = new ValueProperty<>("aspect-ratio",
            (style, value, builder) -> builder.aspectRatio(Optional.of(value)));
    public static final Property<Size<LengthPercentage>> GAP = new ValueProperty<>("gap",
            (style, value, builder) -> builder.gap(value));
    public static final Property<Rect<LengthPercentageAuto>> MARGIN = new ValueProperty<>("margin",
            (style, value, builder) -> builder.margin(value));
    public static final Property<Rect<LengthPercentage>> PADDING = new ValueProperty<>("padding",
            (style, value, builder) -> builder.padding(value));
    public static final Property<Rect<LengthPercentage>> BORDER = new ValueProperty<>("border",
            (style, value, builder) -> builder.border(value));
    public static final Property<Rect<LengthPercentageAuto>> INSET = new ValueProperty<>("inset",
            (style, value, builder) -> builder.inset(value));
    public static final Property<Boolean> ITEM_IS_TABLE = new ValueProperty<>("item-is-table",
            (style, value, builder) -> builder.itemIsTable(value));
    public static final Property<Boolean> ITEM_IS_REPLACED = new ValueProperty<>("item-is-replaced",
            (style, value, builder) -> builder.itemIsReplaced(value));
    public static final Property<java.lang.Float> SCROLLBAR_WIDTH = new ValueProperty<>("scrollbar-width",
            (style, value, builder) -> builder.scrollbarWidth(value));
    public static final Property<Point<Overflow>> OVERFLOW = new ValueProperty<>("overflow",
            (style, value, builder) -> builder.overflow(value));
    public static final Property<GridAutoFlow> GRID_AUTO_FLOW = new ValueProperty<>("grid-auto-flow",
            (style, value, builder) -> builder.gridAutoFlow(value));
    public static final Property<Line<GridPlacement>> GRID_ROW = new ValueProperty<>("grid-row",
            (style, value, builder) -> builder.gridRow(value));
    public static final Property<Line<GridPlacement>> GRID_COLUMN = new ValueProperty<>("grid-column",
            (style, value, builder) -> builder.gridColumn(value));
    public static final Property<List<GridTemplateComponent>> GRID_TEMPLATE_ROWS = new ValueProperty<>(
            "grid-template-rows", (style, value, builder) -> builder.gridTemplateRows(value));
    public static final Property<List<GridTemplateComponent>> GRID_TEMPLATE_COLUMNS = new ValueProperty<>(
            "grid-template-columns", (style, value, builder) -> builder.gridTemplateColumns(value));
    public static final Property<List<TrackSizingFunction>> GRID_AUTO_ROWS = new ValueProperty<>("grid-auto-rows",
            (style, value, builder) -> builder.gridAutoRows(value));
    public static final Property<List<TrackSizingFunction>> GRID_AUTO_COLUMNS = new ValueProperty<>(
            "grid-auto-columns", (style, value, builder) -> builder.gridAutoColumns(value));
    public static final Property<List<List<String>>> GRID_TEMPLATE_ROW_NAMES = new ValueProperty<>(
            "grid-template-row-names", (style, value, builder) -> builder.gridTemplateRowNames(value));
    public static final Property<List<List<String>>> GRID_TEMPLATE_COLUMN_NAMES = new ValueProperty<>(
            "grid-template-column-names", (style, value, builder) -> builder.gridTemplateColumnNames(value));
    public static final Property<List<GridTemplateArea>> GRID_TEMPLATE_AREAS = new ValueProperty<>(
            "grid-template-areas", (style, value, builder) -> builder.gridTemplateAreas(
                    new GridTemplateAreas(value, style.getGridTemplateAreaRowCount(),
                            style.getGridTemplateAreaColumnCount())));
    public static final Property<Integer> GRID_TEMPLATE_AREA_ROW_COUNT = new ValueProperty<>(
            "grid-template-area-row-count", (style, value, builder) -> applyGridAreas(style, builder));
    public static final Property<Integer> GRID_TEMPLATE_AREA_COLUMN_COUNT = new ValueProperty<>(
            "grid-template-area-column-count", (style, value, builder) -> applyGridAreas(style, builder));

    private static void applyGridAreas(Style style, com.antepod.taffy.style.Style.Builder builder) {
        builder.gridTemplateAreas(new GridTemplateAreas(style.getGridTemplateAreas(),
                style.getGridTemplateAreaRowCount(), style.getGridTemplateAreaColumnCount()));
    }
}
