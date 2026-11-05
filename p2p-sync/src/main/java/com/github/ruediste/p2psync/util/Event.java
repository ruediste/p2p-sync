package com.github.ruediste.p2psync.util;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

public class Event<T> {

    private Set<Consumer<T>> listeners = new java.util.HashSet<>();

    public synchronized void addListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(Consumer<T> listener) {
        listeners.remove(listener);
    }

    public void trigger(T event) {
        ArrayList<Consumer<T>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(listeners);
        }
        for (Consumer<T> listener : snapshot) {
            listener.accept(event);
        }
    }
}
