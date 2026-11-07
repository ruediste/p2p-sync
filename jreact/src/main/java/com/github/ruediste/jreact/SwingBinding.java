package com.github.ruediste.jreact;

import javax.swing.JComponent;

import com.github.ruediste.jreact.nodes.SwingNode;

/**
 * Stable indirection between a retained Swing component and the current node.
 *
 * <p>
 * Component listeners capture the binding rather than a node instance. When the
 * component is transferred during reconciliation, the new node updates
 * {@link #node}, so listeners always act on the current node.
 */
public final class SwingBinding {
    public SwingNode node;
    public final JComponent component;
    public final Runnable invalidate;

    public SwingBinding(SwingNode node, JComponent component, Runnable invalidate) {
        this.node = node;
        this.component = component;
        this.invalidate = invalidate;
    }

    public void dispose() {
        component.setVisible(false);
    }
}
