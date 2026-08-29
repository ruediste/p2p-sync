package com.github.ruediste.p2psync.libp2p.core;

@FunctionalInterface
public interface StreamHandler {
    void handleStream(Stream stream);
}
