package com.github.ruediste.jreact.nodes;

import java.util.List;
import java.util.Objects;

public final class NodeImpl extends Node {

    public NodeImpl() {
    }

    public NodeImpl(Object key) {
        super(key);
    }

    public NodeImpl add(Node child) {
        add(child);
        return this;
    }

    public NodeImpl addAll(List<? extends Node> children) {
        Objects.requireNonNull(children);
        children.forEach(this::add);
        return this;
    }

    public NodeImpl remove(Node child) {
        super.remove(child);
        return this;
    }

    public NodeImpl clear() {
        clearChildren();
        return this;
    }

    public NodeImpl replaceChildren(List<? extends Node> children) {
        super.replaceChildren(children);
        return this;
    }
}
