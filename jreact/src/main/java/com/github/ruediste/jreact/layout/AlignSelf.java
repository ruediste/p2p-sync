package com.github.ruediste.jreact.layout;

/** Cross-axis alignment for a single item. */
public enum AlignSelf {
    Auto(0), Start(1), End(2), FlexStart(3), FlexEnd(4), Center(5), Baseline(6), Stretch(7),
    SelfStart(8), SelfEnd(9), SafeStart(10), SafeEnd(11), SafeFlexStart(12), SafeFlexEnd(13),
    SafeCenter(14), SafeSelfStart(15), SafeSelfEnd(16);

    public final int value;

    AlignSelf(int value) {
        this.value = value;
    }

    public static AlignSelf fromValue(int value) {
        for (AlignSelf e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
