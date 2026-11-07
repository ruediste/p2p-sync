package com.github.ruediste.jreact.nodes;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.StyleProperties;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.SwingBinding;
import com.github.ruediste.jreact.TextLayout;
import com.github.ruediste.jreact.layout.AvailableSpace;
import com.github.ruediste.jreact.layout.Size;

/**
 * Base class for nodes backed by a retained native Swing component.
 *
 * <p>
 * The node owns the Swing component and its value listeners. During
 * reconciliation the component is transferred from the previous node with the
 * same key, so caret, selection, and focus survive re-renders. The panel only
 * adds and removes the component from its Swing hierarchy and dispatches focus
 * and keyboard events.
 *
 * <p>
 * The component paints itself; this node only measures the text to reserve
 * layout space and exposes the text for repaint detection.
 */
public abstract class SwingNode extends Node {
    public final String text;
    public Consumer<String> onChange;

    private final Runnable invalidate;
    private SwingBinding binding;
    private boolean syncing;

    protected SwingNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations,
            Runnable invalidate) {
        super(key, inline, declarations);
        this.text = text;
        this.invalidate = invalidate;
    }

    @Override
    public String text() {
        return text;
    }

    @Override
    public Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace) {
        return TextLayout.measure(text, style, knownDimensions.width, availableSpace.width, minimumMeasuredWidth());
    }

    /** Least width reserved by the layout engine for a Swing-backed node. */
    protected float minimumMeasuredWidth() {
        return 120;
    }

    @Override
    public void reconcile(Node previous) {
        super.reconcile(previous);
        if (previous instanceof SwingNode old && old != this && old.binding != null
                && isCompatibleComponent(old.binding.component)) {
            binding = old.binding;
            binding.node = this;
            old.binding = null;
        }
    }

    @Override
    protected PaintState paintState() {
        return new SwingPaintState(basePaintState(), text);
    }

    /** The retained Swing component, or {@code null} before it is created. */
    public JComponent component() {
        return binding == null ? null : binding.component;
    }

    /** Creates the Swing component if this node does not have one yet. */
    public void ensureComponent() {
        if (binding == null)
            binding = createBinding(invalidate);
    }

    protected abstract SwingBinding createBinding(Runnable invalidate);

    protected abstract boolean isCompatibleComponent(JComponent component);

    /** Pushes the node's current value, style, bounds, and accessibility state. */
    public abstract void syncComponent();

    /** Releases the Swing component. The panel removes it from the Swing hierarchy. */
    public void disposeComponent() {
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

    protected void applyCommonComponentStyle(JComponent field) {
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

    protected record SwingPaintState(PaintState base, String text) implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }
}
