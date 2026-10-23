package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32C;

import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.util.ShortGuid;

public class FileTree {
    private static final int GUID_SIZE = Integer.BYTES + Long.BYTES;
    private static final int KEY_SIZE = GUID_SIZE + 1 + Long.BYTES;
    private static final int SPILL_KEY_SIZE = 32 + Integer.BYTES;
    private static final int INLINE_ENTRY_LIMIT = 4096;

    private enum EntryType {
        FILE_HEADER(0),
        DIR_HEADER(1),
        DIR_ITEM(2),
        DIR_INDEX(3),
        INLINE_DATA(4),
        EXTENT_DATA(5);

        final int code;

        EntryType(int code) {
            this.code = code;
        }

        static EntryType fromCode(int code) {
            for (EntryType type : values())
                if (type.code == code)
                    return type;
            throw new IllegalStateException("Unknown file-tree entry type: " + code);
        }
    }

    public static final BlockType<BTree.NodeBlock> BLOCK_TYPE = BTree.blockType(KEY_SIZE, DataStorage.Area.META);

    private final DataStorage storage;
    private final BTree tree;

    public FileTree(DataStorage storage) {
        this(storage, 0);
    }

    public FileTree(DataStorage storage, long rootOffset) {
        this.storage = java.util.Objects.requireNonNull(storage);
        tree = new BTree(storage.new BTreeStorage(DataStorage.Area.META), KEY_SIZE, this::entryReplaced,
                rootOffset);
    }

    public long getRootOffset() {
        return tree.rootOffset;
    }

    public void setRootOffset(long offset) {
        tree.rootOffset = offset;
    }

    public byte[] read(ShortGuid guid) {
        byte[] header = getHeader(guid, EntryType.FILE_HEADER);
        if (header == null)
            throw new IllegalArgumentException("Not a file: " + formatGuid(guid));
        long fileSize = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(GUID_SIZE);
        if (fileSize > Integer.MAX_VALUE)
            throw new IllegalStateException("File is too large to return as a byte array: " + fileSize);
        return read(guid, 0, (int) fileSize);
    }

    /** Reads at most {@code size} bytes starting at {@code offset}. */
    public byte[] read(ShortGuid guid, long offset, int size) {
        requireOffset(offset);
        if (size < 0)
            throw new IllegalArgumentException("Read size must not be negative");
        byte[] header = getHeader(guid, EntryType.FILE_HEADER);
        if (header == null)
            throw new IllegalArgumentException("Not a file: " + formatGuid(guid));
        long fileSize = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(GUID_SIZE);
        if (size == 0 || offset >= fileSize)
            return new byte[0];
        int resultSize = (int) Math.min(size, fileSize - offset);
        byte[] result = new byte[resultSize];
        long readEnd = offset + resultSize;
        // Begin at the floor key so a chunk starting before the requested range is not
        // missed.
        byte[] inlineStartKey = key(guid, EntryType.INLINE_DATA, offset);
        Iterator<BTree.Entry> inlineEntries = tree.floorOrLowestIterator(inlineStartKey);
        while (inlineEntries.hasNext()) {
            BTree.Entry entry = inlineEntries.next();
            int comparison = compareKeys(entry.key, inlineStartKey);
            if (!hasPrefix(entry.key, guid, EntryType.INLINE_DATA)) {
                if (comparison < 0)
                    continue;
                break;
            }
            long entryOffset = offsetOf(entry.key);
            if (entryOffset <= offset && entry.value.length <= offset - entryOffset)
                continue;
            if (entryOffset >= readEnd)
                break;
            copyRange(entry.value, entryOffset, offset, result, entry.value.length);
        }

        int blockSize = storage.blockSize(DataStorage.Area.DATA);
        byte[] extentStartKey = key(guid, EntryType.EXTENT_DATA, offset);
        Iterator<BTree.Entry> extentEntries = tree.floorOrLowestIterator(extentStartKey);
        while (extentEntries.hasNext()) {
            BTree.Entry entry = extentEntries.next();
            int comparison = compareKeys(entry.key, extentStartKey);
            if (!hasPrefix(entry.key, guid, EntryType.EXTENT_DATA)) {
                if (comparison < 0)
                    continue;
                break;
            }
            long fileOffset = offsetOf(entry.key);
            long extentByteLength = (long) (entry.value.length / Long.BYTES) * blockSize;
            if (fileOffset <= offset && extentByteLength <= offset - fileOffset)
                continue;
            if (fileOffset >= readEnd)
                break;
            if (entry.value.length % Long.BYTES != 0)
                throw new IllegalStateException("Invalid extent entry length");
            ByteBuffer extents = ByteBuffer.wrap(entry.value).order(ByteOrder.BIG_ENDIAN);
            // Extent values can cover multiple blocks; skip directly to the first
            // overlapping block.
            long skippedBlocks = Math.max(0, (offset - fileOffset) / blockSize);
            extents.position(Math.toIntExact(skippedBlocks * Long.BYTES));
            fileOffset += skippedBlocks * blockSize;
            while (extents.hasRemaining() && fileOffset < readEnd) {
                byte[] block = storage.readBlock(extents.getLong(), DataStorage.Area.DATA);
                int length = (int) Math.min(blockSize, fileSize - fileOffset);
                if (length > 0)
                    copyRange(block, fileOffset, offset, result, length);
                fileOffset += blockSize;
            }
        }
        return result;
    }

