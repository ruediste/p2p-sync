package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.io.EOFException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.multistream.Multistream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.mux.MuxerSession;

/**
 * A single Yamux multiplexed session over one underlying connection
 * (Noise-encrypted {@link P2PStream}). Owns the reader virtual thread and the
 * writer lock, and manages all {@link YamuxStream}s.
 */
public final class YamuxSession implements MuxerSession {

    static final int INITIAL_WINDOW_SIZE = 256 * 1024;

    private final P2PInputStream in;
    private final P2POutputStream out;
    private final boolean initiator;

    final int initialWindowSize = INITIAL_WINDOW_SIZE;

    private final YamuxStreamIdGenerator idGenerator;
    final ConcurrentHashMap<Long, YamuxStream> streams = new ConcurrentHashMap<>();

    final ReentrantLock writerLock = new ReentrantLock();
    private volatile boolean closed;

    private final Multistream<?> appMultistream;
    private final Thread readerThread;

    public YamuxSession(P2PInputStream in, P2POutputStream out, boolean initiator,
            Multistream<?> appMultistream) {
        this.in = in;
        this.out = out;
        this.initiator = initiator;
        this.idGenerator = new YamuxStreamIdGenerator(initiator);
        this.appMultistream = appMultistream;

        this.readerThread = Thread.ofVirtual().name("yamux-reader").start(this::readerLoop);
    }

    public boolean isInitiator() {
        return initiator;
    }

    // ---- reader loop ----

    private void readerLoop() {
        try {
            while (!closed) {
                YamuxFrame frame;
                try {
                    frame = YamuxFrameIO.readFrame(in);
                } catch (UncheckedIOException e) {
                    if (e.getCause() instanceof EOFException || closed) {
                        break;
                    }
                    throw e;
                }
                if (closed)
                    break;

                dispatchFrame(frame);
            }
        } catch (RuntimeException e) {
            if (!closed) {
                System.err.println("Yamux reader error: " + e.getMessage());
            }
        } finally {
            closeAllStreams();
        }
    }

    private void dispatchFrame(YamuxFrame frame) {
        switch (frame.type) {
            case DATA -> handleDataOrControl(frame);
            case WINDOW_UPDATE -> handleWindowUpdate(frame);
            case PING -> handlePing(frame);
            case GO_AWAY -> handleGoAway(frame);
        }
    }

