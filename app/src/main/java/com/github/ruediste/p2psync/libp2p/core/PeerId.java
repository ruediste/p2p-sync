package com.github.ruediste.p2psync.libp2p.core;

import java.security.SecureRandom;
import java.util.Arrays;

import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multihash;
import com.github.ruediste.p2psync.libp2p.crypto.Marshaling;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;

/**
 * Represents the peer identity which is basically derived from the peer's public key.
 *
 * <p>
 * Ported from {@code io.libp2p.core.PeerId} (jvm-libp2p).
 */
public final class PeerId {

    private final byte[] bytes;

    /**
     * @param bytes peer id bytes, size must be &gt;= 32 and &lt;= 50
     */
    public PeerId(byte[] bytes) {
        if (bytes.length < 32 || bytes.length > 50) {
            throw new IllegalArgumentException("Invalid peerId length: " + bytes.length);
        }
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    /**
     * The common {@link PeerId} string representation, which is just base58 of the PeerId bytes.
     */
    public String toBase58() {
        return Base58.encode(bytes);
    }

    /**
     * PeerId as hex string.
     */
    public String toHex() {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PeerId)) {
            return false;
        }
        return Arrays.equals(bytes, ((PeerId) other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return toBase58();
    }

    /**
     * Creates {@link PeerId} from common base58 string representation.
     */
    public static PeerId fromBase58(String str) {
        return new PeerId(Base58.decode(str));
    }

    /**
     * Creates {@link PeerId} from hex string representation.
     */
    public static PeerId fromHex(String str) {
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + Character.digit(str.charAt(i + 1), 16));
        }
        return new PeerId(data);
    }

    /**
     * Generates a random {@link PeerId}. Useful for testing purposes only since it doesn't
     * generate any private keys.
     */
    public static PeerId random() {
        byte[] data = new byte[32];
        new SecureRandom().nextBytes(data);
        return new PeerId(data);
    }

    /**
     * Creates {@link PeerId} from the peer's public key: the multihash of the marshaled public
     * key, using the {@code identity} digest if the marshaled bytes fit within 42 bytes (as is
     * the case for Ed25519 keys), else {@code sha2-256}.
     */
    public static PeerId fromPubKey(PubKey pubKey) {
        byte[] marshaled = Marshaling.marshalPublicKey(pubKey);
        Multihash.Digest digest = marshaled.length <= 42 ? Multihash.Digest.IDENTITY : Multihash.Digest.SHA2_256;
        return new PeerId(Multihash.sum(digest, marshaled));
    }
}
