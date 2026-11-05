package com.github.ruediste.p2psync.libp2p.test;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;

/** Shared stream helpers for tests. */
public final class TestStreams {

    private TestStreams() {
    }

    /**
     * Reads exactly {@code n} bytes, failing on a premature end of stream.
     */
    public static byte[] readExact(P2PInputStream in, int n) {
        byte[] buf = new byte[n];
        int pos = 0;
        while (pos < n) {
            int read = in.read(buf, pos, n - pos);
            if (read < 0)
                throw new RuntimeException("Unexpected EOF after " + pos + " of " + n + " bytes");
            pos += read;
        }
        return buf;
    }
}
