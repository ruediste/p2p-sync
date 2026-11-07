package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.StyleProperties.ALIGN;
import static com.github.ruediste.jreact.StyleProperties.ALIGN_CONTENT;
import static com.github.ruediste.jreact.StyleProperties.ALIGN_ITEMS;
import static com.github.ruediste.jreact.StyleProperties.ALIGN_SELF;
import static com.github.ruediste.jreact.StyleProperties.ASPECT_RATIO;
import static com.github.ruediste.jreact.StyleProperties.BACKGROUND;
import static com.github.ruediste.jreact.StyleProperties.BOLD;
import static com.github.ruediste.jreact.StyleProperties.BORDER;
import static com.github.ruediste.jreact.StyleProperties.BOX_SHADOW;
import static com.github.ruediste.jreact.StyleProperties.BOX_SIZING;
import static com.github.ruediste.jreact.StyleProperties.CLEAR;
import static com.github.ruediste.jreact.StyleProperties.COLOR;
import static com.github.ruediste.jreact.StyleProperties.DIRECTION;
import static com.github.ruediste.jreact.StyleProperties.DISPLAY;
import static com.github.ruediste.jreact.StyleProperties.FLEX_BASIS;
import static com.github.ruediste.jreact.StyleProperties.FLEX_DIRECTION;
import static com.github.ruediste.jreact.StyleProperties.FLEX_WRAP;
import static com.github.ruediste.jreact.StyleProperties.FLOAT_MODE;
import static com.github.ruediste.jreact.StyleProperties.FONT_FAMILY;
import static com.github.ruediste.jreact.StyleProperties.FONT_SIZE;
import static com.github.ruediste.jreact.StyleProperties.GAP;
import static com.github.ruediste.jreact.StyleProperties.GRID_AUTO_COLUMNS;
import static com.github.ruediste.jreact.StyleProperties.GRID_AUTO_FLOW;
import static com.github.ruediste.jreact.StyleProperties.GRID_AUTO_ROWS;
import static com.github.ruediste.jreact.StyleProperties.GRID_COLUMN;
import static com.github.ruediste.jreact.StyleProperties.GRID_ROW;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_AREAS;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_AREA_COLUMN_COUNT;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_AREA_ROW_COUNT;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_COLUMNS;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_COLUMN_NAMES;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_ROWS;
import static com.github.ruediste.jreact.StyleProperties.GRID_TEMPLATE_ROW_NAMES;
import static com.github.ruediste.jreact.StyleProperties.GROW;
import static com.github.ruediste.jreact.StyleProperties.HEIGHT;
import static com.github.ruediste.jreact.StyleProperties.ITALIC;
import static com.github.ruediste.jreact.StyleProperties.ITEM_IS_REPLACED;
import static com.github.ruediste.jreact.StyleProperties.ITEM_IS_TABLE;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_CONTENT;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_ITEMS;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_SELF;
import static com.github.ruediste.jreact.StyleProperties.LAYOUT_BORDER;
import static com.github.ruediste.jreact.StyleProperties.LAYOUT_INSET;
import static com.github.ruediste.jreact.StyleProperties.LAYOUT_MARGIN;
import static com.github.ruediste.jreact.StyleProperties.LAYOUT_PADDING;
import static com.github.ruediste.jreact.StyleProperties.LAYOUT_TEXT_ALIGN;
import static com.github.ruediste.jreact.StyleProperties.LINE_HEIGHT;
import static com.github.ruediste.jreact.StyleProperties.MARGIN;
import static com.github.ruediste.jreact.StyleProperties.MAX_SIZE;
import static com.github.ruediste.jreact.StyleProperties.MIN_SIZE;
import static com.github.ruediste.jreact.StyleProperties.OPACITY;
import static com.github.ruediste.jreact.StyleProperties.ORDER;
import static com.github.ruediste.jreact.StyleProperties.OVERFLOW;
import static com.github.ruediste.jreact.StyleProperties.PADDING;
import static com.github.ruediste.jreact.StyleProperties.POSITION;
import static com.github.ruediste.jreact.StyleProperties.RADIUS;
import static com.github.ruediste.jreact.StyleProperties.SCROLLBAR_WIDTH;
import static com.github.ruediste.jreact.StyleProperties.SHRINK;
import static com.github.ruediste.jreact.StyleProperties.SIZE;
import static com.github.ruediste.jreact.StyleProperties.TEXT_ALIGN;
import static com.github.ruediste.jreact.StyleProperties.TEXT_SHADOW;
import static com.github.ruediste.jreact.StyleProperties.WIDTH;
import static com.github.ruediste.jreact.StyleProperties.WRAP;
import static com.github.ruediste.jreact.StyleProperties.WRITING_DIRECTION;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.github.ruediste.jreact.StyleProperties.DoubleProperty;
import com.github.ruediste.jreact.StyleProperties.Property;
import com.github.ruediste.jreact.layout.AlignContent;
import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.AlignSelf;
import com.github.ruediste.jreact.layout.BoxSizing;
import com.github.ruediste.jreact.layout.Clear;
import com.github.ruediste.jreact.layout.Dimension;
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
import com.github.ruediste.jreact.layout.TrackSizingFunction;

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
 *
 * <p>
 * The properties used by {@link com.github.ruediste.jreact.layout.LayoutEngine}
 * follow the CSS box model and use the value types from the layout package.
 * They are stored in the same property map as the appearance properties and are
 * read through accessors such as {@link #display()}, {@link #size()} and
 * {@link #layoutMargin()}.
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

    private <T> Style put(Property<T> property, T value) {
        property.set(this, value);
        return this;
    }

    private <T> Style putNullable(Property<T> property, T value) {
        property.setNullable(this, value);
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

    // CSS layout properties used by the layout engine.

    public Display display() {
        return get(DISPLAY, Display.Flex);
    }

    public Style display(Display value) {
        return put(DISPLAY, value);
    }

    public Position position() {
        return get(POSITION, Position.Static);
    }

    public Style position(Position value) {
        return put(POSITION, value);
    }

    public com.github.ruediste.jreact.layout.Direction layoutDirection() {
        return get(WRITING_DIRECTION, com.github.ruediste.jreact.layout.Direction.Ltr);
    }

    public Style layoutDirection(com.github.ruediste.jreact.layout.Direction value) {
        return put(WRITING_DIRECTION, value);
    }

    public Float floatMode() {
        return get(FLOAT_MODE, Float.None);
    }

    public Style floatMode(Float value) {
        return put(FLOAT_MODE, value);
    }

    public Clear clear() {
        return get(CLEAR, Clear.None);
    }

    public Style clear(Clear value) {
        return put(CLEAR, value);
    }

    public BoxSizing boxSizing() {
        return get(BOX_SIZING, BoxSizing.BorderBox);
    }

    public Style boxSizing(BoxSizing value) {
        return put(BOX_SIZING, value);
    }

    public com.github.ruediste.jreact.layout.TextAlign layoutTextAlign() {
        return get(LAYOUT_TEXT_ALIGN, com.github.ruediste.jreact.layout.TextAlign.Auto);
    }

    public Style layoutTextAlign(com.github.ruediste.jreact.layout.TextAlign value) {
        return put(LAYOUT_TEXT_ALIGN, value);
    }

    public AlignItems layoutAlignItems() {
        return get(ALIGN_ITEMS, null);
    }

    public Style layoutAlignItems(AlignItems value) {
        return putNullable(ALIGN_ITEMS, value);
    }

    public AlignSelf alignSelf() {
        return get(ALIGN_SELF, AlignSelf.Auto);
    }

    public Style alignSelf(AlignSelf value) {
        return putNullable(ALIGN_SELF, value);
    }

    public AlignSelf getAlignSelf() {
        return alignSelf();
    }

    public void setAlignSelf(AlignSelf value) {
        alignSelf(value);
    }

    public AlignContent alignContent() {
        return get(ALIGN_CONTENT, null);
    }

    public Style alignContent(AlignContent value) {
        return putNullable(ALIGN_CONTENT, value);
    }

    public JustifyContent layoutJustifyContent() {
        return get(JUSTIFY_CONTENT, null);
    }

    public Style layoutJustifyContent(JustifyContent value) {
        return putNullable(JUSTIFY_CONTENT, value);
    }

    public AlignItems justifyItems() {
        return get(JUSTIFY_ITEMS, null);
    }

    public Style justifyItems(AlignItems value) {
        return putNullable(JUSTIFY_ITEMS, value);
    }

    public AlignSelf justifySelf() {
        return get(JUSTIFY_SELF, AlignSelf.Auto);
    }

    public Style justifySelf(AlignSelf value) {
        return putNullable(JUSTIFY_SELF, value);
    }

    public AlignSelf getJustifySelf() {
        return justifySelf();
    }

    public void setJustifySelf(AlignSelf value) {
        justifySelf(value);
    }

    public FlexDirection layoutFlexDirection() {
        return get(FLEX_DIRECTION, FlexDirection.Row);
    }

    public Style layoutFlexDirection(FlexDirection value) {
        return put(FLEX_DIRECTION, value);
    }

    public FlexWrap layoutFlexWrap() {
        return get(FLEX_WRAP, FlexWrap.NoWrap);
    }

    public Style layoutFlexWrap(FlexWrap value) {
        return put(FLEX_WRAP, value);
    }

    public float flexGrow() {
        return (float) number(GROW, 0);
    }

    public float flexShrink() {
        return (float) number(SHRINK, 1);
    }

    public LengthPercentageAuto flexBasis() {
        return get(FLEX_BASIS, LengthPercentageAuto.auto());
    }

    public Style flexBasis(LengthPercentageAuto value) {
        return put(FLEX_BASIS, value);
    }

    public Size<Dimension> size() {
        return get(SIZE, Size.of(Dimension.auto(), Dimension.auto()));
    }

    public Style size(Size<Dimension> value) {
        return put(SIZE, value.copy());
    }

    public Size<Dimension> minSize() {
        return get(MIN_SIZE, Size.of(Dimension.auto(), Dimension.auto()));
    }

    public Style minSize(Size<Dimension> value) {
        return put(MIN_SIZE, value.copy());
    }

    public Size<Dimension> maxSize() {
        return get(MAX_SIZE, Size.of(Dimension.auto(), Dimension.auto()));
    }

    public Style maxSize(Size<Dimension> value) {
        return put(MAX_SIZE, value.copy());
    }

    public java.lang.Float aspectRatio() {
        return get(ASPECT_RATIO, null);
    }

    public Style aspectRatio(java.lang.Float value) {
        return putNullable(ASPECT_RATIO, value);
    }

    /** width is the column gap, height is the row gap. */
    public Size<LengthPercentage> gap() {
        return get(GAP, Size.of(LengthPercentage.zero(), LengthPercentage.zero()));
    }

    public Style gap(Size<LengthPercentage> value) {
        return put(GAP, value.copy());
    }

    public Rect<LengthPercentageAuto> layoutMargin() {
        return get(LAYOUT_MARGIN, Rect.all(LengthPercentageAuto.length(0)));
    }

    public Style layoutMargin(Rect<LengthPercentageAuto> value) {
        return put(LAYOUT_MARGIN, value.copy());
    }

    public Rect<LengthPercentage> layoutPadding() {
        return get(LAYOUT_PADDING, Rect.all(LengthPercentage.zero()));
    }

    public Style layoutPadding(Rect<LengthPercentage> value) {
        return put(LAYOUT_PADDING, value.copy());
    }

    public Rect<LengthPercentage> layoutBorder() {
        return get(LAYOUT_BORDER, Rect.all(LengthPercentage.zero()));
    }

    public Style layoutBorder(Rect<LengthPercentage> value) {
        return put(LAYOUT_BORDER, value.copy());
    }

    public Rect<LengthPercentageAuto> layoutInset() {
        return get(LAYOUT_INSET, Rect.all(LengthPercentageAuto.auto()));
    }

    public Style layoutInset(Rect<LengthPercentageAuto> value) {
        return put(LAYOUT_INSET, value.copy());
    }

    public boolean itemIsTable() {
        return get(ITEM_IS_TABLE, false);
    }

    public Style itemIsTable(boolean value) {
        return put(ITEM_IS_TABLE, value);
    }

    public boolean itemIsReplaced() {
        return get(ITEM_IS_REPLACED, false);
    }

    public Style itemIsReplaced(boolean value) {
        return put(ITEM_IS_REPLACED, value);
    }

    public float scrollbarWidth() {
        return get(SCROLLBAR_WIDTH, 0f);
    }

    public Style scrollbarWidth(float value) {
        return put(SCROLLBAR_WIDTH, value);
    }

    public Point<Overflow> overflow() {
        return get(OVERFLOW, Point.of(Overflow.Visible, Overflow.Visible));
    }

    public Style overflow(Point<Overflow> value) {
        return put(OVERFLOW, value.copy());
    }

    public GridAutoFlow gridAutoFlow() {
        return get(GRID_AUTO_FLOW, GridAutoFlow.Row);
    }

    public Style gridAutoFlow(GridAutoFlow value) {
        return put(GRID_AUTO_FLOW, value);
    }

    public Line<GridPlacement> gridRow() {
        return get(GRID_ROW, Line.of(GridPlacement.auto(), GridPlacement.auto()));
    }

    public Style gridRow(Line<GridPlacement> value) {
        return put(GRID_ROW, Line.of(value.start, value.end));
    }

    public Line<GridPlacement> gridColumn() {
        return get(GRID_COLUMN, Line.of(GridPlacement.auto(), GridPlacement.auto()));
    }

    public Style gridColumn(Line<GridPlacement> value) {
        return put(GRID_COLUMN, Line.of(value.start, value.end));
    }

    public List<GridTemplateComponent> gridTemplateRows() {
        return get(GRID_TEMPLATE_ROWS, List.of());
    }

    public Style gridTemplateRows(List<GridTemplateComponent> value) {
        return put(GRID_TEMPLATE_ROWS, new ArrayList<>(value));
    }

    public List<GridTemplateComponent> gridTemplateColumns() {
        return get(GRID_TEMPLATE_COLUMNS, List.of());
    }

    public Style gridTemplateColumns(List<GridTemplateComponent> value) {
        return put(GRID_TEMPLATE_COLUMNS, new ArrayList<>(value));
    }

    public List<TrackSizingFunction> gridAutoRows() {
        return get(GRID_AUTO_ROWS, List.of());
    }

    public Style gridAutoRows(List<TrackSizingFunction> value) {
        return put(GRID_AUTO_ROWS, new ArrayList<>(value));
    }

    public List<TrackSizingFunction> gridAutoColumns() {
        return get(GRID_AUTO_COLUMNS, List.of());
    }

    public Style gridAutoColumns(List<TrackSizingFunction> value) {
        return put(GRID_AUTO_COLUMNS, new ArrayList<>(value));
    }

    public List<List<String>> gridTemplateRowNames() {
        return get(GRID_TEMPLATE_ROW_NAMES, List.of());
    }

    public Style gridTemplateRowNames(List<List<String>> value) {
        return put(GRID_TEMPLATE_ROW_NAMES, copyNames(value));
    }

    public List<List<String>> gridTemplateColumnNames() {
        return get(GRID_TEMPLATE_COLUMN_NAMES, List.of());
    }

    public Style gridTemplateColumnNames(List<List<String>> value) {
        return put(GRID_TEMPLATE_COLUMN_NAMES, copyNames(value));
    }

    public Dimension getWidth() {
        return size().width;
    }

    public void setWidth(Object value) {
        Size<Dimension> size = size().copy();
        size.width = Dimension.of(value);
        size(size);
    }

    public Dimension getHeight() {
        return size().height;
    }

    public void setHeight(Object value) {
        Size<Dimension> size = size().copy();
        size.height = Dimension.of(value);
        size(size);
    }

    public Dimension getMinWidth() {
        return minSize().width;
    }

    public void setMinWidth(Object value) {
        Size<Dimension> size = minSize().copy();
        size.width = Dimension.of(value);
        minSize(size);
    }

    public Dimension getMinHeight() {
        return minSize().height;
    }

    public void setMinHeight(Object value) {
        Size<Dimension> size = minSize().copy();
        size.height = Dimension.of(value);
        minSize(size);
    }

    public Dimension getMaxWidth() {
        return maxSize().width;
    }

    public void setMaxWidth(Object value) {
        Size<Dimension> size = maxSize().copy();
        size.width = Dimension.of(value);
        maxSize(size);
    }

    public Dimension getMaxHeight() {
        return maxSize().height;
    }

    public void setMaxHeight(Object value) {
        Size<Dimension> size = maxSize().copy();
        size.height = Dimension.of(value);
        maxSize(size);
    }

    public LengthPercentageAuto getMarginLeft() {
        return layoutMargin().left;
    }

    public void setMarginLeft(Object value) {
        Rect<LengthPercentageAuto> margin = layoutMargin().copy();
        margin.left = LengthPercentageAuto.of(value);
        layoutMargin(margin);
    }

    public LengthPercentageAuto getMarginRight() {
        return layoutMargin().right;
    }

    public void setMarginRight(Object value) {
        Rect<LengthPercentageAuto> margin = layoutMargin().copy();
        margin.right = LengthPercentageAuto.of(value);
        layoutMargin(margin);
    }

    public LengthPercentageAuto getMarginTop() {
        return layoutMargin().top;
    }

    public void setMarginTop(Object value) {
        Rect<LengthPercentageAuto> margin = layoutMargin().copy();
        margin.top = LengthPercentageAuto.of(value);
        layoutMargin(margin);
    }

    public LengthPercentageAuto getMarginBottom() {
        return layoutMargin().bottom;
    }

    public void setMarginBottom(Object value) {
        Rect<LengthPercentageAuto> margin = layoutMargin().copy();
        margin.bottom = LengthPercentageAuto.of(value);
        layoutMargin(margin);
    }

    public LengthPercentage getPaddingLeft() {
        return layoutPadding().left;
    }

    public void setPaddingLeft(Object value) {
        Rect<LengthPercentage> padding = layoutPadding().copy();
        padding.left = LengthPercentage.of(value);
        layoutPadding(padding);
    }

    public LengthPercentage getPaddingRight() {
        return layoutPadding().right;
    }

    public void setPaddingRight(Object value) {
        Rect<LengthPercentage> padding = layoutPadding().copy();
        padding.right = LengthPercentage.of(value);
        layoutPadding(padding);
    }

    public LengthPercentage getPaddingTop() {
        return layoutPadding().top;
    }

    public void setPaddingTop(Object value) {
        Rect<LengthPercentage> padding = layoutPadding().copy();
        padding.top = LengthPercentage.of(value);
        layoutPadding(padding);
    }

    public LengthPercentage getPaddingBottom() {
        return layoutPadding().bottom;
    }

    public void setPaddingBottom(Object value) {
        Rect<LengthPercentage> padding = layoutPadding().copy();
        padding.bottom = LengthPercentage.of(value);
        layoutPadding(padding);
    }

    public LengthPercentage getBorderLeft() {
        return layoutBorder().left;
    }

    public void setBorderLeft(Object value) {
        Rect<LengthPercentage> border = layoutBorder().copy();
        border.left = LengthPercentage.of(value);
        layoutBorder(border);
    }

    public LengthPercentage getBorderRight() {
        return layoutBorder().right;
    }

    public void setBorderRight(Object value) {
        Rect<LengthPercentage> border = layoutBorder().copy();
        border.right = LengthPercentage.of(value);
        layoutBorder(border);
    }

    public LengthPercentage getBorderTop() {
        return layoutBorder().top;
    }

    public void setBorderTop(Object value) {
        Rect<LengthPercentage> border = layoutBorder().copy();
        border.top = LengthPercentage.of(value);
        layoutBorder(border);
    }

    public LengthPercentage getBorderBottom() {
        return layoutBorder().bottom;
    }

    public void setBorderBottom(Object value) {
        Rect<LengthPercentage> border = layoutBorder().copy();
        border.bottom = LengthPercentage.of(value);
        layoutBorder(border);
    }

    public LengthPercentageAuto getLeft() {
        return layoutInset().left;
    }

    public void setLeft(Object value) {
        Rect<LengthPercentageAuto> inset = layoutInset().copy();
        inset.left = LengthPercentageAuto.of(value);
        layoutInset(inset);
    }

    public LengthPercentageAuto getRight() {
        return layoutInset().right;
    }

    public void setRight(Object value) {
        Rect<LengthPercentageAuto> inset = layoutInset().copy();
        inset.right = LengthPercentageAuto.of(value);
        layoutInset(inset);
    }

    public LengthPercentageAuto getTop() {
        return layoutInset().top;
    }

    public void setTop(Object value) {
        Rect<LengthPercentageAuto> inset = layoutInset().copy();
        inset.top = LengthPercentageAuto.of(value);
        layoutInset(inset);
    }

    public LengthPercentageAuto getBottom() {
        return layoutInset().bottom;
    }

    public void setBottom(Object value) {
        Rect<LengthPercentageAuto> inset = layoutInset().copy();
        inset.bottom = LengthPercentageAuto.of(value);
        layoutInset(inset);
    }

    public LengthPercentage getColumnGap() {
        return gap().width;
    }

    public void setColumnGap(Object value) {
        Size<LengthPercentage> gap = gap().copy();
        gap.width = LengthPercentage.of(value);
        gap(gap);
    }

    public LengthPercentage getRowGap() {
        return gap().height;
    }

    public void setRowGap(Object value) {
        Size<LengthPercentage> gap = gap().copy();
        gap.height = LengthPercentage.of(value);
        gap(gap);
    }

    public LengthPercentageAuto getFlexBasis() {
        return flexBasis();
    }

    public void setFlexBasis(Object value) {
        flexBasis(LengthPercentageAuto.of(value));
    }

    public java.lang.Float getAspectRatio() {
        return aspectRatio();
    }

    public void setAspectRatio(java.lang.Float value) {
        aspectRatio(value);
    }

    public Overflow getOverflowX() {
        return overflow().x;
    }

    public void setOverflowX(Object value) {
        Point<Overflow> overflow = overflow().copy();
        overflow.x = Overflow.of(value);
        overflow(overflow);
    }

    public Overflow getOverflowY() {
        return overflow().y;
    }

    public void setOverflowY(Object value) {
        Point<Overflow> overflow = overflow().copy();
        overflow.y = Overflow.of(value);
        overflow(overflow);
    }

    public GridPlacement getGridRowStart() {
        return gridRow().start;
    }

    public void setGridRowStart(Object value) {
        Line<GridPlacement> row = gridRow();
        Line<GridPlacement> updated = Line.of(GridPlacement.of(value), row.end);
        gridRow(updated);
    }

    public GridPlacement getGridRowEnd() {
        return gridRow().end;
    }

    public void setGridRowEnd(Object value) {
        Line<GridPlacement> row = gridRow();
        Line<GridPlacement> updated = Line.of(row.start, GridPlacement.of(value));
        gridRow(updated);
    }

    public GridPlacement getGridColumnStart() {
        return gridColumn().start;
    }

    public void setGridColumnStart(Object value) {
        Line<GridPlacement> column = gridColumn();
        Line<GridPlacement> updated = Line.of(GridPlacement.of(value), column.end);
        gridColumn(updated);
    }

    public GridPlacement getGridColumnEnd() {
        return gridColumn().end;
    }

    public void setGridColumnEnd(Object value) {
        Line<GridPlacement> column = gridColumn();
        Line<GridPlacement> updated = Line.of(column.start, GridPlacement.of(value));
        gridColumn(updated);
    }

    public void setGridRow(Line<GridPlacement> value) {
        gridRow(value);
    }

    public void setGridColumn(Line<GridPlacement> value) {
        gridColumn(value);
    }

    public void setGridTemplateRows(List<GridTemplateComponent> value) {
        gridTemplateRows(value);
    }

    public void setGridTemplateColumns(List<GridTemplateComponent> value) {
        gridTemplateColumns(value);
    }

    public void setGridAutoRows(List<TrackSizingFunction> value) {
        gridAutoRows(value);
    }

    public void setGridAutoColumns(List<TrackSizingFunction> value) {
        gridAutoColumns(value);
    }

    public void setGridTemplateRowNames(List<List<String>> value) {
        gridTemplateRowNames(value);
    }

    public void setGridTemplateColumnNames(List<List<String>> value) {
        gridTemplateColumnNames(value);
    }

    public List<GridTemplateArea> getGridTemplateAreas() {
        return get(GRID_TEMPLATE_AREAS, List.of());
    }

    public void setGridTemplateAreas(List<GridTemplateArea> areas) {
        gridTemplateAreas(areas);
    }

    public Style gridTemplateAreas(List<GridTemplateArea> value) {
        return put(GRID_TEMPLATE_AREAS,
                value == null ? new ArrayList<>() : new ArrayList<>(value));
    }

    public int getGridTemplateAreaRowCount() {
        int inferred = inferredAreaRows();
        Integer explicit = get(GRID_TEMPLATE_AREA_ROW_COUNT, null);
        return explicit == null ? inferred : Math.max(explicit, inferred);
    }

    public int getGridTemplateAreaColumnCount() {
        int inferred = inferredAreaColumns();
        Integer explicit = get(GRID_TEMPLATE_AREA_COLUMN_COUNT, null);
        return explicit == null ? inferred : Math.max(explicit, inferred);
    }

    public void setGridTemplateAreaRowCount(int count) {
        put(GRID_TEMPLATE_AREA_ROW_COUNT, count);
    }

    public void setGridTemplateAreaColumnCount(int count) {
        put(GRID_TEMPLATE_AREA_COLUMN_COUNT, count);
    }

    private int inferredAreaRows() {
        int rows = 0;
        for (GridTemplateArea area : getGridTemplateAreas()) {
            rows = Math.max(rows, area.rowEnd - 1);
        }
        return rows;
    }

    private int inferredAreaColumns() {
        int columns = 0;
        for (GridTemplateArea area : getGridTemplateAreas()) {
            columns = Math.max(columns, area.columnEnd - 1);
        }
        return columns;
    }

    private static List<List<String>> copyNames(List<List<String>> names) {
        List<List<String>> copy = new ArrayList<>(names.size());
        for (List<String> line : names) {
            copy.add(new ArrayList<>(line));
        }
        return copy;
    }

    public Style copy() {
        Style copy = new Style();
        for (var entry : values.entrySet()) {
            copy.values.put(entry.getKey(), copyValue(entry.getValue()));
        }
        copy.id = id;
        copy.className = className;
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Size<?> size) {
            return size.copy();
        }
        if (value instanceof Rect<?> rect) {
            return rect.copy();
        }
        if (value instanceof Point<?> point) {
            return point.copy();
        }
        if (value instanceof Line<?> line) {
            return Line.of(line.start, line.end);
        }
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        return value;
    }

    private static List<?> copyList(List<?> list) {
        List<Object> copy = new ArrayList<>(list.size());
        for (Object item : list) {
            copy.add(item instanceof List<?> nested ? copyList(nested) : item);
        }
        return copy;
    }
}
