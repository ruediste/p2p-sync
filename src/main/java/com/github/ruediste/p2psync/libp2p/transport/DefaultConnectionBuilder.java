package com.github.ruediste.p2psync.libp2p.transport;

import java.net.Socket;
import java.util.List;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.multistream.Multistream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;
import com.github.ruediste.p2psync.libp2p.security.InvalidRemotePubKeyException;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;

/**
 * Builds a fully upgraded {@link Connection} from a raw {@link Socket}. Runs
 * the upgrade
 * sequence (security negotiation, then muxer negotiation) as plain sequential
 * blocking calls.
 */
public final class DefaultConnectionBuilder implements ConnectionBuilder {

    private final List<ProtocolBinding<MuxerSession, MuxerSession>> muxers;
    private final List<ProtocolBinding<SecureSession, SecureSession>> secureChannels;

    public DefaultConnectionBuilder(
            List<ProtocolBinding<SecureSession, SecureSession>> secureChannels,
            List<ProtocolBinding<MuxerSession, MuxerSession>> muxers) {
        this.muxers = muxers;
        this.secureChannels = secureChannels;
    }

    public Connection upgrade(RawConnection rawConnection) {
        return upgrade(rawConnection, null);
    }

    @Override
    public Connection upgrade(RawConnection rawConnection, PeerId expectedRemotePeerId) {
        P2PStream stream = rawConnection.stream();
        SecureSession secureSession;
        MuxerSession muxerSession;
        if (stream.isInitiator()) {
            // establish secure channel
            secureSession = new Multistream<>(secureChannels).negotiateInitiator(stream).getController();

            // Verify the authenticated identity before any further negotiation
            // or handing the connection to the application.
            if (expectedRemotePeerId != null
                    && !expectedRemotePeerId.equals(secureSession.getRemoteId())) {
                throw new InvalidRemotePubKeyException("Peer id mismatch: expected "
                        + expectedRemotePeerId + " but got " + secureSession.getRemoteId());
            }

            // establish muxer
            muxerSession = new Multistream<>(muxers).negotiateInitiator(secureSession.getStream()).getController();
        } else {
            secureSession = new Multistream<>(secureChannels).negotiateResponder(stream).getController();
            muxerSession = new Multistream<>(muxers).negotiateResponder(secureSession.getStream()).getController();
        }

        return new Connection(rawConnection, muxerSession,
                secureSession);
    }

}
