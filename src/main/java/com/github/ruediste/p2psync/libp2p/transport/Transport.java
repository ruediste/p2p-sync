package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

public interface Transport {
    Connection dial(Multiaddr address);

    boolean handles(Multiaddr address);

    String[] getProtocols();
}
