package com.github.ruediste.p2psync.libp2p.core.multiaddr;

import com.github.ruediste.p2psync.libp2p.core.Base58;
import com.github.ruediste.p2psync.libp2p.core.PeerId;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumeration of the multiaddr protocols supported by {@link Multiaddr}.
 *
 * <p>
 * Ported from {@code io.libp2p.core.multiformats.Protocol} (jvm-libp2p), trimmed to the
 * minimal subset needed here: {@code ip4}/{@code ip6}/{@code tcp}/{@code p2p} (no DNS variants,
 * no unix/onion/websocket/etc.).
 */
public enum Protocol {

    IP4(4, 32, "ip4", Protocol::parseIp4, Protocol::stringifyIp4, Protocol::validateSize),
    TCP(6, 16, "tcp", Protocol::parseUint16, Protocol::stringifyUint16, Protocol::validateSize),
    IP6(41, 128, "ip6", Protocol::parseIp6, Protocol::stringifyIp6, Protocol::validateSize),
    P2P(421, -1, "p2p", Protocol::parseBase58, Protocol::stringifyBase58,
            Protocol::validatePeerId);

    static final int LENGTH_PREFIXED_VAR_SIZE = -1;

    /** protocol ids that carry a {@link PeerId} value (only {@code /p2p/} in this port). */
    public static final List<Protocol> PEER_ID_PROTOCOLS = List.of(P2P);

    private static final Map<Integer, Protocol> BY_CODE = new HashMap<>();
    private static final Map<String, Protocol> BY_NAME = new HashMap<>();

    static {
        for (Protocol p : values()) {
            BY_CODE.put(p.code, p);
            BY_NAME.put(p.typeName, p);
        }
    }

    public final int code;
    public final int sizeBits;
    public final String typeName;
    public final byte[] encoded;
    private final Parser parser;
    private final Stringifier stringifier;
    private final Validator validator;

    Protocol(int code, int sizeBits, String typeName, Parser parser, Stringifier stringifier,
            Validator validator) {
        this.code = code;
        this.sizeBits = sizeBits;
        this.typeName = typeName;
        this.parser = parser;
        this.stringifier = stringifier;
        this.validator = validator;
        ByteBuf buf = ByteBuf.buffer(4);
        Varint.writeUvarint(buf, code);
        this.encoded = toByteArray(buf);
    }

    public boolean hasValue() {
        return sizeBits != 0;
    }

    public void validate(byte[] bytes) {
        validator.validate(this, bytes);
    }

    public byte[] addressToBytes(String addr) {
        return parser.parse(this, addr);
    }

    public String bytesToAddress(byte[] addressBytes) {
        return stringifier.stringify(this, addressBytes);
    }

    public byte[] readAddressBytes(ByteBuf buf) {
        if (!hasValue()) {
            return null;
        }
        int size = sizeBits != LENGTH_PREFIXED_VAR_SIZE ? sizeBits / 8 : (int) Varint.readUvarint(buf);
        if (size < 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }
        if (size > buf.readableBytes()) {
            throw new IllegalArgumentException(
                    "Var size " + size + " > readable bytes: " + buf.readableBytes());
        }
        byte[] bb = new byte[size];
        buf.readBytes(bb);
        return bb;
    }

    public void writeAddressBytes(ByteBuf buf, byte[] bytes) {
        if (bytes != null) {
            if (sizeBits == LENGTH_PREFIXED_VAR_SIZE) {
                Varint.writeUvarint(buf, bytes.length);
            }
            buf.writeBytes(bytes);
        }
    }

    public static Protocol get(int code) {
        return BY_CODE.get(code);
    }

    public static Protocol get(String name) {
        return BY_NAME.get(name);
    }

    public static Protocol getOrThrow(int code) {
        Protocol p = get(code);
        if (p == null) {
            throw new IllegalArgumentException("Unknown protocol code: " + code);
        }
        return p;
    }

    public static Protocol getOrThrow(String name) {
        Protocol p = get(name);
        if (p == null) {
            throw new IllegalArgumentException("Unknown protocol name: '" + name + "'");
        }
        return p;
    }

    @FunctionalInterface
    private interface Parser {
        byte[] parse(Protocol protocol, String value);
    }

    @FunctionalInterface
    private interface Stringifier {
        String stringify(Protocol protocol, byte[] value);
    }

    @FunctionalInterface
    private interface Validator {
        void validate(Protocol protocol, byte[] value);
    }

    private static void validateSize(Protocol protocol, byte[] bytes) {
        if (!protocol.hasValue() && bytes != null) {
            throw new IllegalArgumentException(
                    "No value expected for protocol " + protocol + ", but got " + Arrays.toString(bytes));
        }
        if (protocol.hasValue()) {
            if (bytes == null) {
                throw new IllegalArgumentException("Non-null value expected for protocol " + protocol);
            }
            if (protocol.sizeBits != LENGTH_PREFIXED_VAR_SIZE && bytes.length * 8 != protocol.sizeBits) {
                throw new IllegalArgumentException("Value of size " + (protocol.sizeBits / 8)
                        + " expected for protocol " + protocol + " but got " + Arrays.toString(bytes));
            }
        }
    }

    private static void validatePeerId(Protocol protocol, byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Non-null value expected for PeerId in " + protocol);
        }
        new PeerId(bytes); // constructor validates array size
    }

    private static byte[] parseIp4(Protocol protocol, String addr) {
        InetAddress inetAddr = parseInetAddress(addr);
        if (!(inetAddr instanceof Inet4Address)) {
            throw new IllegalArgumentException("The address is not IPv4 address: " + addr);
        }
        return inetAddr.getAddress();
    }

    private static String stringifyIp4(Protocol protocol, byte[] bytes) {
        return addressBytesToString(bytes);
    }

    private static byte[] parseIp6(Protocol protocol, String addr) {
        InetAddress inetAddr = parseInetAddress(addr);
        if (!(inetAddr instanceof Inet6Address)) {
            throw new IllegalArgumentException("The address is not IPv6 address: " + addr);
        }
        return inetAddr.getAddress();
    }

    private static String stringifyIp6(Protocol protocol, byte[] bytes) {
        return addressBytesToString(bytes);
    }

    private static InetAddress parseInetAddress(String addr) {
        try {
            return InetAddress.getByName(addr);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + addr, e);
        }
    }

    private static String addressBytesToString(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            // Only thrown if bytes.length is neither 4 nor 16 - can't happen given our callers.
            throw new IllegalArgumentException(e);
        }
    }

    private static byte[] parseUint16(Protocol protocol, String addr) {
        int x = Integer.parseInt(addr);
        if (x < 0 || x > 65535) {
            throw new IllegalArgumentException(
                    "Failed to parse " + addr + " value (expected 0 <= x < 65536)");
        }
        ByteBuf buf = ByteBuf.buffer(2);
        buf.writeShort(x);
        return toByteArray(buf);
    }

    private static String stringifyUint16(Protocol protocol, byte[] bytes) {
        return String.valueOf(ByteBuf.wrappedBuffer(bytes).readUnsignedShort());
    }

    private static byte[] parseBase58(Protocol protocol, String addr) {
        return Base58.decode(addr);
    }

    private static String stringifyBase58(Protocol protocol, byte[] bytes) {
        return Base58.encode(bytes);
    }

    private static byte[] toByteArray(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
