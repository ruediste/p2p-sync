package com.github.ruediste.jreact.nodes;

import java.util.List;

import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * A block-level flex container.
 *
 * <p>
 * Divs lay out their children with the flex algorithm and paint their own box
 * and children. They are the default container for component output.
 */
public class DivNode extends ContainerNode {
    public DivNode(Key key, Style inline, List<Styles.NodeStyleRules> declarations) {
        super(key, inline, declarations);
    }
}