    private void handleDataOrControl(YamuxFrame frame) {
        if (frame.flags.contains(YamuxFlag.SYN)) {
            handleSyn(frame);
        } else if (frame.flags.contains(YamuxFlag.FIN)) {
            handleFin(frame);
        } else if (frame.flags.contains(YamuxFlag.RST)) {
            handleRst(frame);
        } else if (frame.flags.contains(YamuxFlag.ACK)) {
            handleAck(frame);
        }

        if (frame.type == YamuxType.DATA && frame.data != null && frame.data.length > 0) {
            YamuxStream stream = streams.get(frame.streamId);
            if (stream != null) {
                stream.receiveData(frame.data);
                updateReceiveWindow(stream, frame.data.length);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSyn(YamuxFrame frame) {
        if (!YamuxStreamIdGenerator.isRemoteSynStreamIdValid(!initiator, frame.streamId)) {
            throw new RuntimeException("Invalid remote SYN stream ID: " + frame.streamId);
        }
        YamuxStream stream = new YamuxStream(frame.streamId, this, false);
        streams.put(frame.streamId, stream);

        writeFrameLocked(new YamuxFrame(frame.streamId, YamuxType.WINDOW_UPDATE, YamuxFlag.ACK.asSet(), 0));

        Thread.ofVirtual().name("yamux-stream-" + frame.streamId).start(() -> {
            try {
                P2PStream p2pStream = stream.asP2PStream(false);
                ((Multistream<Object>) appMultistream).negotiate(p2pStream);
            } catch (RuntimeException e) {
                // Stream handler failed
            }
        });
    }

    private void handleFin(YamuxFrame frame) {
        YamuxStream stream = streams.get(frame.streamId);
        if (stream != null) {
            stream.remoteClosed = true;
            stream.signalIncomingClosed();
        }
    }

    private void handleRst(YamuxFrame frame) {
        YamuxStream stream = streams.remove(frame.streamId);
        if (stream != null) {
            stream.reset = true;
            stream.signalIncomingClosed();
            stream.windowLock.lock();
            try {
                stream.windowAvailable.signalAll();
            } finally {
                stream.windowLock.unlock();
            }
        }
    }

    private void handleAck(YamuxFrame frame) {
        YamuxStream stream = streams.get(frame.streamId);
        if (stream != null) {
            stream.acknowledged = true;
        }
    }

    private void handleWindowUpdate(YamuxFrame frame) {
        if (frame.streamId == 0) {
            return;
        }
        YamuxStream stream = streams.get(frame.streamId);
        if (stream == null)
            return;

        stream.windowLock.lock();
        try {
            stream.sendWindow += frame.length;
            stream.windowAvailable.signalAll();
        } finally {
            stream.windowLock.unlock();
        }
    }

    private void updateReceiveWindow(YamuxStream stream, int bytesRead) {
        int newWindow = stream.receiveWindowSize - bytesRead;
        stream.receiveWindowSize = newWindow;
        if (newWindow < initialWindowSize / 2) {
            int delta = initialWindowSize - newWindow;
            stream.receiveWindowSize += delta;
            writeFrameLocked(
                    new YamuxFrame(stream.streamId(), YamuxType.WINDOW_UPDATE, YamuxFlag.NONE, delta));
        }
    }

    private void handlePing(YamuxFrame frame) {
        if (frame.streamId != 0) {
            throw new RuntimeException("Invalid stream ID for PING frame: " + frame.streamId);
        }
        if (frame.flags.contains(YamuxFlag.SYN)) {
            writeFrameLocked(new YamuxFrame(0, YamuxType.PING, YamuxFlag.ACK.asSet(), frame.length));
        }
    }

    private void handleGoAway(YamuxFrame frame) {
        if (frame.streamId != 0) {
            throw new RuntimeException("Invalid stream ID for GO_AWAY frame: " + frame.streamId);
        }
        closed = true;
    }

    // ---- write helpers ----

    void writeFrame(YamuxFrame frame) {
        writeFrameLocked(frame);
    }

    private void writeFrameLocked(YamuxFrame frame) {
        writerLock.lock();
        try {
            if (closed)
                throw new RuntimeException("Yamux connection is closed");
            YamuxFrameIO.writeFrame(out, frame);
        } finally {
            writerLock.unlock();
        }
    }

    // ---- stream lifecycle ----

    public YamuxStream openStream() {
        long id = idGenerator.next();
        YamuxStream stream = new YamuxStream(id, this, true);
        streams.put(id, stream);
        writeFrameLocked(new YamuxFrame(id, YamuxType.DATA, YamuxFlag.SYN.asSet(), 0));
        return stream;
    }

    void doCloseForWriting(YamuxStream stream) {
        stream.closedForWriting = true;
        writeFrameLocked(new YamuxFrame(stream.streamId(), YamuxType.DATA, YamuxFlag.FIN.asSet(), 0));
    }

    void doReset(YamuxStream stream) {
        streams.remove(stream.streamId());
        stream.reset = true;
        stream.signalIncomingClosed();
        stream.windowLock.lock();
        try {
            stream.windowAvailable.signalAll();
        } finally {
            stream.windowLock.unlock();
        }
        writeFrameLocked(new YamuxFrame(stream.streamId(), YamuxType.DATA, YamuxFlag.RST.asSet(), 0));
    }

    void doCloseStream(YamuxStream stream) {
        streams.remove(stream.streamId());
    }

    private void closeAllStreams() {
        for (YamuxStream stream : streams.values()) {
            stream.signalIncomingClosed();
            stream.windowLock.lock();
            try {
                stream.windowAvailable.signalAll();
            } finally {
                stream.windowLock.unlock();
            }
        }
        streams.clear();
    }

    // ---- Session interface ----

    @Override
    public <T> T createStream(List<ProtocolBinding<T>> protocols) {
        YamuxStream yamuxStream = openStream();
        Multistream<T> ms = new Multistream<>(protocols);
        P2PStream p2pStream = yamuxStream.asP2PStream(true);
        Multistream.Result<T> result = ms.negotiate(p2pStream);
        return result.getController();
    }

    // ---- lifecycle ----

    public void close() {
        closed = true;
        try {
            in.close();
        } catch (RuntimeException ignored) {
        }
        try {
            out.close();
        } catch (RuntimeException ignored) {
        }
    }
}
