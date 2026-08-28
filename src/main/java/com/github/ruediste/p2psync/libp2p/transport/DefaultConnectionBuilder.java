package com.github.ruediste.p2psync.libp2p.transport;

import java.net.Socket;
import java.util.List;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.multistream.Multistream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;

/**
 * Builds a fully upgraded {@link Connection} from a raw {@link Socket}. Runs
 * the upgrade
 * sequence (security negotiation, then muxer negotiation) as plain sequential
 * blocking calls.
 */
public final class DefaultConnectionBuilder implements ConnectionBuilder {

    private final List<ProtocolBinding<MuxerSession>> muxers;
    private final List<ProtocolBinding<SecureSession>> secureChannels;

    public DefaultConnectionBuilder(
            List<ProtocolBinding<SecureSession>> secureChannels,
            List<ProtocolBinding<MuxerSession>> muxers) {
        this.muxers = muxers;
        this.secureChannels = secureChannels;
    }

    public Connection upgrade(RawConnection rawConnection) {
        // establish secure channel
        SecureSession secureSession = new Multistream<>(secureChannels).negotiate(rawConnection.stream()).getController();

        // establish muxer
        MuxerSession muxerSession = new Multistream<>(muxers).negotiate(secureSession.getStream()).getController();

        return new Connection(rawConnection, muxerSession,
                secureSession);
    }

}