    /**
     * Writes bytes at an offset, zero-filling a gap and extending the file as
     * needed.
     */
    public void write(ShortGuid guid, long offset, byte[] data) {
        java.util.Objects.requireNonNull(data);
        requireOffset(offset);
        byte[] header = getHeader(guid, EntryType.FILE_HEADER);
        if (header == null)
            throw new IllegalArgumentException("Not a file: " + formatGuid(guid));
        if (data.length == 0)
            return;
        long oldSize = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(GUID_SIZE);
        long newSize = Math.max(oldSize, Math.addExact(offset, data.length));
        int inlineThreshold = storage.infoBlock.getInlineDataThreshold();
        if (newSize <= inlineThreshold) {
            // Inline files are small enough to rewrite as one value set.
            byte[] content = Arrays.copyOf(read(guid), (int) newSize);
            System.arraycopy(data, 0, content, (int) offset, data.length);
            clearData(guid);
            writeData(guid, content);
        } else {
            // Grow the extent map first so the range writer only replaces existing blocks.
            if (newSize != oldSize) {
                truncate(guid, newSize);
                header = getHeader(guid, EntryType.FILE_HEADER);
            }
            writeExtentRange(guid, offset, data);
        }
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putLong(GUID_SIZE, newSize)
                .putLong(GUID_SIZE + 2 * Long.BYTES, System.currentTimeMillis());
        tree.put(key(guid, EntryType.FILE_HEADER, 0), header);
    }

    public void truncate(ShortGuid guid, long newSize) {
        requireOffset(newSize);
        byte[] header = getHeader(guid, EntryType.FILE_HEADER);
        if (header == null)
            throw new IllegalArgumentException("Not a file: " + formatGuid(guid));
        long oldSize = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(GUID_SIZE);
        if (oldSize == newSize)
            return;
        long inlineThreshold = storage.infoBlock.getInlineDataThreshold();
        // Keep each file in exactly one representation, converting only when the
        // threshold is crossed.
        if (newSize <= inlineThreshold) {
            if (oldSize <= inlineThreshold) {
                truncateInlineData(guid, newSize);
            } else {
                byte[] content = read(guid, 0, (int) newSize);
                clearData(guid);
                writeData(guid, content);
            }
        } else if (oldSize <= inlineThreshold) {
            byte[] inlineContent = read(guid);
            clearData(guid);
            writeExtentBlocks(guid, 0, blockCount(newSize), inlineContent);
        } else if (newSize < oldSize) {
            truncateExtentData(guid, newSize);
        } else {
            writeExtentBlocks(guid, blockCount(oldSize), blockCount(newSize), new byte[0]);
        }
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putLong(GUID_SIZE, newSize)
                .putLong(GUID_SIZE + 2 * Long.BYTES, System.currentTimeMillis());
        tree.put(key(guid, EntryType.FILE_HEADER, 0), header);
    }

    public static class FileMeta {
        public long size;
        public long creationTime;
        public long modificationTime;

        public FileMeta() {
            creationTime = modificationTime = System.currentTimeMillis();
        }

        public FileMeta(long size, long creationTime, long modificationTime) {
            this.size = size;
            this.creationTime = creationTime;
            this.modificationTime = modificationTime;
        }
    }

    public record DirectoryEntry(ShortGuid guid, boolean directory, String name) {
    }

    public void createFile(ShortGuid parentId, ShortGuid fileId, String name, FileMeta meta) {
        create(parentId, fileId, name, meta, false);
    }

    public void createDirectory(ShortGuid parentId, ShortGuid dirId, String name, FileMeta meta) {
        create(parentId, dirId, name, meta, true);
    }

    public FileMeta getFileMeta(ShortGuid guid) {
        byte[] value = getHeader(guid, EntryType.FILE_HEADER);
        if (value == null)
            return null;
        ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        return new FileMeta(buffer.getLong(GUID_SIZE), buffer.getLong(GUID_SIZE + Long.BYTES),
                buffer.getLong(GUID_SIZE + 2 * Long.BYTES));
    }

