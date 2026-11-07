package com.github.ruediste.jreact;

import javax.swing.JComponent;

import com.github.ruediste.jreact.nodes.EditableNode;

/**
 * Stable indirection between a retained Swing editor and the current node.
 *
 * <p>
 * Editor listeners capture the binding rather than a node instance. When the
 * editor is transferred during reconciliation, the new node updates
 * {@link #node}, so listeners always act on the current node.
 */
public final class EditorBinding {
    public EditableNode node;
    public final JComponent component;
    public final Runnable invalidate;

    public EditorBinding(EditableNode node, JComponent component, Runnable invalidate) {
        this.node = node;
        this.component = component;
        this.invalidate = invalidate;
    }

    public void dispose() {
        component.setVisible(false);
    }
}
