package com.github.ruediste.p2psync.storage;

/**
 * Ownership policy passed down a block modification path. Exclusive allocator
 * trees use structural ownership; ordinary trees use counted references.
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
