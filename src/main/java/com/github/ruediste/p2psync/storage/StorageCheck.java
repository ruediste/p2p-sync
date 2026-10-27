package com.github.ruediste.p2psync.storage;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.github.ruediste.p2psync.storage.BlockType.BlockRef;
import com.github.ruediste.p2psync.storage.DataStorage.Area;
import com.github.ruediste.p2psync.storage.DataStorage.PersistableBlockType;

/**
 * Read-only consistency check of the committed state of a {@link DataStorage}
 * file.
 *
 * <p>
 * Verifies that every block with a reference count is reachable from the root
 * block, that stored reference counts (ref-count tree plus the persisted
 * deferred updates) match the number of references, and that every block
 * of the file is accounted for exactly once: either allocated, on a deferred
 * free list, or contained in its area's free-space tree.
 *
 * <p>
 * Root reference handles must be registered on the storage before running the
 * check, otherwise the blocks below them cannot be traversed.
 */
public class StorageCheck {

    public enum BlockKind {
        /** reachable and ref counted */
        REFERENCED,
        /** reference count drops to zero with the persisted updates */
        PENDING_RELEASE,
        /** has a positive reference count, but is not reachable */
        UNREFERENCED,
        /** not ref counted bookkeeping list block */
        LIST,
        /** freed, will be added to the free-space tree in the next transaction */
        DEFERRED_FREE
    }

    public static class AreaStats {
        public int blockSize;
        public long freeBlocks;
        public final EnumMap<BlockKind, Long> blocks = new EnumMap<>(BlockKind.class);

        public long allocatedBlocks() {
            return blocks.entrySet().stream().filter(e -> e.getKey() != BlockKind.DEFERRED_FREE)
                    .mapToLong(Map.Entry::getValue).sum();
        }

        public long blocks(BlockKind kind) {
            return blocks.getOrDefault(kind, 0L);
        }
    }

    /**
     * Statistics of a group of B-trees, e.g. a single named tree or all subtrees of
     * a tree.
     */
    public static class TreeStats {
        public long trees;
        public int maxHeight;
        public long internalNodes;
        public long leafNodes;
        public long entries;
        public long usedBytes;
        public long totalBytes;

        public double fillPercent() {
            return totalBytes == 0 ? 0 : 100.0 * usedBytes / totalBytes;
        }
    }

    public static class Report {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final EnumMap<Area, AreaStats> stats = new EnumMap<>(Area.class);
        public final Map<String, TreeStats> treeStats = new TreeMap<>();
        public long fileLength;
        public long paddingBytes;

        public boolean isOk() {
            return errors.isEmpty();
        }

        public void printStats(PrintStream out) {
            out.printf("File length: %d bytes (superblock %d, alignment padding %d)%n", fileLength,
                    DataStorage.SUPERBLOCK_SIZE, paddingBytes);
            out.printf("%-7s %10s %12s %12s %12s %12s %12s %12s %12s%n", "Area", "BlockSize", "Free",
                    "Allocated", "Referenced", "PendingRel", "Unreferenced", "Lists", "DeferredFree");
            for (var entry : stats.entrySet()) {
                AreaStats s = entry.getValue();
                out.printf("%-7s %10d %12d %12d %12d %12d %12d %12d %12d%n", entry.getKey(), s.blockSize,
                        s.freeBlocks, s.allocatedBlocks(), s.blocks(BlockKind.REFERENCED),
                        s.blocks(BlockKind.PENDING_RELEASE), s.blocks(BlockKind.UNREFERENCED),
                        s.blocks(BlockKind.LIST), s.blocks(BlockKind.DEFERRED_FREE));
            }
            out.println();
            out.printf("%-40s %8s %7s %10s %10s %12s %6s%n", "B-tree", "Trees", "Height", "Internal",
                    "Leaves", "Entries", "Fill%");
            treeStats.forEach((name, s) -> out.printf("%-40s %8d %7d %10d %10d %12d %6.1f%n", name, s.trees,
                    s.maxHeight, s.internalNodes, s.leafNodes, s.entries, s.fillPercent()));
        }

        public void print(PrintStream out) {
            warnings.forEach(w -> out.println("WARNING: " + w));
            errors.forEach(e -> out.println("ERROR: " + e));
            printStats(out);
            out.println(isOk() ? "Check passed" : "Check FAILED with " + errors.size() + " error(s)");
        }
    }

    private record Claim(long offset, Area area, BlockKind kind, String description) {
    }

    private record Update(long value, Area area, Optional<BlockType<?>> type) {
    }

