package com.github.ruediste.p2psync.storage;

import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A BTRFS-like data storage system.
 */
public class DataStorage {

    private RandomAccessFile file;

    public static final byte[] MAGIC = new byte[] { (byte) 0xd6, (byte) 0x1b, (byte) 0x52, (byte) 0xd5, (byte) 0x6d,
            (byte) 0xc6, (byte) 0x7e, (byte) 0x4f, (byte) 0xfd, (byte) 0x40, (byte) 0xc6, (byte) 0xd8, (byte) 0xf5,
            (byte) 0xee, (byte) 0x03, (byte) 0xdb };

    public static class SuperBlock {
        byte[] magic = new byte[16];
        long version; // Version of the file format
        long rootBlock; // offset of the root block within the file
    }

    public static class RootBlock {
        long dataFreeSpaceTree;
        long metaFreeSpaceTree;
        long systemFreeSpaceTree;
        long systemKvTree;
        long refCountTree;
        long infoBlock;

        // various data per sub system
    }

    public enum BlockKind {
        SYSTEM,
        META,
        DATA
    }

    public static class InfoBlock {
        boolean dirty;
        private int dataBlockSize; // typically 4KB
        private int metaBlockSize; // typically 16KB
        private int systemBlockSize; // typically 4KB

        // various data per sub system

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            this.dirty = false;
        }

        public int getDataBlockSize() {
            return dataBlockSize;
        }

        public void setDataBlockSize(int dataBlockSize) {
            this.dirty |= this.dataBlockSize != dataBlockSize;
            this.dataBlockSize = dataBlockSize;
        }

        public int getMetaBlockSize() {
            return metaBlockSize;
        }

        public void setMetaBlockSize(int metaBlockSize) {
            this.dirty |= this.metaBlockSize != metaBlockSize;
            this.metaBlockSize = metaBlockSize;
        }

        public int getSystemBlockSize() {
            return systemBlockSize;
        }

        public void setSystemBlockSize(int systemBlockSize) {
            this.dirty |= this.systemBlockSize != systemBlockSize;
            this.systemBlockSize = systemBlockSize;
        }

    }

    // Current state of the storage system, matching the state of the current
    // transaction.
    // On commit, the super block and root block are always written to the storage,
    // since there is no other way to persist changes.
    public SuperBlock superBlock;
    public RootBlock rootBlock;
    public InfoBlock infoBlock;

    private static class RefCountAdjustment {
        int adjustment;
        Optional<Runnable> onDelete = Optional.empty();

        private RefCountAdjustment(int adjustment, Optional<Runnable> onDelete) {
            this.adjustment = adjustment;
            this.onDelete = onDelete;
        }
    }

    private HashMap<Long, RefCountAdjustment> refCountAdjustments = new HashMap<>();

    public void incrementRefCount(long blockOffset, BlockKind kind) {
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> new RefCountAdjustment(0, Optional.empty()));
        adjustment.adjustment++;
        // onDelete is only relevant when the adjustment is negative
        if (adjustment.adjustment == 0)
            adjustment.onDelete = Optional.empty();
    }

    public void decrementRefCount(long blockOffset, BlockKind kind, Supplier<Runnable> onDelete) {
        var adjustment = refCountAdjustments.computeIfAbsent(blockOffset,
                k -> new RefCountAdjustment(0, Optional.empty()));
        adjustment.adjustment--;
        if (adjustment.adjustment == 0) {
            // check if this is a block that was created in this transaction. If so, remove
            // it.
            if (modifiedBlocks.remove(blockOffset) != null) {
                refCountAdjustments.remove(blockOffset);
                // TODO: add block back to the free tree

            }

        } else if (adjustment.adjustment == -1)
            adjustment.onDelete = Optional.of(onDelete.get());
    }

    // Blocks that have been modified in the current transaction. The key is the
    // block offset and the value is the block data.
    // Direct writes can be performed, in which case the value is set to null.
    private HashMap<Long, byte[]> modifiedBlocks = new HashMap<>();

    public DataStorage() {
    }

    public void open(String path) {
    }

    public byte[] readBlock(long blockOffset, BlockKind kind) {
        // Read the block data from the file based on the block offset and kind.
        // take modifiedBlocks into account
        return new byte[0];
    }

    public boolean isBlockModified(long blockOffset) {
        return modifiedBlocks.containsKey(blockOffset);
    }

    public void writeBlock(long blockOffset, BlockKind kind, byte[] data) {
        modifiedBlocks.put(blockOffset, data);
    }

    public void writeBlockDirect(long blockOffset, BlockKind kind, byte[] data) {
        modifiedBlocks.put(blockOffset, null);
    }

    public long writeNewBlock(BlockKind kind, byte[] data) {
        // allocate a block from the free tree
        // mark the block as modified
        incrementRefCount(0, kind);
        return 0;
    }

    public long writeNewBlockDirect(BlockKind kind, byte[] data) {
        // allocate a block from the free tree
        // mark the block as modified
        // write data to disk
        incrementRefCount(0, kind);
        return 0;
    }

    // There is always a transaction in progress. There is no transaction rollback.
    public void commit() {
        // write info block if dirty
        // flush refCountAdjustments
        // flush modifiedBlocks
        // write root block
        // write super block
    }
}
