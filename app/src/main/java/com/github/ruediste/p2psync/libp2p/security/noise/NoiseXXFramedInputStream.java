package com.github.ruediste.p2psync.libp2p.security.noise;

import java.io.EOFException;
import java.io.UncheckedIOException;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.security.CantDecryptInboundException;

/**
 * Wraps a raw {@link P2PInputStream} so reads transparently decrypt individual
 * length-prefixed Noise transport frames (from the post-handshake direction).
 *
 * <p>
 * Frame format (per the libp2p noise spec wire format): {@code noise_message_len}
 * (2 bytes, big-endian) followed by the AEAD ciphertext, which decrypts to the
 * plaintext chunk. A single {@link #read(byte[], int, int)} serves at most one
 * frame's worth of plaintext from an internal buffer, pulling the next frame
 * from the wire only when the previous one is exhausted.
 *
 * <p>
 * From the muxer's (M6) point of view this is just another
 * {@link P2PInputStream}, identical in shape to the raw TCP one
 * (see {@code ARCHITECTURE.md}).
 */
public final class NoiseXXFramedInputStream extends P2PInputStream {

    private final P2PInputStream in;
    private final NoiseXXHandshake.CipherState inbound;

    private byte[] plaintext = new byte[0];
    private int plaintextPos = 0;
    private boolean eof = false;

    /**
     * @param inbound the {@link NoiseXXHandshake.CipherState} that decrypts the
     *                inbound direction's transport frames (the responder's
     *                second split key).
     */
    public NoiseXXFramedInputStream(P2PInputStream in, NoiseXXHandshake.CipherState inbound) {
        this.in = in;
        this.inbound = inbound;
    }

    @Override
    public int read(byte[] buf, int off, int len) {
        if (len == 0) {
            return 0;
        }
        while (plaintextPos >= plaintext.length) {
            if (eof) {
                return -1;
            }
            if (!nextFrame()) {
                eof = true;
                return -1;
            }
        }
        int n = Math.min(len, plaintext.length - plaintextPos);
        System.arraycopy(plaintext, plaintextPos, buf, off, n);
        plaintextPos += n;
        return n;
    }

    /**
     * Fills {@link #plaintext} with the decrypted contents of the next frame.
     *
     * @return {@code false} if the stream ended before a complete frame was
     *         available.
     * @throws CantDecryptInboundException if a frame fails AEAD authentication.
     */
    private boolean nextFrame() {
        int hi = in.read();
        if (hi < 0) {
            return false;
        }
        int lo = in.read();
        if (lo < 0) {
            throw new UncheckedIOException(new EOFException("Truncated Noise frame length header"));
        }
        int length = (hi << 8) | lo;
        if (length > NoiseXXHandshake.MAX_FRAME_LENGTH) {
            throw new IllegalStateException("Noise frame length out of range: " + length);
        }
        byte[] ciphertext = new byte[length];
        in.readFully(ciphertext);
        plaintext = inbound.decryptWithAd(null, ciphertext);
        plaintextPos = 0;
        return true;
    }

    @Override
    public void close() {
        in.close();
    }
}