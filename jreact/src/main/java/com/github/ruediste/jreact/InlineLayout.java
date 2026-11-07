package com.github.ruediste.jreact;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.github.ruediste.jreact.nodes.IconNode;
import com.github.ruediste.jreact.nodes.SpanNode;
import com.github.ruediste.jreact.nodes.TextNode;
import com.github.ruediste.jreact.nodes.TextualNode;

/**
 * Lays out text and icons in a span's shared inline flow.
 *
 * <p>
 * Traverses nested spans without creating additional flex containers, keeping
 * a shared line cursor across primitive and component boundaries. Uses each
 * text node's font metrics and line height, honors explicit newlines, and wraps
 * content without splitting Unicode surrogate pairs.
 *
 * <p>
 * Can measure the enclosing span height without mutation, or commit text
 * fragments and node bounds for painting. Nested spans receive enclosing
 * rectangular bounds rather than separate CSS fragment boxes. This helper
 * neither paints content nor lays out block containers or native editors.
 */
public final class InlineLayout {
    /** A measured text or icon fragment positioned within an inline line. */
    private record Fragment(Node node, String text, double x, double width, double height, FontMetrics metrics) {
    }

    /** Accumulates inline fragments and maintains the current line cursor. */
    private static final class Flow {
        final Graphics2D graphics;
        final double width;
        final List<List<Fragment>> lines = new ArrayList<>();
        double x;

        Flow(Graphics2D graphics, double width) {
            this.graphics = graphics;
            this.width = Math.max(0, width);
            lines.add(new ArrayList<>());
        }

        void newline() {
            lines.add(new ArrayList<>());
            x = 0;
        }

        void text(TextualNode node) {
            FontMetrics metrics = graphics.getFontMetrics(node.style.font());
            double height = node.style.number(StyleProperties.LINE_HEIGHT, Double.NaN);
            if (Double.isNaN(height))
                height = metrics.getHeight();
            for (String token : node.text.split("(?<=\\s)|(?=\\s)")) {
                if (token.equals("\n")) {
                    newline();
                    continue;
                }
                double tokenWidth = metrics.stringWidth(token);
                if (!token.isBlank() && tokenWidth <= width && x > 0 && x + tokenWidth > width)
                    newline();
                int start = 0;
                while (start < token.length()) {
                    int end = start;
                    while (end < token.length()) {
                        int next = end + Character.charCount(token.codePointAt(end));
                        double fragmentWidth = metrics.stringWidth(token.substring(start, next));
                        if (x + fragmentWidth > width && (end > start || x > 0))
                            break;
                        end = next;
                    }
                    if (end == start) {
                        newline();
                        continue;
                    }
                    String fragment = token.substring(start, end);
                    double fragmentWidth = metrics.stringWidth(fragment);
                    lines.getLast().add(new Fragment(node, fragment, x, fragmentWidth, height, metrics));
                    x += fragmentWidth;
                    start = end;
                    if (start < token.length())
                        newline();
                }
            }
            if (node.text.isEmpty())
                lines.getLast().add(new Fragment(node, "", x, 0, height, metrics));
        }

        void visit(Node node) {
            if (node instanceof TextNode textNode)
                text(textNode);
            else if (node instanceof IconNode iconNode) {
                if (x > 0 && x + iconNode.icon.getIconWidth() > width)
                    newline();
                lines.getLast().add(new Fragment(iconNode, null, x, iconNode.icon.getIconWidth(),
                        iconNode.icon.getIconHeight(), null));
                x += iconNode.icon.getIconWidth();
            } else
                node.children.forEach(this::visit);
        }
    }

    private InlineLayout() {
    }

    static double flow(SpanNode span, double width, Graphics2D graphics, boolean commit) {
        Style.Insets inset = span.style.inset();
        Flow flow = new Flow(graphics, width - inset.horizontal());
        span.children.forEach(flow::visit);
        double y = 0;
        Map<Node, List<Node.Line>> positioned = new IdentityHashMap<>();
        Map<Node, Rectangle2D.Double> bounds = new IdentityHashMap<>();
        for (List<Fragment> line : flow.lines) {
            double height = line.stream().mapToDouble(Fragment::height).max()
                    .orElse(graphics.getFontMetrics(span.style.font()).getHeight());
            for (Fragment fragment : line) {
                double x = span.bounds.x + inset.left() + fragment.x;
                double top = span.bounds.y + inset.top() + y;
                var rectangle = new Rectangle2D.Double(x, top, fragment.width, height);
                bounds.merge(fragment.node, rectangle, (left, right) -> {
                    Rectangle2D.union(left, right, left);
                    return left;
                });
                if (fragment.text != null) {
                    double baseline = top + (height - fragment.metrics.getHeight()) / 2 + fragment.metrics.getAscent();
                    positioned.computeIfAbsent(fragment.node, ignored -> new ArrayList<>())
                            .add(new Node.Line(fragment.text, x, baseline));
                }
            }
            y += height;
        }
        if (commit) {
            span.descendants().stream().filter(node -> node != span).forEach(node -> {
                if (node instanceof TextualNode textual)
                    textual.lines.clear();
                node.bounds.setRect(span.bounds.x, span.bounds.y, 0, 0);
            });
            bounds.forEach((node, rectangle) -> {
                node.bounds.setRect(rectangle);
                if (node instanceof TextualNode textual)
                    positioned.getOrDefault(node, List.of()).forEach(line -> textual.lines.add(new Node.Line(
                            line.text(), line.x() - rectangle.x, line.baseline() - rectangle.y)));
            });
            updateSpans(span);
        }
        return y + inset.vertical();
    }

    private static void updateSpans(Node root) {
        for (Node child : root.children) {
            if (!(child instanceof SpanNode span))
                continue;
            updateSpans(span);
            if (!span.children.isEmpty()) {
                span.bounds.setRect(span.children.getFirst().bounds);
                for (Node nested : span.children)
                    Rectangle2D.union(span.bounds, nested.bounds, span.bounds);
            }
        }
    }
}