# Implementation Plan: Java port of jvm-libp2p core (Host / Transport / Noise / Yamux)

## Progress

| Milestone | Status | Notes |
|---|---|---|
| M0 — Project setup | ✅ Done | See log below. |
| M1 — Foundations | ⬜ Not started | |
| M2 — Keys and PeerId | ⬜ Not started | |
| M3 — Multistream-select | ⬜ Not started | |
| M4 — Netty channel plumbing | ⬜ Not started | |
| M5 — TCP transport / upgrade pipeline | ⬜ Not started | |
| M6 — Noise XX security transport | ⬜ Not started | |
| M7 — Yamux stream multiplexer | ⬜ Not started | |
| M8 — Network, ConnectionUpgrader, Host | ⬜ Not started | |
| M9 — End-to-end integration test / demo | ⬜ Not started | |

### Progress log

- **M0 (done)**: Added Netty 4.1.118.Final (`netty-buffer`/`-common`/`-transport`/`-handler`/`-codec`)
  and `protobuf-java` 3.25.5 to `pom.xml`; registered `kr.motd.maven:os-maven-plugin` as a build
  extension and `org.xolstice.maven.plugins:protobuf-maven-plugin` (bound to `generate-sources`/
  `compile`) using `protocArtifact` resolved via `${os.detected.classifier}`. Bumped
  `maven-compiler-plugin` to 3.13.0 (3.8.0 predates Java 21 support). Copied `crypto.proto` and
  `spipe.proto` from `upstream/jvm-libp2p/libp2p/src/main/proto/` into `src/main/proto/` verbatim
  (with a provenance header comment added). Verified `mvn generate-sources` emits
  `target/generated-sources/protobuf/java/{crypto/pb/Crypto.java,spipe/pb/Spipe.java}`, and added
  `src/test/java/.../libp2p/ProtobufToolchainTest.java` exercising round-trips of
  `Crypto.PublicKey` and `Spipe.NoiseHandshakePayload` — `mvn test` passes (3/3 tests green).

## Goal

Port the minimal subset of `jvm-libp2p` (`upstream/jvm-libp2p`) needed to:

1. Generate/load a node identity key pair and derive a `PeerId`.
2. Build a `Host` that listens on a TCP multiaddr.
3. Dial from one `Host` to another over TCP.
4. Negotiate and run a Noise (`XX` pattern) handshake to encrypt the connection.
5. Negotiate and run a Yamux session to multiplex streams over the encrypted connection.
6. Invoke a `ConnectionHandler` on both sides once the connection is fully upgraded, and support opening at least one application stream through the muxer as an end-to-end smoke test.

**Explicitly out of scope for this plan** (do not implement yet):

- Any peer discovery: mDNS, Kademlia DHT, `identify`, `identify/push`, bootstrap lists, relay/circuit-v2, AutoNAT, DCUtR, hole punching.
- `pubsub` / gossipsub.
- Alternative transports: WebSocket, QUIC, `/p2p-circuit`.
- Alternative security transports: TLS 1.3, plaintext, SECIO.
- Alternative muxers: Mplex.
- Key types other than **Ed25519** (RSA / Secp256k1 / ECDSA marshaling format is documented but not implemented yet — see "Future extension points").
- `AddressBook` persistence, DNS resolution for multiaddrs (`dns4`/`dns6`/`dnsaddr`) — only literal `ip4`/`ip6` addresses are required.
- Any actual "sync" application protocol — a trivial built-in echo protocol is allowed purely as an integration-test smoke test (mirrors how upstream uses `Ping` as its minimal example protocol).

Source of truth for behavior/wire formats is the Kotlin implementation under
`upstream/jvm-libp2p/libp2p/src/main/kotlin/io/libp2p/...` (read for reference only,
not copied/translated mechanically — do not check its git history). Where upstream test
fixtures exist (e.g. `PeerIdTest.kt`, `NoiseHandshakeTest.kt`, `YamuxHandlerTest.kt`), reuse
their expected byte sequences/vectors as cross-check data for the Java tests where practical.

## Package layout

All new code lives under a new base package, separate from the existing (currently empty)
application code in `com.github.ruediste.p2psync`:

