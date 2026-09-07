package com.github.ruediste.p2psync.clock;

/**
 * The local vector clock {@code Cl} of a node, see wiki section "Conflict
 * Resolution". Wraps the clock stored in the data with a modification flag. The
 * flag is not part of the clocks stored in the data; it is only kept for the
 * local vector clock.
 */
public final class LocalClock {

    private final int ownNr;
    private VectorClock clock;
    private boolean modified;

    public LocalClock(int ownNr, VectorClock clock) {
        this(ownNr, clock, false);
    }

    public LocalClock(int ownNr, VectorClock clock, boolean modified) {
        this.ownNr = ownNr;
        this.clock = clock;
        this.modified = modified;
    }

    /**
     * The node number of the owning node; its component is incremented on
     * modification.
     */
    public int getOwnNr() {
        return ownNr;
    }

    /** Current clock value */
    public VectorClock get() {
        return clock;
    }

    public VectorClock getAndClear() {
        clear();
        return clock;
    }

    /** True between a {@link #prepareModify()} and the next {@link #clear()}. */
    public boolean isModified() {
        return modified;
    }

    /**
     * Wiki: {@code prepareModify(x)} increments and sets the flag if the flag is
     * cleared and returns {@code x} unmodified otherwise. Called before performing
     * an update; the updated object's clock is set to {@link #get()}.
     */
    public void prepareModify() {
        if (!modified) {
            clock.increment(ownNr);
            modified = true;
        }
    }

    /** Wiki: {@code clear(x)} returns the clock {@code x} with the flag cleared. */
    public void clear() {
        modified = false;
    }

    @Override
    public String toString() {
        return "LocalClock{nr=" + ownNr + ", clock=" + clock + ", modified=" + modified + "}";
    }

    public void resetTo(VectorClock clock) {
        this.clock = clock.clone();
        this.modified = false;
    }
}
