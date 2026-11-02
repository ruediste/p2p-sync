# Architecture: Transport, Streams, and Threading

This document describes the transport/security/multiplexing architecture used by the
`com.github.ruediste.p2psync.libp2p` port. It exists because this project deliberately does **not**
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
_inherently_ three blocking round-trips; writing it as a Netty handler reacting to
`channelRead` events is not simpler than `NoiseXXHandshake.run(in, out)`, and virtual threads
mean it doesn't cost anything either.

Consequently: **no Netty dependency anywhere in this project.** No `ByteBuf`/`Unpooled`, no
`Channel`/`ChannelPipeline`/`ChannelHandler`, no `EventLoopGroup`/`Bootstrap`/`ServerBootstrap`,
no `ChannelFuture`/`Promise`. Where the original, Netty-based plan had a
`ByteBuf`-based-buffer-utility need unrelated to networking (varint/multiaddr encoding), it was
replaced by a small project-owned `core/multiaddr/ByteBuf.java` implementing just the needed
subset — this is not a networking abstraction, just a growable-byte-array helper, and does not
participate in the design below.

## The core abstraction: `P2PInputStream` / `P2POutputStream` / `P2PStream`

Every layer of the stack — a raw TCP socket, a Noise-encrypted connection, an individual Yamux
stream — is exposed to the layer above it as the same minimal pair of classes:

```java
package com.github.ruediste.p2psync.libp2p.core;

public abstract class P2PInputStream implements Closeable {
    public int read(byte[] buf, int off, int len);
    public int read() ;
    @Override
    public abstract void close();
}

public abstract class P2POutputStream implements Closeable {
    public void write(byte[] buf, int off, int len);
    public void write(int b);
    @Override
    public abstract void close();
}
```

These intentionally mirror the standard `java.io.InputStream`/`OutputStream` method contracts
(so implementations can trivially delegate to a real `Socket`'s streams, and callers can use
the same idioms they already know) but expose only the minimal surface every layer actually
needs: bulk `read`/`write` (the primary, abstract methods every implementation must provide),
single-byte `read`/`write` (concrete convenience methods, defined once on the base classes so
no subclass has to re-implement them), and `close`. There is deliberately no `available()`,
`mark`/`reset`, or any of the rest of the standard `java.io` surface. `P2POutputStream`
additionally exposes a no-op `flush()` that buffered implementations override; the convenience
helpers `write(byte[])` and `readFully(byte[])` live on the base classes. `readFully` loops bulk
`read(byte[], int, int)` calls until the buffer is full or EOF, and is used by every
frame-parsing layer below — multistream-select, the Noise handshake/framing, Yamux frame
headers.

**No checked exceptions.** Unlike `java.io.InputStream`/`OutputStream`, none of these methods
declare `throws IOException` — every implementation instead catches any underlying
`IOException` and rethrows it wrapped in the JDK's `java.io.UncheckedIOException` (a
`RuntimeException`). Every
caller in this codebase — multistream-select, the Noise handshake, Yamux frame I/O — is a
straight-line sequence of blocking `read`/`write` calls with no `try/catch`-for-plumbing noise
at every single call site; code that does want to react to I/O failure catches
`UncheckedIOException` (typically once, at the outermost per-connection/per-stream worker
virtual thread) and inspects `getCause()`. Protocol-level violations (a malformed frame, a
rejected negotiation) are _not_ I/O failures and are raised as plain `RuntimeException` instead.

## Thread model

There is no event loop anywhere in this system. Concurrency comes entirely from spawning a
virtual thread per unit of independent blocking work:

```
TcpServer accept-loop thread (1 per listen address)
 └─ spawns → per-connection worker thread (1 per accepted/dialed connection)
              runs: Noise handshake → Yamux muxer negotiation → app ConnectionHandler
              then spawns ↓
             Yamux connection reader thread (1 per connection, reads frames, dispatches)
              └─ spawns → per-stream worker thread (1 per Yamux stream, inbound SYN)
                           runs: multistream-select → app StreamHandler
```

