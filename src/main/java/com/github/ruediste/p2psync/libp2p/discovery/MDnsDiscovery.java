package com.github.ruediste.p2psync.libp2p.discovery;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.p2psync.libp2p.core.Discoverer;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.PeerInfo;
import com.github.ruediste.p2psync.libp2p.core.PeerListener;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.MultiaddrComponent;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;

/**
 * LAN peer discovery over multicast DNS (mDNS), advertising and discovering
 * libp2p peers under a service type such as
 * {@code _p2p-sync-discovery._udp.local.}.
 *
 * <p>
 * A Java port of {@code io.libp2p.discovery.MDnsDiscovery} (jvm-libp2p),
 * adapted to use the published {@code org.jmdns:jmdns} library. The advertised
 * service carries the base58 {@link PeerId} as both the instance name and the
 * TXT text, the TCP listen port in the SRV record, and the listen address as
 * A/AAAA records.
 *
 * <p>
 * One {@link JmDNS} instance is run per local interface the host's TCP servers
 * actually listen on. Each configured listening address is analyzed: a
 * wildcard address (e.g. {@code /ip4/0.0.0.0/...}) covers every qualifying
 * interface with an address of that IP family, a concrete address covers only
 * the interface owning that address. For wildcard listens, the interface set
 * is filtered per the mDNS chat guidance (skip down, non-multicast, loopback,
 * virtual, point-to-point,
 * docker/{@code br-}/{@code veth}/{@code tun}/{@code wg}
 * software interfaces and transient IPv4 link-local addresses). Interfaces are
 * re-enumerated periodically so that mDNS instances are started when new
 * interfaces appear and stopped when interfaces disappear. The {@code address}
 * constructor parameter restricts the management to a single address (used by
 * tests to pin discovery to loopback).
 */
public final class MDnsDiscovery implements Discoverer {

    private static final Logger LOG = LoggerFactory.getLogger(MDnsDiscovery.class);

    public static final String SERVICE_TAG = "_p2p-sync-discovery._udp";
    public static final String SERVICE_TAG_LOCAL = SERVICE_TAG + ".local.";
    public static final int QUERY_INTERVAL = 120;

    /** How often to re-enumerate network interfaces for new/removed ones. */
    private static final long POLL_INTERVAL_MS = 5_000;

