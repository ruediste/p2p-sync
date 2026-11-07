package com.github.ruediste.jreact.layout;

/** Cross-axis wrapping mode for flex layout. */
public enum FlexWrap {
    NoWrap(0), Wrap(1), WrapReverse(2);

    public final int value;

    FlexWrap(int value) {
        this.value = value;
    }

    public static FlexWrap fromValue(int value) {
        for (FlexWrap e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
