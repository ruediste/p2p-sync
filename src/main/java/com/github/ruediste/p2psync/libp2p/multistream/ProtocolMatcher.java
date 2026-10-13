package com.github.ruediste.p2psync.libp2p.multistream;

import java.util.Collection;
import java.util.List;

/**
 * A matcher that evaluates whether a given protocol id activates a
 * {@link ProtocolBinding}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.multistream.ProtocolMatcher} (jvm-libp2p).
 */
public interface ProtocolMatcher {

    boolean matches(String proposed);

    static ProtocolMatcher strict(String protocol) {
        return proposed -> protocol.equals(proposed);
    }

    static ProtocolMatcher prefix(String protocolPrefix) {
        return proposed -> proposed.startsWith(protocolPrefix);
    }

    static ProtocolMatcher list(Collection<String> protocols) {
        return proposed -> protocols.contains(proposed);
    }

    static ProtocolMatcher list(String... protocols) {
        return list(List.of(protocols));
    }
}
