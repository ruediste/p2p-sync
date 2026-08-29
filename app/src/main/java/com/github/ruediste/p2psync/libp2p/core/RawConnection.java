package com.github.ruediste.p2psync.libp2p.core;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * A raw connection with another peer, without encryption/mixer.
 */
public record RawConnection(P2PStream stream, Multiaddr localAddress, Multiaddr remoteAddress) {
}
