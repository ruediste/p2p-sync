package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Port of flex.test.ts from the TypeScript/WebAssembly test-suite. WebAssembly
 * resource cleanup ({@code free()}) is omitted; all functional cases and
 * assertions are preserved.
 */
class FlexPortTest {

    private static Style sized(float width, float height) {
        Style style = new Style();
        style.size = Size.of(Dimension.length(width), Dimension.length(height));
        return style;
    }

    private static void assertSize(Layout layout, float width, float height) {
        assertEquals(width, layout.width, 0.01f, "width");
        assertEquals(height, layout.height, 0.01f, "height");
    }

    // ---- Flex Style Properties / Flex Direction ----

    @Test
    void flexDirectionDefaultsToRowSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(FlexDirection.Row, style.flexDirection);

        style.flexDirection = FlexDirection.Column;
        assertEquals(FlexDirection.Column, style.flexDirection);

        style.flexDirection = FlexDirection.RowReverse;
        assertEquals(FlexDirection.RowReverse, style.flexDirection);

        style.flexDirection = FlexDirection.ColumnReverse;
        assertEquals(FlexDirection.ColumnReverse, style.flexDirection);

        style.flexDirection = FlexDirection.Row;
        assertEquals(FlexDirection.Row, style.flexDirection);
    }

    // ---- Flex Wrap ----

    @Test
    void flexWrapDefaultsToNoWrapSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(FlexWrap.NoWrap, style.flexWrap);

        style.flexWrap = FlexWrap.Wrap;
        assertEquals(FlexWrap.Wrap, style.flexWrap);

        style.flexWrap = FlexWrap.WrapReverse;
        assertEquals(FlexWrap.WrapReverse, style.flexWrap);

        style.flexWrap = FlexWrap.NoWrap;
        assertEquals(FlexWrap.NoWrap, style.flexWrap);
    }

    // ---- Flex Grow and Shrink ----

    @Test
    void flexGrowDefaultsToZeroSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(0f, style.flexGrow, 0.01f);

        style.flexGrow = 1;
        assertEquals(1f, style.flexGrow, 0.01f);

        style.flexGrow = 2.5f;
        assertEquals(2.5f, style.flexGrow, 0.01f);

        style.flexGrow = 0;
        assertEquals(0f, style.flexGrow, 0.01f);
    }

    @Test
    void flexShrinkDefaultsToOneSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(1f, style.flexShrink, 0.01f);

        style.flexShrink = 0;
        assertEquals(0f, style.flexShrink, 0.01f);

        style.flexShrink = 3.5f;
        assertEquals(3.5f, style.flexShrink, 0.01f);

        style.flexShrink = 1;
        assertEquals(1f, style.flexShrink, 0.01f);
    }

    // ---- Flex Basis ----

    @Test
    void flexBasisDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.flexBasis);

        style.setFlexBasis(100);
        assertEquals(LengthPercentageAuto.length(100), style.flexBasis);

        style.setFlexBasis("50%");
        assertEquals(LengthPercentageAuto.percent(50), style.flexBasis);

        style.setFlexBasis("auto");
        assertEquals(LengthPercentageAuto.auto(), style.flexBasis);
    }

    @Test
    void flexBasisHandlesVariousValues() {
        Style style = new Style();

        style.setFlexBasis(0);
        assertEquals(LengthPercentageAuto.length(0), style.flexBasis);

        style.setFlexBasis(200);
        assertEquals(LengthPercentageAuto.length(200), style.flexBasis);

        style.setFlexBasis("100%");
        assertEquals(LengthPercentageAuto.percent(100), style.flexBasis);

        style.setFlexBasis("25%");
        assertEquals(LengthPercentageAuto.percent(25), style.flexBasis);
    }

    // ---- Alignment Properties ----

    @Test
    void alignItemsSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.alignItems);

        style.alignItems = AlignItems.Start;
        assertEquals(AlignItems.Start, style.alignItems);

        style.alignItems = AlignItems.End;
        assertEquals(AlignItems.End, style.alignItems);

        style.alignItems = AlignItems.Center;
        assertEquals(AlignItems.Center, style.alignItems);

        style.alignItems = AlignItems.Baseline;
        assertEquals(AlignItems.Baseline, style.alignItems);

        style.alignItems = AlignItems.Stretch;
        assertEquals(AlignItems.Stretch, style.alignItems);

        style.alignItems = null;
        assertNull(style.alignItems);
    }

    @Test
    void alignSelfSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(AlignSelf.Auto, style.alignSelf);

        style.alignSelf = AlignSelf.FlexStart;
        assertEquals(AlignSelf.FlexStart, style.alignSelf);

        style.alignSelf = AlignSelf.FlexEnd;
        assertEquals(AlignSelf.FlexEnd, style.alignSelf);

        style.alignSelf = AlignSelf.Center;
        assertEquals(AlignSelf.Center, style.alignSelf);

        style.alignSelf = AlignSelf.Auto;
        assertEquals(AlignSelf.Auto, style.alignSelf);
    }

    @Test
    void alignContentSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.alignContent);

        style.alignContent = AlignContent.Start;
        assertEquals(AlignContent.Start, style.alignContent);

        style.alignContent = AlignContent.End;
        assertEquals(AlignContent.End, style.alignContent);

        style.alignContent = AlignContent.SpaceBetween;
        assertEquals(AlignContent.SpaceBetween, style.alignContent);

        style.alignContent = AlignContent.SpaceAround;
        assertEquals(AlignContent.SpaceAround, style.alignContent);

        style.alignContent = AlignContent.SpaceEvenly;
        assertEquals(AlignContent.SpaceEvenly, style.alignContent);

        style.alignContent = null;
        assertNull(style.alignContent);
    }

    @Test
    void justifyContentSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.justifyContent);

        style.justifyContent = JustifyContent.Start;
        assertEquals(JustifyContent.Start, style.justifyContent);

        style.justifyContent = JustifyContent.End;
        assertEquals(JustifyContent.End, style.justifyContent);

        style.justifyContent = JustifyContent.Center;
        assertEquals(JustifyContent.Center, style.justifyContent);

        style.justifyContent = JustifyContent.SpaceBetween;
        assertEquals(JustifyContent.SpaceBetween, style.justifyContent);

        style.justifyContent = JustifyContent.SpaceAround;
        assertEquals(JustifyContent.SpaceAround, style.justifyContent);

        style.justifyContent = JustifyContent.SpaceEvenly;
        assertEquals(JustifyContent.SpaceEvenly, style.justifyContent);

        style.justifyContent = null;
        assertNull(style.justifyContent);
    }

    // ---- Basic Flex Layout ----

    @Test
    void validatesFlexRowLayout() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(30, 30));
        Node child2 = new Node(sized(30, 30));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(0f, child1Layout.x, 0.01f);
        assertEquals(30f, child2Layout.x, 0.01f);
        assertEquals(child1Layout.y, child2Layout.y, 0.01f);
    }

    @Test
    void validatesFlexColumnLayout() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Column;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(50, 50));
        Node child2 = new Node(sized(50, 50));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(0f, child1Layout.y, 0.01f);
        assertEquals(50f, child2Layout.y, 0.01f);
        assertEquals(child1Layout.x, child2Layout.x, 0.01f);
    }

    @Test
    void validatesFlexRowReverseLayout() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.RowReverse;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(30, 30));
        Node child2 = new Node(sized(30, 30));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // In row-reverse, first child should be on the right
        assertTrue(child1Layout.x > child2Layout.x);
    }

    @Test
    void validatesFlexColumnReverseLayout() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.ColumnReverse;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(50, 30));
        Node child2 = new Node(sized(50, 30));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // In column-reverse, first child should be at the bottom
        assertTrue(child1Layout.y > child2Layout.y);
    }

    // ---- Flex Grow ----

    @Test
    void validatesFlexGrowDistributesSpace() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.flexGrow = 1;
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.flexGrow = 1;
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // Both should share space equally
        assertEquals(50f, child1Layout.width, 0.01f);
        assertEquals(50f, child2Layout.width, 0.01f);
    }

    @Test
    void validatesFlexGrowWithDifferentRatios() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.flexGrow = 1;
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.flexGrow = 3;
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // 1:3 ratio
        assertEquals(25f, child1Layout.width, 0.01f);
        assertEquals(75f, child2Layout.width, 0.01f);
    }

    // ---- Flex Shrink ----

    @Test
    void validatesFlexShrinkWhenContentOverflows() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = sized(100, 30);
        child1Style.flexShrink = 1;
        Node child1 = new Node(child1Style);

        Style child2Style = sized(100, 30);
        child2Style.flexShrink = 1;
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // Both should shrink equally
        assertEquals(50f, child1Layout.width, 0.01f);
        assertEquals(50f, child2Layout.width, 0.01f);
    }

    @Test
    void validatesFlexShrinkZeroPreventsShrinking() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = sized(60, 30);
        child1Style.flexShrink = 0;
        Node child1 = new Node(child1Style);

        Style child2Style = sized(60, 30);
        child2Style.flexShrink = 1;
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // Child1 should keep its size, child2 shrinks
        assertEquals(60f, child1Layout.width, 0.01f);
        assertEquals(40f, child2Layout.width, 0.01f);
    }

    // ---- Flex Basis ----

    @Test
    void validatesFlexBasisSetsInitialSize() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.setFlexBasis(30);
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.setFlexBasis(70);
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(30f, child1Layout.width, 0.01f);
        assertEquals(70f, child2Layout.width, 0.01f);
    }

    @Test
    void validatesFlexBasisWithPercentage() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.size = Size.of(Dimension.length(200), Dimension.length(50));
        Node root = new Node(rootStyle);

        Style child1Style = new Style();
        child1Style.setFlexBasis("50%");
        Node child1 = new Node(child1Style);

        root.addChild(child1);

        root.computeLayout(200, 50);

        Layout child1Layout = child1.layout;
        assertEquals(100f, child1Layout.width, 0.01f);
    }

    // ---- Flex Wrap ----

    @Test
    void validatesFlexWrap() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.flexWrap = FlexWrap.Wrap;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        List<Node> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Node child = new Node(sized(60, 30));
            children.add(child);
            root.addChild(child);
        }

        root.computeLayout(100, 100);

        // Each 60px child wraps to its own row since only 1 fits in 100px
        Layout layout0 = children.get(0).layout;
        Layout layout1 = children.get(1).layout;
        Layout layout2 = children.get(2).layout;

        // Each child should be on a different row
        assertTrue(layout1.y > layout0.y);
        assertTrue(layout2.y > layout1.y);
    }

    @Test
    void validatesFlexWrapReverse() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.flexWrap = FlexWrap.WrapReverse;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(60, 30));
        Node child2 = new Node(sized(60, 30));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // In wrap-reverse, second row should appear above first
        assertTrue(child2Layout.y < child1Layout.y);
    }

    // ---- Flex Alignment ----

    @Test
    void validatesAlignItemsCenter() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.alignItems = AlignItems.Center;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child = new Node(sized(20, 20));
        root.addChild(child);

        root.computeLayout(100, 100);

        Layout childLayout = child.layout;
        assertEquals(40f, childLayout.y, 0.01f);
    }

    @Test
    void validatesJustifyContentCenter() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.justifyContent = JustifyContent.Center;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child = new Node(sized(20, 20));
        root.addChild(child);

        root.computeLayout(100, 100);

        Layout childLayout = child.layout;
        assertEquals(40f, childLayout.x, 0.01f);
    }

    @Test
    void validatesAlignItemsAndJustifyContentCenter() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.alignItems = AlignItems.Center;
        rootStyle.justifyContent = JustifyContent.Center;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child = new Node(sized(20, 20));
        root.addChild(child);

        root.computeLayout(100, 100);

        Layout childLayout = child.layout;
        assertEquals(40f, childLayout.x, 0.01f);
        assertEquals(40f, childLayout.y, 0.01f);
    }

    @Test
    void validatesJustifyContentSpaceBetween() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.justifyContent = JustifyContent.SpaceBetween;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(20, 20));
        Node child2 = new Node(sized(20, 20));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(0f, child1Layout.x, 0.01f);
        assertEquals(80f, child2Layout.x, 0.01f);
    }

    @Test
    void validatesJustifyContentSpaceAround() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.justifyContent = JustifyContent.SpaceAround;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(20, 20));
        Node child2 = new Node(sized(20, 20));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // Space around: (100 - 40) / 4 = 15 per half-space
        assertEquals(15f, child1Layout.x, 0.01f);
        assertEquals(65f, child2Layout.x, 0.01f);
    }

    @Test
    void validatesJustifyContentSpaceEvenly() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.justifyContent = JustifyContent.SpaceEvenly;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(20, 20));
        Node child2 = new Node(sized(20, 20));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        // Space evenly: (100 - 40) / 3 = 20 per space
        assertEquals(20f, child1Layout.x, 0.01f);
        assertEquals(60f, child2Layout.x, 0.01f);
    }

    @Test
    void validatesAlignSelfOverridesAlignItems() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.alignItems = AlignItems.Start;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(20, 20));

        Style child2Style = sized(20, 20);
        child2Style.alignSelf = AlignSelf.End;
        Node child2 = new Node(child2Style);

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(0f, child1Layout.y, 0.01f);
        assertEquals(80f, child2Layout.y, 0.01f);
    }

    // ---- Flex Gap ----

    @Test
    void validatesGapBetweenFlexItems() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Row;
        rootStyle.gap = Size.of(LengthPercentage.length(10), LengthPercentage.length(10));
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(30, 30));
        Node child2 = new Node(sized(30, 30));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 50);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(10f, child2Layout.x - (child1Layout.x + child1Layout.width), 0.01f);
    }

    @Test
    void validatesGapInColumnDirection() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Column;
        rootStyle.gap = Size.of(LengthPercentage.length(10), LengthPercentage.length(20));
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(30, 20));
        Node child2 = new Node(sized(30, 20));

        root.addChild(child1);
        root.addChild(child2);

        root.computeLayout(100, 100);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(20f, child2Layout.y - (child1Layout.y + child1Layout.height), 0.01f);
    }

    // ---- Flex Nested ----

    @Test
    void validatesNestedFlexContainers() {

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.flexDirection = FlexDirection.Column;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(100));
        Node root = new Node(rootStyle);

        // First row container
        Style row1Style = new Style();
        row1Style.display = Display.Flex;
        row1Style.flexDirection = FlexDirection.Row;
        row1Style.size = Size.of(Dimension.length(100), Dimension.length(50));
        Node row1 = new Node(row1Style);

        // Children for first row
        Style child1Style = new Style();
        child1Style.flexGrow = 1;
        Node child1 = new Node(child1Style);

        Style child2Style = new Style();
        child2Style.flexGrow = 1;
        Node child2 = new Node(child2Style);

        row1.addChild(child1);
        row1.addChild(child2);
        root.addChild(row1);

        root.computeLayout(100, 100);

        Layout row1Layout = row1.layout;
        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(100f, row1Layout.width, 0.01f);
        assertEquals(50f, child1Layout.width, 0.01f);
        assertEquals(50f, child2Layout.width, 0.01f);
    }

    // ---- Writing direction and safe alignment ----

    @Test
    void appliesRightToLeftDirectionThroughThePublicLayoutApi() {
        Style childStyle = sized(20, 10);
        Node first = new Node(childStyle);
        Node second = new Node(childStyle);

        Style rootStyle = new Style();
        rootStyle.display = Display.Flex;
        rootStyle.direction = Direction.Rtl;
        rootStyle.size = Size.of(Dimension.length(100), Dimension.length(10));
        Node root = new Node(rootStyle, List.of(first, second));

        root.computeLayout(100, 10);

        assertEquals(80f, first.layout.x, 0.01f);
        assertEquals(60f, second.layout.x, 0.01f);
    }

    @Test
    void appliesSafeAndSelfRelativeAlignment() {
        Style oversizedStyle = sized(150, 10);
        oversizedStyle.flexShrink = 0;
        Node oversized = new Node(oversizedStyle);

        Style safeRootStyle = new Style();
        safeRootStyle.display = Display.Flex;
        safeRootStyle.size = Size.of(Dimension.length(100), Dimension.length(20));
        safeRootStyle.justifyContent = JustifyContent.SafeCenter;
        Node safeRoot = new Node(safeRootStyle, List.of(oversized));

        safeRoot.computeLayout(100, 20);
        assertEquals(0f, oversized.layout.x, 0.01f);
        Style selfAlignedStyle = sized(20, 10);
        selfAlignedStyle.direction = Direction.Rtl;
        selfAlignedStyle.alignSelf = AlignSelf.SelfStart;
        Node selfAligned = new Node(selfAlignedStyle);

        Style selfRootStyle = new Style();
        selfRootStyle.display = Display.Flex;
        selfRootStyle.direction = Direction.Ltr;
        selfRootStyle.flexDirection = FlexDirection.Column;
        selfRootStyle.size = Size.of(Dimension.length(100), Dimension.length(20));
        Node selfRoot = new Node(selfRootStyle, List.of(selfAligned));

        selfRoot.computeLayout(100, 20);
        assertEquals(80f, selfAligned.layout.x, 0.01f);
    }
}
