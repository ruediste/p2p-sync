package com.github.ruediste.jreact;

/**
 * A listener for events of a specific concrete class.
 *
 * <p>
 * Listeners are matched against the exact event class, not against subclasses.
 *
 * @param <T> the event type this listener handles
 */
@FunctionalInterface
public interface EventListener<T extends JReactEvent> {
    /**
     * Handles the given event. Call {@link JReactEvent#stopPropagation()} on
     * the event to prevent further capture, target, or bubble phase delivery.
     */
    void handle(T event);
}
