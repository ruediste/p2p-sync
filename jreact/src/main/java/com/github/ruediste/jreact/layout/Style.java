package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CSS layout properties for one node.
 *
 * <p>
 * Style instances are mutable; {@link #copy()} produces an independent deep
 * copy. {@link Node} copies a style when it is constructed.
 */
public final class Style {

    public Display display = Display.Flex;
    public Position position = Position.Static;
    public Direction direction = Direction.Ltr;
    public Float floatMode = Float.None;
    public Clear clear = Clear.None;
    public BoxSizing boxSizing = BoxSizing.BorderBox;
    public TextAlign textAlign = TextAlign.Auto;

    public AlignItems alignItems;
    public AlignSelf alignSelf = AlignSelf.Auto;
    public AlignContent alignContent;
    public JustifyContent justifyContent;
    public AlignItems justifyItems;
    public AlignSelf justifySelf = AlignSelf.Auto;

    public FlexDirection flexDirection = FlexDirection.Row;
    public FlexWrap flexWrap = FlexWrap.NoWrap;
    public float flexGrow;
    public float flexShrink = 1;
    public LengthPercentageAuto flexBasis = LengthPercentageAuto.auto();

    public Size<Dimension> size = Size.of(Dimension.auto(), Dimension.auto());
    public Size<Dimension> minSize = Size.of(Dimension.auto(), Dimension.auto());
    public Size<Dimension> maxSize = Size.of(Dimension.auto(), Dimension.auto());
    public java.lang.Float aspectRatio;

    /** width is the column gap, height is the row gap. */
    public Size<LengthPercentage> gap = Size.of(LengthPercentage.zero(), LengthPercentage.zero());
    public Rect<LengthPercentageAuto> margin = Rect.all(LengthPercentageAuto.length(0));
    public Rect<LengthPercentage> padding = Rect.all(LengthPercentage.zero());
    public Rect<LengthPercentage> border = Rect.all(LengthPercentage.zero());
    public Rect<LengthPercentageAuto> inset = Rect.all(LengthPercentageAuto.auto());

    public boolean itemIsTable;
    public boolean itemIsReplaced;
    public float scrollbarWidth;
    public Point<Overflow> overflow = Point.of(Overflow.Visible, Overflow.Visible);

    public GridAutoFlow gridAutoFlow = GridAutoFlow.Row;
    public Line<GridPlacement> gridRow = Line.of(GridPlacement.auto(), GridPlacement.auto());
    public Line<GridPlacement> gridColumn = Line.of(GridPlacement.auto(), GridPlacement.auto());
    public List<GridTemplateComponent> gridTemplateRows = new ArrayList<>();
    public List<GridTemplateComponent> gridTemplateColumns = new ArrayList<>();
    public List<TrackSizingFunction> gridAutoRows = new ArrayList<>();
    public List<TrackSizingFunction> gridAutoColumns = new ArrayList<>();
    public List<List<String>> gridTemplateRowNames = new ArrayList<>();
    public List<List<String>> gridTemplateColumnNames = new ArrayList<>();

    private List<GridTemplateArea> gridTemplateAreas = new ArrayList<>();
    private Integer explicitAreaRowCount;
    private Integer explicitAreaColumnCount;
    private int gridTemplateAreaRowCount;
    private int gridTemplateAreaColumnCount;

    public Style() {
    }

    public Style(Map<String, ?> properties) {
        set(properties);
    }

    public Style copy() {
        Style copy = new Style();
        copy.display = display;
        copy.position = position;
        copy.direction = direction;
        copy.floatMode = floatMode;
        copy.clear = clear;
        copy.boxSizing = boxSizing;
        copy.textAlign = textAlign;
        copy.alignItems = alignItems;
        copy.alignSelf = alignSelf;
        copy.alignContent = alignContent;
        copy.justifyContent = justifyContent;
        copy.justifyItems = justifyItems;
        copy.justifySelf = justifySelf;
        copy.flexDirection = flexDirection;
        copy.flexWrap = flexWrap;
        copy.flexGrow = flexGrow;
        copy.flexShrink = flexShrink;
        copy.flexBasis = flexBasis;
        copy.size = size.copy();
        copy.minSize = minSize.copy();
        copy.maxSize = maxSize.copy();
        copy.aspectRatio = aspectRatio;
        copy.gap = gap.copy();
        copy.margin = margin.copy();
        copy.padding = padding.copy();
        copy.border = border.copy();
        copy.inset = inset.copy();
        copy.itemIsTable = itemIsTable;
        copy.itemIsReplaced = itemIsReplaced;
        copy.scrollbarWidth = scrollbarWidth;
        copy.overflow = overflow.copy();
        copy.gridAutoFlow = gridAutoFlow;
        copy.gridRow = Line.of(gridRow.start, gridRow.end);
        copy.gridColumn = Line.of(gridColumn.start, gridColumn.end);
        copy.gridTemplateRows = new ArrayList<>(gridTemplateRows);
        copy.gridTemplateColumns = new ArrayList<>(gridTemplateColumns);
        copy.gridAutoRows = new ArrayList<>(gridAutoRows);
        copy.gridAutoColumns = new ArrayList<>(gridAutoColumns);
        copy.gridTemplateRowNames = copyNames(gridTemplateRowNames);
        copy.gridTemplateColumnNames = copyNames(gridTemplateColumnNames);
        copy.gridTemplateAreas = new ArrayList<>(gridTemplateAreas);
        copy.explicitAreaRowCount = explicitAreaRowCount;
        copy.explicitAreaColumnCount = explicitAreaColumnCount;
        copy.gridTemplateAreaRowCount = gridTemplateAreaRowCount;
        copy.gridTemplateAreaColumnCount = gridTemplateAreaColumnCount;
        return copy;
    }

    private static List<List<String>> copyNames(List<List<String>> names) {
        List<List<String>> copy = new ArrayList<>(names.size());
        for (List<String> line : names) {
            copy.add(new ArrayList<>(line));
        }
        return copy;
    }


    public List<GridTemplateArea> getGridTemplateAreas() {
        return gridTemplateAreas;
    }

    public AlignSelf getAlignSelf() {
        return alignSelf;
    }

    public void setAlignSelf(AlignSelf value) {
        alignSelf = value == null ? AlignSelf.Auto : value;
    }

    public AlignSelf getJustifySelf() {
        return justifySelf;
    }

    public void setJustifySelf(AlignSelf value) {
        justifySelf = value == null ? AlignSelf.Auto : value;
    }

    public Dimension getWidth() {
        return size.width;
    }

    public void setWidth(Object value) {
        size.width = Dimension.of(value);
    }

    public Dimension getHeight() {
        return size.height;
    }

    public void setHeight(Object value) {
        size.height = Dimension.of(value);
    }

    public Dimension getMinWidth() {
        return minSize.width;
    }

    public void setMinWidth(Object value) {
        minSize.width = Dimension.of(value);
    }

    public Dimension getMinHeight() {
        return minSize.height;
    }

    public void setMinHeight(Object value) {
        minSize.height = Dimension.of(value);
    }

    public Dimension getMaxWidth() {
        return maxSize.width;
    }

    public void setMaxWidth(Object value) {
        maxSize.width = Dimension.of(value);
    }

    public Dimension getMaxHeight() {
        return maxSize.height;
    }

    public void setMaxHeight(Object value) {
        maxSize.height = Dimension.of(value);
    }

    public LengthPercentageAuto getMarginLeft() {
        return margin.left;
    }

    public void setMarginLeft(Object value) {
        margin.left = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getMarginRight() {
        return margin.right;
    }

    public void setMarginRight(Object value) {
        margin.right = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getMarginTop() {
        return margin.top;
    }

    public void setMarginTop(Object value) {
        margin.top = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getMarginBottom() {
        return margin.bottom;
    }

    public void setMarginBottom(Object value) {
        margin.bottom = LengthPercentageAuto.of(value);
    }

    public LengthPercentage getPaddingLeft() {
        return padding.left;
    }

    public void setPaddingLeft(Object value) {
        padding.left = LengthPercentage.of(value);
    }

    public LengthPercentage getPaddingRight() {
        return padding.right;
    }

    public void setPaddingRight(Object value) {
        padding.right = LengthPercentage.of(value);
    }

    public LengthPercentage getPaddingTop() {
        return padding.top;
    }

    public void setPaddingTop(Object value) {
        padding.top = LengthPercentage.of(value);
    }

    public LengthPercentage getPaddingBottom() {
        return padding.bottom;
    }

    public void setPaddingBottom(Object value) {
        padding.bottom = LengthPercentage.of(value);
    }

    public LengthPercentage getBorderLeft() {
        return border.left;
    }

    public void setBorderLeft(Object value) {
        border.left = LengthPercentage.of(value);
    }

    public LengthPercentage getBorderRight() {
        return border.right;
    }

    public void setBorderRight(Object value) {
        border.right = LengthPercentage.of(value);
    }

    public LengthPercentage getBorderTop() {
        return border.top;
    }

    public void setBorderTop(Object value) {
        border.top = LengthPercentage.of(value);
    }

    public LengthPercentage getBorderBottom() {
        return border.bottom;
    }

    public void setBorderBottom(Object value) {
        border.bottom = LengthPercentage.of(value);
    }

    public LengthPercentageAuto getLeft() {
        return inset.left;
    }

    public void setLeft(Object value) {
        inset.left = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getRight() {
        return inset.right;
    }

    public void setRight(Object value) {
        inset.right = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getTop() {
        return inset.top;
    }

    public void setTop(Object value) {
        inset.top = LengthPercentageAuto.of(value);
    }

    public LengthPercentageAuto getBottom() {
        return inset.bottom;
    }

    public void setBottom(Object value) {
        inset.bottom = LengthPercentageAuto.of(value);
    }

    public LengthPercentage getColumnGap() {
        return gap.width;
    }

    public void setColumnGap(Object value) {
        gap.width = LengthPercentage.of(value);
    }

    public LengthPercentage getRowGap() {
        return gap.height;
    }

    public void setRowGap(Object value) {
        gap.height = LengthPercentage.of(value);
    }

    public LengthPercentageAuto getFlexBasis() {
        return flexBasis;
    }

    public void setFlexBasis(Object value) {
        flexBasis = LengthPercentageAuto.of(value);
    }

    public java.lang.Float getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(java.lang.Float value) {
        aspectRatio = value;
    }

    public Overflow getOverflowX() {
        return overflow.x;
    }

    public void setOverflowX(Object value) {
        overflow.x = Overflow.of(value);
    }

    public Overflow getOverflowY() {
        return overflow.y;
    }

    public void setOverflowY(Object value) {
        overflow.y = Overflow.of(value);
    }

    public GridPlacement getGridRowStart() {
        return gridRow.start;
    }

    public void setGridRowStart(Object value) {
        gridRow.start = GridPlacement.of(value);
    }

    public GridPlacement getGridRowEnd() {
        return gridRow.end;
    }

    public void setGridRowEnd(Object value) {
        gridRow.end = GridPlacement.of(value);
    }

    public GridPlacement getGridColumnStart() {
        return gridColumn.start;
    }

    public void setGridColumnStart(Object value) {
        gridColumn.start = GridPlacement.of(value);
    }

    public GridPlacement getGridColumnEnd() {
        return gridColumn.end;
    }

    public void setGridColumnEnd(Object value) {
        gridColumn.end = GridPlacement.of(value);
    }

    public void setGridRow(Line<GridPlacement> value) {
        gridRow = Line.of(value.start, value.end);
    }

    public void setGridColumn(Line<GridPlacement> value) {
        gridColumn = Line.of(value.start, value.end);
    }

    public void setGridTemplateRows(List<GridTemplateComponent> value) {
        gridTemplateRows = new ArrayList<>(value);
    }

    public void setGridTemplateColumns(List<GridTemplateComponent> value) {
        gridTemplateColumns = new ArrayList<>(value);
    }

    public void setGridAutoRows(List<TrackSizingFunction> value) {
        gridAutoRows = new ArrayList<>(value);
    }

    public void setGridAutoColumns(List<TrackSizingFunction> value) {
        gridAutoColumns = new ArrayList<>(value);
    }

    public void setGridTemplateRowNames(List<List<String>> value) {
        gridTemplateRowNames = copyNames(value);
    }

    public void setGridTemplateColumnNames(List<List<String>> value) {
        gridTemplateColumnNames = copyNames(value);
    }

    public int getGridTemplateAreaRowCount() {
        return gridTemplateAreaRowCount;
    }

    public int getGridTemplateAreaColumnCount() {
        return gridTemplateAreaColumnCount;
    }

    public void setGridTemplateAreas(List<GridTemplateArea> areas) {
        this.gridTemplateAreas = areas == null ? new ArrayList<>() : new ArrayList<>(areas);
        recomputeAreaCounts();
    }

    public void setGridTemplateAreaRowCount(int count) {
        explicitAreaRowCount = count;
        gridTemplateAreaRowCount = Math.max(count, inferredAreaRows());
    }

    public void setGridTemplateAreaColumnCount(int count) {
        explicitAreaColumnCount = count;
        gridTemplateAreaColumnCount = Math.max(count, inferredAreaColumns());
    }

    private int inferredAreaRows() {
        int rows = 0;
        for (GridTemplateArea area : gridTemplateAreas) {
            rows = Math.max(rows, area.rowEnd - 1);
        }
        return rows;
    }

    private int inferredAreaColumns() {
        int columns = 0;
        for (GridTemplateArea area : gridTemplateAreas) {
            columns = Math.max(columns, area.columnEnd - 1);
        }
        return columns;
    }

    private void recomputeAreaCounts() {
        gridTemplateAreaRowCount = explicitAreaRowCount == null ? inferredAreaRows()
                : Math.max(explicitAreaRowCount, inferredAreaRows());
        gridTemplateAreaColumnCount = explicitAreaColumnCount == null ? inferredAreaColumns()
                : Math.max(explicitAreaColumnCount, inferredAreaColumns());
    }


    /** Returns a single value for one key, an array for several, or null for none. */
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

    public Object getProperty(String key) {
        return switch (key) {
            case "display" -> display;
            case "position" -> position;
            case "direction" -> direction;
            case "float" -> floatMode;
            case "clear" -> clear;
            case "boxSizing" -> boxSizing;
            case "textAlign" -> textAlign;
            case "alignItems" -> alignItems;
            case "alignSelf" -> alignSelf;
            case "alignContent" -> alignContent;
            case "justifyContent" -> justifyContent;
            case "justifyItems" -> justifyItems;
            case "justifySelf" -> justifySelf;
            case "flexDirection" -> flexDirection;
            case "flexWrap" -> flexWrap;
            case "flexGrow" -> flexGrow;
            case "flexShrink" -> flexShrink;
            case "flexBasis" -> flexBasis;
            case "size" -> size;
            case "width" -> size.width;
            case "height" -> size.height;
            case "minSize" -> minSize;
            case "minWidth" -> minSize.width;
            case "minHeight" -> minSize.height;
            case "maxSize" -> maxSize;
            case "maxWidth" -> maxSize.width;
            case "maxHeight" -> maxSize.height;
            case "aspectRatio" -> aspectRatio;
            case "gap" -> gap;
            case "columnGap" -> gap.width;
            case "rowGap" -> gap.height;
            case "margin" -> margin;
            case "marginLeft" -> margin.left;
            case "marginRight" -> margin.right;
            case "marginTop" -> margin.top;
            case "marginBottom" -> margin.bottom;
            case "padding" -> padding;
            case "paddingLeft" -> padding.left;
            case "paddingRight" -> padding.right;
            case "paddingTop" -> padding.top;
            case "paddingBottom" -> padding.bottom;
            case "border" -> border;
            case "borderLeft" -> border.left;
            case "borderRight" -> border.right;
            case "borderTop" -> border.top;
            case "borderBottom" -> border.bottom;
            case "inset" -> inset;
            case "left" -> inset.left;
            case "right" -> inset.right;
            case "top" -> inset.top;
            case "bottom" -> inset.bottom;
            case "itemIsTable" -> itemIsTable;
            case "itemIsReplaced" -> itemIsReplaced;
            case "scrollbarWidth" -> scrollbarWidth;
            case "overflow" -> overflow;
            case "overflowX" -> overflow.x;
            case "overflowY" -> overflow.y;
            case "gridAutoFlow" -> gridAutoFlow;
            case "gridRow" -> gridRow;
            case "gridRowStart" -> gridRow.start;
            case "gridRowEnd" -> gridRow.end;
            case "gridColumn" -> gridColumn;
            case "gridColumnStart" -> gridColumn.start;
            case "gridColumnEnd" -> gridColumn.end;
            case "gridTemplateRows" -> gridTemplateRows;
            case "gridTemplateColumns" -> gridTemplateColumns;
            case "gridAutoRows" -> gridAutoRows;
            case "gridAutoColumns" -> gridAutoColumns;
            case "gridTemplateAreas" -> gridTemplateAreas;
            case "gridTemplateRowNames" -> gridTemplateRowNames;
            case "gridTemplateColumnNames" -> gridTemplateColumnNames;
            case "gridTemplateAreaRowCount" -> gridTemplateAreaRowCount;
            case "gridTemplateAreaColumnCount" -> gridTemplateAreaColumnCount;
            default -> throw new IllegalArgumentException("Unknown property path: " + key);
        };
    }

    public Style set(String key, Object value) {
        switch (key) {
            case "display" -> display = parseEnum(value, Display.class, display);
            case "position" -> position = parseEnum(value, Position.class, position);
            case "direction" -> direction = parseEnum(value, Direction.class, direction);
            case "float" -> floatMode = parseEnum(value, Float.class, floatMode);
            case "clear" -> clear = parseEnum(value, Clear.class, clear);
            case "boxSizing" -> boxSizing = parseEnum(value, BoxSizing.class, boxSizing);
            case "textAlign" -> textAlign = parseEnum(value, TextAlign.class, textAlign);
            case "alignItems" -> alignItems = parseEnum(value, AlignItems.class, alignItems);
            case "alignSelf" -> alignSelf = normalizeSelf(parseEnum(value, AlignSelf.class, alignSelf));
            case "alignContent" -> alignContent = parseEnum(value, AlignContent.class, alignContent);
            case "justifyContent" -> justifyContent = parseEnum(value, JustifyContent.class,
                    justifyContent);
            case "justifyItems" -> justifyItems = parseEnum(value, AlignItems.class, justifyItems);
            case "justifySelf" -> justifySelf = normalizeSelf(parseEnum(value, AlignSelf.class, justifySelf));
            case "flexDirection" -> flexDirection = parseEnum(value, FlexDirection.class,
                    flexDirection);
            case "flexWrap" -> flexWrap = parseEnum(value, FlexWrap.class, flexWrap);
            case "flexGrow" -> {
                if (value instanceof Number number) {
                    flexGrow = number.floatValue();
                }
            }
            case "flexShrink" -> {
                if (value instanceof Number number) {
                    flexShrink = number.floatValue();
                }
            }
            case "flexBasis" -> {
                if (value != null) {
                    flexBasis = LengthPercentageAuto.of(value);
                }
            }
            case "size" -> size = dimensionSize(value);
            case "width" -> size.width = Dimension.of(value);
            case "height" -> size.height = Dimension.of(value);
            case "minSize" -> minSize = dimensionSize(value);
            case "minWidth" -> minSize.width = Dimension.of(value);
            case "minHeight" -> minSize.height = Dimension.of(value);
            case "maxSize" -> maxSize = dimensionSize(value);
            case "maxWidth" -> maxSize.width = Dimension.of(value);
            case "maxHeight" -> maxSize.height = Dimension.of(value);
            case "aspectRatio" -> {
                if (value == null) {
                    aspectRatio = null;
                } else if (value instanceof Number number) {
                    aspectRatio = number.floatValue();
                }
            }
            case "gap" -> gap = lengthPercentageSize(value);
            case "columnGap" -> gap.width = LengthPercentage.of(value);
            case "rowGap" -> gap.height = LengthPercentage.of(value);
            case "margin" -> margin = lengthPercentageAutoRect(value);
            case "marginLeft" -> margin.left = LengthPercentageAuto.of(value);
            case "marginRight" -> margin.right = LengthPercentageAuto.of(value);
            case "marginTop" -> margin.top = LengthPercentageAuto.of(value);
            case "marginBottom" -> margin.bottom = LengthPercentageAuto.of(value);
            case "padding" -> padding = lengthPercentageRect(value);
            case "paddingLeft" -> padding.left = LengthPercentage.of(value);
            case "paddingRight" -> padding.right = LengthPercentage.of(value);
            case "paddingTop" -> padding.top = LengthPercentage.of(value);
            case "paddingBottom" -> padding.bottom = LengthPercentage.of(value);
            case "border" -> border = lengthPercentageRect(value);
            case "borderLeft" -> border.left = LengthPercentage.of(value);
            case "borderRight" -> border.right = LengthPercentage.of(value);
            case "borderTop" -> border.top = LengthPercentage.of(value);
            case "borderBottom" -> border.bottom = LengthPercentage.of(value);
            case "inset" -> inset = lengthPercentageAutoRect(value);
            case "left" -> inset.left = LengthPercentageAuto.of(value);
            case "right" -> inset.right = LengthPercentageAuto.of(value);
            case "top" -> inset.top = LengthPercentageAuto.of(value);
            case "bottom" -> inset.bottom = LengthPercentageAuto.of(value);
            case "itemIsTable" -> {
                if (value instanceof Boolean bool) {
                    itemIsTable = bool;
                }
            }
            case "itemIsReplaced" -> {
                if (value instanceof Boolean bool) {
                    itemIsReplaced = bool;
                }
            }
            case "scrollbarWidth" -> {
                if (value instanceof Number number) {
                    scrollbarWidth = number.floatValue();
                }
            }
            case "overflow" -> overflow = overflowPoint(value);
            case "overflowX" -> overflow.x = parseEnum(value, Overflow.class, overflow.x);
            case "overflowY" -> overflow.y = parseEnum(value, Overflow.class, overflow.y);
            case "gridAutoFlow" -> gridAutoFlow = parseEnum(value, GridAutoFlow.class, gridAutoFlow);
            case "gridRow" -> gridRow = gridLine(value);
            case "gridRowStart" -> gridRow.start = GridPlacement.of(value);
            case "gridRowEnd" -> gridRow.end = GridPlacement.of(value);
            case "gridColumn" -> gridColumn = gridLine(value);
            case "gridColumnStart" -> gridColumn.start = GridPlacement.of(value);
            case "gridColumnEnd" -> gridColumn.end = GridPlacement.of(value);
            case "gridTemplateRows" -> gridTemplateRows = templateComponents(value);
            case "gridTemplateColumns" -> gridTemplateColumns = templateComponents(value);
            case "gridAutoRows" -> gridAutoRows = trackFunctions(value);
            case "gridAutoColumns" -> gridAutoColumns = trackFunctions(value);
            case "gridTemplateAreas" -> setGridTemplateAreas(areas(value));
            case "gridTemplateRowNames" -> gridTemplateRowNames = names(value);
            case "gridTemplateColumnNames" -> gridTemplateColumnNames = names(value);
            case "gridTemplateAreaRowCount" -> {
                if (value instanceof Number number) {
                    setGridTemplateAreaRowCount(number.intValue());
                }
            }
            case "gridTemplateAreaColumnCount" -> {
                if (value instanceof Number number) {
                    setGridTemplateAreaColumnCount(number.intValue());
                }
            }
            default -> throw new IllegalArgumentException("Unknown property path for set: " + key);
        }
        return this;
    }

    public Style set(Map<String, ?> properties) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
        return this;
    }


    private static AlignSelf normalizeSelf(AlignSelf value) {
        return value == null ? AlignSelf.Auto : value;
    }

    @SuppressWarnings("unchecked")
    private static <E> E parseEnum(Object value, Class<E> type, E current) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (E) value;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!Double.isFinite(raw) || raw != Math.rint(raw)) {
                return current;
            }
            try {
                Object converted = type.getMethod("fromValue", int.class).invoke(null,
                        (int) raw);
                return converted == null ? current : (E) converted;
            } catch (ReflectiveOperationException ignored) {
                return current;
            }
        }
        return current;
    }

    private static Size<Dimension> dimensionSize(Object value) {
        if (value instanceof Size<?> size) {
            return Size.of(Dimension.of(size.width), Dimension.of(size.height));
        }
        if (value instanceof Map<?, ?> map) {
            return Size.of(Dimension.of(map.get("width")), Dimension.of(map.get("height")));
        }
        throw new IllegalArgumentException("Expected a size, got: " + value);
    }

    private static Size<LengthPercentage> lengthPercentageSize(Object value) {
        if (value instanceof Size<?> size) {
            return Size.of(LengthPercentage.of(size.width), LengthPercentage.of(size.height));
        }
        if (value instanceof Map<?, ?> map) {
            return Size.of(LengthPercentage.of(map.get("width")),
                    LengthPercentage.of(map.get("height")));
        }
        throw new IllegalArgumentException("Expected a size, got: " + value);
    }

    private static Rect<LengthPercentage> lengthPercentageRect(Object value) {
        if (value instanceof Rect<?> rect) {
            return new Rect<>(LengthPercentage.of(rect.left), LengthPercentage.of(rect.right),
                    LengthPercentage.of(rect.top), LengthPercentage.of(rect.bottom));
        }
        if (value instanceof Map<?, ?> map) {
            return new Rect<>(LengthPercentage.of(map.get("left")),
                    LengthPercentage.of(map.get("right")), LengthPercentage.of(map.get("top")),
                    LengthPercentage.of(map.get("bottom")));
        }
        throw new IllegalArgumentException("Expected a rect, got: " + value);
    }

    private static Rect<LengthPercentageAuto> lengthPercentageAutoRect(Object value) {
        if (value instanceof Rect<?> rect) {
            return new Rect<>(LengthPercentageAuto.of(rect.left),
                    LengthPercentageAuto.of(rect.right), LengthPercentageAuto.of(rect.top),
                    LengthPercentageAuto.of(rect.bottom));
        }
        if (value instanceof Map<?, ?> map) {
            return new Rect<>(LengthPercentageAuto.of(map.get("left")),
                    LengthPercentageAuto.of(map.get("right")),
                    LengthPercentageAuto.of(map.get("top")),
                    LengthPercentageAuto.of(map.get("bottom")));
        }
        throw new IllegalArgumentException("Expected a rect, got: " + value);
    }

    private static Point<Overflow> overflowPoint(Object value) {
        if (value instanceof Point<?> point) {
            return Point.of(Overflow.of(point.x), Overflow.of(point.y));
        }
        if (value instanceof Map<?, ?> map) {
            return Point.of(Overflow.of(map.get("x")), Overflow.of(map.get("y")));
        }
        throw new IllegalArgumentException("Expected an overflow point, got: " + value);
    }

    private static Line<GridPlacement> gridLine(Object value) {
        if (value instanceof Line<?> line) {
            return Line.of(GridPlacement.of(line.start), GridPlacement.of(line.end));
        }
        if (value instanceof Map<?, ?> map) {
            return Line.of(GridPlacement.of(map.get("start")), GridPlacement.of(map.get("end")));
        }
        throw new IllegalArgumentException("Expected a grid line, got: " + value);
    }

    private static List<GridTemplateComponent> templateComponents(Object value) {
        List<GridTemplateComponent> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Expected a list of grid tracks, got: " + value);
        }
        for (Object item : iterable) {
            if (item instanceof TrackSizingFunction track) {
                result.add(GridTemplateComponent.single(track));
            } else {
                result.add(GridTemplateComponent.valueOf(item));
            }
        }
        return result;
    }

    private static List<TrackSizingFunction> trackFunctions(Object value) {
        List<TrackSizingFunction> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Expected a list of grid tracks, got: " + value);
        }
        for (Object item : iterable) {
            if (item instanceof GridTemplateComponent component && component.isSingle()) {
                result.add(component.track);
            } else {
                result.add(TrackSizingFunction.valueOf(item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<GridTemplateArea> areas(Object value) {
        List<GridTemplateArea> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Expected a list of grid areas, got: " + value);
        }
        for (Object item : iterable) {
            if (item instanceof GridTemplateArea area) {
                result.add(area);
            } else if (item instanceof Map<?, ?> map) {
                result.add(new GridTemplateArea(String.valueOf(map.get("name")),
                        intValue(map.get("rowStart")), intValue(map.get("rowEnd")),
                        intValue(map.get("columnStart")), intValue(map.get("columnEnd"))));
            } else {
                throw new IllegalArgumentException("Expected a grid area, got: " + item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> names(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<List<String>> result = new ArrayList<>();
        for (Object line : (Iterable<?>) value) {
            List<String> names = new ArrayList<>();
            for (Object name : (Iterable<?>) line) {
                names.add(String.valueOf(name));
            }
            result.add(names);
        }
        return result;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
