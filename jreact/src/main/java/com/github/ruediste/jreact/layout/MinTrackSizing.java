package com.github.ruediste.jreact.layout;

/** Minimum track sizing function for grid tracks. */
public final class MinTrackSizing {

    public enum Kind {
        LENGTH, PERCENT, MIN_CONTENT, MAX_CONTENT, AUTO
    }

    public final Kind kind;
    public final float value;

    private MinTrackSizing(Kind kind, float value) {
        this.kind = kind;
        this.value = value;
    }

    public static MinTrackSizing length(float value) {
        return new MinTrackSizing(Kind.LENGTH, value);
    }

    public static MinTrackSizing percent(float value) {
        return new MinTrackSizing(Kind.PERCENT, value);
    }

    public static MinTrackSizing minContent() {
        return new MinTrackSizing(Kind.MIN_CONTENT, 0);
    }

    public static MinTrackSizing maxContent() {
        return new MinTrackSizing(Kind.MAX_CONTENT, 0);
    }

    public static MinTrackSizing auto() {
        return new MinTrackSizing(Kind.AUTO, 0);
    }

    public static MinTrackSizing of(Object value) {
        if (value instanceof MinTrackSizing sizing) {
            return sizing;
        }
        if (value instanceof Number number) {
            return length(number.floatValue());
        }
        if (value instanceof CharSequence text) {
            String s = text.toString().trim().toLowerCase();
            return switch (s) {
                case "auto" -> auto();
                case "min-content" -> minContent();
                case "max-content" -> maxContent();
                default -> s.endsWith("%")
                        ? percent(java.lang.Float.parseFloat(s.substring(0, s.length() - 1)))
                        : length(java.lang.Float.parseFloat(s));
            };
        }
        throw new IllegalArgumentException("Expected a minimum track sizing function, got: " + value);
    }
}