```
com.github.ruediste.p2psync.libp2p
├── core                    Host, PeerId, PeerInfo, Connection, Stream, P2PChannel,
│                            ConnectionHandler, Network, AddressBook, Libp2pException + subtypes
├── core.multiaddr           Multiaddr, Protocol, Multihash, MultiaddrComponent, Varint
├── core.multistream         ProtocolBinding, ProtocolDescriptor, ProtocolMatcher, Multistream
├── multistream              MultistreamImpl, Negotiator, ProtocolSelect (wire protocol impl)
├── crypto                   Key, PrivKey, PubKey, KeyType, Marshaling (thin wrapper around the
│                            generated `crypto.pb.Crypto` protobuf classes)
├── crypto.keys              Ed25519PrivateKey, Ed25519PublicKey
├── transport                Transport (interface)
├── transport.netty          NettyTransport base, ConnectionBuilder, ConnectionOverNetty,
│                            StreamOverNetty, P2PChannelOverNetty, ConnectionUpgrader
├── transport.tcp            TcpTransport
├── security                 SecureChannel
├── security.noise           NoiseXXSecureChannel, NoiseXXHandshake, NoiseXXCodec,
│                            UShortLengthCodec (payload uses the generated `spipe.pb.Spipe`
│                            protobuf classes directly, no hand-written wrapper needed)
├── mux                      StreamMuxer, AbstractChildChannel, MuxChannel, MuxId
├── mux.yamux                YamuxStreamMuxer, YamuxHandler, YamuxFrameCodec, YamuxFrame,
│                            YamuxFlag, YamuxType, YamuxId, YamuxStreamIdGenerator
├── network                  NetworkImpl
└── host                     HostImpl, HostBuilder, MemoryAddressBook
```

This mirrors upstream's package split (`core`, `crypto`, `transport`, `multistream`,
`security.noise`, `mux.yamux`, `network`, `host`) closely enough that future diffs against
upstream (e.g. to port more protocols later) stay easy to reason about.

Protobuf-generated classes (see "Protobuf toolchain" below) live in their own `crypto.pb`/
`spipe.pb` packages, matching the `package` statements in the `.proto` files, and are treated
as generated code (not hand-edited, not manually maintained package-layout-wise).

## Dependencies to add to `pom.xml`

| Dependency | Purpose | Notes |
|---|---|---|
| `io.netty:netty-buffer`, `netty-common`, `netty-transport`, `netty-handler`, `netty-codec` | Pipeline/IO framework, TCP transport, and length-prefix framing (`ProtobufVarint32FrameDecoder`/`Prepender` classes live in `netty-codec` and work standalone, independent of the protobuf toolchain below). | Use a recent 4.1.x LTS (or 4.2.x) release; pin via a `<netty.version>` property. |
| `com.google.protobuf:protobuf-java` | Runtime for the generated `crypto.pb`/`spipe.pb` message classes (see "Protobuf toolchain" below). | Pin via a `<protobuf.version>` property; use a version whose matching `protoc` artifact is available for the build machine's OS/arch (e.g. `3.25.5`, matching upstream, or a newer 3.25.x/4.x release — all support the `proto2` syntax used by these files). |
| `org.junit.jupiter:junit-jupiter` **or** keep existing `junit:junit:4.11` | Tests | Project currently has JUnit 4; keep JUnit 4 for consistency unless a strong reason to move to JUnit 5 arises during implementation. |

Deliberately **not** added for this milestone:

- `tech.pegasys:noise-java` — see "Noise" section below: hand-roll `Noise_XX_25519_ChaChaPoly_SHA256`
  using only JDK 21 built-in crypto (`X25519`, `ChaCha20-Poly1305`, `HmacSHA256`), avoiding an extra
  runtime dependency for a single fixed handshake pattern.
- `org.bouncycastle:*` — not needed while only Ed25519 is supported; JDK 15+ has native
  `KeyPairGenerator.getInstance("Ed25519")` / `Signature.getInstance("Ed25519")` (JEP 339).
  Add BouncyCastle later if/when Secp256k1/RSA/ECDSA key types are ported.
- `com.github.multiformats:java-multibase`, Guava — not needed for the minimal `ip4`/`ip6`/`tcp`/`p2p`
  multiaddr subset.

## Protobuf toolchain and `.proto` files

Two upstream `.proto` files are needed for this scope — `crypto.proto` (key marshaling,
used by `PeerId` derivation and identity persistence) and `spipe.proto` (only its
`NoiseHandshakePayload` message is used, by the Noise `XX` handshake in M6). Both are tiny
(`proto2` syntax, ≤5 fields each) but should be compiled with a real protobuf toolchain rather
than hand-rolled, so the wire format is guaranteed byte-compatible with upstream/other libp2p
implementations and stays trivially maintainable if fields are ever added.

