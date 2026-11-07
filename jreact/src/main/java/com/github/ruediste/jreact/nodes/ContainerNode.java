package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.util.List;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * Base class for nodes that own children and paint them.
 *
 * <p>
 * Containers are the only nodes that accept children. The flex layout
 * algorithm treats them as boxes; {@link SpanNode} additionally participates in
 * inline flow.
 */
public abstract class ContainerNode extends Node {
    protected ContainerNode(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
    }

    @Override
    public void addChild(Node child) {
        if (child == this)
            throw new IllegalArgumentException("A node cannot be its own child");
        if (child.parent != null)
            child.parent.children.remove(child);
        children.add(child);
        child.parent = this;
    }

    @Override
    protected void paintChildren(Graphics2D graphics, float opacity) {
        for (Node child : children)
            child.paint(graphics, opacity);
    }
}
