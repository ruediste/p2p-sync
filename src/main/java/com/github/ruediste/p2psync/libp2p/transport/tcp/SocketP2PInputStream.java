package com.github.ruediste.p2psync.libp2p.transport.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Socket;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;

class SocketP2PInputStream extends P2PInputStream {

    private final InputStream in;

    SocketP2PInputStream(Socket socket) {
        try {
            this.in = socket.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int read(byte[] buf, int off, int len) {
        try {
            return in.read(buf, off, len);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int read() {
        try {
            return in.read();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
