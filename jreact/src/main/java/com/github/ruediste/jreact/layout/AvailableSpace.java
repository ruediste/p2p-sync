package com.github.ruediste.jreact.layout;

/** Space available to a layout call: definite pixels, min-content or max-content. */
public final class AvailableSpace {

    public enum Kind {
        DEFINITE, MIN_CONTENT, MAX_CONTENT
    }

    public final Kind kind;
    public final float value;

    private AvailableSpace(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static AvailableSpace definite(float value) {
        return new AvailableSpace(Kind.DEFINITE, value);
    }

    public static AvailableSpace minContent() {
        return new AvailableSpace(Kind.MIN_CONTENT, 0);
    }

    public static AvailableSpace maxContent() {
        return new AvailableSpace(Kind.MAX_CONTENT, 0);
    }

    public static AvailableSpace of(Object value) {
        if (value instanceof AvailableSpace space) {
            return space;
        }
        if (value instanceof Number number) {
            return definite(number.floatValue());
        }
        if (value instanceof CharSequence text) {
            String s = text.toString().trim();
            if (s.equalsIgnoreCase("min-content")) {
                return minContent();
            }
            if (s.equalsIgnoreCase("max-content")) {
                return maxContent();
            }
            return definite(java.lang.Float.parseFloat(s));
        }
        throw new IllegalArgumentException("Expected available space, got: " + value);
    }

    public boolean isDefinite() {
        return kind == Kind.DEFINITE;
    }

    public float definiteOr(float fallback) {
        return kind == Kind.DEFINITE ? value : fallback;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case DEFINITE -> java.lang.Float.toString(value);
            case MIN_CONTENT -> "min-content";
            case MAX_CONTENT -> "max-content";
        };
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof AvailableSpace other)) {
            return false;
        }
        return kind == other.kind && java.lang.Float.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, value);
    }
}
