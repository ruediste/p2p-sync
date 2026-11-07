package com.github.ruediste.jreact.nodes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.List;

import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * A div that paints a slider thumb above its children.
 *
 * <p>
 * The track and fill are ordinary child nodes; this subclass adds the
 * fraction/color decoration and paints the thumb in the overlay phase.
 */
public final class SliderNode extends DivNode {
    public double fraction;
    public Color color;

    public SliderNode(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
    }

    @Override
    protected void paintOverlay(Graphics2D graphics) {
        double centerX = bounds.x + 8 + fraction * Math.max(0, bounds.width - 16);
        double centerY = bounds.getCenterY();
        var thumb = new Ellipse2D.Double(centerX - 8, centerY - 8, 16, 16);
        graphics.setColor(color);
        graphics.fill(thumb);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(2));
        graphics.draw(thumb);
    }

    @Override
    protected PaintState paintState() {
        return new SliderPaintState(basePaintState(), fraction, color);
    }

    private record SliderPaintState(PaintState base, double fraction, Color color) implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }
}
