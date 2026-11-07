package com.github.ruediste.jreact.nodes;

import java.util.List;

import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.Display;
import com.github.ruediste.jreact.layout.FlexDirection;
import com.github.ruediste.jreact.layout.FlexWrap;

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
            style.display(Display.Flex);
        if (!StyleProperties.FLEX_DIRECTION.isSet(style))
            style.layoutFlexDirection(FlexDirection.Row);
        if (!StyleProperties.FLEX_WRAP.isSet(style))
            style.layoutFlexWrap(FlexWrap.Wrap);
        if (!StyleProperties.ALIGN_ITEMS.isSet(style))
            style.layoutAlignItems(AlignItems.Center);
    }
}
