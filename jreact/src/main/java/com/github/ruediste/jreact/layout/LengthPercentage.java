package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A CSS length or percentage (no {@code auto}). */
public final class LengthPercentage {

    public enum Kind {
        LENGTH, PERCENT
    }

    public final Kind kind;
    public final float value;

    private LengthPercentage(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static LengthPercentage length(float value) {
        return new LengthPercentage(Kind.LENGTH, value);
    }

    public static LengthPercentage percent(float percent) {
        return new LengthPercentage(Kind.PERCENT, percent);
    }

    public static LengthPercentage zero() {
        return length(0);
    }

    public static LengthPercentage of(Object value) {
        if (value instanceof LengthPercentage lp) {
            return lp;
        }
        if (value instanceof Number n) {
            return length(n.floatValue());
        }
        if (value instanceof CharSequence s) {
            String text = s.toString().trim();
            if (text.endsWith("%")) {
                return percent(java.lang.Float.parseFloat(text.substring(0, text.length() - 1)));
            }
            return length(java.lang.Float.parseFloat(text));
        }
        throw new IllegalArgumentException("Expected a length or percentage, got: " + value);
    }

    public boolean isPercent() {
        return kind == Kind.PERCENT;
    }

    public float resolve(float basis) {
        return kind == Kind.LENGTH ? value : basis * value / 100f;
    }

    @Override
    public String toString() {
        return kind == Kind.LENGTH ? java.lang.Float.toString(value) : value + "%";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof LengthPercentage other)) {
            return false;
        }
        return kind == other.kind && java.lang.Float.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
