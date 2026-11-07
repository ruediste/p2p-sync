package com.github.ruediste.jreact.nodes;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;

import com.github.ruediste.jreact.EditorBinding;
import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;

/**
 * Base class for textual nodes backed by a native Swing editor.
 *
 * <p>
 * The node owns the editor component and its value listeners. During
 * reconciliation the editor is transferred from the previous node with the same
 * key, so caret, selection, and focus survive re-renders. The panel only adds
 * and removes the component from its Swing hierarchy and dispatches focus and
 * keyboard events.
 */
public abstract class EditableNode extends TextualNode {
    public Consumer<String> onChange;

    private final Runnable invalidate;
    private EditorBinding binding;
    private boolean syncing;

    protected EditableNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations,
            Runnable invalidate) {
        super(key, text, inline, declarations);
        this.invalidate = invalidate;
    }

    @Override
    public void reconcile(Node previous) {
        super.reconcile(previous);
        if (previous instanceof EditableNode old && old != this && old.binding != null
                && isCompatibleEditor(old.binding.component)) {
            binding = old.binding;
            binding.node = this;
            old.binding = null;
        }
    }

    /** The retained editor component, or {@code null} before it is created. */
    public JComponent editor() {
        return binding == null ? null : binding.component;
    }

    /** Creates the editor if this node does not have one yet. */
    public void ensureEditor() {
        if (binding == null)
            binding = createBinding(invalidate);
    }

    protected abstract EditorBinding createBinding(Runnable invalidate);

    protected abstract boolean isCompatibleEditor(JComponent editor);

    /** Pushes the node's current value, style, bounds, and accessibility state. */
    public abstract void syncEditor();

    /** Releases the editor. The panel removes it from the Swing hierarchy. */
    public void disposeEditor() {
        if (binding != null) {
            binding.dispose();
            binding = null;
        }
    }

    protected boolean isSyncing() {
        return syncing;
    }

    protected void setSyncing(boolean value) {
        syncing = value;
    }

    protected void applyCommonEditorStyle(JComponent field) {
        field.setEnabled(!disabled);
        field.setFont(style.font());
        field.setForeground(style.color());
        field.setBackground(style.get(StyleProperties.BACKGROUND, Color.WHITE));
        field.setBorder(BorderFactory.createEmptyBorder(
                (int) (layout.border.top + layout.padding.top),
                (int) (layout.border.left + layout.padding.left),
                (int) (layout.border.bottom + layout.padding.bottom),
                (int) (layout.border.right + layout.padding.right)));
        field.setBounds(bounds.getBounds());
        field.doLayout();
        field.getAccessibleContext().setAccessibleName(accessibleName);
    }
}
