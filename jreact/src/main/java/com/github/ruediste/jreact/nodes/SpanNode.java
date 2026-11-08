package com.github.ruediste.jreact.nodes;

import java.util.List;

import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;
import com.antepod.taffy.style.AlignItems;
import com.antepod.taffy.style.Display;
import com.antepod.taffy.style.FlexDirection;
import com.antepod.taffy.style.FlexWrap;

/**
 * An inline container whose text, icons, and nested spans share a line flow.
 *
 * <p>
 * A span lays out its children as a wrapping row, so inline content can flow
 * across multiple lines. It uses the layout engine like any other container;
 * there is no separate inline layout pass.
 */
public final class SpanNode extends ContainerNode {
    public SpanNode(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
    }

    @Override
    public void applyLayoutDefaults() {
        if (!StyleProperties.DISPLAY.isSet(style))
            style.display(Display.FLEX);
        if (!StyleProperties.FLEX_DIRECTION.isSet(style))
            style.flexDirection(FlexDirection.ROW);
        if (!StyleProperties.FLEX_WRAP.isSet(style))
            style.flexWrap(FlexWrap.WRAP);
        if (!StyleProperties.ALIGN_ITEMS.isSet(style))
            style.alignItems(AlignItems.CENTER);
    }
}
