package com.github.ruediste.p2psync.libp2p.transport.tcp;

import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

public class TcpConnection implements com.github.ruediste.p2psync.libp2p.core.Connection {

    private final P2PStream stream;
    private final Multiaddr localAddress;
    private final Multiaddr remoteAddress;
    private final PeerId remotePeerId;
    private final boolean initiator;

    public TcpConnection(P2PStream stream, Multiaddr localAddress, Multiaddr remoteAddress,
            PeerId remotePeerId, boolean initiator) {
        this.stream = stream;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.remotePeerId = remotePeerId;
        this.initiator = initiator;
    }

    public P2PStream getStream() {
        return stream;
    }

    @Override
    public Multiaddr remoteAddress() {
        return remoteAddress;
    }

    @Override
    public Multiaddr localAddress() {
        return localAddress;
    }

    @Override
    public PeerId remotePeerId() {
        return remotePeerId;
    }

    @Override
    public boolean isInitiator() {
        return initiator;
    }

    @Override
    public void close() {
        stream.close();
    }
}
