package com.github.ruediste.p2psync.libp2p.core;

@FunctionalInterface
public interface ConnectionEstablishedListener {
    void handleConnection(Connection connection);
}
