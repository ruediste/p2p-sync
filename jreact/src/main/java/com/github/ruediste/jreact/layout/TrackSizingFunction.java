package com.github.ruediste.jreact.layout;

import java.util.Map;
import java.util.Objects;

/** A min/max pair describing one grid track. */
public final class TrackSizingFunction {

    public final MinTrackSizing min;
    public final MaxTrackSizing max;

    public TrackSizingFunction(MinTrackSizing min, MaxTrackSizing max) {
        this.min = min;
        this.max = max;
    }

    public static TrackSizingFunction of(MinTrackSizing min, MaxTrackSizing max) {
        return new TrackSizingFunction(min, max);
    }

    public static TrackSizingFunction of(Object min, Object max) {
        return new TrackSizingFunction(MinTrackSizing.of(min), MaxTrackSizing.of(max));
    }

    /** Accepts a TrackSizingFunction or a map with {@code min} and {@code max}. */
    public static TrackSizingFunction valueOf(Object value) {
        if (value instanceof TrackSizingFunction sizing) {
            return sizing;
        }
        if (value instanceof Map<?, ?> map) {
            return of(map.get("min"), map.get("max"));
        }
        throw new IllegalArgumentException("Expected a track sizing function, got: " + value);
    }

    @Override
    public String toString() {
        return "{min=" + min.kind + (min.kind == MinTrackSizing.Kind.LENGTH
                || min.kind == MinTrackSizing.Kind.PERCENT ? "(" + min.value + ")" : "")
                + ", max=" + max.kind + (max.kind == MaxTrackSizing.Kind.LENGTH
                        || max.kind == MaxTrackSizing.Kind.PERCENT
                        || max.kind == MaxTrackSizing.Kind.FLEX ? "(" + max.value + ")" : "") + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof TrackSizingFunction other)) {
            return false;
        }
        return min.kind == other.min.kind && java.lang.Float.compare(min.value, other.min.value) == 0
                && max.kind == other.max.kind && java.lang.Float.compare(max.value, other.max.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min.kind, min.value, max.kind, max.value);
    }
}
