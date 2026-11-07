package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A CSS length, percentage, or {@code auto}. */
public final class LengthPercentageAuto {

    public enum Kind {
        LENGTH, PERCENT, AUTO
    }

    public final Kind kind;
    public final float value;

    private LengthPercentageAuto(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static LengthPercentageAuto length(float value) {
        return new LengthPercentageAuto(Kind.LENGTH, value);
    }

    public static LengthPercentageAuto percent(float percent) {
        return new LengthPercentageAuto(Kind.PERCENT, percent);
    }

    public static LengthPercentageAuto auto() {
        return new LengthPercentageAuto(Kind.AUTO, 0);
    }

    public static LengthPercentageAuto zero() {
        return length(0);
    }

    public static LengthPercentageAuto of(Object value) {
        if (value instanceof LengthPercentageAuto lpa) {
            return lpa;
        }
        if (value instanceof LengthPercentage lp) {
            return lp.isPercent() ? percent(lp.value) : length(lp.value);
        }
        if (value instanceof Number n) {
            return length(n.floatValue());
        }
        if (value instanceof CharSequence s) {
            String text = s.toString().trim();
            if (text.equalsIgnoreCase("auto")) {
                return auto();
            }
            if (text.endsWith("%")) {
                return percent(java.lang.Float.parseFloat(text.substring(0, text.length() - 1)));
            }
            return length(java.lang.Float.parseFloat(text));
        }
        throw new IllegalArgumentException("Expected a length, percentage, or auto, got: " + value);
    }

    public boolean isAuto() {
        return kind == Kind.AUTO;
    }

    public boolean isPercent() {
        return kind == Kind.PERCENT;
    }

    public float resolve(float basis) {
        return switch (kind) {
            case LENGTH -> value;
            case PERCENT -> basis * value / 100f;
            case AUTO -> java.lang.Float.NaN;
        };
    }

    public float resolveOr(float basis, float fallback) {
        float resolved = resolve(basis);
        return java.lang.Float.isNaN(resolved) ? fallback : resolved;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case LENGTH -> java.lang.Float.toString(value);
            case PERCENT -> value + "%";
            case AUTO -> "auto";
        };
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof LengthPercentageAuto other)) {
            return false;
        }
        return kind == other.kind && java.lang.Float.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
