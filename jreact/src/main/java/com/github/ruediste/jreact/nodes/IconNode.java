package com.github.ruediste.jreact.nodes;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.Icon;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;

/**
 * A leaf node that paints a Swing icon.
 *
 * <p>
 * The icon's preferred size contributes to layout; the icon is painted inside
 * the node's content box.
 */
public final class IconNode extends Node {
    public final Icon icon;

    public IconNode(Key key, Icon icon, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
        this.icon = icon;
    }

    @Override
    public Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace) {
        return new Size<>((float) icon.getIconWidth(), (float) icon.getIconHeight());
    }

    @Override
    protected void paintContent(Graphics2D graphics) {
        icon.paintIcon(null, graphics,
                (int) (bounds.x + layout.border.left + layout.padding.left),
                (int) (bounds.y + layout.border.top + layout.padding.top));
    }

    @Override
    protected PaintState paintState() {
        return new IconPaintState(basePaintState(), icon);
    }

    private record IconPaintState(PaintState base, Icon icon) implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }
}
