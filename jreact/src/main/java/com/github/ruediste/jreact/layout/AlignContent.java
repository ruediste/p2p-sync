package com.github.ruediste.jreact.layout;

/** Distribution of wrapped flex lines or grid tracks. */
public enum AlignContent {
    Start(0), End(1), FlexStart(2), FlexEnd(3), Center(4), Stretch(5), SpaceBetween(6),
    SpaceAround(7), SpaceEvenly(8), SafeStart(9), SafeEnd(10), SafeFlexStart(11),
    SafeFlexEnd(12), SafeCenter(13);

    public final int value;

    AlignContent(int value) {
        this.value = value;
    }

    public static AlignContent fromValue(int value) {
        for (AlignContent e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
