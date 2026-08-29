package com.github.ruediste.p2psync.libp2p.security;

/**
 * Thrown when the remote peer's noise static key signature fails to verify, or
 * (for the dialing/initiating party) the peer identity extracted from the Noise
 * handshake payload does not match the {@code /p2p/} component of the dialed
 * multiaddr.
 *
 * <p>
 * Mirrors {@code io.libp2p.security.InvalidRemotePubKey} (jvm-libp2p).
 */
public class InvalidRemotePubKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRemotePubKeyException(String message) {
        super(message);
    }

    public InvalidRemotePubKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}