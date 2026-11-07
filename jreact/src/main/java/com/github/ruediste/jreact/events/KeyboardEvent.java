package com.github.ruediste.jreact.events;

import com.github.ruediste.jreact.JReactEvent;

/** Fired for keyboard input on a focused node. */
public class KeyboardEvent extends JReactEvent {
    /** The AWT key code of the pressed key. */
    public final int keyCode;

    public KeyboardEvent(int keyCode) {
        this.keyCode = keyCode;
    }
}
