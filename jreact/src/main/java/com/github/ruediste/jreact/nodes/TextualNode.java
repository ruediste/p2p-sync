package com.github.ruediste.jreact.nodes;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * Base class for nodes that carry text and measured text lines.
 *
 * <p>
 * Textual nodes are measured by {@link TextMetrics}. {@link TextNode} paints
 * the lines; {@link EditableNode} subclasses delegate painting to a native
 * Swing editor while still using the measured lines for sizing.
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
