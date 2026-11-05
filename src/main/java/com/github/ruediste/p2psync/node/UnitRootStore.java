package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.List;

import com.github.ruediste.p2psync.storage.BTree;
import com.github.ruediste.p2psync.storage.BlockType;
import com.github.ruediste.p2psync.storage.DataStorage;
import com.github.ruediste.p2psync.storage.DataStorage.Area;
import com.github.ruediste.p2psync.storage.FileTree;

/**
 * Per-SyncUnit root registry. Root handles in the DataStorage root block are
 * global fixed IDs, so the arbitrary roots of the individual units live in this
 * node-level B-tree. Its own root is handle {@value #HANDLE_ID}.
 */
final class UnitRootStore {
    static final int HANDLE_ID = 2;
    static final int KEY_SIZE = SyncUnitId.LENGTH + 1;

    static final int ACCEPTED_OPERATIONS = 0;
    static final int LOCAL_OPERATIONS = 1;
    static final int STAGING_OPERATIONS = 2;
    static final int AUTHORIZATION_OPERATIONS = 3;
    static final int CONTENT_OPERATIONS = 4;
    static final int FILE_TREE = 5;
    static final int GUID_FORWARD = 6;
    static final int GUID_REVERSE = 7;
    static final int SNAPSHOT_FILE_TREE = 8;

    private record RootRef(long offset) {
    }

    private static final BlockType<BTree.Entry<byte[], RootRef>> ENTRY_TYPE = new BlockType<>() {
        @Override
        public List<BlockRef> getReferencedBlocks(BTree.Entry<byte[], RootRef> entry, DataStorage storage) {
            if (entry.key == null)
                throw new IllegalStateException("Unit root entry has no key");
            int kind = Byte.toUnsignedInt(entry.key[SyncUnitId.LENGTH]);
            long offset = entry.value.offset;
            if (offset == 0)
                return List.of();
            return List.of(new BlockRef(offset, Area.META, blockTypeForKind(kind)));
        }

        @Override
        public ByteBuffer serialize(BTree.Entry<byte[], RootRef> entry) {
            return ByteBuffer.allocate(Long.BYTES).putLong(entry.value.offset).flip();
        }

        @Override
        public BTree.Entry<byte[], RootRef> deserialize(ByteBuffer buffer) {
            if (buffer.remaining() != Long.BYTES)
                throw new IllegalStateException("Invalid unit root entry");
            return new BTree.Entry<>(null, new RootRef(buffer.getLong()));
        }
    };

    private static BlockType<BTree.NodeBlock> blockTypeForKind(int kind) {
        return switch (kind) {
            case ACCEPTED_OPERATIONS, LOCAL_OPERATIONS, STAGING_OPERATIONS,
                    AUTHORIZATION_OPERATIONS, CONTENT_OPERATIONS ->
                OperationTree.BLOCK_TYPE;
            case FILE_TREE, SNAPSHOT_FILE_TREE -> FileTree.BLOCK_TYPE;
            case GUID_FORWARD, GUID_REVERSE -> GuidTree.BLOCK_TYPE;
            default -> throw new IllegalStateException("Unknown unit root kind " + kind);
        };
    }

    private final BTree<byte[], RootRef> roots;
    private final DataStorage.RootRefHandle<BTree.NodeBlock> rootHandle;

    UnitRootStore(DataStorage dataStorage) {
        roots = new BTree<>(dataStorage.new BTreeStorage(Area.META), BTree.KeySerializer.bytes(KEY_SIZE),
                ENTRY_TYPE);
        rootHandle = dataStorage.getRootRefHandle(HANDLE_ID, Area.META, roots.blockType);
    }

    void restoreRoot() {
        roots.rootOffset = rootHandle.blockOffset;
    }

    long get(SyncUnitId unitId, int kind) {
        RootRef ref = roots.get(key(unitId, kind));
        return ref == null ? 0 : ref.offset;
    }

    void put(SyncUnitId unitId, int kind, long offset) {
        roots.put(key(unitId, kind), new RootRef(offset));
    }

    void flush() {
        rootHandle.blockOffset = roots.rootOffset;
    }

    private static byte[] key(SyncUnitId unitId, int kind) {
        byte[] key = new byte[KEY_SIZE];
        System.arraycopy(unitId.bytes(), 0, key, 0, SyncUnitId.LENGTH);
        key[SyncUnitId.LENGTH] = (byte) kind;
        return key;
    }
}
