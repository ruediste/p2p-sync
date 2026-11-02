package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Protocol;

public final class SocketUtils {

    public static RawConnection toConnection(Socket socket, boolean isInitiator) {
        return toConnection(socket, isInitiator, null);
    }

    /**
     * Adapts a raw socket and optionally runs {@code onClose} once when the
     * stream is closed by any layer. This lets the listening transport drop the
     * socket from its tracking table without owning the connection lifecycle.
     */
    public static RawConnection toConnection(Socket socket, boolean isInitiator, Runnable onClose) {
        P2PInputStream rawIn = P2PInputStream.wrap(getInputStream(socket));
        P2POutputStream rawOut = P2POutputStream.wrap(getOutputStream(socket));
        AtomicBoolean closeNotified = new AtomicBoolean();

        Runnable notifyClose = () -> {
            if (onClose != null && closeNotified.compareAndSet(false, true)) {
                onClose.run();
            }
        };

        P2PInputStream in = new P2PInputStream() {
            @Override
            public int read(byte[] buf, int off, int len) {
                return rawIn.read(buf, off, len);
            }

            @Override
            public int read() {
                return rawIn.read();
            }

            @Override
            public void close() {
                try {
                    rawIn.close();
                } finally {
                    notifyClose.run();
                }
            }
        };

        P2POutputStream out = new P2POutputStream() {
            @Override
            public void write(byte[] buf, int off, int len) {
                rawOut.write(buf, off, len);
            }

            @Override
            public void write(int b) {
                rawOut.write(b);
            }

            @Override
            public void flush() {
                rawOut.flush();
            }

            @Override
            public void close() {
                try {
                    rawOut.close();
                } finally {
                    notifyClose.run();
                }
            }
        };

        P2PStream rawStream = new P2PStream(in, out, isInitiator);

        Multiaddr localAddress = localAddress(socket);
        Multiaddr remoteAddress = remoteAddress(socket);

        return new RawConnection(rawStream, localAddress, remoteAddress);
    }

    public static java.io.InputStream getInputStream(Socket socket) {
        try {
            return socket.getInputStream();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static java.io.OutputStream getOutputStream(Socket socket) {
        try {
            return socket.getOutputStream();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Multiaddr localAddress(Socket socket) {
        InetSocketAddress addr = (InetSocketAddress) socket.getLocalSocketAddress();
        if (addr == null) {
            return new Multiaddr("/ip4/0.0.0.0/tcp/0");
        }
        return toMultiaddr(addr.getAddress(), addr.getPort());
    }

    public static Multiaddr remoteAddress(Socket socket) {
        InetSocketAddress addr = (InetSocketAddress) socket.getRemoteSocketAddress();
        return toMultiaddr(addr.getAddress(), addr.getPort());
    }

    /**
     * Builds a multiaddr with the correct {@code /ip4/} or {@code /ip6/}
     * component for the given address.
     */
    public static Multiaddr toMultiaddr(InetAddress address, int port) {
        if (address == null) {
            return new Multiaddr("/ip4/0.0.0.0/tcp/" + port);
        }
        String ip = address.getHostAddress();
        int scope = ip.indexOf('%');
        if (scope >= 0) {
            // Multiaddrs carry no zone index; a scoped link-local address is
            // still dialable by its bare address on the same interface.
            ip = ip.substring(0, scope);
        }
        String protocol = address instanceof Inet6Address ? "/ip6/" : "/ip4/";
        return new Multiaddr(protocol + ip + "/tcp/" + port);
    }

    public static Optional<HostPort> getHostPort(Multiaddr address) {
        var components = address.getComponents();
        if (components.size() < 2)
            return Optional.empty();
        var first = components.get(0);
        String host;
        if (first.getProtocol() == Protocol.IP4 || first.getProtocol() == Protocol.IP6)
            host = first.getStringValue();
        else
            return Optional.empty();

        var second = components.get(1);
        if (second.getProtocol() == Protocol.TCP)
            return Optional.of(new HostPort(host, second.getIntValue()));
        else
            return Optional.empty();
    }

    public record HostPort(String host, int port) {
    }
}
