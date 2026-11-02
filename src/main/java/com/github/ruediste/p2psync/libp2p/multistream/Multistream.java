package com.github.ruediste.p2psync.libp2p.multistream;

import java.nio.charset.StandardCharsets;
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
 * This exact combination is reused three times: once per connection for
 * security negotiation (bindings = {@link com.github.ruediste.p2psync.libp2p.security.noise.NoiseXXProtocolBinding}),
 * once per connection for muxer negotiation (bindings =
 * {@link com.github.ruediste.p2psync.libp2p.mux.yamux.YamuxProtocolBinding}),
 * and once per application stream for protocol negotiation (bindings = the
 * {@code Host}'s registered app protocols).
 *
 * <p>
 * Ported from {@code io.libp2p.multistream.MultistreamImpl} (jvm-libp2p);
 * upstream's
 * {@code preHandler}/{@code postHandler}/pipeline-installation hooks and the
 * {@code CompletableFuture}-returning {@code initChannel} have no equivalent
 * here — negotiation
 * and the binding's {@code initInitiator}/{@code initResponder} are both plain
 * blocking calls, so
 * {@link #negotiateInitiator} / {@link #negotiateResponder} simply
 * returns the resolved {@link Result} once both steps complete (or throws).
 */
public final class Multistream<TInitiator, TResponder> {

    static final String MULTISTREAM_PROTOCOL = "/multistream/1.0.0";
    private static final String NOT_ACCEPTED = "na";

    /**
     * Upper bound on proposals accepted from one peer during negotiation. A
     * malicious peer could otherwise keep answering {@code na} forever.
     */
    static final int MAX_PROPOSALS = 256;

    private final List<ProtocolBinding<TInitiator, TResponder>> bindings;

    /**
     * @param bindings the bindings to negotiate. The list is retained (not
     *                 copied) so callers such as the {@code Host} can register
     *                 protocols dynamically; callers that mutate it
     *                 concurrently must provide a thread-safe list.
     */
    public Multistream(List<ProtocolBinding<TInitiator, TResponder>> bindings) {
        this.bindings = bindings;
    }

    public List<ProtocolBinding<TInitiator, TResponder>> getBindings() {
        return bindings;
    }

    /**
     * Negotiates over {@code stream} as the initiator, then runs the winning
     * binding's {@link ProtocolBinding#initInitiator}.
     *
     * <p>
     * Announces every candidate protocol from every binding (in
     * binding order,
     * each binding's own preference order) until the responder accepts one.
     */
    public Result<TInitiator> negotiateInitiator(P2PStream stream) {
        if (!stream.isInitiator())
            throw new RuntimeException("stream is not initiator");
        String selectedProtocol;
        List<String> candidates = bindings.stream()
                .flatMap(b -> b.getProtocolDescriptor().getAnnounceProtocols().stream())
                .collect(Collectors.toList());
        selectedProtocol = negotiateAsInitiator(stream, candidates);

        ProtocolBinding<TInitiator, TResponder> binding = selectBinding(bindings, selectedProtocol);
        TInitiator controller = binding.initInitiator(stream, selectedProtocol);
        return new Result<>(selectedProtocol, controller);
    }

    public Result<TResponder> negotiateResponder(P2PStream stream) {
        if (stream.isInitiator())
            throw new RuntimeException("stream is not responder");
        String selectedProtocol;
        List<ProtocolMatcher> matchers = bindings.stream()
                .map(b -> b.getProtocolDescriptor().getProtocolMatcher())
                .collect(Collectors.toList());
        selectedProtocol = negotiateAsResponder(stream, matchers);
        ProtocolBinding<TInitiator, TResponder> binding = selectBinding(bindings, selectedProtocol);
        TResponder responder = binding.initResponder(stream, selectedProtocol);
        return new Result<>(selectedProtocol, responder);
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
            int length = protocol.getBytes(StandardCharsets.UTF_8).length;
            if (length > MultistreamFraming.MAX_MESSAGE_LENGTH - 1) {
                throw new IllegalArgumentException("Too long protocol id: '" + protocol + "'");
            }
            if (length == 0) {
                throw new IllegalArgumentException("Empty protocol id");
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
            if (!NOT_ACCEPTED.equals(msg)) {
                throw new RuntimeException("Unexpected multistream response: '" + msg + "'");
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
        int proposals = 0;
        while (true) {
            String msg = MultistreamFraming.readMessage(in);
            if (!headerReceived) {
                expectHeader(msg);
                headerReceived = true;
                continue;
            }
            if (++proposals > MAX_PROPOSALS) {
                throw new RuntimeException("Too many multistream proposals (limit "
                        + MAX_PROPOSALS + ")");
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
    private static <TInitiator, TResponder> ProtocolBinding<TInitiator, TResponder> selectBinding(
            List<ProtocolBinding<TInitiator, TResponder>> bindings, String selectedProtocol) {
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
