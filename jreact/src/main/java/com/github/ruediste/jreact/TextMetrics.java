package com.github.ruediste.jreact;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures and positions a standalone text node using Java font metrics.
 *
 * <p>
 * Wraps paragraphs to the available content width, preserves explicit blank
 * lines, and breaks oversized words at Unicode code-point boundaries. Resolves
 * font, content insets, line height, and horizontal alignment from the node's
 * style. Measurement can return a height without changing the node, or commit
 * node-local text fragments and baselines for {@link Renderer}.
 *
 * <p>
 * This helper does not paint text or share a line across sibling nodes;
 * {@link InlineLayout} handles shared inline flow inside spans.
 */
final class TextMetrics {
    private TextMetrics() {
    }

    static List<String> wrap(String text, FontMetrics metrics, double width) {
        var result = new ArrayList<String>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int end = start;
                int breakAt = -1;
                while (end < paragraph.length()) {
                    int next = end + Character.charCount(paragraph.codePointAt(end));
                    if (metrics.stringWidth(paragraph.substring(start, next)) > width && end > start)
                        break;
                    end = next;
                    if (Character.isWhitespace(paragraph.codePointBefore(end)))
                        breakAt = end;
                }
                if (end < paragraph.length() && breakAt > start)
                    end = breakAt;
                result.add(paragraph.substring(start, end));
                start = end;
            }
        }
        return result;
    }

    static double measure(Node node, Graphics2D graphics, double width, boolean commit) {
        FontMetrics metrics = graphics.getFontMetrics(node.style.font());
        Style.Insets inset = node.style.inset();
        double available = Math.max(0, width - inset.horizontal());
        List<String> lines = wrap(node.text, metrics, available);
        double lineHeight = node.style.number(StyleProperties.LINE_HEIGHT, Double.NaN);
        if (Double.isNaN(lineHeight))
            lineHeight = metrics.getHeight();
        if (commit) {
            node.lines.clear();
            double baseline = inset.top() + (lineHeight - metrics.getHeight()) / 2 + metrics.getAscent();
            for (String line : lines) {
                double spare = available - metrics.stringWidth(line);
                double offset = switch (node.style.get(StyleProperties.TEXT_ALIGN, Style.TextAlign.LEFT)) {
                    case LEFT -> 0;
                    case CENTER -> spare / 2;
                    case RIGHT -> spare;
                };
                node.lines.add(new Node.Line(line, inset.left() + offset, baseline));
                baseline += lineHeight;
            }
        }
        return lines.size() * lineHeight + inset.vertical();
    }
}