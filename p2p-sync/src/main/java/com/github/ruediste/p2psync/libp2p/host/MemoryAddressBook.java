package com.github.ruediste.p2psync.libp2p.host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.ruediste.p2psync.libp2p.core.AddressBook;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Simple in-memory {@link AddressBook} backed by a {@link ConcurrentHashMap}.
 *
 * <p>
 * Each address carries its own expiry time (a non-positive TTL means "never
 * expires"). Expired addresses are filtered lazily on read.
 */
public final class MemoryAddressBook implements AddressBook {

    private record Entry(Multiaddr address, long expiresAt) {
        boolean isExpired(long now) {
            return expiresAt <= now;
        }
    }

    private final ConcurrentHashMap<PeerId, List<Entry>> map = new ConcurrentHashMap<>();

    @Override
    public Collection<Multiaddr> getAddrs(PeerId id) {
        long now = System.currentTimeMillis();
        List<Entry> entries = map.get(id);
        if (entries == null) {
            return null;
        }
        List<Multiaddr> result = new ArrayList<>();
        boolean expired = false;
        for (Entry e : entries) {
            if (e.isExpired(now)) {
                expired = true;
            } else {
                result.add(e.address());
            }
        }
        if (expired) {
            map.computeIfPresent(id, (key, existing) -> {
                List<Entry> live = new ArrayList<>();
                long now2 = System.currentTimeMillis();
                for (Entry e : existing) {
                    if (!e.isExpired(now2)) {
                        live.add(e);
                    }
                }
                return live.isEmpty() ? null : live;
            });
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public Set<PeerId> getPeerIds() {
        getAddrsForAll();
        return Set.copyOf(map.keySet());
    }

    private void getAddrsForAll() {
        for (PeerId id : List.copyOf(map.keySet())) {
            getAddrs(id);
        }
    }

    @Override
    public void setAddrs(PeerId id, long ttl, Multiaddr... addrs) {
        long expiresAt = expiration(ttl);
        List<Entry> entries = new ArrayList<>();
        for (Multiaddr addr : addrs) {
            entries.add(new Entry(addr, expiresAt));
        }
        if (entries.isEmpty()) {
            map.remove(id);
        } else {
            map.put(id, List.copyOf(entries));
        }
    }

    @Override
    public void addAddrs(PeerId id, long ttl, Multiaddr... addrs) {
        long expiresAt = expiration(ttl);
        map.compute(id, (key, existing) -> {
            Map<Multiaddr, Entry> merged = new LinkedHashMap<>();
            if (existing != null) {
                for (Entry e : existing) {
                    merged.put(e.address(), e);
                }
            }
            for (Multiaddr addr : addrs) {
                merged.put(addr, new Entry(addr, expiresAt));
            }
            return List.copyOf(merged.values());
        });
    }

    @Override
    public void removePeer(PeerId id) {
        map.remove(id);
    }

    private static long expiration(long ttl) {
        if (ttl <= 0) {
            return Long.MAX_VALUE;
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + ttl;
        return expiresAt < now ? Long.MAX_VALUE : expiresAt;
    }
}