    public FileMeta getDirectoryMeta(ShortGuid guid) {
        byte[] value = getHeader(guid, EntryType.DIR_HEADER);
        if (value == null)
            return null;
        ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        return new FileMeta(0, buffer.getLong(GUID_SIZE), buffer.getLong(GUID_SIZE + Long.BYTES));
    }

    public List<DirectoryEntry> listDirectory(ShortGuid directoryId) {
        if (getHeader(directoryId, EntryType.DIR_HEADER) == null)
            throw new IllegalArgumentException("Not a directory: " + formatGuid(directoryId));
        List<DirectoryEntry> result = new ArrayList<>();
        for (BTree.Entry entry : entriesFor(directoryId, EntryType.DIR_INDEX))
            result.add(decodeDirectoryIndex(entry.value));
        return List.copyOf(result);
    }

    public DirectoryEntry lookup(ShortGuid directoryId, String name) {
        validateName(name);
        if (getHeader(directoryId, EntryType.DIR_HEADER) == null)
            throw new IllegalArgumentException("Not a directory: " + formatGuid(directoryId));
        DirItem item = decodeDirItem(tree.get(key(directoryId, EntryType.DIR_ITEM,
                Integer.toUnsignedLong(nameHash(name)))));
        for (NameEntry candidate : allNames(item))
            if (candidate.name.equals(name))
                return new DirectoryEntry(copyGuid(candidate.guid), candidate.type == EntryType.DIR_HEADER, name);
        return null;
    }

    public void rename(ShortGuid guid, String newName) {
        validateName(newName);
        byte[] header = requireEntityHeader(guid);
        boolean directory = isDirectoryHeader(header);
        ShortGuid parent = readParent(header);
        DirectoryEntry previous = findChild(parent, guid);
        if (previous == null)
            throw new IllegalArgumentException("Entity has no directory entry: " + formatGuid(guid));
        if (previous.name().equals(newName))
            return;
        ensureNameAvailable(parent, newName, guid);
        removeDirectoryEntry(parent, previous);
        addDirectoryEntry(parent, new DirectoryEntry(copyGuid(guid), directory, newName));
        updateModificationTime(header, directory);
        tree.put(key(guid, directory ? EntryType.DIR_HEADER : EntryType.FILE_HEADER, 0), header);
    }

    public void move(ShortGuid guid, ShortGuid newParentId) {
        byte[] header = requireEntityHeader(guid);
        boolean directory = isDirectoryHeader(header);
        if (getHeader(newParentId, EntryType.DIR_HEADER) == null)
            throw new IllegalArgumentException("Destination is not a directory: " + formatGuid(newParentId));
        if (directory && (sameGuid(guid, newParentId) || isDescendant(newParentId, guid)))
            throw new IllegalArgumentException("A directory cannot be moved into itself or a descendant");
        ShortGuid oldParentId = readParent(header);
        DirectoryEntry oldEntry = findChild(oldParentId, guid);
        if (oldEntry == null)
            throw new IllegalArgumentException("Entity has no directory entry: " + formatGuid(guid));
        ensureNameAvailable(newParentId, oldEntry.name(), guid);
        if (sameGuid(oldParentId, newParentId))
            return;
        removeDirectoryEntry(oldParentId, oldEntry);
        addDirectoryEntry(newParentId, oldEntry);
        writeGuid(header, 0, newParentId);
        updateModificationTime(header, directory);
        tree.put(key(guid, directory ? EntryType.DIR_HEADER : EntryType.FILE_HEADER, 0), header);
    }

    public boolean delete(ShortGuid guid) {
        byte[] header = requireEntityHeader(guid);
        boolean directory = isDirectoryHeader(header);
        if (directory) {
            for (DirectoryEntry child : listDirectory(guid))
                delete(child.guid());
        } else {
            clearData(guid);
        }
        ShortGuid parent = readParent(header);
        if (!sameGuid(parent, guid)) {
            DirectoryEntry entry = findChild(parent, guid);
            if (entry != null)
                removeDirectoryEntry(parent, entry);
        }
        return tree.remove(key(guid, directory ? EntryType.DIR_HEADER : EntryType.FILE_HEADER, 0));
    }

