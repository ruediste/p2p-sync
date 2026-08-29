package com.github.ruediste.p2psync.libp2p.security.noise;

import com.github.ruediste.p2psync.libp2p.core.P2PStream;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolBinding;
import com.github.ruediste.p2psync.libp2p.multistream.ProtocolDescriptor;
import com.github.ruediste.p2psync.libp2p.security.SecureSession;

/**
 * The libp2p Noise security channel protocol binding ({@code /noise}).
 *
 * <p>
 * {@link #initInitiator} and {@link #initResponder} just delegate to
 * {@link NoiseXXHandshake#run} (a plain blocking call) and wrap the raw stream
 * in a {@link NoiseXXFramedInputStream}/{@link NoiseXXFramedOutputStream} pair,
 * handing the resulting secured {@link P2PStream} to the layer above.
 *
 * <p>
 * The Noise static keypair (distinct from the identity keypair) is generated
 * once per process.
 */
public final class NoiseXXProtocolBinding implements ProtocolBinding<SecureSession, SecureSession> {

    private static final String ANNOUNCE = "/noise";
    private static final ProtocolDescriptor DESCRIPTOR = new ProtocolDescriptor(ANNOUNCE);

    private final PrivKey localKey;
    private final PeerId expectedRemotePeerId;

    public NoiseXXProtocolBinding(PrivKey localKey) {
        this(localKey, null);
    }

    /**
     * @param expectedRemotePeerId when this side initiates a connection, the
     *                             handshake must authenticate the remote as this
     *                             peer (typically from the dialed multiaddr's
     *                             {@code /p2p/} component); {@code null} disables
     *                             the check.
     */
    public NoiseXXProtocolBinding(PrivKey localKey, PeerId expectedRemotePeerId) {
        this.localKey = localKey;
        this.expectedRemotePeerId = expectedRemotePeerId;
    }

    @Override
    public ProtocolDescriptor getProtocolDescriptor() {
        return DESCRIPTOR;
    }

    private SecureSession init(P2PStream stream, String selectedProtocol, boolean initiator) {
        NoiseXXHandshake.Result result = NoiseXXHandshake.run(stream, localKey,
                expectedRemotePeerId);

        NoiseXXFramedInputStream framedIn = new NoiseXXFramedInputStream(stream.getIn(),
                result.getInboundCipher());
        NoiseXXFramedOutputStream framedOut = new NoiseXXFramedOutputStream(stream.getOut(),
                result.getOutboundCipher());
        P2PStream securedStream = new P2PStream(framedIn, framedOut, initiator);

        return new SecureSession(result.getLocalId(), result.getRemoteId(), result.getRemotePubKey(),
                securedStream);
    }

    @Override
    public SecureSession initInitiator(P2PStream stream, String selectedProtocol) {
        return init(stream, selectedProtocol, true);
    }

    @Override
    public SecureSession initResponder(P2PStream stream, String selectedProtocol) {
        return init(stream, selectedProtocol, false);
    }

}