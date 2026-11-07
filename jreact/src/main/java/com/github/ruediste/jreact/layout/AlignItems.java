package com.github.ruediste.jreact.layout;

/** Cross-axis alignment for all items. */
public enum AlignItems {
    Start(0), End(1), FlexStart(2), FlexEnd(3), Center(4), Baseline(5), Stretch(6),
    SelfStart(7), SelfEnd(8), SafeStart(9), SafeEnd(10), SafeFlexStart(11), SafeFlexEnd(12),
    SafeCenter(13), SafeSelfStart(14), SafeSelfEnd(15);

    public final int value;

    AlignItems(int value) {
        this.value = value;
    }

    public static AlignItems fromValue(int value) {
        for (AlignItems e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
