package com.github.ruediste.jreact.nodes;

import java.util.Objects;

import com.github.ruediste.jreact.Fiber;

public final class ComponentNode extends Node {

    public final Object identityType;
    public Object properties;
    public Runnable render;
    public Fiber fiber;

    public ComponentNode(Object identityType) {
        this(identityType, null);
    }

    public ComponentNode(Object identityType, Object key) {
        super(key);
        this.identityType = Objects.requireNonNull(identityType);
    }

    @Override
    public Object identityType() {
        return identityType;
    }
}
