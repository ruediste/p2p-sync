package com.github.ruediste.p2psync.storage;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

public class RefCountUpdates {
    private final DataStorage storage;

    /**
     * Updates store absolute reference counts. A block allocated in the current
     * transaction is freed immediately if its count becomes 0. A previously
     * committed block that reaches zero is released at commit time and only put
     * on the deferred free list, because the previous superblock can still reach
     * it until the commit point.
     */
    private HashMap<Long, RefCountUpdates.RefCountUpdate> refCountUpdates = new HashMap<>();

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
    }

    public void clear() {
        refCountUpdates.clear();
    }

    public RefCountUpdate getOrCreate(long blockOffset, DataStorage.Area area) {
        return refCountUpdates.computeIfAbsent(blockOffset, k -> {
            return new RefCountUpdate(area, storage.getRefPersistedCount(blockOffset));
        });
    }

    public RefCountUpdate create(long blockOffset, DataStorage.Area area, long count) {
        var update = new RefCountUpdate(area, count);
        refCountUpdates.put(blockOffset, update);
        return update;
    }

    public RefCountUpdate get(long blockOffset) {
        return refCountUpdates.get(blockOffset);
    }

    public void remove(long blockOffset) {
        refCountUpdates.remove(blockOffset);
    }

    public boolean containsKey(long offset) {
        return refCountUpdates.containsKey(offset);
    }

    public Set<Entry<Long, RefCountUpdate>> entrySet() {
        return refCountUpdates.entrySet();
    }
}
