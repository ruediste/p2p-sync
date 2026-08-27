package com.github.ruediste.p2psync.libp2p.core;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;

/**
 * A connection between two libp2p peers, after it has been fully established
 * (secure session and muxer session running on top)
 */
public class Connection {

    public final P2PStream rawStream;
    public final Multiaddr localAddress;
    public final Multiaddr remoteAddress;
    public final PeerId remotePeerId;
    public final SecureSession secureSession;
    public final MuxerSession muxerSession;

    public Connection(P2PStream rawStream, Multiaddr localAddress, Multiaddr remoteAddress,
            PeerId remotePeerId, MuxerSession muxerSession, SecureSession secureSession) {
        this.rawStream = rawStream;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.remotePeerId = remotePeerId;
        this.secureSession = secureSession;
        this.muxerSession = muxerSession;
    }

    public void close() {
        muxerSession.close();
        rawStream.close();
    }

}