    private record Interval(long start, long end, Area area, String description) {
    }

    private final DataStorage storage;
    private final Report report = new Report();
    private final Map<Long, Claim> claims = new HashMap<>();
    private long fileLength;

    private StorageCheck(DataStorage storage) {
        this.storage = storage;
    }

    /**
     * Opens the file read-only on the given storage (which must have its root
     * reference handles registered), checks it and closes it again.
     */
    public static Report check(DataStorage storage, String path) {
        storage.openReadOnly(path);
        try {
            return new StorageCheck(storage).run();
        } finally {
            storage.close();
        }
    }

    private Report run() {
        RootBlock root = storage.rootBlock;
        fileLength = root.fileLength.value;
        report.fileLength = fileLength;
        try {
            if (storage.file.length() < fileLength)
                error("File is shorter (" + storage.file.length() + ") than its committed length " + fileLength);
        } catch (java.io.IOException e) {
            error("Cannot determine file length: " + e);
        }

        for (int i = 0; i < root.refHandles.handleIds.size(); i++) {
            int id = root.refHandles.handleIds.get(i);
            long offset = root.refHandles.blockOffsets.get(i);
            if (offset != 0 && !storage.rootRefHandles.containsKey(id))
                report.warnings.add("Root reference handle " + id + " (block " + offset
                        + ") is not registered, its blocks cannot be traversed");
        }

        Map<Long, Long> treeCounts = loadRefCountTree();
        Map<Long, Update> updates = loadUpdates(root.refCountUpdateList.value);
        checkRefCounts(treeCounts, updates);
        for (Area area : Area.values())
            checkExclusiveTree(storage.freeSpaceTrees.get(area), "free space tree " + area, treeCounts, updates);
        checkExclusiveTree(storage.refCountTree, "ref count tree", treeCounts, updates);

        for (Area area : Area.values())
            readList(root.getDeferredFreeListHead(area), "deferred free list (" + area + ")", Long.BYTES,
                    buffer -> claim(buffer.getLong(), area, BlockKind.DEFERRED_FREE, "deferred free block"));

        Set<Long> pendingMetaRemovals = new TreeSet<>();
        readList(root.pendingMetaFreeTreeRemovalList.value, "pending META free-tree removal list", Long.BYTES,
                buffer -> pendingMetaRemovals.add(buffer.getLong()));

        EnumMap<Area, TreeMap<Long, Long>> freeExtents = loadFreeExtents();
        removePendingMetaRemovals(freeExtents.get(Area.META), pendingMetaRemovals);

        checkCoverage(freeExtents);
        computeStats(freeExtents);
        return report;
    }

    private void error(String message) {
        report.errors.add(message);
    }

    private boolean isValidOffset(long offset, Area area) {
        int blockSize = storage.blockSize(area);
        return offset >= DataStorage.SUPERBLOCK_SIZE && offset % blockSize == 0 && offset <= fileLength - blockSize;
    }

    private boolean validOffset(long offset, Area area, String description) {
        if (!isValidOffset(offset, area)) {
            error("Invalid " + area + " offset for " + description + ": " + offset);
            return false;
        }
        return true;
    }

    private void claim(long offset, Area area, BlockKind kind, String description) {
        if (!validOffset(offset, area, description))
            return;
        var claim = new Claim(offset, area, kind, description);
        var existing = claims.putIfAbsent(offset, claim);
        if (existing != null)
            error("Block " + offset + " is used twice: as " + existing.area + " " + existing.description + " ("
                    + existing.kind + ") and as " + area + " " + description + " (" + kind + ")");
    }

    private Map<Long, Long> loadRefCountTree() {
        Map<Long, Long> counts = new HashMap<>();
        try {
            for (var entry : storage.refCountTree) {
                if (entry.value <= 0)
                    error("Ref-count tree contains non-positive count " + entry.value + " for block " + entry.key);
                counts.put(entry.key, entry.value);
            }
        } catch (RuntimeException e) {
            error("Failed to read ref-count tree: " + e);
        }
        return counts;
    }

