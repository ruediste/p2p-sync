package com.github.ruediste.p2psync.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

public class RefCountAdjustments {
    private final DataStorage storage;

    /**
     * Invariant: adjustments can only be negative if the block has been allocated
     * in a previous transaction. A block that has been allocated in the current
     * transaction is freed immediately if the adjustment becomes 0.
     */
    private HashMap<Long, RefCountAdjustments.RefCountAdjustment> refCountAdjustments = new HashMap<>();

    /**
     * Refcount adjustments for management data structures (Meta Free Tree and
     * RefCount Tree) are tracked separately from regular adjustments to detect when
     * the recursive refcount adjustments are complete. Note that these adjustments
     * are all persistable, thus we can just stop the adjustments at any time and
     * just store the remaining adjustments for the next transaction.
     */
    private HashSet<Long> refCountAdjustmentsPersistable = new HashSet<>();
    private HashSet<Long> refCountAdjustmentsNonPersistable = new HashSet<>();

    public RefCountAdjustments(DataStorage storage) {
        this.storage = storage;
    }

    public static class RefCountAdjustment {
        /**
         * If true, the underlying ref count is 0 and the adjustment represents the
         * total final ref count.
         */
        boolean equalsCount;
        int adjustment;
        private Optional<BlockType<?>> blockType = Optional.empty();

        final DataStorage.Area area;

        RefCountAdjustment(DataStorage.Area area) {
            this.area = area;
        }

        public static RefCountAdjustment create(DataStorage.Area area) {
            return new RefCountAdjustment(area);
        }

        @Override
        public String toString() {
            return adjustment +
                    "@" + area;
        }

        public Optional<BlockType<?>> blockType() {
            return blockType;
        }
    }

    public void setBlockType(long blockOffset, RefCountAdjustment adjustment, BlockType<?> blockType) {
        adjustment.blockType = Optional.of(blockType);
        if (storage.persistableBlockType(blockType).isPresent()) {
            refCountAdjustmentsPersistable.add(blockOffset);
            refCountAdjustmentsNonPersistable.remove(blockOffset);
        } else {
            refCountAdjustmentsPersistable.remove(blockOffset);
            refCountAdjustmentsNonPersistable.add(blockOffset);
        }
    }

    public void clear() {
        refCountAdjustments.clear();
        refCountAdjustmentsPersistable.clear();
        refCountAdjustmentsNonPersistable.clear();
    }

    public RefCountAdjustment getOrCreate(long blockOffset, DataStorage.Area area) {
        return refCountAdjustments.computeIfAbsent(blockOffset, k -> {
            refCountAdjustmentsPersistable.add(blockOffset);
            return RefCountAdjustment.create(area);
        });
    }

    public RefCountAdjustment get(long blockOffset) {
        return refCountAdjustments.get(blockOffset);
    }

    public void remove(long blockOffset) {
        refCountAdjustments.remove(blockOffset);
        refCountAdjustmentsPersistable.remove(blockOffset);
        refCountAdjustmentsNonPersistable.remove(blockOffset);
    }

    public boolean containsKey(long offset) {
        return refCountAdjustments.containsKey(offset);
    }

    public int persistableCount() {
        return refCountAdjustmentsPersistable.size();
    }

    public int nonPersistableCount() {
        return refCountAdjustmentsNonPersistable.size();
    }

    public Set<Entry<Long, RefCountAdjustment>> entrySet() {
        return refCountAdjustments.entrySet();
    }
}
