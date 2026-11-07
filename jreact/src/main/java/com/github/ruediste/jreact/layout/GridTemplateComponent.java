package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One entry in a grid template: a single track or a repetition. */
public final class GridTemplateComponent {

    public enum Repetition {
        NONE, AUTO_FILL, AUTO_FIT
    }

    public final TrackSizingFunction track;
    public final Repetition repetition;
    public final int repeatCount;
    public final List<TrackSizingFunction> tracks;
    public final List<List<String>> lineNames;

    private GridTemplateComponent(TrackSizingFunction track, Repetition repetition, int repeatCount,
            List<TrackSizingFunction> tracks, List<List<String>> lineNames) {
        this.track = track;
        this.repetition = repetition;
        this.repeatCount = repeatCount;
        this.tracks = tracks;
        this.lineNames = lineNames;
    }

    public static GridTemplateComponent single(TrackSizingFunction track) {
        return new GridTemplateComponent(track, Repetition.NONE, 0, List.of(), List.of());
    }

    public static GridTemplateComponent repeat(int count, List<TrackSizingFunction> tracks) {
        return new GridTemplateComponent(null, Repetition.NONE, count, List.copyOf(tracks), List.of());
    }

    public static GridTemplateComponent repeat(String count, List<TrackSizingFunction> tracks) {
        Repetition repetition = count.equalsIgnoreCase("auto-fit") ? Repetition.AUTO_FIT
                : Repetition.AUTO_FILL;
        return new GridTemplateComponent(null, repetition, 0, List.copyOf(tracks), List.of());
    }

    public static GridTemplateComponent repeat(int count, List<TrackSizingFunction> tracks,
            List<List<String>> lineNames) {
        return new GridTemplateComponent(null, Repetition.NONE, count, List.copyOf(tracks),
                List.copyOf(lineNames));
    }

    public static GridTemplateComponent valueOf(Object value) {
        if (value instanceof GridTemplateComponent component) {
            return component;
        }
        if (value instanceof TrackSizingFunction track) {
            return single(track);
        }
        if (value instanceof Map<?, ?> map) {
            Object count = map.get("count");
            @SuppressWarnings("unchecked")
            List<Object> rawTracks = new ArrayList<>((List<Object>) map.get("tracks"));
            List<TrackSizingFunction> tracks = new ArrayList<>();
            for (Object raw : rawTracks) {
                tracks.add(TrackSizingFunction.valueOf(raw));
            }
            @SuppressWarnings("unchecked")
            List<List<String>> names = map.get("lineNames") == null ? List.of()
                    : (List<List<String>>) map.get("lineNames");
            if (count instanceof Number number) {
                return repeat(number.intValue(), tracks, names);
            }
            return repeat(String.valueOf(count), tracks);
        }
        throw new IllegalArgumentException("Expected a grid template component, got: " + value);
    }

    public boolean isSingle() {
        return repetition == Repetition.NONE && repeatCount == 0 && track != null;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof GridTemplateComponent other)) {
            return false;
        }
        return repetition == other.repetition && repeatCount == other.repeatCount
                && java.util.Objects.equals(track, other.track) && tracks.equals(other.tracks)
                && lineNames.equals(other.lineNames);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(track, repetition, repeatCount, tracks, lineNames);
    }

    @Override
    public String toString() {
        return isSingle() ? track.toString()
                : "{repetition=" + repetition + ", count=" + repeatCount + ", tracks=" + tracks + "}";
    }
}
