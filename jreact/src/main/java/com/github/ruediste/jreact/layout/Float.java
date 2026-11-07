package com.github.ruediste.jreact.layout;

/** Controls whether a block-level box floats to one side. */
public enum Float {
    Left(0), Right(1), None(2);

    public final int value;

    Float(int value) {
        this.value = value;
    }

    public static Float fromValue(int value) {
        for (Float e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
