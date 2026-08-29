package com.github.ruediste.p2psync.libp2p.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;

/**
 * The Host is the libp2p entrypoint. Manages identity, network,
 * address book, and protocol handlers.
 *
 * <p>
 * Ported from {@code io.libp2p.core.Host} (jvm-libp2p).
 */
public interface Host {

    PrivKey privKey();

    PeerId peerId();

    Network network();

    AddressBook addressBook();

    List<Multiaddr> listenAddresses();

    CompletableFuture<Void> start();

    CompletableFuture<Void> stop();

    void addProtocolHandler(ProtocolBinding<?> protocolBinding);

    void removeProtocolHandler(ProtocolBinding<?> protocolBinding);

    List<ProtocolBinding<?>> getProtocols();

    void addConnectionEstablishedListener(ConnectionEstablishedListener handler);

    void removeConnectionEstablishedListener(ConnectionEstablishedListener handler);

    <T> T newStream(List<ProtocolBinding<T>> protocols, Connection conn);
}