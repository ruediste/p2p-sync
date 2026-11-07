package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Port of block.test.ts from the TypeScript/WebAssembly test-suite. WebAssembly
 * resource cleanup ({@code free()}) is omitted; all functional cases and
 * assertions are preserved.
 */
class BlockPortTest {

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

    // ---- Block Style Properties / Display Block ----

    @Test
    void displaySetsToBlockCorrectly() {
        Style style = new Style();
        assertEquals(Display.Flex, style.display()); // default

        style.display(Display.Block);
        assertEquals(Display.Block, style.display());
    }

    // ---- Item Properties ----

    @Test
    void itemIsTableDefaultsToFalseSetsAndGetsCorrectly() {
        Style style = new Style();
        assertFalse(style.itemIsTable());

        style.itemIsTable(true);
        assertTrue(style.itemIsTable());

        style.itemIsTable(false);
        assertFalse(style.itemIsTable());
    }

    @Test
    void itemIsReplacedDefaultsToFalseSetsAndGetsCorrectly() {
        Style style = new Style();
        assertFalse(style.itemIsReplaced());

        style.itemIsReplaced(true);
        assertTrue(style.itemIsReplaced());

        style.itemIsReplaced(false);
        assertFalse(style.itemIsReplaced());
    }

    @Test
    void itemIsTableAndItemIsReplacedCanBeToggledMultipleTimes() {
        Style style = new Style();

        for (int i = 0; i < 5; i++) {
            style.itemIsTable(true);
            assertTrue(style.itemIsTable());
            style.itemIsTable(false);
            assertFalse(style.itemIsTable());
        }

        for (int i = 0; i < 5; i++) {
            style.itemIsReplaced(true);
            assertTrue(style.itemIsReplaced());
            style.itemIsReplaced(false);
            assertFalse(style.itemIsReplaced());
        }
    }

    // ---- Scrollbar Width ----

    @Test
    void scrollbarWidthDefaultsToZeroSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(0f, style.scrollbarWidth(), 0.01f);

        style.scrollbarWidth(15);
        assertEquals(15f, style.scrollbarWidth(), 0.01f);

