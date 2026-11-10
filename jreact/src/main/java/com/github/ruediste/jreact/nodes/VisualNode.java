package com.github.ruediste.jreact.nodes;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import com.antepod.taffy.style.Display;
import com.github.ruediste.jreact.PaintUtils;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;

public class VisualNode extends Node {

    public VisualNode() {
    }

    public VisualNode(Object key) {
        super(key);
    }

    public Rectangle2D.Double paintBounds() {
        return new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public void paint(Graphics2D graphics, Rectangle clip) {
        if (style.display() == Display.NONE)
            return;
        if (clip != null && !bounds.intersects(clip))
            return;

        Composite previousComposite = graphics.getComposite();
        double opacity = style.number(StyleProperties.OPACITY, 1);
        if (opacity < 1) {
            float alpha = (float) Math.max(0, Math.min(1, opacity));
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        try {
            Shape shape = PaintUtils.shape(this);
            Style.Shadow shadow = style.get(StyleProperties.BOX_SHADOW, null);
            if (shadow != null)
                PaintUtils.shadow(this, graphics, shadow, layer -> layer.fill(shape));

            Color background = style.get(StyleProperties.BACKGROUND, null);
            if (background != null) {
                graphics.setColor(background);
                graphics.fill(shape);
            }

            PaintUtils.paintBorder(this, graphics, shape);
            paintContent(graphics);
        } finally {
            graphics.setComposite(previousComposite);
        }
    }

    protected void paintContent(Graphics2D graphics) {
    }
}