**Copy the `.proto` files themselves** (unmodified, so they stay wire-identical to upstream)
into this repo:

- `upstream/jvm-libp2p/libp2p/src/main/proto/crypto.proto` → `src/main/proto/crypto.proto`
- `upstream/jvm-libp2p/libp2p/src/main/proto/spipe.proto` → `src/main/proto/spipe.proto`

Add a one-line header comment to each copy noting it was copied verbatim from
`jvm-libp2p` (`libp2p/src/main/proto/…`), dual-licensed Apache-2.0/MIT, for reference/provenance.
Do not trim unused messages (`Propose`/`Exchange` in `spipe.proto`) — keeping the full file
matches upstream exactly and costs nothing (unused generated classes are harmless).

Neither file declares a `java_package` option, so `protoc`'s default naming applies: the
generated outer classes are `crypto.pb.Crypto` (nested `Crypto.PublicKey`, `Crypto.PrivateKey`,
`Crypto.KeyType`) and `spipe.pb.Spipe` (nested `Spipe.NoiseHandshakePayload`, plus the unused
`Spipe.Propose`/`Spipe.Exchange`).

**Maven toolchain wiring** (`pom.xml`), mirroring what upstream's Gradle build does with the
`com.google.protobuf` Gradle plugin, using the Maven-ecosystem equivalent:

- `kr.motd.maven:os-maven-plugin` registered as a build `<extension>`, so `${os.detected.classifier}`
  resolves the right native `protoc` binary for the build machine.
- `org.xolstice.maven.plugins:protobuf-maven-plugin` bound to the `generate-sources` phase,
  `compile` goal, configured with:
  - `<protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>`
  - default `protoSourceRoot` (`src/main/proto`) — matches the layout above, no extra config needed.
  - default output (`target/generated-sources/protobuf/java`), automatically registered as a
    compile source root by the plugin (no `build-helper-maven-plugin` needed).
- No gRPC plugin/codegen needed — only plain message classes.

Generated classes are build artifacts (`target/generated-sources/...`), not committed to
source control (already covered by the existing `.gitignore` pattern for `target/`).

Code in `crypto/Marshaling.java` (M2) and the Noise handshake payload handling (M6) call the
generated builders directly, e.g.:

```java
Crypto.PublicKey proto = Crypto.PublicKey.newBuilder()
    .setType(Crypto.KeyType.Ed25519)
    .setData(ByteString.copyFrom(pubKey.raw()))
    .build();
byte[] marshaled = proto.toByteArray();
```

```java
Spipe.NoiseHandshakePayload payload = Spipe.NoiseHandshakePayload.newBuilder()
    .setLibp2PKey(ByteString.copyFrom(identityPubKeyMarshaled))
    .setNoiseStaticKeySignature(ByteString.copyFrom(signature))
    .build();
```

This removes any need to hand-document the protobuf wire format in code comments — the
`.proto` files are the single source of truth for both this project and upstream.

## Milestones

Each milestone should be its own commit (or small set of commits) with passing tests before
moving to the next. Milestones 1–4 have no network I/O and are pure unit-testable logic;
milestone 5 onward requires loopback TCP integration tests.

### M0 — Project setup: dependencies and protobuf toolchain

Add the Netty and protobuf-java dependencies, the `os-maven-plugin` extension and
`protobuf-maven-plugin` build plugin described above to `pom.xml`, and copy `crypto.proto` /
`spipe.proto` into `src/main/proto/`. Acceptance: `mvn generate-sources` produces
`crypto.pb.Crypto` and `spipe.pb.Spipe` under `target/generated-sources/protobuf/java`, and
`mvn compile` succeeds with a trivial test referencing e.g.
`Crypto.PublicKey.newBuilder().setType(Crypto.KeyType.Ed25519)...build()` to confirm the
generated code is on the compile classpath. No hand-written libp2p code yet.

### M1 — Foundations: exceptions, varint, Multiaddr

Files: `core/Libp2pException.java` (+ subtypes: `ConnectionClosedException`,
`NoSuchLocalProtocolException`, `NoSuchRemoteProtocolException`, `ProtocolViolationException`,
`StreamNotActiveException`, `InternalErrorException`), `core/multiaddr/Varint.java`,
`core/multiaddr/Protocol.java` (enum: `IP4`, `IP6`, `TCP`, `P2P` only — skip DNS variants),
`core/multiaddr/Multihash.java` (Identity + SHA2-256 digest only), `core/multiaddr/Multiaddr.java`.

