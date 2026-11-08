package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.PaintUtils;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.TextLayout;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;

/**
 * A standalone text node that measures, wraps, and paints its text.
 *
 * <p>
 * The node reports its content size through the layout engine's measurement
 * callback. After layout allocates a content width, the wrapped lines are
 * committed for painting, including an optional text shadow.
 */
public final class TextNode extends Node {
    public final String text;
    public final List<Line> lines = new ArrayList<>();

    public TextNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
        this.text = text;
    }

    @Override
    public String text() {
        return text;
    }

    @Override
    public Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace) {
        return TextLayout.measure(text, style, knownDimensions.width, availableSpace.width, 0);
    }

    @Override
    protected void layoutApplied() {
        TextLayout.commit(this);
    }

    @Override
    protected PaintState paintState() {
        return new TextPaintState(basePaintState(), text, List.copyOf(lines));
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

    protected record TextPaintState(PaintState base, String text, List<Line> lines) implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }
}
