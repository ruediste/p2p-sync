package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates stream IDs for a Yamux session. Odd IDs for the connection
 * initiator, even IDs for the connection responder. ID 0 is reserved.
 */
public final class YamuxStreamIdGenerator {

    private final AtomicLong idCounter;

    public YamuxStreamIdGenerator(boolean connectionInitiator) {
        this.idCounter = new AtomicLong(connectionInitiator ? 1L : 2L);
    }

    public long next() {
        return idCounter.getAndAdd(2);
    }

    /**
     * Validates that a stream ID from the remote side has the correct parity.
     */
    public static boolean isRemoteSynStreamIdValid(boolean isRemoteConnectionInitiator, long id) {
        return id > 0 && (isRemoteConnectionInitiator ? id % 2 == 1L : id % 2 == 0L);
    }
}
