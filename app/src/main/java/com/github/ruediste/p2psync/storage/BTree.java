package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a B+ Tree stored in a block space. Keys are of fixed size, the
 * data size is variable, but relatively small compared to the block size.
 * 
 * The inner nodes contain only keys and child pointers, while the leaf nodes
 * contain keys and associated values.
 */
public class BTree {

    /*
     * Data Structures ===============
     * 
     * Common Header
     * - ref count: 4 bytes
     * - node type: 1 byte (inner or leaf)
     * - number of keys: 2 bytes (keys in this node)
     * 
     * Inner Node
     * - common Header (7 bytes)
     * - leftmost child pointer: 8 bytes
     * - array of key/child pointer pairs follows the leftmost child pointer
     * 
     * Leaf Node
     * - Common Header (7 bytes)
     * - next leaf pointer: 8 bytes
     * - offsets: array of 2-byte offsets for each key/value pair
     * - key/value pairs follow the offsets array
     * 
     * The offsets are relative to the start of the key/value pairs section. The
     * offsets array does not contain an entry for the first key/value pair, as
     * its offset is implicitly 0. It however contains an additional offset past
     * the last key/value pair, allowing to derive the length of the last value.
     * For n key/value pairs the array therefore holds n entries: entry i is the
     * end offset (and thus the start offset of pair i+1) of pair i. Each
     * key/value pair consists of the key followed by the value.
     * 
     * A delete rebalances the tree: if a node falls below half of the block
     * size it either borrows entries from a sibling or is merged with it.
     * Empty inner nodes are removed and the root is shrunk when it ends up
     * with a single child.
     */
    private static final byte NODE_TYPE_LEAF = 0;
    private static final byte NODE_TYPE_INNER = 1;

    private static final int HEADER_SIZE = 4 + 1 + 2;
    private static final int LEAF_KV_START_OFFSET = HEADER_SIZE + 8;

    private BlockSpace space;
    private long rootBlock;
    private int keySize;

    public BTree(BlockSpace space, long rootBlock, int keySize) {
        this.space = space;
        this.rootBlock = rootBlock;
        this.keySize = keySize;
    }

    public static BTree create(BlockSpace space, int keySize) {
        long rootBlock = space.allocateBlocks(1)[0];
        BTree tree = new BTree(space, rootBlock, keySize);
        tree.initializeEmptyLeaf(rootBlock);
        return tree;
    }

    private void initializeEmptyLeaf(long blockId) {
        byte[] data = new byte[space.getBlockSize()];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putInt(1); // ref count
        buf.put(NODE_TYPE_LEAF);
        buf.putShort((short) 0); // number of keys
        buf.putLong(0L); // next leaf pointer
        space.writeBlock(blockId, data);
    }

    public long getRootBlock() {
        return rootBlock;
    }

    public void insert(byte[] key, byte[] value) {
        checkKey(key);
        if (LEAF_KV_START_OFFSET + 2 + keySize + value.length > space.getBlockSize()) {
            throw new IllegalArgumentException("Key/value pair does not fit into a block");
        }
        SplitResult res = insertInternal(rootBlock, key, value);
        if (res != null) {
            // Root split occurred, create a new root
            long newRoot = space.allocateBlocks(1)[0];
            byte[][] keys = new byte[][] { res.splitKey };
            long[] children = new long[] { rootBlock, res.newChildBlock };
            writeInner(newRoot, keys, children);
            rootBlock = newRoot;
        }
    }

    private static class SplitResult {
        final byte[] splitKey;
        final long newChildBlock;

        SplitResult(byte[] splitKey, long newChildBlock) {
            this.splitKey = splitKey;
            this.newChildBlock = newChildBlock;
        }
    }

