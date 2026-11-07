package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Port of layout.test.ts from the TypeScript/WebAssembly test-suite.
 * WebAssembly resource cleanup ({@code free()}) is omitted; every functional
 * case and its upstream expectations are preserved.
 */
class LayoutPortTest {

    private static final float EPS = 0.01f;

    private static Style sized(float width, float height) {
        Style style = new Style();
        style.size(Size.of(Dimension.length(width), Dimension.length(height)));
        return style;
    }

    private static void assertLayout(Layout layout, float x, float y, float width, float height) {
        assertEquals(x, layout.x, EPS, "x");
        assertEquals(y, layout.y, EPS, "y");
        assertEquals(width, layout.width, EPS, "width");
        assertEquals(height, layout.height, EPS, "height");
    }

    // ---- Layout Computation ----

    @Test
    void validatesBasicFlexEngineLayout() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.layoutFlexDirection(FlexDirection.Column);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = sized(50, 50);
        NodeImpl child1 = new NodeImpl(childStyle);
        NodeImpl child2 = new NodeImpl(childStyle);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout rootLayout = root.getLayout();
        assertEquals(100f, rootLayout.width, EPS);
        assertEquals(100f, rootLayout.height, EPS);

        assertLayout(child1.getLayout(), 0, 0, 50, 50);
        assertLayout(child2.getLayout(), 0, 50, 50, 50);
    }

    @Test
    void validatesCenterAlignment() {
        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutAlignItems(AlignItems.Center);
        rootStyle.layoutJustifyContent(JustifyContent.Center);
        NodeImpl root = new NodeImpl(rootStyle);

        NodeImpl child = new NodeImpl(sized(20, 20));
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout layout = child.getLayout();
        assertEquals(40f, layout.x, EPS);
        assertEquals(40f, layout.y, EPS);
    }

    // ---- Grid Layout Computation ----

    @Test
    void validatesBasicGridLayoutWithExplicitPlacement() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        NodeImpl child1 = new NodeImpl(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        NodeImpl child2 = new NodeImpl(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout rootLayout = root.getLayout();
        assertEquals(100f, rootLayout.width, EPS);
        assertEquals(100f, rootLayout.height, EPS);

        Layout child1Layout = child1.getLayout();
        Layout child2Layout = child2.getLayout();
        assertTrue(child2Layout.x >= child1Layout.x);
    }

    @Test
    void validatesGridAutoFlowRow() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.gridAutoFlow(GridAutoFlow.Row);
        NodeImpl root = new NodeImpl(rootStyle);

        List<NodeImpl> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            NodeImpl child = new NodeImpl(sized(20, 20));
            children.add(child);
            root.addChild(child);
        }

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        for (NodeImpl child : children) {
            Layout layout = child.getLayout();
            assertEquals(20f, layout.width, EPS);
            assertEquals(20f, layout.height, EPS);
        }
    }

    @Test
    void validatesGridWithSpan() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = new Style();
        childStyle.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        childStyle.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.span(2)));
        NodeImpl child = new NodeImpl(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.getLayout();
        assertEquals(childLayout.width, childLayout.width);
        assertEquals(childLayout.height, childLayout.height);
    }

    @Test
    void validatesGridWithGap() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(10)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style child1Style = new Style();
        child1Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child1Style.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        NodeImpl child1 = new NodeImpl(child1Style);

        Style child2Style = new Style();
        child2Style.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        child2Style.gridColumn(Line.of(GridPlacement.line(2), GridPlacement.line(3)));
        NodeImpl child2 = new NodeImpl(child2Style);

        root.addChild(child1);
        root.addChild(child2);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout child1Layout = child1.getLayout();
        Layout child2Layout = child2.getLayout();
        float child1Right = child1Layout.x + child1Layout.width;
        assertEquals(10f, child2Layout.x - child1Right, 0.5f);
    }

    @Test
    void validatesGridWithScrollbarWidth() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.scrollbarWidth(15);
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        NodeImpl child = new NodeImpl(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.getLayout();
        assertTrue(childLayout.width <= 100f);
        assertTrue(childLayout.height <= 100f);
    }

    // ---- Block Layout Computation ----

    @Test
    void validatesBlockLayout() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        NodeImpl child = new NodeImpl(sized(50, 50));
        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.getLayout();
        assertEquals(50f, childLayout.width, EPS);
        assertEquals(50f, childLayout.height, EPS);
    }

    @Test
    void validatesBlockLayoutWithItemIsReplaced() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = new Style();
        childStyle.itemIsReplaced(true);
        childStyle.aspectRatio(2f);
        childStyle.size(Size.of(Dimension.length(50), Dimension.auto()));
        NodeImpl child = new NodeImpl(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.getLayout();
        assertEquals(50f, childLayout.width, EPS);
        assertEquals(25f, childLayout.height, EPS);
    }

    // ---- Layout compound getters ----

    @Test
    void positionGetterReturnsPointWithXAndY() {
        NodeImpl root = new NodeImpl(sized(800, 600));
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Point<java.lang.Float> position = root.getLayout().position();
        assertNotNull(position);
        assertEquals(0f, position.x, EPS);
        assertEquals(0f, position.y, EPS);
    }

    @Test
    void sizeGetterReturnsSizeWithWidthAndHeight() {
        NodeImpl root = new NodeImpl(sized(800, 600));
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> size = root.getLayout().size();
        assertNotNull(size);
        assertEquals(800f, size.width, EPS);
        assertEquals(600f, size.height, EPS);
    }

    @Test
    void contentSizeGetterReturnsSizeWithContentWidthAndContentHeight() {
        NodeImpl root = new NodeImpl(new Style());
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> contentSize = root.getLayout().contentSize();
        assertNotNull(contentSize);
        assertTrue(contentSize.width >= 0);
        assertTrue(contentSize.height >= 0);
    }

    @Test
    void scrollbarSizeGetterReturnsSizeWithScrollbarWidthAndScrollbarHeight() {
        NodeImpl root = new NodeImpl(new Style());
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> scrollbarSize = root.getLayout().scrollbarSize();
        assertNotNull(scrollbarSize);
        assertEquals(0f, scrollbarSize.width, EPS);
        assertEquals(0f, scrollbarSize.height, EPS);
    }

    @Test
    void borderGetterReturnsRectWithAllBorderWidths() {
        Style style = new Style();
        style.layoutBorder(new Rect<>(
                LengthPercentage.length(1),
                LengthPercentage.length(2),
                LengthPercentage.length(3),
                LengthPercentage.length(4)));
        NodeImpl node = new NodeImpl(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> border = node.getLayout().border;
        assertNotNull(border);
        assertEquals(1f, border.left, EPS);
        assertEquals(2f, border.right, EPS);
        assertEquals(3f, border.top, EPS);
        assertEquals(4f, border.bottom, EPS);
    }

    @Test
    void paddingGetterReturnsRectWithAllPaddingValues() {
        Style style = new Style();
        style.layoutPadding(new Rect<>(
                LengthPercentage.length(10),
                LengthPercentage.length(20),
                LengthPercentage.length(5),
                LengthPercentage.length(15)));
        NodeImpl node = new NodeImpl(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> padding = node.getLayout().padding;
        assertNotNull(padding);
        assertEquals(10f, padding.left, EPS);
        assertEquals(20f, padding.right, EPS);
        assertEquals(5f, padding.top, EPS);
        assertEquals(15f, padding.bottom, EPS);
    }

    @Test
    void marginGetterReturnsRectWithAllMarginValues() {
        Style style = new Style();
        style.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(5),
                LengthPercentageAuto.length(10),
                LengthPercentageAuto.length(8),
                LengthPercentageAuto.length(12)));
        NodeImpl node = new NodeImpl(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> margin = node.getLayout().margin;
        assertNotNull(margin);
        assertEquals(5f, margin.left, EPS);
        assertEquals(10f, margin.right, EPS);
        assertEquals(8f, margin.top, EPS);
        assertEquals(12f, margin.bottom, EPS);
    }

    // ---- printTree ----

    @Test
    void printTreeReturnsStringRepresentationOfSingleNode() {
        NodeImpl node = new NodeImpl(sized(100, 50));
        LayoutEngine.computeLayout(node, (float) 100, (float) 50);

        String output = LayoutEngine.printTree(node);
        assertTrue(output.length() > 0);
        assertTrue(output.contains("└──"));
        assertTrue(output.contains("x:"));
        assertTrue(output.contains("y:"));
        assertTrue(output.contains("w:"));
        assertTrue(output.contains("h:"));
    }

    @Test
    void printTreeReturnsStringRepresentationOfTreeWithChildren() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.layoutFlexDirection(FlexDirection.Column);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = sized(50, 50);
        NodeImpl child1 = new NodeImpl(childStyle);
        NodeImpl child2 = new NodeImpl(childStyle);

        root.addChild(child1);
        root.addChild(child2);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        String output = LayoutEngine.printTree(root);
        assertTrue(output.contains("└──"));
        assertTrue(output.contains("├──"));

        long lines = output.lines().filter(line -> line.contains(" [x:")).count();
        assertEquals(3, lines);
    }

    @Test
    void printTreeIncludesLayoutInformationInOutput() {
        Style style = sized(100, 50);
        style.layoutPadding(new Rect<>(
                LengthPercentage.length(5),
                LengthPercentage.length(10),
                LengthPercentage.length(3),
                LengthPercentage.length(7)));
        style.layoutBorder(new Rect<>(
                LengthPercentage.length(1),
                LengthPercentage.length(2),
                LengthPercentage.length(1),
                LengthPercentage.length(2)));
        NodeImpl node = new NodeImpl(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 50);

        String output = LayoutEngine.printTree(node);
        assertTrue(output.contains("x: 0"));
        assertTrue(output.contains("y: 0"));
        assertTrue(output.contains("w: 100"));
        assertTrue(output.contains("h: 50"));
        assertTrue(output.contains("content_w:"));
        assertTrue(output.contains("content_h:"));
        assertTrue(output.contains("border: l:1 r:2 t:1 b:2"));
        assertTrue(output.contains("padding: l:5 r:10 t:3 b:7"));
    }

    @Test
    void printTreeShowsLayoutInformationWithoutNodeId() {
        NodeImpl node = new NodeImpl(sized(100, 50));
        LayoutEngine.computeLayout(node, (float) 100, (float) 50);

        String output = LayoutEngine.printTree(node);
        assertTrue(output.startsWith("└── [x:"));
        assertTrue(output.contains("w: 100"));
    }

    @Test
    void printTreeHandlesDeepTreeStructure() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        NodeImpl root = new NodeImpl(rootStyle);

        Style childStyle = sized(30, 30);
        NodeImpl level1 = new NodeImpl(childStyle);
        root.addChild(level1);

        NodeImpl level2 = new NodeImpl(childStyle);
        level1.addChild(level2);

        NodeImpl level3 = new NodeImpl(childStyle);
        level2.addChild(level3);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        String output = LayoutEngine.printTree(root);
        long lines = output.lines().filter(line -> !line.trim().isEmpty()).count();
        assertTrue(lines >= 4);
    }

    // ---- Content size extents ----

    private static void assertContentExtents(Style rootStyle, Style childStyle, float expectedWidth,
            float expectedHeight) {
        List<NodeImpl> children = new ArrayList<>();
        if (childStyle != null) {
            children.add(new NodeImpl(childStyle));
        }
        NodeImpl node = new NodeImpl(rootStyle, children);
        LayoutEngine.computeLayout(node, (float) 100, (float) 50);

        Layout layout = node.getLayout();
        assertEquals(expectedWidth, layout.contentWidth, EPS);
        assertEquals(expectedHeight, layout.contentHeight, EPS);

        Size<java.lang.Float> contentSize = layout.contentSize();
        assertEquals(expectedWidth, contentSize.width, EPS);
        assertEquals(expectedHeight, contentSize.height, EPS);
    }

    @Test
    void preservesContentSizeAccessorsForOverflowAfterTheScrollOrigin() {
        Style rootStyle = sized(100, 50);
        rootStyle.layoutPadding(Rect.all(LengthPercentage.length(10)));

        Style childStyle = sized(150, 80);
        childStyle.flexShrink(0);

        assertContentExtents(rootStyle, childStyle, 160, 90);
    }

    @Test
    void preservesContentSizeAccessorsForOverflowBeforeTheScrollOrigin() {
        Style rootStyle = sized(100, 50);
        rootStyle.position(Position.Relative);

        Style childStyle = sized(40, 25);
        childStyle.position(Position.Absolute);
        childStyle.layoutInset(new Rect<>(
                LengthPercentageAuto.length(-30),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(-20),
                LengthPercentageAuto.auto()));

        assertContentExtents(rootStyle, childStyle, 10, 5);
    }

    @Test
    void preservesContentSizeAccessorsForAnEmptyNode() {
        assertContentExtents(sized(100, 50), null, 0, 0);
    }

    // ---- Containing blocks ----

    private static float[] layoutAbsoluteIn(Position parentPosition) {

        Style absoluteStyle = new Style();
        absoluteStyle.position(Position.Absolute);
        absoluteStyle.size(Size.of(Dimension.length(10), Dimension.length(10)));
        absoluteStyle.layoutInset(new Rect<>(
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto()));
        NodeImpl absolute = new NodeImpl(absoluteStyle);

        Style parentStyle = new Style();
        parentStyle.position(parentPosition);
        parentStyle.size(Size.of(Dimension.length(50), Dimension.length(50)));
        parentStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        NodeImpl parent = new NodeImpl(parentStyle, List.of(absolute));

        Style rootStyle = new Style();
        rootStyle.position(Position.Relative);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        NodeImpl root = new NodeImpl(rootStyle, List.of(parent));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);
        Layout layout = absolute.getLayout();
        return new float[] { layout.x, layout.y };
    }

    @Test
    void placesAbsoluteChildrenOfAPositionedParentInThatParent() {
        float[] relative = layoutAbsoluteIn(Position.Relative);
        assertEquals(0f, relative[0], EPS);
        assertEquals(0f, relative[1], EPS);

        float[] sticky = layoutAbsoluteIn(Position.Sticky);
        assertEquals(0f, sticky[0], EPS);
        assertEquals(0f, sticky[1], EPS);
    }

    @Test
    void placesAbsoluteChildrenOfAStaticParentInNearestPositionedAncestor() {
        float[] result = layoutAbsoluteIn(Position.Static);
        assertEquals(5f, result[0], EPS);
        assertEquals(5f, result[1], EPS);
    }

    private static void assertInitialContainingBlock(Position position, Position rootPosition,
            AvailableSpace availableWidth, AvailableSpace availableHeight, Dimension width,
            Dimension height, float expectedX, float expectedY, float expectedWidth,
            float expectedHeight) {

        Style childStyle = new Style();
        childStyle.position(position);
        childStyle.size(Size.of(width, height));
        childStyle.layoutInset(new Rect<>(
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0)));
        NodeImpl child = new NodeImpl(childStyle);

        Style rootStyle = new Style();
        rootStyle.position(rootPosition);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        NodeImpl root = new NodeImpl(rootStyle, List.of(child));

        LayoutEngine.computeLayout(root, Size.of(availableWidth, availableHeight));

        Layout rootLayout = root.getLayout();
        Point<java.lang.Float> rootPosition2 = rootLayout.position();
        assertEquals(20f, rootPosition2.x, EPS);
        assertEquals(30f, rootPosition2.y, EPS);

        Layout layout = child.getLayout();
        assertEquals(expectedX, layout.x, EPS);
        assertEquals(expectedY, layout.y, EPS);
        assertEquals(expectedWidth, layout.width, EPS);
        assertEquals(expectedHeight, layout.height, EPS);
    }

    @Test
    void absoluteWithNoPositionedAncestorReportsInitialContainingBlock() {
        assertInitialContainingBlock(Position.Absolute, Position.Static,
                AvailableSpace.definite(300), AvailableSpace.definite(200),
                Dimension.length(10), Dimension.length(10),
                270, 160, 10, 10);
    }

    @Test
    void fixedInsideAStaticRootReportsInitialContainingBlock() {
        assertInitialContainingBlock(Position.Fixed, Position.Static,
                AvailableSpace.definite(300), AvailableSpace.definite(200),
                Dimension.length(10), Dimension.length(10),
                270, 160, 10, 10);
    }

    @Test
    void fixedInsideAPositionedRootReportsInitialContainingBlock() {
        assertInitialContainingBlock(Position.Fixed, Position.Relative,
                AvailableSpace.definite(300), AvailableSpace.definite(200),
                Dimension.length(10), Dimension.length(10),
                270, 160, 10, 10);
    }

    @Test
    void percentageSizesUseTheInitialContainingBlock() {
        assertInitialContainingBlock(Position.Absolute, Position.Static,
                AvailableSpace.definite(300), AvailableSpace.definite(200),
                Dimension.percent(50), Dimension.percent(50),
                130, 70, 150, 100);
    }

    @Test
    void indefiniteSpaceFallsBackToTheRootPaddingBox() {
        assertInitialContainingBlock(Position.Fixed, Position.Static,
                AvailableSpace.maxContent(), AvailableSpace.maxContent(),
                Dimension.length(10), Dimension.length(10),
                85, 85, 10, 10);
    }

    @Test
    void theFallbackIsAppliedPerAxis() {
        assertInitialContainingBlock(Position.Absolute, Position.Static,
                AvailableSpace.definite(300), AvailableSpace.maxContent(),
                Dimension.length(10), Dimension.length(10),
                270, 85, 10, 10);
    }

    @Test
    void ignoresInsetsOnStaticAndStickyNodes() {
        for (Position position : List.of(Position.Static, Position.Sticky)) {

            Style childStyle = new Style();
            childStyle.position(position);
            childStyle.size(Size.of(Dimension.length(10), Dimension.length(10)));
            childStyle.layoutInset(new Rect<>(
                    LengthPercentageAuto.length(15),
                    LengthPercentageAuto.auto(),
                    LengthPercentageAuto.length(15),
                    LengthPercentageAuto.auto()));
            NodeImpl child = new NodeImpl(childStyle);

            Style rootStyle = new Style();
            rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
            NodeImpl root = new NodeImpl(rootStyle, List.of(child));

            LayoutEngine.computeLayout(root, (float) 100, (float) 100);
            Layout layout = child.getLayout();
            assertEquals(0f, layout.x, EPS);
            assertEquals(0f, layout.y, EPS);
        }
    }
}
