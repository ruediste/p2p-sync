package com.github.ruediste.p2psync.clock;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.github.ruediste.p2psync.proto.Sync;

public final class VectorClock {

    private Map<Integer, Long> values;

    private VectorClock() {
        this(new HashMap<>());
    }

    private VectorClock(Map<Integer, Long> values) {
        this.values = values;
    }

    public static VectorClock empty() {
        return new VectorClock();
    }

    public static VectorClock of(Sync.VectorClock clock) {
        return new VectorClock(new HashMap<>(clock.getValuesMap()));
    }

    /** Counter of the given {@code nodeNr}, 0 if absent. */
    public long get(int nr) {
        return values.getOrDefault(nr, 0L);
    }

    /** Returns a clock with the counter of {@code nr} incremented by one. */
    public void increment(int nr) {
        values.merge(nr, 1L, (old, increment) -> old + increment);
    }

    /** Node-wise maximum of this clock and {@code other}. */
    public void merge(VectorClock other) {
        other.values.forEach(
                (key, value) -> values.merge(key, value, (ownValue, otherValue) -> Math.max(ownValue, otherValue)));
    }

    /**
     * Relation between this clock and {@code other}: dominance is checked
     * component-wise with missing entries treated as 0.
     */
    public ClockRelation compare(VectorClock other) {
        boolean anyLess = false;
        boolean anyGreater = false;
        var keys = new HashSet<>(values.keySet());
        keys.addAll(other.values.keySet());

        for (var key : keys) {
            var ownValue = values.getOrDefault(key, 0L);
            var otherValue = other.values.getOrDefault(key, 0L);
            if (ownValue < otherValue)
                anyLess = true;
            if (ownValue > otherValue)
                anyGreater = true;
        }
        if (anyLess && anyGreater) {
            return ClockRelation.CONCURRENT;
        }
        if (anyLess) {
            return ClockRelation.BEFORE;
        }
        if (anyGreater) {
            return ClockRelation.AFTER;
        }
        return ClockRelation.EQUAL;
    }

    public boolean isBefore(VectorClock other) {
        return compare(other) == ClockRelation.BEFORE;
    }

    public boolean isAfter(VectorClock other) {
        return compare(other) == ClockRelation.AFTER;
    }

    public boolean isConcurrentWith(VectorClock other) {
        return compare(other) == ClockRelation.CONCURRENT;
    }

    /**
     * Remaps node numbers: each entry {@code nr -> counter} is emitted under
     * {@code mapping.get(nr)}; entries without a mapping are left unchanged.
     */
    public void remap(Map<Integer, Integer> mapping) {
        var newValues = new HashMap<Integer, Long>();
        values.entrySet().forEach(x -> newValues.put(mapping.getOrDefault(x.getKey(), x.getKey()), x.getValue()));
        this.values = newValues;
    }

    @Override
    public String toString() {
        return String.join(", ",
                values.entrySet().stream().sorted(Comparator.comparing(x -> x.getKey()))
                        .map(x -> x.getKey() + ":" + x.getValue()).toList());
    }

    public static VectorClock from(Sync.VectorClock proto) {
        return new VectorClock(new HashMap<>(proto.getValuesMap()));
    }

    public Sync.VectorClock toProto() {
        return Sync.VectorClock.newBuilder().putAllValues(values).build();
    }

    public VectorClock clone() {
        return new VectorClock(new HashMap<>(values));
    }
}
