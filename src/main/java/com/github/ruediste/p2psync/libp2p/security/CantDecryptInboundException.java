package com.github.ruediste.p2psync.libp2p.security;

/**
 * Thrown when a Noise frame fails AEAD authentication during decryption,
 * indicating the remote sent corrupted or adversarial data. Such a failure is
 * fatal for the connection.
 *
 * <p>
 * Mirrors {@code io.libp2p.security.CantDecryptInboundException} (jvm-libp2p).
 */
public class CantDecryptInboundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CantDecryptInboundException(String message, Throwable cause) {
        super(message, cause);
    }
}