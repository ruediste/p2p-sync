package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Exercises the native Java port against representative cases from the
 * TypeScript test-suite shipped with the Rust/WebAssembly bindings.
 */
class LayoutEnginePortTest {

    private static Style sized(float width, float height) {
        Style style = new Style();
        style.size = Size.of(Dimension.length(width), Dimension.length(height));
        return style;
    }

    private static void assertLayout(Layout layout, float x, float y, float width, float height) {
        assertEquals(x, layout.x, 0.01f, "x");
        assertEquals(y, layout.y, 0.01f, "y");
        assertEquals(width, layout.width, 0.01f, "width");
        assertEquals(height, layout.height, 0.01f, "height");
    }

    @Test
    void basicFlexRowPlacesChildrenAlongTheMainAxis() {
        Node a = new Node(sized(20, 10));
        Node b = new Node(sized(20, 10));
        Node root = new Node(sized(100, 100), List.of(a, b));

        root.computeLayout(100, 100);

        assertLayout(a.layout, 0, 0, 20, 10);
        assertLayout(b.layout, 20, 0, 20, 10);
    }

    @Test
    void flexGrowDistributesFreeSpace() {
        Style child = sized(0, 20);
        child.flexGrow = 1;
        Node a = new Node(child);
        Node b = new Node(child);
        Node root = new Node(sized(300, 100), List.of(a, b));

        root.computeLayout(300, 100);

        assertEquals(150, a.layout.width, 0.01f);
        assertEquals(150, b.layout.width, 0.01f);
        assertEquals(150, b.layout.x, 0.01f);
    }

    @Test
    void flexGrowWithFixedSiblingLeavesFixedSizeUntouched() {
        Style grow = sized(0, 20);
        grow.flexGrow = 1;
        Node a = new Node(grow);
        Node b = new Node(sized(100, 20));
        Node root = new Node(sized(400, 100), List.of(a, b));

        root.computeLayout(400, 100);

        assertEquals(300, a.layout.width, 0.01f);
        assertEquals(100, b.layout.width, 0.01f);
        assertEquals(300, b.layout.x, 0.01f);
    }

    @Test
    void centerAlignmentPositionsChildInBothAxes() {
        Style rootStyle = sized(100, 100);
        rootStyle.justifyContent = JustifyContent.Center;
        rootStyle.alignItems = AlignItems.Center;
        Node child = new Node(sized(20, 20));
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 100);

