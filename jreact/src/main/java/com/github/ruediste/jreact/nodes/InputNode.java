package com.github.ruediste.jreact.nodes;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.github.ruediste.jreact.SwingBinding;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;

/**
 * A single-line text input backed by a retained {@link JTextField}.
 *
 * <p>
 * The field is controlled: document changes call {@link #onChange} and
 * invalidate the render root, while {@link #syncComponent()} pushes the node's
 * value back into the field without feedback. The field paints itself with the
 * node's effective opacity.
 */
public final class InputNode extends SwingNode {
    public InputNode(Key key, String text, Style inline, List<Styles.NodeStyleRules> declarations,
            Runnable invalidate) {
        super(key, text, inline, declarations, invalidate);
        focusable = true;
    }

    @Override
    protected SwingBinding createBinding(Runnable invalidate) {
        var field = new InputField();
        var binding = new SwingBinding(this, field, invalidate);
        field.binding = binding;
        field.setOpaque(false);
        field.setFocusTraversalKeysEnabled(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void change() {
                if (!binding.node.isSyncing() && !binding.node.disabled) {
                    binding.node.onChange.accept(field.getText());
                    binding.invalidate.run();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                change();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                change();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                change();
            }
        });
        return binding;
    }

    @Override
    protected boolean isCompatibleComponent(JComponent component) {
        return component instanceof JTextField;
    }

    @Override
    public void syncComponent() {
        var field = (JTextField) component();
        setSyncing(true);
        try {
            if (!field.getText().equals(text))
                field.setText(text);
            applyCommonComponentStyle(field);
        } finally {
            setSyncing(false);
        }
    }

    @Override
    protected boolean paintsFocusOutline() {
        return false;
    }

    private static final class InputField extends JTextField {
        SwingBinding binding;

        @Override
        public void paint(Graphics graphics) {
            Graphics2D graphics2d = (Graphics2D) graphics;
            var previous = graphics2d.getComposite();
            graphics2d.setComposite(AlphaComposite.SrcOver.derive(binding.node.effectiveOpacity()));
            try {
                super.paint(graphics);
            } finally {
                graphics2d.setComposite(previous);
            }
        }
    }
}
