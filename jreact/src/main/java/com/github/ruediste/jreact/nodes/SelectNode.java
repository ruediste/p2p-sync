package com.github.ruediste.jreact.nodes;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;

import com.github.ruediste.jreact.EditorBinding;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * A selection control backed by a retained {@link JComboBox}.
 *
 * <p>
 * The combo is controlled: selection changes call {@link #onChange} and
 * invalidate the render root, while {@link #syncEditor()} pushes the node's
 * value and options back into the combo without feedback.
 */
public final class SelectNode extends EditableNode {
    public final List<String> options;

    public SelectNode(Key key, String text, List<String> options, Style inline,
            List<Styles.NodeStyleRules> declarations, Runnable invalidate) {
        super(key, text, inline, declarations, invalidate);
        this.options = List.copyOf(options);
        focusable = true;
    }

    @Override
    protected EditorBinding createBinding(Runnable invalidate) {
        var combo = new SelectCombo();
        var binding = new EditorBinding(this, combo, invalidate);
        combo.binding = binding;
        combo.addActionListener(event -> {
            if (!binding.node.isSyncing() && !binding.node.disabled && combo.getSelectedItem() != null) {
                binding.node.onChange.accept((String) combo.getSelectedItem());
                binding.invalidate.run();
            }
        });
        return binding;
    }

    @Override
    protected boolean isCompatibleEditor(JComponent editor) {
        return editor instanceof JComboBox;
    }

    @Override
    public void syncEditor() {
        var combo = (JComboBox<?>) editor();
        setSyncing(true);
        try {
            var current = new ArrayList<String>();
            for (int index = 0; index < combo.getItemCount(); index++)
                current.add((String) combo.getItemAt(index));
            if (!current.equals(options)) {
                @SuppressWarnings("unchecked")
                var stringCombo = (JComboBox<String>) combo;
                stringCombo.setModel(new DefaultComboBoxModel<>(options.toArray(String[]::new)));
            }
            combo.setSelectedItem(text);
            applyCommonEditorStyle(combo);
        } finally {
            setSyncing(false);
        }
    }

    @Override
    protected PaintState paintState() {
        return new SelectPaintState(basePaintState(), text, List.copyOf(lines), options);
    }

    private record SelectPaintState(PaintState base, String text, List<Line> lines, List<String> options)
            implements PaintState {
        @Override
        public Rectangle paintBounds() {
            return base.paintBounds();
        }
    }

    private static final class SelectCombo extends JComboBox<String> {
        EditorBinding binding;

        @Override
        public void paint(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics;
            var previous = copy.getComposite();
            copy.setComposite(AlphaComposite.SrcOver.derive(binding.node.effectiveOpacity()));
            try {
                super.paint(graphics);
            } finally {
                copy.setComposite(previous);
            }
        }
    }
}
