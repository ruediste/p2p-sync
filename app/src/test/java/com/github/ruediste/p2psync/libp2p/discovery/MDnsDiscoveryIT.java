package com.github.ruediste.p2psync.libp2p.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.Assume;
import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.PeerInfo;
import com.github.ruediste.p2psync.libp2p.host.HostBuilder;

/**
 * Loopback mDNS integration tests (mirroring upstream
 * {@code MDnsDiscoveryTest}): discovering one's self and discovering a second
 * peer. These tests rely on real multicast and JmDNS's probe/announce state
 * machine, so they take several seconds each; they run only in the {@code it}
 * profile ({@code mvn test -Pit}), not in the default {@code mvn test} run.
 */
public class MDnsDiscoveryIT {

    private static final String TEST_TAG = "_ipfs-test._udp.local.";

    @Test
    public void startAndListenForSelf() throws Exception {
        assumeMulticastAvailable();
        Host host = new HostBuilder().listenAddress("/ip4/127.0.0.1/tcp/0").build();
        host.start().get(10, TimeUnit.SECONDS);
        AtomicReference<PeerInfo> found = new AtomicReference<>();
        MDnsDiscovery d = new MDnsDiscovery(host.peerId(), host.network().listenAddresses(),
                TEST_TAG, Optional.of(InetAddress.getByName("127.0.0.1")));
        try {
            d.start(pi -> {
                if (pi.getPeerId().equals(host.peerId())) {
                    found.set(pi);
                }
            }).get(10, TimeUnit.SECONDS);
            waitFor(() -> found.get() != null, 15_000);
            assertNotNull("expected to discover self", found.get());
            assertEquals(host.peerId(), found.get().getPeerId());
            assertFalse(found.get().getAddresses().isEmpty());
        } finally {
            // Do not await graceful mDNS teardown: JmDNS canceling adds several
            // seconds per instance and is irrelevant for the test outcome.
            d.stop();
            host.stop();
        }
    }

    @Test
    public void startAndListenForOther() throws Exception {
        assumeMulticastAvailable();
        Host other = new HostBuilder().listenAddress("/ip4/127.0.0.1/tcp/0").build();
        other.start().get(10, TimeUnit.SECONDS);
        MDnsDiscovery otherD = new MDnsDiscovery(other.peerId(), other.network().listenAddresses(),
                TEST_TAG, Optional.of(InetAddress.getByName("127.0.0.1")));
        otherD.start((peer) -> {
        }).get(10, TimeUnit.SECONDS);

        Host host = new HostBuilder().listenAddress("/ip4/127.0.0.1/tcp/0").build();
        host.start().get(10, TimeUnit.SECONDS);
        AtomicReference<PeerInfo> found = new AtomicReference<>();
        MDnsDiscovery d = new MDnsDiscovery(host.peerId(), host.network().listenAddresses(),
                TEST_TAG, Optional.of(InetAddress.getByName("127.0.0.1")));
        try {
            d.start(pi -> {
                if (pi.getPeerId().equals(other.peerId())) {
                    found.set(pi);
                }
            }).get(10, TimeUnit.SECONDS);
            waitFor(() -> found.get() != null, 15_000);
            assertNotNull("expected to discover the other peer", found.get());
            assertEquals(other.peerId(), found.get().getPeerId());
            assertFalse(found.get().getAddresses().isEmpty());
        } finally {
            d.stop();
            host.stop();
            otherD.stop();
            other.stop();
        }
    }

    private static void waitFor(BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private static void assumeMulticastAvailable() {
        try (MulticastSocket ms = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName("224.0.0.251");
            ms.joinGroup(new java.net.InetSocketAddress(group, 0), null);
            ms.leaveGroup(new java.net.InetSocketAddress(group, 0), null);
        } catch (Exception e) {
            Assume.assumeNoException("mDNS multicast not available in this environment", e);
        }
    }
}
