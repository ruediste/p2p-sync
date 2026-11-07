package com.github.ruediste.jreact.layout;

/** Controls which preceding floats a box must clear. */
public enum Clear {
    Left(0), Right(1), Both(2), None(3);

    public final int value;

    Clear(int value) {
        this.value = value;
    }

    public static Clear fromValue(int value) {
        for (Clear e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
