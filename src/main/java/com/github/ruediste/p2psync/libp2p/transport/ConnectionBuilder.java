package com.github.ruediste.p2psync.libp2p.transport;

import java.net.Socket;

import com.github.ruediste.p2psync.libp2p.core.Connection;

public interface ConnectionBuilder {
    Connection upgrade(Socket socket, boolean isInitiator);
}
