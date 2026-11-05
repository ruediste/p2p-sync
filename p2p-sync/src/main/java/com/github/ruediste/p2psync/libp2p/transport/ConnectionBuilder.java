package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;

public interface ConnectionBuilder {
    Connection upgrade(RawConnection rawConnection);

    /**
     * Upgrades a connection that is expected to authenticate as
     * {@code expectedRemotePeerId}. Implementations that cannot enforce the
     * expectation fall back to {@link #upgrade(RawConnection)}.
     */
    default Connection upgrade(RawConnection rawConnection, PeerId expectedRemotePeerId) {
        return upgrade(rawConnection);
    }
}
