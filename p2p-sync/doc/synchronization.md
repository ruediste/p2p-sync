# Node State Synchronization

This document describes how concurrent access to a node's mutable in-memory state is
serialized. It covers the lock, what it guards, which threads enter the node, the rules for
holding it, and the patterns used at blocking boundaries.

## Why a lock is needed

A node owns two kinds of mutable state that are **not thread-safe**:

- in-memory collections and materialized views (`users`, `syncUnits`, operation trees, the
  per-user `UserFileTree` and `ContactList`, Lamport clocks and counters), and
- storage structures in the node's `DataStorage` file (`BTree` roots, `UnitRootStore`,
  `BlobStorage`, and the short-GUID mappings in `GuidMapper`/`GuidTree`).

A `DataStorage` file is opened by exactly one `Node`, so storage operations do not have their
own lock. In addition, some logically read-only paths mutate these structures lazily: resolving
a GUID can allocate a short ID, and walking a B-tree may materialize nodes. Reads therefore
cannot be assumed to be side-effect free.

Without a common lock, a FUSE `getattr`/`readdir` running on one jnr-fuse thread can overlap a
file write or a sync applying operations on another thread. That can expose a half-updated B-tree
or GUID mapping, producing errors such as `IllegalStateException: Missing reverse GUID mapping`
and an `EIO` result for the filesystem caller.

## The node state lock

The lock is the **intrinsic monitor of the `Node` instance**:

```java
synchronized (node) {
    // access node-owned mutable state
}
```

There is no second node-wide lock. `Node` methods declared `synchronized` lock the same monitor
as blocks written as `synchronized (node)` in `NodeUserHandle` and `NodeFuseFS`.

### Guarded state

The lock guards all state reachable from a `Node` that is not explicitly documented as
independent:

| Owner | Guarded state |
| --- | --- |
| `Node` | `users`, `syncUnits`, `clock`, `syncUnitCounter`, `sharedFolderMapper`, `sharedFolderNextNodeId`, `remoteAddresses` |
| `Node.NodeUserData` | all operation trees and staging/retrieval maps, `snapshotState`, `fileSystem`, `guidMapper`, `contacts`, inbox/outbox and delivery state, counters, labels |
| `Node.SyncUnitData` | authorization state, content maps (`todoItems`, `channelMessages`), operation trees, `guidMapper`, clocks and counters |
| storage | every `DataStorage`/`BTree`/`UnitRootStore`/`BlobStorage`/`GuidMapper` structure reachable from the node |

### Independent state

The following is intentionally outside the node lock:

- `Node.Network.nodes` — a `ConcurrentHashMap` for the in-process test network.
- `remoteHost`, `remoteSyncProtocol`, and the libp2p host/network objects — these have their own
  internal synchronization; a node never mutates them under its state lock.
- immutable values returned from node methods (`List.copyOf`, records, byte-array snapshots).
- logging and `SimpleFuseFS`, which owns a separate `DataStorage` and uses its own monitor.

## Threads that enter a node

| Caller | Entry point | Locking strategy |
| --- | --- | --- |
| jnr-fuse worker threads | `NodeFuseFS` callbacks | hold `node` for the whole request (`NodeFuseFS.execute`) |
| Swing event dispatch thread | `DesktopApp.refresh()` and actions | call one short `Node`/`NodeUserHandle` method at a time; blocking work runs through `Background` |
| background sync worker | `Node.syncKnownPeers` | build a work snapshot under the lock; perform network exchanges unlocked; each apply reacquires the lock |
| libp2p stream thread | `Node.handleRemoteSyncRequest` | build the response under the lock; all socket I/O happens outside it |
| `SimpleFuseFS` | standalone storage inspection tool | its own monitor; unrelated to a running node |

## Rules

1. **Take the node lock for every access to guarded state.** This includes reads. If a method
   touches a guarded collection, tree, file view, or GUID mapping, it must run under the lock.
2. **Lock whole adapter operations.** `NodeFuseFS` holds the lock for one complete FUSE request.
   An operation such as `getattr` performs several dependent lookups; locking each lookup
   separately is not sufficient to give the kernel a consistent answer.
