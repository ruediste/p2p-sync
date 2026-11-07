package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.util.List;

import com.github.ruediste.jreact.PaintUtils;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;

/**
 * A standalone text node that paints its measured lines.
 *
 * <p>
 * Text is wrapped by {@link TextMetrics}; this class only renders the committed
 * lines, including an optional text shadow.
 */
public final class TextNode extends TextualNode {
    public TextNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, text, inline, declarations);
    }

    @Override
    protected void paintContent(Graphics2D graphics) {
        Style.Shadow textShadow = style.get(StyleProperties.TEXT_SHADOW, null);
        if (textShadow != null)
            PaintUtils.shadow(this, graphics, textShadow, this::paintText);
        graphics.setColor(style.color());
        paintText(graphics);
    }

    private void paintText(Graphics2D graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.clip(bounds);
            copy.setFont(style.font());
            for (Line line : lines)
                copy.drawString(line.text(), (float) (bounds.x + line.x()), (float) (bounds.y + line.baseline()));
        } finally {
            copy.dispose();
        }
    }
}
