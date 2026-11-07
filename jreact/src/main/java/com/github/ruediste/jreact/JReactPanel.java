package com.github.ruediste.jreact;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Swing host for a JReact component tree and its user interactions.
 *
 * <p>
 * Owns a {@link RenderRoot}, coalesces invalidations into scheduled refreshes,
 * supplies viewport dimensions and interaction flags, resolves styles, and
 * invokes layout and Java2D painting. Node snapshots identify changed paint
 * regions, including old geometry and shadows, for localized repaint requests.
 * Mouse hit testing, keyboard traversal, activation, and tooltips connect the
 * painted tree to its declarative callbacks.
 *
 * <p>
 * Input and select nodes are backed by controlled {@link JTextField} and
 * {@link JComboBox} children. Their identities follow node keys;
 * synchronization
 * updates native values without feeding those updates back into change
 * callbacks. Native editors are overlaid on the canvas, not painted primitives.
 *
 * <p>
 * Construct, refresh, and close this panel on the Swing event dispatch thread.
 * The host must close it when its owning UI is disposed to unmount component
 * state, clean up effects, and remove native editors.
 */
public class JReactPanel extends JPanel implements AutoCloseable {
    private final RenderRoot root;
    private final Map<Node.Key, JComponent> editors = new LinkedHashMap<>();
    private Map<Node.Key, Node> nodes = new LinkedHashMap<>();
    private Node.Key hovered, pressed, focused;
    private boolean scheduled, syncing, closed;
    private int layoutWidth = -1, layoutHeight = -1;

