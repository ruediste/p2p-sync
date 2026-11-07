package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Port of grid-areas.test.ts from the TypeScript/WebAssembly test-suite.
 * WebAssembly resource cleanup ({@code free()}) is omitted; all functional
 * cases
 * and assertions are preserved.
 */
class GridAreasPortTest {

    private static void assertLayout(Layout layout, float x, float y, float width, float height) {
        assertEquals(x, layout.x, 0.01f, "x");
        assertEquals(y, layout.y, 0.01f, "y");
        assertEquals(width, layout.width, 0.01f, "width");
        assertEquals(height, layout.height, 0.01f, "height");
    }

    @Test
    void preservesUnnamedRowsAndColumnsInGridTemplateAreas() {
        Style style = new Style();
        style.display = Display.Grid;
        style.setWidth(300);
        style.setHeight(200);
        style.setGridTemplateAreaRowCount(2);
        style.setGridTemplateAreaColumnCount(3);
        style.setGridTemplateAreas(
                List.of(new GridTemplateArea("main", 1, 2, 1, 2)));

        assertEquals(2, style.getGridTemplateAreaRowCount());
        assertEquals(3, style.getGridTemplateAreaColumnCount());
        List<Node> children = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            children.add(new Node(new Style()));
        }
        Node root = new Node(style, children);

        LayoutEngine.computeLayout(root, (float) 300, (float) 200);

