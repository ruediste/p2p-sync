package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class VarintTest {

    @Test
    public void roundTripsSmallValues() {
        for (long v : new long[] { 0, 1, 2, 42, 127, 128, 129, 300 }) {
            ByteBuf buf = Unpooled.buffer();
            Varint.writeUvarint(buf, v);
            assertEquals(v, Varint.readUvarint(buf));
        }
    }

    @Test
    public void roundTripsLargeValues() {
        for (long v : new long[] { 0xFFFFL, 0xFFFFFFL, Integer.MAX_VALUE, 1L << 40 }) {
            ByteBuf buf = Unpooled.buffer();
            Varint.writeUvarint(buf, v);
            assertEquals(v, Varint.readUvarint(buf));
        }
    }

    @Test
    public void returnsMinusOneOnIncompleteBuffer() {
        ByteBuf buf = Unpooled.buffer();
        Varint.writeUvarint(buf, 300); // needs 2 bytes
        ByteBuf truncated = buf.slice(0, 1);
        int readerIndexBefore = truncated.readerIndex();
        assertEquals(-1, Varint.readUvarint(truncated));
        // reader index must be unchanged on failure
        assertEquals(readerIndexBefore, truncated.readerIndex());
    }

    @Test
    public void matchesKnownEncodingOf300() {
        // 300 = 0b1_0010_1100 -> low 7 bits 0101100 with continuation bit, then remaining bits
        ByteBuf buf = Unpooled.buffer();
        Varint.writeUvarint(buf, 300);
        assertEquals(2, buf.readableBytes());
        assertEquals(0xAC, buf.getUnsignedByte(0));
        assertEquals(0x02, buf.getUnsignedByte(1));
    }
}
