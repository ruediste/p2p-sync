package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Port of grid.test.ts from the TypeScript/WebAssembly test-suite. WebAssembly
 * resource cleanup ({@code free()}) is omitted; all functional cases and
 * assertions are preserved.
 */
class GridPortTest {

    private static TrackSizingFunction track(Object min, Object max) {
        return TrackSizingFunction.of(min, max);
    }

    private static GridTemplateComponent single(Object min, Object max) {
        return GridTemplateComponent.single(track(min, max));
    }

    private static void assertLine(GridPlacement placement, int line) {
        assertTrue(placement.isLine());
        assertEquals(line, placement.line);
    }

    private static void assertSpan(GridPlacement placement, int span) {
        assertTrue(placement.isSpan());
        assertEquals(span, placement.span);
    }

    private static void assertAuto(GridPlacement placement) {
        assertTrue(placement.isAuto());
    }

    private static void assertLayout(Layout layout, float x, float y, float width, float height) {
        assertEquals(x, layout.x, 0.01f, "x");
        assertEquals(y, layout.y, 0.01f, "y");
        assertEquals(width, layout.width, 0.01f, "width");
        assertEquals(height, layout.height, 0.01f, "height");
    }

    private static Style sizedStyle(float width, float height) {
        Style style = new Style();
        style.size(Size.of(Dimension.length(width), Dimension.length(height)));
        return style;
    }

    // ---- Grid Style Properties ----

    @Test
    void gridTemplateRowsSetsAndGetsFixedLengthTracks() {
        Style style = new Style();
        style.display(Display.Grid);

        style.gridTemplateRows(List.of(single(100, 100), single(50, 50)));

        assertEquals(2, style.gridTemplateRows().size());
        List<GridTemplateComponent> readback = style.gridTemplateRows();
        assertEquals(100f, readback.get(0).track.min.value, 0.0f);
        assertEquals(50f, readback.get(1).track.min.value, 0.0f);
    }

    @Test
    void gridTemplateRowsSetsAndGetsAutoAndFrTracks() {
        Style style = new Style();
        style.display(Display.Grid);

        style.gridTemplateRows(List.of(single(100, 100), single("auto", "1fr")));

        assertEquals(2, style.gridTemplateRows().size());
    }

    @Test
    void gridTemplateColumnsSetsAndGetsContentSizingTracks() {
        Style style = new Style();
        style.display(Display.Grid);

        style.gridTemplateColumns(List.of(single("50%", "max-content"),
                single("min-content", "fit-content")));

        assertEquals(2, style.gridTemplateColumns().size());
    }

    @Test
    void gridTemplateColumnsSetsAndGetsFixedLengthTracks() {
        Style style = new Style();

        style.gridTemplateColumns(List.of(single(60, 60), single("auto", "1fr")));

        assertEquals(2, style.gridTemplateColumns().size());
    }

    @Test
    void gridAutoRowsSetsAndGetsContentSizingTracks() {
        Style style = new Style();
        style.display(Display.Grid);

        style.gridAutoRows(List.of(track("min-content", "max-content")));

        assertEquals(1, style.gridAutoRows().size());
        TrackSizingFunction readback = style.gridAutoRows().get(0);
        assertEquals(MinTrackSizing.Kind.MIN_CONTENT, readback.min.kind);
        assertEquals(MaxTrackSizing.Kind.MAX_CONTENT, readback.max.kind);
    }

    @Test
    void gridAutoColumnsSetsAndGetsFrTracks() {
        Style style = new Style();
        style.display(Display.Grid);

        style.gridAutoColumns(List.of(track("auto", "1fr")));

        assertEquals(1, style.gridAutoColumns().size());
    }

    @Test
    void gridAutoRowsSetsMultipleTracks() {
        Style style = new Style();

        style.gridAutoRows(List.of(track(50, 50), track("auto", "1fr")));

        assertEquals(2, style.gridAutoRows().size());
    }

