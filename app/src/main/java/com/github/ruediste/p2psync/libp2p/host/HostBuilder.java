package com.github.ruediste.p2psync.libp2p.host;

import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.libp2p.core.ConnectionEstablishedListener;
import com.github.ruediste.p2psync.libp2p.core.Host;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.crypto.KeyType;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.multistream.Multistream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;
import com.github.ruediste.p2psync.libp2p.mux.yamux.YamuxProtocolBinding;
import com.github.ruediste.p2psync.libp2p.network.NetworkImpl;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;
import com.github.ruediste.p2psync.libp2p.security.noise.NoiseXXProtocolBinding;
import com.github.ruediste.p2psync.libp2p.transport.DefaultConnectionBuilder;
import com.github.ruediste.p2psync.libp2p.transport.InitiatingTransport;
import com.github.ruediste.p2psync.libp2p.transport.ListeningTransportBinding;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpInitiatingTransport;
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpListeningTransportBinding;

/**
 * Fluent Java-idiomatic builder for {@link Host}.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * Host host = new HostBuilder()
 *     .privateKey(key)          // optional; auto-generates Ed25519 if unset
 *     .listenAddress("/ip4/127.0.0.1/tcp/0")
 *     .connectionHandler(conn -> { ... })
 *     .build();
 * }</pre>
 *
 * <p>
 * Defaults: Ed25519 identity, TCP transport, Noise XX security, Yamux muxer.
 */
public final class HostBuilder {

    private PrivKey privateKey;
    private final List<String> listenAddresses = new ArrayList<>();
    private final List<ConnectionEstablishedListener> connectionHandlers = new ArrayList<>();
    private final List<ProtocolBinding<?>> protocolHandlers = new ArrayList<>();

    public HostBuilder privateKey(PrivKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public HostBuilder listenAddress(String addr) {
        this.listenAddresses.add(addr);
        return this;
    }

    public HostBuilder connectionHandler(ConnectionEstablishedListener handler) {
        this.connectionHandlers.add(handler);
        return this;
    }

    public HostBuilder protocolHandler(ProtocolBinding<?> binding) {
        this.protocolHandlers.add(binding);
        return this;
    }

    public Host build() {
        // Identity: auto-generate Ed25519 if unset
        PrivKey key = privateKey != null ? privateKey : PrivKey.generate(KeyType.ED25519);

        // Build the app-level multistream for inbound Yamux streams
        @SuppressWarnings("unchecked")
        Multistream<Object> appMultistream = new Multistream<>(
                List.copyOf((List<ProtocolBinding<Object>>) (List<?>) protocolHandlers));

        // Wire muxer (Yamux) -> secure channel (Noise XX) -> upgrader
        YamuxProtocolBinding yamuxBinding = new YamuxProtocolBinding(appMultistream);
        NoiseXXProtocolBinding noiseBinding = new NoiseXXProtocolBinding(key);

        List<ProtocolBinding<MuxerSession>> muxers = List.of(yamuxBinding);
        List<ProtocolBinding<SecureSession>> secureChannels = List.of(noiseBinding);

        DefaultConnectionBuilder connectionBuilder = new DefaultConnectionBuilder(secureChannels, muxers);

        // Create initiating transport (TCP)
        TcpInitiatingTransport tcpTransport = new TcpInitiatingTransport();
        List<InitiatingTransport> transports = List.of(tcpTransport);

        // Create listening transport (TCP)
        TcpListeningTransportBinding tcpListeningTransport = new TcpListeningTransportBinding();
        List<ListeningTransportBinding> listeningTransportBindings = List.of(tcpListeningTransport);

        // Create broadcast connection handler
        ConnectionEstablishedListener broadcastHandler = conn -> {
            for (ConnectionEstablishedListener handler : connectionHandlers) {
                handler.handleConnection(conn);
            }
        };

        // 6. Create Network
        NetworkImpl network = new NetworkImpl(transports, listeningTransportBindings, connectionBuilder,
                broadcastHandler);

        // 7. Create AddressBook
        MemoryAddressBook addressBook = new MemoryAddressBook();

        // 8. Create and return Host
        List<Multiaddr> listenAddrs = listenAddresses.stream()
                .map(Multiaddr::new)
                .toList();

        return new HostImpl(key, network, addressBook, listenAddrs,
                new ArrayList<>(protocolHandlers),
                new ArrayList<>(connectionHandlers));
    }
}