Reference: `core/multiformats/{Protocol,Multiaddr,Multihash}.kt`, `etc/types/ByteBufExt.kt`
(uvarint read/write).

Tests: uvarint round-trip; `Multiaddr` parse/serialize for `/ip4/127.0.0.1/tcp/4001`,
`/ip4/127.0.0.1/tcp/4001/p2p/<peerId>`; `getPeerId()` extraction; invalid-address rejection.

### M2 — Keys and PeerId

Files: `crypto/KeyType.java`, `crypto/PrivKey.java`, `crypto/PubKey.java`,
`crypto/keys/Ed25519PrivateKey.java`, `crypto/keys/Ed25519PublicKey.java`,
`crypto/Marshaling.java` (thin `marshalPublicKey`/`marshalPrivateKey`/`unmarshalPublicKey`/
`unmarshalPrivateKey` helpers that build/parse the **generated** `crypto.pb.Crypto.PublicKey`/
`Crypto.PrivateKey` protobuf messages — see "Protobuf toolchain" above; this milestone depends
on that toolchain being wired up first), `core/PeerId.java`.

Implementation notes:
- Use `KeyPairGenerator.getInstance("Ed25519")` / `Signature.getInstance("Ed25519")`
  (`java.security` + `java.security.spec.NamedParameterSpec`) — no external crypto library.
- `raw()` for Ed25519 keys must extract the 32-byte raw seed/point (not full PKCS8/X509 DER),
  to match `Data` in `crypto.proto`, matching upstream's `Ed25519PrivateKey`/`PublicKey.raw()`.
  This is the fiddly part: JDK returns PKCS8/X509-wrapped keys; extract the raw 32 bytes from
  the encoded `PrivateKeyInfo`/`SubjectPublicKeyInfo` (fixed offset for Ed25519, or use
  `java.security.spec.EdECPrivateKeySpec`/`EdECPublicKeySpec` +
  `KeyFactory.getInstance("Ed25519")` to go back and forth cleanly).
- `PeerId.fromPubKey(pubKey)`: marshal pubkey via `Marshaling`, then Multihash-encode:
  Identity digest if marshaled bytes.length <= 42, else SHA2-256 digest. Store/compare as
  raw bytes; `toBase58()`/`fromBase58()` (need a small Base58 encode/decode utility —
  ~30 lines, no dependency required).

Tests: generate key pair, marshal/unmarshal round trip, `PeerId.fromPubKey` produces a stable
base58 id; cross-check against `PeerIdTest.kt` fixtures/format if convenient (same input pubkey
bytes → same PeerId string).

### M3 — Multistream-select

Files: `core/multistream/ProtocolBinding.java`, `ProtocolDescriptor.java`,
`ProtocolMatcher.java`, `Multistream.java`, `multistream/MultistreamImpl.java`,
`multistream/Negotiator.java`, `multistream/ProtocolSelect.java`,
`core/P2PChannel.java`, `core/P2PChannelHandler.java`.

Reference: `core/multistream/*.kt`, `multistream/{MultistreamImpl,Negotiator,ProtocolSelect}.kt`,
`etc/events/ProtocolNegotiation.kt`, `etc/util/netty/protobuf/LimitedProtobufVarint32FrameDecoder.kt`.

Implementation notes:
- Wire protocol `/multistream/1.0.0`: varint32-length-prefixed, `\n`-terminated UTF-8 strings.
  Build the pipeline with Netty's `ProtobufVarint32FrameDecoder`/`ProtobufVarint32LengthFieldPrepender`
  (bound max frame length to 1024 bytes, mirroring `LimitedProtobufVarint32FrameDecoder`) +
  `StringDecoder`/`StringEncoder` + a small suffix-newline codec.
- `Negotiator` requester side sends header + first candidate immediately on `channelActive`;
  responder side matches against `ProtocolMatcher`s (support `strict` matcher only — sufficient
  for `/noise`, `/yamux/1.0.0`, and app protocol ids used later).
- `ProtocolSelect` listens for the negotiation-succeeded/failed user events, resolves the
  matching `ProtocolBinding`, and installs its handler right after itself in the pipeline.
