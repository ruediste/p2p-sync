package com.github.ruediste.p2psync.libp2p.security;

/**
 * Thrown when a Noise handshake message is malformed or truncated: the frame
 * arrived with fewer bytes than the XX pattern tokens require, so it cannot be
 * parsed into ephemeral/static keys and a payload. The connection must be
 * rejected as a protocol violation.
 *
 * <p>
 * Mirrors {@code io.libp2p.security.SecureHandshakeError} (jvm-libp2p), which
 * upstream raises for malformed Noise handshake payloads.
 */
public class MalformedNoiseHandshakeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedNoiseHandshakeException(String message) {
        super(message);
    }
}