package com.github.ruediste.jreact.layout;

/** Overflow behavior for one axis. */
public enum Overflow {
    Visible(0), Clip(1), Hidden(2), Scroll(3);

    public final int value;

    Overflow(int value) {
        this.value = value;
    }

    public static Overflow fromValue(int value) {
        for (Overflow e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }

    public static Overflow of(Object value) {
        if (value instanceof Overflow overflow) {
            return overflow;
        }
        if (value instanceof Number number) {
            Overflow overflow = fromValue(number.intValue());
            return overflow == null ? Visible : overflow;
        }
        if (value instanceof CharSequence text) {
            return switch (text.toString().trim().toLowerCase()) {
                case "clip" -> Clip;
                case "hidden" -> Hidden;
                case "scroll" -> Scroll;
                default -> Visible;
            };
        }
        return Visible;
    }
}
