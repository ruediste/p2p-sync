package com.github.ruediste.p2psync.node;

import java.util.Arrays;
import com.google.protobuf.ByteString;

public final record BlockId(byte[] bytes) {
    public ByteString toProto() {
        return ByteString.copyFrom(bytes);
    }

    public static BlockId fromProto(ByteString bytes) {
        return new BlockId(bytes.toByteArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof BlockId other) {
            return Arrays.equals(bytes, other.bytes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
