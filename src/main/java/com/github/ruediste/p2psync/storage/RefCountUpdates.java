package com.github.ruediste.p2psync.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

public class RefCountUpdates {
    private final DataStorage storage;

    /**
     * Updates store absolute reference counts. A block allocated in the current
     * transaction is freed immediately if its count becomes 0.
     */
    private HashMap<Long, RefCountUpdates.RefCountUpdate> refCountUpdates = new HashMap<>();

    /**
     * Refcount updates for management data structures (Meta Free Tree and
     * RefCount Tree) are tracked separately from regular updates to detect when
     * the recursive refcount updates are complete. Note that these updates
     * are all persistable, thus we can just stop the updates at any time and
     * just store the remaining updates for the next transaction.
     */
    private HashSet<Long> refCountUpdatesPersistable = new HashSet<>();
    private HashSet<Long> refCountUpdatesNonPersistable = new HashSet<>();

    public RefCountUpdates(DataStorage storage) {
        this.storage = storage;
    }

    public static class RefCountUpdate {
        long initialCount;
        long count;
        private Optional<BlockType<?>> blockType = Optional.empty();

        final DataStorage.Area area;

        RefCountUpdate(DataStorage.Area area) {
            this(area, 0);
        }

        RefCountUpdate(DataStorage.Area area, long initialCount) {
            this.area = area;
            this.initialCount = initialCount;
            this.count = initialCount;
        }

        public static RefCountUpdate create(DataStorage.Area area) {
            return new RefCountUpdate(area);
        }

        @Override
        public String toString() {
            return count +
                    "@" + area;
        }

        public Optional<BlockType<?>> blockType() {
            return blockType;
        }
    }

    public void setBlockType(long blockOffset, RefCountUpdate update, BlockType<?> blockType) {
        update.blockType = Optional.of(blockType);
        if (storage.persistableBlockType(blockType).isPresent()) {
            refCountUpdatesPersistable.add(blockOffset);
            refCountUpdatesNonPersistable.remove(blockOffset);
        } else {
            refCountUpdatesPersistable.remove(blockOffset);
            refCountUpdatesNonPersistable.add(blockOffset);
        }
    }

    public void clear() {
        refCountUpdates.clear();
        refCountUpdatesPersistable.clear();
        refCountUpdatesNonPersistable.clear();
    }

    public RefCountUpdate getOrCreate(long blockOffset, DataStorage.Area area) {
        return refCountUpdates.computeIfAbsent(blockOffset, k -> {
            var update = new RefCountUpdate(area, storage.getRefCount(blockOffset));
            refCountUpdatesPersistable.add(blockOffset);
            return update;
        });
    }

    public RefCountUpdate create(long blockOffset, DataStorage.Area area, long count) {
        var update = new RefCountUpdate(area, count);
        refCountUpdates.put(blockOffset, update);
        refCountUpdatesPersistable.add(blockOffset);
        refCountUpdatesNonPersistable.remove(blockOffset);
        return update;
    }

    public RefCountUpdate get(long blockOffset) {
        return refCountUpdates.get(blockOffset);
    }

    public void remove(long blockOffset) {
        refCountUpdates.remove(blockOffset);
        refCountUpdatesPersistable.remove(blockOffset);
        refCountUpdatesNonPersistable.remove(blockOffset);
    }

    public boolean containsKey(long offset) {
        return refCountUpdates.containsKey(offset);
    }

    public int persistableCount() {
        return refCountUpdatesPersistable.size();
    }

    public int nonPersistableCount() {
        return refCountUpdatesNonPersistable.size();
    }

    public Set<Entry<Long, RefCountUpdate>> entrySet() {
        return refCountUpdates.entrySet();
    }
}
