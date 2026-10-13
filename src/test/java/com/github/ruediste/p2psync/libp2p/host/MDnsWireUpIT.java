package com.github.ruediste.p2psync.libp2p.host;

import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.Assume;
import org.junit.Test;

import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;

/**
 * End-to-end wiring test: two {@link Host}s started with mDNS discovery
 * enabled (bound to loopback) end up with each other's
 * {@code /ip4/127.0.0.1/tcp/<port>} address in their address book, without any
 * manual dialing. Runs only in the {@code it} profile ({@code mvn test -Pit}).
 */
public class MDnsWireUpIT {

    private static final long TIMEOUT_MILLIS = 25_000;

    @Test
    public void twoMdnsHostsDiscoverEachOther() throws Exception {
        assumeMulticastAvailable();
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        Host a = new HostBuilder()
                .listenAddress("/ip4/127.0.0.1/tcp/0")
                .discoverMdnsAddress(loopback)
                .build();
        Host b = new HostBuilder()
                .listenAddress("/ip4/127.0.0.1/tcp/0")
                .discoverMdnsAddress(loopback)
                .build();

        a.start().get(10, TimeUnit.SECONDS);
        b.start().get(10, TimeUnit.SECONDS);
        try {
            String bAddr = "/ip4/127.0.0.1/tcp/" + tcpPort(b);
            String aAddr = "/ip4/127.0.0.1/tcp/" + tcpPort(a);

            waitFor(() -> hasAddr(a, b.peerId(), bAddr), TIMEOUT_MILLIS);
            waitFor(() -> hasAddr(b, a.peerId(), aAddr), TIMEOUT_MILLIS);

            assertTrue("host A should have learned host B's address " + bAddr,
                    hasAddr(a, b.peerId(), bAddr));
            assertTrue("host B should have learned host A's address " + aAddr,
                    hasAddr(b, a.peerId(), aAddr));
        } finally {
            // Do not await graceful mDNS teardown: JmDNS canceling adds several
            // seconds per instance and is irrelevant for the test outcome.
            a.stop();
            b.stop();
        }
    }

    private static int tcpPort(Host host) {
        Multiaddr addr = host.network().listenAddresses().get(0);
        return addr.getFirstComponent(Protocol.TCP).getIntValue();
    }

    private static boolean hasAddr(Host host, PeerId peerId, String addr) {
        Collection<Multiaddr> addrs = host.addressBook().getAddrs(peerId);
        if (addrs == null) {
            return false;
        }
        return addrs.stream().anyMatch(m -> m.toString().equals(addr));
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
