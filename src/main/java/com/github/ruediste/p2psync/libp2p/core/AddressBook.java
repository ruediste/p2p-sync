package com.github.ruediste.p2psync.libp2p.core;

import java.util.Collection;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * The address book holds known addresses for peers.
 *
 * <p>
 * Ported from {@code io.libp2p.core.AddressBook} (jvm-libp2p). In this
 * simplified blocking-I/O port, the methods return plain values instead of
 * {@code CompletableFuture}.
 */
public interface AddressBook {

    Collection<Multiaddr> getAddrs(PeerId id);

    void setAddrs(PeerId id, long ttl, Multiaddr... addrs);

    void addAddrs(PeerId id, long ttl, Multiaddr... addrs);
}