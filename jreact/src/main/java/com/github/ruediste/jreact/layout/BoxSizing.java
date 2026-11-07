package com.github.ruediste.jreact.layout;

/** Controls whether width and height include padding and border. */
public enum BoxSizing {
    BorderBox(0), ContentBox(1);

    public final int value;

    BoxSizing(int value) {
        this.value = value;
    }

    public static BoxSizing fromValue(int value) {
        for (BoxSizing e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