    private void create(ShortGuid parentId, ShortGuid guid, String name, FileMeta meta, boolean directory) {
        java.util.Objects.requireNonNull(meta);
        validateName(name);
        if (getHeader(guid, EntryType.FILE_HEADER) != null || getHeader(guid, EntryType.DIR_HEADER) != null)
            throw new IllegalArgumentException("GUID already exists: " + formatGuid(guid));
        if (!sameGuid(parentId, guid) && getHeader(parentId, EntryType.DIR_HEADER) == null)
            throw new IllegalArgumentException("Parent is not a directory: " + formatGuid(parentId));
        if (!sameGuid(parentId, guid))
            ensureNameAvailable(parentId, name, null);
        ByteBuffer header = ByteBuffer.allocate(GUID_SIZE + (directory ? 2 : 3) * Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        writeGuid(header, parentId);
        if (!directory)
            header.putLong(meta.size);
        header.putLong(meta.creationTime).putLong(meta.modificationTime);
        tree.put(key(guid, directory ? EntryType.DIR_HEADER : EntryType.FILE_HEADER, 0), header.array());
        if (!sameGuid(parentId, guid))
            addDirectoryEntry(parentId, new DirectoryEntry(copyGuid(guid), directory, name));
    }

    private void writeData(ShortGuid guid, byte[] content) {
        int inlineThreshold = storage.infoBlock.getInlineDataThreshold();
        if (content.length <= inlineThreshold) {
            // write content inline
            for (int offset = 0; offset < content.length; offset += INLINE_ENTRY_LIMIT) {
                int length = Math.min(INLINE_ENTRY_LIMIT, content.length - offset);
                tree.put(key(guid, EntryType.INLINE_DATA, offset),
                        Arrays.copyOfRange(content, offset, offset + length));
            }
        } else
            writeExtentBlocks(guid, 0, blockCount(content.length), content);
    }

    private void truncateInlineData(ShortGuid guid, long newSize) {
        for (BTree.Entry entry : entriesFor(guid, EntryType.INLINE_DATA)) {
            long start = offsetOf(entry.key);
            if (start >= newSize) {
                tree.remove(entry.key);
            } else {
                int keepLength = (int) Math.min(entry.value.length, newSize - start);
                if (keepLength < entry.value.length)
                    tree.put(entry.key, Arrays.copyOf(entry.value, keepLength));
            }
        }
    }

    private void truncateExtentData(ShortGuid guid, long newSize) {
        int dataBlockSize = storage.blockSize(DataStorage.Area.DATA);
        long finalBlockIndex = blockCount(newSize) - 1;
        int finalBlockLength = (int) (newSize % dataBlockSize);
        for (BTree.Entry entry : entriesFor(guid, EntryType.EXTENT_DATA)) {
            long start = offsetOf(entry.key);
            int blockCount = entry.value.length / Long.BYTES;
            long keepCount = start >= newSize ? 0 : Math.min(blockCount, blockCount(newSize - start));
            if (keepCount == blockCount)
                continue;
            if (keepCount == 0) {
                tree.remove(entry.key);
                continue;
            }
            ByteBuffer oldOffsets = ByteBuffer.wrap(entry.value).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer retainedOffsets = ByteBuffer.allocate((int) keepCount * Long.BYTES)
                    .order(ByteOrder.BIG_ENDIAN);
            // Retain the surviving blocks and replace a partial final block with a zeroed
            // tail.
            for (int index = 0; index < keepCount; index++) {
                long blockOffset = oldOffsets.getLong();
                long blockIndex = start / dataBlockSize + index;
                if (finalBlockLength != 0 && blockIndex == finalBlockIndex) {
                    byte[] block = storage.readBlock(blockOffset, DataStorage.Area.DATA);
                    Arrays.fill(block, finalBlockLength, block.length, (byte) 0);
                    blockOffset = storage.writeNewBlock(DataStorage.Area.DATA, block);
                }
                storage.incrementRefCount(blockOffset, DataStorage.Area.DATA);
                retainedOffsets.putLong(blockOffset);
            }
            tree.put(entry.key, retainedOffsets.array());
        }
    }

    private void writeExtentBlocks(ShortGuid guid, long firstBlock, long endBlock, byte[] prefix) {
        int blockSize = storage.blockSize(DataStorage.Area.DATA);
        int offsetsPerEntry = Math.max(1, (storage.blockSize(DataStorage.Area.META) - 64) / Long.BYTES);
        List<Long> offsets = new ArrayList<>(offsetsPerEntry);
        // Remove children first so no directory entry can outlive its target.
        long entryStart = firstBlock * blockSize;
        // Batch consecutive DATA block offsets into extent values sized for META
        // blocks.
        for (long blockIndex = firstBlock; blockIndex < endBlock; blockIndex++) {
            long fileOffset = blockIndex * blockSize;
            byte[] block = new byte[blockSize];
            if (fileOffset < prefix.length) {
                int length = (int) Math.min(blockSize, prefix.length - fileOffset);
                System.arraycopy(prefix, (int) fileOffset, block, 0, length);
            }
            long blockOffset = storage.writeNewBlock(DataStorage.Area.DATA, block);
            storage.incrementRefCount(blockOffset, DataStorage.Area.DATA);
            if (offsets.isEmpty())
                entryStart = fileOffset;
            offsets.add(blockOffset);
            if (offsets.size() == offsetsPerEntry || blockIndex + 1 == endBlock) {
                ByteBuffer value = ByteBuffer.allocate(offsets.size() * Long.BYTES).order(ByteOrder.BIG_ENDIAN);
                offsets.forEach(value::putLong);
                tree.put(key(guid, EntryType.EXTENT_DATA, entryStart), value.array());
                offsets.clear();
            }
        }
    }

    private void writeExtentRange(ShortGuid guid, long offset, byte[] data) {
        int blockSize = storage.blockSize(DataStorage.Area.DATA);
        long endOffset = Math.addExact(offset, data.length);
        // Copy-on-write only the touched DATA blocks, preserving all other offsets in
        // each extent value.
        for (long blockIndex = offset / blockSize; blockIndex < blockCount(endOffset); blockIndex++) {
            long blockStart = blockIndex * blockSize;
            BTree.Entry extentEntry = findExtentEntry(guid, blockStart, blockSize);
            // Spill new names by SHA-256 while preserving the current spill-tree root.
            if (extentEntry == null)
                throw new IllegalStateException("Missing file extent at offset " + blockStart);
            long entryStart = offsetOf(extentEntry.key);
            ByteBuffer offsets = ByteBuffer.wrap(extentEntry.value).order(ByteOrder.BIG_ENDIAN);
            long[] blockOffsets = new long[offsets.remaining() / Long.BYTES];
            for (int index = 0; index < blockOffsets.length; index++)
                blockOffsets[index] = offsets.getLong();
            int extentIndex = (int) ((blockStart - entryStart) / blockSize);
            byte[] block = storage.readBlock(blockOffsets[extentIndex], DataStorage.Area.DATA);
            long writeStart = Math.max(offset, blockStart);
            long writeEnd = Math.min(endOffset, blockStart + blockSize);
            System.arraycopy(data, (int) (writeStart - offset), block, (int) (writeStart - blockStart),
                    (int) (writeEnd - writeStart));
            blockOffsets[extentIndex] = storage.writeNewBlock(DataStorage.Area.DATA, block);
            ByteBuffer replacement = ByteBuffer.allocate(blockOffsets.length * Long.BYTES)
                    .order(ByteOrder.BIG_ENDIAN);
            for (long blockOffset : blockOffsets) {
                storage.incrementRefCount(blockOffset, DataStorage.Area.DATA);
                replacement.putLong(blockOffset);
            }
            tree.put(extentEntry.key, replacement.array());
        }
    }

    private BTree.Entry findExtentEntry(ShortGuid guid, long blockStart, int blockSize) {
        for (BTree.Entry entry : entriesFor(guid, EntryType.EXTENT_DATA)) {
            long entryStart = offsetOf(entry.key);
            long entrySize = (long) (entry.value.length / Long.BYTES) * blockSize;
            if (blockStart >= entryStart && blockStart - entryStart < entrySize)
                return entry;
        }
        return null;
    }

    private long blockCount(long size) {
        int blockSize = storage.blockSize(DataStorage.Area.DATA);
        return size == 0 ? 0 : 1 + (size - 1) / blockSize;
    }

    private void clearData(ShortGuid guid) {
        List<byte[]> keys = new ArrayList<>();
        for (BTree.Entry entry : entriesFor(guid, EntryType.INLINE_DATA))
            keys.add(entry.key);
        for (BTree.Entry entry : entriesFor(guid, EntryType.EXTENT_DATA))
            keys.add(entry.key);
        keys.forEach(tree::remove);
    }

    private void entryReplaced(byte[] key, byte[] value) {
        EntryType type = EntryType.fromCode(Byte.toUnsignedInt(key[GUID_SIZE]));
        if (type == EntryType.EXTENT_DATA) {
            if (value.length % Long.BYTES != 0)
                throw new IllegalStateException("Invalid extent entry length");
            ByteBuffer offsets = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
            while (offsets.hasRemaining())
                storage.decrementRefCount(new BlockRef(offsets.getLong(), DataStorage.Area.DATA,
                        DataStorage.BYTE_ARRAY_BLOCK_TYPE));
        } else if (type == EntryType.DIR_ITEM) {
            long spillRoot = decodeDirItem(value).spillRoot;
            if (spillRoot != 0)
                storage.decrementRefCount(new BlockRef(spillRoot, DataStorage.Area.META,
                        BTree.blockType(SPILL_KEY_SIZE, DataStorage.Area.META)));
        }
    }

    private void addDirectoryEntry(ShortGuid parentId, DirectoryEntry entry) {
        byte[] itemKey = key(parentId, EntryType.DIR_ITEM, Integer.toUnsignedLong(nameHash(entry.name())));
        DirItem item = decodeDirItem(tree.get(itemKey));
        for (NameEntry existing : allNames(item))
            if (existing.name.equals(entry.name()))
                throw new IllegalArgumentException("Directory entry already exists: " + entry.name());
        NameEntry added = new NameEntry(copyGuid(entry.guid()),
                entry.directory() ? EntryType.DIR_HEADER : EntryType.FILE_HEADER,
                entry.name());
        List<NameEntry> inlineEntries = new ArrayList<>(item.entries);
        inlineEntries.add(added);
        if (encodedInlineSize(inlineEntries) <= INLINE_ENTRY_LIMIT && inlineEntries.size() <= 255) {
            item.entries.clear();
            item.entries.addAll(inlineEntries);
        } else {
            BTree spill = newSpillTree(item.spillRoot);
            spill.put(nextSpillKey(spill, added.name), encodeSpillValue(added));
            item.spillRoot = spill.rootOffset;
            storage.incrementRefCount(item.spillRoot, DataStorage.Area.META);
        }
        tree.put(itemKey, encodeDirItem(item));

        long nextIndex = 0;
        for (BTree.Entry index : entriesFor(parentId, EntryType.DIR_INDEX)) {
            long indexOffset = offsetOf(index.key);
            if (indexOffset < 0 || indexOffset == Long.MAX_VALUE)
                throw new IllegalStateException("Directory index exhausted");
            nextIndex = Math.max(nextIndex, indexOffset + 1);
        }
        tree.put(key(parentId, EntryType.DIR_INDEX, nextIndex), encodeDirectoryIndex(added));
        touchDirectory(parentId);
    }

    private void removeDirectoryEntry(ShortGuid parentId, DirectoryEntry entry) {
        byte[] itemKey = key(parentId, EntryType.DIR_ITEM, Integer.toUnsignedLong(nameHash(entry.name())));
        DirItem item = decodeDirItem(tree.get(itemKey));
        boolean removed = item.entries.removeIf(candidate -> candidate.name.equals(entry.name())
                && sameGuid(candidate.guid, entry.guid()));
        if (!removed && item.spillRoot != 0) {
            BTree spill = newSpillTree(item.spillRoot);
            for (BTree.Entry candidate : spill) {
                NameEntry name = decodeSpillValue(candidate.value);
                if (name.name.equals(entry.name()) && sameGuid(name.guid, entry.guid())) {
                    spill.remove(candidate.key);
                    removed = true;
                    break;
                }
            }
            item.spillRoot = spill.rootOffset;
            if (item.spillRoot != 0)
                storage.incrementRefCount(item.spillRoot, DataStorage.Area.META);
        }
        if (!removed)
            throw new IllegalStateException("Directory entry not found: " + entry.name());
        if (item.entries.isEmpty() && item.spillRoot == 0)
            tree.remove(itemKey);
        else
            tree.put(itemKey, encodeDirItem(item));
        for (BTree.Entry index : entriesFor(parentId, EntryType.DIR_INDEX)) {
            DirectoryEntry indexed = decodeDirectoryIndex(index.value);
            if (indexed.name().equals(entry.name()) && sameGuid(indexed.guid(), entry.guid())) {
                tree.remove(index.key);
                break;
            }
        }
        touchDirectory(parentId);
    }

    private void ensureNameAvailable(ShortGuid parent, String name, ShortGuid except) {
        for (DirectoryEntry entry : listDirectory(parent))
            if (entry.name().equals(name) && (except == null || !sameGuid(entry.guid(), except)))
                throw new IllegalArgumentException("Directory entry already exists: " + name);
    }

    private DirectoryEntry findChild(ShortGuid parent, ShortGuid guid) {
        for (DirectoryEntry entry : listDirectory(parent))
            if (sameGuid(entry.guid(), guid))
                return entry;
        return null;
    }

    private boolean isDescendant(ShortGuid candidate, ShortGuid ancestor) {
        ShortGuid current = copyGuid(candidate);
        while (true) {
            if (sameGuid(current, ancestor))
                return true;
            byte[] header = getHeader(current, EntryType.DIR_HEADER);
            if (header == null)
                return false;
            ShortGuid parent = readParent(header);
            if (sameGuid(parent, current))
                return false;
            current = parent;
        }
    }

    private void touchDirectory(ShortGuid guid) {
        byte[] header = getHeader(guid, EntryType.DIR_HEADER);
        if (header == null)
            return;
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putLong(GUID_SIZE + Long.BYTES,
                System.currentTimeMillis());
        tree.put(key(guid, EntryType.DIR_HEADER, 0), header);
    }

    private static void updateModificationTime(byte[] header, boolean directory) {
        int index = GUID_SIZE + (directory ? Long.BYTES : 2 * Long.BYTES);
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putLong(index, System.currentTimeMillis());
    }

    private byte[] requireEntityHeader(ShortGuid guid) {
        byte[] header = getHeader(guid, EntryType.FILE_HEADER);
        if (header == null)
            header = getHeader(guid, EntryType.DIR_HEADER);
        if (header == null)
            throw new IllegalArgumentException("Entity does not exist: " + formatGuid(guid));
        return header;
    }

    private boolean isDirectoryHeader(byte[] header) {
        return header.length == GUID_SIZE + 2 * Long.BYTES;
    }

    private byte[] getHeader(ShortGuid guid, EntryType type) {
        return tree.get(key(guid, type, 0));
    }

    private List<BTree.Entry> entriesFor(ShortGuid guid, EntryType type) {
        byte[] start = key(guid, type, 0);
        Iterator<BTree.Entry> iterator = tree.floorOrLowestIterator(start);
        List<BTree.Entry> result = new ArrayList<>();
        while (iterator.hasNext()) {
            BTree.Entry entry = iterator.next();
            if (compareKeys(entry.key, start) < 0)
                continue;
            if (!hasPrefix(entry.key, guid, type))
                break;
            result.add(entry);
        }
        return result;
    }

    private List<NameEntry> allNames(DirItem item) {
        List<NameEntry> result = new ArrayList<>(item.entries);
        if (item.spillRoot != 0)
            for (BTree.Entry entry : newSpillTree(item.spillRoot))
                result.add(decodeSpillValue(entry.value));
        return result;
    }

    private BTree newSpillTree(long root) {
        return new BTree(storage.new BTreeStorage(DataStorage.Area.META), SPILL_KEY_SIZE, null, root);
    }

    private static byte[] nextSpillKey(BTree spill, String name) {
        byte[] hash = sha256(name.getBytes(StandardCharsets.UTF_8));
        int next = 0;
        for (BTree.Entry entry : spill) {
            if (startsWith(entry.key, hash)) {
                int existing = ByteBuffer.wrap(entry.key, 32, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
                if (Integer.compareUnsigned(existing, next) >= 0)
                    next = existing + 1;
            }
        }
        return ByteBuffer.allocate(SPILL_KEY_SIZE).order(ByteOrder.BIG_ENDIAN).put(hash).putInt(next).array();
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        for (int index = 0; index < prefix.length; index++)
            if (value[index] != prefix[index])
                return false;
        return true;
    }

    private static int encodedInlineSize(List<NameEntry> entries) {
        int size = Long.BYTES + 1;
        for (NameEntry entry : entries)
            size += GUID_SIZE + 2 + entry.name.getBytes(StandardCharsets.UTF_8).length;
        return size;
    }

    private static byte[] encodeDirItem(DirItem item) {
        int size = encodedInlineSize(item.entries);
        if (size > INLINE_ENTRY_LIMIT || item.entries.size() > 255)
            throw new IllegalArgumentException("Inline directory item exceeds its size limit");
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).putLong(item.spillRoot)
                .put((byte) item.entries.size());
        for (NameEntry entry : item.entries) {
            buffer.put(guidBytes(entry.guid)).put((byte) entry.type.code);
            byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
            buffer.put((byte) name.length).put(name);
        }
        return buffer.array();
    }

    private static DirItem decodeDirItem(byte[] value) {
        if (value == null)
            return new DirItem(0, new ArrayList<>());
        if (value.length < Long.BYTES + 1)
            throw new IllegalStateException("Invalid directory item");
        ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        long spillRoot = buffer.getLong();
        int count = Byte.toUnsignedInt(buffer.get());
        List<NameEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (buffer.remaining() < GUID_SIZE + 2)
                throw new IllegalStateException("Truncated directory item");
            ShortGuid guid = readGuid(buffer);
            int type = Byte.toUnsignedInt(buffer.get());
            entries.add(
                    new NameEntry(guid, EntryType.fromCode(type), readName(buffer, Byte.toUnsignedInt(buffer.get()))));
        }
        if (buffer.hasRemaining())
            throw new IllegalStateException("Trailing bytes in directory item");
        return new DirItem(spillRoot, entries);
    }

