package com.github.ruediste.p2psync.storage;

/**
 * The reference mode is passed to a function modifying a certain block captures
 * how references to other blocks should be handled.
 */
public enum RefMode {
    /**
     * Replace a structurally exclusive, uncounted block; transfer retained child
     * ownership and retire removed children.
     */
    EXCLUSIVE,
    /**
     * Move one existing incoming reference to the replacement. Transfer referenced
     * block ownership if the old block loses its last reference (refcount 1);
     * otherwise duplicate the references (increment refcount of referenced blocks)
     * and pass {@link #SHARE} when updating referenced blocks.
     */
    TRANSFER,

    /**
     * Preserve the existing incoming reference and create an independently owned
     * replacement, acquiring references to referenced blocks (increase refcount).
     */
    SHARE
}
