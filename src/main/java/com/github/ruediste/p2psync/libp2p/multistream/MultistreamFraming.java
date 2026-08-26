package com.github.ruediste.p2psync.libp2p.multistream;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;

/**
 * Wire framing for the {@code /multistream/1.0.0} protocol
 * (https://github.com/multiformats/multistream-select): each message is a
 * varint32-length-prefixed, {@code '\n'}-terminated UTF-8 string.
 *
 * <p>
 * Ported from the framing upstream builds out of Netty's
 * {@code ProtobufVarint32LengthFieldPrepender}/{@code LimitedProtobufVarint32FrameDecoder} +
 * {@code StringEncoder}/{@code StringDecoder} + {@code StringSuffixCodec('\n')}. Here it's just
 * a couple of methods operating directly on {@link P2PInputStream}/{@link P2POutputStream} —
 * no frame-decoder/pipeline framework needed since these are already blocking streams.
 *
 * <p>
 * The varint length prefix is decoded one byte at a time via {@link P2PInputStream#read()}
 * (stopping as soon as the continuation bit is clear); the fixed-length string body afterwards
 * is read with {@link P2PInputStream#readFully(byte[])}. Neither method declares a checked
 * exception, matching {@link P2PInputStream}/{@link P2POutputStream}: a genuine I/O failure
 * (premature EOF) surfaces as {@link UncheckedIOException}; a malformed/oversized message is a
 * protocol violation and surfaces as a plain {@link RuntimeException} (per the M1 deviation
 * note in {@code ImplementationPlan.md} — no bespoke exception hierarchy).
 */
public final class MultistreamFraming {

    /** Matches upstream's {@code Multistream.MAX_MULTISTREAM_MESSAGE_LENGTH}. */
    public static final int MAX_MESSAGE_LENGTH = 1024;

    private static final byte SUFFIX = '\n';

    private MultistreamFraming() {
    }

    public static void writeMessage(P2POutputStream out, String message) {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        int payloadLength = body.length + 1; // +1 for the trailing '\n'
        if (payloadLength > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Multistream message too long (" + payloadLength + " > " + MAX_MESSAGE_LENGTH + "): '"
                            + message + "'");
        }
        writeVarint(out, payloadLength);
        out.write(body);
        out.write(SUFFIX);
    }

    public static String readMessage(P2PInputStream in) {
        long length = readVarint(in);
        if (length < 0 || length > MAX_MESSAGE_LENGTH) {
            throw new RuntimeException("Invalid multistream message length: " + length);
        }
        if (length == 0) {
            throw new RuntimeException("Multistream message must at least contain the '\\n' suffix");
        }
        byte[] payload = new byte[(int) length];
        in.readFully(payload);
        if (payload[payload.length - 1] != SUFFIX) {
            throw new RuntimeException("Multistream message missing trailing '\\n' suffix");
        }
        return new String(payload, 0, payload.length - 1, StandardCharsets.UTF_8);
    }

    private static long readVarint(P2PInputStream in) {
        long result = 0;
        int shift = 0;
        for (int i = 0; i <= 8; i++) {
            int b = in.read();
            if (b < 0) {
                throw new UncheckedIOException(
                        new EOFException("Unexpected end of stream while reading multistream varint length"));
            }
            result |= ((long) (b & 0x7f)) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new RuntimeException("Multistream varint length too long");
    }

    private static void writeVarint(P2POutputStream out, long value) {
        long v = value;
        while (Long.compareUnsigned(v, 0x80) >= 0) {
            out.write((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }
}
