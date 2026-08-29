package com.github.ruediste.p2psync.libp2p.core.multiaddr;

/**
 * Unsigned varint read/write helpers, as defined in
 * https://github.com/multiformats/unsigned-varint.
 *
 * <p>
 * Ported from {@code io.libp2p.etc.types.ByteBufExt} (jvm-libp2p) {@code writeUvarint}/
 * {@code readUvarint} extension functions, operating on the project's own minimal {@link ByteBuf}
 * instead of Netty's.
 */
public final class Varint {

    private Varint() {
    }

    public static ByteBuf writeUvarint(ByteBuf buf, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("uvarint value must be positive");
        }
        long v = value;
        while (Long.compareUnsigned(v, 0x80) >= 0) {
            buf.writeByte((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        buf.writeByte((int) v);
        return buf;
    }

    public static ByteBuf writeUvarint(ByteBuf buf, int value) {
        return writeUvarint(buf, (long) value);
    }

    /**
     * Reads an unsigned varint from {@code buf}.
     *
     * <p>
     * If the buffer doesn't contain enough bytes to read the varint then {@code -1} is
     * returned and the buffer's reader index remains at the original position.
     */
    public static long readUvarint(ByteBuf buf) {
        long x = 0;
        int s = 0;

        int originalReaderIndex = buf.readerIndex();
        for (int i = 0; i <= 8; i++) {
            if (!buf.isReadable()) {
                // buffer contains just a fragment of uint
                buf.readerIndex(originalReaderIndex);
                return -1;
            }
            int b = buf.readUnsignedByte();
            if (b < 0x80) {
                return x | ((long) b << s);
            }
            x |= ((long) (b & 0x7f)) << s;
            s += 7;
        }
        throw new IllegalStateException("uvarint too long");
    }
}
