package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
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
        style.size(Size.of(Dimension.length(width), Dimension.length(height)));
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
        NodeImpl a = new NodeImpl(sized(20, 10));
        NodeImpl b = new NodeImpl(sized(20, 10));
        NodeImpl root = new NodeImpl(sized(100, 100), List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(a.getLayout(), 0, 0, 20, 10);
        assertLayout(b.getLayout(), 20, 0, 20, 10);
    }

    @Test
    void flexGrowDistributesFreeSpace() {
        Style child = sized(0, 20);
        child.flexGrow(1);
        NodeImpl a = new NodeImpl(child);
        NodeImpl b = new NodeImpl(child);
        NodeImpl root = new NodeImpl(sized(300, 100), List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 300, (float) 100);

        assertEquals(150, a.getLayout().width, 0.01f);
        assertEquals(150, b.getLayout().width, 0.01f);
        assertEquals(150, b.getLayout().x, 0.01f);
    }

    @Test
    void flexGrowWithFixedSiblingLeavesFixedSizeUntouched() {
        Style grow = sized(0, 20);
        grow.flexGrow(1);
        NodeImpl a = new NodeImpl(grow);
        NodeImpl b = new NodeImpl(sized(100, 20));
        NodeImpl root = new NodeImpl(sized(400, 100), List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 400, (float) 100);

        assertEquals(300, a.getLayout().width, 0.01f);
        assertEquals(100, b.getLayout().width, 0.01f);
        assertEquals(300, b.getLayout().x, 0.01f);
    }

    @Test
    void centerAlignmentPositionsChildInBothAxes() {
        Style rootStyle = sized(100, 100);
        rootStyle.layoutJustifyContent(JustifyContent.Center);
        rootStyle.layoutAlignItems(AlignItems.Center);
        NodeImpl child = new NodeImpl(sized(20, 20));
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(child.getLayout(), 40, 40, 20, 20);
    }

    @Test
    void flexShrinkHonoursShrinkZero() {
        Style first = sized(100, 20);
        first.flexShrink(0);
        NodeImpl a = new NodeImpl(first);
        NodeImpl b = new NodeImpl(sized(100, 20));
        NodeImpl root = new NodeImpl(sized(160, 100), List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 160, (float) 100);

        assertEquals(100, a.getLayout().width, 0.01f);
        assertEquals(60, b.getLayout().width, 0.01f);
    }

    @Test
    void rtlRowStartsAtTheRightEdge() {
        Style rootStyle = sized(100, 100);
        rootStyle.layoutDirection(Direction.Rtl);
        NodeImpl a = new NodeImpl(sized(20, 10));
        NodeImpl b = new NodeImpl(sized(20, 10));
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertEquals(80, a.getLayout().x, 0.01f);
        assertEquals(60, b.getLayout().x, 0.01f);
    }

    @Test
    void blockLayoutStacksChildrenAndCentersAutoMargins() {
        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        NodeImpl a = new NodeImpl(sized(100, 50));
        Style centered = sized(50, 20);
        centered.layoutMargin(new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.length(0)));
        NodeImpl b = new NodeImpl(centered);
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(a.getLayout(), 0, 0, 100, 50);
        assertLayout(b.getLayout(), 25, 50, 50, 20);
    }

    @Test
    void blockLayoutAppliesPaddingAndBorders() {
        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        rootStyle.layoutPadding(Rect.all(LengthPercentage.length(10)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        NodeImpl child = new NodeImpl(new Style());
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(child.getLayout(), 15, 15, 70, 0);
    }

    @Test
    void gridExplicitTracksAndGapAreHonoured() {
        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(10)));
        rootStyle.gridTemplateColumns(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40))));
        rootStyle.gridTemplateRows(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40))));
        NodeImpl a = new NodeImpl(new Style());
        NodeImpl b = new NodeImpl(new Style());
        NodeImpl c = new NodeImpl(new Style());
        NodeImpl d = new NodeImpl(new Style());
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b, c, d));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(a.getLayout(), 0, 0, 50, 50);
        assertLayout(b.getLayout(), 60, 0, 40, 50);
        assertLayout(c.getLayout(), 0, 60, 50, 40);
        assertLayout(d.getLayout(), 60, 60, 40, 40);
    }

    @Test
    void gridFractionalTracksShareRemainingSpace() {
        Style rootStyle = sized(300, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridTemplateColumns(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(
                        MinTrackSizing.auto(), MaxTrackSizing.flex(1))),
                GridTemplateComponent.single(TrackSizingFunction.of(
                        MinTrackSizing.auto(), MaxTrackSizing.flex(2)))));
        NodeImpl a = new NodeImpl(new Style());
        NodeImpl b = new NodeImpl(new Style());
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 300, (float) 100);

        assertEquals(100, a.getLayout().width, 0.01f);
        assertEquals(200, b.getLayout().width, 0.01f);
        assertEquals(100, b.getLayout().x, 0.01f);
    }

    @Test
    void detailedGridInfoReportsTracksGuttersAndPositions() {
        Style rootStyle = sized(200, 100);
        rootStyle.display(Display.Grid);
        rootStyle.layoutPadding(Rect.all(LengthPercentage.length(10)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(2)));
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(5)));
        rootStyle.gridTemplateColumns(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(70, 70))));
        rootStyle.gridTemplateRows(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(20, 20)),
                GridTemplateComponent.single(TrackSizingFunction.of(30, 30))));
        NodeImpl root = new NodeImpl(rootStyle, new NodeImpl(new Style()));

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);

        DetailedGridInfo info = root.getLayout().detailedGridInfo;
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
        rootStyle.position(Position.Relative);
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Style absolute = sized(10, 10);
        absolute.position(Position.Absolute);
        absolute.layoutInset(new Rect<>(LengthPercentageAuto.length(0), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.auto()));
        NodeImpl child = new NodeImpl(absolute);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(child.getLayout(), 5, 5, 10, 10);
    }

    @Test
    void staticAncestorsAreSkippedWhenResolvingAbsoluteContainingBlock() {
        Style rootStyle = sized(100, 100);
        rootStyle.position(Position.Relative);
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Style parentStyle = sized(50, 50);
        parentStyle.layoutMargin(new Rect<>(LengthPercentageAuto.length(20),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(30),
                LengthPercentageAuto.auto()));
        Style absolute = sized(10, 10);
        absolute.position(Position.Absolute);
        absolute.layoutInset(new Rect<>(LengthPercentageAuto.length(0), LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0), LengthPercentageAuto.auto()));
        NodeImpl child = new NodeImpl(absolute);
        NodeImpl parent = new NodeImpl(parentStyle, child);
        NodeImpl root = new NodeImpl(rootStyle, parent);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertLayout(child.getLayout(), 5, 5, 10, 10);
    }

    @Test
    void measureCallbackReceivesContentBoxAvailableSpace() {
        Style style = new Style();
        style.size(Size.of(Dimension.length(100), Dimension.auto()));
        style.layoutPadding(Rect.all(LengthPercentage.length(10)));
        style.layoutBorder(Rect.all(LengthPercentage.length(2)));
        NodeImpl root = new NodeImpl(style);

        float[] availableWidth = { -1 };
        root.setMeasure((known, available, measuredNode) -> {
            availableWidth[0] = available.width.value;
            return Size.of(known.width.isDefinite() ? known.width.value : 40f, 30f);
        });
        LayoutEngine.computeLayout(root, Size.of(AvailableSpace.definite(500),
                AvailableSpace.definite(500)));

        assertLayout(root.getLayout(), 0, 0, 100, 54);
        assertEquals(76, availableWidth[0], 0.01f);
    }

    @Test
    void styleKeyValueRoundTripAndGridAreaInference() {
        Style style = new Style();
        style.setWidth(200);
        style.setMarginLeft(10);
        style.setPaddingTop(5);
        style.setOverflowX(Overflow.Hidden);

        assertEquals(Dimension.length(200), style.size().width);
        assertEquals(LengthPercentageAuto.length(10), style.layoutMargin().left);
        assertEquals(LengthPercentage.length(5), style.layoutPadding().top);
        assertEquals(Overflow.Hidden, style.overflow().x);

        style.setGridTemplateAreaRowCount(1);
        style.setGridTemplateAreaColumnCount(1);
        style.setGridTemplateAreas(List.of(new GridTemplateArea("content", 1, 4, 1, 5)));
        assertEquals(3, style.getGridTemplateAreaRowCount());
        assertEquals(4, style.getGridTemplateAreaColumnCount());

        style.setGridTemplateAreas(List.of());
        assertEquals(1, style.getGridTemplateAreaRowCount());
        assertEquals(1, style.getGridTemplateAreaColumnCount());
    }

    @Test
    void emptyGridWithOnlyAbsoluteChildStillReportsDetailedInfo() {
        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        Style absolute = sized(10, 10);
        absolute.position(Position.Absolute);
        NodeImpl child = new NodeImpl(absolute);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        DetailedGridInfo info = root.getLayout().detailedGridInfo;
        assertNotNull(info);
        assertTrue(info.columns.sizes.isEmpty());
        assertTrue(info.rows.sizes.isEmpty());
        assertEquals(List.of(0f), info.columns.gutters);
        assertEquals(List.of(0f), info.rows.gutters);
        assertTrue(info.items.isEmpty());
        assertNull(child.getLayout().detailedGridInfo);
    }

    @Test
    void flexWrapCreatesMultipleLinesAndWrapReverseInvertsCrossOrder() {
        Style rootStyle = sized(100, 100);
        rootStyle.layoutFlexWrap(FlexWrap.Wrap);
        NodeImpl a = new NodeImpl(sized(60, 30));
        NodeImpl b = new NodeImpl(sized(60, 30));
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertEquals(0, a.getLayout().y, 0.01f);
        assertEquals(30, b.getLayout().y, 0.01f);

        Style reverseStyle = sized(100, 100);
        reverseStyle.layoutFlexWrap(FlexWrap.WrapReverse);
        NodeImpl ra = new NodeImpl(sized(60, 30));
        NodeImpl rb = new NodeImpl(sized(60, 30));
        NodeImpl reverseRoot = new NodeImpl(reverseStyle, List.of(ra, rb));
        LayoutEngine.computeLayout(reverseRoot, (float) 100, (float) 100);
        assertTrue(rb.getLayout().y < ra.getLayout().y);
    }

    @Test
    void flexSafeCenterDoesNotOverflowTheStartEdge() {
        Style rootStyle = sized(100, 100);
        rootStyle.layoutJustifyContent(JustifyContent.SafeCenter);
        Style childStyle = sized(150, 20);
        childStyle.flexShrink(0);
        NodeImpl child = new NodeImpl(childStyle);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertEquals(0, child.getLayout().x, 0.01f);
    }

    @Test
    void alignSelfSelfStartUsesChildWritingDirection() {
        Style rootStyle = sized(100, 20);
        rootStyle.layoutFlexDirection(FlexDirection.Column);
        Style childStyle = sized(20, 10);
        childStyle.layoutDirection(Direction.Rtl);
        childStyle.alignSelf(AlignSelf.SelfStart);
        NodeImpl child = new NodeImpl(childStyle);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 20);

        assertEquals(80, child.getLayout().x, 0.01f);
    }

    @Test
    void blockFloatAndClearAdvanceTheFlowPosition() {
        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.auto()));
        Style floated = sized(20, 30);
        floated.floatMode(Float.Left);
        Style cleared = sized(30, 10);
        cleared.clear(Clear.Both);
        NodeImpl first = new NodeImpl(floated);
        NodeImpl second = new NodeImpl(cleared);
        NodeImpl root = new NodeImpl(rootStyle, List.of(first, second));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        assertEquals(0, first.getLayout().y, 0.01f);
        assertEquals(30, second.getLayout().y, 0.01f);
        assertEquals(40, root.getLayout().height, 0.01f);
    }

    @Test
    void contentExtentsIncludeOverflowingChildren() {
        Style rootStyle = sized(100, 50);
        rootStyle.layoutPadding(Rect.all(LengthPercentage.length(10)));
        Style childStyle = sized(150, 80);
        childStyle.flexShrink(0);
        NodeImpl child = new NodeImpl(childStyle);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 50);

        assertEquals(160, root.getLayout().contentWidth, 0.01f);
        assertEquals(90, root.getLayout().contentHeight, 0.01f);
        assertEquals(0, child.getLayout().contentWidth, 0.01f);
    }

    @Test
    void gridAutoFlowColumnFillsColumnsFirst() {
        Style rootStyle = sized(200, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gridAutoFlow(GridAutoFlow.Column);
        rootStyle.gridTemplateRows(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50))));
        NodeImpl a = new NodeImpl(sized(20, 20));
        NodeImpl b = new NodeImpl(sized(20, 20));
        NodeImpl root = new NodeImpl(rootStyle, List.of(a, b));

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);

        assertEquals(0, a.getLayout().x, 0.01f);
        assertEquals(50, b.getLayout().y, 0.01f);
    }

    @Test
    void gridAreaCountsCreateEqualImplicitTracks() {
        Style rootStyle = sized(300, 200);
        rootStyle.display(Display.Grid);
        rootStyle.setGridTemplateAreaRowCount(2);
        rootStyle.setGridTemplateAreaColumnCount(3);
        List<NodeImpl> children = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            children.add(new NodeImpl(new Style()));
        }
        NodeImpl root = new NodeImpl(rootStyle, children);

        LayoutEngine.computeLayout(root, (float) 300, (float) 200);

        assertEquals(200, children.get(2).getLayout().x, 0.01f);
        assertEquals(100, children.get(3).getLayout().y, 0.01f);
    }

    @Test
    void absolutePositionUsesInitialContainingBlockWhenNoAncestorIsPositioned() {
        Style rootStyle = sized(100, 100);
        rootStyle.layoutMargin(new Rect<>(LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0), LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        Style absolute = sized(10, 10);
        absolute.position(Position.Absolute);
        absolute.layoutInset(new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(0)));
        NodeImpl child = new NodeImpl(absolute);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 300, (float) 200);

        assertLayout(root.getLayout(), 20, 30, 100, 100);
        assertLayout(child.getLayout(), 270, 160, 10, 10);
    }

    @Test
    void fixedPositionIgnoresPositionedAncestors() {
        Style rootStyle = sized(100, 100);
        rootStyle.position(Position.Relative);
        Style fixed = sized(10, 10);
        fixed.position(Position.Fixed);
        fixed.layoutInset(new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(), LengthPercentageAuto.length(0)));
        NodeImpl child = new NodeImpl(fixed);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 300, (float) 200);

        assertLayout(child.getLayout(), 290, 190, 10, 10);
    }

    @Test
    void printTreeIncludesLayoutAndBoxDetails() {
        Style rootStyle = sized(100, 50);
        rootStyle.layoutBorder(new Rect<>(LengthPercentage.length(1), LengthPercentage.length(2),
                LengthPercentage.length(1), LengthPercentage.length(2)));
        rootStyle.layoutPadding(new Rect<>(LengthPercentage.length(5), LengthPercentage.length(10),
                LengthPercentage.length(3), LengthPercentage.length(7)));
        NodeImpl root = new NodeImpl(rootStyle,
                List.of(new NodeImpl(new Style()), new NodeImpl(new Style())));

        LayoutEngine.computeLayout(root, (float) 100, (float) 50);
        String output = LayoutEngine.printTree(root);

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
        assertEquals(Display.Flex, style.display());
        assertEquals(Position.Static, style.position());
        assertEquals(FlexDirection.Row, style.layoutFlexDirection());
        assertEquals(FlexWrap.NoWrap, style.layoutFlexWrap());
        assertEquals(0f, style.flexGrow(), 0.01f);
        assertEquals(1f, style.flexShrink(), 0.01f);
        assertEquals(Dimension.auto(), style.size().width);
        assertEquals(Dimension.auto(), style.size().height);

        style.display(Display.Grid);
        style.layoutDirection(Direction.Rtl);
        style.floatMode(Float.Right);
        style.clear(Clear.Both);
        style.layoutAlignItems(AlignItems.Center);
        Style copy = style.copy();
        assertEquals(Display.Grid, copy.display());
        copy.display(Display.Flex);
        assertEquals(Display.Grid, style.display());
    }

    @Test
    void minAndMaxConstraintsSurviveStyleRoundTrips() {
        Style childStyle = sized(10, 100);
        childStyle.setMinWidth("50%");
        childStyle.setMaxHeight("50%");
        NodeImpl child = new NodeImpl(childStyle);
        Style rootStyle = sized(200, 100);
        rootStyle.display(Display.Block);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        assertLayout(child.getLayout(), 0, 0, 100, 50);

        Style constrained = child.getStyle().copy();
        constrained.setMinWidth(60);
        constrained.setMaxHeight(30);
        child.setStyle(constrained.copy());
        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        assertLayout(child.getLayout(), 0, 0, 60, 30);

        Style unconstrained = child.getStyle().copy();
        unconstrained.setMinWidth("auto");
        unconstrained.setMinHeight("auto");
        unconstrained.setMaxWidth("auto");
        unconstrained.setMaxHeight("auto");
        child.setStyle(unconstrained.copy());
        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        assertLayout(child.getLayout(), 0, 0, 10, 100);
    }

    @Test
    void percentageMinHeightAndMaxWidthAreResolved() {
        Style childStyle = sized(300, 1);
        childStyle.setMinHeight("20%");
        childStyle.setMaxWidth("75%");
        NodeImpl child = new NodeImpl(childStyle);
        Style rootStyle = sized(200, 100);
        rootStyle.display(Display.Block);
        NodeImpl root = new NodeImpl(rootStyle, child);

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        assertLayout(child.getLayout(), 0, 0, 150, 20);

        childStyle.setMinWidth("auto");
        childStyle.setMinHeight(30);
        childStyle.setMaxWidth(120);
        childStyle.setMaxHeight("auto");
        child.setStyle(childStyle.copy());
        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        assertLayout(child.getLayout(), 0, 0, 120, 30);
    }

    @Test
    void gridAlignmentIsReportedInGutters() {
        Style rootStyle = sized(300, 100);
        rootStyle.display(Display.Grid);
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(0)));
        rootStyle.layoutJustifyContent(JustifyContent.SpaceBetween);
        rootStyle.alignContent(AlignContent.Center);
        rootStyle.gridTemplateColumns(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50)),
                GridTemplateComponent.single(TrackSizingFunction.of(50, 50))));
        rootStyle.gridTemplateRows(List.of(
                GridTemplateComponent.single(TrackSizingFunction.of(40, 40))));
        NodeImpl root = new NodeImpl(rootStyle, new NodeImpl(new Style()));

        LayoutEngine.computeLayout(root, (float) 300, (float) 100);

        DetailedGridInfo info = root.getLayout().detailedGridInfo;
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
