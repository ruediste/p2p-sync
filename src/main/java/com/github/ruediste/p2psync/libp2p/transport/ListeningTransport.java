package com.github.ruediste.p2psync.libp2p.transport;

import java.io.Closeable;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

/**
 * An active listening endpoint returned by
 * {@link ListeningTransportBinding#listen}. Extends {@link Closeable} so it can
 * be shut down, and additionally reports the address the transport is actually
 * listening on.
 *
 * <p>
 * This matters when a configured listen address uses a wildcard port
 * (e.g. {@code /ip4/127.0.0.1/tcp/0}): the {@code Multiaddr} passed to
 * {@link ListeningTransportBinding#listen} still contains port 0, so callers
 * (such as the {@code Host} when publishing its {@code listenAddresses()})
 * must query the actual bound address instead of echoing the configured one.
 */
public interface ListeningTransport extends Closeable {

    /**
     * The address this transport is actually bound to (resolved port and
     * bound host), ready to dial back.
     */
    Multiaddr getActualListeningAddr();
}