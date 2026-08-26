package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;

/**
 * Parsed component of a {@link Multiaddr}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.multiformats.MultiaddrComponent} (jvm-libp2p).
 */
public final class MultiaddrComponent {

    private final Protocol protocol;
    private final byte[] value;
    private String stringValueCache;
    private boolean stringValueComputed;

    public MultiaddrComponent(Protocol protocol, byte[] value) {
        this.protocol = protocol;
        this.value = value;
        protocol.validate(value);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public byte[] getValue() {
        return value;
    }

    public String getStringValue() {
        if (!stringValueComputed) {
            stringValueCache = value != null ? protocol.bytesToAddress(value) : null;
            stringValueComputed = true;
        }
        return stringValueCache;
    }

    public void serialize(ByteBuf buf) {
        buf.writeBytes(protocol.encoded);
        protocol.writeAddressBytes(buf, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MultiaddrComponent)) {
            return false;
        }
        MultiaddrComponent that = (MultiaddrComponent) other;
        return protocol == that.protocol && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, Arrays.hashCode(value));
    }

    @Override
    public String toString() {
        String stringValue = getStringValue();
        return "/" + protocol.typeName + (stringValue != null ? "/" + stringValue : "");
    }
}
