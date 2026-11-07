package com.github.ruediste.jreact;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.jreact.layout.AvailableSpace;
import com.github.ruediste.jreact.layout.Direction;
import com.github.ruediste.jreact.layout.Display;
import com.github.ruediste.jreact.layout.Layout;
import com.github.ruediste.jreact.layout.Size;
import com.github.ruediste.jreact.layout.TextAlign;
import com.github.ruediste.jreact.nodes.InputNode;
import com.github.ruediste.jreact.nodes.SelectNode;
import com.github.ruediste.jreact.nodes.TextualNode;

/**
 * Measures and wraps text for {@link TextualNode}.
 *
 * <p>
 * Text nodes participate in layout through the measurement callback of the
 * layout engine: {@link #measure} reports the content-box size for a given
 * available width, and {@link #commit} records the final wrapped lines after
 * layout has allocated a content width. The committed {@link Node.Line}s are
 * node-local and are consumed by painting and by native editor synchronization.
 */
public final class TextLayout {
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, false, false);

    private TextLayout() {
    }

    public static Size<java.lang.Float> measure(TextualNode node, AvailableSpace knownWidth,
            AvailableSpace availableWidth) {
        Font font = node.style.font();
        String text = node.text;
        float natural = naturalWidth(text, font);
        float width;
        if (knownWidth.isDefinite()) {
            width = knownWidth.value;
        } else if (availableWidth.isDefinite()) {
            width = Math.min(natural, availableWidth.value);
        } else if (availableWidth.kind == AvailableSpace.Kind.MIN_CONTENT) {
            width = longestWordWidth(text, font);
        } else {
            width = natural;
        }
        if (!knownWidth.isDefinite()
                && (node instanceof InputNode || node instanceof SelectNode)) {
            width = Math.max(120, width);
        }
        width = Math.max(0, width);
        float height = wrap(text, font, width).size() * lineHeight(node, font);
        return Size.of(width, height);
    }

    public static void commit(TextualNode node) {
        node.lines.clear();
        if (node.style.display() == Display.None) {
            return;
        }
        Layout layout = node.getLayout();
        Font font = node.style.font();
        float contentWidth = Math.max(0, layout.usedContentWidth);
        List<String> lines = wrap(node.text, font, contentWidth);
        float lineHeight = lineHeight(node, font);
        LineMetrics metrics = font.getLineMetrics("Hg", RENDER_CONTEXT);
        float baseline = layout.border.top + layout.padding.top
                + (lineHeight - metrics.getHeight()) / 2 + metrics.getAscent();
        TextAlign align = node.style.layoutTextAlign();
        boolean rtl = node.style.layoutDirection() == Direction.Rtl;
        for (String line : lines) {
            float offset = switch (align) {
                case LegacyLeft -> 0;
                case LegacyCenter -> (contentWidth - textWidth(line, font)) / 2;
                case LegacyRight -> contentWidth - textWidth(line, font);
                case Auto -> rtl ? contentWidth - textWidth(line, font) : 0;
            };
            node.lines.add(new Node.Line(line,
                    layout.border.left + layout.padding.left + Math.max(0, offset), baseline));
            baseline += lineHeight;
        }
    }

    private static float lineHeight(TextualNode node, Font font) {
        double configured = node.style.lineHeight();
        return Double.isNaN(configured) ? font.getLineMetrics("Hg", RENDER_CONTEXT).getHeight()
                : (float) configured;
    }

    private static float naturalWidth(String text, Font font) {
        float width = 0;
        for (String paragraph : text.split("\n", -1))
            width = Math.max(width, textWidth(paragraph, font));
        return width;
    }

    private static float longestWordWidth(String text, Font font) {
        float width = 0;
        for (String paragraph : text.split("\n", -1))
            for (String word : paragraph.split("\\s+"))
                width = Math.max(width, textWidth(word, font));
        return width;
    }

    private static float textWidth(String text, Font font) {
        return (float) font.getStringBounds(text, RENDER_CONTEXT).getWidth();
    }

    private static List<String> wrap(String text, Font font, float width) {
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
                    if (textWidth(paragraph.substring(start, next), font) > width && end > start)
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
}
