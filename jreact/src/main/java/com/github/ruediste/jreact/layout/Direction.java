package com.github.ruediste.jreact.layout;

/** Writing direction used for logical layout. */
public enum Direction {
    Ltr(0), Rtl(1);

    public final int value;

    Direction(int value) {
        this.value = value;
    }

    public static Direction fromValue(int value) {
        for (Direction e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