    private SplitResult insertInternal(long blockId, byte[] key, byte[] value) {
        if (isLeaf(blockId)) {
            return insertIntoLeaf(blockId, key, value);
        } else {
            Inner inner = readInner(blockId);
            int childIdx = findChildIndex(inner.keys, key);
            SplitResult res = insertInternal(inner.children[childIdx], key, value);
            if (res == null) {
                return null;
            }

            // insert the split key and the new child into this inner node
            int n = inner.keys.length;
            byte[][] newKeys = new byte[n + 1][];
            long[] newChildren = new long[n + 2];
            System.arraycopy(inner.keys, 0, newKeys, 0, childIdx);
            newKeys[childIdx] = res.splitKey;
            System.arraycopy(inner.keys, childIdx, newKeys, childIdx + 1, n - childIdx);
            System.arraycopy(inner.children, 0, newChildren, 0, childIdx + 1);
            newChildren[childIdx + 1] = res.newChildBlock;
            System.arraycopy(inner.children, childIdx + 1, newChildren, childIdx + 2, n - childIdx);

            if (innerNodeSize(newKeys) <= space.getBlockSize()) {
                writeInner(blockId, newKeys, newChildren);
                return null;
            }

            // split the inner node, the middle key moves up
            int totalKeys = newKeys.length;
            int mid = totalKeys / 2;
            byte[] upKey = newKeys[mid];

            byte[][] leftKeys = Arrays.copyOf(newKeys, mid);
            long[] leftChildren = Arrays.copyOf(newChildren, mid + 1);

            byte[][] rightKeys = Arrays.copyOfRange(newKeys, mid + 1, totalKeys);
            long[] rightChildren = Arrays.copyOfRange(newChildren, mid + 1, newChildren.length);

            long rightBlock = space.allocateBlocks(1)[0];
            writeInner(blockId, leftKeys, leftChildren);
            writeInner(rightBlock, rightKeys, rightChildren);
            return new SplitResult(upKey, rightBlock);
        }
    }

    private SplitResult insertIntoLeaf(long blockId, byte[] key, byte[] value) {
        Leaf leaf = readLeaf(blockId);
        int n = leaf.keys.length;

        // find the insert position
        int insertPos = 0;
        while (insertPos < n && compareKeys(key, leaf.keys[insertPos]) > 0) {
            insertPos++;
        }

        if (insertPos < n && Arrays.equals(key, leaf.keys[insertPos])) {
            // update existing value
            leaf.values[insertPos] = value;
        } else {
            leaf.keys = insertIntoArray(leaf.keys, insertPos, key);
            leaf.values = insertIntoArray(leaf.values, insertPos, value);
        }

        if (leafSize(leaf) > space.getBlockSize()) {
            // split the leaf, the first key of the right node is copied up
            int total = leaf.keys.length;
            int mid = total / 2;

            Leaf right = new Leaf();
            right.keys = Arrays.copyOfRange(leaf.keys, mid, total);
            right.values = Arrays.copyOfRange(leaf.values, mid, total);
            right.nextLeaf = leaf.nextLeaf;

            leaf.keys = Arrays.copyOf(leaf.keys, mid);
            leaf.values = Arrays.copyOf(leaf.values, mid);

            long rightBlock = space.allocateBlocks(1)[0];
            leaf.nextLeaf = rightBlock;
            writeLeaf(blockId, leaf);
            writeLeaf(rightBlock, right);
            return new SplitResult(right.keys[0], rightBlock);
        }

        writeLeaf(blockId, leaf);
        return null;
    }

    public void delete(byte[] key) {
        checkKey(key);
        deleteInternal(rootBlock, key);

        // shrink the root if it is an inner node left with a single child
        if (!isLeaf(rootBlock)) {
            Inner root = readInner(rootBlock);
            if (root.keys.length == 0) {
                long oldRoot = rootBlock;
                rootBlock = root.children[0];
                space.freeBlocks(new long[] { oldRoot });
            }
        }
    }

    /**
     * Deletes the key from the subtree rooted at the given block. Returns true
     * if the node underflowed (fell below half of the block size) and therefore
     * needs to be rebalanced by its parent.
     */
    private boolean deleteInternal(long blockId, byte[] key) {
        if (isLeaf(blockId)) {
            Leaf leaf = readLeaf(blockId);
            int idx = -1;
            for (int i = 0; i < leaf.keys.length; i++) {
                if (Arrays.equals(key, leaf.keys[i])) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                // key not present, nothing to do
                return false;
            }
            leaf.keys = removeFromArray(leaf.keys, idx);
            leaf.values = removeFromArray(leaf.values, idx);
            writeLeaf(blockId, leaf);
            return leafSize(leaf) < minNodeSize();
        } else {
            Inner inner = readInner(blockId);
            int childIdx = findChildIndex(inner.keys, key);
            boolean underflow = deleteInternal(inner.children[childIdx], key);
            if (!underflow) {
                return false;
            }
            rebalanceChild(inner, childIdx);
            writeInner(blockId, inner.keys, inner.children);
            return innerNodeSize(inner.keys) < minNodeSize();
        }
    }

    private int minNodeSize() {
        return space.getBlockSize() / 2;
    }

