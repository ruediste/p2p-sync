package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Port of grid-details.test.ts from the TypeScript/WebAssembly test-suite.
 * WebAssembly resource cleanup ({@code free()}) is omitted; detailed grid
 * expectations are compared through the public fields of {@link DetailedGridInfo}.
 */
class GridDetailsPortTest {

    private static Style sizedStyle(float width, float height) {
        Style style = new Style();
        style.size = Size.of(Dimension.length(width), Dimension.length(height));
        return style;
    }

    private static void assertFloats(List<java.lang.Float> expected, List<java.lang.Float> actual, float delta) {
        assertEquals(expected.size(), actual.size(), "size");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), delta, "element " + i);
        }
    }

    private static void assertPositions(float[][] expected, List<Line<java.lang.Float>> actual) {
        assertEquals(expected.length, actual.size(), "positions");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], actual.get(i).start, 0.01f, "position " + i + " start");
            assertEquals(expected[i][1], actual.get(i).end, 0.01f, "position " + i + " end");
        }
    }

    private static void assertTrackInfo(DetailedGridInfo.TrackInfo info, List<java.lang.Float> sizes,
            List<java.lang.Float> gutters, float[][] positions) {
        assertFloats(sizes, info.sizes, 0.01f);
        assertFloats(gutters, info.gutters, 0.01f);
        assertPositions(positions, info.positions);
    }

    @Test
    void returnsNullWhenNoDetailedGridLayoutHasBeenComputed() {
        Style leafStyle = new Style();
        Style gridStyle = new Style();
        gridStyle.display = Display.Grid;
        Node leaf = new Node(leafStyle);
        Node emptyGrid = new Node(gridStyle);
        Node grid = new Node(gridStyle, List.of(leaf));

        assertNull(grid.detailedGridInfo);
        grid.computeLayout(100, 100);
        assertNotNull(grid.detailedGridInfo);
        assertNull(leaf.detailedGridInfo);
        emptyGrid.computeLayout(100, 100);
        assertNull(emptyGrid.detailedGridInfo);
    }

    @Test
    void reportsGridGapsAndBorderRelativeTrackPositions() {
        Style childStyle = new Style();
        Style rootStyle = sizedStyle(200, 100);
        rootStyle.display = Display.Grid;
        rootStyle.padding = Rect.all(LengthPercentage.length(10));
        rootStyle.border = Rect.all(LengthPercentage.length(2));
        rootStyle.setColumnGap(10);
        rootStyle.setRowGap(5);
        rootStyle.gridTemplateColumns = List.of(single(50, 50), single(70, 70));
        rootStyle.gridTemplateRows = List.of(single(20, 20), single(30, 30));

        Node root = new Node(rootStyle, List.of(new Node(childStyle)));
        root.computeLayout(200, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        if (info == null) {
            throw new AssertionError("Expected detailed grid layout information");
        }
        assertEquals(0, info.columns.negativeImplicitTracks);
        assertEquals(2, info.columns.explicitTracks);
        assertEquals(0, info.columns.positiveImplicitTracks);
        assertTrackInfo(info.columns, List.of(50f, 70f), List.of(0f, 10f, 0f),
                new float[][] { { 12, 62 }, { 72, 142 } });
        assertFloats(List.of(20f, 30f), info.rows.sizes, 0.01f);
        assertFloats(List.of(0f, 5f, 0f), info.rows.gutters, 0.01f);
        assertPositions(new float[][] { { 12, 32 }, { 37, 67 } }, info.rows.positions);
        assertEquals(1, info.items.size());
        DetailedGridInfo.ItemInfo item = info.items.get(0);
        assertEquals(1, item.rowStart);
        assertEquals(2, item.rowEnd);
        assertEquals(1, item.columnStart);
        assertEquals(2, item.columnEnd);
    }

    @Test
    void includesDistributedAlignmentSpaceInGridGutters() {
        Style childStyle = new Style();
        Style rootStyle = sizedStyle(300, 100);
        rootStyle.display = Display.Grid;
        rootStyle.setColumnGap(10);
        rootStyle.justifyContent = JustifyContent.SpaceBetween;
        rootStyle.alignContent = AlignContent.Center;
        rootStyle.gridTemplateColumns = List.of(single(50, 50), single(50, 50));
        rootStyle.gridTemplateRows = List.of(single(40, 40));

        Node root = new Node(rootStyle, List.of(new Node(childStyle)));
        root.computeLayout(300, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        if (info == null) {
            throw new AssertionError("Expected detailed grid layout information");
        }
        assertFloats(List.of(50f, 50f), info.columns.sizes, 0.01f);
        assertFloats(List.of(0f, 200f, 0f), info.columns.gutters, 0.01f);
        assertPositions(new float[][] { { 0, 50 }, { 250, 300 } }, info.columns.positions);
        assertPositions(new float[][] { { 30, 70 } }, info.rows.positions);
        assertFloats(List.of(0f, 0f), info.rows.gutters, 0.01f);
    }

    @ParameterizedTest(name = "keeps collapsed auto-fit tracks in logical order for direction {0}")
    @EnumSource(Direction.class)
    void keepsCollapsedAutoFitTracksInLogicalOrder(Direction direction) {
        Style firstStyle = new Style();
        firstStyle.gridColumn = Line.of(GridPlacement.line(1), GridPlacement.line(2));
        Style secondStyle = new Style();
        secondStyle.gridColumn = Line.of(GridPlacement.line(3), GridPlacement.line(4));
        Style rootStyle = sizedStyle(300, 100);
        rootStyle.display = Display.Grid;
        rootStyle.direction = direction;
        rootStyle.setColumnGap(10);
        rootStyle.justifyContent = JustifyContent.SpaceBetween;
        rootStyle.gridTemplateColumns = List.of(GridTemplateComponent.repeat("auto-fit",
                List.of(track(50, 50))));

        Node root = new Node(rootStyle,
                List.of(new Node(firstStyle), new Node(secondStyle)));
        root.computeLayout(300, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        if (info == null) {
            throw new AssertionError("Expected detailed grid layout information");
        }
        assertEquals(5, info.columns.explicitTracks);
        assertFloats(List.of(50f, 0f, 50f, 0f, 0f), info.columns.sizes, 0.01f);
        if (direction == Direction.Ltr) {
            assertFloats(List.of(0f, 10f, 190f, 0f, 0f, 0f), info.columns.gutters, 0.01f);
            assertPositions(new float[][] { { 0, 50 }, { 60, 60 }, { 250, 300 }, { 300, 300 },
                    { 300, 300 } }, info.columns.positions);
        } else {
            assertFloats(List.of(0f, 200f, 0f, 0f, 0f, 0f), info.columns.gutters, 0.01f);
            assertPositions(new float[][] { { 250, 300 }, { 50, 50 }, { 0, 50 }, { 0, 0 },
                    { 0, 0 } }, info.columns.positions);
        }
        assertEquals(2, info.items.size());
        assertEquals(1, info.items.get(0).columnStart);
        assertEquals(3, info.items.get(1).columnStart);
    }

    @Test
    void preservesEmptyTrackArraysWhenTheGridHasOnlyAbsoluteChildren() {
        Style childStyle = new Style();
        childStyle.position = Position.Absolute;
        childStyle.size = Size.of(Dimension.length(10), Dimension.length(10));
        Style rootStyle = new Style();
        rootStyle.display = Display.Grid;
        Node root = new Node(rootStyle, List.of(new Node(childStyle)));
        root.computeLayout(100, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        if (info == null) {
            throw new AssertionError("Expected detailed grid layout information");
        }
        assertEquals(List.of(), info.columns.sizes);
        assertEquals(List.of(), info.columns.positions);
        assertFloats(List.of(0f), info.columns.gutters, 0.01f);
        assertEquals(List.of(), info.rows.sizes);
        assertEquals(List.of(), info.rows.positions);
        assertFloats(List.of(0f), info.rows.gutters, 0.01f);
        assertEquals(List.of(), info.items);
    }

    private static GridTemplateComponent single(Object min, Object max) {
        return GridTemplateComponent.single(track(min, max));
    }

    private static TrackSizingFunction track(Object min, Object max) {
        return TrackSizingFunction.of(min, max);
    }
}
