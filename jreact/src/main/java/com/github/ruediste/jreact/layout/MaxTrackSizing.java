package com.github.ruediste.jreact.layout;

/** Maximum track sizing function for grid tracks. */
public final class MaxTrackSizing {

    public enum Kind {
        LENGTH, PERCENT, MIN_CONTENT, MAX_CONTENT, FIT_CONTENT, FLEX, AUTO
    }

    public final Kind kind;
    public final float value;

    private MaxTrackSizing(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static MaxTrackSizing length(float value) {
        return new MaxTrackSizing(Kind.LENGTH, value);
    }

    public static MaxTrackSizing percent(float value) {
        return new MaxTrackSizing(Kind.PERCENT, value);
    }

    public static MaxTrackSizing minContent() {
        return new MaxTrackSizing(Kind.MIN_CONTENT, 0);
    }

    public static MaxTrackSizing maxContent() {
        return new MaxTrackSizing(Kind.MAX_CONTENT, 0);
    }

    public static MaxTrackSizing fitContent() {
        return new MaxTrackSizing(Kind.FIT_CONTENT, 0);
    }

    public static MaxTrackSizing flex(float factor) {
        return new MaxTrackSizing(Kind.FLEX, factor);
    }

    public static MaxTrackSizing auto() {
        return new MaxTrackSizing(Kind.AUTO, 0);
    }

    public static MaxTrackSizing of(Object value) {
        if (value instanceof MaxTrackSizing sizing) {
            return sizing;
        }
        if (value instanceof Number number) {
            return length(number.floatValue());
        }
        if (value instanceof CharSequence text) {
            String s = text.toString().trim().toLowerCase();
            if (s.equals("auto")) {
                return auto();
            }
            if (s.equals("min-content")) {
                return minContent();
            }
            if (s.equals("max-content")) {
                return maxContent();
            }
            if (s.equals("fit-content")) {
                return fitContent();
            }
            if (s.endsWith("fr")) {
                return flex(java.lang.Float.parseFloat(s.substring(0, s.length() - 2)));
            }
            if (s.endsWith("%")) {
                return percent(java.lang.Float.parseFloat(s.substring(0, s.length() - 1)));
            }
            return length(java.lang.Float.parseFloat(s));
        }
        throw new IllegalArgumentException("Expected a maximum track sizing function, got: " + value);
    }
}
