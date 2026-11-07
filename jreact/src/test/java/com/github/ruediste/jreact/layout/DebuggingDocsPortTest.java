package com.github.ruediste.jreact.layout;

import com.github.ruediste.jreact.Style;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Port of debugging-docs.test.ts. The documentation markdown is not executed;
 * each documented case is reproduced with the same tree, and canvas points are
 * computed by a helper that mirrors the {@code debugDraw} traversal from
 * {@code docs/advanced/debugging.md}.
 */
class DebuggingDocsPortTest {

    private static final float EPS = 0.01f;

    /** One drawn rectangle from the debug traversal. */
    private static final class RectCall {
        final float x;
        final float y;
        final float width;
        final float height;

        RectCall(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static void debugDraw(Node root, Node node, Point<java.lang.Float> parentOrigin,
            Point<java.lang.Float> absoluteOrigin, Point<java.lang.Float> rootOrigin,
            List<RectCall> calls) {
        Style style = node.style;
        Position position = style.position();

        Point<java.lang.Float> origin = position == Position.Fixed
                ? rootOrigin
                : position == Position.Absolute ? absoluteOrigin : parentOrigin;
        Layout layout = node.layout;
        Point<java.lang.Float> point = Point.of(origin.x + layout.x, origin.y + layout.y);
        calls.add(new RectCall(point.x, point.y, layout.width, layout.height));

        Point<java.lang.Float> childRootOrigin = node == root ? point : rootOrigin;
        Point<java.lang.Float> childAbsoluteOrigin = node == root || position != Position.Static
                ? point
                : absoluteOrigin;
        for (Node child : node.children) {
            debugDraw(root, child, point, childAbsoluteOrigin, childRootOrigin, calls);
        }
    }

    private static void assertCanvasPoint(Position position, Position parentPosition,
            Position rootPosition, float expectedX, float expectedY) {

        Style childStyle = new Style();
        childStyle.position(position);
        childStyle.size(Size.of(Dimension.length(10), Dimension.length(10)));
        childStyle.layoutInset(new Rect<>(
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.auto()));
        Node child = new Node(childStyle);

        Style parentStyle = new Style();
        parentStyle.position(parentPosition);
        parentStyle.size(Size.of(Dimension.length(50), Dimension.length(50)));
        parentStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        Node parent = new Node(parentStyle, List.of(child));

        Style rootStyle = new Style();
        rootStyle.position(rootPosition);
        rootStyle.size(Size.of(Dimension.length(100), Dimension.length(100)));
        rootStyle.layoutMargin(new Rect<>(
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.length(0),
                LengthPercentageAuto.length(30),
                LengthPercentageAuto.length(0)));
        rootStyle.layoutBorder(Rect.all(LengthPercentage.length(5)));
        Node root = new Node(rootStyle, List.of(parent));

        LayoutEngine.computeLayout(root, (float) 300, (float) 200);

        List<RectCall> calls = new ArrayList<>();
        Point<java.lang.Float> zero = Point.of(0f, 0f);
        debugDraw(root, root, zero, zero, zero, calls);

        assertEquals(3, calls.size());
        RectCall childCall = calls.get(2);
        assertEquals(expectedX, childCall.x, EPS);
        assertEquals(expectedY, childCall.y, EPS);
        assertEquals(10f, childCall.width, EPS);
        assertEquals(10f, childCall.height, EPS);
    }

    @Test
    void inFlowChildUsesItsDirectParent() {
        assertCanvasPoint(Position.Static, Position.Static, Position.Relative, 45, 65);
    }

    @Test
    void absoluteChildSkipsAStaticParent() {
        assertCanvasPoint(Position.Absolute, Position.Static, Position.Relative, 25, 35);
    }

    @Test
    void absoluteChildUsesItsRelativeParent() {
        assertCanvasPoint(Position.Absolute, Position.Relative, Position.Relative, 45, 65);
    }

    @Test
    void absoluteChildUsesItsStickyParent() {
        assertCanvasPoint(Position.Absolute, Position.Sticky, Position.Relative, 45, 65);
    }

    @Test
    void absoluteChildWithoutAPositionedAncestorUsesTheRootOrigin() {
        assertCanvasPoint(Position.Absolute, Position.Static, Position.Static, 0, 0);
    }

    @Test
    void fixedChildSkipsPositionedAncestors() {
        assertCanvasPoint(Position.Fixed, Position.Relative, Position.Relative, 0, 0);
    }
}
