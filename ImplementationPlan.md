# Implementation Plan: Java port of jvm-libp2p core (Host / Transport / Noise / Yamux)

See `ARCHITECTURE.md` for the transport/stream/threading architecture this plan implements
(custom blocking-I/O TCP server, virtual threads, `P2PInputStream`/`P2POutputStream`) — the
milestones below assume that document as background reading.

## Progress

| Milestone                               | Status         | Notes          |
| --------------------------------------- | -------------- | -------------- |
| M0 — Project setup                      | ✅ Done        | See log below. |
| M1 — Foundations                        | ✅ Done        | See log below. |
| M2 — Keys and PeerId                    | ✅ Done        | See log below. |
| M3 — Multistream-select                 | ⬜ Not started |                |
| M4 — Core stream abstractions (P2P{In,Out}putStream, Connection/Stream) | ⬜ Not started | |
| M5 — TCP transport / upgrade pipeline   | ⬜ Not started |                |
| M6 — Noise XX security transport        | ⬜ Not started |                |
| M7 — Yamux stream multiplexer           | ⬜ Not started |                |
| M8 — Network, ConnectionUpgrader, Host  | ⬜ Not started |                |
| M9 — End-to-end integration test / demo | ⬜ Not started |                |

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
  **(Superseded)**: the Netty dependencies added here were removed again once the
  transport/stream architecture was changed to blocking I/O on virtual threads — see the "Plan
  deviation (retroactively applies to M0 onward — transport/stream architecture)" entry below.
  Only `protobuf-java` (still needed for `crypto.proto`/`spipe.proto`) remains.
- **Plan deviation (applies from M1 onward)**: no bespoke `Libp2pException` hierarchy. Error
  conditions throw plain JDK exceptions (`IllegalArgumentException`/`RuntimeException`) instead
  of a custom taxonomy — see the note under M1 below for rationale.