- This exact mechanism is reused twice per connection (security negotiation, then muxer
  negotiation) and later once per stream (application protocol negotiation) — implement it
  generically now so milestones 5–8 just reuse it.

Tests: two in-memory `EmbeddedChannel`s wired back-to-back (or a loopback pair) negotiating a
fake protocol id; negotiation failure path (`NoSuchRemoteProtocolException`).

### M4 — Netty channel plumbing (Connection/Stream as P2PChannel)

Files: `transport/netty/P2PChannelOverNetty.java`, `ConnectionOverNetty.java`,
`StreamOverNetty.java`, `core/Connection.java`, `core/Stream.java`, `core/StreamHandler.java`
(`StreamPromise`), `core/ConnectionHandler.java`.

Reference: `transport/implementation/{P2PChannelOverNetty,ConnectionOverNetty,StreamOverNetty}.kt`,
`etc/Attributes.kt` (channel `AttributeKey`s: `CONNECTION`, `STREAM`, `PROTOCOL`, `REMOTE_PEER_ID`).

No network I/O yet — just the abstraction layer that lets the rest of the code depend on
`P2PChannel`/`Connection`/`Stream` instead of raw Netty `Channel`.

### M5 — TCP transport and connection upgrade pipeline

Files: `transport/Transport.java`, `transport/netty/NettyTransport.java` (Bootstrap/
ServerBootstrap setup, `NioSocketChannel`/`NioServerSocketChannel`,
`MultiThreadIoEventLoopGroup`/`NioIoHandler`), `transport/netty/ConnectionBuilder.java`,
`transport/netty/ConnectionUpgrader.java`, `transport/tcp/TcpTransport.java`.

Reference: `transport/implementation/{PlainNettyTransport,ConnectionBuilder}.kt`,
`transport/ConnectionUpgrader.kt`, `transport/tcp/TcpTransport.kt`.

Implementation notes:
- `ConnectionBuilder.initChannel`: wrap channel as `ConnectionOverNetty`, then
  `upgrader.establishSecureChannel(connection)` → on success set secure session, then
  `upgrader.establishMuxer(connection)` → on success set muxer session, then invoke the
  connection handler and complete the "connection established" future. This exact sequencing
  (TCP connect → security handshake → muxer negotiation → app connection handler) is the crux
  of the whole plan and should be a literal, testable method.
- `TcpTransport.handles(addr)`: multiaddr has `ip4`/`ip6` + `tcp` components.
- At this point `ConnectionUpgrader` can be stubbed/tested with a fake `SecureChannel`/
  `StreamMuxer` pair before Noise/Yamux exist (M6/M7), to validate the sequencing independently.

Tests: loopback TCP connect/listen with stub security+muxer bindings that just complete
immediately; verify `ConnectionHandler` fires exactly once per side with a fully "upgraded"
`Connection` (non-null secure/muxer session).

### M6 — Noise `XX` security transport

Files: `security/SecureChannel.java`, `security/noise/NoiseXXHandshake.java`
(the Noise `XX` state machine — reads/writes the **generated** `spipe.pb.Spipe.NoiseHandshakePayload`
protobuf message directly for the handshake payload, field 1 `libp2p_key` + field 2
`noise_static_key_signature`; the unused `libp2p_data`/`libp2p_data_signature` fields are simply
left unset), `security/noise/NoiseXXCodec.java`, `security/noise/UShortLengthCodec.java`,
`security/noise/NoiseXXSecureChannel.java`.

Reference: `security/noise/{NoiseXXSecureChannel,NoiseXXCodec,NoiseSecureChannelSession}.kt`,
and cross-check against `security/noise/{NoiseHandshakeTest,NoiseSecureChannelTest,
NoiseXXCodecTest}.kt` for known-vector sanity checks (payload signing string
`"noise-libp2p-static-key:"`, framing sizes, AEAD error handling).

Implementation notes — hand-rolled Noise using only JDK crypto (`Noise_XX_25519_ChaChaPoly_SHA256`):
- DH: ephemeral + static X25519 key pairs via `KeyPairGenerator.getInstance("X25519")`,
  shared secret via `KeyAgreement.getInstance("X25519")`.
- Hash/HKDF: SHA-256 (`MessageDigest`) + HMAC-SHA-256 (`Mac`) implementing Noise's
  `MixKey`/`MixHash`/`GetKeys`/`Split` per the [Noise Protocol spec](noiseprotocol.org) `XX`
  pattern (`e, ee, s, es` / `e, ee, se, s, es` message pattern — implement exactly the 3-message
  XX flow, no generic multi-pattern framework needed).
