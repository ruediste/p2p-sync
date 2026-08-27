package com.github.ruediste.p2psync.libp2p.core;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

public interface Connection {
    Multiaddr remoteAddress();

    Multiaddr localAddress();

    PeerId remotePeerId();

    boolean isInitiator();

    void close();
}
