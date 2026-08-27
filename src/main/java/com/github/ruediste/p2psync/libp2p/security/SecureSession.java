package com.github.ruediste.p2psync.libp2p.security;

import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

/**
 * The result of a successful security handshake: the (now secured)
 * {@link P2PStream} to hand to the layer above (the muxer), plus the two
 * peers' identities.
 */
public class SecureSession {
    private final PeerId localId;
    private final PeerId remoteId;
    private final PubKey remotePubKey;
    private final P2PStream stream;

    public SecureSession(PeerId localId, PeerId remoteId, PubKey remotePubKey, P2PStream stream) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.remotePubKey = remotePubKey;
        this.stream = stream;
    }

    public PeerId getLocalId() {
        return localId;
    }

    public PeerId getRemoteId() {
        return remoteId;
    }

    public PubKey getRemotePubKey() {
        return remotePubKey;
    }

    /**
     * The {@link P2PStream} wrapping the raw stream with per-frame AEAD
     * encryption/decryption. This replaces the raw stream in the upgrade
     * pipeline (e.g. the muxer is negotiated over it).
     */

    public P2PStream getStream() {
        return stream;
    }
}