        style.scrollbarWidth(0);
        assertEquals(0f, style.scrollbarWidth(), 0.01f);
    }

    @Test
    void scrollbarWidthHandlesDecimalValues() {
        Style style = new Style();

        style.scrollbarWidth(15.5f);
        assertEquals(15.5f, style.scrollbarWidth(), 0.00001f);

        style.scrollbarWidth(0.5f);
        assertEquals(0.5f, style.scrollbarWidth(), 0.00001f);
    }

    @Test
    void scrollbarWidthHandlesLargeValues() {
        Style style = new Style();

        style.scrollbarWidth(100);
        assertEquals(100f, style.scrollbarWidth(), 0.01f);
    }

    // ---- Text Align ----

    @Test
    void textAlignDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(TextAlign.Auto, style.layoutTextAlign());

        style.layoutTextAlign(TextAlign.LegacyLeft);
        assertEquals(TextAlign.LegacyLeft, style.layoutTextAlign());

        style.layoutTextAlign(TextAlign.LegacyRight);
        assertEquals(TextAlign.LegacyRight, style.layoutTextAlign());

        style.layoutTextAlign(TextAlign.LegacyCenter);
        assertEquals(TextAlign.LegacyCenter, style.layoutTextAlign());

        style.layoutTextAlign(TextAlign.Auto);
        assertEquals(TextAlign.Auto, style.layoutTextAlign());
    }

    @Test
    void textAlignAllVariantsWorkCorrectly() {
        Style style = new Style();

        TextAlign[] variants = {
                TextAlign.Auto,
                TextAlign.LegacyLeft,
                TextAlign.LegacyRight,
                TextAlign.LegacyCenter,
        };

        for (TextAlign variant : variants) {
            style.layoutTextAlign(variant);
            assertEquals(variant, style.layoutTextAlign());
        }
    }

    // ---- Basic Block Layout ----

    @Test
    void validatesBlockLayoutWithFixedSize() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Node child = new Node(sized(50, 50));
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(50f, childLayout.width, 0.01f);
        assertEquals(50f, childLayout.height, 0.01f);
    }

    @Test
    void validatesBlockStackedChildren() {

        Style rootStyle = sized(100, 200);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Node child1 = new Node(sized(100, 50));
        Node child2 = new Node(sized(100, 50));

        root.addChild(child1);
        root.addChild(child2);

        LayoutEngine.computeLayout(root, (float) 100, (float) 200);

        Layout child1Layout = child1.layout;
        Layout child2Layout = child2.layout;

        assertEquals(0f, child1Layout.y, 0.01f);
        assertEquals(50f, child2Layout.y, 0.01f);
    }

    @Test
    void validatesBlockWithPercentageWidth() {

        Style rootStyle = sized(200, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(0, 50);
        childStyle.size(Size.of(Dimension.percent(50), Dimension.length(50)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 200, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(100f, childLayout.width, 0.01f);
    }

    // ---- Block with Replaced Elements ----

    @Test
    void validatesBlockLayoutWithItemIsReplaced() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.itemIsReplaced(true);
        childStyle.aspectRatio(2f);
        childStyle.size(Size.of(Dimension.length(50), Dimension.auto()));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(50f, childLayout.width, 0.01f);
        assertEquals(25f, childLayout.height, 0.01f);
    }

    @Test
    void validatesBlockWithAspectRatioAndHeight() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.itemIsReplaced(true);
        childStyle.aspectRatio(2f);
        childStyle.size(Size.of(Dimension.auto(), Dimension.length(30)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(30f, childLayout.height, 0.01f);
        assertEquals(60f, childLayout.width, 0.01f);
    }

    @Test
    void validatesBlockWithItemIsTable() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(50, 50);
        childStyle.itemIsTable(true);
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(50f, childLayout.width, 0.01f);
        assertEquals(50f, childLayout.height, 0.01f);
    }

    // ---- Block with Margin and Padding ----

    @Test
    void validatesBlockWithMargin() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(50, 50);
        childStyle.layoutMargin(Rect.all(LengthPercentageAuto.length(10)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(10f, childLayout.x, 0.01f);
        assertEquals(10f, childLayout.y, 0.01f);
    }

    @Test
    void validatesBlockWithAutoMarginForCentering() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(50, 50);
        childStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(0)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertEquals(25f, childLayout.x, 0.01f);
    }

    @Test
    void validatesBlockWithPadding() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        rootStyle.layoutPadding(Rect.all(LengthPercentage.length(10)));
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertLayout(childLayout, 10, 10, 80, 80);
    }

    // ---- Block with Border ----

    @Test
    void validatesBlockWithBorder() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertLayout(childLayout, 5, 5, 90, 90);
    }

    // ---- Block with Scrollbar Width ----

    @Test
    void validatesBlockWithScrollbarWidthAffectsContent() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        rootStyle.scrollbarWidth(10);
        Node root = new Node(rootStyle);

        Style childStyle = new Style();
        childStyle.size(Size.of(Dimension.percent(100), Dimension.percent(100)));
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertTrue(childLayout.width <= 100f);
    }

    // ---- Block with Min/Max Size ----

    @Test
    void validatesBlockWithMinSize() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(20, 20);
        childStyle.setMinWidth(50);
        childStyle.setMinHeight(50);
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertTrue(childLayout.width >= 50f);
        assertTrue(childLayout.height >= 50f);
    }

    @Test
    void validatesBlockWithMaxSize() {

        Style rootStyle = sized(100, 100);
        rootStyle.display(Display.Block);
        Node root = new Node(rootStyle);

        Style childStyle = sized(100, 100);
        childStyle.setMaxWidth(50);
        childStyle.setMaxHeight(50);
        Node child = new Node(childStyle);
        root.addChild(child);

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);

        Layout childLayout = child.layout;
        assertTrue(childLayout.width <= 50f);
        assertTrue(childLayout.height <= 50f);
    }

    // ---- Float clearance and content alignment ----

    @Test
    void containsFloatsInAFlowRootAndAppliesClear() {

        Style floatedStyle = sized(20, 30);
        floatedStyle.floatMode(Float.Left);
        Node floated = new Node(floatedStyle);

        Style clearedStyle = sized(30, 10);
        clearedStyle.clear(Clear.Both);
        Node cleared = new Node(clearedStyle);

        Style rootStyle = new Style();
        rootStyle.display(Display.FlowRoot);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.auto()));
        Node root = new Node(rootStyle, List.of(floated, cleared));

        LayoutEngine.computeLayout(root, Size.of(AvailableSpace.definite(100), AvailableSpace.maxContent()));

        assertEquals(0f, floated.layout.y, 0.01f);
        assertEquals(30f, cleared.layout.y, 0.01f);
        assertEquals(40f, root.layout.height, 0.01f);
    }

    @Test
    void appliesAlignContentToBlockLayout() {

        Node child = new Node(sized(20, 20));

        Style rootStyle = new Style();
        rootStyle.display(Display.Block);
        rootStyle.alignContent(AlignContent.Center);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        Node root = new Node(rootStyle, List.of(child));

        LayoutEngine.computeLayout(root, (float) 100, (float) 100);
        assertEquals(40f, child.layout.y, 0.01f);
    }
}
