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
 *
 * <p>
 * Receive-side flow control is applied where the application actually consumes
 * data: {@link #receiveData(byte[])} deducts from the advertised receive window
 * and the window is only replenished from {@link StreamInput#read}. This keeps
 * the incoming queue bounded by the receive window even if the consumer is
 * slow.
 */
public final class YamuxStream {

    private final long streamId;
    final YamuxSession connection;

    /** Remaining bytes the remote peer may still send before an update. */
    private int receiveWindowSize;

    /** Bytes consumed since the last WINDOW_UPDATE was sent. */
    private int consumedSinceUpdate;

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
        return new P2PStream(inputStream, outputStream, initiator, connection.getRemotePeerId());
    }

    // ---- read side ----

    private final class StreamInput extends P2PInputStream {
        @Override
        public int read(byte[] buf, int off, int len) {
            if (len == 0)
                return 0;
            int n;
            incomingLock.lock();
            try {
                n = readLocked(buf, off, len);
            } finally {
                incomingLock.unlock();
            }
            if (n > 0) {
                // Replenish the receive window only now that the application
                // has actually consumed the bytes. Never write a frame while
                // holding incomingLock: that would stall the session reader.
                connection.onBytesConsumed(YamuxStream.this, n);
            }
            return n;
        }

        private int readLocked(byte[] buf, int off, int len) {
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
        }

        @Override
        public void close() {
            YamuxStream.this.close();
        }
    }

    /**
     * Queues newly received data, enforcing the advertised receive window.
     *
     * @return {@code false} if the peer violated the receive window and the
     *         stream must be reset.
     */
    boolean receiveData(byte[] data) {
        incomingLock.lock();
        try {
            if (reset || incomingClosed) {
                return true;
            }
            if (data.length > receiveWindowSize) {
                return false;
            }
            receiveWindowSize -= data.length;
            incoming.add(data);
            incomingAvailable.signalAll();
            return true;
        } finally {
            incomingLock.unlock();
        }
    }

    /**
     * Accounts for {@code n} bytes consumed by the application and returns the
     * window increment to advertise, or {@code 0} if no update is needed.
     */
    int bytesConsumed(int n) {
        incomingLock.lock();
        try {
            consumedSinceUpdate += n;
            if (consumedSinceUpdate >= connection.initialWindowSize / 2) {
                int delta = consumedSinceUpdate;
                consumedSinceUpdate = 0;
                receiveWindowSize += delta;
                return delta;
            }
            return 0;
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

    /** Wakes both blocked readers and writers after an abnormal termination. */
    void terminate() {
        reset = true;
        incomingLock.lock();
        try {
            incomingClosed = true;
            incomingAvailable.signalAll();
        } finally {
            incomingLock.unlock();
        }
        windowLock.lock();
        try {
            windowAvailable.signalAll();
        } finally {
            windowLock.unlock();
        }
    }

    /** Wakes both blocked readers and writers after a normal local close. */
    void terminateLocally() {
        closedForWriting = true;
        incomingLock.lock();
        try {
            incomingClosed = true;
            incomingAvailable.signalAll();
        } finally {
            incomingLock.unlock();
        }
        windowLock.lock();
        try {
            windowAvailable.signalAll();
        } finally {
            windowLock.unlock();
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
            YamuxStream.this.close();
        }
    }

    private void waitForSendWindow() {
        windowLock.lock();
        try {
            while (sendWindow <= 0 && !reset && !closedForWriting) {
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
        try {
            if (!closedForWriting && !reset) {
                closeForWriting();
            }
        } finally {
            connection.doCloseStream(this);
        }
    }
}