    private Map<Long, Update> loadUpdates(long head) {
        Map<Long, Update> updates = new HashMap<>();
        readList(head, "ref-count update list", Long.BYTES + Long.BYTES + 2, buffer -> {
            long offset = buffer.getLong();
            long value = buffer.getLong();
            Area area = DataStorage.areaFromCode(Byte.toUnsignedInt(buffer.get()));
            int typeCode = Byte.toUnsignedInt(buffer.get());
            Optional<BlockType<?>> type = typeCode == 0 ? Optional.empty()
                    : Optional.of(PersistableBlockType.fromCode(typeCode).blockType.apply(storage));
            if (value < 0)
                error("Negative ref-count update for block " + offset);
            if (value == 0 && type.isEmpty())
                error("Zero ref-count update without block type for block " + offset);
            if (updates.put(offset, new Update(value, area, type)) != null)
                error("Duplicate ref-count update for block " + offset);
        });
        return updates;
    }

    /**
     * Reads a linked list of META blocks with the layout next(8), count(8),
     * entries. The list blocks themselves are claimed as {@link BlockKind#LIST}.
     */
    private void readList(long head, String name, int entrySize, Consumer<ByteBuffer> entryReader) {
        int capacity = (storage.blockSize(Area.META) - 2 * Long.BYTES) / entrySize;
        Set<Long> seen = new HashSet<>();
        long offset = head;
        try {
            while (offset != 0) {
                if (!seen.add(offset)) {
                    error("Cycle in " + name + " at block " + offset);
                    return;
                }
                if (!validOffset(offset, Area.META, name + " block"))
                    return;
                claim(offset, Area.META, BlockKind.LIST, name + " block");
                ByteBuffer buffer = ByteBuffer.wrap(storage.readBlock(offset, Area.META));
                long next = buffer.getLong();
                long count = buffer.getLong();
                if (count < 0 || count > capacity) {
                    error("Invalid entry count " + count + " in " + name + " block " + offset);
                    return;
                }
                for (int i = 0; i < count; i++)
                    entryReader.accept(buffer);
                offset = next;
            }
        } catch (RuntimeException e) {
            error("Failed to read " + name + " at block " + offset + ": " + e);
        }
    }

    /**
     * Traverses all blocks reachable from the root block and from blocks pending
     * release, counting references, and compares the counts with the stored ones.
     */
    private void checkRefCounts(Map<Long, Long> treeCounts, Map<Long, Update> updates) {
        Map<Long, BlockRef> visited = new HashMap<>();
        Map<Long, Long> expected = new HashMap<>();
        ArrayDeque<BlockRef> queue = new ArrayDeque<>();
        Consumer<BlockRef> visit = ref -> {
            var existing = visited.putIfAbsent(ref.blockOffset(), ref);
            if (existing == null)
                queue.add(ref);
            else if (existing.area() != ref.area())
                error("Block " + ref.blockOffset() + " is referenced as " + existing.area() + " and " + ref.area());
        };

        // B-tree node offset -> name of the tree group it belongs to; tree roots are
        // tracked separately to count trees and heights
        Map<Long, String> treeOf = new HashMap<>();
        Set<Long> treeRoots = new HashSet<>();
        Map<Long, String> rootNames = rootTreeNames();

        var rootRef = new BlockRef(storage.superBlock.rootBlock, Area.SYSTEM, RootBlock.getBlockType(storage));
        expected.put(rootRef.blockOffset(), 1L);
        visit.accept(rootRef);

        // blocks released by the persisted updates still hold references to their
        // children
        updates.forEach((offset, update) -> {
            long stored = treeCounts.getOrDefault(offset, 0L);
            if (stored > 0 && update.value == 0 && update.type.isPresent()) {
                var ref = new BlockRef(offset, update.area, update.type.get());
                if (ref.type() instanceof BTree.NodeBlockType && treeOf.putIfAbsent(offset, "pending release") == null)
                    treeRoots.add(offset);
                visit.accept(ref);
            }
        });

        while (!queue.isEmpty()) {
            BlockRef ref = queue.poll();
            if (!validOffset(ref.blockOffset(), ref.area(), "referenced block"))
                continue;
            List<BlockRef> children;
            String tree = treeOf.get(ref.blockOffset());
            BTree.NodeInfo node = null;
            try {
                byte[] data = storage.readBlock(ref.blockOffset(), ref.area());
                children = references(ref, data);
                if (ref.type() instanceof BTree.NodeBlockType nodeType) {
                    node = nodeType.nodeInfo(data);
                    addTreeNode(tree, node, data.length, treeRoots.contains(ref.blockOffset()));
                }
            } catch (RuntimeException e) {
                error("Failed to read " + ref.area() + " block " + ref.blockOffset() + ": " + e);
                continue;
            }
            for (BlockRef child : children) {
                if (child.blockOffset() == 0)
                    continue;
                expected.merge(child.blockOffset(), 1L, Long::sum);
                if (child.type() instanceof BTree.NodeBlockType && !treeOf.containsKey(child.blockOffset())) {
                    if (node != null && node.level() > 0 && child.type() == ref.type())
                        treeOf.put(child.blockOffset(), tree);
                    else {
                        treeOf.put(child.blockOffset(), rootNames.getOrDefault(child.blockOffset(),
                                tree != null ? "subtrees of " + tree : "other trees"));
                        treeRoots.add(child.blockOffset());
                    }
                }
                visit.accept(child);
            }
        }

        Set<Long> offsets = new TreeSet<>(visited.keySet());
        offsets.addAll(treeCounts.keySet());
        offsets.addAll(updates.keySet());
        for (long offset : offsets) {
            long stored = treeCounts.getOrDefault(offset, 0L);
            Update update = updates.get(offset);
            long effective = update == null ? stored : update.value;
            if (effective < 0)
                error("Negative effective ref count " + effective + " for block " + offset);
            BlockRef ref = visited.get(offset);
            if (ref != null) {
                if (!isValidOffset(offset, ref.area()))
                    continue;
                long expectedCount = expected.getOrDefault(offset, 0L);
                if (effective != expectedCount)
                    error("Ref count mismatch for " + ref.area() + " block " + offset + ": stored=" + stored
                            + ", update=" + (update == null ? 0 : update.value) + ", references="
                            + expectedCount);
                claim(offset, ref.area(), effective > 0 ? BlockKind.REFERENCED : BlockKind.PENDING_RELEASE,
                        "ref counted block");
            } else if (effective > 0) {
                if (update != null) {
                    error("Unreferenced " + update.area + " block " + offset + " with ref count " + effective);
                    claim(offset, update.area, BlockKind.UNREFERENCED, "unreferenced block");
                } else
                    error("Unreferenced block " + offset + " (unknown area) with ref count " + effective);
            }
        }
    }

