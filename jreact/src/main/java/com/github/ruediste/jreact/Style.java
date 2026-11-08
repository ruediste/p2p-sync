package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.StyleProperties.ALIGN_CONTENT;
import static com.github.ruediste.jreact.StyleProperties.ALIGN_ITEMS;
import static com.github.ruediste.jreact.StyleProperties.ALIGN_SELF;
import static com.github.ruediste.jreact.StyleProperties.ASPECT_RATIO;
import static com.github.ruediste.jreact.StyleProperties.BACKGROUND;
import static com.github.ruediste.jreact.StyleProperties.BOLD;
import static com.github.ruediste.jreact.StyleProperties.BORDER;
import static com.github.ruediste.jreact.StyleProperties.BORDER_DECORATION;
import static com.github.ruediste.jreact.StyleProperties.BOX_SHADOW;
import static com.github.ruediste.jreact.StyleProperties.BOX_SIZING;
import static com.github.ruediste.jreact.StyleProperties.CLEAR;
import static com.github.ruediste.jreact.StyleProperties.COLOR;
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
import static com.github.ruediste.jreact.StyleProperties.ITALIC;
import static com.github.ruediste.jreact.StyleProperties.ITEM_IS_REPLACED;
import static com.github.ruediste.jreact.StyleProperties.ITEM_IS_TABLE;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_CONTENT;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_ITEMS;
import static com.github.ruediste.jreact.StyleProperties.JUSTIFY_SELF;
import static com.github.ruediste.jreact.StyleProperties.INSET;
import static com.github.ruediste.jreact.StyleProperties.MARGIN;
import static com.github.ruediste.jreact.StyleProperties.PADDING;
import static com.github.ruediste.jreact.StyleProperties.TEXT_ALIGN;
import static com.github.ruediste.jreact.StyleProperties.LINE_HEIGHT;
import static com.github.ruediste.jreact.StyleProperties.MAX_SIZE;
import static com.github.ruediste.jreact.StyleProperties.MIN_SIZE;
import static com.github.ruediste.jreact.StyleProperties.OPACITY;
import static com.github.ruediste.jreact.StyleProperties.OVERFLOW;
import static com.github.ruediste.jreact.StyleProperties.POSITION;
import static com.github.ruediste.jreact.StyleProperties.RADIUS;
import static com.github.ruediste.jreact.StyleProperties.SCROLLBAR_WIDTH;
import static com.github.ruediste.jreact.StyleProperties.SHRINK;
import static com.github.ruediste.jreact.StyleProperties.SIZE;
import static com.github.ruediste.jreact.StyleProperties.TEXT_SHADOW;
import static com.github.ruediste.jreact.StyleProperties.WRITING_DIRECTION;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.antepod.taffy.geometry.Line;
import com.antepod.taffy.geometry.Point;
import com.antepod.taffy.geometry.Rect;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AlignContent;
import com.antepod.taffy.style.AlignItems;
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
 * properties; text measurement and rendering interpret fonts, colors, borders,
 * and shadows. This class does not parse CSS text or perform layout or painting
 * itself.
 *
 * <p>
 * The properties used by {@link com.github.ruediste.jreact.layout.LayoutEngine}
 * follow the CSS box model and use the value types from Taffy.
 */
public final class Style {
    final HashMap<Property<?>, Object> values = new HashMap<>();

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

    public Style flexGrow(double value) {
        return dimension(GROW, value);
    }

    public Style flexShrink(double value) {
        return dimension(SHRINK, value);
    }

    public Style width(double value) {
        nonNegative(value);
        return width(Dimension.length((float) value));
    }

    public Style width(Dimension value) {
        Size<Dimension> current = size();
        return size(new Size<>(Objects.requireNonNull(value), current.height));
    }

    public Style height(double value) {
        nonNegative(value);
        return height(Dimension.length((float) value));
    }

    public Style height(Dimension value) {
        Size<Dimension> current = size();
        return size(new Size<>(current.width, Objects.requireNonNull(value)));
    }

    public Style widthAuto() {
        return width(Dimension.AUTO);
    }

    public Style heightAuto() {
        return height(Dimension.AUTO);
    }

    public Style margin(double value) {
        return margin(value, value, value, value);
    }

