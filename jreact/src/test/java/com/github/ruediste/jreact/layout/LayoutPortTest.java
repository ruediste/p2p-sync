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
    void validatesBasicFlexLayout() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.layoutFlexDirection(FlexDirection.Column);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        Node root = new Node(rootStyle);

        Style childStyle = sized(50, 50);
        Node child1 = new Node(childStyle);
        Node child2 = new Node(childStyle);

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout rootLayout = root.layout;
        assertEquals(100f, rootLayout.width, EPS);
        assertEquals(100f, rootLayout.height, EPS);

        assertLayout(child1.layout, 0, 0, 50, 50);
        assertLayout(child2.layout, 0, 50, 50, 50);
    }

    @Test
    void validatesCenterAlignment() {
        Style rootStyle = new Style();
        rootStyle.display(Display.Flex);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutAlignItems(AlignItems.Center);
        rootStyle.layoutJustifyContent(JustifyContent.Center);
        Node root = new Node(rootStyle);

        Node child = new Node(sized(20, 20));
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout layout = child.layout;
        assertEquals(40f, layout.x, EPS);
        assertEquals(40f, layout.y, EPS);
    }

    // ---- Grid Layout Computation ----

    @Test
    void validatesBasicGridLayoutWithExplicitPlacement() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
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
        assertEquals(100f, rootLayout.width, EPS);
        assertEquals(100f, rootLayout.height, EPS);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;
        assertTrue(child2Layout.x >= child1Layout.x);
    }

    @Test
    void validatesGridAutoFlowRow() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.gridAutoFlow(GridAutoFlow.Row);
        Node root = new Node(rootStyle);

        List<Node> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Node child = new Node(sized(20, 20));
            children.add(child);
            root.addChild(child);
        }

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        for (Node child : children) {
            Layout layout = child.layout;
            assertEquals(20f, layout.width, EPS);
            assertEquals(20f, layout.height, EPS);
        }
    }

    @Test
    void validatesGridWithSpan() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.gridRow(Line.of(GridPlacement.line(1), GridPlacement.line(2)));
        childStyle.gridColumn(Line.of(GridPlacement.line(1), GridPlacement.span(2)));
        Node child = new Node(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(childLayout.width, childLayout.width);
        assertEquals(childLayout.height, childLayout.height);
    }

    @Test
    void validatesGridWithGap() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.gap(Size.of(LengthPercentage.length(10), LengthPercentage.length(10)));
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
    void validatesGridWithScrollbarWidth() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Grid);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.scrollbarWidth(15);
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        Node child = new Node(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertTrue(childLayout.width <= 100f);
        assertTrue(childLayout.height <= 100f);
    }

    // ---- Block Layout Computation ----

    @Test
    void validatesBlockLayout() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        Node root = new Node(rootStyle);

        Node child = new Node(sized(50, 50));
        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(50f, childLayout.width, EPS);
        assertEquals(50f, childLayout.height, EPS);
    }

    @Test
    void validatesBlockLayoutWithItemIsReplaced() {

        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.itemIsReplaced(true);
        childStyle.aspectRatio(2f);
        childStyle.size(Size.of(Dimension.length(50), Dimension.auto()));
        Node child = new Node(childStyle);

        root.addChild(child);
        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(50f, childLayout.width, EPS);
        assertEquals(25f, childLayout.height, EPS);
    }

    // ---- Layout compound getters ----

    @Test
    void positionGetterReturnsPointWithXAndY() {
        Node root = new Node(sized(800, 600));
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Point<java.lang.Float> position = root.layout.position();
        assertNotNull(position);
        assertEquals(0f, position.x, EPS);
        assertEquals(0f, position.y, EPS);
    }

    @Test
    void sizeGetterReturnsSizeWithWidthAndHeight() {
        Node root = new Node(sized(800, 600));
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> size = root.layout.size();
        assertNotNull(size);
        assertEquals(800f, size.width, EPS);
        assertEquals(600f, size.height, EPS);
    }

    @Test
    void contentSizeGetterReturnsSizeWithContentWidthAndContentHeight() {
        Node root = new Node(new Style());
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> contentSize = root.layout.contentSize();
        assertNotNull(contentSize);
        assertTrue(contentSize.width >= 0);
        assertTrue(contentSize.height >= 0);
    }

    @Test
    void scrollbarSizeGetterReturnsSizeWithScrollbarWidthAndScrollbarHeight() {
        Node root = new Node(new Style());
        LayoutEngine.computeLayout(root, (float) 800, (float) 600);

        Size<java.lang.Float> scrollbarSize = root.layout.scrollbarSize();
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
        Node node = new Node(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> border = node.layout.border;
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
        Node node = new Node(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> padding = node.layout.padding;
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
        Node node = new Node(style);
        LayoutEngine.computeLayout(node, (float) 100, (float) 100);

        Rect<java.lang.Float> margin = node.layout.margin;
        assertNotNull(margin);
        assertEquals(5f, margin.left, EPS);
        assertEquals(10f, margin.right, EPS);
        assertEquals(8f, margin.top, EPS);
        assertEquals(12f, margin.bottom, EPS);
    }

    // ---- printTree ----

    @Test
    void printTreeReturnsStringRepresentationOfSingleNode() {
        Node node = new Node(sized(100, 50));
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
        Node root = new Node(rootStyle);

        Style childStyle = sized(50, 50);
        Node child1 = new Node(childStyle);
        Node child2 = new Node(childStyle);

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
        Node node = new Node(style);
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
        Node node = new Node(sized(100, 50));
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
        Node root = new Node(rootStyle);

        Style childStyle = sized(30, 30);
        Node level1 = new Node(childStyle);
        root.addChild(level1);

        Node level2 = new Node(childStyle);
        level1.addChild(level2);

        Node level3 = new Node(childStyle);
        level2.addChild(level3);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        String output = LayoutEngine.printTree(root);
        long lines = output.lines().filter(line -> !line.trim().isEmpty()).count();
        assertTrue(lines >= 4);
    }

    // ---- Content size extents ----

    private static void assertContentExtents(Style rootStyle, Style childStyle, float expectedWidth,
            float expectedHeight) {
        List<Node> children = new ArrayList<>();
        if (childStyle != null) {
            children.add(new Node(childStyle));
        }
        Node node = new Node(rootStyle, children);
        LayoutEngine.computeLayout(node, (float) 100, (float) 50);

        Layout layout = node.layout;
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
        Node absolute = new Node(absoluteStyle);

        Style parentStyle = new Style();
        parentStyle.position(parentPosition);
        parentStyle.size(Size.of(Dimension.length(50), Dimension.length(50)));
        parentStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        Node parent = new Node(parentStyle, List.of(absolute));

        Style rootStyle = new Style();
        rootStyle.position(Position.Relative);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Node root = new Node(rootStyle, List.of(parent));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);
        Layout layout = absolute.layout;
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
        Node child = new Node(childStyle);

        Style rootStyle = new Style();
        rootStyle.position(rootPosition);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Node root = new Node(rootStyle, List.of(child));

        LayoutEngine.computeLayout(root, Size.of(availableWidth, availableHeight));

        Layout rootLayout = root.layout;
        Point<java.lang.Float> rootPosition2 = rootLayout.position();
        assertEquals(20f, rootPosition2.x, EPS);
        assertEquals(30f, rootPosition2.y, EPS);

        Layout layout = child.layout;
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
            Node child = new Node(childStyle);

            Style rootStyle = new Style();
            rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
            Node root = new Node(rootStyle, List.of(child));

            LayoutEngine.computeLayout(root, (float) 100, (float) 100);
            Layout layout = child.layout;
            assertEquals(0f, layout.x, EPS);
            assertEquals(0f, layout.y, EPS);
        }
    }
}