- **M1 (done)**: Implemented under `com.github.ruediste.p2psync.libp2p.core[.multiaddr]`:
  `Varint` (uvarint read/write on the project's own minimal `ByteBuf`, mirrors `ByteBufExt.kt`;
  originally implemented against Netty's `ByteBuf` — see the transport/stream architecture
  deviation note below), `Protocol` enum
  (`IP4`/`TCP`/`IP6`/`P2P` only; parsers/stringifiers implemented as static-method references
  on the enum itself rather than Kotlin-style top-level lambda constants, since Java enum
  constants can't forward-reference sibling static fields — method references sidestep that
  ordering restriction), `MultiaddrComponent`, `Multiaddr` (string/byte (de)serialization,
  `getPeerId`/`withP2P`/`withComponent`/`merged`/`concatenated`; the `p2p-circuit`-aware split
  logic and path-style component handling from upstream were dropped as out of scope),
  `Multihash` (trimmed to `IDENTITY`/`SHA2_256` only, simple `sum`/`decode` byte-array API
  instead of upstream's pluggable digest registry), `Base58` (ported near-verbatim from
  `etc/encode/Base58.kt`), and a first cut of `PeerId` (byte storage,
  `toBase58`/`fromBase58`/`toHex`/`fromHex`/`random`, `equals`/`hashCode`; `fromPubKey` deferred
  to M2). `PeerId` and `Base58` were pulled forward from M2 into M1 because `Protocol.P2P`'s
  validator and `Multiaddr.getPeerId()`/`withP2P()` already depend on them (same layering as
  upstream, where `core/multiformats/Protocol.kt` imports `io.libp2p.core.PeerId`). IPv6
  string formatting uses plain `java.net.Inet6Address#getHostAddress()` (no leading-zero
  compression like Guava's `InetAddresses.toAddrString`), a known cosmetic gap versus upstream
  that doesn't affect the `ip4`/`tcp`/`p2p` paths this project actually exercises. Added
  `VarintTest`, `MultiaddrTest`, `MultihashTest`, `PeerIdTest` — `mvn test` passes (22/22 tests
  green, including the M0 tests).
- **M2 (done)**: Implemented under `com.github.ruediste.p2psync.libp2p.crypto[.keys]`:
  `PubKey`/`PrivKey` (abstract classes storing the generated `crypto.pb.Crypto.KeyType` directly
  as `keyType`, mirroring upstream's `Key`/`PubKey`/`PrivKey`; `bytes()`/`equals()`/`hashCode()`
  implemented on the base classes since they're identical for every key type), `KeyType` (a
  small wrapper enum around `Crypto.KeyType`, trimmed to `ED25519` only — see the M1-era "out of
  scope" note; `PrivKey.generate(KeyType)`/`generate(KeyType, SecureRandom)` are the Java
  static-method equivalent of upstream's top-level `generateKeyPair(type, bits, random)`
  function in `Key.kt`, dispatching only to `Ed25519PrivateKey.generateKeyPair` for now),
  `Marshaling` (`marshalPublicKey`/`marshalPrivateKey`/`unmarshalPublicKey`/`unmarshalPrivateKey`,
  building/parsing the generated `Crypto.PublicKey`/`Crypto.PrivateKey` messages directly),
  `keys.Ed25519PrivateKey`/`keys.Ed25519PublicKey` (backed by the JDK's built-in `"Ed25519"`
  `KeyPairGenerator`/`KeyFactory`/`Signature` providers — JEP 339, no BouncyCastle, as planned).
  `PeerId.fromPubKey` added on top of the M1 `PeerId`.
  **Plan deviation**: deriving a public key from a raw 32-byte private seed (needed by
  `Ed25519PrivateKey.unmarshal`, e.g. when loading a persisted identity) has no direct
  `java.security` API — unlike BouncyCastle's `Ed25519PrivateKeyParameters.generatePublicKey()`,
  the JDK's `KeyFactory` can reconstruct a `PrivateKey` from a raw seed
  (`EdECPrivateKeySpec`) but cannot re-derive the associated public point from it. Worked around
  by feeding the raw seed through a `SecureRandom` stand-in (`nextBytes` just returns those
  exact 32 bytes) into `KeyPairGenerator.getInstance("Ed25519")`: empirically (and per
  `Ed25519KeysTest`) the `SunEC` provider consumes exactly 32 bytes from the supplied
  `SecureRandom` as the private scalar/seed with no extra hashing, so this reliably regenerates
  the identical key pair including the public point — see the Javadoc on `Ed25519PrivateKey`
  for the full reasoning. Encoding/decoding the raw 32-byte Ed25519 _public_ key point itself
  uses fully standard JEP 339 API (`EdECPoint`/`EdECPublicKeySpec`/`KeyFactory`), no trick
  needed there. Added `Ed25519KeysTest`, `MarshalingTest`, and extended `PeerIdTest` with
  `fromPubKey` tests, including a hand-computed golden fixture (fixed raw Ed25519 pubkey →
  expected protobuf marshaling → expected identity-multihash wrapping → expected base58 string,
  each layer computed independently in the test comment) cross-checking the exact
  marshal-then-multihash pipeline (no upstream Kotlin test happens to cover an Ed25519
  `PeerIdTest` fixture — only RSA/Secp256k1 — so this fixture was derived by hand instead of
  copied) — `mvn test` passes (38/38 tests green).
- **Plan deviation (retroactively applies to M0 onward — transport/stream architecture)**:
  the original plan built the whole transport/security/muxer stack on Netty (channels,
  pipelines, an `EventLoopGroup`). This has been dropped in favor of a hand-rolled TCP server
  and plain **blocking I/O on Java 21 virtual threads** (JEP 444) throughout — see
  `ARCHITECTURE.md` for the full rationale and thread model. Concretely:
  - `io.netty:netty-buffer`/`-common`/`-transport`/`-handler`/`-codec` are no longer
    dependencies (removed from `pom.xml`; only `protobuf-java` remains as a runtime
    dependency, see the updated "Dependencies" table below).
  - The M1 `Varint`/`Multiaddr`/`MultiaddrComponent`/`Multihash`/`Protocol` code, which used
    Netty's `ByteBuf`/`Unpooled` purely as a growable-byte-buffer utility (no networking
    involved), now uses a small project-owned `core/multiaddr/ByteBuf.java` implementing just
    the handful of methods those classes need (sequential big-endian writes with
    auto-growth, a rewindable reader index, a couple of read-only view helpers) — same
    behavior, zero external dependency. `VarintTest`/`MultiaddrTest`/`MultihashTest` updated
    accordingly; `mvn test` still passes (38/38 tests green) after the removal.
  - Every later milestone (M3 onward, none of which had been started yet) is rewritten below
    to use the project's own minimal `P2PInputStream`/`P2POutputStream` abstraction instead of
    Netty `Channel`/`ByteBuf`, and a directly-implemented `java.net.ServerSocket`-based TCP
    server instead of Netty's `ServerBootstrap`/`NioServerSocketChannel`.