- AEAD: `Cipher.getInstance("ChaCha20-Poly1305")` with the Noise nonce format (8-byte LE
  counter, zero-padded to 12 bytes) for each handshake message and, after `Split`, for the
  ongoing per-direction `CipherState`s.
- Handshake payload: sign `"noise-libp2p-static-key:" + noiseStaticPubKeyBytes` with the
  libp2p identity private key (Ed25519), embed identity pubkey + signature in
  `NoiseHandshakePayload`, send as the (encrypted) payload of handshake messages 2 and 3.
  Verify the same on the receiving side and derive the remote `PeerId`; if dialing, must match
  the `PeerId` from the dial multiaddr's `/p2p/` component.
- Netty wiring identical to upstream: `UShortLengthCodec` (2-byte big-endian length prefix,
  max 65535) is pushed first and stays permanently; handshake handler runs the 3-message
  exchange; on success it's replaced by `NoiseXXCodec` (AEAD encrypt/decrypt every subsequent
  frame) + a splitter for outbound plaintext > frame capacity.
- `protocolDescriptor = ProtocolDescriptor("/noise")`.

Tests: two `NoiseXXHandshake` instances (initiator/responder) piped through an in-memory byte
channel (e.g. two `EmbeddedChannel`s forwarding buffers to each other, or a loopback
`SocketChannel` pair) complete a handshake and can then exchange AEAD-encrypted application
data both ways; tamper tests (flipped bit) must fail decryption; peer-id mismatch on dial must
be rejected.

### M7 — Yamux stream multiplexer

Files: `mux/StreamMuxer.java`, `mux/MuxId.java`, `mux/AbstractChildChannel.java`,
`mux/MuxChannel.java`, `mux/yamux/{YamuxFlag,YamuxType,YamuxId,YamuxFrame,YamuxFrameCodec,
YamuxStreamIdGenerator,YamuxHandler,YamuxStreamMuxer}.java`.

Reference: `core/mux/StreamMuxer.kt`, `mux/MuxHandler.kt`, `mux/yamux/*.kt`,
`etc/util/netty/mux/{AbstractMuxHandler,MuxChannel,MuxId,RemoteWriteClosed}.kt`,
`etc/util/netty/AbstractChildChannel.kt`; cross-check frame encoding against
`YamuxHandlerTest.kt`.

Implementation notes:
- Wire format (12-byte header, big-endian): `version:u8, type:u8, flags:u16, streamId:u32,
  length:u32` + optional `DATA` payload. `maxFrameDataLength = 1<<20`.
- Types: `DATA=0, WINDOW_UPDATE=1, PING=2, GO_AWAY=3`. Flags (single-flag only):
  `SYN=1, ACK=2, FIN=4, RST=8`.
