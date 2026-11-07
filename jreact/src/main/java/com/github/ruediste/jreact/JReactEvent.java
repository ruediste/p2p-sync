package com.github.ruediste.jreact;

/**
 * Base class of all JReact events.
 *
 * <p>
 * Events are identified by their concrete class. Listeners are matched by the
 * exact event class, not by subclass.
 */
public class JReactEvent {
    private boolean stopPropagation;

    /** Whether propagation of this event has been stopped. */
    public boolean isStopPropagation() {
        return stopPropagation;
    }

    /** Stops capture, target, and bubble phase propagation of this event. */
    public void stopPropagation() {
        stopPropagation = true;
    }
}
