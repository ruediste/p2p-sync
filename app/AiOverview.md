# Comprehensive Project Summary: p2p-sync

This file gives a summary of the project, intended for AI Coding tools.

## Overall Project Structure

```
/home/ruedi/git/p2p-sync/
  .git/
  .gitignore                          # Ignores /upstream/
  AGENT.md                            # Instructions for AI agents working on the code
  LICENSE.txt                         # License file
  app/                                # Maven project root
    ARCHITECTURE.md                   # Detailed architecture doc (blocking I/O, virtual threads, P2PStream)
    ImplementationPlan.md             # Milestone plan (M0-M9)
    pom.xml                           # Maven build (Java 21, protobuf 3.25.5, JUnit 4)
    .project                          # Eclipse project config
    .classpath                        # Eclipse classpath
    .gitignore
    .vscode/settings.json
    .settings/                        # Eclipse settings (Maven, JDT, APT)
    src/
      main/
        proto/
          crypto.proto                # Protobuf: KeyType, PublicKey, PrivateKey messages
          spipe.proto                 # Protobuf: NoiseHandshakePayload, Propose, Exchange
        resources/
          logback.xml                 # Minimal logback config (INFO console)
        java/com/github/ruediste/p2psync/
          App.java                    # Stub "Hello World"
          libp2p/                     # Main libp2p port package
            core/                     # Core abstractions
            discovery/                # mDNS LAN peer discovery (M9)
            crypto/                   # Cryptographic key types
            host/                     # Host implementation
            multistream/              # Multistream-select protocol
            mux/                      # Multiplexer interfaces
            mux/yamux/               # Yamux muxer implementation
            network/                  # Network implementation
            security/                 # Security interfaces
            security/noise/          # Noise XX handshake + framing
            transport/                # Transport interfaces
            transport/tcp/           # TCP transport implementation
      test/
        java/.../
          AppTest.java
          libp2p/
            ProtobufToolchainTest.java
            test/BytePipe.java        # In-memory byte pipe for test use
            test/BytePipeTest.java
            discovery/MDnsDiscoveryTest.java  # unit tests; loopback integration tests moved to MDnsDiscoveryIT
            discovery/MDnsDiscoveryIT.java    # mDNS loopback integration tests, run via `mvn test -Pit`
            host/MDnsWireUpIT.java            # mDNS wire-up integration test, run via `mvn test -Pit`
            core/PeerIdTest.java
            core/multiaddr/MultiaddrTest.java
            core/multiaddr/MultihashTest.java
            core/multiaddr/VarintTest.java
            crypto/MarshalingTest.java
            crypto/keys/Ed25519KeysTest.java
            mux/yamux/YamuxTest.java
            security/noise/NoiseXXTest.java
            transport/TransportTest.java
    target/                           # Build output, compiled classes, surefire reports
  upstream/
    .gitignore
    jvm-libp2p/                       # Reference implementation (Kotlin/Gradle, Netty-based)
    nabu/                             # Reference project (Java/Maven)
    p2p-sync.wiki/                    # Wiki docs
      Home.md
      Technical-Overview.md
      Protocols-Tools.md
      data-model.lofi.png
```

## What the Project Does

**p2p-sync** is a decentralized peer-to-peer file storage and synchronization system. The project's ultimate goal is to create a "private cloud" where your own devices (laptop, phone, home server) replicate your data encrypted between each other, without a central server.

The project is inspired by IPFS/libp2p but deliberately does **not** use any existing library. Instead, it ports/rewrites the minimal subset of `jvm-libp2p` (from Kotlin to Java) needed for peer-to-peer connectivity, then extends it with storage/sync functionality from the `nabu` reference project.

**Current scope** (as defined by the Implementation Plan): Building the libp2p transport layer stack -- key generation, peer identity, TCP transport, Noise XX encrypted handshake, Yamux stream multiplexing, and the Host/Network abstractions.

**Longer term** (per the wiki): A full encrypted storage system with sharding, erasure coding, vector-clock-based CRDT merging, garbage collection, file sharing, messaging, and NAT traversal.

## Current State of the Code

**Architecture:**

- **Blocking I/O on virtual threads** (Java 21): No Netty dependency. Every layer is written as plain synchronous blocking code on `P2PInputStream`/`P2POutputStream`, with one virtual thread per connection and per multiplexed stream.
- **P2PInputStream/P2POutputStream** are the central abstraction -- every layer (raw TCP socket, Noise-encrypted connection, individual Yamux stream) implements this pair.
- **Upgrade pipeline**: Raw TCP -> multistream-select -> Noise XX handshake -> encrypted stream -> multistream-select -> Yamux session + muxer -> app-level `Connection`.
- **Thread model**: No event loop. One accept-loop virtual thread per `TcpServer`, one per-connection reader thread for Yamux, one per-stream worker thread for inbound SYNs. Writes are serialized through a `ReentrantLock`. Shutdown is just closing sockets.
- **No external crypto library**: Ed25519 via JDK `KeyPairGenerator("Ed25519")`, X25519 via JDK `KeyAgreement("X25519")`, ChaCha20-Poly1305 via JDK `Cipher("ChaCha20-Poly1305")`, SHA-256/HMAC via JDK `MessageDigest`/`Mac`.

