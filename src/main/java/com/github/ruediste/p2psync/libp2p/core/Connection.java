package com.github.ruediste.p2psync.libp2p.core;

import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;

/**
 * A connection between two libp2p peers, after it has been fully established
 * (secure session and muxer session running on top)
 */
public record Connection(RawConnection rawConnection, MuxerSession muxerSession,
        SecureSession secureSession) {

    public final PeerId getRemotePeerId() {
        return secureSession.getRemoteId();
    };

    public void close() {
        muxerSession.close();
        rawConnection.stream().close();
    }

}
