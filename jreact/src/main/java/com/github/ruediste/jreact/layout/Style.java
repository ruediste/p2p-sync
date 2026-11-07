package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.List;

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
}
