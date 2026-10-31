package com.github.ruediste.p2psync.node;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolDescriptor;

/**
 * Length-prefixed protobuf request/response transport for user synchronization.
 */
final class UserSyncProtocol implements ProtocolBinding<UserSyncProtocol.Controller, Void> {
    static final String PROTOCOL = "/p2p-sync/user-sync/1.0.0";
    private static final int MAX_MESSAGE_SIZE = 128 * 1024 * 1024;

    private final Function<byte[], byte[]> requestHandler;

    UserSyncProtocol(Function<byte[], byte[]> requestHandler) {
        this.requestHandler = Objects.requireNonNull(requestHandler);
    }

    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(PROTOCOL);
    }

    @Override
    public Controller initInitiator(P2PStream stream, String selectedProtocol) {
        return new Controller(stream);
    }

    @Override
    public Void initResponder(P2PStream stream, String selectedProtocol) {
        Thread.ofVirtual().name("user-sync-responder").start(() -> {
            try (stream) {
                byte[] request = readFrame(stream.getIn());
                writeFrame(stream.getOut(), requestHandler.apply(request));
            } catch (RuntimeException ignored) {
                stream.close();
            }
        });
        return null;
    }

    static final class Controller implements AutoCloseable {
        private final P2PStream stream;

        Controller(P2PStream stream) {
            this.stream = stream;
        }

        byte[] exchange(byte[] request) {
            writeFrame(stream.getOut(), request);
            return readFrame(stream.getIn());
        }

        @Override
        public void close() {
            stream.close();
        }
    }

    private static byte[] readFrame(P2PInputStream input) {
        byte[] lengthBytes = new byte[Integer.BYTES];
        input.readFully(lengthBytes);
        int length = ByteBuffer.wrap(lengthBytes).getInt();
        if (length < 0 || length > MAX_MESSAGE_SIZE)
            throw new IllegalArgumentException("Invalid user-sync message size: " + length);
        byte[] message = new byte[length];
        input.readFully(message);
        return message;
    }

    private static void writeFrame(P2POutputStream output, byte[] message) {
        if (message.length > MAX_MESSAGE_SIZE)
            throw new IllegalArgumentException("User-sync message exceeds maximum size");
        output.write(ByteBuffer.allocate(Integer.BYTES).putInt(message.length).array());
        output.write(message);
        output.flush();
    }
}
