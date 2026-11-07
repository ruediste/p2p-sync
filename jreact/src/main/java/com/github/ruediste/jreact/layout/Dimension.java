package com.github.ruediste.jreact.layout;

import java.util.Objects;

/**
 * A CSS dimension: a pixel length, a percentage, or {@code auto}.
 *
 * <p>
 * Percentages are stored as percentages (for example {@code 50} for {@code 50%})
 * and resolved against a basis size when layout is performed.
 */
public final class Dimension {

    public enum Kind {
        LENGTH, PERCENT, AUTO
    }

    public final Kind kind;
    public final float value;

    private Dimension(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static Dimension length(float value) {
        return new Dimension(Kind.LENGTH, value);
    }

    public static Dimension percent(float percent) {
        return new Dimension(Kind.PERCENT, percent);
    }

    public static Dimension auto() {
        return new Dimension(Kind.AUTO, 0);
    }

    public static Dimension of(Object value) {
        if (value instanceof Dimension d) {
            return d;
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
        throw new IllegalArgumentException("Expected a dimension, got: " + value);
    }

    public boolean isAuto() {
        return kind == Kind.AUTO;
    }

    public boolean isLength() {
        return kind == Kind.LENGTH;
    }

    public boolean isPercent() {
        return kind == Kind.PERCENT;
    }

    /** Resolves this dimension against a definite basis, returning NaN for auto. */
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
        if (!(object instanceof Dimension other)) {
            return false;
        }
        return kind == other.kind && java.lang.Float.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