## Goal

Port the minimal subset of `jvm-libp2p` (`upstream/jvm-libp2p`) needed to:

1. Generate/load a node identity key pair and derive a `PeerId`.
2. Build a `Host` that listens on a TCP multiaddr.
3. Dial from one `Host` to another over TCP.
4. Negotiate and run a Noise (`XX` pattern) handshake to encrypt the connection.
5. Negotiate and run a Yamux session to multiplex streams over the encrypted connection.
6. Invoke a `ConnectionHandler` on both sides once the connection is fully upgraded, and support opening at least one application stream through the muxer as an end-to-end smoke test. Implement ping for this purpose.

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

**Deliberate architectural deviation from upstream**: upstream `jvm-libp2p` is built on Netty
(non-blocking I/O, channel pipelines, an event-loop group). This project does **not** use
Netty anywhere. Instead:

- Transport, security (Noise), and multiplexing (Yamux) are all implemented directly on top of
  plain blocking `java.net.Socket` I/O.
- Every connection and every multiplexed stream runs on its own **virtual thread**
  (`java.lang.Thread`, JEP 444, Java 21) — blocking a virtual thread on a socket read/write is
  cheap, so there is no need for non-blocking I/O, callbacks, futures/promises, or a
  channel-pipeline abstraction to get concurrency across many connections/streams.
- The only "framework" abstraction introduced is a minimal pair of classes,
  `core.P2PInputStream`/`core.P2POutputStream`, that every layer (raw TCP socket, Noise-framed
  connection, individual Yamux stream) implements/wraps. See `ARCHITECTURE.md` for the full
  rationale, the exact shape of these two classes, and the thread model in detail — the
  milestones below assume that document as background reading.

## Package layout

All new code lives under a new base package, separate from the existing (currently empty)
application code in `com.github.ruediste.p2psync`:

```
com.github.ruediste.p2psync.libp2p
├── core                    Host, PeerId, PeerInfo, Connection, Stream, P2PInputStream,
│                            P2POutputStream, ConnectionHandler, StreamHandler, Network,
│                            AddressBook, Base58
├── core.multiaddr           Multiaddr, Protocol, Multihash, MultiaddrComponent, Varint, ByteBuf
├── core.multistream         ProtocolBinding, ProtocolDescriptor, ProtocolMatcher, Multistream
├── multistream              MultistreamImpl, Negotiator, ProtocolSelect (wire protocol impl,
│                            implemented directly against P2PInputStream/P2POutputStream —
│                            no pipeline/frame-decoder framework involved)
├── crypto                   Key, PrivKey, PubKey, KeyType, Marshaling (thin wrapper around the
│                            generated `crypto.pb.Crypto` protobuf classes)
├── crypto.keys              Ed25519PrivateKey, Ed25519PublicKey
├── transport                Transport (interface)
├── transport.tcp            TcpTransport (dial), TcpServer (accept loop + per-connection
│                            virtual thread), TcpConnection, SocketP2PInputStream/
│                            SocketP2POutputStream (thin adapters over `Socket#getInputStream`/
│                            `#getOutputStream`), ConnectionBuilder, ConnectionUpgrader
├── security                 SecureChannel
├── security.noise           NoiseXXSecureChannel, NoiseXXHandshake, NoiseXXFramedInputStream,
│                            NoiseXXFramedOutputStream (payload uses the generated
│                            `spipe.pb.Spipe` protobuf classes directly, no hand-written
│                            wrapper needed)
├── mux                      StreamMuxer, MuxedConnection, MuxedStream, MuxId
├── mux.yamux                YamuxStreamMuxer, YamuxConnection, YamuxStream, YamuxFrameIO,
│                            YamuxFrame, YamuxFlag, YamuxType, YamuxId, YamuxStreamIdGenerator
├── network                  NetworkImpl
└── host                     HostImpl, HostBuilder, MemoryAddressBook
```

This mirrors upstream's package split (`core`, `crypto`, `transport`, `multistream`,
`security.noise`, `mux.yamux`, `network`, `host`) closely enough that future diffs against
upstream (e.g. to port more protocols later) stay easy to reason about, even though the
`transport.netty`/`P2PChannelOverNetty`/`AbstractChildChannel` family of Netty-shaped classes
upstream uses has no equivalent here (replaced by the blocking-stream classes above).

Protobuf-generated classes (see "Protobuf toolchain" below) live in their own `crypto.pb`/
`spipe.pb` packages, matching the `package` statements in the `.proto` files, and are treated
as generated code (not hand-edited, not manually maintained package-layout-wise).

## Dependencies to add to `pom.xml`

| Dependency                                                                | Purpose                                                                                             | Notes                                                                                                                                                                                                                                                         |
| -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.google.protobuf:protobuf-java`                                        | Runtime for the generated `crypto.pb`/`spipe.pb` message classes (see "Protobuf toolchain" below).    | Pin via a `<protobuf.version>` property; use a version whose matching `protoc` artifact is available for the build machine's OS/arch (e.g. `3.25.5`, matching upstream, or a newer 3.25.x/4.x release — all support the `proto2` syntax used by these files). |
| `org.junit.jupiter:junit-jupiter` **or** keep existing `junit:junit:4.11` | Tests                                                                                                  | Project currently has JUnit 4; keep JUnit 4 for consistency unless a strong reason to move to JUnit 5 arises during implementation.                                                                                                                           |