    private void checkExclusiveTree(BTree<?, ?> tree, String name, Map<Long, Long> counts,
            Map<Long, Update> updates) {
        if (tree.rootOffset == 0)
            return;
        var pending = new ArrayDeque<Long>();
        var visited = new HashSet<Long>();
        pending.add(tree.rootOffset);
        while (!pending.isEmpty()) {
            long offset = pending.remove();
            // Unlike counted trees, an exclusive tree cannot have shared nodes;
            // revisiting one indicates multiple owners or a cycle.
            if (!visited.add(offset)) {
                error("Exclusive tree block " + offset + " has multiple owners in " + name);
                continue;
            }
            if (!validOffset(offset, Area.META, name))
                continue;
            // Uncounted does not mean free: these nodes still occupy allocated space.
            claim(offset, Area.META, BlockKind.REFERENCED, "exclusive block of " + name);
            if (counts.containsKey(offset) || updates.containsKey(offset))
                error("Exclusive tree block " + offset + " has a reference count");
            try {
                var block = storage.readBlock(offset, Area.META, tree.blockType);
                var type = (BTree.NodeBlockType) tree.blockType;
                var info = type.nodeInfo(block.data);
                addTreeNode(name, info, block.data.length, offset == tree.rootOffset);
                for (var child : type.getReferencedBlocks(block, storage))
                    pending.add(child.blockOffset());
            } catch (RuntimeException exception) {
                error("Failed to read exclusive " + name + " block " + offset + ": " + exception);
            }
        }
    }

    private <T> List<BlockRef> references(BlockRef ref, byte[] data) {
        @SuppressWarnings("unchecked")
        BlockType<T> type = (BlockType<T>) ref.type();
        T value = type.deserialize(ByteBuffer.wrap(data));
        return type.getReferencedBlocks(value, storage);
    }

    private Map<Long, String> rootTreeNames() {
        RootBlock root = storage.rootBlock;
        Map<Long, String> names = new HashMap<>();
        for (Area area : Area.values())
            names.put(root.getFreeTreeRoot(area), "free space tree " + area);
        names.put(root.refCountTree.blockOffset, "ref count tree");
        for (int i = 0; i < root.refHandles.handleIds.size(); i++)
            names.put(root.refHandles.blockOffsets.get(i), "root handle " + root.refHandles.handleIds.get(i));
        return names;
    }

