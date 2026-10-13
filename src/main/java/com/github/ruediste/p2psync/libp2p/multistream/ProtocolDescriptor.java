package com.github.ruediste.p2psync.libp2p.multistream;

import java.util.Collection;
import java.util.List;

/**
 * Describes which protocol ids are accepted on inbound (responder) negotiation,
 * and which
 * protocol ids are announced (in preference order) on outbound (initiator)
 * negotiation.
 *
 * <p>
 * A descriptor may relate to a single protocol, in which case
 * {@link #getAnnounceProtocols()}
 * would normally contain protocol versions starting from the newest (most
 * preferable) and
 * ending with the oldest supported version.
 *
 * <p>
 * Ported from {@code io.libp2p.core.multistream.ProtocolDescriptor}
 * (jvm-libp2p).
 */
public final class ProtocolDescriptor {

    private final List<String> announceProtocols;
    private final ProtocolMatcher protocolMatcher;

    public ProtocolDescriptor(List<String> announceProtocols, ProtocolMatcher protocolMatcher) {
        this.announceProtocols = List.copyOf(announceProtocols);
        this.protocolMatcher = protocolMatcher;
    }

    public ProtocolDescriptor(String announce) {
        this(List.of(announce), ProtocolMatcher.strict(announce));
    }

    public ProtocolDescriptor(String... protocols) {
        this(List.of(protocols), ProtocolMatcher.list(protocols));
    }

    public ProtocolDescriptor(String announce, ProtocolMatcher matcher) {
        this(List.of(announce), matcher);
    }

    public ProtocolDescriptor(ProtocolMatcher matcher) {
        this(List.of(), matcher);
    }

    public List<String> getAnnounceProtocols() {
        return announceProtocols;
    }

    public ProtocolMatcher getProtocolMatcher() {
        return protocolMatcher;
    }

    public boolean matchesAny(Collection<String> protocols) {
        return protocols.stream().anyMatch(protocolMatcher::matches);
    }
}
