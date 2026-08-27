package com.github.ruediste.p2psync.libp2p.core;

@FunctionalInterface
public interface ConnectionHandler {
    void handleConnection(Connection connection);
}
