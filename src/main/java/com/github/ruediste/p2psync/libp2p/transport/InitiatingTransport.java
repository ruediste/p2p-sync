package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

public interface InitiatingTransport {
    RawConnection dial(Multiaddr address);

    boolean handles(Multiaddr address);
}