    private static byte[] encodeSpillValue(NameEntry entry) {
        byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(GUID_SIZE + 1 + name.length).put(guidBytes(entry.guid)).put((byte) entry.type.code)
                .put(name).array();
    }

    private static NameEntry decodeSpillValue(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        ShortGuid guid = readGuid(buffer);
        EntryType type = EntryType.fromCode(Byte.toUnsignedInt(buffer.get()));
        return new NameEntry(guid, type, new String(remainingBytes(buffer), StandardCharsets.UTF_8));
    }

    private static byte[] encodeDirectoryIndex(NameEntry entry) {
        byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(GUID_SIZE + 1 + name.length).put(guidBytes(entry.guid)).put((byte) entry.type.code)
                .put(name).array();
    }

    private static DirectoryEntry decodeDirectoryIndex(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        ShortGuid guid = readGuid(buffer);
        EntryType type = EntryType.fromCode(Byte.toUnsignedInt(buffer.get()));
        return new DirectoryEntry(guid, type == EntryType.DIR_HEADER,
                new String(remainingBytes(buffer), StandardCharsets.UTF_8));
    }

    private static byte[] remainingBytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static byte[] key(ShortGuid guid, EntryType type, long offset) {
        return ByteBuffer.allocate(KEY_SIZE).order(ByteOrder.BIG_ENDIAN).put(guidBytes(guid)).put((byte) type.code)
                .putLong(offset).array();
    }

