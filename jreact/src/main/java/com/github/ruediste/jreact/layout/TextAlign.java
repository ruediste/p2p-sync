package com.github.ruediste.jreact.layout;

/** Legacy text alignment used by block layout. */
public enum TextAlign {
    Auto(0), LegacyLeft(1), LegacyRight(2), LegacyCenter(3);

    public final int value;

    TextAlign(int value) {
        this.value = value;
    }

    public static TextAlign fromValue(int value) {
        for (TextAlign e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
