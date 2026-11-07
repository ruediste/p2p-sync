package com.github.ruediste.jreact.nodes;

import java.util.List;

import com.github.ruediste.jreact.InlineLayout;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * An inline container whose text, icons, and nested spans share a line flow.
 *
 * <p>
 * Spans do not create a separate flex container for their children. Layout
 * measures and places their inline content through {@link InlineLayout}.
 */
public final class SpanNode extends ContainerNode {
    public SpanNode(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
    }
}
