package com.github.ruediste.jreact;

import com.github.ruediste.jreact.nodes.Node;

/**
 * Base class of all JReact events.
 *
 * <p>
 * Events are identified by their concrete class. Listeners are matched by the
 * exact event class, not by subclass.
 */
public class JReactEvent {
    private boolean stopPropagation;
    public Node target;

    public Node target() {
        return target;
    }

    /** Whether propagation of this event has been stopped. */
    public boolean isStopPropagation() {
        return stopPropagation;
    }

    /** Stops capture, target, and bubble phase propagation of this event. */
    public void stopPropagation() {
        stopPropagation = true;
    }
}
