package com.github.ruediste.jreact.nodes;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.TextLayout;
import com.github.ruediste.jreact.layout.AvailableSpace;
import com.github.ruediste.jreact.layout.Size;

/**
 * Base class for nodes that carry text and measured text lines.
 *
 * <p>
 * Textual nodes report their content size through the layout engine's
 * measurement callback. After layout allocates a content width, the wrapped
 * lines are committed for painting. {@link TextNode} paints the lines;
 * {@link EditableNode} subclasses delegate painting to a native Swing editor
 * while still using the measured lines for sizing.
 */
public abstract class TextualNode extends Node {
    public final String text;
    public final List<Line> lines = new ArrayList<>();

    protected TextualNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations) {
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
        return TextLayout.measure(this, knownDimensions.width, availableSpace.width);
    }

    @Override
    protected void layoutApplied() {
        TextLayout.commit(this);
    }

    @Override
    protected PaintState paintState() {
        return new TextPaintState(basePaintState(), text, List.copyOf(lines));
    }

    protected record TextPaintState(PaintState base, String text, List<Line> lines) implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }
}
