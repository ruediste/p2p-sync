package com.github.ruediste.p2psync.libp2p.transport;

import com.github.ruediste.p2psync.libp2p.core.Connection;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;

public interface ConnectionBuilder {
    Connection upgrade(RawConnection rawConnection);
}
