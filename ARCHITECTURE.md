# Architecture: Transport, Streams, and Threading

This document describes the transport/security/multiplexing architecture used by the
`com.github.ruediste.p2psync.libp2p` port (see `ImplementationPlan.md` for the milestone-by-
milestone build-out of this design). It exists because this project deliberately does **not**
follow upstream `jvm-libp2p`'s choice of Netty (non-blocking I/O, channel pipelines, an
event-loop group) — instead everything is built on plain **blocking I/O**, using one
**virtual thread** (JEP 444, Java 21) per connection and per multiplexed stream.

## Why not Netty

Netty exists to make a single (or a handful of) OS thread(s) serve a very large number of
concurrent connections without blocking, at the cost of a channel/pipeline/event-loop
programming model: handlers react to events (`channelRead`, `userEventTriggered`, ...) instead
of just calling `read()`/`write()` in a straight line, and any logic that spans multiple network
round-trips (a handshake, a negotiation) has to be written as an explicit state machine.

Java 21's virtual threads make that trade-off unnecessary for this project's scale (a node
talks to at most a few hundred peers, each peer connection carrying a modest number of
multiplexed streams): a virtual thread blocked on a socket read consumes no OS thread while
parked, so "one thread per connection, one thread per stream" scales to the numbers this system
actually needs, while letting every layer — multistream-select, the Noise handshake, the Yamux
muxer — be written as plain, sequential, blocking Java code. A 3-message Noise handshake is
*inherently* three blocking round-trips; writing it as a Netty handler reacting to
`channelRead` events is not simpler than `NoiseXXHandshake.run(in, out)`, and virtual threads
mean it doesn't cost anything either.

