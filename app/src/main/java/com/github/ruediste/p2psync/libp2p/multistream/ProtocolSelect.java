package com.github.ruediste.p2psync.libp2p.multistream;

import java.util.List;

/**
 * Resolves the local {@link ProtocolBinding} matching a protocol id that
 * multistream-select
 * negotiation ({@link Multistream}) agreed on.
 *
 * <p>
 * Ported from {@code io.libp2p.multistream.ProtocolSelect} (jvm-libp2p),
 * reduced to a single
 * static lookup: upstream's version is a {@code ChannelInboundHandlerAdapter}
 * that reacts to a
 * {@code ProtocolNegotiationSucceeded} pipeline event and asynchronously
 * installs the winning
 * binding's handler into the pipeline. Here there is no pipeline to install a
 * handler into —
 * the caller ({@link Multistream}) just resolves the binding and calls
 * {@link ProtocolBinding#init} on it directly with the same streams negotiation
 * used.
 */
public final class ProtocolSelect {

    private ProtocolSelect() {
    }

    /**
     * @throws RuntimeException if none of {@code bindings} matches
     *                          {@code selectedProtocol} —
     *                          this should not normally happen, since
     *                          {@code selectedProtocol}
     *                          is itself the result of negotiating against these
     *                          same
     *                          bindings' matchers/announced protocols; see the M1
     *                          deviation
     *                          note on exceptions (this stands in for upstream's
     *                          {@code NoSuchLocalProtocolException}).
     */
    public static <T> ProtocolBinding<T> select(List<ProtocolBinding<T>> bindings, String selectedProtocol) {
        return bindings.stream()
                .filter(b -> b.getProtocolDescriptor().getProtocolMatcher().matches(selectedProtocol))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Protocol negotiation succeeded on '" + selectedProtocol
                                + "' but no local binding matches it"));
    }
}
