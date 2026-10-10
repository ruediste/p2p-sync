package com.github.ruediste.p2psync.clock;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import com.github.ruediste.p2psync.proto.Sync;

public final class VectorClock {
    private static final AtomicLong nextTag = new AtomicLong();


    private Map<Integer, Long> values;

    public VectorClock() {
        this.values = new TreeMap<>();
    }

    public static VectorClock empty() {
        return new VectorClock();
    }
    
    private VectorClock(VectorClock other) {
        this.values = new TreeMap<>(other.values);
    }

    /** Counter of the given {@code nodeNr}, 0 if absent. */
    public long get(int nr) {
        return Optional.ofNullable(values.get(nr)).orElse(0L);
    }

    public void reset(int nr) {
        values.put(nr, 0L);
    }

    /** Returns a clock with the counter of {@code nr} incremented by one. */
    public void increment(int nr) {
        values.merge(nr, 1L, Long::sum);
    }

    /**
     * Node-wise maximum of this clock and {@code other}. On tag mismatches, a new
     * tag is created.
     */
    public void merge(VectorClock other) {
        other.values.forEach(
                (otherKey, otherClock) -> {
                    var ownClock = values.get(otherKey);
                    if (ownClock == null) {
                        values.put(otherKey, otherClock);
                    } else {
                        values.put(otherKey, Math.max(ownClock, otherClock));
                    }
                });
    }

    /**
     * Relation between this clock and {@code other}: dominance is checked
     * component-wise with missing entries treated as 0. If tags differ, the entries
     * are considered concurrent.
     */
    public ClockRelation compare(VectorClock other) {
        boolean anyLess = false;
        boolean anyGreater = false;

        var ownIterator = values.entrySet().iterator();
        var otherIterator = other.values.entrySet().iterator();
        var ownEntry = ownIterator.hasNext() ? ownIterator.next() : null;
        var otherEntry = otherIterator.hasNext() ? otherIterator.next() : null;

        while (ownEntry != null || otherEntry != null) {
            int ownKey = ownEntry != null ? ownEntry.getKey() : Integer.MAX_VALUE;
            int otherKey = otherEntry != null ? otherEntry.getKey() : Integer.MAX_VALUE;

            Long ownValue = null;
            Long otherValue = null;

            if (otherEntry == null || (ownEntry != null && ownKey < otherKey)) {
                ownValue = ownEntry.getValue();
                ownEntry = ownIterator.hasNext() ? ownIterator.next() : null;
            } else if (ownEntry == null || otherKey < ownKey) {
                otherValue = otherEntry.getValue();
                otherEntry = otherIterator.hasNext() ? otherIterator.next() : null;
            } else {
                ownValue = ownEntry.getValue();
                otherValue = otherEntry.getValue();
                ownEntry = ownIterator.hasNext() ? ownIterator.next() : null;
                otherEntry = otherIterator.hasNext() ? otherIterator.next() : null;
            }

            if (ownValue == null && otherValue != null)
                anyLess = true;
            if (ownValue != null && otherValue == null) {
                anyGreater = true;
            } else {
                if (ownValue < otherValue)
                    anyLess = true;
                if (ownValue > otherValue)
                    anyGreater = true;
            }

            if (anyLess && anyGreater)
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

    public boolean isBeforeOrEqual(VectorClock other) {
        var relation = compare(other);
        return relation == ClockRelation.BEFORE || relation == ClockRelation.EQUAL;
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
        var newValues = new TreeMap<Integer, Long>();
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
        throw new UnsupportedOperationException("from(Sync.VectorClock) not implemented yet");
    }

    public Sync.VectorClock toProto() {
        throw new UnsupportedOperationException("toProto() not implemented yet");
    }

    public VectorClock clone() {
        return new VectorClock(this);
    }
}
