package com.github.ruediste.p2psync.libp2p.security.noise;

import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;

/**
 * Wraps a raw {@link P2POutputStream} so writes transparently encrypt data into
 * one or more length-prefixed Noise transport frames.
 *
 * <p>
 * Frame format (per the libp2p noise spec wire format): {@code noise_message_len}
 * (2 bytes, big-endian) followed by the AEAD ciphertext (length-prefix +
 * plaintext + 16-byte tag). A single {@link #write(byte[], int, int)} of more
 * than {@link NoiseXXHandshake#SPLIT_MAX} plaintext bytes is split into multiple
 * frames so each stays within the 65535-byte Noise message limit (this mirrors
 * upstream's {@code SplitEncoder}).
 *
 * <p>
 * From the muxer's (M6) point of view this is just another
 * {@link P2POutputStream}, identical in shape to the raw TCP one
 * (see {@code ARCHITECTURE.md}).
 */
public final class NoiseXXFramedOutputStream extends P2POutputStream {

    private final P2POutputStream out;
    private final NoiseXXHandshake.CipherState outbound;

    /**
     * @param outbound the {@link NoiseXXHandshake.CipherState} that encrypts the
     *                 outbound direction's transport frames (the initiator's
     *                 first split key).
     */
    public NoiseXXFramedOutputStream(P2POutputStream out, NoiseXXHandshake.CipherState outbound) {
        this.out = out;
        this.outbound = outbound;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        int written = 0;
        while (written < len) {
            int chunk = Math.min(len - written, NoiseXXHandshake.SPLIT_MAX);
            writeFrame(buf, off + written, chunk);
            written += chunk;
        }
    }

    @Override
    public void write(int b) {
        writeFrame(new byte[] { (byte) b }, 0, 1);
    }

    private void writeFrame(byte[] plaintext, int off, int len) {
        byte[] chunk = new byte[len];
        System.arraycopy(plaintext, off, chunk, 0, len);
        byte[] ciphertext = outbound.encryptWithAd(null, chunk);
        out.write((ciphertext.length >>> 8) & 0xFF);
        out.write(ciphertext.length & 0xFF);
        out.write(ciphertext);
    }

    @Override
    public void close() {
        out.close();
    }
}