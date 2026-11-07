package com.github.ruediste.jreact.layout;

/** Main-axis alignment and distribution. */
public enum JustifyContent {
    Start(0), End(1), FlexStart(2), FlexEnd(3), Center(4), Stretch(5), SpaceBetween(6),
    SpaceAround(7), SpaceEvenly(8), SafeStart(9), SafeEnd(10), SafeFlexStart(11),
    SafeFlexEnd(12), SafeCenter(13);

    public final int value;

    JustifyContent(int value) {
        this.value = value;
    }

    public static JustifyContent fromValue(int value) {
        for (JustifyContent e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
