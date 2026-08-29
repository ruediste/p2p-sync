package com.github.ruediste.p2psync.libp2p.multistream;

import java.util.List;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;

/**
 * Given a fixed list of {@link ProtocolBinding}s, runs multistream-select over
 * a {@link P2PStream}
 * (as either initiator or responder, according to
 * {@link P2PStream#isInitiator()}), then invokes the
 * winning binding's {@link ProtocolBinding#init}.
 *
 * <p>
 * This exact combination is reused three times by later milestones: once per
 * connection for
 * security negotiation (bindings = {@code [NoiseXXSecureChannel]}), once per
 * connection for
 * muxer negotiation (bindings = {@code [YamuxStreamMuxer]}), and once per
 * application stream
 * for protocol negotiation (bindings = the {@code Host}'s registered app
 * protocols).
 *
 * <p>
 * Ported from {@code io.libp2p.multistream.MultistreamImpl} (jvm-libp2p);
 * upstream's
 * {@code preHandler}/{@code postHandler}/pipeline-installation hooks and the
 * {@code CompletableFuture}-returning {@code initChannel} have no equivalent
 * here — negotiation
 * and the binding's {@code init} are both plain blocking calls, so
 * {@link #negotiate} simply
 * returns the resolved {@link Result} once both steps complete (or throws).
 */
public final class Multistream<TController> {

    static final String MULTISTREAM_PROTOCOL = "/multistream/1.0.0";
    private static final String NOT_ACCEPTED = "na";

    private final List<ProtocolBinding<TController>> bindings;

    public Multistream(List<ProtocolBinding<TController>> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public List<ProtocolBinding<TController>> getBindings() {
        return bindings;
    }

    /**
     * Negotiates over {@code stream}, as initiator or responder depending on
     * {@link P2PStream#isInitiator()}, then runs the winning binding.
     *
     * <p>
     * As initiator, announces every candidate protocol from every binding (in
     * binding order,
     * each binding's own preference order). As responder, matches the initiator's
     * proposals
     * against every binding's matcher.
     */
    public Result<TController> negotiate(P2PStream stream) {
        String selectedProtocol;
        if (stream.isInitiator()) {
            List<String> candidates = bindings.stream()
                    .flatMap(b -> b.getProtocolDescriptor().getAnnounceProtocols().stream())
                    .collect(Collectors.toList());
            selectedProtocol = negotiateAsInitiator(stream, candidates);
        } else {
            List<ProtocolMatcher> matchers = bindings.stream()
                    .map(b -> b.getProtocolDescriptor().getProtocolMatcher())
                    .collect(Collectors.toList());
            selectedProtocol = negotiateAsResponder(stream, matchers);
        }
        ProtocolBinding<TController> binding = selectBinding(bindings, selectedProtocol);
        TController controller = binding.init(stream, selectedProtocol);
        return new Result<>(selectedProtocol, controller);
    }

    /**
     * Negotiates as the initiator: proposes each of {@code protocols} in order
     * until the
     * responder accepts one (by echoing it back) or all candidates are exhausted.
     *
     * @return the agreed protocol id.
     * @throws RuntimeException if none of {@code protocols} were accepted by the
     *                          responder.
     */
    static String negotiateAsInitiator(P2PStream stream, List<String> protocols) {
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("No protocol candidates to negotiate");
        }
        for (String protocol : protocols) {
            if (protocol.length() > MultistreamFraming.MAX_MESSAGE_LENGTH - 1) {
                throw new IllegalArgumentException("Too long protocol id: '" + protocol + "'");
            }
        }

        P2PInputStream in = stream.getIn();
        P2POutputStream out = stream.getOut();

        MultistreamFraming.writeMessage(out, MULTISTREAM_PROTOCOL);
        MultistreamFraming.writeMessage(out, protocols.get(0));

        boolean headerReceived = false;
        int i = 0;
        while (true) {
            String msg = MultistreamFraming.readMessage(in);
            if (!headerReceived) {
                expectHeader(msg);
                headerReceived = true;
                continue;
            }
            if (msg.equals(protocols.get(i))) {
                return msg;
            }
            if (i == protocols.size() - 1) {
                throw new RuntimeException(
                        "Protocol negotiation failed: remote rejected all of " + protocols);
            }
            i++;
            MultistreamFraming.writeMessage(out, protocols.get(i));
        }
    }

    /**
     * Negotiates as the responder: waits for the initiator's proposals, accepting
     * (by echoing
     * back) the first one that matches any of {@code matchers}, or replying
     * {@code na} and
     * waiting for the next proposal otherwise.
     *
     * @return the agreed protocol id.
     */
    static String negotiateAsResponder(P2PStream stream, List<ProtocolMatcher> matchers) {
        P2PInputStream in = stream.getIn();
        P2POutputStream out = stream.getOut();

        MultistreamFraming.writeMessage(out, MULTISTREAM_PROTOCOL);

        boolean headerReceived = false;
        while (true) {
            String msg = MultistreamFraming.readMessage(in);
            if (!headerReceived) {
                expectHeader(msg);
                headerReceived = true;
                continue;
            }
            boolean matched = matchers.stream().anyMatch(matcher -> matcher.matches(msg));
            if (matched) {
                MultistreamFraming.writeMessage(out, msg);
                return msg;
            }
            MultistreamFraming.writeMessage(out, NOT_ACCEPTED);
        }
    }

    /**
     * Resolves the local {@link ProtocolBinding} matching a protocol id that
     * multistream-select
     * negotiation ({@link Multistream}) agreed on.
     * 
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
    private static <T> ProtocolBinding<T> selectBinding(List<ProtocolBinding<T>> bindings, String selectedProtocol) {
        return bindings.stream()
                .filter(b -> b.getProtocolDescriptor().getProtocolMatcher().matches(selectedProtocol))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Protocol negotiation succeeded on '" + selectedProtocol
                                + "' but no local binding matches it"));
    }

    private static void expectHeader(String msg) {
        if (!MULTISTREAM_PROTOCOL.equals(msg)) {
            throw new RuntimeException("Expected multistream header '" + MULTISTREAM_PROTOCOL
                    + "', got: '" + msg + "'");
        }
    }

    /**
     * The outcome of a successful negotiation: the agreed protocol id and its
     * controller.
     */
    public static final class Result<T> {
        private final String selectedProtocol;
        private final T controller;

        public Result(String selectedProtocol, T controller) {
            this.selectedProtocol = selectedProtocol;
            this.controller = controller;
        }

        public String getSelectedProtocol() {
            return selectedProtocol;
        }

        public T getController() {
            return controller;
        }
    }
}
