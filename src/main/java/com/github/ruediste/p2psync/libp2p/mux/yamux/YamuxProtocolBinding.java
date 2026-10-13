package com.github.ruediste.p2psync.libp2p.mux.yamux;

import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.multistream.Multistream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolDescriptor;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;

/**
 * Protocol binding for the Yamux stream multiplexer ({@code /yamux/1.0.0}).
 *
 * <p>
 * On {@link #initInitiator}, constructs a {@link YamuxSession} around the given
 * stream and starts its background reader thread. The returned
 * {@link YamuxSession} implements {@link MuxerSession} so the
 * caller
 * can open new streams and handle inbound ones.
 */
public final class YamuxProtocolBinding implements ProtocolBinding<MuxerSession, MuxerSession> {

    private final Multistream<?, ?> inboundMultistream;

    /**
     * @param inboundMultistream the multistream to run on each inbound stream
     *                           (typically a {@code Multistream} of the host's
     *                           registered application protocol bindings)
     */
    public YamuxProtocolBinding(Multistream<?, ?> inboundMultistream) {
        this.inboundMultistream = inboundMultistream;
    }

    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor("/yamux/1.0.0");
    }

    @Override
    public MuxerSession initInitiator(P2PStream stream, String selectedProtocol) {
        return new YamuxSession(stream.getIn(), stream.getOut(), true, inboundMultistream);
    }

    @Override
    public MuxerSession initResponder(P2PStream stream, String selectedProtocol) {
        return new YamuxSession(stream.getIn(), stream.getOut(), false, inboundMultistream);
    }
}