- **`TcpServer`**: one virtual thread runs `serverSocket.accept()` in a loop; each accepted
  `Socket` is handed to a brand-new virtual thread (`Thread.ofVirtual().start(...)`) that runs
  the entire connection-upgrade sequence — Noise handshake, Yamux negotiation, invoking the
  application's `ConnectionHandler` — as ordinary sequential, blocking Java method calls. Dialing
  (`TcpDiallingTransport.dial`) runs the identical sequence on the calling virtual thread.
- **Per-connection Yamux reader thread**: once the muxer is established, one dedicated virtual
  thread owns reading frames off the connection's `P2PInputStream` in a loop and dispatching
  them: `DATA` → append to the target stream's incoming queue; `WINDOW_UPDATE` → bump that
  stream's send-window counter and wake any writer parked on it; inbound `SYN` → spawn yet
  another virtual thread to run per-stream multistream-select + the application `StreamHandler`.
  The `YamuxSession` constructor starts this thread; the per-connection worker does not become
  the reader. This is the one place a background thread runs for the lifetime of a connection
  (as opposed to for the duration of one blocking call) — everything else is workers that run
  to completion and exit.
- **Writes are serialized through the shared output stream**: many virtual threads (one per open
  stream) can call `YamuxStream#write` concurrently, but they all funnel through the _one_
  underlying connection `P2POutputStream`. Netty gets this serialization for free (a channel's
  pipeline runs on a single event-loop thread); with blocking I/O it needs an explicit
  `java.util.concurrent.locks.ReentrantLock` (or `synchronized`) around that shared output
  stream. The Noise framed input and output streams are likewise `synchronized` per frame, and
  each Yamux stream serializes its incoming-queue reads and writes with `incomingLock`; the
  connection reader remains the only producer for a stream's queue.
- **Blocking-on-backpressure is fine, and preferred over buffering**: when a Yamux stream's send
  window is exhausted, `YamuxStream#write` simply parks the calling virtual thread on a
  condition variable until a `WINDOW_UPDATE` arrives, instead of buffering the write (capped at
  some limit, resetting the stream on overflow) the way the original Netty-based plan needed to
  in order to avoid blocking a shared event-loop thread. Parking a virtual thread is cheap, so
  "just block" is both simpler and has no arbitrary buffer-size limit to tune.
- **Shutdown is closing sockets and sessions**: there is no `EventLoopGroup`. Closing a
  `ServerSocket` wakes the blocked `accept` loop, and closing a `Socket` wakes a thread blocked
  in a read on it (surfacing as `UncheckedIOException`). `Host.stop()` closes every `TcpServer`
  and every open `Connection`; `Connection.close()` closes the muxer session, which terminates
  its streams (signalling readers and writers parked on Yamux condition variables) before
  closing the raw stream and socket. The network's virtual-thread-per-task executor is then shut
  down. Application worker threads blocked somewhere other than those sockets or stream queues
  are not interrupted, so they must observe the closed stream or use their own timeouts.
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
transport.tcp.SocketUtils                      ── thin adapters over java.net.Socket
        ▲
        │ java.net.Socket (accepted by TcpServer, or opened by TcpDiallingTransport.dial)
```

Multistream-select (`multistream.Multistream`) is not a distinct
layer in this diagram — it's a stateless blocking call,
`Multistream#negotiateInitiator(P2PStream)` / `#negotiateResponder(P2PStream)`, invoked at three
points: once for security (a `P2PStream` wrapping the raw TCP streams → agree
on `/noise`), once for muxing (a `P2PStream` wrapping the Noise-framed streams → agree on
`/yamux/1.0.0`), and once per application stream (a `P2PStream` wrapping a Yamux stream's
streams → agree on the app protocol id).
