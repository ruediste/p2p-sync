package com.github.ruediste.p2psync.libp2p.mux.yamux;

public enum YamuxType {
    DATA(0),
    WINDOW_UPDATE(1),
    PING(2),
    GO_AWAY(3);

    public final int intValue;

    YamuxType(int intValue) {
        this.intValue = intValue;
    }

    private static final YamuxType[] LOOKUP = values();

    public static YamuxType fromInt(int intValue) {
        for (YamuxType t : LOOKUP) {
            if (t.intValue == intValue) {
                return t;
            }
        }
        throw new IllegalArgumentException("Invalid Yamux type value: " + intValue);
    }
}