Deliberately **not** added:

- `io.netty:*` (`netty-buffer`/`-common`/`-transport`/`-handler`/`-codec`) — superseded plan; see
  the architectural deviation note above and `ARCHITECTURE.md`. The transport/security/muxer
  stack is hand-rolled directly on top of blocking `java.net.Socket` I/O and Java 21 virtual
  threads (JEP 444), which avoids both the runtime dependency and the conceptual overhead of
  Netty's channel/pipeline/event-loop model for a project that has no need for non-blocking I/O
  at the connection counts this system operates at.
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

Add the `protobuf-java` dependency, the `os-maven-plugin` extension and
`protobuf-maven-plugin` build plugin described above to `pom.xml`, and copy `crypto.proto` /
`spipe.proto` into `src/main/proto/`. Acceptance: `mvn generate-sources` produces
`crypto.pb.Crypto` and `spipe.pb.Spipe` under `target/generated-sources/protobuf/java`, and
`mvn compile` succeeds with a trivial test referencing e.g.
`Crypto.PublicKey.newBuilder().setType(Crypto.KeyType.Ed25519)...build()` to confirm the
generated code is on the compile classpath. No hand-written libp2p code yet. (No Netty
dependency is added — see the architectural deviation note under "Goal" above.)

### M1 — Foundations: varint, Multiaddr

**Deviation from the original plan**: no bespoke `Libp2pException` hierarchy is introduced.
Error conditions throughout this port simply throw plain JDK exceptions
(`IllegalArgumentException`/`IllegalStateException`/`RuntimeException`) with a descriptive
message — a dedicated exception taxonomy (`ConnectionClosedException`,
`NoSuchLocalProtocolException`, `NoSuchRemoteProtocolException`, `ProtocolViolationException`,
`StreamNotActiveException`, `InternalErrorException`, ...) adds ceremony without enough payoff
for this project's scope, since nothing here needs to catch/handle those subtypes differently.
Later milestones referencing e.g. `NoSuchRemoteProtocolException` should read this as "throw a
`RuntimeException` describing the same failure" instead.

