package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.util.Objects;

import javax.swing.Icon;

public class IconNode extends VisualNode {

    public Icon icon;

    public IconNode(Icon icon) {
        this(null, icon);
    }

    public IconNode(Object key, Icon icon) {
        super(key);
        this.icon = Objects.requireNonNull(icon);
    }

    @Override
    protected void paintContent(Graphics2D graphics) {
        icon.paintIcon(null, graphics, (int) Math.round(bounds.x), (int) Math.round(bounds.y));
    }
}
