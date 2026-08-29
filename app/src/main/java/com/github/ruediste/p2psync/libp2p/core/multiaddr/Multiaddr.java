package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implements the Multiaddress concept: https://github.com/multiformats/multiaddr
 *
 * <p>
 * A multiaddress is the chain of components like {@code protocol: value} pairs (value is
 * optional). Its string representation is {@code /protocol/value/protocol/value/...}, e.g.
 * {@code /ip4/127.0.0.1/tcp/1234}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.multiformats.Multiaddr} (jvm-libp2p), trimmed to the
 * {@code ip4}/{@code ip6}/{@code tcp}/{@code p2p} protocol subset (no {@code p2p-circuit}
 * splitting logic, no path-style components).
 */
public final class Multiaddr {

    private final List<MultiaddrComponent> components;

    public Multiaddr(List<MultiaddrComponent> components) {
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
    }

    public Multiaddr(String addr) {
        this(parseString(addr));
    }

    public List<MultiaddrComponent> getComponents() {
        return components;
    }

    /**
     * Returns only components matching any of the supplied protocols.
     */
    public List<MultiaddrComponent> filterComponents(Protocol... protocols) {
        List<Protocol> wanted = Arrays.asList(protocols);
        List<MultiaddrComponent> result = new ArrayList<>();
        for (MultiaddrComponent c : components) {
            if (wanted.contains(c.getProtocol())) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * Returns the first found component for {@code protocol}, or {@code null} if not present.
     */
    public MultiaddrComponent getFirstComponent(Protocol protocol) {
        List<MultiaddrComponent> matches = filterComponents(protocol);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public boolean has(Protocol protocol) {
        return getFirstComponent(protocol) != null;
    }

    public boolean hasAny(Protocol... protocols) {
        for (Protocol p : protocols) {
            if (has(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the {@link PeerId} from the {@code /p2p/} component value, or {@code null} if
     * this address has none.
     */
    public PeerId getPeerId() {
        for (MultiaddrComponent c : components) {
            if (Protocol.PEER_ID_PROTOCOLS.contains(c.getProtocol())) {
                return new PeerId(c.getValue());
            }
        }
        return null;
    }

    /**
     * Appends a {@code /p2p/} component if absent, or checks that the existing and supplied
     * ids are equal.
     *
     * @throws IllegalArgumentException if an existing {@code /p2p/} identity doesn't match
     *                                   {@code peerId}
     */
    public Multiaddr withP2P(PeerId peerId) {
        return withComponent(Protocol.P2P, peerId.getBytes());
    }

    private Multiaddr withComponentImpl(Protocol protocol, byte[] value) {
        MultiaddrComponent existing = getFirstComponent(protocol);
        MultiaddrComponent newComponent = new MultiaddrComponent(protocol, value);
        if (existing != null) {
            if (!Arrays.equals(existing.getValue(), value)) {
                throw new IllegalArgumentException("Value (" + newComponent.getStringValue()
                        + ") for " + protocol + " doesn't match existing value in " + this);
            }
            return this;
        }
        List<MultiaddrComponent> newComponents = new ArrayList<>(components);
        newComponents.add(newComponent);
        return new Multiaddr(newComponents);
    }

    public Multiaddr withComponent(Protocol protocol, byte[] value) {
        return withComponentImpl(protocol, value);
    }

    public Multiaddr withComponent(Protocol protocol, String stringValue) {
        return withComponentImpl(protocol, protocol.addressToBytes(stringValue));
    }

    public Multiaddr withComponent(Protocol protocol) {
        return withComponentImpl(protocol, null);
    }

    /**
     * Returns a {@link Multiaddr} with the concatenated components of {@code this} and
     * {@code other}. No cross-component checks or merge is performed.
     */
    public Multiaddr concatenated(Multiaddr other) {
        List<MultiaddrComponent> newComponents = new ArrayList<>(components);
        newComponents.addAll(other.components);
        return new Multiaddr(newComponents);
    }

    /**
     * Merges the components of {@code other} into {@code this}, same effect as appending each
     * of {@code other}'s components via {@link #withComponent}.
     *
     * @throws IllegalArgumentException if any of this address' component values don't match
     *                                   the value for the same protocol in {@code other}
     */
    public Multiaddr merged(Multiaddr other) {
        Multiaddr result = this;
        for (MultiaddrComponent c : other.components) {
            result = result.withComponentImpl(c.getProtocol(), c.getValue());
        }
        return result;
    }

    public ByteBuf serializeToBuf(ByteBuf buf) {
        for (MultiaddrComponent c : components) {
            c.serialize(buf);
        }
        return buf;
    }

    public byte[] serialize() {
        ByteBuf buf = serializeToBuf(ByteBuf.buffer());
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (MultiaddrComponent c : components) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Multiaddr)) {
            return false;
        }
        return components.equals(((Multiaddr) other).components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(components);
    }

    public static Multiaddr fromString(String addr) {
        return new Multiaddr(parseString(addr));
    }

    public static Multiaddr deserialize(byte[] bytes) {
        return new Multiaddr(parseBytes(ByteBuf.wrappedBuffer(bytes)));
    }

    public static Multiaddr deserializeFromBuf(ByteBuf buf) {
        return new Multiaddr(parseBytes(buf));
    }

    public static Multiaddr empty() {
        return new Multiaddr(Collections.emptyList());
    }

    private static List<MultiaddrComponent> parseString(String addr) {
        List<MultiaddrComponent> ret = new ArrayList<>();
        try {
            String trimmed = trimTrailingSlashes(addr);
            String[] parts = trimmed.split("/");
            if (parts.length == 0 || !parts[0].isEmpty()) {
                throw new IllegalArgumentException("MultiAddress must start with a /");
            }

            int i = 1;
            while (i < parts.length) {
                String part = parts[i++];
                Protocol p = Protocol.getOrThrow(part);

                byte[] bytes;
                if (!p.hasValue()) {
                    bytes = null;
                } else {
                    if (i >= parts.length) {
                        throw new IllegalArgumentException("Missing value for protocol " + p);
                    }
                    String component = parts[i++];
                    bytes = p.addressToBytes(component);
                }
                ret.add(new MultiaddrComponent(p, bytes));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed multiaddr: '" + addr + "'", e);
        }
        return ret;
    }

    private static String trimTrailingSlashes(String addr) {
        int end = addr.length();
        while (end > 0 && addr.charAt(end - 1) == '/') {
            end--;
        }
        return addr.substring(0, end);
    }

    private static List<MultiaddrComponent> parseBytes(ByteBuf buf) {
        List<MultiaddrComponent> ret = new ArrayList<>();
        while (buf.isReadable()) {
            Protocol protocol = Protocol.getOrThrow((int) Varint.readUvarint(buf));
            ret.add(new MultiaddrComponent(protocol, protocol.readAddressBytes(buf)));
        }
        return ret;
    }
}