Files: `core/multiaddr/Varint.java`,
`core/multiaddr/Protocol.java` (enum: `IP4`, `IP6`, `TCP`, `P2P` only — skip DNS variants),
`core/multiaddr/Multihash.java` (Identity + SHA2-256 digest only), `core/multiaddr/Multiaddr.java`,
`core/multiaddr/MultiaddrComponent.java`, `core/Base58.java`, `core/PeerId.java` (minimal version:
byte storage, `toBase58`/`fromBase58`/`fromHex`/`toHex`/`random`, `equals`/`hashCode`; `fromPubKey`
is added in M2 once `crypto.PubKey`/`Marshaling` exist — needed already in M1 because
`Protocol.P2P`'s validator and `Multiaddr.getPeerId()` both depend on `PeerId`, mirroring
upstream's own layering).

Reference: `core/multiformats/{Protocol,Multiaddr,MultiaddrComponent,Multihash}.kt`,
`etc/types/ByteBufExt.kt` (uvarint read/write), `core/PeerId.kt`, `etc/encode/Base58.kt`.

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
`core/P2PInputStream.java`, `core/P2POutputStream.java`.

Reference: `core/multistream/*.kt`, `multistream/{MultistreamImpl,Negotiator,ProtocolSelect}.kt`,
`etc/events/ProtocolNegotiation.kt` (for the negotiation state machine's shape only — the
Netty-pipeline-event mechanics described there don't apply here).

`P2PInputStream`/`P2POutputStream` (see `ARCHITECTURE.md`) are introduced here because
`Negotiator` is the first thing that needs to read/write bytes; they are two tiny abstract
classes exposing bulk array read/write (the primitive every layer must implement) plus a
single-byte convenience method defined once in terms of it:

```java
public abstract class P2PInputStream implements Closeable {
    public abstract int read(byte[] buf, int off, int len) throws IOException;

    public int read() throws IOException {
        byte[] single = new byte[1];
        int n = read(single, 0, 1);
        return n < 0 ? -1 : single[0] & 0xFF;
    }
}

public abstract class P2POutputStream implements Closeable {
    public abstract void write(byte[] buf, int off, int len) throws IOException;

    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }
}
```

Implementation notes:

- Wire protocol `/multistream/1.0.0`: varint32-length-prefixed, `\n`-terminated UTF-8 strings.
  Implement a tiny pair of helpers, `MultistreamFraming.writeMessage(P2POutputStream, String)`/
  `readMessage(P2PInputStream)`, that do the varint-length-prefix + UTF-8 + `\n` framing by
  calling `read`/`write` directly (the varint length prefix is naturally decoded one byte at a
  time via `P2PInputStream#read()`, stopping as soon as the continuation bit is clear; the
  fixed-length string body afterwards uses a `readFully`-style loop over
  `read(byte[], int, int)`, since that — like `InputStream#read(byte[], int, int)` — is allowed
  to return fewer bytes than requested); cap the frame length at 1024 bytes (mirrors upstream's
  `LimitedProtobufVarint32FrameDecoder`) and throw on overflow. No frame-decoder/pipeline
  framework needed — this is a handful of straight-line blocking calls.
- `Negotiator`: requester side runs synchronously — write the multistream header + first
  candidate, then blocking-read the response line(s) and loop until a protocol matches or the
  candidates are exhausted (throwing if none do); responder side blocking-reads the header/first
  candidate, matches against `ProtocolMatcher`s (support `strict` matcher only — sufficient for
  `/noise`, `/yamux/1.0.0`, and app protocol ids used later), and writes back accept/reject lines.
  Because this all runs on a virtual thread (see M5), there is no `channelActive`/event-callback
  shape to this at all — `Negotiator.negotiate(P2PInputStream, P2POutputStream, ...)` is a plain
  blocking method that returns the agreed protocol id (or throws).
- `ProtocolSelect` resolves the matching `ProtocolBinding` once negotiation succeeds and hands
  it the same `P2PInputStream`/`P2POutputStream` pair to continue reading/writing on — there is
  no pipeline to "install a handler into"; the caller just invokes the binding's handler method
  directly with the streams.
- This exact mechanism is reused twice per connection (security negotiation, then muxer
  negotiation) and later once per stream (application protocol negotiation) — implement it
  generically now so milestones 5–8 just reuse it.

Tests: two in-memory `P2PInputStream`/`P2POutputStream` pairs, each side driven from its own
plain (platform) thread since `Negotiator` blocks, connected via a small in-memory
byte-pipe implementation (e.g. backed by `java.io.PipedInputStream`/`PipedOutputStream`, or a
tiny hand-rolled blocking byte queue — this pipe implementation is reusable by later
milestones' tests too, so it's worth writing once as test-support code), negotiating a fake
protocol id; negotiation failure path (plain `RuntimeException`, no remote protocol match —
see M1 deviation note on exceptions).

### M4 — Core stream abstractions (Connection/Stream)

Files: `core/Connection.java`, `core/Stream.java`, `core/StreamHandler.java`
(`StreamPromise`), `core/ConnectionHandler.java`.

Reference: `core/{Connection,Stream}.kt`, `core/ConnectionHandler.kt` (for the shape of the
public API only — the underlying `P2PChannel`/Netty-`Channel` implementation upstream uses does
not apply here: `Connection`/`Stream` are implemented directly in terms of the connection's/
stream's own `P2PInputStream`/`P2POutputStream` pair from M3, plus metadata (remote `PeerId`,
negotiated protocol, secure/muxer session handles) — no `AttributeKey`-on-a-channel indirection
needed since there's no channel).

No network I/O yet — just the `Connection`/`Stream` interfaces/base classes that let the rest
of the code depend on those instead of directly on `P2PInputStream`/`P2POutputStream` +
raw sockets. `P2PInputStream`/`P2POutputStream` themselves were already introduced in M3.

### M5 — TCP transport, custom TCP server, and the connection upgrade pipeline

Files: `transport/Transport.java`, `transport/tcp/TcpServer.java` (accept loop),
`transport/tcp/TcpTransport.java` (dial + `handles(addr)`), `transport/tcp/TcpConnection.java`,
`transport/tcp/SocketP2PInputStream.java`, `transport/tcp/SocketP2POutputStream.java`,
`transport/ConnectionBuilder.java`, `transport/ConnectionUpgrader.java`.

Reference: `transport/implementation/{PlainNettyTransport,ConnectionBuilder}.kt`,
`transport/ConnectionUpgrader.kt`, `transport/tcp/TcpTransport.kt` (for the *sequencing*/
responsibilities of these classes only — the actual implementation is plain blocking
`java.net.ServerSocket`/`java.net.Socket`, not Netty `Bootstrap`/`ServerBootstrap`). See
`ARCHITECTURE.md` for the full thread-model writeup this milestone implements.

Implementation notes:

- `TcpServer`: wraps a bound `java.net.ServerSocket`. `start()` spawns one dedicated **virtual
  thread** running `while (!closed) { Socket s = serverSocket.accept(); Thread.ofVirtual()
  .start(() -> handleAccepted(s)); }`. Each accepted socket gets its own fresh virtual thread
  running `handleAccepted`, which performs the entire upgrade sequence synchronously (see
  `ConnectionBuilder` below) before invoking the application's `ConnectionHandler`. `close()`
  closes the `ServerSocket` (which unblocks `accept()` with a `SocketException`, ending the
  accept-loop thread) and, if desired, tracks + closes still-open accepted connections.
- `TcpTransport.dial(multiaddr)`: `new Socket(host, port)` (blocking connect, optionally with a
  connect timeout via `Socket#connect(SocketAddress, int)`), then runs the exact same
  `ConnectionBuilder` upgrade sequence synchronously on the calling virtual thread. Callers that
  want a non-blocking-looking API (e.g. `Host.connect(...)` returning something awaitable) wrap
  this blocking call at the outermost layer with
  `CompletableFuture.supplyAsync(() -> dial(...), Executors.newVirtualThreadPerTaskExecutor())`
  — no async plumbing is needed inside the transport/security/muxer layers themselves.
- `ConnectionBuilder.upgrade(Socket socket, boolean isInitiator)`: wraps
  `socket.getInputStream()`/`socket.getOutputStream()` as `SocketP2PInputStream`/
  `SocketP2POutputStream` (trivial adapters delegating straight to the underlying `InputStream`/
  `OutputStream`), constructs a `TcpConnection` around them, then calls, in order and as plain
  sequential blocking method calls (no futures/promises/callbacks at this layer):
  1. `upgrader.establishSecureChannel(connection)` — runs the Noise handshake (M6) synchronously,
     sets the secure session on success.
  2. `upgrader.establishMuxer(connection)` — runs the Yamux `/yamux/1.0.0` multistream
     negotiation (M7) synchronously, sets the muxer session on success and starts the muxer's
     background reader virtual thread (see M7).
  3. Invoke the configured `ConnectionHandler` with the fully upgraded `Connection`.
  Any exception at any step closes the socket and propagates/logs, exactly as the original plan
  intended — this method is a literal, single-threaded, testable method precisely *because*
  it's allowed to block.
- `TcpTransport.handles(addr)`: unchanged (multiaddr has `ip4`/`ip6` + `tcp` components).
- At this point `ConnectionUpgrader` can be stubbed/tested with a fake `SecureChannel`/
  `StreamMuxer` pair before Noise/Yamux exist (M6/M7), to validate the sequencing independently.

Tests: loopback TCP connect/listen with stub security+muxer bindings that just complete
immediately; verify `ConnectionHandler` fires exactly once per side with a fully "upgraded"
`Connection` (non-null secure/muxer session). Since both the server's accept-handler and the
client's dial run to completion on their own thread before returning/invoking the handler, the
test is a plain sequential JUnit method plus a `CountDownLatch` (or simply `Thread#join`/a
short poll) to wait for the *server-side* handler thread specifically, since it necessarily
runs on a different thread than the test method.

### M6 — Noise `XX` security transport

Files: `security/SecureChannel.java`, `security/noise/NoiseXXHandshake.java`
(the Noise `XX` state machine — reads/writes the **generated** `spipe.pb.Spipe.NoiseHandshakePayload`
protobuf message directly for the handshake payload, field 1 `libp2p_key` + field 2
`noise_static_key_signature`; the unused `libp2p_data`/`libp2p_data_signature` fields are simply
left unset), `security/noise/NoiseXXFramedInputStream.java`,
`security/noise/NoiseXXFramedOutputStream.java`, `security/noise/NoiseXXSecureChannel.java`.

Reference: `security/noise/{NoiseXXSecureChannel,NoiseXXCodec,NoiseSecureChannelSession}.kt`,
and cross-check against `security/noise/{NoiseHandshakeTest,NoiseSecureChannelTest,
NoiseXXCodecTest}.kt` for known-vector sanity checks (payload signing string
`"noise-libp2p-static-key:"`, framing sizes, AEAD error handling) — the cryptographic details
below are unchanged from the original plan; only the I/O plumbing around them changes.

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
- **I/O plumbing (the actual deviation from the original plan)**: `NoiseXXHandshake.run(
  P2PInputStream rawIn, P2POutputStream rawOut, ...)` is a plain blocking method that performs
  the 2-byte-big-endian-length-prefixed (max 65535, same framing upstream's `UShortLengthCodec`
  used) 3-message exchange directly against the connection's raw streams and returns the two
  derived `CipherState`s (one per direction) on success — there is no handshake *handler*
  sitting in a pipeline reacting to `channelRead` events; it's a synchronous request/response
  loop, which is exactly what the Noise `XX` pattern already is. Once the handshake completes,
  `NoiseXXFramedInputStream`/`NoiseXXFramedOutputStream` wrap the same raw streams and implement
  `P2PInputStream`/`P2POutputStream` by transparently decrypting/encrypting each
  length-prefixed frame inside `read`/`write` — from the muxer's (M7) point of view these are
  just another `P2PInputStream`/`P2POutputStream` pair, identical in shape to the raw TCP ones.
- `protocolDescriptor = ProtocolDescriptor("/noise")`.

Tests: two `NoiseXXHandshake` instances (initiator/responder), each driven from its own thread
(the handshake blocks on reads, so both sides need to run concurrently), piped through an
in-memory `P2PInputStream`/`P2POutputStream` pair (the same test-support byte-pipe introduced in
M3) complete a handshake and can then exchange AEAD-encrypted application data both ways via
`NoiseXXFramedInputStream`/`NoiseXXFramedOutputStream`; tamper tests (flipped bit) must fail
decryption; peer-id mismatch on dial must be rejected.

### M7 — Yamux stream multiplexer

Files: `mux/StreamMuxer.java`, `mux/MuxId.java`, `mux/MuxedConnection.java`,
`mux/MuxedStream.java`, `mux/yamux/{YamuxFlag,YamuxType,YamuxId,YamuxFrame,YamuxFrameIO,
YamuxStreamIdGenerator,YamuxConnection,YamuxStream,YamuxStreamMuxer}.java`.

Reference: `core/mux/StreamMuxer.kt`, `mux/MuxHandler.kt`, `mux/yamux/*.kt` (wire format and
flow-control *semantics* only — the Netty child-`Channel`/`EventLoop` plumbing described in
`etc/util/netty/mux/*.kt`/`etc/util/netty/AbstractChildChannel.kt` has no equivalent here);
cross-check frame encoding against `YamuxHandlerTest.kt`.

Implementation notes:

- Wire format (12-byte header, big-endian): `version:u8, type:u8, flags:u16, streamId:u32,
  length:u32` + optional `DATA` payload. `maxFrameDataLength = 1<<20`. Types:
  `DATA=0, WINDOW_UPDATE=1, PING=2, GO_AWAY=3`. Flags (single-flag only): `SYN=1, ACK=2, FIN=4,
  RST=8`. `YamuxFrameIO` implements `readFrame(P2PInputStream)`/`writeFrame(P2POutputStream,
  YamuxFrame)` as plain blocking calls (`readFully`-style loop for the 12-byte header, then the
  payload) — this wire format is unchanged from the original plan, only the encode/decode entry
  points change shape (methods on a plain I/O helper instead of a Netty
  `ByteToMessageDecoder`/`MessageToByteEncoder`).
- Stream IDs: odd if this side initiated the underlying _connection_, even otherwise
  (session id `0` reserved).
- **Threading (the actual deviation from the original plan)**: `YamuxConnection` owns the
  single underlying `P2PInputStream`/`P2POutputStream` of the secured connection (from M6), plus:
  - One dedicated **reader virtual thread**, started when the muxer is established, that loops
    `YamuxFrameIO.readFrame(...)` and dispatches: `DATA` payloads are appended to the target
    `YamuxStream`'s incoming byte queue (unblocking any virtual thread parked in that stream's
    `read()`); `WINDOW_UPDATE` increases the target stream's send-window counter and notifies any
    thread parked waiting to write; `PING`/`GO_AWAY` are handled inline; unknown/invalid frames
    close the connection. Inbound `SYN` (new stream opened by the remote side) constructs a new
    `YamuxStream` and hands it off to a **freshly spawned virtual thread** that runs
    multistream-select (M3) + the matching application `StreamHandler` — mirroring exactly what
    `TcpServer`'s accept loop does per connection, one layer up.
  - A single **writer lock** (`java.util.concurrent.locks.ReentrantLock`, or `synchronized`)
    guarding the shared underlying `P2POutputStream`, since multiple application virtual threads
    may call `YamuxStream#write` concurrently on different streams that all funnel through the
    one underlying connection stream. (Netty serialized this for free via the single-threaded
    per-channel event loop; blocking I/O needs an explicit lock instead — see `ARCHITECTURE.md`.)
  - Each `YamuxStream` implements `P2PInputStream`/`P2POutputStream`: `write()` acquires the
    writer lock, frames the payload as one or more `DATA` frames, and — if the stream's send
    window is currently exhausted — simply **blocks the calling virtual thread** on a condition
    variable until a `WINDOW_UPDATE` arrives (parking a virtual thread is cheap; there is no
    need for the upstream `maxBufferedConnectionWrites`-capped buffering/reset-on-overflow
    scheme this had to have to avoid blocking a shared Netty event-loop thread). `read()` blocks
    on the stream's own incoming-data queue, populated by the connection's reader thread.
  - `INITIAL_WINDOW_SIZE = 256 * 1024`; send `WINDOW_UPDATE` once the receive window drops below
    half — this flow-control policy is unchanged from the original plan.
- `YamuxConnection.openStream()` sends a `SYN` frame (via the writer lock) and returns a
  `YamuxStream` immediately; the `ACK` is awaited lazily on the first `read`/`write` call.
- `YamuxStreamMuxer.protocolDescriptor = ProtocolDescriptor("/yamux/1.0.0")`; establishing the
  muxer means: negotiate `/yamux/1.0.0` via multistream-select (M3) over the connection's
  already-Noise-encrypted `P2PInputStream`/`P2POutputStream` (M6), then construct the
  `YamuxConnection` around those same two streams and start its reader thread.

Tests: two `YamuxConnection`s over an in-memory pipe (or loopback TCP): open a stream from each
side, send/receive data larger than one window (this specifically exercises the
block-on-window-exhaustion path and confirms a `WINDOW_UPDATE` unblocks the parked writer
thread), verify `RST`/`FIN` close semantics.

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
- `HostImpl.start()`: for each configured listen address, bind and start a `TcpServer`
  (M5) — i.e. spawn its accept-loop virtual thread. `stop()`: close every `TcpServer` (unblocks
  and ends each accept-loop thread) and every open `Connection` (closing the underlying socket
  unblocks that connection's Yamux reader thread with an `IOException`, which it treats as a
  normal "connection closed" shutdown signal — no explicit cancellation/interruption protocol
  needed beyond `Socket#close()`).
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

Note: `Host.start()`/`connect()` return plain `CompletableFuture`s (backed by
`Executors.newVirtualThreadPerTaskExecutor()`, per the M5 note) purely so this test's API shape
matches the original plan — internally each just kicks off blocking work on a virtual thread.
There is no `EventLoopGroup` (or any other framework resource) to shut down: `Host.stop()`
closing every `TcpServer`/`Connection` is suffient to unwind every virtual thread this system
spawned (each blocking call simply gets an `IOException` from its now-closed socket and returns).

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