    @Test
    void gridTemplateRowsSetsRepetitionTracks() {
        Style style = new Style();

        style.gridTemplateRows(List.of(GridTemplateComponent.repeat(3, List.of(track(10, 10)))));

        assertEquals(1, style.gridTemplateRows().size());
    }

    @Test
    void gridTemplateColumnsSetsRepetitionWithLineNames() {
        Style style = new Style();

        style.gridTemplateColumns(List.of(GridTemplateComponent.repeat(2,
                List.of(track(10, 10)),
                List.of(List.of("start"), List.of("middle"), List.of("end")))));

        assertEquals(1, style.gridTemplateColumns().size());
    }

    @Test
    void gridTemplateRowsSetsAutoFillRepetition() {
        Style style = new Style();

        style.gridTemplateRows(List.of(GridTemplateComponent.repeat("auto-fill",
                List.of(track(100, 100)))));

        assertEquals(1, style.gridTemplateRows().size());
    }

    @Test
    void gridTemplateColumnsSetsAutoFitRepetition() {
        Style style = new Style();

        style.gridTemplateColumns(List.of(GridTemplateComponent.repeat("auto-fit",
                List.of(track(50, "1fr")))));

        assertEquals(1, style.gridTemplateColumns().size());
    }

    @Test
    void gridRowHandlesLineNumbers() {
        Style style = new Style();

        style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(3)));
        assertLine(style.gridRow().start, 1);
        assertLine(style.gridRow().end, 3);
    }

    @Test
    void gridColumnHandlesLineNumbers() {
        Style style = new Style();

        style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(4)));
        assertLine(style.gridColumn().start, 1);
        assertLine(style.gridColumn().end, 4);
    }

    @Test
    void gridRowHandlesNegativeLineIndices() {
        Style style = new Style();

        style.gridRow(Line.of(GridPlacement.line(-1), GridPlacement.line(-2)));
        assertLine(style.gridRow().start, -1);
        assertLine(style.gridRow().end, -2);

        style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(-1)));
        assertLine(style.gridRow().start, 1);
        assertLine(style.gridRow().end, -1);
    }

    @Test
    void gridColumnHandlesNegativeLineIndices() {
        Style style = new Style();

        style.gridColumn(Line.of(GridPlacement.line(-3), GridPlacement.line(-1)));
        assertLine(style.gridColumn().start, -3);
        assertLine(style.gridColumn().end, -1);
    }

    @Test
    void gridRowHandlesSpan() {
        Style style = new Style();

        style.gridRow(Line.of(GridPlacement.line(2), GridPlacement.span(2)));
        assertLine(style.gridRow().start, 2);
        assertSpan(style.gridRow().end, 2);
    }

    @Test
    void gridColumnHandlesSpan() {
        Style style = new Style();

        style.gridColumn(Line.of(GridPlacement.auto(), GridPlacement.span(3)));
        assertAuto(style.gridColumn().start);
        assertSpan(style.gridColumn().end, 3);
    }

    @Test
    void gridRowHandlesNamedLinePlacement() {
        Style style = new Style();

        style.gridRow(Line.of(GridPlacement.line(1, "header"), GridPlacement.auto()));
        assertEquals("header", style.gridRow().start.ident);
        assertEquals(1, style.gridRow().start.line);
        assertAuto(style.gridRow().end);
    }

    @Test
    void gridColumnHandlesNamedSpan() {
        Style style = new Style();

        style.gridColumn(Line.of(GridPlacement.span(2, "sidebar"), GridPlacement.auto()));
        assertEquals(2, style.gridColumn().start.span);
        assertEquals("sidebar", style.gridColumn().start.ident);
    }

    @Test
    void gridRowHandlesSpanWithVariousValues() {
        Style style = new Style();

        // Span 1
        style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.span(1)));
        assertSpan(style.gridRow().end, 1);

        // Large span
        style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.span(10)));
        assertSpan(style.gridRow().end, 10);

        // Span on start
        style.gridRow(Line.of(GridPlacement.span(3), GridPlacement.line(5)));
        assertSpan(style.gridRow().start, 3);
        assertLine(style.gridRow().end, 5);
    }

    @Test
    void gridColumnHandlesSpanOnBothSides() {
        Style style = new Style();

        style.gridColumn(Line.of(GridPlacement.span(2), GridPlacement.span(3)));
        assertSpan(style.gridColumn().start, 2);
        assertSpan(style.gridColumn().end, 3);
    }

    @Test
    void gridRowHandlesAutoPlacement() {
        Style style = new Style();

        style.gridRow(Line.of(GridPlacement.auto(), GridPlacement.auto()));
        assertAuto(style.gridRow().start);
        assertAuto(style.gridRow().end);

        style.gridRow(Line.of(GridPlacement.auto(), GridPlacement.line(3)));
        assertAuto(style.gridRow().start);
        assertLine(style.gridRow().end, 3);
    }

    @Test
    void gridTemplateRowNamesSetsAndGetsLineNames() {
        Style style = new Style();

        style.gridTemplateRowNames(List.of(List.of("header-start"),
                List.of("header-end", "main-start")));

        List<List<String>> names = style.gridTemplateRowNames();
        assertEquals(2, names.size());
        assertEquals("header-start", names.get(0).get(0));
        assertEquals("main-start", names.get(1).get(1));
    }

    @Test
    void gridTemplateColumnNamesSetsAndGetsLineNames() {
        Style style = new Style();

        style.gridTemplateColumnNames(List.of(List.of("col-start"), List.of("col-end")));

        assertEquals(2, style.gridTemplateColumnNames().size());
    }

    @Test
    void gridTemplateRowNamesHandlesEmptyLineNames() {
        Style style = new Style();

        style.gridTemplateRowNames(List.of(List.of(), List.of("named-line"), List.of(),
                List.of("another-name")));
        assertEquals(4, style.gridTemplateRowNames().size());
        assertEquals(List.of(), style.gridTemplateRowNames().get(0));
        assertEquals(List.of("named-line"), style.gridTemplateRowNames().get(1));
    }

    @Test
    void gridTemplateColumnNamesHandlesMultipleNamesPerLine() {
        Style style = new Style();

        style.gridTemplateColumnNames(List.of(
                List.of("content-start", "sidebar-end", "main-start"),
                List.of("content-end", "sidebar-start")));
        assertEquals(2, style.gridTemplateColumnNames().size());
        assertEquals(List.of("content-start", "sidebar-end", "main-start"),
                style.gridTemplateColumnNames().get(0));
    }

    @Test
    void gridTemplateAreasSetsAndGetsAreas() {
        Style style = new Style();

        List<GridTemplateArea> areas = List.of(new GridTemplateArea("header", 1, 2, 1, 3));
        style.setGridTemplateAreas(areas);

        assertEquals(1, style.getGridTemplateAreas().size());
        GridTemplateArea area = style.getGridTemplateAreas().get(0);
        assertEquals("header", area.name);
        assertEquals(2, area.rowEnd);
    }

    @Test
    void gridTemplateAreasHandlesComplexHolyGrailLayout() {
        Style style = new Style();

        style.setGridTemplateAreas(List.of(
                new GridTemplateArea("header", 1, 2, 1, 4),
                new GridTemplateArea("nav", 2, 3, 1, 2),
                new GridTemplateArea("main", 2, 3, 2, 3),
                new GridTemplateArea("aside", 2, 3, 3, 4),
                new GridTemplateArea("footer", 3, 4, 1, 4)));

        assertEquals(5, style.getGridTemplateAreas().size());
        assertEquals("header", style.getGridTemplateAreas().get(0).name);
        assertEquals("footer", style.getGridTemplateAreas().get(4).name);

        GridTemplateArea header = style.getGridTemplateAreas().get(0);
        assertEquals(1, header.rowStart);
        assertEquals(4, header.columnEnd);
    }

    @Test
    void gridAutoFlowDefaultsToRow() {
        Style style = new Style();
        assertEquals(GridAutoFlow.Row, style.gridAutoFlow());
    }

    @Test
    void gridAutoFlowSetsAndGetsAllVariants() {
        Style style = new Style();

        List<GridAutoFlow> variants = List.of(GridAutoFlow.Row, GridAutoFlow.Column,
                GridAutoFlow.RowDense, GridAutoFlow.ColumnDense);

        for (GridAutoFlow variant : variants) {
            style.gridAutoFlow(variant);
            assertEquals(variant, style.gridAutoFlow());
        }
    }

    // ---- Grid Layout Computation ----

    @Test
    void validatesBasicGridLayoutWithExplicitPlacement() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);

        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout rootLayout = root.layout;
        assertEquals(100f, rootLayout.width, 0.01f);
        assertEquals(100f, rootLayout.height, 0.01f);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;
        assertTrue(child2Layout.x >= child1Layout.x);
    }

    @Test
    void validatesGridWithFixedColumnWidths() {

        Style rootStyle = sizedStyle(200, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateColumns(List.of(single(50, 50), single(100, 100),
                single(50, 50)));

        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(50f, child1Layout.width, 0.01f);
        assertEquals(100f, child2Layout.width, 0.01f);
        assertEquals(50f, child2Layout.x, 0.01f);
    }

    @Test
    void validatesGridAutoFlowRow() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridAutoFlow(GridAutoFlow.Row);
        rootStyle.gridTemplateColumns(List.of(single(50, 50), single(50, 50)));

        Node root = new Node(rootStyle);

        List<Node> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            children.add(new Node(sizedStyle(20, 20)));
            root.addChild(children.get(i));
        }

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout layout0 = children.get(0).layout;
        Layout layout1 = children.get(1).layout;
        Layout layout2 = children.get(2).layout;
        Layout layout3 = children.get(3).layout;

        // First row
        assertEquals(layout0.y, layout1.y, 0.01f);
        assertTrue(layout1.x > layout0.x);

        // Second row
        assertEquals(layout2.y, layout3.y, 0.01f);
        assertTrue(layout2.y > layout0.y);
    }

    @Test
    void validatesGridAutoFlowColumn() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridAutoFlow(GridAutoFlow.Column);
        rootStyle.gridTemplateRows(List.of(single(50, 50), single(50, 50)));

        Node root = new Node(rootStyle);

        List<Node> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            children.add(new Node(sizedStyle(20, 20)));
            root.addChild(children.get(i));
        }

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout layout0 = children.get(0).layout;
        Layout layout1 = children.get(1).layout;
        Layout layout2 = children.get(2).layout;

        // First column
        assertEquals(layout0.x, layout1.x, 0.01f);
        assertTrue(layout1.y > layout0.y);

        // Second column
        assertEquals(layout2.y, layout0.y, 0.01f);
        assertTrue(layout2.x > layout0.x);
    }

    @Test
    void validatesGridWithRowSpan() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateRows(List.of(single(50, 50), single(50, 50)));

        Node root = new Node(rootStyle);

        // Child spans 2 rows
        Style childStyle = new Style();
        childStyle.gridRow(Line.of(GridPlacement.line(1), GridPlacement.span(2)));
        childStyle.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child = new Node(childStyle);

        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(100f, childLayout.height, 0.01f);
    }

    @Test
    void validatesGridWithColumnSpan() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateColumns(List.of(single(50, 50), single(50, 50)));

        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        childStyle.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.span(2)));
        Node child = new Node(childStyle);

        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(100f, childLayout.width, 0.01f);
    }

    @Test
    void validatesGridWithGapBetweenChildren() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(10)));
        rootStyle.gridTemplateColumns(List.of(single(45, 45), single(45, 45)));

        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        float child1Right = child1Layout.x + child1Layout.width;
        assertEquals(10f, child2Layout.x - child1Right, 0.5f);
    }

    @Test
    void validatesGridWithRowAndColumnGaps() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(20)));
        rootStyle.gridTemplateColumns(List.of(single(45, 45), single(45, 45)));
        rootStyle.gridTemplateRows(List.of(single(40, 40), single(40, 40)));

        Node root = new Node(rootStyle);

        List<Node> children = new ArrayList<>();
        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 2; col++) {
                Style style = new Style();
                style.gridRow(Line.of(GridPlacement.line(row), GridPlacement.line(row + 1)));
                style.gridColumn(Line.of(GridPlacement.line(col),
                        GridPlacement.line(col + 1)));
                Node child = new Node(style);
                children.add(child);
                root.addChild(child);
            }
        }

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        // Check column gap
        Layout layout0 = children.get(0).layout;
        Layout layout1 = children.get(1).layout;
        assertEquals(10f, layout1.x - (layout0.x + layout0.width), 0.5f);

        // Check row gap
        Layout layout2 = children.get(2).layout;
        assertEquals(20f, layout2.y - (layout0.y + layout0.height), 0.5f);
    }

    @Test
    void validatesGridWithCenterAlignment() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.layoutJustifyContent(JustifyContent.Center);
        rootStyle.layoutAlignItems(AlignItems.Center);

        Node root = new Node(rootStyle);

        Node child = new Node(sizedStyle(20, 20));

        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(40f, childLayout.x, 0.01f);
        assertEquals(40f, childLayout.y, 0.01f);
    }

    @Test
    void validatesGridWithScrollbarWidth() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.scrollbarWidth(15);

        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        Node child = new Node(childStyle);

        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertTrue(childLayout.width <= 100);
        assertTrue(childLayout.height <= 100);
    }

    @Test
    void validatesGridWithFrUnits() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateColumns(List.of(single("auto", "1fr"), single("auto", "2fr")));

        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // 1fr : 2fr = 1:2 ratio (with tolerance for integer rounding)
        assertTrue(Math.abs(child2Layout.width - child1Layout.width * 2) <= 2);
    }

    @Test
    void validatesGridWithMixedFixedAndFrUnits() {

        Style rootStyle = sizedStyle(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateColumns(List.of(single(20, 20), single("auto", "1fr")));

        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(20f, child1Layout.width, 0.01f);
        assertEquals(80f, child2Layout.width, 0.01f);
    }

    // ---- Implicit grid row sizing ----

    @Test
    void resolvesPercentageHeightsInImplicitGridRows() {
        Style firstStyle = new Style();
        firstStyle.setHeight("50%");
        Node first = new Node(firstStyle);
        Node second = new Node(sizedStyle(50, 20));

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.setWidth(100);
        rootStyle.setHeight(100);
        Node root = new Node(rootStyle, List.of(first, second));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);
        assertLayout(first.layout, 0, 0, 100, 20);
        assertLayout(second.layout, 0, 40, 50, 20);
    }

    // ---- Grid track sizing and serialization ----

    private static void setTracks(Style style, String property, List<TrackSizingFunction> tracks) {
        switch (property) {
            case "gridAutoRows" -> style.gridAutoRows(tracks);
            case "gridAutoColumns" -> style.gridAutoColumns(tracks);
            case "gridTemplateRows" -> style.gridTemplateRows(tracks.stream()
                    .map(GridTemplateComponent::single).toList());
            case "gridTemplateColumns" -> style.gridTemplateColumns(tracks.stream()
                    .map(GridTemplateComponent::single).toList());
            default -> throw new IllegalArgumentException("Unknown grid property: " + property);
        }
    }

    private static Object getTracks(Style style, String property) {
        return switch (property) {
            case "gridAutoRows" -> style.gridAutoRows();
            case "gridAutoColumns" -> style.gridAutoColumns();
            case "gridTemplateRows" -> style.gridTemplateRows();
            case "gridTemplateColumns" -> style.gridTemplateColumns();
            default -> throw new IllegalArgumentException("Unknown grid property: " + property);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<TrackSizingFunction> trackList(Object value, String property) {
        List<TrackSizingFunction> result = new ArrayList<>();
        if (property.startsWith("gridTemplate")) {
            for (GridTemplateComponent component : (List<GridTemplateComponent>) value) {
                result.add(component.track);
            }
        } else {
            result.addAll((List<TrackSizingFunction>) value);
        }
        return result;
    }

    @ParameterizedTest(name = "resolves percentages in {0} against the container")
    @ValueSource(strings = { "gridAutoRows", "gridAutoColumns", "gridTemplateRows",
            "gridTemplateColumns" })
    void resolvesPercentagesAgainstTheContainer(String property) {
        boolean columns = property.endsWith("Columns");
        List<TrackSizingFunction> tracks = List.of(track("50%", "50%"), track("50%", "50%"));
        List<Node> children = List.of(new Node(new Style()), new Node(new Style()));
        Style style = new Style();
        style.display(Display.Grid);
        style.setWidth(200);
        style.setHeight(200);
        style.gridAutoFlow(columns ? GridAutoFlow.Column : GridAutoFlow.Row);
        setTracks(style, property, tracks);
        Node root = new Node(style, children);

        LayoutEngine.computeLayout(root, (float) 200, (float) 200);
        if (columns) {
            assertLayout(children.get(0).layout, 0, 0, 100, 200);
            assertLayout(children.get(1).layout, 100, 0, 100, 200);
        } else {
            assertLayout(children.get(0).layout, 0, 0, 200, 100);
            assertLayout(children.get(1).layout, 0, 100, 200, 100);
        }
        assertEquals(tracks, trackList(getTracks(root.style.copy(), property), property));
        assertEquals(tracks, trackList(getTracks(style, property), property));
    }

    @Test
    void resolvesPercentageTracksNestedInARepeat() {
        List<Node> children = List.of(new Node(new Style()), new Node(new Style()));
        Style style = new Style();
        style.display(Display.Grid);
        style.setWidth(200);
        style.setHeight(20);
        style.gridTemplateColumns(List.of(GridTemplateComponent.repeat(2,
                List.of(track("50%", "50%")))));
        Node root = new Node(style, children);

        LayoutEngine.computeLayout(root, (float) 200, (float) 20);
        assertEquals(100f, children.get(0).layout.width, 0.01f);
        assertEquals(100f, children.get(1).layout.x, 0.01f);
        assertEquals(100f, children.get(1).layout.width, 0.01f);
        assertEquals(List.of(GridTemplateComponent.repeat(2, List.of(track("50%", "50%")))),
                style.gridTemplateColumns());
    }

    @Test
    void limitsATrackWithAPercentageMaximumAndANumericMinimum() {
        Node child = new Node(new Style());
        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.setWidth(200);
        rootStyle.setHeight(20);
        rootStyle.gridTemplateColumns(List.of(single(0, "50%")));
        Node root = new Node(rootStyle, List.of(child));

        LayoutEngine.computeLayout(root, (float) 200, (float) 20);
        assertEquals(100f, child.layout.width, 0.01f);
    }

    @ParameterizedTest(name = "preserves existing track values through {0} reads")
    @ValueSource(strings = { "gridAutoRows", "gridAutoColumns", "gridTemplateRows",
            "gridTemplateColumns" })
    void preservesExistingTrackValuesThroughReads(String property) {
        List<TrackSizingFunction> tracks = List.of(
                track(12, 24),
                track("25%", "75%"),
                track("auto", "1.5fr"),
                track("min-content", "max-content"),
                track("max-content", "min-content"),
                track("auto", "fit-content"),
                track("auto", "auto"));
        Style original = new Style();
        setTracks(original, property, tracks);
        Node node = new Node(original);
        Style copied = node.style.copy();

        assertEquals(tracks, trackList(getTracks(copied, property), property));
        setTracks(original, property, tracks);
        assertEquals(tracks, trackList(getTracks(original, property), property));
    }
}
