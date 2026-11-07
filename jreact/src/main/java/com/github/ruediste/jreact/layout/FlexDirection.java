package com.github.ruediste.jreact.layout;

/** Main-axis direction for flex layout. */
public enum FlexDirection {
    Row(0), Column(1), RowReverse(2), ColumnReverse(3);

    public final int value;

    FlexDirection(int value) {
        this.value = value;
    }

    public static FlexDirection fromValue(int value) {
        for (FlexDirection e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
