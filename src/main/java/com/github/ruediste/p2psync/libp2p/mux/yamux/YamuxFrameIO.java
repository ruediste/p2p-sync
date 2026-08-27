package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.Set;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;

/**
 * Reads and writes Yamux frames as plain blocking I/O calls on
 * {@link P2PInputStream}/{@link P2POutputStream}.
 *
 * <p>
 * Wire format (12-byte header, big-endian):
 * {@code version:u8, type:u8, flags:u16, streamId:u32, length:u32} + optional
 * data payload.
 */
public final class YamuxFrameIO {

    public static final int HEADER_SIZE = 12;
    public static final int MAX_FRAME_DATA_LENGTH = 1 << 20;

    private YamuxFrameIO() {
    }

    /**
     * Reads one Yamux frame from the stream, blocking until the full frame
     * (header + optional payload) is available.
     */
    public static YamuxFrame readFrame(P2PInputStream in) {
        byte[] header = new byte[HEADER_SIZE];
        try {
            in.readFully(header);
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof EOFException) {
                throw e;
            }
            throw e;
        }

        int version = header[0] & 0xFF;
        if (version != 0) {
            throw new RuntimeException("Unsupported Yamux version: " + version);
        }
        int type = header[1] & 0xFF;
        int flags = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        long streamId = ((long) (header[4] & 0xFF) << 24)
                | ((long) (header[5] & 0xFF) << 16)
                | ((long) (header[6] & 0xFF) << 8)
                | (long) (header[7] & 0xFF);
        long length = ((long) (header[8] & 0xFF) << 24)
                | ((long) (header[9] & 0xFF) << 16)
                | ((long) (header[10] & 0xFF) << 8)
                | (long) (header[11] & 0xFF);

        YamuxType yamuxType = YamuxType.fromInt(type);
        Set<YamuxFlag> yamuxFlags = YamuxFlag.fromInt(flags);

        if (yamuxType != YamuxType.DATA || length <= 0) {
            return new YamuxFrame(streamId, yamuxType, yamuxFlags, (int) length);
        }

        if (length > MAX_FRAME_DATA_LENGTH) {
            throw new RuntimeException("Yamux frame too large: " + length);
        }

        byte[] data = new byte[(int) length];
        in.readFully(data);
        return new YamuxFrame(streamId, yamuxType, yamuxFlags, (int) length, data);
    }

    /**
     * Writes one Yamux frame to the stream, blocking until the full frame is
     * written.
     */
    public static void writeFrame(P2POutputStream out, YamuxFrame frame) {
        byte[] header = new byte[HEADER_SIZE];
        header[0] = 0;
        header[1] = (byte) frame.type.intValue;
        int flags = YamuxFlag.toInt(frame.flags);
        header[2] = (byte) ((flags >> 8) & 0xFF);
        header[3] = (byte) (flags & 0xFF);
        header[4] = (byte) ((frame.streamId >> 24) & 0xFF);
        header[5] = (byte) ((frame.streamId >> 16) & 0xFF);
        header[6] = (byte) ((frame.streamId >> 8) & 0xFF);
        header[7] = (byte) (frame.streamId & 0xFF);

        int wireLength = frame.type == YamuxType.DATA && frame.data != null
                ? frame.data.length
                : frame.length;
        header[8] = (byte) ((wireLength >> 24) & 0xFF);
        header[9] = (byte) ((wireLength >> 16) & 0xFF);
        header[10] = (byte) ((wireLength >> 8) & 0xFF);
        header[11] = (byte) (wireLength & 0xFF);

        out.write(header);
        if (frame.data != null && frame.data.length > 0) {
            out.write(frame.data);
        }
    }
}
