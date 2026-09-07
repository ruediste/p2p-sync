package com.github.ruediste.p2psync.clock;

/**
 * Relation between two vector clocks, see wiki section "Conflict Resolution":
 * {@code =}, {@code <}, {@code >}, {@code ||}.
 */
public enum ClockRelation {
    EQUAL, BEFORE, AFTER, CONCURRENT
}
