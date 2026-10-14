package com.github.ruediste.p2psync.libp2p.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;
import com.github.ruediste.p2psync.libp2p.host.HostBuilder;

/**
 * Unit tests for the address-expansion helpers of {@link MDnsDiscovery} plus
 * listen-address mapping tests. The slow loopback integration tests
 * (discovering self / a second peer over real multicast) live in
 * {@link MDnsDiscoveryIT} and run only in the {@code it} profile.
 */
public class MDnsDiscoveryTest {

    // ---------- unit tests ----------

    @Test
    public void isWildcardDetectsIp6Wildcard() {
        assertTrue(MDnsDiscovery.isWildcard(new Multiaddr("/ip6/::/tcp/4001")));
    }

    @Test
    public void isWildcardFalseForConcreteAddresses() {
        assertFalse(MDnsDiscovery.isWildcard(new Multiaddr("/ip4/127.0.0.1/tcp/4000")));
        assertFalse(MDnsDiscovery.isWildcard(new Multiaddr("/ip4/10.2.7.1/tcp/9999")));
        assertFalse(MDnsDiscovery.isWildcard(new Multiaddr("/ip6/::1/tcp/5555")));
    }

    @Test
    public void listenPortReadsTcpFromFirstIp4() {
        assertEquals(4000, MDnsDiscovery.listenPort(List.of(
                new Multiaddr("/ip4/127.0.0.1/tcp/4000"),
                new Multiaddr("/ip4/10.2.7.1/tcp/9999"),
                new Multiaddr("/ip6/::1/tcp/5555"))));
    }

    @Test
    public void listenPortFallsBackToIp6() {
        assertEquals(5555, MDnsDiscovery.listenPort(List.of(new Multiaddr("/ip6/::1/tcp/5555"))));
    }

    @Test
    public void listenPortThrowsWithoutIpAddress() {
        assertThrows(IllegalStateException.class, () -> MDnsDiscovery.listenPort(List.of()));
    }

    @Test
    public void expandWildcardLeavesConcreteAddressesUnchanged() {
        MDnsDiscovery d = new MDnsDiscovery(PeerId.random(), List.of());
        assertEquals(List.of(new Multiaddr("/ip4/127.0.0.1/tcp/4000")),
                d.expandWildcardAddresses(new Multiaddr("/ip4/127.0.0.1/tcp/4000")));
        assertEquals(List.of(new Multiaddr("/ip4/10.2.7.1/tcp/9999")),
                d.expandWildcardAddresses(new Multiaddr("/ip4/10.2.7.1/tcp/9999")));
        List<Multiaddr> ip6 = d.expandWildcardAddresses(new Multiaddr("/ip6/::1/tcp/5555"));
        assertEquals(1, ip6.size());
        assertTrue(ip6.get(0).toString().endsWith("/tcp/5555"));
    }

    @Test
    public void expandWildcardIp6ListsInterfaceAddresses() {
        MDnsDiscovery d = new MDnsDiscovery(PeerId.random(), List.of());
        List<Multiaddr> out = d.expandWildcardAddresses(new Multiaddr("/ip6/::/tcp/4001"));
        assertFalse(out.isEmpty(), "wildcard should expand to at least one interface address");
        for (Multiaddr m : out) {
            assertTrue(m.toString().endsWith("/tcp/4001"), m.toString());
            assertTrue(m.has(Protocol.IP4) || m.has(Protocol.IP6));
        }
    }

    // ---------- listen-address -> interface mapping ----------

    @Test
    public void coversWildcardAndConcreteAddresses() throws Exception {
        InetAddress v4 = InetAddress.getByName("192.0.2.5");
        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        InetAddress anyV4 = InetAddress.getByName("0.0.0.0");
        InetAddress anyV6 = InetAddress.getByName("::");

        assertTrue(MDnsDiscovery.covers(anyV4, v4), "wildcard v4 covers any v4");
        assertTrue(MDnsDiscovery.covers(anyV6, v6), "wildcard v6 covers any v6");
        assertFalse(MDnsDiscovery.covers(anyV4, v6), "wildcard v4 does not cover v6");
        assertFalse(MDnsDiscovery.covers(anyV6, v4), "wildcard v6 does not cover v4");
        assertTrue(MDnsDiscovery.covers(v4, v4), "concrete equals");
        assertFalse(MDnsDiscovery.covers(v4, InetAddress.getByName("192.0.2.6")), "concrete differs");
    }

    @Test
    public void computeWantedMapsConcreteListenToOwningInterface() throws Exception {
        Host host = new HostBuilder().listenAddress("/ip4/127.0.0.1/tcp/0").build();
        host.start().get(10, TimeUnit.SECONDS);
        try {
            MDnsDiscovery d = new MDnsDiscovery(host.peerId(), host.network().listenAddresses());
            Map<InetAddress, Set<Integer>> wanted = d.computeWanted();
            Multiaddr listen = host.network().listenAddresses().get(0);
            InetAddress listenIp = InetAddress.getByName(
                    listen.getFirstComponent(Protocol.IP4).getStringValue());
            assertTrue(wanted.containsKey(listenIp), "owning interface should carry the listen address");
            assertEquals(Set.of(listen.getFirstComponent(Protocol.TCP).getIntValue()),
                    wanted.get(listenIp));
        } finally {
            host.stop().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void computeWantedRestrictedToAddress() throws Exception {
        Host host = new HostBuilder().listenAddress("/ip4/127.0.0.1/tcp/0").build();
        host.start().get(10, TimeUnit.SECONDS);
        try {
            int port = host.network().listenAddresses().get(0)
                    .getFirstComponent(Protocol.TCP).getIntValue();
            MDnsDiscovery d = new MDnsDiscovery(host.peerId(), host.network().listenAddresses(),
                    "test.local.", Optional.of(InetAddress.getByName("127.0.0.1")));
            Map<InetAddress, Set<Integer>> wanted = d.computeWanted();
            assertEquals(Map.of(InetAddress.getByName("127.0.0.1"), Set.of(port)), wanted,
                    "restrict pins the managed address");
        } finally {
            host.stop().get(10, TimeUnit.SECONDS);
        }
    }

    // ---------- loopback integration tests ----------

    // The loopback integration tests (startAndListenForSelf,
    // startAndListenForOther) were moved to MDnsDiscoveryIT, which runs in the
    // `it` profile only.
}
