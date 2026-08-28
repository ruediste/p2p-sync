package com.github.ruediste.p2psync.libp2p.host;

import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.p2psync.libp2p.core.ConnectionHandler;
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
import com.github.ruediste.p2psync.libp2p.transport.tcp.TcpInitiatingTransport;

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
    private final List<ConnectionHandler> connectionHandlers = new ArrayList<>();
    private final List<ProtocolBinding<?>> protocolHandlers = new ArrayList<>();

    public HostBuilder privateKey(PrivKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public HostBuilder listenAddress(String addr) {
        this.listenAddresses.add(addr);
        return this;
    }

    public HostBuilder connectionHandler(ConnectionHandler handler) {
        this.connectionHandlers.add(handler);
        return this;
    }

    public HostBuilder protocolHandler(ProtocolBinding<?> binding) {
        this.protocolHandlers.add(binding);
        return this;
    }

    public Host build() {
        // 1. Identity: auto-generate Ed25519 if unset
        PrivKey key = privateKey != null ? privateKey : PrivKey.generate(KeyType.ED25519);

        // 2. Build the app-level multistream for inbound Yamux streams
        Multistream<Object> appMultistream = new Multistream<>(
                List.copyOf((List<ProtocolBinding<Object>>) (List<?>) protocolHandlers));

        // 3. Wire muxer (Yamux) -> secure channel (Noise XX) -> upgrader
        YamuxProtocolBinding yamuxBinding = new YamuxProtocolBinding(appMultistream);
        NoiseXXProtocolBinding noiseBinding = new NoiseXXProtocolBinding(key);

        List<ProtocolBinding<MuxerSession>> muxers = List.of(yamuxBinding);
        List<ProtocolBinding<SecureSession>> secureChannels = List.of(noiseBinding);

        DefaultConnectionBuilder connectionBuilder = new DefaultConnectionBuilder(secureChannels, muxers);

        // 4. Create transport (TCP)
        TcpInitiatingTransport tcpTransport = new TcpInitiatingTransport();
        List<InitiatingTransport> transports = List.of(tcpTransport);

        // 5. Create broadcast connection handler
        ConnectionHandler broadcastHandler = conn -> {
            for (ConnectionHandler handler : connectionHandlers) {
                handler.handleConnection(conn);
            }
        };

        // 6. Create Network
        NetworkImpl network = new NetworkImpl(transports, connectionBuilder, broadcastHandler);

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