    public JReactPanel(Runnable component) {
        requireEdt();
        root = new RenderRoot(component, this::schedule);
        setLayout(null);
        setFocusable(true);
        setToolTipText("");
        setFocusTraversalKeysEnabled(false);
        setBackground(java.awt.Color.WHITE);
        setPreferredSize(new Dimension(960, 720));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                schedule();
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hover(event.getX(), event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = null;
                refresh();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event))
                    return;
                Node target = interactive(hitTest(event.getX(), event.getY()));
                pressed = target == null ? null : target.key;
                if (target != null && !target.disabled) {
                    focused = target.key;
                    requestFocusInWindow();
                    pointer(target, event.getX());
                }
                refresh();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                Node target = nodes.get(pressed);
                if (target != null && !target.disabled)
                    pointer(target, event.getX());
                hover(event.getX(), event.getY());
                refresh();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event))
                    return;
                Node target = interactive(hitTest(event.getX(), event.getY()));
                Node.Key previous = pressed;
                pressed = null;
                if (target != null && Objects.equals(previous, target.key) && !target.disabled
                        && target.onClick != null)
                    target.onClick.run();
                refresh();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!editors.containsValue(event.getOppositeComponent())) {
                    focused = null;
                    pressed = null;
                    refresh();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                keyboard(event);
            }
        });
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("JReactPanel must be used on the Swing event dispatch thread");
    }

    public Node tree() {
        return root.tree();
    }

    private void schedule() {
        if (!scheduled && !closed) {
            scheduled = true;
            SwingUtilities.invokeLater(() -> {
                scheduled = false;
                if (!closed)
                    refresh();
            });
        }
    }

    public void refresh() {
        requireEdt();
        if (closed)
            return;
        Map<Node.Key, Snapshot> previous = snapshots();
        root.viewport(Math.max(0, getWidth()), Math.max(0, getHeight()));
        if (root.isDirty() || root.tree() == null)
            root.render();
        nodes = new LinkedHashMap<>();
        root.tree().descendants().forEach(node -> nodes.put(node.key, node));
        if (!nodes.containsKey(focused) || nodes.get(focused).disabled)
            focused = null;
        for (Node node : nodes.values()) {
            node.hover = isAncestorOf(node, nodes.get(hovered));
            node.active = Objects.equals(pressed, node.key) && !node.disabled;
            node.focus = Objects.equals(focused, node.key) && !node.disabled;
        }
        Styles.resolve(root.tree());
        var metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            FlexLayout.layout(root.tree(), Math.max(0, getWidth()), Math.max(0, getHeight()), metrics);
        } finally {
            metrics.dispose();
        }
        syncEditors();
        if (layoutWidth != getWidth() || layoutHeight != getHeight()) {
            layoutWidth = getWidth();
            layoutHeight = getHeight();
            repaint();
        } else {
            Map<Node.Key, Snapshot> current = snapshots();
            Rectangle dirty = null;
            for (var entry : previous.entrySet())
                if (!entry.getValue().equals(current.get(entry.getKey())))
                    dirty = union(dirty, entry.getValue().paintBounds);
            for (var entry : current.entrySet())
                if (!entry.getValue().equals(previous.get(entry.getKey())))
                    dirty = union(dirty, entry.getValue().paintBounds);
            if (dirty != null)
                repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    /** Captures paint-relevant node state for comparing consecutive refreshes. */
    private record Snapshot(String text, Map<StyleProperties.Property<?>, Object> style, Rectangle2D bounds,
            boolean focus, Node.Kind kind, Object icon, List<String> options, Double slider, float opacity,
            Rectangle paintBounds) {
    }

    private Map<Node.Key, Snapshot> snapshots() {
        var result = new LinkedHashMap<Node.Key, Snapshot>();
        nodes.forEach((key, node) -> {
            Rectangle rectangle = node.bounds.getBounds();
            for (var property : List.of(StyleProperties.BOX_SHADOW, StyleProperties.TEXT_SHADOW)) {
                Style.Shadow shadow = node.style.get(property, null);
                if (shadow != null) {
                    Rectangle shadowBounds = node.bounds.getBounds();
                    shadowBounds.grow((int) Math.ceil(shadow.blur() * 2 + 2), (int) Math.ceil(shadow.blur() * 2 + 2));
                    shadowBounds.translate((int) Math.floor(shadow.offsetX()), (int) Math.floor(shadow.offsetY()));
                    rectangle = rectangle.union(shadowBounds);
                }
            }
            result.put(key, new Snapshot(node.text, Map.copyOf(node.style.values), (Rectangle2D) node.bounds.clone(),
                    node.focus, node.kind, node.icon, node.options, node.sliderFraction, opacity(node), rectangle));
        });
        return result;
    }

    private static Rectangle union(Rectangle left, Rectangle right) {
        return left == null ? right : left.union(right);
    }

    private static float opacity(Node node) {
        float opacity = 1;
        for (Node ancestor = node; ancestor != null; ancestor = ancestor.parent)
            opacity *= (float) ancestor.style.number(StyleProperties.OPACITY, 1);
        return opacity;
    }

    private static boolean isAncestorOf(Node ancestor, Node node) {
        for (Node current = node; current != null; current = current.parent)
            if (current == ancestor)
                return true;
        return false;
    }

    public Node hitTest(double x, double y) {
        requireEdt();
        var ordered = new ArrayList<>(nodes.values());
        for (int index = ordered.size() - 1; index >= 0; index--) {
            Node node = ordered.get(index);
            if (node.bounds.contains(x, y))
                return node;
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Node target = interactive(hitTest(event.getX(), event.getY()));
        return target == null ? null : target.accessibleName;
    }

    private Node interactive(Node node) {
        for (Node current = node; current != null; current = current.parent)
            if (current.focusable || current.onClick != null || current.onPointer != null)
                return current;
        return null;
    }

    private void hover(double x, double y) {
        Node hit = hitTest(x, y);
        Node.Key key = hit == null ? null : hit.key;
        if (!Objects.equals(hovered, key)) {
            hovered = key;
            refresh();
        }
        Node target = interactive(hit);
        setCursor(Cursor
                .getPredefinedCursor(target != null && !target.disabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void pointer(Node node, double x) {
        if (node.onPointer != null && node.bounds.width > 0)
            node.onPointer.accept(Math.max(0, Math.min(1, (x - node.bounds.x) / node.bounds.width)));
    }

    private void keyboard(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_TAB) {
            List<Node> targets = nodes.values().stream().filter(node -> node.focusable && !node.disabled).toList();
            if (!targets.isEmpty()) {
                int current = -1;
                for (int index = 0; index < targets.size(); index++)
                    if (targets.get(index).key.equals(focused))
                        current = index;
                int next = current < 0 && event.isShiftDown() ? targets.size() - 1
                        : Math.floorMod(current + (event.isShiftDown() ? -1 : 1), targets.size());
                focused = targets.get(next).key;
                JComponent editor = editors.get(focused);
                if (editor != null)
                    editor.requestFocusInWindow();
            }
            event.consume();
        } else {
            Node node = nodes.get(focused);
            if (node != null && !node.disabled) {
                if ((event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_SPACE)
                        && node.onClick != null) {
                    node.onClick.run();
                    event.consume();
                } else if (node.onPointer != null && node.onKey != null) {
                    node.onKey.accept(event.getKeyCode());
                    event.consume();
                }
            }
        }
        refresh();
    }

    private void syncEditors() {
        syncing = true;
        try {
            editors.entrySet().removeIf(entry -> {
                Node node = nodes.get(entry.getKey());
                if (node != null && (node.kind == Node.Kind.INPUT || node.kind == Node.Kind.SELECT)
                        && (node.kind == Node.Kind.INPUT) == (entry.getValue() instanceof JTextField))
                    return false;
                remove(entry.getValue());
                return true;
            });
            for (Node node : nodes.values()) {
                if (node.kind != Node.Kind.INPUT && node.kind != Node.Kind.SELECT)
                    continue;
                JComponent field = editors.computeIfAbsent(node.key, this::createEditor);
                if (field instanceof JTextField textField) {
                    if (!textField.getText().equals(node.text))
                        textField.setText(node.text);
                } else if (field instanceof JComboBox<?> combo) {
                    var options = new ArrayList<String>();
                    for (int index = 0; index < combo.getItemCount(); index++)
                        options.add((String) combo.getItemAt(index));
                    if (!options.equals(node.options)) {
                        @SuppressWarnings("unchecked")
                        var stringCombo = (JComboBox<String>) combo;
                        stringCombo
                                .setModel(new javax.swing.DefaultComboBoxModel<>(node.options.toArray(String[]::new)));
                    }
                    combo.setSelectedItem(node.text);
                }
                field.setEnabled(!node.disabled);
                field.setFont(node.style.font());
                field.setForeground(node.style.color());
                field.setBackground(node.style.get(StyleProperties.BACKGROUND, java.awt.Color.WHITE));
                Style.Insets inset = node.style.inset();
                field.setBorder(BorderFactory.createEmptyBorder((int) inset.top(), (int) inset.left(),
                        (int) inset.bottom(), (int) inset.right()));
                field.setBounds(node.bounds.getBounds());
                field.doLayout();
                field.getAccessibleContext().setAccessibleName(node.accessibleName);
            }
        } finally {
            syncing = false;
        }
    }

    private JComponent createEditor(Node.Key key) {
        JComponent field;
        if (nodes.get(key).kind == Node.Kind.SELECT) {
            var combo = new JComboBox<String>() {
                @Override
                public void paint(Graphics graphics) {
                    paintEditor(this, graphics, () -> super.paint(graphics));
                }
            };
            combo.addActionListener(event -> {
                Node node = nodes.get(key);
                if (!syncing && node != null && !node.disabled && combo.getSelectedItem() != null) {
                    node.onChange.accept((String) combo.getSelectedItem());
                    schedule();
                }
            });
            field = combo;
        } else
            field = new JTextField() {
                @Override
                public void paint(Graphics graphics) {
                    paintEditor(this, graphics, () -> super.paint(graphics));
                }
            };
        field.setOpaque(false);
        field.setFocusTraversalKeysEnabled(false);
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_TAB) {
                    keyboard(event);
                    if (!editors.containsKey(focused))
                        requestFocusInWindow();
                }
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                focused = key;
                schedule();
            }

            @Override
            public void focusLost(FocusEvent event) {
                if (Objects.equals(focused, key))
                    focused = null;
                schedule();
            }
        });
        if (field instanceof JTextField textField)
            textField.getDocument().addDocumentListener(new DocumentListener() {
                private void change() {
                    Node node = nodes.get(key);
                    if (!syncing && node != null && !node.disabled) {
                        node.onChange.accept(textField.getText());
                        schedule();
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
        add(field);
        return field;
    }

    private void paintEditor(JComponent editor, Graphics graphics, Runnable paint) {
        Graphics2D graphics2d = (Graphics2D) graphics;
        var previous = graphics2d.getComposite();
        for (var entry : editors.entrySet())
            if (entry.getValue() == editor) {
                Node node = nodes.get(entry.getKey());
                if (node != null)
                    graphics2d.setComposite(java.awt.AlphaComposite.SrcOver.derive(opacity(node)));
                break;
            }
        try {
            paint.run();
        } finally {
            graphics2d.setComposite(previous);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (root.tree() == null || root.isDirty() || layoutWidth != getWidth() || layoutHeight != getHeight())
            refresh();
        if (root.tree() != null)
            Renderer.paint(root.tree(), (Graphics2D) graphics);
    }

    @Override
    public void close() {
        requireEdt();
        if (!closed) {
            closed = true;
            try {
                root.close();
            } finally {
                editors.clear();
                nodes.clear();
                removeAll();
            }
        }
    }
}