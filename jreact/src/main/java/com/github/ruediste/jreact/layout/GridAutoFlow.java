package com.github.ruediste.jreact.layout;

/** Controls row-wise or column-wise grid auto placement. */
public enum GridAutoFlow {
    Row(0), Column(1), RowDense(2), ColumnDense(3);

    public final int value;

    GridAutoFlow(int value) {
        this.value = value;
    }

    public static GridAutoFlow fromValue(int value) {
        for (GridAutoFlow e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
