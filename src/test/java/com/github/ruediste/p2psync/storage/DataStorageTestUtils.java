package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;

final class DataStorageTestUtils {

    private DataStorageTestUtils() {
    }

    static boolean containsBlock(BTree<byte[], byte[]> tree, long offset) {
        byte[] key = ByteBuffer.allocate(Long.BYTES).putLong(offset).array();
        var iterator = tree.floorOrLowestIterator(key);
        if (!iterator.hasNext())
            return false;
        var extent = iterator.next();
        long start = ByteBuffer.wrap(extent.key).getLong();
        long size = ByteBuffer.wrap(extent.value).getLong();
        return offset >= start && offset < start + size;
    }
}