    private int nodeSize(long blockId) {
        if (isLeaf(blockId)) {
            return leafSize(readLeaf(blockId));
        }
        return innerNodeSize(readInner(blockId).keys);
    }

    /**
     * Rebalances the underflowing child at the given index of the parent,
     * either by merging it with a sibling or by borrowing entries from a
     * sibling. The parent is updated in place but not written back; the caller
     * is responsible for persisting it.
     */
    private void rebalanceChild(Inner parent, int childIdx) {
        long childBlock = parent.children[childIdx];
        if (nodeSize(childBlock) >= minNodeSize()) {
            return;
        }

        boolean useLeft;
        int sibIdx;
        if (childIdx > 0) {
            sibIdx = childIdx - 1;
            useLeft = true;
        } else if (childIdx + 1 < parent.children.length) {
            sibIdx = childIdx + 1;
            useLeft = false;
        } else {
            // only child, parent will shrink/bubble up instead
            return;
        }

        boolean leaf = isLeaf(childBlock);
        if (borrowFromSibling(parent, childIdx, useLeft, leaf)) {
            return;
        }

        // the sibling is at its minimum occupancy, merge if the combined node fits
        int sharedOverhead = leaf ? LEAF_KV_START_OFFSET : HEADER_SIZE + 8;
        int combined = nodeSize(childBlock) + nodeSize(parent.children[sibIdx]) - sharedOverhead;
        if (!leaf) {
            // the separator from the parent is pushed down into the merged inner node
            combined += keySize + 8;
        }

        if (combined <= space.getBlockSize()) {
            mergeWithSibling(parent, childIdx, useLeft, leaf);
        }
    }

    private void mergeWithSibling(Inner parent, int childIdx, boolean useLeft, boolean leaf) {
        int sibIdx = useLeft ? childIdx - 1 : childIdx + 1;
        long leftBlock = parent.children[useLeft ? sibIdx : childIdx];
        long rightBlock = parent.children[useLeft ? childIdx : sibIdx];
        int removedChild = useLeft ? childIdx : childIdx + 1;
        int removedSep = useLeft ? childIdx - 1 : childIdx;

        if (leaf) {
            Leaf left = readLeaf(leftBlock);
            Leaf right = readLeaf(rightBlock);
            left.keys = concat(left.keys, right.keys);
            left.values = concat(left.values, right.values);
            left.nextLeaf = right.nextLeaf;
            writeLeaf(leftBlock, left);
        } else {
            Inner left = readInner(leftBlock);
            Inner right = readInner(rightBlock);
            left.keys = concatWithSeparator(left.keys, parent.keys[removedSep], right.keys);
            left.children = concat(left.children, right.children);
            writeInner(leftBlock, left.keys, left.children);
        }

        space.freeBlocks(new long[] { rightBlock });
        parent.keys = removeFromArray(parent.keys, removedSep);
        parent.children = removeFromLongArray(parent.children, removedChild);
    }

    private boolean borrowFromSibling(Inner parent, int childIdx, boolean useLeft, boolean leaf) {
        int sibIdx = useLeft ? childIdx - 1 : childIdx + 1;
        long childBlock = parent.children[childIdx];
        long sibBlock = parent.children[sibIdx];
        boolean moved = false;

        if (leaf) {
            Leaf child = readLeaf(childBlock);
            Leaf sib = readLeaf(sibBlock);
            while (leafSize(child) < minNodeSize() && sib.keys.length > 1) {
                if (useLeft) {
                    int last = sib.keys.length - 1;
                    if (leafSize(sib) - (keySize + sib.values[last].length) < minNodeSize()) {
                        break;
                    }
                    child.keys = prepend(child.keys, sib.keys[last]);
                    child.values = prepend(child.values, sib.values[last]);
                    sib.keys = removeFromArray(sib.keys, last);
                    sib.values = removeFromArray(sib.values, last);
                    parent.keys[childIdx - 1] = child.keys[0];
                } else {
                    if (leafSize(sib) - (keySize + sib.values[0].length) < minNodeSize()) {
                        break;
                    }
                    child.keys = append(child.keys, sib.keys[0]);
                    child.values = append(child.values, sib.values[0]);
                    sib.keys = removeFromArray(sib.keys, 0);
                    sib.values = removeFromArray(sib.values, 0);
                    parent.keys[childIdx] = sib.keys[0];
                }
                moved = true;
            }
            if (moved) {
                writeLeaf(childBlock, child);
                writeLeaf(sibBlock, sib);
            }
        } else {
            Inner child = readInner(childBlock);
            Inner sib = readInner(sibBlock);
            while (innerNodeSize(child.keys) < minNodeSize() && sib.keys.length > 1) {
                if (innerNodeSize(sib.keys) - (keySize + 8) < minNodeSize()) {
                    break;
                }
                if (useLeft) {
                    int last = sib.keys.length - 1;
                    long movedChild = sib.children[last + 1];
                    byte[] movedKey = sib.keys[last];
                    sib.keys = removeFromArray(sib.keys, last);
                    sib.children = removeFromLongArray(sib.children, last + 1);
                    child.keys = prepend(child.keys, parent.keys[childIdx - 1]);
                    child.children = prepend(child.children, movedChild);
                    parent.keys[childIdx - 1] = movedKey;
                } else {
                    long movedChild = sib.children[0];
                    byte[] movedKey = sib.keys[0];
                    sib.keys = removeFromArray(sib.keys, 0);
                    sib.children = removeFromLongArray(sib.children, 0);
                    child.keys = append(child.keys, parent.keys[childIdx]);
                    child.children = append(child.children, movedChild);
                    parent.keys[childIdx] = movedKey;
                }
                moved = true;
            }
            if (moved) {
                writeInner(childBlock, child.keys, child.children);
                writeInner(sibBlock, sib.keys, sib.children);
            }
        }
        return moved;
    }

