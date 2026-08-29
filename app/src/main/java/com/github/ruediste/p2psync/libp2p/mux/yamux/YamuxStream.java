package com.github.ruediste.p2psync.libp2p.mux.yamux;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;

/**
 * A single Yamux multiplexed stream. Exposes its read side as a
 * {@link P2PInputStream} and its write side as a {@link P2POutputStream},
 * accessible via {@link #getInputStream()} and {@link #getOutputStream()}.
 */
public final class YamuxStream {

    private final long streamId;
    final YamuxSession connection;
    int receiveWindowSize;

    /** Incoming data queue, written by connection reader thread. */
    private final Queue<byte[]> incoming = new ArrayDeque<>();
    private final ReentrantLock incomingLock = new ReentrantLock();
    private final Condition incomingAvailable = incomingLock.newCondition();
    private boolean incomingClosed;

    private byte[] currentChunk;
    private int currentOffset;

    final ReentrantLock windowLock = new ReentrantLock();
    final Condition windowAvailable = windowLock.newCondition();
    int sendWindow;

    volatile boolean acknowledged;
    volatile boolean closedForWriting;
    volatile boolean remoteClosed;
    volatile boolean reset;

    private final P2PInputStream inputStream;
    private final P2POutputStream outputStream;

    public YamuxStream(long streamId, YamuxSession connection, boolean initiator) {
        this.streamId = streamId;
        this.connection = connection;
        this.sendWindow = connection.initialWindowSize;
        this.receiveWindowSize = connection.initialWindowSize;
        this.inputStream = new StreamInput();
        this.outputStream = new StreamOutput();
    }

    public long streamId() {
        return streamId;
    }

    public P2PInputStream getInputStream() {
        return inputStream;
    }

    public P2POutputStream getOutputStream() {
        return outputStream;
    }

    public P2PStream asP2PStream(boolean initiator) {
        return new P2PStream(inputStream, outputStream, initiator);
    }

    // ---- read side ----

    private final class StreamInput extends P2PInputStream {
        @Override
        public int read(byte[] buf, int off, int len) {
            if (len == 0)
                return 0;
            incomingLock.lock();
            try {
                while (true) {
                    if (reset)
                        throw new RuntimeException("Yamux stream " + streamId + " has been reset");
                    if (currentChunk != null) {
                        int remaining = currentChunk.length - currentOffset;
                        if (remaining > 0) {
                            int n = Math.min(len, remaining);
                            System.arraycopy(currentChunk, currentOffset, buf, off, n);
                            currentOffset += n;
                            if (currentOffset >= currentChunk.length) {
                                currentChunk = null;
                                currentOffset = 0;
                            }
                            return n;
                        }
                        currentChunk = null;
                        currentOffset = 0;
                    }
                    if (incomingClosed && incoming.isEmpty()) {
                        return -1;
                    }
                    if (!incoming.isEmpty()) {
                        currentChunk = incoming.poll();
                        currentOffset = 0;
                        continue;
                    }
                    try {
                        incomingAvailable.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(
                                "Interrupted while reading from Yamux stream " + streamId, e);
                    }
                }
            } finally {
                incomingLock.unlock();
            }
        }

        @Override
        public void close() {
            YamuxStream.this.close();
        }
    }

    void receiveData(byte[] data) {
        incomingLock.lock();
        try {
            incoming.add(data);
            incomingAvailable.signalAll();
        } finally {
            incomingLock.unlock();
        }
    }

    void signalIncomingClosed() {
        incomingLock.lock();
        try {
            incomingClosed = true;
            incomingAvailable.signalAll();
        } finally {
            incomingLock.unlock();
        }
    }

    // ---- write side ----

    private final class StreamOutput extends P2POutputStream {
        @Override
        public void write(byte[] buf, int off, int len) {
            if (closedForWriting) {
                throw new RuntimeException("Yamux stream " + streamId + " is closed for writing");
            }
            if (len == 0)
                return;

            int pos = 0;
            while (pos < len) {
                waitForSendWindow();
                if (reset) {
                    throw new RuntimeException("Yamux stream " + streamId + " has been reset");
                }
                if (closedForWriting) {
                    throw new RuntimeException("Yamux stream " + streamId + " is closed for writing");
                }

                int chunkSize;
                windowLock.lock();
                try {
                    chunkSize = Math.min(len - pos,
                            Math.min(sendWindow, YamuxFrameIO.MAX_FRAME_DATA_LENGTH));
                    if (chunkSize <= 0) {
                        continue;
                    }
                    sendWindow -= chunkSize;
                } finally {
                    windowLock.unlock();
                }

                byte[] chunk = new byte[chunkSize];
                System.arraycopy(buf, off + pos, chunk, 0, chunkSize);
                connection.writeFrame(
                        new YamuxFrame(streamId, YamuxType.DATA, YamuxFlag.NONE, chunkSize, chunk));
                pos += chunkSize;
            }
        }

        @Override
        public void close() {
            if (!closedForWriting && !reset) {
                closeForWriting();
            }
            connection.doCloseStream(YamuxStream.this);
        }
    }

    private void waitForSendWindow() {
        windowLock.lock();
        try {
            while (sendWindow <= 0 && !reset) {
                try {
                    windowAvailable.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Interrupted waiting for send window on stream " + streamId, e);
                }
            }
        } finally {
            windowLock.unlock();
        }
    }

    public void closeForWriting() {
        connection.doCloseForWriting(this);
    }

    public void resetStream() {
        connection.doReset(this);
    }

    public void close() {
        if (!closedForWriting && !reset) {
            closeForWriting();
        }
        connection.doCloseStream(this);
    }
}
