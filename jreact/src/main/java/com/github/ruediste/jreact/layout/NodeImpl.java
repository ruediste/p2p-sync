package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;
import com.github.ruediste.jreact.Style;

/**
 * Default implementation of {@link LayoutNode}.
 *
 * <p>
 * A node owns its style, children and measurement callback, i.e. the inputs to
 * layout. The computed results live in {@link #getLayout()}; the engine only
 * computes and all persistent state lives in the nodes.
 */
public final class NodeImpl implements LayoutNode {

    private Style style;

    private MeasureFunction measureFunction;

    private NodeImpl parent;

    private final List<LayoutNode> children = new ArrayList<>();

    private final List<LayoutNode> childrenView = Collections.unmodifiableList(children);

    private final Layout layout = new Layout();

    public NodeImpl() {
        this(new Style());
    }

    public NodeImpl(Style style) {
        this.style = style == null ? new Style() : style.copy();
    }

    public NodeImpl(Style style, NodeImpl... children) {
        this(style);
        addChildren(children);
    }

    public NodeImpl(Style style, List<? extends LayoutNode> children) {
        this(style);
        addChildren(children);
    }

    @Override
    public Style getStyle() {
        return style;
    }

    /** Replaces the layout properties of this node. */
    public void setStyle(Style style) {
        this.style = style == null ? new Style() : style.copy();
    }

    @Override
    public Size<java.lang.Float> measure(Size<AvailableSpace> knownDimensions,
            Size<AvailableSpace> availableSpace) {
        return measureFunction == null ? null
                : measureFunction.measure(knownDimensions, availableSpace, this);
    }

    /** Sets the measurement callback, or {@code null} to disable measurement. */
    public void setMeasure(MeasureFunction measure) {
        this.measureFunction = measure;
    }

    @Override
    public LayoutNode getParent() {
        return parent;
    }

    @Override
    public List<LayoutNode> getChildren() {
        return childrenView;
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    /** Adds a child at the end of the child list. */
    public void addChild(NodeImpl child) {
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(child);
        children.add(child);
        child.parent = this;
    }

    /** Adds a child at the given index. */
    public void addChild(int index, NodeImpl child) {
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(child);
        int clamped = Math.max(0, Math.min(index, children.size()));
        children.add(clamped, child);
        child.parent = this;
    }

    /** Adds all given children at the end of the child list. */
    public void addChildren(NodeImpl... children) {
        for (NodeImpl child : children) {
            addChild(child);
        }
    }

    /** Adds all given children at the end of the child list. */
    public void addChildren(List<? extends LayoutNode> children) {
        for (LayoutNode child : children) {
            addChild(requireNodeImpl(child));
        }
    }

    /** Replaces all children by the given ones. */
    public void setChildren(NodeImpl... children) {
        setChildren(List.of(children));
    }

    /** Replaces all children by the given ones. */
    public void setChildren(List<? extends LayoutNode> children) {
        List<LayoutNode> newChildren = new ArrayList<>(children);
        for (LayoutNode child : new ArrayList<>(this.children)) {
            requireNodeImpl(child).parent = null;
        }
        this.children.clear();
        addChildren(newChildren);
    }

    /** Removes the given child, returning whether it was present. */
    public boolean removeChild(NodeImpl child) {
        if (!children.remove(child)) {
            return false;
        }
        child.parent = null;
        return true;
    }

    /** Removes and returns the child at the given index. */
    public NodeImpl removeChildAt(int index) {
        NodeImpl child = requireNodeImpl(children.remove(index));
        child.parent = null;
        return child;
    }

    /** Removes the children in the range {@code [startIndex, endIndex)}. */
    public void removeChildrenRange(int startIndex, int endIndex) {
        int from = Math.max(0, startIndex);
        int to = Math.min(children.size(), endIndex);
        for (int i = to - 1; i >= from; i--) {
            requireNodeImpl(children.remove(i)).parent = null;
        }
    }

    /** Replaces a child by a new one. */
    public void replaceChild(NodeImpl oldChild, NodeImpl newChild) {
        int index = children.indexOf(oldChild);
        if (index < 0) {
            throw new IllegalArgumentException("Node is not a child of this node");
        }
        replaceChildAt(index, newChild);
    }

    /** Replaces the child at the given index by a new one. */
    public void replaceChildAt(int index, NodeImpl newChild) {
        if (index < 0 || index >= children.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for node with "
                    + children.size() + " children");
        }
        if (newChild == this) {
            throw new IllegalArgumentException("A node cannot be its own child");
        }
        detach(newChild);
        NodeImpl oldChild = requireNodeImpl(children.set(index, newChild));
        oldChild.parent = null;
        newChild.parent = this;
    }

    /** Returns the number of children. */
    public int childCount() {
        return children.size();
    }

    /** Detaches this node from its parent, if it has one. */
    public void remove() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    private static NodeImpl requireNodeImpl(LayoutNode node) {
        if (node instanceof NodeImpl nodeImpl) {
            return nodeImpl;
        }
        throw new IllegalArgumentException("Children of a NodeImpl must be NodeImpl instances");
    }

    private void detach(NodeImpl child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
            child.parent = null;
        }
    }
}