    private void addTreeNode(String tree, BTree.NodeInfo node, int blockSize, boolean isRoot) {
        var stats = report.treeStats.computeIfAbsent(tree == null ? "other trees" : tree, name -> new TreeStats());
        if (isRoot) {
            stats.trees++;
            stats.maxHeight = Math.max(stats.maxHeight, node.level() + 1);
        }
        if (node.level() == 0) {
            stats.leafNodes++;
            stats.entries += node.itemCount();
        } else
            stats.internalNodes++;
        stats.usedBytes += node.usedBytes();
        stats.totalBytes += blockSize;
    }

    private EnumMap<Area, TreeMap<Long, Long>> loadFreeExtents() {
        EnumMap<Area, TreeMap<Long, Long>> result = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            TreeMap<Long, Long> extents = new TreeMap<>();
            result.put(area, extents);
            int blockSize = storage.blockSize(area);
            try {
                for (var entry : storage.freeSpaceTrees.get(area)) {
                    if (entry.key.length != Long.BYTES || entry.value.length != Long.BYTES) {
                        error("Invalid " + area + " free-space tree entry size");
                        continue;
                    }
                    long start = ByteBuffer.wrap(entry.key).getLong();
                    long size = ByteBuffer.wrap(entry.value).getLong();
                    if (start < DataStorage.SUPERBLOCK_SIZE || size <= 0 || start % blockSize != 0
                            || size % blockSize != 0 || size > fileLength - start) {
                        error("Invalid " + area + " free extent [" + start + ", +" + size + ")");
                        continue;
                    }
                    extents.put(start, start + size);
                }
            } catch (RuntimeException e) {
                error("Failed to read " + area + " free-space tree: " + e);
            }
        }
        return result;
    }

    /**
     * Pending META removals are allocated blocks which are still contained in the
     * META free-space tree. Cut them out of the free extents.
     */
    private void removePendingMetaRemovals(TreeMap<Long, Long> extents, Set<Long> pending) {
        int blockSize = storage.blockSize(Area.META);
        for (long offset : pending) {
            var claim = claims.get(offset);
            if (claim == null || claim.area != Area.META || claim.kind == BlockKind.DEFERRED_FREE)
                error("Pending META free-tree removal " + offset + " is not an allocated META block");
            var extent = extents.floorEntry(offset);
            if (extent == null || extent.getValue() < offset + blockSize) {
                error("Pending META free-tree removal " + offset + " is not contained in the META free-space tree");
                continue;
            }
            extents.remove(extent.getKey());
            if (extent.getKey() < offset)
                extents.put(extent.getKey(), offset);
            if (offset + blockSize < extent.getValue())
                extents.put(offset + blockSize, extent.getValue());
        }
    }

    /**
     * Verifies that free extents and claimed blocks cover the whole file without
     * overlaps. Gaps are only allowed as alignment padding.
     */
    private void checkCoverage(EnumMap<Area, TreeMap<Long, Long>> freeExtents) {
        List<Interval> intervals = new ArrayList<>();
        for (Claim claim : claims.values())
            intervals.add(new Interval(claim.offset, claim.offset + storage.blockSize(claim.area), claim.area,
                    claim.area + " " + claim.description + " " + claim.offset));
        freeExtents.forEach((area, extents) -> extents.forEach((start, end) -> intervals
                .add(new Interval(start, end, area, area + " free extent [" + start + ", " + end + ")"))));
        intervals.sort(Comparator.comparingLong(Interval::start));

        long pos = DataStorage.SUPERBLOCK_SIZE;
        Interval previous = null;
        for (Interval interval : intervals) {
            if (interval.start < pos) {
                error(interval.description + " overlaps " + previous.description);
            } else if (interval.start > pos) {
                int blockSize = storage.blockSize(interval.area);
                if (interval.start - pos < blockSize && interval.start % blockSize == 0)
                    report.paddingBytes += interval.start - pos;
                else
                    error("Unaccounted space [" + pos + ", " + interval.start + ")");
            }
            if (interval.end > pos) {
                pos = interval.end;
                previous = interval;
            }
        }
        if (pos < fileLength)
            error("Unaccounted space [" + pos + ", " + fileLength + ")");
    }

    private void computeStats(EnumMap<Area, TreeMap<Long, Long>> freeExtents) {
        for (Area area : Area.values()) {
            var stats = new AreaStats();
            stats.blockSize = storage.blockSize(area);
            for (var extent : freeExtents.get(area).entrySet())
                stats.freeBlocks += (extent.getValue() - extent.getKey()) / stats.blockSize;
            report.stats.put(area, stats);
        }
        for (Claim claim : claims.values())
            report.stats.get(claim.area).blocks.merge(claim.kind, 1L, Long::sum);
    }
}