        assertEquals(200f, children.get(2).layout.x, 0.01f);
        assertEquals(100f, children.get(3).layout.y, 0.01f);
    }

    @Test
    void clampsExplicitGridAreaCountsToTheNamedAreaExtents() {
        Style style = new Style();
        style.setGridTemplateAreaRowCount(1);
        style.setGridTemplateAreaColumnCount(1);
        style.setGridTemplateAreas(
                List.of(new GridTemplateArea("content", 1, 4, 1, 5)));

        assertEquals(3, style.getGridTemplateAreaRowCount());
        assertEquals(4, style.getGridTemplateAreaColumnCount());
    }

    @Test
    void preservesExplicitGridAreaCountsWhenAreasAreAssignedLater() {
        Style style = new Style();
        style.setGridTemplateAreaRowCount(2);
        style.setGridTemplateAreaColumnCount(3);

        style.setGridTemplateAreas(
                List.of(new GridTemplateArea("main", 1, 2, 1, 2)));

        assertEquals(2, style.getGridTemplateAreaRowCount());
        assertEquals(3, style.getGridTemplateAreaColumnCount());

        style.setGridTemplateAreas(List.of());

        assertEquals(List.of(), style.getGridTemplateAreas());
        assertEquals(2, style.getGridTemplateAreaRowCount());
        assertEquals(3, style.getGridTemplateAreaColumnCount());
    }

    @Test
    void shrinksAndClearsInferredGridDimensions() {
        List<Node> children = List.of(new Node(new Style()), new Node(new Style()));
        Style style = new Style();
        style.display = Display.Grid;
        style.setWidth(200);
        style.setHeight(100);
        style.setGridTemplateAreas(
                List.of(new GridTemplateArea("main", 1, 3, 1, 3)));
        Node root = new Node(style, children);

        List<List<GridTemplateArea>> updates = List.of(
                List.of(new GridTemplateArea("main", 1, 2, 1, 2)),
                List.of());
        for (List<GridTemplateArea> areas : updates) {
            style.setGridTemplateAreas(areas);
            assertEquals(areas.size(), style.getGridTemplateAreaRowCount());
            assertEquals(areas.size(), style.getGridTemplateAreaColumnCount());
            root.style = style.copy();
            LayoutEngine.computeLayout(root, (float) 200, (float) 100);

            assertLayout(children.get(0).layout, 0, 0, 200, 50);
            assertLayout(children.get(1).layout, 0, 50, 200, 50);
        }
    }

    @ParameterizedTest(name = "preserves explicit dimensions through {0} node style round-trips")
    @ValueSource(strings = { "leaf", "context", "children" })
    void preservesExplicitDimensionsThroughNodeStyleRoundTrips(String creation) {
        GridTemplateArea area = new GridTemplateArea("main", 1, 3, 1, 4);
        Style original = new Style();
        original.setGridTemplateAreas(List.of(area));
        original.setGridTemplateAreaRowCount(2);
        original.setGridTemplateAreaColumnCount(3);
        Node node;
        if (creation.equals("leaf")) {
            node = new Node(original);
        } else if (creation.equals("context")) {
            node = Node.withContext(original, Map.of("text", "hello"));
        } else {
            node = new Node(original, List.of());
        }

        Style grown = node.style.copy();
        grown.setGridTemplateAreas(List.of(new GridTemplateArea("main", 1, 5, 1, 6)));
        assertEquals(4, grown.getGridTemplateAreaRowCount());
        assertEquals(5, grown.getGridTemplateAreaColumnCount());
        node.style = grown.copy();

        Style cleared = node.style.copy();
        cleared.setGridTemplateAreas(List.of());
        assertEquals(2, cleared.getGridTemplateAreaRowCount());
        assertEquals(3, cleared.getGridTemplateAreaColumnCount());

        // Replacing the entire style must discard the previous explicit counts.
        Style replacement = new Style();
        replacement.setGridTemplateAreas(List.of(area));
        node.style = replacement.copy();
        Style inferred = node.style.copy();
        inferred.setGridTemplateAreas(List.of());
        assertEquals(0, inferred.getGridTemplateAreaRowCount());
        assertEquals(0, inferred.getGridTemplateAreaColumnCount());
    }

    @ParameterizedTest(name = "preserves only the explicitly set {0} dimension")
    @ValueSource(strings = { "row", "column" })
    void preservesOnlyTheExplicitlySetDimension(String axis) {
        Style style = new Style();
        style.display = Display.Grid;
        style.setWidth(200);
        style.setHeight(100);
        style.gridAutoFlow = axis.equals("row") ? GridAutoFlow.Column : GridAutoFlow.Row;
        style.setGridTemplateAreas(
                List.of(new GridTemplateArea("main", 1, 4, 1, 4)));
        if (axis.equals("row")) {
            style.setGridTemplateAreaRowCount(2);
        } else {
            style.setGridTemplateAreaColumnCount(2);
        }
        style.setGridTemplateAreas(List.of());
        if (axis.equals("row")) {
            assertEquals(2, style.getGridTemplateAreaRowCount());
            assertEquals(0, style.getGridTemplateAreaColumnCount());
        } else {
            assertEquals(0, style.getGridTemplateAreaRowCount());
            assertEquals(2, style.getGridTemplateAreaColumnCount());
        }

        List<Node> children = List.of(new Node(new Style()), new Node(new Style()));
        Node root = new Node(style, children);
        LayoutEngine.computeLayout(root, (float) 200, (float) 100);
        if (axis.equals("row")) {
            assertLayout(children.get(1).layout, 0, 50, 200, 50);
        } else {
            assertLayout(children.get(1).layout, 100, 0, 100, 100);
        }

        style.setGridTemplateAreaRowCount(0);
        style.setGridTemplateAreaColumnCount(0);
        assertEquals(0, style.getGridTemplateAreaRowCount());
        assertEquals(0, style.getGridTemplateAreaColumnCount());
    }

    @Test
    void preservesExplicitDimensionsInStylesPassedToMeasureCallbacks() {
        Object context = Map.of("text", "hello");
        Style original = new Style();
        original.setGridTemplateAreaRowCount(2);
        original.setGridTemplateAreaColumnCount(3);
        original.setGridTemplateAreas(
                List.of(new GridTemplateArea("main", 1, 5, 1, 6)));
        Node node = Node.withContext(original, context);
        List<Style> measuredStyles = new ArrayList<>();
        List<Object> contexts = new ArrayList<>();

        node.measure = (known, available, measuredNode) -> {
            Style style = measuredNode.style.copy();
            measuredStyles.add(style);
            contexts.add(measuredNode.context);
            return Size.of(20f, 10f);
        };
        LayoutEngine.computeLayout(node, Size.of(AvailableSpace.definite(200), AvailableSpace.definite(100)));
        assertTrue(measuredStyles.size() > 0);
        for (Style style : measuredStyles) {
            style.setGridTemplateAreas(List.of());
            assertEquals(2, style.getGridTemplateAreaRowCount());
            assertEquals(3, style.getGridTemplateAreaColumnCount());
        }
        assertTrue(contexts.stream().allMatch(value -> value == context));
    }
}
