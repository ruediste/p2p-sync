package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.util.Set;

public enum YamuxFlag {
    SYN(1),
    ACK(2),
    FIN(4),
    RST(8);

    public final int intFlag;

    YamuxFlag(int intFlag) {
        this.intFlag = intFlag;
    }

    public Set<YamuxFlag> asSet() {
        return Set.of(this);
    }

    public static final Set<YamuxFlag> NONE = Set.of();

    private static final java.util.Map<Integer, Set<YamuxFlag>> FLAG_MAP = java.util.Map.of(
            0, NONE,
            1, Set.of(SYN),
            2, Set.of(ACK),
            4, Set.of(FIN),
            8, Set.of(RST));

    public static Set<YamuxFlag> fromInt(int flags) {
        Set<YamuxFlag> result = FLAG_MAP.get(flags);
        if (result == null) {
            throw new IllegalArgumentException("Invalid Yamux flags value: " + flags);
        }
        return result;
    }

    public static int toInt(java.util.Collection<YamuxFlag> flags) {
        int value = 0;
        for (YamuxFlag f : flags) {
            value |= f.intFlag;
        }
        if (!FLAG_MAP.containsKey(value)) {
            throw new IllegalArgumentException("Invalid Yamux flags combination: " + flags);
        }
        return value;
    }
}
