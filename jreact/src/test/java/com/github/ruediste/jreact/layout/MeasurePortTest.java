package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Port of measure.test.ts from the TypeScript/WebAssembly test-suite.
 * WebAssembly resource cleanup ({@code free()}) is omitted; every functional
 * case and its upstream expectations are preserved.
 */
class MeasurePortTest {

    private static final float EPS = 0.01f;

    @Test
    void measuresALeaf() {
        Node node = new Node(new Style());
        int[] calls = { 0 };
        Node[] measuredNode = { null };

        node.measure = (known, available, nodeBeingMeasured) -> {
            calls[0]++;
            measuredNode[0] = nodeBeingMeasured;
            return Size.of(150f, 50f);
        };
        LayoutEngine.computeLayout(node, Size.of(AvailableSpace.definite(300), AvailableSpace.definite(100)));

        Layout layout = node.layout;
        assertTrue(calls[0] > 0);
        assertSame(node, measuredNode[0]);
        assertEquals(150f, layout.size().width, EPS);
        assertEquals(50f, layout.size().height, EPS);
    }

    private static void assertBoxSizingMeasurement(BoxSizing boxSizing, float expectedWidth,
            float expectedAvailableWidth) {
        Style style = new Style();
        style.size(Size.of(Dimension.length(100), Dimension.auto()));
        style.boxSizing(boxSizing);
        style.layoutPadding(Rect.all(LengthPercentage.length(10)));
        style.layoutBorder(Rect.all(LengthPercentage.length(2)));
        Node node = new Node(style);
        List<AvailableSpace> measuredWidths = new ArrayList<>();

        node.measure = (known, available, measuredNode) -> {
            assertSame(node, measuredNode);
            assertEquals(Dimension.length(100), measuredNode.style.getWidth());
            assertEquals(boxSizing, measuredNode.style.boxSizing());
            measuredWidths.add(available.width);
            float width = known.width.isDefinite() ? known.width.value : 40f;
            return Size.of(width, 30f);
        };
        LayoutEngine.computeLayout(node, Size.of(AvailableSpace.definite(500), AvailableSpace.definite(500)));

        Layout layout = node.layout;
        boolean containsWidth = measuredWidths.stream()
                .anyMatch(space -> space.isDefinite() && Math.abs(space.value
                        - expectedAvailableWidth) < EPS);
        assertTrue(containsWidth,
                "expected an available width of " + expectedAvailableWidth + " in " + measuredWidths);
        assertEquals(expectedWidth, layout.size().width, EPS);
        assertEquals(54f, layout.size().height, EPS);
        assertTrue(LayoutEngine.printTree(node).contains("content_w:"));
    }

    @Test
    void keepsContentMeasurementAndBoxSizingForBorderBox() {
        assertBoxSizingMeasurement(BoxSizing.BorderBox, 100, 76);
    }

    @Test
    void keepsContentMeasurementAndBoxSizingForContentBox() {
        assertBoxSizingMeasurement(BoxSizing.ContentBox, 124, 100);
    }
}