    public byte[] search(byte[] key) {
        checkKey(key);
        return searchRecursive(rootBlock, key);
    }

    private byte[] searchRecursive(long blockId, byte[] key) {
        if (isLeaf(blockId)) {
            Leaf leaf = readLeaf(blockId);
            for (int i = 0; i < leaf.keys.length; i++) {
                int cmp = compareKeys(key, leaf.keys[i]);
                if (cmp == 0) {
                    return leaf.values[i];
                } else if (cmp < 0) {
                    return null;
                }
            }
            return null;
        } else {
            Inner inner = readInner(blockId);
            int childIdx = findChildIndex(inner.keys, key);
            return searchRecursive(inner.children[childIdx], key);
        }
    }

    private void checkKey(byte[] key) {
        if (key == null || key.length != keySize) {
            throw new IllegalArgumentException("Invalid key size");
        }
    }

    /**
     * Returns the index of the child to follow for the given key. Keys equal
     * to a separator key live in the right subtree.
     */
    private int findChildIndex(byte[][] keys, byte[] key) {
        int idx = 0;
        while (idx < keys.length && compareKeys(key, keys[idx]) >= 0) {
            idx++;
        }
        return idx;
    }

    private boolean isLeaf(long blockId) {
        byte[] data = space.readBlock(blockId);
        return data[4] == NODE_TYPE_LEAF;
    }

    private static class Leaf {
        long nextLeaf;
        byte[][] keys;
        byte[][] values;
    }

    private Leaf readLeaf(long blockId) {
        byte[] data = space.readBlock(blockId);
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.getInt(); // ref count
        byte nodeType = buf.get();
        if (nodeType != NODE_TYPE_LEAF) {
            throw new IllegalStateException("Expected leaf node, got " + nodeType);
        }
        int numKeys = buf.getShort() & 0xFFFF;
        Leaf leaf = new Leaf();
        leaf.nextLeaf = buf.getLong();

        // entry i starts at (i == 0 ? 0 : offsets[i - 1]) and ends at offsets[i]
        short[] offsets = new short[numKeys];
        for (int i = 0; i < numKeys; i++) {
            offsets[i] = buf.getShort();
        }
        int kvStart = buf.position();

        leaf.keys = new byte[numKeys][];
        leaf.values = new byte[numKeys][];
        for (int i = 0; i < numKeys; i++) {
            int start = (i == 0) ? 0 : offsets[i - 1] & 0xFFFF;
            int end = offsets[i] & 0xFFFF;
            buf.position(kvStart + start);
            byte[] key = new byte[keySize];
            buf.get(key);
            byte[] value = new byte[end - start - keySize];
            buf.get(value);
            leaf.keys[i] = key;
            leaf.values[i] = value;
        }
        return leaf;
    }

    private void writeLeaf(long blockId, Leaf leaf) {
        int numKeys = leaf.keys.length;
        int kvSize = 0;
        for (int i = 0; i < numKeys; i++) {
            kvSize += keySize + leaf.values[i].length;
        }
        if (kvSize > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Key/value data too large for a leaf node");
        }

        byte[] data = new byte[space.getBlockSize()];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putInt(1); // ref count
        buf.put(NODE_TYPE_LEAF);
        buf.putShort((short) numKeys);
        buf.putLong(leaf.nextLeaf);

        int offset = 0;
        for (int i = 0; i < numKeys; i++) {
            offset += keySize + leaf.values[i].length;
            buf.putShort((short) offset);
        }

        for (int i = 0; i < numKeys; i++) {
            buf.put(leaf.keys[i]);
            buf.put(leaf.values[i]);
        }
        space.writeBlock(blockId, data);
    }

