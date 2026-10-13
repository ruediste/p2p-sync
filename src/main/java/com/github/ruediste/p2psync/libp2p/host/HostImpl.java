package com.github.ruediste.p2psync.libp2p.host;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.github.ruediste.p2psync.libp2p.core.AddressBook;
import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.ConnectionEstablishedListener;
import com.github.ruediste.p2psync.libp2p.core.Discoverer;
import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.Network;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.discovery.MDnsDiscovery;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;

/**
 * The libp2p Host implementation. Wires together the network, address book,
 * and protocol handlers.
 *
 * <p>
 * Ported from {@code io.libp2p.host.HostImpl} (jvm-libp2p), adapted for this
 * project's blocking-I/O / virtual-thread model.
 */
public final class HostImpl implements Host {

    private static final long DISCOVERED_ADDR_TTL = 0;

    private final PrivKey privKey;
    private final PeerId peerId;
    private final Network network;
    private final AddressBook addressBook;
    private final List<Multiaddr> listenAddrs;
    private final List<ProtocolBinding<?, ?>> protocolHandlers;
    private final List<ConnectionEstablishedListener> connectionHandlers;
    private final boolean mdnsDiscovery;
    private final Optional<InetAddress> mdnsAddress;
    private final List<Discoverer> discoverers = new ArrayList<>();

    public HostImpl(
            PrivKey privKey,
            Network network,
            AddressBook addressBook,
            List<Multiaddr> listenAddrs,
            List<ProtocolBinding<?, ?>> protocolHandlers,
            List<ConnectionEstablishedListener> connectionHandlers,
            boolean mdnsDiscovery,
            Optional<InetAddress> mdnsAddress) {
        this.privKey = privKey;
        this.peerId = PeerId.fromPubKey(privKey.publicKey());
        this.network = network;
        this.addressBook = addressBook;
        this.listenAddrs = List.copyOf(listenAddrs);
        this.protocolHandlers = new ArrayList<>(protocolHandlers);
        this.connectionHandlers = new ArrayList<>(connectionHandlers);
        this.mdnsDiscovery = mdnsDiscovery;
        this.mdnsAddress = mdnsAddress;
    }

    @Override
    public PrivKey privKey() {
        return privKey;
    }

    @Override
    public PeerId peerId() {
        return peerId;
    }

    @Override
    public Network network() {
        return network;
    }

    @Override
    public AddressBook addressBook() {
        return addressBook;
    }

    @Override
    public List<Multiaddr> actualListenAddresses() {
        return network.listenAddresses().stream()
                .map(addr -> addr.withP2P(peerId))
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Void> start() {
        CompletableFuture<?>[] futures = listenAddrs.stream()
                .map(network::listen)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenCompose(ignored -> {
            if (!mdnsDiscovery) {
                return CompletableFuture.completedFuture(null);
            }
            MDnsDiscovery mdns = new MDnsDiscovery(peerId, network.listenAddresses(),
                    MDnsDiscovery.SERVICE_TAG_LOCAL, mdnsAddress);
            return mdns.start(peerInfo -> {
                if (!peerInfo.getPeerId().equals(peerId)) {
                    addressBook.addAddrs(peerInfo.getPeerId(), DISCOVERED_ADDR_TTL,
                            peerInfo.getAddresses().toArray(new Multiaddr[0]));
                }
            }).thenRun(() -> discoverers.add(mdns));
        });
    }

    @Override
    public CompletableFuture<Void> stop() {
        CompletableFuture<?>[] futures = discoverers.stream()
                .map(Discoverer::stop)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenCompose(ignored -> network.close());
    }

    @Override
    public void addProtocolHandler(ProtocolBinding<?, ?> protocolBinding) {
        protocolHandlers.add(protocolBinding);
    }

    @Override
    public void removeProtocolHandler(ProtocolBinding<?, ?> protocolBinding) {
        protocolHandlers.remove(protocolBinding);
    }

    @Override
    public List<ProtocolBinding<?, ?>> getProtocols() {
        return List.copyOf(protocolHandlers);
    }

    @Override
    public void addConnectionEstablishedListener(ConnectionEstablishedListener handler) {
        connectionHandlers.add(handler);
    }

    @Override
    public void removeConnectionEstablishedListener(ConnectionEstablishedListener handler) {
        connectionHandlers.remove(handler);
    }

    @Override
    public <TInitiator> TInitiator newStream(List<ProtocolBinding<TInitiator, ?>> protocols,
            Connection conn) {
        return conn.muxerSession().createStream(protocols);
    }
}