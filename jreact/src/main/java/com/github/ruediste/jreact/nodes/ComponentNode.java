package com.github.ruediste.jreact.nodes;

import java.util.Objects;
import java.util.Optional;

import com.github.ruediste.jreact.Fiber;

public abstract class ComponentNode extends Node {

    public final Object identityType;
    public Fiber fiber;

    public ComponentNode(Object identityType) {
        this.identityType = Objects.requireNonNull(identityType);
    }

    @Override
    public Object identityType() {
        return identityType;
    }

    @Override
    public void reconcile(Optional<Node> oldNode) {
        fiber = oldNode.map(o -> ((ComponentNode) o).fiber).orElseGet(Fiber::new);
        fiber.component = this;
    }

    public abstract void render();

    public abstract boolean sameProperties(ComponentNode other);
}