- Stream IDs: odd if this side initiated the underlying *connection*, even otherwise
  (session id `0` reserved). `AbstractChildChannel`/`MuxChannel` exposes every Yamux stream as
  a genuine Netty child `Channel` (own pipeline, registered on the connection's `EventLoop`) so
  the existing `StreamOverNetty`/multistream-select machinery from M3/M4 works unmodified for
  per-stream protocol negotiation.
- Flow control: `INITIAL_WINDOW_SIZE = 256 * 1024`; send `WINDOW_UPDATE` once the receive
  window drops below half; buffer outbound writes while `sendWindowSize` is exhausted, capped
  by `maxBufferedConnectionWrites` (default 10 MiB) — exceeding it resets the stream rather
  than blocking the connection.
- `YamuxStreamMuxer.protocolDescriptor = ProtocolDescriptor("/yamux/1.0.0")`; `initChannel`
  pushes `YamuxFrameCodec` then `YamuxHandler` onto the (already Noise-encrypted) connection
  channel.

Tests: two `YamuxHandler`s over a loopback pair (or piped `EmbeddedChannel`s): open a stream
from each side, send/receive data larger than one window, verify `WINDOW_UPDATE` is emitted
and unblocks a previously buffered write, verify `RST`/`FIN` close semantics, verify
ack-backlog/overflow limits raise the expected exceptions.

### M8 — Network, ConnectionUpgrader wiring, Host

Files: `network/NetworkImpl.java`, `host/HostImpl.java`, `host/HostBuilder.java`,
`host/MemoryAddressBook.java`, `core/Host.java`, `core/Network.java`, `core/AddressBook.java`,
`core/PeerInfo.java`.

Reference: `network/NetworkImpl.kt`, `host/{HostImpl,MemoryAddressBook}.kt`,
`core/dsl/{Builders.kt,BuilderJ.kt}`, `core/dsl/HostBuilder.java` (upstream's own Java-facing
builder — useful naming/shape reference), and `upstream/nabu/.../HostBuilder.java` for how a
downstream Java project actually calls the builder in practice.

Implementation notes:
- `HostBuilder` (fluent, Java-idiomatic — no Kotlin DSL lambdas-with-receiver needed):
  `.privateKey(key)` (or auto-generate Ed25519 if unset), `.listenAddress(String)`,
  `.connectionHandler(ConnectionHandler)`. Defaults: TCP transport, `NoiseXXSecureChannel`,
  Yamux (do **not** default to Mplex — it isn't ported). `.build()` wires:
  `muxer -> secureChannel(privKey, muxer) -> ConnectionUpgrader(security multistream,
  muxer multistream) -> TcpTransport(upgrader) -> NetworkImpl(transports, broadcastConnHandler)
  -> HostImpl(...)`.
- `NetworkImpl.connect(peerId, addrs)`: reuse existing connection to `peerId` if present
  (matched by `secureSession().remoteId`), else dial and append `/p2p/<peerId>` to the target
  multiaddr for post-Noise identity verification (M6).
- `HostImpl.start()`: bind all configured listen addresses; `stop()`: close network/transports.
- `Host.newStream(...)` is needed for the M9 smoke test: locate the app `ProtocolBinding` and
  call `connection.muxerSession().createStream(...)`.

Tests: unit tests for `NetworkImpl` connection reuse/dedupe logic with fake transports.

### M9 — End-to-end integration test / demo

Goal: prove the full stack works exactly as requested ("start two nodes, and establish a
connection between them"), plus a minimal stream smoke test to exercise Yamux end-to-end.

Files: `src/test/java/.../libp2p/EndToEndConnectionTest.java` (primary deliverable), and
optionally a tiny `TrivialEchoProtocol` (`ProtocolBinding`) purely for the stream smoke test —
clearly marked as test-only scaffolding, not a real application protocol.

Test outline:
```java
Host nodeA = new HostBuilder()
    .listenAddress("/ip4/127.0.0.1/tcp/0")
    .connectionHandler(conn -> connectionsOnA.add(conn))
    .build();
Host nodeB = new HostBuilder()
    .listenAddress("/ip4/127.0.0.1/tcp/0")
    .connectionHandler(conn -> connectionsOnB.add(conn))
    .build();

nodeA.start().get();
nodeB.start().get();

Multiaddr aAddr = nodeA.listenAddresses().get(0).withP2P(nodeA.getPeerId());

Connection conn = nodeB.getNetwork().connect(nodeA.getPeerId(), aAddr).get(5, SECONDS);

// assertions:
// - conn.secureSession().getRemoteId() equals nodeA.getPeerId()
// - conn.muxerSession() is non-null (Yamux negotiated)
// - connectionsOnA and connectionsOnB each received exactly one Connection
// - (optional) open a stream from B to A with TrivialEchoProtocol, write bytes, read the
//   same bytes back, proving per-stream multistream-select + Yamux data path works
```

Also add a runnable demo (`main` method or a small `examples` source set, matching upstream's
`examples/pinger` spirit) that starts two nodes as separate JVM processes/ports and logs the
successful connection — useful for manual smoke-testing outside of the test suite.

## Future extension points (explicitly not part of this plan)

- Additional key types (Secp256k1, RSA, ECDSA) — `crypto.KeyType`/`Marshaling` already delegate
  to the generated `crypto.pb.Crypto.KeyType` enum, so adding a case is a small, mechanical
  change; add BouncyCastle only when actually needed for the underlying signature primitives.
- Additional muxer (Mplex) or security transport (TLS 1.3) — both plug into the same
  `ProtocolBinding`/multistream-select machinery from M3, no changes needed elsewhere.
- Peer discovery (mDNS/Kademlia), `identify` protocol, connection gating/backoff, `AddressBook`
  persistence, DNS multiaddr resolution.
- Relay/circuit-v2, AutoNAT, DCUtR (hole punching) — mentioned in the project wiki's long-term
  P2P mechanism list but out of scope until basic connectivity is solid.
