package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import java.util.Arrays;

/**
 * Minimal growable byte buffer with independent reader/writer cursors.
 *
 * <p>
 * This project used to depend on Netty's {@code io.netty.buffer.ByteBuf}/{@code Unpooled} for
 * exactly this purpose. Netty has since been dropped entirely (see the "Technology choices"
 * section of {@code ImplementationPlan.md} — the transport/stream layer now uses plain blocking
 * JDK I/O on virtual threads instead), so this class reimplements the tiny subset of that API
 * actually used here: sequential big-endian writes with auto-growth, sequential reads with a
 * rewindable reader index (needed by {@link Varint#readUvarint} to "un-read" an incomplete
 * varint), and a couple of read-only view helpers used by tests.
 *
 * <p>
 * Not thread-safe; instances are short-lived, single-use encode/decode scratch buffers.
 */
public final class ByteBuf {

    private byte[] array;
    private int writerIndex;
    private int readerIndex;

    private ByteBuf(byte[] array, int writerIndex, int readerIndex) {
        this.array = array;
        this.writerIndex = writerIndex;
        this.readerIndex = readerIndex;
    }

    /** A new, empty, writable buffer with a small default initial capacity. */
    public static ByteBuf buffer() {
        return buffer(64);
    }

    /** A new, empty, writable buffer with the given initial capacity. */
    public static ByteBuf buffer(int initialCapacity) {
        return new ByteBuf(new byte[Math.max(initialCapacity, 1)], 0, 0);
    }

    /**
     * A read-only view over {@code bytes} (not copied): reader index starts at {@code 0},
     * writer index (i.e. the readable limit) is {@code bytes.length}.
     */
    public static ByteBuf wrappedBuffer(byte[] bytes) {
        return new ByteBuf(bytes, bytes.length, 0);
    }

    private void ensureWritable(int extra) {
        if (writerIndex + extra > array.length) {
            array = Arrays.copyOf(array, Math.max(array.length * 2, writerIndex + extra));
        }
    }

    public ByteBuf writeByte(int value) {
        ensureWritable(1);
        array[writerIndex++] = (byte) value;
        return this;
    }

    public ByteBuf writeShort(int value) {
        ensureWritable(2);
        array[writerIndex++] = (byte) (value >>> 8);
        array[writerIndex++] = (byte) value;
        return this;
    }

    public ByteBuf writeBytes(byte[] bytes) {
        ensureWritable(bytes.length);
        System.arraycopy(bytes, 0, array, writerIndex, bytes.length);
        writerIndex += bytes.length;
        return this;
    }

    public boolean isReadable() {
        return readerIndex < writerIndex;
    }

    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    public int readerIndex() {
        return readerIndex;
    }

    public ByteBuf readerIndex(int index) {
        this.readerIndex = index;
        return this;
    }

    public int readUnsignedByte() {
        return array[readerIndex++] & 0xFF;
    }

    public int readUnsignedShort() {
        int hi = readUnsignedByte();
        int lo = readUnsignedByte();
        return (hi << 8) | lo;
    }

    /** Fills {@code dst} completely from the current reader index and advances past it. */
    public void readBytes(byte[] dst) {
        System.arraycopy(array, readerIndex, dst, 0, dst.length);
        readerIndex += dst.length;
    }

    /** Non-consuming absolute read, used by tests. */
    public int getUnsignedByte(int index) {
        return array[index] & 0xFF;
    }

    /** Read-only copy of {@code [index, index + length)}, with its own fresh reader index of 0. */
    public ByteBuf slice(int index, int length) {
        return wrappedBuffer(Arrays.copyOfRange(array, index, index + length));
    }
}
