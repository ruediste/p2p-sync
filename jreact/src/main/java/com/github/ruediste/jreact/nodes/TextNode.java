package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.util.Objects;

import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;

public class TextNode extends VisualNode {

    public String text;

    public TextNode(String text) {
        this(null, text);
    }

    public TextNode(Object key, String text) {
        super(key);
        this.text = Objects.requireNonNull(text);
    }

    @Override
    protected void paintContent(Graphics2D graphics) {
        graphics.setFont(style.font());
        Style.Shadow shadow = style.get(StyleProperties.TEXT_SHADOW, null);
        for (Line line : lines) {
            float x = (float) bounds.x + line.x();
            float y = (float) bounds.y + line.baseline();
            if (shadow != null) {
                graphics.setColor(shadow.color());
                graphics.drawString(line.text(), x + (float) shadow.offsetX(), y + (float) shadow.offsetY());
            }
            graphics.setColor(style.color());
            graphics.drawString(line.text(), x, y);
        }
    }
}
