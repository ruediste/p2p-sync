package com.github.ruediste.p2psync.libp2p.transport;

import java.io.Closeable;
import java.util.function.Consumer;

import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * Represents the entry point to a transport that listens for incoming
 * connection. Provides metadata (does the transport handle a listening address)
 * and allows to start listening. Established connections are reported to the
 * caller.
 */
public interface ListeningTransportBinding {
    boolean handles(Multiaddr address);

    Closeable listen(Multiaddr address, Consumer<RawConnection> connectionHandler);
}