    private int leafSize(Leaf leaf) {
        int size = LEAF_KV_START_OFFSET + 2 * leaf.keys.length;
        for (int i = 0; i < leaf.keys.length; i++) {
            size += keySize + leaf.values[i].length;
        }
        return size;
    }

    private static class Inner {
        byte[][] keys;
        long[] children;
    }

    private Inner readInner(long blockId) {
        byte[] data = space.readBlock(blockId);
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.getInt(); // ref count
        byte nodeType = buf.get();
        if (nodeType != NODE_TYPE_INNER) {
            throw new IllegalStateException("Expected inner node, got " + nodeType);
        }
        int numKeys = buf.getShort() & 0xFFFF;
        Inner inner = new Inner();
        inner.keys = new byte[numKeys][];
        inner.children = new long[numKeys + 1];
        inner.children[0] = buf.getLong();
        for (int i = 0; i < numKeys; i++) {
            byte[] key = new byte[keySize];
            buf.get(key);
            inner.keys[i] = key;
            inner.children[i + 1] = buf.getLong();
        }
        return inner;
    }

    private void writeInner(long blockId, byte[][] keys, long[] children) {
        byte[] data = new byte[space.getBlockSize()];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putInt(1); // ref count
        buf.put(NODE_TYPE_INNER);
        buf.putShort((short) keys.length);
        buf.putLong(children[0]);
        for (int i = 0; i < keys.length; i++) {
            buf.put(keys[i]);
            buf.putLong(children[i + 1]);
        }
        space.writeBlock(blockId, data);
    }

    private int innerNodeSize(byte[][] keys) {
        return HEADER_SIZE + 8 + keys.length * (keySize + 8);
    }

    private static byte[][] insertIntoArray(byte[][] array, int idx, byte[] element) {
        byte[][] result = new byte[array.length + 1][];
        System.arraycopy(array, 0, result, 0, idx);
        result[idx] = element;
        System.arraycopy(array, idx, result, idx + 1, array.length - idx);
        return result;
    }

    private static byte[][] removeFromArray(byte[][] array, int idx) {
        byte[][] result = new byte[array.length - 1][];
        System.arraycopy(array, 0, result, 0, idx);
        System.arraycopy(array, idx + 1, result, idx, array.length - idx - 1);
        return result;
    }

    private static long[] removeFromLongArray(long[] array, int idx) {
        long[] result = new long[array.length - 1];
        System.arraycopy(array, 0, result, 0, idx);
        System.arraycopy(array, idx + 1, result, idx, array.length - idx - 1);
        return result;
    }

    private static byte[][] concat(byte[][] a, byte[][] b) {
        byte[][] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static long[] concat(long[] a, long[] b) {
        long[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[][] concatWithSeparator(byte[][] a, byte[] separator, byte[][] b) {
        byte[][] result = new byte[a.length + 1 + b.length][];
        System.arraycopy(a, 0, result, 0, a.length);
        result[a.length] = separator;
        System.arraycopy(b, 0, result, a.length + 1, b.length);
        return result;
    }

    private static byte[][] prepend(byte[][] array, byte[] element) {
        byte[][] result = new byte[array.length + 1][];
        result[0] = element;
        System.arraycopy(array, 0, result, 1, array.length);
        return result;
    }

    private static byte[][] append(byte[][] array, byte[] element) {
        byte[][] result = Arrays.copyOf(array, array.length + 1);
        result[array.length] = element;
        return result;
    }

    private static long[] prepend(long[] array, long element) {
        long[] result = new long[array.length + 1];
        result[0] = element;
        System.arraycopy(array, 0, result, 1, array.length);
        return result;
    }

    private static long[] append(long[] array, long element) {
        long[] result = Arrays.copyOf(array, array.length + 1);
        result[array.length] = element;
        return result;
    }

    private int compareKeys(byte[] k1, byte[] k2) {
        // keys are compared lexicographically as unsigned byte sequences
        for (int i = 0; i < keySize; i++) {
            int cmp = (k1[i] & 0xFF) - (k2[i] & 0xFF);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