    /** Matches standard Docker, bridge, VPN, and hypervisor interface prefixes. */
    private static final Pattern IGNORED_IFACE_NAMES = Pattern.compile(
            "^(docker\\d+|veth.*|br-.*|virbr\\d+|bridge\\d+|tun\\d+|tap\\d+|wg\\d+|ppp\\d+"
                    + "|tailscale\\d+|zt.*|vboxnet\\d+|vmnet\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final PeerId peerId;
    private final List<Multiaddr> listenAddresses;
    private final String serviceTag;
    private final Optional<InetAddress> address;

    private PeerListener newPeerFoundListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mdns-discovery");
        t.setDaemon(true);
        return t;
    });

    /**
     * Active JmDNS instances, keyed by the local interface address they are bound
     * to.
     */
    private final Map<InetAddress, ManagedInstance> instances = new LinkedHashMap<>();
    private ScheduledExecutorService poller;

    public MDnsDiscovery(PeerId peerId, List<Multiaddr> listenAddresses) {
        this(peerId, listenAddresses, SERVICE_TAG_LOCAL, Optional.empty());
    }

    public MDnsDiscovery(PeerId peerId, List<Multiaddr> listenAddresses, String serviceTag) {
        this(peerId, listenAddresses, serviceTag, Optional.empty());
    }

    public MDnsDiscovery(PeerId peerId, List<Multiaddr> listenAddresses, String serviceTag,
            Optional<InetAddress> address) {
        this.peerId = peerId;
        this.listenAddresses = List.copyOf(listenAddresses);
        this.serviceTag = serviceTag;
        this.address = address;
    }

    @Override
    public CompletableFuture<Void> start(PeerListener newPeerFoundListener) {
        this.newPeerFoundListener = Objects.requireNonNull(newPeerFoundListener);
        return CompletableFuture.runAsync(() -> {
            reconcile();
            poller = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mdns-poll");
                t.setDaemon(true);
                return t;
            });
            poller.scheduleWithFixedDelay(() -> executor.execute(this::reconcile),
                    POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }, executor);
    }

    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (poller != null) {
                poller.shutdownNow();
                poller = null;
            }
            for (InetAddress a : new ArrayList<>(instances.keySet())) {
                closeInstance(a);
            }
        }, executor);
    }

    private void closeInstance(InetAddress a) {
        ManagedInstance m = instances.remove(a);
        if (m == null) {
            return;
        }
        // Note: JmDNS.close() already unregisters all services (sending goodbye
        // packets) and waits for the canceling state machine to finish; an
        // explicit unregisterAllServices() would duplicate that work.
        try {
            m.dns.close();
            LOG.debug("Stopped mDNS instance on {}", a);
        } catch (Exception e) {
            LOG.warn("Error closing mDNS instance on {}", a, e);
        }
    }

    private void peerFound(PeerInfo peerInfo) {
        try {
            newPeerFoundListener.peerFound(peerInfo);
        } catch (RuntimeException e) {
            LOG.warn("Peer listener failed for {}", peerInfo.getPeerId(), e);
        }
    }

    /**
     * Determines the currently wanted set of (interface address -&gt; TCP ports)
     * and
     * reconciles the running {@link JmDNS} instances against it: instances for
     * interfaces that vanished are stopped, instances are started or recreated for
     * addresses that are new or changed.
     *
     * <p>
     * Runs on the single-threaded {@link #executor}, so it is serialized with
     * {@link #start} and {@link #stop}.
     */
    private void reconcile() {
        Map<InetAddress, Set<Integer>> wanted;
        try {
            wanted = computeWanted();
        } catch (RuntimeException e) {
            LOG.warn("Failed to analyze listen addresses; skipping mDNS reconcile", e);
            return;
        }

        for (InetAddress a : new ArrayList<>(instances.keySet())) {
            if (!wanted.containsKey(a)) {
                closeInstance(a);
            }
        }
        for (Map.Entry<InetAddress, Set<Integer>> e : wanted.entrySet()) {
            ManagedInstance m = instances.get(e.getKey());
            if (m == null || !m.ports.equals(e.getValue())) {
                closeInstance(e.getKey());
                startInstance(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Computes, for every network interface the host's TCP servers are listening
     * on, the set of TCP listen ports that are reachable at that interface.
     */
    Map<InetAddress, Set<Integer>> computeWanted() {
        Map<InetAddress, Set<Integer>> wanted = new LinkedHashMap<>();
        Set<InetAddress> usable = usableAddresses();
        for (Multiaddr listenAddr : listenAddresses) {
            InetAddress listenIp = firstIp(listenAddr);
            MultiaddrComponent tcp = listenAddr.getFirstComponent(Protocol.TCP);
            if (listenIp == null || tcp == null) {
                continue;
            }
            int port = tcp.getIntValue();
            boolean wildcard = isWildcard(listenAddr);
            if (address.isPresent()) {
                if (covers(listenIp, address.get())) {
                    wanted.computeIfAbsent(address.get(), k -> new LinkedHashSet<>()).add(port);
                }
            } else {
                for (InetAddress local : listInterfaceAddresses()) {
                    if (!covers(listenIp, local)) {
                        continue;
                    }
                    // For the classic <0.0.0.0/::> wildcard listen the TCP server covers a
                    // broad set of interfaces; only advertise on the ones worth it per the
                    // mDNS chat guidance (no docker/VPN/loopback/link-local noise).
                    if (wildcard && !usable.contains(local)) {
                        continue;
                    }
                    wanted.computeIfAbsent(local, k -> new LinkedHashSet<>()).add(port);
                }
            }
        }
        return wanted;
    }

    /**
     * Returns true if an address the TCP server is bound to (possibly a wildcard
     * like {@code 0.0.0.0}) also serves the given local interface address.
     */
    static boolean covers(InetAddress listenIp, InetAddress ifaceIp) {
        if (listenIp instanceof Inet4Address != ifaceIp instanceof Inet4Address) {
            return false;
        }
        if (listenIp.isAnyLocalAddress()) {
            return true;
        }
        return listenIp.equals(ifaceIp);
    }

    /**
     * The set of local interface addresses worth advertising on, applying the
     * interface filter from the mDNS chat: interfaces that are up, multicast
     * capable, not loopback/virtual/point-to-point, not a docker/br/veth/tun/wg
     * software interface, and (on Linux) backed by physical hardware.
     */
    Set<InetAddress> usableAddresses() {
        Set<InetAddress> out = new LinkedHashSet<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!isUsableInterface(ni)) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    // Skip transient IPv4 link-local (169.254.x.x assigned before DHCP) and
                    // loopback. IPv6 link-local (fe80::) is kept: it is stable per-interface
                    // and multicast-capable.
                    if (a instanceof Inet4Address && (a.isLinkLocalAddress() || a.isLoopbackAddress())) {
                        continue;
                    }
                    out.add(a);
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private static boolean isUsableInterface(NetworkInterface ni) {
        try {
            if (!ni.isUp() || !ni.supportsMulticast() || ni.isVirtual() || ni.isPointToPoint()) {
                return false;
            }
            String name = ni.getName();
            if (IGNORED_IFACE_NAMES.matcher(name).find()) {
                return false;
            }
            // On Linux, physical devices (PCI, USB, SoC) link to
            // /sys/class/net/<name>/device;
            // software interfaces (bridges, docker, veth) lack this entry.
            if (System.getProperty("os.name", "").toLowerCase().contains("linux")
                    && !Files.exists(Paths.get("/sys/class/net", name, "device"))) {
                return false;
            }
            return true;
        } catch (SocketException e) {
            return false;
        }
    }

    private void startInstance(InetAddress a, Set<Integer> ports) {
        try {
            JmDNS dns = JmDNS.create(a);
            try {
String peerIdStr = peerId.toBase58();
            for (int port : ports) {
                dns.registerService(ServiceInfo.create(serviceTag, peerIdStr, port, peerIdStr));
            }
                dns.addServiceListener(serviceTag, new Listener());
            } catch (Exception e) {
                try {
                    dns.close();
                } catch (Exception ignore) {
                    // ignore secondary close failure
                }
                throw e;
            }
            instances.put(a, new ManagedInstance(ports, dns));
            LOG.debug("Started mDNS instance on {} (ports {})", a, ports);
        } catch (Exception e) {
            LOG.warn("Failed to start mDNS instance on {}: {}", a, e.getMessage());
        }
    }

    private static InetAddress firstIp(Multiaddr a) {
        for (MultiaddrComponent c : a.getComponents()) {
            if (c.getProtocol() == Protocol.IP4 || c.getProtocol() == Protocol.IP6) {
                try {
                    return InetAddress.getByAddress(c.getValue());
                } catch (UnknownHostException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<InetAddress> listInterfaceAddresses() {
        try {
            List<InetAddress> out = new ArrayList<>();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    out.add(ia.getAddress());
                }
            }
            return out;
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Expands a wildcard listen address (e.g. {@code /ip6/::/tcp/4001}) into one
     * concrete address per interface. Non-wildcard addresses are returned as-is
     * (minus any superfluous {@code /p2p} component).
     */
    public List<Multiaddr> expandWildcardAddresses(Multiaddr addr) {
        if (!isWildcard(addr)) {
            List<MultiaddrComponent> comps = addr.getComponents().stream()
                    .filter(c -> c.getProtocol() != Protocol.P2P)
                    .toList();
            return List.of(new Multiaddr(comps));
        }
        if (addr.has(Protocol.IP4)) {
            return listNetworkAddresses(false, addr);
        }
        if (addr.has(Protocol.IP6)) {
            return listNetworkAddresses(true, addr);
        }
        return List.of();
    }

    public List<Multiaddr> listNetworkAddresses(boolean includeIp6, Multiaddr addr) {
        List<Multiaddr> result = new ArrayList<>();
        for (InetAddress ip : listInterfaceAddresses()) {
            if (!includeIp6 && !(ip instanceof Inet4Address)) {
                continue;
            }
            List<MultiaddrComponent> comps = new ArrayList<>();
            comps.add(new MultiaddrComponent(
                    ip instanceof Inet4Address ? Protocol.IP4 : Protocol.IP6, ip.getAddress()));
            for (MultiaddrComponent c : addr.getComponents()) {
                if (c.getProtocol() != Protocol.IP4 && c.getProtocol() != Protocol.IP6
                        && c.getProtocol() != Protocol.P2P) {
                    comps.add(c);
                }
            }
            result.add(new Multiaddr(comps));
        }
        return result;
    }

    public static boolean isWildcard(Multiaddr addr) {
        String s = addr.toString();
        if (s.contains("/::/")) {
            return true;
        }
        MultiaddrComponent ip6 = addr.getFirstComponent(Protocol.IP6);
        // This project's Multiaddr renders IPv6 via InetAddress.getHostAddress(), which
        // expands "::" to "0:0:0:0:0:0:0:0", so detect the wildcard from the bytes.
        if (ip6 != null) {
            byte[] v = ip6.getValue();
            if (v != null) {
                for (byte b : v) {
                    if (b != 0) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    static int listenPort(List<Multiaddr> listenAddresses) {
        Multiaddr addr = listenAddresses.stream()
                .filter(a -> a.has(Protocol.IP4))
                .findFirst()
                .orElseGet(() -> listenAddresses.stream()
                        .filter(a -> a.has(Protocol.IP6))
                        .findFirst()
                        .orElse(null));
        if (addr == null) {
            throw new IllegalStateException("No IP4/IP6 listen address found in " + listenAddresses);
        }
        MultiaddrComponent tcp = addr.getFirstComponent(Protocol.TCP);
        if (tcp == null) {
            throw new IllegalStateException("Listen address " + addr + " has no TCP component");
        }
        return tcp.getIntValue();
    }

    /**
     * Receives fully-resolved service announcements and turns each into a
     * {@link PeerInfo}, forwarded to the {@link PeerListener} passed to
     * {@link #start}.
     */
    private final class Listener implements ServiceListener {

        @Override
        public void serviceAdded(ServiceEvent event) {
            // Resolution (and thus peerFound) happens in serviceResolved.
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            // Expiry of a peer's records is intentionally ignored for now.
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            if (info == null) {
                return;
            }
            // The unqualified instance name is the base58 PeerId (we register it that way),
            // and is reliably present regardless of how the library encodes TXT records.
            String peerIdStr = info.getName();
            if (peerIdStr == null || peerIdStr.isEmpty()) {
                return;
            }
            PeerId peerId;
            try {
                peerId = PeerId.fromBase58(peerIdStr);
            } catch (RuntimeException e) {
                LOG.warn("Ignoring mDNS peer with invalid base58 id '{}'", peerIdStr);
                return;
            }
            int port = info.getPort();
            List<Multiaddr> addrs = new ArrayList<>();
            for (Inet4Address a : info.getInet4Addresses()) {
                addUniqueAddr(addrs, "/ip4/" + a.getHostAddress() + "/tcp/" + port);
            }
            for (Inet6Address a : info.getInet6Addresses()) {
                addUniqueAddr(addrs, "/ip6/" + a.getHostAddress() + "/tcp/" + port);
            }
            if (addrs.isEmpty()) {
                return;
            }
            peerFound(new PeerInfo(peerId, addrs));
        }

        private void addUniqueAddr(List<Multiaddr> addrs, String s) {
            Multiaddr m = new Multiaddr(s);
            if (!addrs.contains(m)) {
                addrs.add(m);
            }
        }
    }

    private static final class ManagedInstance {
        final Set<Integer> ports;
        final JmDNS dns;

        ManagedInstance(Set<Integer> ports, JmDNS dns) {
            this.ports = Set.copyOf(ports);
            this.dns = dns;
        }
    }
}