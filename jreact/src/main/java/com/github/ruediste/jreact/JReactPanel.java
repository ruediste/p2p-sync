package com.github.ruediste.jreact;

import java.awt.Component;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.events.KeyboardEvent;
import com.github.ruediste.jreact.events.PointerEvent;
import com.github.ruediste.jreact.nodes.SwingNode;

/**
 * Swing host for a JReact component tree and its user interactions.
 *
 * <p>
 * Owns a {@link JReactRoot}, coalesces invalidations into scheduled refreshes,
 * supplies viewport dimensions and interaction flags, resolves styles, and
 * invokes layout and Java2D painting. Nodes are reconciled with the previous
 * tree by key, transferring interaction state, retained Swing components, and
 * the last
 * committed paint state. Each node reports its own dirty region, so the panel
 * only unions those regions into localized repaint requests.
 *
 * <p>
 * Input and select nodes own their controlled {@link javax.swing.JTextField}
 * and {@link javax.swing.JComboBox} components. The panel adds and removes
 * those components from its Swing hierarchy and dispatches focus and keyboard
 * events; value synchronization and change callbacks stay in the node.
 *
 * <p>
 * Construct, refresh, and close this panel on the Swing event dispatch thread.
 * The host must close it when its owning UI is disposed to unmount component
 * state, clean up effects, and remove native Swing components.
 */
public class JReactPanel extends JPanel implements AutoCloseable {
    private final JReactRoot root;
    private Map<Node.Key, Node> nodes = new LinkedHashMap<>();
    private Node.Key hovered, pressed, focused;
    private boolean scheduled, closed;
    private int layoutWidth = -1, layoutHeight = -1;

    public JReactPanel(Runnable component) {
        requireEdt();
        root = new JReactRoot(component, this::schedule);
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
                if (target != null && Objects.equals(previous, target.key) && !target.disabled)
                    target.dispatchEvent(new ClickEvent());
                refresh();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!isSwingComponent(event.getOppositeComponent())) {
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
        Node previousTree = root.tree();
        root.viewport(Math.max(0, getWidth()), Math.max(0, getHeight()));

        // todo: move to the JReactRoot

        // trigger render if the root is dirty or has no tree
        if (root.isDirty() || root.tree() == null)
            root.render();

        Node currentTree = root.tree();
        var reconciliation = NodeReconciler.reconcile(currentTree, previousTree);

        // update pseudo classes (hover, active, focus) for all nodes
        nodes = new LinkedHashMap<>();
        currentTree.descendants().forEach(node -> nodes.put(node.key, node));
        if (!nodes.containsKey(focused) || nodes.get(focused).disabled)
            focused = null;
        for (Node node : nodes.values()) {
            node.hover = isAncestorOf(node, nodes.get(hovered));
            node.active = Objects.equals(pressed, node.key) && !node.disabled;
            node.focus = Objects.equals(focused, node.key) && !node.disabled;
        }

        // apply styles
        Styles.resolve(currentTree);

        // calculate layout
        currentTree.layout(Math.max(0, getWidth()), Math.max(0, getHeight()));

        // reconcile nodes
        Rectangle dirty = NodeReconciler.dirtyRegion(currentTree, reconciliation);

        // todo: move to JReactRoot until here

        // todo: defer effects until after paint() has been called

        syncComponents(currentTree);

        if (layoutWidth != getWidth() || layoutHeight != getHeight()) {
            layoutWidth = getWidth();
            layoutHeight = getHeight();
            repaint();
        } else if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
        currentTree.commitPaintState();
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
            if (current.focusable || current.hasEventListener(ClickEvent.class)
                    || current.hasEventListener(PointerEvent.class))
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
        if (node.bounds.width > 0)
            node.dispatchEvent(new PointerEvent(Math.max(0, Math.min(1, (x - node.bounds.x) / node.bounds.width))));
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
                Node node = nodes.get(focused);
                if (node instanceof SwingNode swingNode && swingNode.component() != null)
                    swingNode.component().requestFocusInWindow();
            }
            event.consume();
        } else {
            Node node = nodes.get(focused);
            if (node != null && !node.disabled) {
                if ((event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_SPACE)
                        && node.hasEventListener(ClickEvent.class)) {
                    node.dispatchEvent(new ClickEvent());
                    event.consume();
                } else if (node.hasEventListener(PointerEvent.class)
                        && node.hasEventListener(KeyboardEvent.class)) {
                    node.dispatchEvent(new KeyboardEvent(event.getKeyCode()));
                    event.consume();
                }
            }
        }
        refresh();
    }

    private void syncComponents(Node tree) {
        var currentComponents = new LinkedHashSet<JComponent>();
        for (Node node : tree.descendants()) {
            if (!(node instanceof SwingNode swingNode))
                continue;
            swingNode.ensureComponent();
            JComponent component = swingNode.component();
            if (component == null)
                continue;
            currentComponents.add(component);
            if (component.getParent() != this) {
                add(component);
                attachComponentListeners(component, swingNode.key);
            }
            swingNode.syncComponent();
        }
        for (Component child : getComponents())
            if (child instanceof JComponent component && !currentComponents.contains(component))
                remove(component);
    }

    private void attachComponentListeners(JComponent component, Node.Key key) {
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_TAB) {
                    keyboard(event);
                    if (!(nodes.get(focused) instanceof SwingNode))
                        requestFocusInWindow();
                }
            }
        });
        component.addFocusListener(new FocusAdapter() {
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
    }

    private boolean isSwingComponent(Component component) {
        for (Node node : nodes.values())
            if (node instanceof SwingNode swingNode && swingNode.component() == component)
                return true;
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (root.tree() == null || root.isDirty() || layoutWidth != getWidth() || layoutHeight != getHeight())
            refresh();
        if (root.tree() != null)
            root.tree().paint((Graphics2D) graphics, 1);
    }

    @Override
    public void close() {
        requireEdt();
        if (!closed) {
            closed = true;
            try {
                root.close();
            } finally {
                Node tree = root.tree();
                if (tree != null)
                    for (Node node : tree.descendants())
                        if (node instanceof SwingNode swingNode)
                            swingNode.disposeComponent();
                nodes.clear();
                removeAll();
            }
        }
    }
}
