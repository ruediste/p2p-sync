package com.github.ruediste.p2psync.libp2p.echo;

import com.github.ruediste.p2psync.libp2p.core.P2PInputStream;
import com.github.ruediste.p2psync.libp2p.core.P2POutputStream;
import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolDescriptor;

/**
 * Test-only scaffolding: a trivial echo {@link ProtocolBinding}.
 *
 * <p>
 * The initiator sends bytes; the responder reads them and writes them back.
 * The controller can be used by the initiator to send and then read the echo.
 *
 * <p>
 * Protocol id: {@code /p2p-sync/echo/1.0.0}. This is NOT a real application
 * protocol — it exists purely as an end-to-end smoke test of the multistream +
 * Yamux data path (mirrors how upstream uses {@code Ping} as its minimal
 * example).
 */
public final class TrivialEchoProtocol implements ProtocolBinding<TrivialEchoProtocol.Controller, Void> {

    public static final String PROTOCOL = "/p2p-sync/echo/1.0.0";

    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return new ProtocolDescriptor(PROTOCOL);
    }

    @Override
    public Controller initInitiator(P2PStream stream, String selectedProtocol) {
        return new Controller(stream.getIn(), stream.getOut());
    }

    @Override
    public Void initResponder(P2PStream stream, String selectedProtocol) {
        // Responder: echo on a virtual thread
        Thread.ofVirtual().name("echo-responder").start(() -> {
            try {
                byte[] buf = new byte[4096];
                while (true) {
                    int n = stream.getIn().read(buf, 0, buf.length);
                    if (n < 0)
                        break;
                    stream.getOut().write(buf, 0, n);
                }
            } catch (RuntimeException ignored) {
                // stream closed
            }
        });
        return null;
    }

    /**
     * Initiator-side handle: write data then read back the echo.
     */
    public static final class Controller {
        private final P2PInputStream in;
        private final P2POutputStream out;

        Controller(P2PInputStream in, P2POutputStream out) {
            this.in = in;
            this.out = out;
        }

        public void echo(byte[] data) {
            out.write(data);
        }

        public int read(byte[] buf, int off, int len) {
            return in.read(buf, off, len);
        }

        public void close() {
            in.close();
            out.close();
        }
    }

}
