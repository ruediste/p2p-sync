package com.github.ruediste.p2psync.libp2p.core;

import java.util.List;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Simple value object bundling a {@link PeerId} with its known addresses.
 *
 * <p>
 * Ported from {@code io.libp2p.core.PeerInfo} (jvm-libp2p).
 */
public final class PeerInfo {

    private final PeerId peerId;
    private final List<Multiaddr> addresses;

    public PeerInfo(PeerId peerId, List<Multiaddr> addresses) {
        this.peerId = peerId;
        this.addresses = List.copyOf(addresses);
    }

    public PeerId getPeerId() {
        return peerId;
    }

    public List<Multiaddr> getAddresses() {
        return addresses;
    }
}