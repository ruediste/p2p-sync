package com.github.ruediste.jreact;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.antepod.taffy.style.Display;
import com.github.ruediste.jreact.nodes.InputNode;
import com.github.ruediste.jreact.nodes.Node;
import com.github.ruediste.jreact.nodes.SelectNode;
import com.github.ruediste.jreact.nodes.VisualNode;

public final class JReactPanel extends JPanel {

    private final JReactRoot root;
    private final Map<List<Node.Identity>, JComponent> editors = new LinkedHashMap<>();
    private final Map<JComponent, Node> editorNodes = new IdentityHashMap<>();

    private RefreshResult prepared;
    private boolean refreshScheduled;
    private boolean paintPending;
    private boolean passiveScheduled;
    private boolean syncingEditors;

    public JReactPanel(Runnable rootRender) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("JReactPanel must be created on the event dispatch thread");
        root = new JReactRoot(Objects.requireNonNull(rootRender));
        root.setInvalidationCallback(this::requestRefresh);
        setLayout(null);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                root.pointerPressed(event.getX(), event.getY());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                root.pointerReleased(event.getX(), event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                root.pointerExited();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                root.pointerMoved(event.getX(), event.getY());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                root.pointerMoved(event.getX(), event.getY());
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                root.keyPressed(event.getKeyCode());
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                requestRefresh();
            }
        });
    }

    public JReactRoot root() {
        return root;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestRefresh();
    }

    public void requestRefresh() {
        if (refreshScheduled)
            return;
        refreshScheduled = true;
        SwingUtilities.invokeLater(() -> {
            refreshScheduled = false;
            refreshNow();
        });
    }

    private void refreshNow() {
        if (getWidth() <= 0 || getHeight() <= 0)
            return;
        prepared = root.refresh(getWidth(), getHeight());
        synchronizeEditors(prepared.tree());
        paintPending = true;

        java.awt.geom.Rectangle2D dirty = prepared.dirtyRegion();
        if (dirty == null || dirty.getWidth() <= 0 || dirty.getHeight() <= 0) {
            repaint();
        } else {
            repaint((int) Math.floor(dirty.getX()), (int) Math.floor(dirty.getY()),
                    (int) Math.ceil(dirty.getWidth()), (int) Math.ceil(dirty.getHeight()));
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (prepared == null) {
            requestRefresh();
            return;
        }

        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintTree(prepared.tree(), copy, graphics.getClipBounds());
        } finally {
            copy.dispose();
        }

        if (paintPending) {
            paintPending = false;
            schedulePaintCompleted();
        }
    }

    private void schedulePaintCompleted() {
        if (passiveScheduled)
            return;
        passiveScheduled = true;
        SwingUtilities.invokeLater(() -> {
            passiveScheduled = false;
            root.paintCompleted();
            if (root.needsRender())
                requestRefresh();
        });
    }

    private void synchronizeEditors(Node tree) {
        Map<List<Node.Identity>, Node> found = new LinkedHashMap<>();
        collectEditors(tree, found);

        Iterator<Map.Entry<List<Node.Identity>, JComponent>> iterator = editors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<List<Node.Identity>, JComponent> entry = iterator.next();
            if (!found.containsKey(entry.getKey())) {
                JComponent editor = entry.getValue();
                editorNodes.remove(editor);
                remove(editor);
                iterator.remove();
            }
        }

        syncingEditors = true;
        try {
            List<Map.Entry<List<Node.Identity>, Node>> entries = new ArrayList<>(found.entrySet());
            for (Map.Entry<List<Node.Identity>, Node> entry : entries) {
                Node node = entry.getValue();
                JComponent editor = editors.get(entry.getKey());
                if (editor == null && node instanceof InputNode)
                    editor = createTextField();
                else if (editor == null && node instanceof SelectNode)
                    editor = createSelect();
                if (editor == null)
                    continue;
                if (editors.put(entry.getKey(), editor) == null)
                    add(editor);
                editorNodes.put(editor, node);
                synchronizeEditor(editor, node);
            }
        } finally {
            syncingEditors = false;
        }
    }

    private static void collectEditors(Node node, Map<List<Node.Identity>, Node> found) {
        if (node instanceof InputNode || node instanceof SelectNode)
            found.put(editorKey(node), node);
        for (Node child : node.children())
            collectEditors(child, found);
    }

    private static List<Node.Identity> editorKey(Node node) {
        java.util.LinkedList<Node.Identity> path = new java.util.LinkedList<>();
        for (Node current = node; current != null; current = current.parent)
            path.addFirst(current.identity());
        return path;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField();
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                textChanged(field);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                textChanged(field);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                textChanged(field);
            }
        });
        return field;
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> createSelect() {
        JComboBox<String> select = new JComboBox<>();
        select.addActionListener(event -> selectChanged(select));
        return select;
    }

    private void textChanged(JTextField field) {
        if (syncingEditors)
            return;
        Node node = editorNodes.get(field);
        if (node instanceof InputNode input) {
            String value = field.getText();
            input.value = value;
            input.onChange.accept(value);
        }
    }

    private void selectChanged(JComboBox<String> select) {
        if (syncingEditors)
            return;
        Node node = editorNodes.get(select);
        if (node instanceof SelectNode selectNode) {
            Object selected = select.getSelectedItem();
            if (selected == null)
                return;
            String value = selected.toString();
            selectNode.value = value;
            selectNode.onChange.accept(value);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void synchronizeEditor(JComponent editor, Node node) {
        if (node instanceof InputNode input && editor instanceof JTextField field) {
            if (!field.getText().equals(input.value))
                field.setText(input.value);
        } else if (node instanceof SelectNode select && editor instanceof JComboBox<?> combo) {
            boolean optionsChanged = combo.getItemCount() != select.options.size();
            if (!optionsChanged) {
                for (int index = 0; index < select.options.size(); index++) {
                    if (!Objects.equals(combo.getItemAt(index), select.options.get(index))) {
                        optionsChanged = true;
                        break;
                    }
                }
            }
            if (optionsChanged)
                ((JComboBox) combo).setModel(new DefaultComboBoxModel(select.options.toArray()));
            if (!Objects.equals(combo.getSelectedItem(), select.value))
                combo.setSelectedItem(select.value);
        }

        editor.setBounds((int) Math.round(node.bounds.x), (int) Math.round(node.bounds.y),
                (int) Math.round(node.bounds.width), (int) Math.round(node.bounds.height));
        editor.setFont(node.style.font());
        editor.setForeground(node.style.color());
        editor.setEnabled(!node.disabled);
        editor.getAccessibleContext().setAccessibleName(node.accessibleName);
    }

    private static void paintTree(Node node, Graphics2D graphics, Rectangle clip) {
        if (node instanceof VisualNode visual) {
            if (visual.style.display() == Display.NONE)
                return;
            visual.paint(graphics, clip);
        }
        for (Node child : node.children())
            paintTree(child, graphics, clip);
    }

    public void close() {
        root.close();
        for (JComponent editor : editors.values())
            remove(editor);
        editors.clear();
        editorNodes.clear();
        prepared = null;
    }
}