**Key packages and their classes:**

| Package          | Key Classes                                                                                                                                                                                                   | Purpose                                                                                   |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `core`           | `P2PInputStream`, `P2POutputStream`, `P2PStream`, `Stream`, `Connection`, `RawConnection`, `PeerId`, `PeerInfo`, `Base58`, `Host`, `Network`, `AddressBook`, `StreamHandler`, `ConnectionEstablishedListener`, `Discoverer`, `PeerListener` | Core abstractions for streams, connections, identities, discovery, and the Host/Network entry points |
| `discovery`      | `MDnsDiscovery`                                                                                                                                                                                               | mDNS LAN peer discovery via `javax.jmdns`: one JmDNS per interface the TCP servers listen on, polling for new/changed interfaces, chat-guided interface filtering (docker/VPN/loopback exclusion) for wildcard listens     |
| `core/multiaddr` | `Multiaddr`, `MultiaddrComponent`, `Protocol`, `Varint`, `Multihash`, `ByteBuf`                                                                                                                               | Multiaddress parsing/serialization, multihash, varint encoding                            |
| `crypto`         | `PrivKey`, `PubKey`, `KeyType`, `Marshaling`                                                                                                                                                                  | Key abstractions and protobuf marshaling                                                  |
| `crypto/keys`    | `Ed25519PublicKey`, `Ed25519PrivateKey`                                                                                                                                                                       | Ed25519 implementation via JDK crypto                                                     |
| `transport`      | `ConnectionBuilder`, `DefaultConnectionBuilder`, `DiallingTransport`, `ListeningTransportBinding`                                                                                                           | Transport abstraction interfaces                                                          |
| `transport/tcp`  | `TcpDiallingTransport`, `TcpListeningTransportBinding`, `TcpServer`, `SocketP2PInputStream`, `SocketP2POutputStream`, `SocketUtils`                                                                         | TCP transport implementation                                                              |
| `security`       | `SecureSession`, `InvalidRemotePubKeyException`, `CantDecryptInboundException`                                                                                                                                | Security session and exceptions                                                           |
| `security/noise` | `NoiseXXHandshake` (716 lines, ~half the package), `NoiseXXProtocolBinding`, `NoiseXXFramedInputStream`, `NoiseXXFramedOutputStream`                                                                          | Noise XX handshake + AEAD framing                                                         |
| `multistream`    | `Multistream`, `MultistreamFraming`, `ProtocolBinding` (`ProtocolBinding<TInitiator,TResponder>` with split `initInitiator`/`initResponder`), `ProtocolDescriptor`, `ProtocolMatcher`, `ProtocolSelect`                                                                                             | Multistream-select negotiation                                                            |
| `mux`            | `MuxerSession`                                                                                                                                                                                                | Multiplexer session interface                                                             |
| `mux/yamux`      | `YamuxSession`, `YamuxStream`, `YamuxFrame`, `YamuxFrameIO`, `YamuxProtocolBinding`, `YamuxFlag`, `YamuxType`, `YamuxStreamIdGenerator`                                                                       | Yamux protocol multiplexer implementation                                                 |
| `host`           | `HostImpl`, `HostBuilder`, `MemoryAddressBook`                                                                                                                                                                | Host implementation and builder                                                           |
| `network`        | `NetworkImpl`                                                                                                                                                                                                 | Network implementation                                                                    |
| `test`           | `BytePipe`                                                                                                                                                                                                    | In-memory pipe for test protocol exercises                                                |

## Tech Stack

| Technology       | Version/Detail                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------- |
| Language         | Java 21 (uses virtual threads, records, pattern matching `instanceof`)                            |
| Build            | Maven 3.x                                                                                         |
| Test framework   | JUnit 4.11                                                                                        |
| Protobuf         | 3.25.5 (`protobuf-maven-plugin` 0.6.1, auto-generates Java from `.proto` files)                   |
| Crypto           | JDK built-in only: `Ed25519`, `X25519`, `ChaCha20-Poly1305`, `SHA-256`, `HMAC-SHA256`             |
| No external deps | No Netty, no BouncyCastle, no noise-java -- everything is custom                  |
| Logging          | `slf4j-api 2.0.17` (compile) + `logback-classic 1.5.18` (runtime) — added for mDNS (M9); JmDNS logs via slf4j |
| mDNS             | `org.jmdns:jmdns:3.6.3` (published library, not the vendored fork) — LAN peer discovery (M9) |
| IDE              | Eclipse (M2E + JDT), VS Code                                                                      |
| Git history      | Multiple checkpoints from AI agent sessions (`cline`); traces back to an earlier Kotlin prototype |

## What's Next

Identify protocol, relay/NAT traversal, and any actual "sync" application protocol. mDNS peer discovery (M9) is implemented (multi-interface aware, per `app/mDNSChat.txt` guidance); Kademlia DHT is still open, and automatic dialing on mDNS discovery is a documented follow-up (currently discovered peers are only added to the address book).
