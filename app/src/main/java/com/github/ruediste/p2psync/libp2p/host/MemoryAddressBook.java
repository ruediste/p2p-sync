package com.github.ruediste.p2psync.libp2p.host;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import com.github.ruediste.p2psync.libp2p.core.AddressBook;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Simple in-memory {@link AddressBook} backed by a {@link ConcurrentHashMap}.
 *
 * <p>
 * Ported from {@code io.libp2p.host.MemoryAddressBook} (jvm-libp2p).
 */
public final class MemoryAddressBook implements AddressBook {

    private final ConcurrentHashMap<PeerId, Collection<Multiaddr>> map = new ConcurrentHashMap<>();

    @Override
    public Collection<Multiaddr> getAddrs(PeerId id) {
        return map.get(id);
    }

    @Override
    public void setAddrs(PeerId id, long ttl, Multiaddr... addrs) {
        map.put(id, java.util.List.of(addrs));
    }

    @Override
    public void addAddrs(PeerId id, long ttl, Multiaddr... addrs) {
        map.compute(id, (key, existing) -> {
            java.util.LinkedHashSet<Multiaddr> set = new java.util.LinkedHashSet<>();
            if (existing != null) {
                set.addAll(existing);
            }
            java.util.Collections.addAll(set, addrs);
            return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(set));
        });
    }
}