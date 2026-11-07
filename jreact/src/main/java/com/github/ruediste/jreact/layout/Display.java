package com.github.ruediste.jreact.layout;

/** CSS {@code display} mode. */
public enum Display {
    Block(0), Flex(1), Grid(2), None(3), FlowRoot(4);

    public final int value;

    Display(int value) {
        this.value = value;
    }

    public static Display fromValue(int value) {
        for (Display e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
