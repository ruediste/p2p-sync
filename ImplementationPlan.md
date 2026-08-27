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
| M3 — Multistream-select                 | ✅ Done        | See log below. |
| M4 — TCP transport / upgrade pipeline   | ✅ Done        | See log below. |
| M5 — Noise XX security transport        | ✅ Done        | See log below. |
| M6 — Yamux stream multiplexer           | ✅ Done        | See log below. |
| M7 — Network, ConnectionUpgrader, Host  | ⬜ Not started |                |
| M8 — End-to-end integration test / demo | ⬜ Not started |                |

#

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

## Milestones

Each milestone should be its own commit (or small set of commits) with passing tests before
moving to the next. Milestones 1–3 have no network I/O and are pure unit-testable logic;
milestone 4 onward requires loopback TCP integration tests.

Completed Milestone descriptions have been removed on purpose.

### M7 — Network, ConnectionUpgrader wiring, Host

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
  multiaddr for post-Noise identity verification (M5).
- `HostImpl.start()`: for each configured listen address, bind and start a `TcpServer`
  (M4) — i.e. spawn its accept-loop virtual thread. `stop()`: close every `TcpServer` (unblocks
  and ends each accept-loop thread) and every open `Connection` (closing the underlying socket
  unblocks that connection's Yamux reader thread with an `UncheckedIOException` (wrapping the
  socket's `IOException`), which it treats as a normal "connection closed" shutdown signal — no
  explicit cancellation/interruption protocol needed beyond `Socket#close()`).
- `Host.newStream(...)` is needed for the M8 smoke test: locate the app `ProtocolBinding` and
  call `connection.muxerSession().createStream(...)`.

Tests: unit tests for `NetworkImpl` connection reuse/dedupe logic with fake transports.

### M8 — End-to-end integration test / demo

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
`Executors.newVirtualThreadPerTaskExecutor()`, per the M4 note) purely so this test's API shape
matches the original plan — internally each just kicks off blocking work on a virtual thread.
There is no `EventLoopGroup` (or any other framework resource) to shut down: `Host.stop()`
closing every `TcpServer`/`Connection` is suffient to unwind every virtual thread this system
spawned (each blocking call simply gets an `UncheckedIOException` from its now-closed socket
and returns).

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