3. **Never perform blocking work while holding the lock.** In particular: no socket I/O, no
   waiting for futures, no file I/O for credential export, no `SwingUtilities.invokeAndWait`, and
   no arbitrary callbacks that may call back into the node. The only callbacks invoked under the
   lock are the jnr-fuse `FuseFillDir` filler used by `readdir`, which copies into a native reply
   buffer, and the low-level `NodeUserHandle.modify`/`forEachDirectory` consumers. They must stay
   short, must not block, and must not re-enter the node.
4. **Build under the lock, exchange outside it, apply under it.** Remote synchronization follows
   this pattern:
   - `NodeUserHandle.syncFrom`: build the request and apply the response inside
     `synchronized (node)`; the `exchangeRemote` call is outside.
   - `Node.joinSyncUnit` / `Node.syncSyncUnitFrom`: the same structure.
   - `Node.handleRemoteSyncRequest`: only the request/response computation is locked; the
     libp2p stream exchange is not.
5. **Do not acquire another node's lock while holding this node's lock.** In production a
   process hosts one `Node`, so this mostly means "do not call back into another node while
   holding the lock". Read a peer's state through its synchronized accessors (`hostsUser`,
   `hostsSyncUnit`) before taking this node's lock, or after releasing it. The in-process
   `Node.Network` has a few test-only paths that mutate a peer directly while this node's lock is
   held; they are not safe for concurrent multi-node tests and are called out in the limitations
   below.
6. **Persist as part of a mutation.** `Node.persistState()` acquires the node lock and snapshots
   the in-memory state, so the persisted image matches what is currently materialized. Call it
   as part of the operation that changed state; never mutate guarded state outside the lock and
   persist it later from a different context.
7. **Snapshot collections before iterating them outside the lock.** Prefer `userSnapshot()` or
   `List.copyOf(...)` under the lock. Returned live records/objects are still guarded; callers
   must not mutate them outside the lock, and must not retain mutable internal collections.
8. **Keep critical sections short on the Swing thread.** `DesktopApp.refresh()` calls node
   read methods on the event dispatch thread. Those calls can wait for a sync apply that already
   holds the lock, so UI work should not be added inside a node method under the lock.

## Implementation map

- `Node` — mutating methods and read methods that touch guarded state are `synchronized`.
  Internal helpers such as `nextClock`, `advanceUnitClock`, `mirrorStartLeaving`,
  `syncHomeActiveNodeSet`, and `applyParticipationOperations` are `synchronized` as well.
- `NodeUserHandle` — mutators already used `synchronized (node)`; reads (`exists`, `isDirectory`,
  `fileSize`, `readFile`, `listDirectory`, `forEachDirectory`) and the initial part of
  `sendMessage` now use the same lock. `sendMessage` releases it before delivering messages, so
  delivery can perform network work without blocking filesystem access.
- `NodeFuseFS.execute` — wraps every FUSE request in `synchronized (node)`. Unexpected runtime
  exceptions are still logged and returned as `EIO`; with the lock they indicate a real defect
  rather than a race.
- `Node.syncKnownPeers` — decides which user/unit/peer combinations need syncing under the lock,
  then runs each sync outside it.
- `SimpleFuseFS` — an independent tool over its own `DataStorage`; it serializes callbacks with
  `synchronized` and performs periodic commits under that lock. It is not a second lock in the
  node hierarchy.

## Adding a new operation

- If the operation touches node-owned state, make the public `Node`/`NodeUserHandle` method
  `synchronized` or wrap the state access in `synchronized (node)`.
- If the operation is a multi-step adapter call (filesystem, RPC, UI command), hold the lock for
  the whole logical operation and keep the callback short.
- If the operation needs network or blocking I/O, split it into a locked prepare phase, an
  unlocked I/O phase, and a locked apply phase.
- If it needs a peer node's state, use that node's synchronized accessor and do not hold both
  node locks at once.
- Add a regression test if the operation can run concurrently with FUSE or sync work.

## Known limitations

- The node lock is coarse. Large sync applies or persistence can briefly block unrelated reads,
  including the desktop refresh. This is deliberate: correctness first, with short critical
  sections kept as short as practical.
- The in-process `Node.Network` used by tests can host several `Node` instances in one JVM.
  Those nodes each have their own lock, and some test-only paths read another node's fields
  directly. Production runs one node per process. Do not add production concurrency that relies
  on cross-node locking without redesigning it around message passing or explicit lock ordering.
- `DataStorage` itself is not synchronized. It must always be reached through a single owner
  (`Node` in production, `SimpleFuseFS` in its standalone tool) that provides the lock.
