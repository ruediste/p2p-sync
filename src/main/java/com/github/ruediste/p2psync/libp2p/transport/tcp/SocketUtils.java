package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.UncheckedIOException;
import java.net.Socket;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.RawConnection;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;

public final class SocketUtils {

    public static RawConnection toConnection(Socket socket, boolean isInitiator) {
        P2PInputStream in = P2PInputStream.wrap(getInputStream(socket));
        P2POutputStream out = P2POutputStream.wrap(getOutputStream(socket));
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

    public static Multiaddr localAddress(java.net.Socket socket) {
        java.net.InetSocketAddress addr = (java.net.InetSocketAddress) socket.getLocalSocketAddress();
        if (addr == null) {
            return new Multiaddr("/ip4/0.0.0.0/tcp/0");
        }
        String ip = addr.getAddress().getHostAddress();
        return new Multiaddr("/ip4/" + ip + "/tcp/" + addr.getPort());
    }

    public static Multiaddr remoteAddress(java.net.Socket socket) {
        java.net.InetSocketAddress addr = (java.net.InetSocketAddress) socket.getRemoteSocketAddress();
        String ip = addr.getAddress().getHostAddress();
        return new Multiaddr("/ip4/" + ip + "/tcp/" + addr.getPort());
    }
}