Consequently: **no Netty dependency anywhere in this project.** No `ByteBuf`/`Unpooled`, no
`Channel`/`ChannelPipeline`/`ChannelHandler`, no `EventLoopGroup`/`Bootstrap`/`ServerBootstrap`,
no `ChannelFuture`/`Promise`. Where the original, Netty-based plan had a
`ByteBuf`-based-buffer-utility need unrelated to networking (varint/multiaddr encoding), it was
replaced by a small project-owned `core/multiaddr/ByteBuf.java` implementing just the needed
subset (see `ImplementationPlan.md`'s M1 deviation note) — this is not a networking
abstraction, just a growable-byte-array helper, and does not participate in the design below.

## The core abstraction: `P2PInputStream` / `P2POutputStream`

Every layer of the stack — a raw TCP socket, a Noise-encrypted connection, an individual Yamux
stream — is exposed to the layer above it as the same minimal pair of classes:

```java
package com.github.ruediste.p2psync.libp2p.core;

public abstract class P2PInputStream implements Closeable {
    /** Same contract as {@link java.io.InputStream#read(byte[], int, int)}: blocks until at
     *  least one byte is available (or EOF/error), may return fewer bytes than requested. */
    public abstract int read(byte[] buf, int off, int len) throws IOException;

    /** Same contract as {@link java.io.InputStream#read()}: blocks for, and returns, a single
     *  byte (0-255), or {@code -1} on EOF. Defined in terms of {@link #read(byte[], int, int)}
     *  so subclasses only ever need to implement the array-based version; overriding this one
     *  too is only worth it if a layer has a cheaper single-byte path. */
    public int read() throws IOException {
        byte[] single = new byte[1];
        int n = read(single, 0, 1);
        return n < 0 ? -1 : single[0] & 0xFF;
    }

    @Override
    public abstract void close() throws IOException;
}

public abstract class P2POutputStream implements Closeable {
    /** Same contract as {@link java.io.OutputStream#write(byte[], int, int)}: blocks until all
     *  {@code len} bytes have been written (or an error occurs). */
    public abstract void write(byte[] buf, int off, int len) throws IOException;

    /** Same contract as {@link java.io.OutputStream#write(int)}: blocks until the single given
     *  byte (the low 8 bits of {@code b}) has been written. Defined in terms of
     *  {@link #write(byte[], int, int)}, same rationale as {@link P2PInputStream#read()}. */
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public abstract void close() throws IOException;
}
```

These intentionally mirror the standard `java.io.InputStream`/`OutputStream` method contracts
(so implementations can trivially delegate to a real `Socket`'s streams, and callers can use
the same idioms they already know) but expose only the minimal surface every layer actually
needs: bulk `read`/`write` (the primary, abstract methods every implementation must provide),
single-byte `read`/`write` (concrete convenience methods, defined once on the base classes so
no subclass has to re-implement them), and `close`. There is deliberately no `available()`,
`mark`/`reset`, `flush()`, or any of the rest of the standard `java.io` surface. (A tiny
`readFully(P2PInputStream, byte[])`-style helper, looping `read()` until the buffer is full or
EOF, lives alongside these two classes and is used by every frame-parsing layer below —
multistream-select, the Noise handshake/framing, Yamux frame headers.)

Concrete implementations, one per layer, each wrapping the layer beneath it:

| Layer | Implementation | Wraps |
| --- | --- | --- |
| Raw TCP | `transport.tcp.SocketP2PInputStream`/`SocketP2POutputStream` | `Socket#getInputStream()`/`#getOutputStream()` |
| Noise-encrypted connection | `security.noise.NoiseXXFramedInputStream`/`NoiseXXFramedOutputStream` | the raw TCP `P2PInputStream`/`P2POutputStream` (decrypts/encrypts each length-prefixed AEAD frame inside `read`/`write`) |
| One multiplexed Yamux stream | `mux.yamux.YamuxStream` (implements both) | the Noise-encrypted connection's single shared `P2PInputStream`/`P2POutputStream`, via the connection's frame reader thread / writer lock (see below) |

Because every layer presents the exact same two-class interface, code written against it once —
multistream-select (used for security negotiation, muxer negotiation, *and* per-stream
application-protocol negotiation) — is written exactly once and reused at all three of those
points, exactly as the original (Netty-based) plan intended with its `P2PChannel` abstraction.
The difference is purely mechanical: `P2PChannel` wrapped a Netty `Channel` and worked via
pipeline events; `P2PInputStream`/`P2POutputStream` are just blocking streams and work via plain
method calls.

## Thread model

There is no event loop anywhere in this system. Concurrency comes entirely from spawning a
virtual thread per unit of independent blocking work:

```
TcpServer accept-loop thread (1 per listen address)
 └─ spawns → per-connection worker thread (1 per accepted/dialed connection)
              runs: Noise handshake → Yamux muxer negotiation → app ConnectionHandler
              then becomes ↓
             Yamux connection reader thread (1 per connection, reads frames, dispatches)
              └─ spawns → per-stream worker thread (1 per Yamux stream, inbound SYN)
                           runs: multistream-select → app StreamHandler
```

- **`TcpServer`** (M5): one virtual thread runs `serverSocket.accept()` in a loop; each accepted
  `Socket` is handed to a brand-new virtual thread (`Thread.ofVirtual().start(...)`) that runs
  the entire connection-upgrade sequence — Noise handshake, Yamux negotiation, invoking the
  application's `ConnectionHandler` — as ordinary sequential, blocking Java method calls. Dialing
  (`TcpTransport.dial`) runs the identical sequence on the calling virtual thread.
- **Per-connection Yamux reader thread** (M7): once the muxer is established, one dedicated
  virtual thread owns reading frames off the connection's `P2PInputStream` in a loop and
  dispatching them: `DATA` → append to the target stream's incoming queue; `WINDOW_UPDATE` →
  bump that stream's send-window counter and wake any writer parked on it; inbound `SYN` → spawn
  yet another virtual thread to run per-stream multistream-select + the application
  `StreamHandler`. This is the one place a background thread runs for the lifetime of a
  connection (as opposed to for the duration of one blocking call) — everything else is workers
  that run to completion and exit.
- **Writes are synchronized, reads are not**: many virtual threads (one per open stream) can
  call `YamuxStream#write` concurrently, but they all funnel through the *one* underlying
  connection `P2POutputStream`. Netty gets this serialization for free (a channel's pipeline
  runs on a single event-loop thread); with blocking I/O it needs an explicit
  `java.util.concurrent.locks.ReentrantLock` (or `synchronized`) around that shared output
  stream. Reads don't need this: each stream's incoming-data queue is only ever written to by
  the single connection reader thread and only ever read from by that stream's own consumer.
- **Blocking-on-backpressure is fine, and preferred over buffering**: when a Yamux stream's send
  window is exhausted, `YamuxStream#write` simply parks the calling virtual thread on a
  condition variable until a `WINDOW_UPDATE` arrives, instead of buffering the write (capped at
  some limit, resetting the stream on overflow) the way the original Netty-based plan needed to
  in order to avoid blocking a shared event-loop thread. Parking a virtual thread is cheap, so
  "just block" is both simpler and has no arbitrary buffer-size limit to tune.
- **Shutdown is just closing sockets**: there is no `EventLoopGroup`/executor to shut down.
  Closing a `Socket` (or `ServerSocket`) causes any thread currently blocked in a `read`/`write`/
  `accept` call on it to wake up with an `IOException`/`SocketException`, which every loop in
  this system treats as its normal "stop" signal. `Host.stop()` therefore just needs to close
  every `TcpServer` and every open `Connection`; every virtual thread this system spawned
  unwinds on its own shortly after.
- **Async-looking public API, synchronous internals**: at the outermost boundary (`Host.start()`,
  `Network.connect()`), callers may still want something they can attach callbacks to or
  `.get()` on. That's provided with a plain `CompletableFuture`, produced via
  `CompletableFuture.supplyAsync(() -> blockingCall(), Executors.newVirtualThreadPerTaskExecutor())`.
  No internal layer (transport, security, muxer, multistream-select) ever deals with futures,
  promises, or callbacks — they are all plain blocking method calls; only the outermost
  `Host`/`Network` facade wraps a call in a future for API ergonomics.

## Layering summary

```
Application (StreamHandler / ConnectionHandler)
        ▲
        │ P2PInputStream / P2POutputStream (one pair per app stream)
        │
mux.yamux.YamuxStream               ── one per multiplexed stream, backed by the
        ▲                              connection's shared frame reader thread + writer lock
        │ P2PInputStream / P2POutputStream (one pair per connection)
        │
security.noise.NoiseXXFramed{In,Out}putStream  ── AEAD encrypt/decrypt per frame
        ▲
        │ P2PInputStream / P2POutputStream (one pair per connection)
        │
transport.tcp.SocketP2P{In,Out}putStream       ── thin adapter over java.net.Socket
        ▲
        │ java.net.Socket (accepted by TcpServer, or opened by TcpTransport.dial)
```

Multistream-select (`multistream.Negotiator`/`ProtocolSelect`) is not a distinct layer in this
diagram — it's a stateless blocking function, `negotiate(P2PInputStream, P2POutputStream, ...)`,
invoked at three points: once for security (raw TCP streams → agree on `/noise`), once for
muxing (Noise-framed streams → agree on `/yamux/1.0.0`), and once per application stream (a
Yamux stream's streams → agree on the app protocol id).

## Where to look for the milestone-level detail

`ImplementationPlan.md` walks through building this stack incrementally (M3 introduces
`P2PInputStream`/`P2POutputStream` and multistream-select; M5 introduces `TcpServer`/
`TcpTransport` and the virtual-thread-per-connection model; M6 reworks the Noise handshake/
framing onto blocking streams; M7 reworks Yamux onto the reader-thread + writer-lock model
described above), including file-by-file breakdowns and test strategy per milestone.
