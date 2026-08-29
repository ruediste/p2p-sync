package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.util.Set;

public final class YamuxFrame {
    public final long streamId;
    public final YamuxType type;
    public final Set<YamuxFlag> flags;
    public final int length;
    public final byte[] data;

    public YamuxFrame(long streamId, YamuxType type, Set<YamuxFlag> flags, int length, byte[] data) {
        this.streamId = streamId;
        this.type = type;
        this.flags = flags;
        this.length = length;
        this.data = data;
    }

    public YamuxFrame(long streamId, YamuxType type, Set<YamuxFlag> flags, int length) {
        this(streamId, type, flags, length, null);
    }

    @Override
    public String toString() {
        String dataStr = (data != null && data.length > 0) ? ", len=" + data.length : "";
        String flagsStr = flags.isEmpty() ? "NONE" : flags.toString();
        return "YamuxFrame(streamId=" + streamId + ", type=" + type + ", flags=" + flagsStr
                + ", length=" + length + dataStr + ")";
    }
}
