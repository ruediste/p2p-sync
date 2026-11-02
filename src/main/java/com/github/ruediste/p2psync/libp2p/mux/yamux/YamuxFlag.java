package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum YamuxFlag {
    SYN(1),
    ACK(2),
    FIN(4),
    RST(8);

    public final int intFlag;

    private static final int ALL_FLAGS = 1 | 2 | 4 | 8;

    YamuxFlag(int intFlag) {
        this.intFlag = intFlag;
    }

    public Set<YamuxFlag> asSet() {
        return Set.of(this);
    }

    public static final Set<YamuxFlag> NONE = Set.of();

    /**
     * Decodes a raw flags field. Any combination of the defined flag bits is
     * valid (Yamux explicitly allows e.g. {@code SYN|FIN} on the same frame);
     * unknown bits are rejected.
     */
    public static Set<YamuxFlag> fromInt(int flags) {
        if ((flags & ~ALL_FLAGS) != 0) {
            throw new IllegalArgumentException("Invalid Yamux flags value: " + flags);
        }
        if (flags == 0) {
            return NONE;
        }
        EnumSet<YamuxFlag> result = EnumSet.noneOf(YamuxFlag.class);
        for (YamuxFlag flag : values()) {
            if ((flags & flag.intFlag) != 0) {
                result.add(flag);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static int toInt(Collection<YamuxFlag> flags) {
        int value = 0;
        for (YamuxFlag f : flags) {
            value |= f.intFlag;
        }
        if ((value & ~ALL_FLAGS) != 0) {
            throw new IllegalArgumentException("Invalid Yamux flags combination: " + flags);
        }
        return value;
    }
}
