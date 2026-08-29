package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Represents the the entry point for a transport to connect to other nodes.
 * Provides metadata (does the transport handle an address) and allows to
 * establish the raw connection. The caller is responsible to establish a secure
 * channel and add a muxer.
 */
public interface InitiatingTransport {
    boolean handles(Multiaddr address);

    RawConnection dial(Multiaddr address);
}
