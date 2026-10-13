package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import com.github.ruediste.p2psync.libp2p.core.PeerId;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Minimal implementation of the multihash spec (https://github.com/multiformats/multihash),
 * trimmed to the two digest functions actually needed by this port: {@code identity} (used
 * for embedding small public keys directly, per the "inline" convention) and
 * {@code sha2-256} (used for larger public keys). See {@link PeerId#fromPubKey}.
 *
 * <p>
 * Ported (in simplified form) from {@code io.libp2p.core.multiformats.Multihash} (jvm-libp2p).
 */
public final class Multihash {

    public enum Digest {
        IDENTITY,
        SHA2_256
    }

    private static final int CODE_IDENTITY = 0x00;
    private static final int CODE_SHA2_256 = 0x12;

    private Multihash() {
    }

    /**
     * Computes the given digest over {@code content} and wraps it in the multihash binary
     * format: {@code uvarint(code) + uvarint(length) + digest bytes}.
     */
    public static byte[] sum(Digest digest, byte[] content) {
        byte[] value;
        int code;
        switch (digest) {
            case IDENTITY:
                value = content;
                code = CODE_IDENTITY;
                break;
            case SHA2_256:
                value = sha256(content);
                code = CODE_SHA2_256;
                break;
            default:
                throw new IllegalArgumentException("Unsupported digest: " + digest);
        }
        ByteBuf buf = ByteBuf.buffer(value.length + 10);
        Varint.writeUvarint(buf, code);
        Varint.writeUvarint(buf, value.length);
        buf.writeBytes(value);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    /**
     * Parses a multihash-encoded byte array back into its digest kind and raw value.
     */
    public static Decoded decode(byte[] bytes) {
        ByteBuf buf = ByteBuf.wrappedBuffer(bytes);
        long code = Varint.readUvarint(buf);
        int length = (int) Varint.readUvarint(buf);
        if (length < 0 || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid multihash length: " + length);
        }
        byte[] value = new byte[length];
        buf.readBytes(value);
        Digest digest;
        if (code == CODE_IDENTITY) {
            digest = Digest.IDENTITY;
        } else if (code == CODE_SHA2_256) {
            digest = Digest.SHA2_256;
        } else {
            throw new IllegalArgumentException("Unrecognised multihash code: " + code);
        }
        return new Decoded(digest, value);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Decoded {
        public final Digest digest;
        public final byte[] value;

        Decoded(Digest digest, byte[] value) {
            this.digest = digest;
            this.value = value;
        }
    }
}