        assertLayout(child.layout, 40, 40, 20, 20);
    }

    @Test
    void flexShrinkHonoursShrinkZero() {
        Style first = sized(100, 20);
        first.flexShrink = 0;
        Node a = new Node(first);
        Node b = new Node(sized(100, 20));
        Node root = new Node(sized(160, 100), List.of(a, b));

        root.computeLayout(160, 100);

        assertEquals(100, a.layout.width, 0.01f);
        assertEquals(60, b.layout.width, 0.01f);
    }

    @Test
    void rtlRowStartsAtTheRightEdge() {
        Style rootStyle = sized(100, 100);
        rootStyle.direction = Direction.Rtl;
        Node a = new Node(sized(20, 10));
        Node b = new Node(sized(20, 10));
        Node root = new Node(rootStyle, List.of(a, b));

        root.computeLayout(100, 100);

        assertEquals(80, a.layout.x, 0.01f);
        assertEquals(60, b.layout.x, 0.01f);
    }

    @Test
    void blockLayoutStacksChildrenAndCentersAutoMargins() {
        Style rootStyle = sized(100, 100);
        rootStyle.display = Display.Block;
        Node a = new Node(sized(100, 50));
        Style centered = sized(50, 20);
        centered.margin = new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.length(0));
        Node b = new Node(centered);
        Node root = new Node(rootStyle, List.of(a, b));

        root.computeLayout(100, 100);

        assertLayout(a.layout, 0, 0, 100, 50);
        assertLayout(b.layout, 25, 50, 50, 20);
    }

    @Test
    void blockLayoutAppliesPaddingAndBorders() {
        Style rootStyle = sized(100, 100);
        rootStyle.display = Display.Block;
        rootStyle.padding = Rect.all(LengthPercentage.length(10));
        rootStyle.border = Rect.all(LengthPercentage.length(5));
        Node child = new Node(new Style());
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 100);

        assertLayout(child.layout, 15, 15, 70, 0);
    }

    @Test
    void gridExplicitTracksAndGapAreHonoured() {
        Style rootStyle = sized(100, 100);
        rootStyle.display = Display.Grid;
        rootStyle.gap = Size.of(LengthPercentage.length(10), LengthPercentage.length(10));
        rootStyle.gridTemplateColumns = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40)));
        rootStyle.gridTemplateRows = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40)));
        Node a = new Node(new Style());
        Node b = new Node(new Style());
        Node c = new Node(new Style());
        Node d = new Node(new Style());
        Node root = new Node(rootStyle, List.of(a, b, c, d));

        root.computeLayout(100, 100);

        assertLayout(a.layout, 0, 0, 50, 50);
        assertLayout(b.layout, 60, 0, 40, 50);
        assertLayout(c.layout, 0, 60, 50, 40);
        assertLayout(d.layout, 60, 60, 40, 40);
    }

    @Test
    void gridFractionalTracksShareRemainingSpace() {
        Style rootStyle = sized(300, 100);
        rootStyle.display = Display.Grid;
        rootStyle.gridTemplateColumns = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(
                        MinTrackSizing.auto(), MaxTrackSizing.flex(1))),
                GridTemplateComponent.single(TrackSizingFunction.of(
                        MinTrackSizing.auto(), MaxTrackSizing.flex(2))));
        Node a = new Node(new Style());
        Node b = new Node(new Style());
        Node root = new Node(rootStyle, List.of(a, b));

        root.computeLayout(300, 100);

        assertEquals(100, a.layout.width, 0.01f);
        assertEquals(200, b.layout.width, 0.01f);
        assertEquals(100, b.layout.x, 0.01f);
    }

    @Test
    void detailedGridInfoReportsTracksGuttersAndPositions() {
        Style rootStyle = sized(200, 100);
        rootStyle.display = Display.Grid;
        rootStyle.padding = Rect.all(LengthPercentage.length(10));
        rootStyle.border = Rect.all(LengthPercentage.length(2));
        rootStyle.gap = Size.of(LengthPercentage.length(10), LengthPercentage.length(5));
        rootStyle.gridTemplateColumns = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(70, 70)));
        rootStyle.gridTemplateRows = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(20, 20)),
                GridTemplateComponent.single(TrackSizingFunction.of(30, 30)));
        Node root = new Node(rootStyle, new Node(new Style()));

        root.computeLayout(200, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        assertNotNull(info);
        assertEquals(2, info.columns.explicitTracks);
        assertEquals(List.of(50f, 70f), info.columns.sizes);
        assertEquals(List.of(0f, 10f, 0f), info.columns.gutters);
        assertEquals(12, info.columns.positions.get(0).start, 0.01f);
        assertEquals(62, info.columns.positions.get(0).end, 0.01f);
        assertEquals(72, info.columns.positions.get(1).start, 0.01f);
        assertEquals(142, info.columns.positions.get(1).end, 0.01f);
        assertEquals(List.of(0f, 5f, 0f), info.rows.gutters);
        assertEquals(1, info.items.size());
        assertEquals(1, info.items.get(0).rowStart);
        assertEquals(2, info.items.get(0).rowEnd);
        assertEquals(1, info.items.get(0).columnStart);
        assertEquals(2, info.items.get(0).columnEnd);
    }

    @Test
    void absolutePositionUsesNearestPositionedAncestorBorderBox() {
        Style rootStyle = sized(100, 100);
        rootStyle.position = Position.Relative;
        rootStyle.border = Rect.all(LengthPercentage.length(5));
        Style absolute = sized(10, 10);
        absolute.position = Position.Absolute;
        absolute.inset = new Rect<>(LengthPercentageAuto.length(0), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.auto());
        Node child = new Node(absolute);
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 100);

        assertLayout(child.layout, 5, 5, 10, 10);
    }

    @Test
    void staticAncestorsAreSkippedWhenResolvingAbsoluteContainingBlock() {
        Style rootStyle = sized(100, 100);
        rootStyle.position = Position.Relative;
        rootStyle.border = Rect.all(LengthPercentage.length(5));
        Style parentStyle = sized(50, 50);
        parentStyle.margin = new Rect<>(LengthPercentageAuto.length(20),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(30),
                LengthPercentageAuto.auto());
        Style absolute = sized(10, 10);
        absolute.position = Position.Absolute;
        absolute.inset = new Rect<>(LengthPercentageAuto.length(0), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.auto());
        Node child = new Node(absolute);
        Node parent = new Node(parentStyle, child);
        Node root = new Node(rootStyle, parent);

        root.computeLayout(100, 100);

        assertLayout(child.layout, 5, 5, 10, 10);
    }

    @Test
    void measureCallbackReceivesContentBoxAvailableSpace() {
        Style style = new Style();
        style.size = Size.of(Dimension.length(100), Dimension.auto());
        style.padding = Rect.all(LengthPercentage.length(10));
        style.border = Rect.all(LengthPercentage.length(2));
        Node root = new Node(style);

        float[] availableWidth = { -1 };
        root.measure = (known, available, measuredNode) -> {
            availableWidth[0] = available.width.value;
            return Size.of(known.width.isDefinite() ? known.width.value : 40f, 30f);
        };
        root.computeLayout(Size.of(AvailableSpace.definite(500),
                AvailableSpace.definite(500)));

        assertLayout(root.layout, 0, 0, 100, 54);
        assertEquals(76, availableWidth[0], 0.01f);
    }

    @Test
    void styleKeyValueRoundTripAndGridAreaInference() {
        Style style = new Style();
        style.set("width", 200);
        style.set("marginLeft", 10);
        style.set("paddingTop", 5);
        style.set("overflowX", Overflow.Hidden);

        assertEquals(Dimension.length(200), style.get("width"));
        assertEquals(LengthPercentageAuto.length(10), style.get("marginLeft"));
        assertEquals(LengthPercentage.length(5), style.get("paddingTop"));
        assertEquals(Overflow.Hidden, style.get("overflowX"));

        style.set("gridTemplateAreaRowCount", 1);
        style.set("gridTemplateAreaColumnCount", 1);
        style.setGridTemplateAreas(List.of(new GridTemplateArea("content", 1, 4, 1, 5)));
        assertEquals(3, style.get("gridTemplateAreaRowCount"));
        assertEquals(4, style.get("gridTemplateAreaColumnCount"));

        style.setGridTemplateAreas(List.of());
        assertEquals(1, style.get("gridTemplateAreaRowCount"));
        assertEquals(1, style.get("gridTemplateAreaColumnCount"));
    }

    @Test
    void emptyGridWithOnlyAbsoluteChildStillReportsDetailedInfo() {
        Style rootStyle = new Style();
        rootStyle.display = Display.Grid;
        Style absolute = sized(10, 10);
        absolute.position = Position.Absolute;
        Node child = new Node(absolute);
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        assertNotNull(info);
        assertTrue(info.columns.sizes.isEmpty());
        assertTrue(info.rows.sizes.isEmpty());
        assertEquals(List.of(0f), info.columns.gutters);
        assertEquals(List.of(0f), info.rows.gutters);
        assertTrue(info.items.isEmpty());
        assertNull(child.detailedGridInfo);
    }

    @Test
    void flexWrapCreatesMultipleLinesAndWrapReverseInvertsCrossOrder() {
        Style rootStyle = sized(100, 100);
        rootStyle.flexWrap = FlexWrap.Wrap;
        Node a = new Node(sized(60, 30));
        Node b = new Node(sized(60, 30));
        Node root = new Node(rootStyle, List.of(a, b));

        root.computeLayout(100, 100);

        assertEquals(0, a.layout.y, 0.01f);
        assertEquals(30, b.layout.y, 0.01f);

        Style reverseStyle = sized(100, 100);
        reverseStyle.flexWrap = FlexWrap.WrapReverse;
        Node ra = new Node(sized(60, 30));
        Node rb = new Node(sized(60, 30));
        Node reverseRoot = new Node(reverseStyle, List.of(ra, rb));
        reverseRoot.computeLayout(100, 100);
        assertTrue(rb.layout.y < ra.layout.y);
    }

    @Test
    void flexSafeCenterDoesNotOverflowTheStartEdge() {
        Style rootStyle = sized(100, 100);
        rootStyle.justifyContent = JustifyContent.SafeCenter;
        Style childStyle = sized(150, 20);
        childStyle.flexShrink = 0;
        Node child = new Node(childStyle);
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 100);

        assertEquals(0, child.layout.x, 0.01f);
    }

    @Test
    void alignSelfSelfStartUsesChildWritingDirection() {
        Style rootStyle = sized(100, 20);
        rootStyle.flexDirection = FlexDirection.Column;
        Style childStyle = sized(20, 10);
        childStyle.direction = Direction.Rtl;
        childStyle.alignSelf = AlignSelf.SelfStart;
        Node child = new Node(childStyle);
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 20);

        assertEquals(80, child.layout.x, 0.01f);
    }

    @Test
    void blockFloatAndClearAdvanceTheFlowPosition() {
        Style rootStyle = new Style();
        rootStyle.display = Display.Block;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.auto());
        Style floated = sized(20, 30);
        floated.floatMode = Float.Left;
        Style cleared = sized(30, 10);
        cleared.clear = Clear.Both;
        Node first = new Node(floated);
        Node second = new Node(cleared);
        Node root = new Node(rootStyle, List.of(first, second));

        root.computeLayout(100, 100);

        assertEquals(0, first.layout.y, 0.01f);
        assertEquals(30, second.layout.y, 0.01f);
        assertEquals(40, root.layout.height, 0.01f);
    }

    @Test
    void contentExtentsIncludeOverflowingChildren() {
        Style rootStyle = sized(100, 50);
        rootStyle.padding = Rect.all(LengthPercentage.length(10));
        Style childStyle = sized(150, 80);
        childStyle.flexShrink = 0;
        Node child = new Node(childStyle);
        Node root = new Node(rootStyle, child);

        root.computeLayout(100, 50);

        assertEquals(160, root.layout.contentWidth, 0.01f);
        assertEquals(90, root.layout.contentHeight, 0.01f);
        assertEquals(0, child.layout.contentWidth, 0.01f);
    }

    @Test
    void gridAutoFlowColumnFillsColumnsFirst() {
        Style rootStyle = sized(200, 100);
        rootStyle.display = Display.Grid;
        rootStyle.gridAutoFlow = GridAutoFlow.Column;
        rootStyle.gridTemplateRows = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)));
        Node a = new Node(sized(20, 20));
        Node b = new Node(sized(20, 20));
        Node root = new Node(rootStyle, List.of(a, b));

        root.computeLayout(200, 100);

        assertEquals(0, a.layout.x, 0.01f);
        assertEquals(50, b.layout.y, 0.01f);
    }

    @Test
    void gridAreaCountsCreateEqualImplicitTracks() {
        Style rootStyle = sized(300, 200);
        rootStyle.display = Display.Grid;
        rootStyle.setGridTemplateAreaRowCount(2);
        rootStyle.setGridTemplateAreaColumnCount(3);
        List<Node> children = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            children.add(new Node(new Style()));
        }
        Node root = new Node(rootStyle, children);

        root.computeLayout(300, 200);

        assertEquals(200, children.get(2).layout.x, 0.01f);
        assertEquals(100, children.get(3).layout.y, 0.01f);
    }

    @Test
    void absolutePositionUsesInitialContainingBlockWhenNoAncestorIsPositioned() {
        Style rootStyle = sized(100, 100);
        rootStyle.margin = new Rect<>(LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0), LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0));
        Style absolute = sized(10, 10);
        absolute.position = Position.Absolute;
        absolute.inset = new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(0));
        Node child = new Node(absolute);
        Node root = new Node(rootStyle, child);

        root.computeLayout(300, 200);

        assertLayout(root.layout, 20, 30, 100, 100);
        assertLayout(child.layout, 270, 160, 10, 10);
    }

    @Test
    void fixedPositionIgnoresPositionedAncestors() {
        Style rootStyle = sized(100, 100);
        rootStyle.position = Position.Relative;
        Style fixed = sized(10, 10);
        fixed.position = Position.Fixed;
        fixed.inset = new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(0));
        Node child = new Node(fixed);
        Node root = new Node(rootStyle, child);

        root.computeLayout(300, 200);

        assertLayout(child.layout, 290, 190, 10, 10);
    }

    @Test
    void printTreeIncludesLayoutAndBoxDetails() {
        Style rootStyle = sized(100, 50);
        rootStyle.border = new Rect<>(LengthPercentage.length(1), LengthPercentage.length(2),
                LengthPercentage.length(1), LengthPercentage.length(2));
        rootStyle.padding = new Rect<>(LengthPercentage.length(5), LengthPercentage.length(10),
                LengthPercentage.length(3), LengthPercentage.length(7));
        Node root = new Node(rootStyle,
                List.of(new Node(new Style()), new Node(new Style())));

        root.computeLayout(100, 50);
        String output = root.printTree();

        assertTrue(output.contains("└──"));
        assertTrue(output.contains("x: 0"));
        assertTrue(output.contains("w: 100"));
        assertTrue(output.contains("content_w:"));
        assertTrue(output.contains("border: l:1 r:2 t:1 b:2"));
        assertTrue(output.contains("padding: l:5 r:10 t:3 b:7"));
        assertEquals(3, output.lines().filter(line -> line.contains(" [x:")).count());
    }

    @Test
    void styleDefaultsCopyAndInvalidEnumValues() {
        Style style = new Style();
        assertEquals(Display.Flex, style.display);
        assertEquals(Position.Static, style.position);
        assertEquals(FlexDirection.Row, style.flexDirection);
        assertEquals(FlexWrap.NoWrap, style.flexWrap);
        assertEquals(0f, style.flexGrow, 0.01f);
        assertEquals(1f, style.flexShrink, 0.01f);
        assertEquals(Dimension.auto(), style.size.width);
        assertEquals(Dimension.auto(), style.size.height);

        style.set("display", Display.Grid);
        style.set("direction", Direction.Rtl);
        style.set("float", Float.Right);
        style.set("clear", Clear.Both);
        style.set("alignItems", AlignItems.Center);
        Style copy = style.copy();
        assertEquals(Display.Grid, copy.display);
        copy.display = Display.Flex;
        assertEquals(Display.Grid, style.display);

        style.set("display", 255);
        assertEquals(Display.Grid, style.display);
    }

    @Test
    void minAndMaxConstraintsSurviveStyleRoundTrips() {
        Style childStyle = sized(10, 100);
        childStyle.minSize = Size.of(Dimension.percent(50), Dimension.auto());
        childStyle.maxSize = Size.of(Dimension.auto(), Dimension.percent(50));
        Node child = new Node(childStyle);
        Style rootStyle = sized(200, 100);
        rootStyle.display = Display.Block;
        Node root = new Node(rootStyle, child);

        root.computeLayout(200, 100);
        assertLayout(child.layout, 0, 0, 100, 50);

        Style constrained = child.style.copy();
        constrained.minSize = Size.of(Dimension.length(60), Dimension.auto());
        constrained.maxSize = Size.of(Dimension.auto(), Dimension.length(30));
        child.style = constrained.copy();
        root.computeLayout(200, 100);
        assertLayout(child.layout, 0, 0, 60, 30);

        Style unconstrained = child.style.copy();
        unconstrained.minSize = Size.of(Dimension.auto(), Dimension.auto());
        unconstrained.maxSize = Size.of(Dimension.auto(), Dimension.auto());
        child.style = unconstrained.copy();
        root.computeLayout(200, 100);
        assertLayout(child.layout, 0, 0, 10, 100);
    }

    @Test
    void percentageMinHeightAndMaxWidthAreResolved() {
        Style childStyle = sized(300, 1);
        childStyle.set("minHeight", "20%");
        childStyle.set("maxWidth", "75%");
        Node child = new Node(childStyle);
        Style rootStyle = sized(200, 100);
        rootStyle.display = Display.Block;
        Node root = new Node(rootStyle, child);

        root.computeLayout(200, 100);
        assertLayout(child.layout, 0, 0, 150, 20);

        childStyle.minSize = Size.of(Dimension.auto(), Dimension.length(30));
        childStyle.maxSize = Size.of(Dimension.length(120), Dimension.auto());
        child.style = childStyle.copy();
        root.computeLayout(200, 100);
        assertLayout(child.layout, 0, 0, 120, 30);
    }

    @Test
    void gridAlignmentIsReportedInGutters() {
        Style rootStyle = sized(300, 100);
        rootStyle.display = Display.Grid;
        rootStyle.gap = Size.of(LengthPercentage.length(10), LengthPercentage.length(0));
        rootStyle.justifyContent = JustifyContent.SpaceBetween;
        rootStyle.alignContent = AlignContent.Center;
        rootStyle.gridTemplateColumns = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)));
        rootStyle.gridTemplateRows = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40)));
        Node root = new Node(rootStyle, new Node(new Style()));

        root.computeLayout(300, 100);

        DetailedGridInfo info = root.detailedGridInfo;
        assertNotNull(info);
        assertEquals(List.of(50f, 50f), info.columns.sizes);
        assertEquals(List.of(0f, 200f, 0f), info.columns.gutters);
        assertEquals(0, info.columns.positions.get(0).start, 0.01f);
        assertEquals(50, info.columns.positions.get(0).end, 0.01f);
        assertEquals(250, info.columns.positions.get(1).start, 0.01f);
        assertEquals(300, info.columns.positions.get(1).end, 0.01f);
        assertEquals(30, info.rows.positions.get(0).start, 0.01f);
        assertEquals(70, info.rows.positions.get(0).end, 0.01f);
        assertEquals(List.of(0f, 0f), info.rows.gutters);
    }
}
