package com.github.ruediste.jreact.layout;

/** CSS {@code position} mode. */
public enum Position {
    Relative(0), Absolute(1), Static(2), Fixed(3), Sticky(4);

    public final int value;

    Position(int value) {
        this.value = value;
    }

    public static Position fromValue(int value) {
        for (Position e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