    public Style margin(double top, double right, double bottom, double left) {
        nonNegative(top);
        nonNegative(right);
        nonNegative(bottom);
        nonNegative(left);
        return margin(new Rect<>(
                LengthPercentageAuto.length((float) left),
                LengthPercentageAuto.length((float) right),
                LengthPercentageAuto.length((float) top),
                LengthPercentageAuto.length((float) bottom)));
    }

    public Style padding(double value) {
        return padding(value, value, value, value);
    }

    public Style padding(double top, double right, double bottom, double left) {
        nonNegative(top);
        nonNegative(right);
        nonNegative(bottom);
        nonNegative(left);
        return padding(new Rect<>(
                LengthPercentage.length((float) left),
                LengthPercentage.length((float) right),
                LengthPercentage.length((float) top),
                LengthPercentage.length((float) bottom)));
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
        put(BORDER_DECORATION, value);
        return border(new Rect<>(
                LengthPercentage.length((float) value.left.width()),
                LengthPercentage.length((float) value.right.width()),
                LengthPercentage.length((float) value.top.width()),
                LengthPercentage.length((float) value.bottom.width())));
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

    public double lineHeight() {
        return number(LINE_HEIGHT, Double.NaN);
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

    public <T> T get(Property<T> property, T fallback) {
        return property.get(this, fallback);
    }

    public double number(DoubleProperty property, double fallback) {
        return property.get(this, fallback);
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
        return get(DISPLAY, Display.FLEX);
    }

    public Style display(Display value) {
        return put(DISPLAY, value);
    }

    public Position position() {
        return get(POSITION, Position.RELATIVE);
    }

    public Style position(Position value) {
        return put(POSITION, value);
    }

    public Direction layoutDirection() {
        return get(WRITING_DIRECTION, Direction.LTR);
    }

    public Style layoutDirection(Direction value) {
        return put(WRITING_DIRECTION, value);
    }

    public Float floatMode() {
        return get(FLOAT_MODE, Float.NONE);
    }

    public Style floatMode(Float value) {
        return put(FLOAT_MODE, value);
    }

    public Clear clear() {
        return get(CLEAR, Clear.NONE);
    }

    public Style clear(Clear value) {
        return put(CLEAR, value);
    }

    public BoxSizing boxSizing() {
        return get(BOX_SIZING, BoxSizing.BORDER_BOX);
    }

    public Style boxSizing(BoxSizing value) {
        return put(BOX_SIZING, value);
    }

    public TextAlign textAlign() {
        return get(TEXT_ALIGN, TextAlign.AUTO);
    }

    public AlignItems alignItems() {
        return get(ALIGN_ITEMS, null);
    }

    public Style alignItems(AlignItems value) {
        return putNullable(ALIGN_ITEMS, value);
    }

    public AlignItems alignSelf() {
        return get(ALIGN_SELF, null);
    }

    public Style alignSelf(AlignItems value) {
        return putNullable(ALIGN_SELF, value);
    }

    public AlignItems getAlignSelf() {
        return alignSelf();
    }

    public void setAlignSelf(AlignItems value) {
        alignSelf(value);
    }

    public AlignContent alignContent() {
        return get(ALIGN_CONTENT, null);
    }

    public Style alignContent(AlignContent value) {
        return putNullable(ALIGN_CONTENT, value);
    }

    public AlignContent justifyContent() {
        return get(JUSTIFY_CONTENT, null);
    }

    public Style justifyContent(AlignContent value) {
        return putNullable(JUSTIFY_CONTENT, value);
    }

    public AlignItems justifyItems() {
        return get(JUSTIFY_ITEMS, null);
    }

    public Style justifyItems(AlignItems value) {
        return putNullable(JUSTIFY_ITEMS, value);
    }

    public AlignItems justifySelf() {
        return get(JUSTIFY_SELF, null);
    }

    public Style justifySelf(AlignItems value) {
        return putNullable(JUSTIFY_SELF, value);
    }

    public AlignItems getJustifySelf() {
        return justifySelf();
    }

    public void setJustifySelf(AlignItems value) {
        justifySelf(value);
    }

    public FlexDirection flexDirection() {
        return get(FLEX_DIRECTION, FlexDirection.ROW);
    }

    public Style flexDirection(FlexDirection value) {
        return put(FLEX_DIRECTION, value);
    }

    public FlexWrap flexWrap() {
        return get(FLEX_WRAP, FlexWrap.NO_WRAP);
    }

    public Style flexWrap(FlexWrap value) {
        return put(FLEX_WRAP, value);
    }

    public float flexGrow() {
        return (float) number(GROW, 0);
    }

    public float flexShrink() {
        return (float) number(SHRINK, 1);
    }

    public Dimension flexBasis() {
        return get(FLEX_BASIS, Dimension.AUTO);
    }

    public Style flexBasis(Dimension value) {
        return put(FLEX_BASIS, value);
    }

    public Size<Dimension> size() {
        return get(SIZE, new Size<>(Dimension.AUTO, Dimension.AUTO));
    }

    public Style size(Size<Dimension> value) {
        return put(SIZE, new Size<>(value.width, value.height));
    }

    public Size<Dimension> minSize() {
        return get(MIN_SIZE, new Size<>(Dimension.AUTO, Dimension.AUTO));
    }

    public Style minSize(Size<Dimension> value) {
        return put(MIN_SIZE, new Size<>(value.width, value.height));
    }

    public Size<Dimension> maxSize() {
        return get(MAX_SIZE, new Size<>(Dimension.AUTO, Dimension.AUTO));
    }

    public Style maxSize(Size<Dimension> value) {
        return put(MAX_SIZE, new Size<>(value.width, value.height));
    }

    public java.lang.Float aspectRatio() {
        return get(ASPECT_RATIO, null);
    }

    public Style aspectRatio(java.lang.Float value) {
        return putNullable(ASPECT_RATIO, value);
    }

    /** width is the column gap, height is the row gap. */
    public Size<LengthPercentage> gap() {
        return get(GAP, new Size<>(LengthPercentage.ZERO, LengthPercentage.ZERO));
    }

    public Style gap(Size<LengthPercentage> value) {
        return put(GAP, value);
    }

    public Rect<LengthPercentageAuto> margin() {
        return get(MARGIN, new Rect<>(LengthPercentageAuto.length(0), LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(0), LengthPercentageAuto.length(0)));
    }

    public Style margin(Rect<LengthPercentageAuto> value) {
        return put(MARGIN, value);
    }

    public Rect<LengthPercentage> padding() {
        return get(PADDING, new Rect<>(LengthPercentage.ZERO, LengthPercentage.ZERO,
                LengthPercentage.ZERO, LengthPercentage.ZERO));
    }

    public Style padding(Rect<LengthPercentage> value) {
        return put(PADDING, value);
    }

    public Rect<LengthPercentage> border() {
        return get(BORDER, new Rect<>(LengthPercentage.ZERO, LengthPercentage.ZERO,
                LengthPercentage.ZERO, LengthPercentage.ZERO));
    }

    public Style border(Rect<LengthPercentage> value) {
        return put(BORDER, new Rect<>(value.left, value.right, value.top, value.bottom));
    }

    public Rect<LengthPercentageAuto> inset() {
        return get(INSET, new Rect<>(LengthPercentageAuto.AUTO, LengthPercentageAuto.AUTO,
                LengthPercentageAuto.AUTO, LengthPercentageAuto.AUTO));
    }

    public Style inset(Rect<LengthPercentageAuto> value) {
        return put(INSET, new Rect<>(value.left, value.right, value.top, value.bottom));
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
        return get(OVERFLOW, new Point<>(Overflow.VISIBLE, Overflow.VISIBLE));
    }

    public Style overflow(Point<Overflow> value) {
        return put(OVERFLOW, new Point<>(value.x, value.y));
    }

    public GridAutoFlow gridAutoFlow() {
        return get(GRID_AUTO_FLOW, GridAutoFlow.ROW);
    }

    public Style gridAutoFlow(GridAutoFlow value) {
        return put(GRID_AUTO_FLOW, value);
    }

    public Line<GridPlacement> gridRow() {
        return get(GRID_ROW, new Line<>(GridPlacement.AUTO, GridPlacement.AUTO));
    }

    public Style gridRow(Line<GridPlacement> value) {
        return put(GRID_ROW, new Line<>(value.start, value.end));
    }

    public Line<GridPlacement> gridColumn() {
        return get(GRID_COLUMN, new Line<>(GridPlacement.AUTO, GridPlacement.AUTO));
    }

    public Style gridColumn(Line<GridPlacement> value) {
        return put(GRID_COLUMN, new Line<>(value.start, value.end));
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

    public void setWidth(Dimension value) {
        Size<Dimension> current = size();
        size(new Size<>(Objects.requireNonNull(value), current.height));
    }

    public Dimension getHeight() {
        return size().height;
    }

    public void setHeight(Dimension value) {
        Size<Dimension> current = size();
        size(new Size<>(current.width, Objects.requireNonNull(value)));
    }

    public Dimension getMinWidth() {
        return minSize().width;
    }

    public void setMinWidth(Dimension value) {
        Size<Dimension> current = minSize();
        minSize(new Size<>(Objects.requireNonNull(value), current.height));
    }

    public Dimension getMinHeight() {
        return minSize().height;
    }

    public void setMinHeight(Dimension value) {
        Size<Dimension> current = minSize();
        minSize(new Size<>(current.width, Objects.requireNonNull(value)));
    }

    public Dimension getMaxWidth() {
        return maxSize().width;
    }

    public void setMaxWidth(Dimension value) {
        Size<Dimension> current = maxSize();
        maxSize(new Size<>(Objects.requireNonNull(value), current.height));
    }

    public Dimension getMaxHeight() {
        return maxSize().height;
    }

    public void setMaxHeight(Dimension value) {
        Size<Dimension> current = maxSize();
        maxSize(new Size<>(current.width, Objects.requireNonNull(value)));
    }

    public LengthPercentageAuto getMarginLeft() {
        return margin().left;
    }

    public void setMarginLeft(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = margin();
        margin(new Rect<>(Objects.requireNonNull(value), current.right, current.top, current.bottom));
    }

    public LengthPercentageAuto getMarginRight() {
        return margin().right;
    }

    public void setMarginRight(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = margin();
        margin(new Rect<>(current.left, Objects.requireNonNull(value), current.top, current.bottom));
    }

    public LengthPercentageAuto getMarginTop() {
        return margin().top;
    }

    public void setMarginTop(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = margin();
        margin(new Rect<>(current.left, current.right, Objects.requireNonNull(value), current.bottom));
    }

    public LengthPercentageAuto getMarginBottom() {
        return margin().bottom;
    }

    public void setMarginBottom(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = margin();
        margin(new Rect<>(current.left, current.right, current.top, Objects.requireNonNull(value)));
    }

    public LengthPercentage getPaddingLeft() {
        return padding().left;
    }

    public void setPaddingLeft(LengthPercentage value) {
        Rect<LengthPercentage> current = padding();
        padding(new Rect<>(Objects.requireNonNull(value), current.right, current.top, current.bottom));
    }

    public LengthPercentage getPaddingRight() {
        return padding().right;
    }

    public void setPaddingRight(LengthPercentage value) {
        Rect<LengthPercentage> current = padding();
        padding(new Rect<>(current.left, Objects.requireNonNull(value), current.top, current.bottom));
    }

    public LengthPercentage getPaddingTop() {
        return padding().top;
    }

    public void setPaddingTop(LengthPercentage value) {
        Rect<LengthPercentage> current = padding();
        padding(new Rect<>(current.left, current.right, Objects.requireNonNull(value), current.bottom));
    }

    public LengthPercentage getPaddingBottom() {
        return padding().bottom;
    }

    public void setPaddingBottom(LengthPercentage value) {
        Rect<LengthPercentage> current = padding();
        padding(new Rect<>(current.left, current.right, current.top, Objects.requireNonNull(value)));
    }

    public LengthPercentage getBorderLeft() {
        return border().left;
    }

    public void setBorderLeft(LengthPercentage value) {
        Rect<LengthPercentage> current = border();
        border(new Rect<>(Objects.requireNonNull(value), current.right, current.top, current.bottom));
    }

    public LengthPercentage getBorderRight() {
        return border().right;
    }

    public void setBorderRight(LengthPercentage value) {
        Rect<LengthPercentage> current = border();
        border(new Rect<>(current.left, Objects.requireNonNull(value), current.top, current.bottom));
    }

    public LengthPercentage getBorderTop() {
        return border().top;
    }

    public void setBorderTop(LengthPercentage value) {
        Rect<LengthPercentage> current = border();
        border(new Rect<>(current.left, current.right, Objects.requireNonNull(value), current.bottom));
    }

    public LengthPercentage getBorderBottom() {
        return border().bottom;
    }

    public void setBorderBottom(LengthPercentage value) {
        Rect<LengthPercentage> current = border();
        border(new Rect<>(current.left, current.right, current.top, Objects.requireNonNull(value)));
    }

    public LengthPercentageAuto getLeft() {
        return inset().left;
    }

    public void setLeft(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = inset();
        inset(new Rect<>(Objects.requireNonNull(value), current.right, current.top, current.bottom));
    }

    public LengthPercentageAuto getRight() {
        return inset().right;
    }

    public void setRight(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = inset();
        inset(new Rect<>(current.left, Objects.requireNonNull(value), current.top, current.bottom));
    }

    public LengthPercentageAuto getTop() {
        return inset().top;
    }

    public void setTop(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = inset();
        inset(new Rect<>(current.left, current.right, Objects.requireNonNull(value), current.bottom));
    }

    public LengthPercentageAuto getBottom() {
        return inset().bottom;
    }

    public void setBottom(LengthPercentageAuto value) {
        Rect<LengthPercentageAuto> current = inset();
        inset(new Rect<>(current.left, current.right, current.top, Objects.requireNonNull(value)));
    }

    public LengthPercentage getColumnGap() {
        return gap().width;
    }

    public void setColumnGap(LengthPercentage value) {
        Size<LengthPercentage> current = gap();
        gap(new Size<>(Objects.requireNonNull(value), current.height));
    }

    public LengthPercentage getRowGap() {
        return gap().height;
    }

    public void setRowGap(LengthPercentage value) {
        Size<LengthPercentage> current = gap();
        gap(new Size<>(current.width, Objects.requireNonNull(value)));
    }

    public Dimension getFlexBasis() {
        return flexBasis();
    }

    public void setFlexBasis(Dimension value) {
        flexBasis(Objects.requireNonNull(value));
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

    public void setOverflowX(Overflow value) {
        Point<Overflow> current = overflow();
        overflow(new Point<>(Objects.requireNonNull(value), current.y));
    }

    public Overflow getOverflowY() {
        return overflow().y;
    }

    public void setOverflowY(Overflow value) {
        Point<Overflow> current = overflow();
        overflow(new Point<>(current.x, Objects.requireNonNull(value)));
    }

    public GridPlacement getGridRowStart() {
        return gridRow().start;
    }

    public void setGridRowStart(GridPlacement value) {
        Line<GridPlacement> current = gridRow();
        gridRow(new Line<>(Objects.requireNonNull(value), current.end));
    }

    public GridPlacement getGridRowEnd() {
        return gridRow().end;
    }

    public void setGridRowEnd(GridPlacement value) {
        Line<GridPlacement> current = gridRow();
        gridRow(new Line<>(current.start, Objects.requireNonNull(value)));
    }

    public GridPlacement getGridColumnStart() {
        return gridColumn().start;
    }

    public void setGridColumnStart(GridPlacement value) {
        Line<GridPlacement> current = gridColumn();
        gridColumn(new Line<>(Objects.requireNonNull(value), current.end));
    }

    public GridPlacement getGridColumnEnd() {
        return gridColumn().end;
    }

    public void setGridColumnEnd(GridPlacement value) {
        Line<GridPlacement> current = gridColumn();
        gridColumn(new Line<>(current.start, Objects.requireNonNull(value)));
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
            return new Size<>(size.width, size.height);
        }
        if (value instanceof Rect<?> rect) {
            return new Rect<>(rect.left, rect.right, rect.top, rect.bottom);
        }
        if (value instanceof Point<?> point) {
            return new Point<>(point.x, point.y);
        }
        if (value instanceof Line<?> line) {
            return new Line<>(line.start, line.end);
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
