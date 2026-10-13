package com.github.ruediste.p2psync.node;

import java.util.Arrays;

public final record JoinId(byte[] bytes) {
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof JoinId other) {
            return Arrays.equals(bytes, other.bytes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
