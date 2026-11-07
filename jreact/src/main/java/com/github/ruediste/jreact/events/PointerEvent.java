package com.github.ruediste.jreact.events;

import com.github.ruediste.jreact.JReactEvent;

/** Fired for pointer input on a node, for example a slider drag. */
public class PointerEvent extends JReactEvent {
    /** Relative position within the node's bounds, clamped to {@code 0..1}. */
    public final double position;

    public PointerEvent(double position) {
        this.position = position;
    }
}