    private static byte[] guidBytes(ShortGuid guid) {
        return ByteBuffer.allocate(GUID_SIZE).order(ByteOrder.BIG_ENDIAN).putInt(guid.nodeId).putLong(guid.counter)
                .array();
    }

    private static void writeGuid(ByteBuffer buffer, ShortGuid guid) {
        buffer.put(guidBytes(guid));
    }

    private static void writeGuid(byte[] target, int offset, ShortGuid guid) {
        System.arraycopy(guidBytes(guid), 0, target, offset, GUID_SIZE);
    }

    private static ShortGuid readGuid(ByteBuffer buffer) {
        ShortGuid result = new ShortGuid();
        result.nodeId = buffer.getInt();
        result.counter = buffer.getLong();
        return result;
    }

    private static ShortGuid readParent(byte[] header) {
        return readGuid(ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN));
    }

    private static long offsetOf(byte[] key) {
        return ByteBuffer.wrap(key, GUID_SIZE + 1, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static boolean hasPrefix(byte[] key, ShortGuid guid, EntryType type) {
        byte[] prefix = key(guid, type, 0);
        for (int index = 0; index < GUID_SIZE + 1; index++)
            if (key[index] != prefix[index])
                return false;
        return true;
    }

    private static int compareKeys(byte[] left, byte[] right) {
        for (int index = 0; index < left.length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0)
                return comparison;
        }
        return 0;
    }

    private static void copyRange(byte[] source, long sourceOffset, long requestedOffset, byte[] target,
            int sourceLength) {
        long start = Math.max(sourceOffset, requestedOffset);
        long end = Math.min(sourceOffset + sourceLength, requestedOffset + target.length);
        if (end > start)
            System.arraycopy(source, (int) (start - sourceOffset), target, (int) (start - requestedOffset),
                    (int) (end - start));
    }

    private static int nameHash(String name) {
        CRC32C crc = new CRC32C();
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return (int) crc.getValue();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateName(String name) {
        java.util.Objects.requireNonNull(name);
        int length = name.getBytes(StandardCharsets.UTF_8).length;
        if (name.isEmpty() || length > 255 || name.indexOf('/') >= 0 || name.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid directory entry name");
    }

    private static String readName(ByteBuffer buffer, int length) {
        if (buffer.remaining() < length)
            throw new IllegalStateException("Truncated directory name");
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean sameGuid(ShortGuid left, ShortGuid right) {
        return left != null && right != null && left.nodeId == right.nodeId && left.counter == right.counter;
    }

    private static ShortGuid copyGuid(ShortGuid guid) {
        ShortGuid result = new ShortGuid();
        result.nodeId = guid.nodeId;
        result.counter = guid.counter;
        return result;
    }

    private static String formatGuid(ShortGuid guid) {
        return guid.nodeId + ":" + Long.toUnsignedString(guid.counter);
    }

    private static void requireOffset(long offset) {
        if (offset < 0)
            throw new IllegalArgumentException("File offset must not be negative: " + offset);
    }

    private static final class DirItem {
        long spillRoot;
        final List<NameEntry> entries;

        DirItem(long spillRoot, List<NameEntry> entries) {
            this.spillRoot = spillRoot;
            this.entries = entries;
        }
    }

    private static final class NameEntry {
        final ShortGuid guid;
        final EntryType type;
        final String name;

        NameEntry(ShortGuid guid, EntryType type, String name) {
            this.guid = guid;
            this.type = type;
            this.name = name;
        }
    }
}
