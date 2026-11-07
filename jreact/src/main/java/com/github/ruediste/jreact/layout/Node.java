package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of a layout tree.
 *
 * <p>
 * A node owns its style, context, children, measurement callback and the layout
 * information computed for it. The engine only computes; all persistent state
 * lives in the nodes.
 */
public final class Node {

    public Style style;
    public Object context;
    public MeasureFunction measure;

    public Node parent;
    public final List<Node> children = new ArrayList<>();
    public final Layout layout = new Layout();
    public DetailedGridInfo detailedGridInfo;

    public Node containingBlock;
    public float absX;
    public float absY;
    public boolean placedOutOfFlow;
    public float usedContentWidth;
    public float usedContentHeight;

    public Node() {
        this(new Style());
    }

    public Node(Style style) {
        this.style = style == null ? new Style() : style.copy();
    }

    public Node(Style style, Node... children) {
        this(style);
        addChildren(children);
    }

    public Node(Style style, List<Node> children) {
        this(style);
        addChildren(children);
    }

    public static Node withContext(Style style, Object context) {
        Node node = new Node(style);
        node.context = context;
        return node;
    }

    public void addChild(Node child) {
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(child);
        children.add(child);
        child.parent = this;
    }

    public void addChild(int index, Node child) {
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(child);
        int clamped = Math.max(0, Math.min(index, children.size()));
        children.add(clamped, child);
        child.parent = this;
    }

    public void addChildren(Node... children) {
        for (Node child : children) {
            addChild(child);
        }
    }

    public void addChildren(List<Node> children) {
        for (Node child : children) {
            addChild(child);
        }
    }

    public void setChildren(Node... children) {
        setChildren(List.of(children));
    }

    public void setChildren(List<Node> children) {
        List<Node> newChildren = new ArrayList<>(children);
        for (Node child : new ArrayList<>(this.children)) {
            child.parent = null;
        }
        this.children.clear();
        addChildren(newChildren);
    }

    public boolean removeChild(Node child) {
        if (!children.remove(child)) {
            return false;
        }
        child.parent = null;
        return true;
    }

    public Node removeChildAt(int index) {
        Node child = children.remove(index);
        child.parent = null;
        return child;
    }

    public void removeChildrenRange(int startIndex, int endIndex) {
        int from = Math.max(0, startIndex);
        int to = Math.min(children.size(), endIndex);
        for (int i = to - 1; i >= from; i--) {
            children.remove(i).parent = null;
        }
    }

    public void replaceChild(Node oldChild, Node newChild) {
        int index = children.indexOf(oldChild);
        if (index < 0) {
            throw new IllegalArgumentException("Node is not a child of this node");
        }
        replaceChildAt(index, newChild);
    }

    public void replaceChildAt(int index, Node newChild) {
        if (index < 0 || index >= children.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for node with "
                    + children.size() + " children");
        }
        if (newChild == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(newChild);
        Node oldChild = children.set(index, newChild);
        oldChild.parent = null;
        newChild.parent = this;
    }

    public int indexOfChild(Node child) {
        return children.indexOf(child);
    }

    public int childCount() {
        return children.size();
    }

    public Node childAt(int index) {
        return children.get(index);
    }

    /** Detaches this node from its parent, if it has one. */
    public void remove() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    public void computeLayout(float width, float height) {
        LayoutEngine.computeLayout(this, width, height);
    }

    public void computeLayout(Size<AvailableSpace> availableSpace) {
        LayoutEngine.computeLayout(this, availableSpace);
    }

    public String printTree() {
        return LayoutEngine.printTree(this);
    }

    private void detach(Node child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
            child.parent = null;
        }
    }
}
