# Persistence Overview

This document describes how the current implementation persists data: which files
are written, which formats are used, how a write becomes durable, and which parts
of `doc/storage-sync.md` are only designed (not yet implemented).

The file contains various TODOs, which are to be implemented.

## 1. Persisted artifacts

| Artifact | Produced by | Content |
| --- | --- | --- |
| `node.db` (any path given to `DesktopMain`) | `Node` | Complete node state: peer key pair, users, signed operation logs, SyncUnit registry and histories, active node sets, inbox, file contents. A single `DataStorage` file. |
| `files.db` (any path given to `SimpleFuseMain`) | `SimpleFuseFS` | One `FileTree` (btrfs-style inode tree) in a `DataStorage` file. Standalone prototype, unrelated to `Node`. |
| exported user credential file | `Node.exportUserKey` | protobuf `UserCredential` (`ciphertext`, `salt`, `iv`), written with POSIX permissions `600`. Passphrase-derived AES-GCM; the passphrase may be empty. |
| attachment export (UI) | `UnitsPanel` / mail view | plain user-chosen files, not node state |

Everything else is in memory only: the libp2p address book (`MemoryAddressBook`),
mounted directories, FUSE path caches, and UI state. There is no configuration
file, no `Preferences`, and no per-user directory in the data path — the node is
one file, and one process must own it.

## 2. Layer stack

```
UI / NodeFuseFS / SimpleFuseFS
        |  operations (signed protobuf events) or FileTree calls
        v
Node state: protobuf `NodeState` blob  -->  BlockStorage (content-addressed, SHA-256)
        |                                        |
        +---------- both rooted in DataStorage ---+
                            v
DataStorage: single-file copy-on-write block store
   superblock -> root block -> { free-space trees, ref-count tree, root ref handles }
                            v
              typed blocks / slots / B+tree nodes (raw binary)
```

Two independent applications sit on `DataStorage`:

- `Node` (the desktop application) stores **one protobuf document** that is
  re-serialized on every persist, plus content-addressed copies of the same
  information through `BlockStorage`.
- `SimpleFuseFS` stores a **B+tree of binary key/value entries** (`FileTree`) and
  never uses protobuf.

## 3. DataStorage file format

`DataStorage` is a btrfs-inspired, copy-on-write, single-writer store. Classes:
`DataStorage`, `RootBlock`, `SlotBlock`, `BlockType`, `BTree`, `RefCountUpdates`,
`RefMode` (package `com.github.ruediste.p2psync.storage`).

### 3.1 Superblock (offset 0, 4096 bytes)

| Field | Type | Notes |
| --- | --- | --- |
| magic | 16 bytes | `d6 1b 52 d5 6d c6 7e 4f fd 40 c6 d8 f5 ee 03 db` |
| version | uint64 | `FORMAT_VERSION = 1`; a mismatch refuses to open |
| rootBlock | uint64 | file offset of the committed root block |
| dataBlockSize | int32 | default 4096 |
| metaBlockSize | int32 | default 16384 |
| systemBlockSize | int32 | default 4096 |

Block sizes live in the superblock (they are needed to read the root block),
unlike the design sketch in `doc/storage-sync.md` which puts them in the info
block. All structs are encoded with `ByteBuffer` default order, i.e.
**big-endian**, and there is **no padding/alignment inside blocks**.

### 3.2 Areas and file layout

Three areas with their own block size and free-space tree:

| Area | Block size | Initial size | Holds |
| --- | --- | --- | --- |
| `SYSTEM` | 4 KiB | 32 KiB | root block, info block |
| `META` | 16 KiB | 2 MiB | all trees (free-space trees, ref-count tree, B+tree nodes, block index, deferred lists) |
| `DATA` | 4 KiB | 8 MiB | file content blocks, chunk/manifest blocks, node-state reference |

`format(...)` lays the areas out contiguously after the superblock, sets the file
length to the end of `DATA`, seeds a small META bootstrap pool (up to 256 blocks)
and stores the rest of the initial space as free extents in the free-space trees.
When an area runs out of blocks, `extend(area)` appends at least 1 MiB (and at
least 256 blocks) to the end of the file and records it as a free extent. Because
areas are appended in the order they grow, a file is not area-partitioned over
its whole life — only the free-space trees know which blocks belong to which
area.

### 3.3 Root block

There is a new root block for every commit. It is a `SlotBlock` (see 3.5) with
the following slots, in serialization order:

```
dataFreeSpaceTree      uint64  (EXCLUSIVE, uncounted root pointer)
metaFreeSpaceTree      uint64
systemFreeSpaceTree    uint64
refCountTree           uint64
infoBlock              uint64  (counted ref, Area.SYSTEM)
fileLength             uint64  committed file length
systemDeferredFreeBlockList    uint64
metaDeferredFreeBlockList      uint64
dataDeferredFreeBlockList      uint64
pendingMetaFreeTreeRemovalList uint64
refCountUpdateList             uint64
refHandles             uint16 count, then uint16 handle ids, then uint64 offsets
longHandleIds          uint16 count + uint16 ids
longHandles            uint16 count + uint64 values
```

`refHandles`/`longHandles` are how applications persist their own roots: the
application registers `getRootRefHandle(id, area, blockType)`, writes the block
through the handle, and the handle's offset is stored in the root block. The ids
in use today:

| Handle id | Owner | Area | Type |
| --- | --- | --- | --- |
| 1 | `BlockStorage` index B+tree | META | `BTree.NodeBlock` (32-byte key) |
| 2 | `Node` node-state reference | DATA | 32-byte `ByteArrayBlock` |
| 1 | `SimpleFuseFS` (`files.db`) | META | `FileTree.BLOCK_TYPE` |

TODO: use separate handle ids, even across applications.

The design-only slots of `doc/storage-sync.md` (`systemKvTree`, `distributedClock`,
`nextGuidCounter`, `guidMappingTree`) are **not** implemented; the node keeps its
clock and GUID counter inside the protobuf state instead.

### 3.4 Info block

Currently only one field: `inlineDataThreshold` (int32, 16384). It is separated
from the root block because it changes rarely, and it is what `FileTree` uses to
decide between inline and extent storage for file data.

### 3.5 Typed blocks, slots and blocking

- `BlockType<T>` (extends `BlockSerializer<T>`) decouples the byte codec from the
  memory object and adds `getReferencedBlocks(value, storage)`, which enumerates
  the outgoing block references of a block. These references are what the
  reference-count bookkeeping and `StorageCheck` work with.
- `BlockInstance<TSelf>` marks an in-memory value as a block (it returns its
  `BlockType`).
- `SlotBlock<TSelf>` builds the codec from a list of typed slots
  (`RefSlot`, `RefsSlot`, `IntSlot`, `LongSlot`, `LongsSlot`, `UInt16Slot`,
  `UInt16sSlot`, `BytesSlot`), so simple structs do not need hand-written
  serialization. `SlotBlockType` serializes slots back to back with no header and
  no length prefix; the block size comes from the area, so a block's payload must
  fit into its area's block size.
- Blocks referenced through `BlockType.BYTES`, `DataStorage.BYTE_ARRAY_BLOCK_TYPE`
  and `LONG_BLOCK_TYPE` are raw payloads without a header.

### 3.6 B+tree nodes

`BTree<K, V>` is a copy-on-write B+tree with variable-sized values.

```
node := int32 itemCount          (4 bytes)
        uint8 level              (0 = leaf)
        items...
internal item := key[keySize] , uint64 childOffset
leaf item     := key[keySize] , int32 valueOffset , int32 valueSize
                 ... value bytes stored after the item array
```

- Keys have a fixed serialized size per tree and are ordered by unsigned
  lexicographic comparison of their serialized bytes; internal nodes store the
  smallest key of the subtree they point to.
- `BTree.BYTE_ARRAY_ENTRY_TYPE` stores the value verbatim; `BTree.entryType(t)`
  adapts a value-only type (deserialized entries then have a `null` key, which is
  how `FileTree` and `BlockStorage` work).
- Tree blocks declare their child references through `BTree.blockType(...)`, so
  refcounts and `StorageCheck` see internal pointers and entry references.

### 3.7 Reference counting and ownership

- `refCountTree`: B+tree `key = block offset (int64)`, `value = count (int64)`,
  stored in META.
- Within a transaction, counts are kept as **absolute** pending values in
  `RefCountUpdates`, not as deltas. A new block starts at 0, the first update
  reads the persisted count, updates that return to the persisted value are
  dropped, and a count that reaches 0 inside the transaction frees the block
  immediately (recursively releasing its references, reading the block from disk
  to enumerate them if necessary).
- `RefMode` selects who owns the edges along a modified path: `EXCLUSIVE`
  (structural ownership without refcount entries, used by the allocator trees),
  `TRANSFER` (move one incoming counted edge; shared children are copied) and
  `SHARE` (keep the source, publish a copy). `childRefMode()` in `DataStorage`
  implements the policy.
- Blocks freed in the current transaction are **not** returned to a free-space
  tree immediately. They go on per-area *deferred free* linked lists, because the
  previous superblock can still reach them until the commit point.

### 3.8 Deferred bookkeeping blocks (all in META)

| Structure | Purpose |
| --- | --- |
| deferred free block lists (`system`/`meta`/`data`) | linked list of blocks that became free in the last transaction; head offsets in the root block; applied at the start of the next transaction |
| pending META free-tree removal list | blocks allocated in the last transaction that still have to be removed from the META free-space tree; kept pending so allocation never hands them out before the removal is done |
| deferred ref-count update list | absolute refcount updates for blocks that still had to be written when the commit point was reached; each record stores offset, count, area and a `PersistableBlockType` code (1 = META free tree, 2 = ref-count tree) so the children of such a block can be released later |

The point of these lists is that the tail of a commit (persisting them, writing
the final root block) touches only the META free-space tree, which keeps the
commit from recursively needing new free-space updates in all areas.

### 3.9 Commit sequence (`DataStorage.commit()`)

1. `beforeCommit` event (registered handles may write their root block).
2. `flushRootBlockHandles()` — copy handle ids/values into the root block.
3. Allocate and write a **new** root block in SYSTEM, increment its refcount,
   decrement the previous root block's refcount.
4. Apply pending refcount updates and drain pending META free-tree removals.
5. Persist the pending-META-removal list and the deferred free-block heads,
   writing the root block repeatedly until all allocations have settled
   (`DeferredPersistor` loop, so commit cannot run out of blocks).
6. Write the final root block, then flush **all modified blocks** to disk, then
   write the 4096-byte superblock with the new root offset. The superblock write
   is the commit point.
7. Reset the transaction: clear `modifiedBlocks`, start empty free-block pools,
   switch to new deferred free lists, and fold the previous transaction's
   deferred frees plus the list blocks into the free-space trees.

Block writes are buffered in `modifiedBlocks` (data + reference list) and only
hit the file in step 6; `readBlock` serves transaction-local data from that
buffer.

### 3.10 Open, validation and crash behavior

`open(path)` formats the file if it is missing or empty, otherwise it reads the
superblock, root block, info block, free-space trees, ref-count tree, and then
replays the pending-removal list, the deferred refcount list and the deferred
free lists. It then truncates the file to `rootBlock.fileLength`, which discards
bytes written by a transaction that never committed. `openReadOnly` loads only
the committed state for inspection.

Validation performed while opening: magic, format version, block sizes, root
block offset, committed file length, list cycles, duplicate pending blocks and
refcount records. Corrupt input fails the open instead of being silently reused.

Durability caveat: the file is accessed through `RandomAccessFile` and there is
**no `FileDescriptor.sync()`/`fsync` anywhere**, and blocks are written before the
superblock without ordering barriers. The design gives crash *consistency* for
process crashes and page-cache-visible writes (the old superblock still points at
an intact tree, partly freed blocks are kept on deferred lists), but a power loss
can lose the last commits and can leave a file that needs the inspection tool.

### 3.11 Inspection and consistency checking

`StorageCheck.check(storage, path)` opens the file read-only and verifies that

- every block with a stored refcount is reachable from the root block,
- stored refcounts (tree plus persisted deferred updates) match the number of
  observed references,
- every block in the file is accounted for exactly once: allocated, on a
  deferred free list, or inside its area's free-space tree (full coverage check),
- free extents are aligned, non-empty and within the committed file length.

`StorageCheckMain` runs it from the command line for a node file or a
`files.db`; `StorageCheckTest` covers normal and broken files.

## 4. Application formats

### 4.1 BlockStorage — content-addressed blobs

`BlockStorage` (handle 1, META index) maps `SHA-256(content) -> (length, manifest)`.
Block types in DATA:

```
ChunkBlock    := int32 length , bytes[length]              (max blockSize-4 bytes)
ManifestBlock := int64 nextOffset , int32 count , int64 chunkOffsets[count]
BlobRef       := int64 length , int64 manifestOffset       (B+tree value, 32-byte key)
```

Content is split into chunk blocks; manifests chain backwards, so the head of the
chain is the last manifest written. Reading re-reads the chain, then verifies
`SHA-256(result) == id` before returning. `put` is idempotent (existing content is
compared, a mismatch raises a content-address collision), `remove` deletes the
index entry only, and `collectGarbage(reachable)` drops index entries that the
caller did not mark. Unreferenced chunk/manifest blocks are reclaimed by the
normal `DataStorage` refcounting, because `ManifestBlock.getReferencedBlocks`
declares them.

`store(GeneratedMessage)` is the convenience path: protobuf bytes -> SHA-256 -> put.

**What it is used for today.** Only two graphs live in it:

1. the whole node document (`NodeState`) — root handle 2 holds its 32-byte id,
2. the legacy per-user `rootId` graph (section 4.3), which duplicates the user
   file tree and file contents as `Directory`/`StorageBlock` messages.

It is *not* the store for the files the node serves: those are carried in the
`NodeState` protobuf (`snapshotState`, `WRITE_FILE` payloads) and in signed
SyncUnit operation payloads, and `SimpleFuseFS` talks to `DataStorage` through
`FileTree` without using `BlockStorage` at all. There is also no block-level
exchange protocol: sync runs on signed operation logs, `Node.Network.getBlock()`
only searches the in-process test nodes, and `UserFileTree.fromDataRoot` — the
only code that would pull blocks from a peer into the local store — has no
callers.

TODO: remove the whole BlockStorage; remove the legacy per-user `rootId` graph as well. In general:
- Use DataStorage 
- To persist specific blobs of data (such as serialized protobuf messages), create some utility functionality to read write a blob, without a containing key/value store

### 4.2 Node state — one protobuf document per persist

`Node.persistState()` builds a single `Sync.NodeState` (see `src/main/proto/sync.proto`),
stores it through `BlockStorage`, writes the 32-byte block id into handle 2,
runs `collectGarbage` over the reachability graph, and commits.

```
NodeState
  peerId, peerPrivateKey, clock, guidCounter
  users[]  NodeUserState
     userPrivateKey, localName, rootId, rootDirectoryGuid, snapshotState
     operationTree[]      PersistedOperation   (accepted main history)
     localOperationTree[] PersistedOperation   (authored here, not yet merged)
     stagingTree[]        PersistedOperation   (received, not yet merged)
     maxOperationClock[]  OperationClock       (per source node)
     activeNodeSet        ActiveNodeSetState   (version, nodes, lastSeen, leaving process)
     retrievalTree[]      PartialOperationState (resumable transfers)
     participatingSyncUnitIds[], deliveredMessageIds[], inboxMessages[]
  syncUnits[] PersistedSyncUnit
     id, type, creatorPublicKey
     authorizationOperations[] PersistedSyncUnitAuthorizationOperation (signed)
     contentOperations[]       PersistedSyncUnitContentOperation       (signed)
```
TODO: this needs a major refactoring:
- operation trees: 
  - persist directly as BTrees
  - keep trees of different sync units separate
  - also during synchronization, ask for the operations of specific sync units
- users should by treated as normal sync units as far as reasonable

Facts worth knowing:

- Every persisted fact is a protobuf message; log entries keep the **signed
  payload and signature as opaque bytes** (`payload`, `signature`), plus the
  Lamport clock and node hash that form the `OperationId` ordering key. The node
  therefore never needs to re-sign anything on restore and can re-verify
  signatures.
- User operation payloads (file system operations, direct messages, SyncUnit
  participation) are themselves `Sync.UserOperation` protobuf messages. SyncUnit
  state is derived by replaying `authorizationOperations`
  (`SyncUnitAuthorization.replay`) and then `contentOperations`
  (`SyncUnitData.replayContent`).
- The user's file tree is materialized in memory (`UserFileTree`) by replaying
  `snapshotState` (a `Sync.UserTreeState`) plus all three operation trees. Only
  `truncateStableLog()` writes a new `snapshotState`, folding the operations
  acknowledged by every active node into it.
- Private keys (peer and every user) are stored **in clear** inside `NodeState`
  (see section 6).
- Because the whole document is rewritten on each persist, a persist costs one
  new state blob (chunks + manifest), one new 4 KiB DATA block for handle 2, a
  new root block, and copy-on-write of every path shared with the previous state.

TODO: file trees must never be loaded completely into memory; They must not be persisted as a single protobuf message. Instead, use the existing `FileTree`; Also, the user's file tree is just another Shared Folder with the special properties that it cannot be shared, deleted, renamed etc. The root of a user's file tree contains the `home` directory, representing the user's file tree, as well as a directory for shared folders. The user has to specify a unique label for each shared folder. Invites contain the label used by the inviting user. The label may not be "home" or conflict with any other shared folder. On conflict, only the shared folder with the lower id is mapped into the user's view. The other is clearly marked as conflicting in the UI.

### 4.3 The `rootId` block graph

In addition to the state blob, each user maintains a content-addressed copy of
its materialized file tree, reachable through `NodeUserState.rootId`:

```
StorageUserRoot  { shardListId, dataRootBlockId, nodeNrMapId }        (protobuf)
  ShardList      { shardIds[] }
  StorageShard   { dataSize, blockIds[] }
  DataUserRoot   { userStatusMessage, rootDirectoryId, rootDirectoryGuid }
    Directory    { clock, directories[DirectoryEntry], files[FileEntry] }
      DirectoryEntry { clock, name, directoryId, entityId }
      FileEntry      { clock, name, dataBlockId, entityId }
        StorageBlock { dataBytes[] }        <- file content as a stored block
```

`Node.collectReachableBlocks` walks exactly this graph (state blob, root, shard
list, shards, data root, directory tree) plus the `NodeState` blob itself, so all
of it survives garbage collection. Note that `UserFileTree.fromDataRoot` exists
but is never called: the `DataUserRoot`/`ShardList`/`NodeNrMap` graph is
write-only data today. It is still written on every `rebuildCurrentState()`,
which means each user write also re-stores the complete file tree and every file
content as content-addressed blocks.

### 4.4 `FileTree` — binary inode tree (used by `files.db` only)

`FileTree` is a B+tree with a 21-byte key `guid(12) | type(1) | offset(8)`
(`ShortGuid` = int32 nodeId + int64 counter), plus a separate 36-byte-key spill
tree (`sha256(name) | uint32 counter`) for oversized directory items.

| Entry type | Code | Value |
| --- | --- | --- |
| `FILE_HEADER` | 0 | `parentId[12] , size , creationTime , modificationTime` (uint64 each) |
| `DIR_HEADER` | 1 | `parentId[12] , creationTime , modificationTime` |
| `DIR_ITEM` | 2 | `spillRoot , uint8 nameCount , nameEntries[]` (max 4096 bytes, else spill tree) |
| `DIR_INDEX` | 3 | sequential directory listing entry (uses `offset` as counter) |
| `INLINE_DATA` | 4 | file bytes, `offset` = byte offset in the file |
| `EXTENT_DATA` | 5 | `int64 blockOffsets[]`, `offset` = byte offset covered |

`DIR_ITEM` keys use a CRC32C of the name in the `offset` component and store a
list of `{ locationGuid[12], locationType, nameLength, name[] }` entries to
handle collisions. Data is stored inline while the file is at most
`inlineDataThreshold` (16 KiB) and converted to extent blocks in the DATA area
above that size. All multi-byte fields are big-endian.

`SimpleFuseFS` commits after every mutating FUSE operation (`dirty` flag) and
additionally every 5 seconds from a scheduler thread; a failed periodic commit
permanently fails the mount (later operations return `EIO`).

## 5. When a write becomes durable

| Trigger | What is written |
| --- | --- |
| any local user operation (`recordLocalOperation`: FUSE create/write/truncate/rename/delete, status change, message send) | full `NodeState` + GC + `DataStorage.commit()` |
| `rebuildCurrentState()` after an operation | a second `persistState()` (state blob and `DataUserRoot` graph rebuilt) |
| receiving/staging/merging remote operations, SyncUnit joins, invitations, role changes | `persistState()` |
| sync with peers (`Sync now`) | `persistState()` after each ingested batch |
| stable-log truncation (`truncateStableLog`) | new `snapshotState` + `persistState()` |
| `SimpleFuseFS` mutation / 5 s timer | `DataStorage.commit()` of the `FileTree` root handle |
| explicit close (`Node.close`) | one final `persistState()` before the storage file is closed |

So there is no WAL and no batching for the node: one user-visible write can cause
two full commits of the node document.

## 6. Security properties of the stored data

- **No encryption at rest.** `node.db` contains the peer private key, every user's
  private key, all logs, messages and file contents in clear text. The README and
  `doc/users-and-nodes.md` explicitly rely on filesystem permissions.
- Exported credentials are the only encrypted artifact: `UserCredential` with
  passphrase-derived AES-GCM (`CredentialProtection`), salt and IV stored beside
  the ciphertext.
- Integrity of *replicated* data is enforced cryptographically: operation
  signatures (Ed25519) and the SHA-256 content addressing in `BlockStorage`
  (`getBlock` re-hashes, `put` rejects mismatches).
- Integrity of the *storage layer* is structural only: magic/version checks,
  offset and length validation, refcount bookkeeping, plus the optional
  `StorageCheck` scan. There are no per-block checksums.

## 7. Design vs. implementation

Implemented:

- single-file copy-on-write storage with SYSTEM/META/DATA areas, per-area block
  sizes, free-space extent trees, refcount tree, deferred frees, typed blocks,
  B+tree with variable-size values, and copy-on-write snapshots via `RefMode`.
- blob data as inline/extent entries and a GUID-keyed inode tree with hashed
  directory names and a spill tree (`FileTree`).
- signed operation histories per SyncUnit with main/local/staging logs, resumable
  transfers, causal snapshots, and active node sets.

Described in `doc/storage-sync.md` but not implemented:

- the `systemKvTree` and `guidMappingTree` blocks (short GUIDs are used by
  `FileTree`/`SimpleFuseFS` only; the node uses protobuf `entityId` byte arrays
  and a per-node counter), TODO: remove systemKvTree from documentation. Use and implement the `guidMappingTree`.
- the distributed clock and `nextGuidCounter` in the root block (they live in
  `NodeState`), TODO: use/implement
- the user file system as an inode/operation tree in `DataStorage` with
  `OperationKey`, `SNAPSHOT`, `INLINE_DATA` and `EXTENT_DATA` entries for user
  files (today the operations are protobuf messages in a `NodeState` list and file
  data is a protobuf payload field),
- storing the active node set in the system KV tree (today it is a field of
  `NodeUserState`), TODO: remove reference to the system KV tree
- `StorageUserRoot` shard splitting: shard lists are created empty and never
  filled. TODO: remove from documentation; no sharding is planned for the moment

## 8. Practical consequences

- A `node.db` grows by roughly one state blob per operation. Space is reused
  through refcount-driven freeing and the deferred free lists (applied in the
  next transaction), but the file itself is only truncated to the committed
  length when opening, so it does not shrink in normal operation.
- Concurrent writers are impossible by design (`RandomAccessFile`, single writer,
  in-memory transaction state); `Node` and `SimpleFuseFS` both assume one process
  per file.
- Tooling that reads these files must implement the big-endian structs of the
  storage layer plus protobuf for the node layer; `StorageCheck` and
  `StorageCheckTest` are the reference for the structural invariants.

## 9. Open questions

The TODOs above are implementable, but the following decisions must be made
first. They are grouped by the TODO they belong to.

### 9.1 Root reference handle IDs (section 3.3)

- Are handle IDs meant to be globally reserved constants, or namespaced per
  application? `BlockStorage` and `SimpleFuseFS` both use ID 1 today, which is
  harmless while they use separate files. => globally reserved
- Will `Node` and `SimpleFuseFS` ever share one `DataStorage` file? If not, what
  concrete collision does "separate handle ids, even across applications" avoid?
  => no collision expected, but it avoids confusion
- What is the reservation scheme for future handles (e.g. blocks of IDs per
  subsystem)? => no explicit scheme yet

### 9.2 Blob storage without `BlockStorage` (section 4.1)

- Is the replacement blob store content-addressed (keeping deduplication and
  SHA-256 verification) or a plain caller-identified blob?
  => caller identified
- Is it intended only for serialized protobuf messages such as `NodeState`, or
  will it also store user file content and sync transfers?
  => intended for relatively small blobs/protobuf messages
- Should it reuse the existing chunk/manifest layout? How are length, manifest
  offset and block area recorded, and by which root handle?
  => just use a list of blocks referencing data blobs. See deferred lists in DataStorage
- How is garbage collection handled once `BlockStorage.collectGarbage` and the
  `rootId` graph are gone?
  => by block ownership
- What happens to the `getBlock()` paths and tests that currently depend on
  `BlockStorage`, and to `UserFileTree.fromDataRoot`, which has no callers?
  => refactor/remove/update accordingly

### 9.3 Operation trees and SyncUnits (section 4.2)

- What is the exact serialized key layout? `doc/storage-sync.md` defines
  `OperationKey` (`operationId[40] + entryType + offset`) but no Java
  implementation exists.
  => implement in Java
- How are operation payloads and signatures stored when they exceed a META block
  size? Are they split into inline/extent entries, DATA blocks, or something
  else?
  => split into inline/extent entries
- Do accepted, local and staging histories remain three separate trees per
  SyncUnit, or is the staging concept replaced by transfer state?
  => three separate trees; periodically reconciled
- What is per-SyncUnit rather than per-user now: `snapshotState`,
  `maxOperationClock`, `truncateStableLog` , retrieval state, inbox messages,
  active node set?
  => the user becomes a sync unit, containing per-user data, such as messages, 
  accepted sync unit, contacts etc. Each node has a list of users it synchronizes.
- The sync protocol currently returns a whole `PersistedSyncUnit`. How should
  `RemoteSyncRequest`/`RemoteSyncResponse` carry per-SyncUnit clocks, offsets,
  partial transfers and snapshots?
  => transfer modifications only, except for initial full syncs. 
- Are authorization operations and content operations in the same B+tree (with
  different key/entry types) or separate trees?
  => same tree; they are just different operation types
- What does "users should be treated as normal SyncUnits as far as reasonable"
  mean concretely? Users currently have contacts, a private key, an inbox, an
  active node set and a home file tree, and `createSyncUnit` explicitly rejects
  the `USER` type. Which of these move into the SyncUnit model, and which remain
  user-specific?
  => see above; users are sync units, each node just has a list of users it synchronizes.

### 9.4 `FileTree`-backed user and shared folders (section 4.2)

- Is the `FileTree` authoritative, a materialized cache rebuilt from operation
  logs, or both? How are the two representations kept consistent?
  => authoritative; rebuilt from operation logs as needed, with consistency ensured by applying operations in order;
  keep file tree snapshots in intervals.
- Is the user's home tree its own SyncUnit, a reserved directory inside the
  `USER` SyncUnit, or a separate local-only structure? => own syncunit
- How are shared folders overlaid under the root? Are they real inodes in one
  `FileTree`, virtual mounts, or separate trees joined in the UI? => see above
- What exactly is a shared-folder label's scope (per user, per node, global), and
  how is it normalized and compared (case sensitivity, Unicode normalization,
  whitespace)? => per user; case sensitive, no normalization
- What is the deterministic ordering behind "the shared folder with the lower
  id"? Is it the `SyncUnitId` byte order, creation order, or something else?
  => `SyncUnitId` byte order
- What does "clearly marked as conflicting in the UI" mean, and what happens
  when a conflicting folder is renamed, removed, or when the other folder's
  label changes?
  => the UI shows that the two folders have the same label. After a rename, the folder appears in the users file system view
- Can files or directories be moved between shared folders and home? What
  happens on conflicting names?
  => operations between shared folders (home is also a shared folder) are allowed; they are represented as add/delete/write operations
- How do invites carry the label and how does the invitee map it into the root? 
  => extend the invite message; when the invitee accepts, it is mapped; the invitee can optionally adjust the label
- `FileTree` currently exposes `read(guid)` and `SimpleFuseFS` may materialize
  whole files. Which APIs must be streaming/range-based to satisfy "never loaded
  completely into memory"?
  => to all file operations in a streaming/range based fashion (even directory reads)
- How do `FileTree`'s `ShortGuid`s relate to the node's full entity IDs? This
  depends on the GUID mapping tree (9.5).
  => the guid mapping table is per sync unit

### 9.5 `guidMappingTree` and `ShortGuid`s (section 7)

- Is the mapping key a hash of the peer public key (as `storage-sync.md` says)
  or a hash of the peer ID (as `Node.nodeKeyFor` currently does)?
  => use SHA-256(PeerId bytes); update documentation accordingly; make consistent across the codebase
- When is a `nodeId` allocated: when the node is first seen, when a short GUID is
  first persisted, or lazily when a block is written?
  => lazily when a block is written
- Is the mapping shared by all `FileTree` instances in a node file, or per tree?
  => per sync unit
- Are short GUIDs ever sent to other peers? If yes, how does a receiver resolve
  a `nodeId` that is private to the sender? If no, how are snapshots and block
  transfers expressed?
  => short GUIDs are not sent to other peers; snapshots and block transfers use full node IDs
- How are existing 40-byte entity IDs migrated to `ShortGuid`s?
  => no migration needed, all existing data can be discarded
- What happens when the 32-bit `nodeId` space is exhausted, given that mappings
  are never removed?
  => this is a design limitation; raise an error
- Is `nextGuidCounter` the allocator for `nodeId`s in the mapping tree or the
  counter used in entity IDs? The current `Node.guidCounter` is the latter.
  => counter used in entity IDs

### 9.6 Distributed clock and counters in the root block (section 7)

- Should the clock be one per node or one per user? `Node.clock` is currently
  node-global, while user operations and SyncUnit operations interleave.
  => the clock is per sync unit
- `DataStorage` has `RootLongHandle` but no public accessor. Is one long handle
  per value (clock, counter) acceptable, or should these become dedicated root
  block fields?
  => n/a
- Must the clock and counter be written in the same transaction as the data they
  stamp? What happens if the state blob is persisted but the root block update
  fails?
  => the transactions span the whole node storage file, so this should not be an issue
- Should the root block values become the only source of truth, or are they
  mirrored in `NodeState` during a transition?
  => in memory mirroring is ok

### 9.7 Active node set and sharding documentation (section 7)

- After removing the `systemKvTree` reference, where is the active node set meant
  to live? It currently remains in `NodeUserState`.
  => the active node set should be maintained per sync unit, similar to the guid mapping
- If the legacy `rootId` graph is removed, can `StorageUserRoot`, `ShardList`,
  `StorageShard`, `DataUserRoot` and `NodeNrMap` be deleted as well?
  => yes
- Should the sharding text be removed from `storage-sync.md` too, or only from
  this document? Is any future block-level sharding still planned?
  => remove all references to sharding

### 9.8 Follow-up questions after the answers

#### Per-SyncUnit rooting

- Root reference handles are global, fixed `uint16` IDs in the root block. Where
  are the arbitrary per-SyncUnit roots stored: accepted/local/staging operation
  trees, `FileTree`, GUID mapping tree, active node set and per-unit clock?
  => propose some fitting data structure; I guess that map/tree of sync units is required,
  each with its own data structure and set of roots; use either persisted protobuf messages,
  or slotted data types for simpler cases
- Should there be one node-level registry tree keyed by `SyncUnitId` that points
  to all per-unit roots, and if so, which handle roots that registry?
  => I would suggest a single handle for the whole node state, and reference sync units from there
- Are unit roots stored as full entity IDs or offsets, and how are units loaded
  lazily without loading all operation trees or file trees at startup?
  => Sync units should store a full snapshot at the end of the local operation history. Typically, use some BTree to store the data and access it efficiently, without loading everything into memory

#### Blob utility

- How does a caller refer to a blob? Is the reference an in-memory object only,
  a block offset, a key in a root handle, or something that must be persisted
  separately?
- How can multiple blobs coexist without reintroducing a key/value directory?
- What is the on-disk layout of "a list of blocks referencing data blobs"? Is it
  a header block with length plus block offsets, a linked list, or the existing
  manifest layout?
- Are blobs immutable? Can a caller overwrite or free one, and how does block
  ownership know that all blocks of a replaced blob can be released?
- Is there a maximum blob size? What happens when a serialized message exceeds
  it?
- The deferred free lists are an internal `DataStorage` reclamation mechanism
  for freed blocks; they do not track application blobs. How does a blob's
  container block get freed when the caller drops it?

=> I see few reasons to store blobs outside of BTrees. If such storage is needed, use linked
list of blobs containing block offsets of data blobs containing the actual data. The last block in the list can then contain the final length of the blob.

#### Operation trees

- What is the complete `entryType` taxonomy now that authorization, content,
  contacts, messages, todos and user operations share one tree? The
  `OperationKey` in `storage-sync.md` only defines file-system entry types.
  => come up with a proposal.
- What is the inline/extent encoding for oversized operation values? Which block
  type stores the extent list, and does the signature cover only the logical
  payload?
  => signature: cover full payload; extend list: stored in the B-Tree, referencing data blocks
- What exactly triggers reconciliation of local/staging/accepted trees, and in
  which direction? Is it per SyncUnit ack tracking or a local merge?
  => periodical reconciliation; local and staging trees are merged into the accepted tree
- For an initial full sync, what is transferred: a snapshot only, a snapshot plus
  an operation tail, or the complete operation history? Are `FileTree` blocks
  transferred, or is the file tree rebuilt locally from the operations?
  => transfer a whole snapshot. transfer by file/directory id, thus it is just a list of (file id, content), (directory id, list of directory entries); They can be stored directly into the B-Tree on the receiver side.



#### Users as SyncUnits

- Which user data is replicated as USER SyncUnit content, and which stays
  local-only: contacts, inbox messages, delivered message IDs, retrieval state,
  participating SyncUnit IDs, local name, user private key?
  => local only: retrieval state, user private key
- How is a direct message from user A to user B represented when each user is a
  separate SyncUnit? Is the recipient's inbox part of B's USER unit, A's USER
  unit, or a separate local store?
  => recipient inbox is part of B's USER unit; user A has an outbox
- What does "accepted sync unit" mean: the set of `participatingSyncUnitIds`,
  and are those references themselves content operations in the USER unit?
  => users receive invitations to sync units. They can accept them; then they are acceppted and the nodes of the user synchronize them accordingly.
- Is the per-SyncUnit active node set replicated unit content or local metadata?
  Is membership based on user keys or node keys, and what happens when one node
  hosts several users that are members of the same shared folder?
  => active nodes set is replicated unit content; membership is based on node keys;
- For a per-SyncUnit clock: does each unit start at zero, is it advanced by
  received unit operations, and which operations use it? What clock is used for
  node-level actions such as active-node-set versioning, direct-message delivery
  and join/leave?
  => there should be no need for clock for node-level actions. active node sets are per sync unit; delivery is per sync unit (user sync unit); Only exception: per node counter to genereate SyncUnit IDs
- Is the entity-ID counter now per node or per SyncUnit, and where is each
  per-unit clock/counter persisted?
  => entity-ID counter is per SyncUnit; stored in sync unit

#### FileTree, shared folders and GUIDs

- The home tree is its own SyncUnit. What type and ID does it use, how does it
  relate to the USER SyncUnit, and how does the root view combine home with the
  shared-folder units?
  => each user unit has the id of the home tree sync unit
- A move between two SyncUnits cannot be a single `FileTree` operation. Is it
  deliberately represented as delete plus create/write, thereby losing entity
  identity and Kleppmann move semantics? Is that acceptable?
  => yes, to avoid cross sync unit operations and the associated complexity
- Is a shared-folder label a local per-user alias or shared unit metadata? Are
  labels synced? What happens if the inviter renames after the invitee accepted
  or adjusted its own label?
  => shared-folder label is per user and synced for the user. The same shared folder can have different labels for different users.
- If only the lower-ID folder is mapped into the view, how does the user access
  the conflicting higher-ID folder, and what happens when the lower-ID folder is
  later removed or renamed?
  => the user has to associate a separate label in the UI. If the lower-ID folder is removed, the previously conflicting higher-ID folder can then be accessed through the same label
- Which layers need streaming APIs: `FileTree` directory iteration, FUSE
  readdir/read, the UI, or all of them? `FileTree.listDirectory` currently
  returns a fully materialized list.
  => use streaming APIs wherever possible to avoid materializing large directory listings in memory. If the UI shows everything, most likely the UI has to be changed.
- The GUID mapping is per SyncUnit, but short GUIDs are never sent. Why is it
  per unit rather than one node-wide mapping, and how are short GUIDs converted
  back to full IDs when serving or rebuilding a unit?
  => mapping is per SyncUnit, as different nodes participate in each sync unit. They are not shared across nodes,
  as allocating unique short IDs without conflicts is difficult across multiple nodes.
  => short GUIDs are converted back to full IDs using the per-unit, node local mapping tree.
- `nextGuidCounter` is now the entity-ID counter, so what allocates `nodeId`s in
  each per-unit mapping tree: a separate counter, a scan for the maximum, or
  something else? Where is that value persisted?
  => separate counter, persisted besides the mapping tree.
- When a node replays a remote operation for an entity whose full ID is not yet
  mapped, does it allocate a mapping for the remote node hash lazily?
  => yes

### 9.9 Additional follow-up questions

#### Full-sync snapshots

- Is a snapshot itself a signed `SNAPSHOT` operation in the unit's accepted tree,
  or only materialized `(file id, content)` and
  `(directory id, directory entries)` records?
  => not signed. Snapshots are never transmitted to other nodes, they are only materialized locally.
- If a snapshot is not signed, how does the receiver authenticate it and verify
  that the sender was authorized to produce it?
  => n/a
- Which operation ID and per-source max clocks does a snapshot carry, so that a
  later "modifications only" sync knows which operations it already covers?
  => good catch: use a separate tree for the snapshots. The snapshot id matches an operation ID and contains the modification of that operation as well. Initialize the operation counter to 1, and add a empty snapshot with count 0
- What happens if two peers offer different snapshots? Which one is chosen?
- Does a snapshot include authorization state or enough authorization history to
  validate subsequent content operations?
  => n/a
- Is snapshot transfer resumable and streamed like the existing operation
  transfers, given that it can contain large files?
  => n/a

#### Direct messages and the recipient's USER unit

- USER units currently authorize only the creator to modify them. How does user
  A put a message into user B's USER-unit inbox: is it re-signed/accepted by B,
  or is there a special cross-user write rule?
  => a node of user A has to ask a node of user B to add the message
     to B's inbox.
- Is A's outbox replicated in A's USER unit, is B's inbox replicated in B's USER
  unit, or are both stored?
  => A's outbox is replicated in A's USER unit, B's inbox is replicated in B's USER unit.
- Which of `deliveredMessageIds`, outbox state and delivery retries are
  local-only?
  => once a message is delivered, it is removed from the outbox, which is synced across nodes
- If B has multiple nodes, how is duplicate delivery handled and which node
  writes the inbox operation?
  => any node of B can write the inbox operation, duplicate delivery is avoided by checking `deliveredMessageIds` before adding a message.

#### Accepted SyncUnits

- Section 9.3 says the USER unit contains accepted sync units, while 9.8 says
  `participatingSyncUnitIds` is local-only. Is the authoritative accepted-unit
  list replicated USER-unit content, with `participatingSyncUnitIds` only a local
  cache?
  => it is replicated, updated 9.8
- If the accepted-unit list is local-only, how does a new node of the same user
  learn which shared folders to sync?
  => n/a
- Are accept and leave operations USER-unit content signed by the user key?
  => yes, they are signed by the user's key.


#### Home SyncUnit

- How is the home unit's ID created, and where is it replicated so that all nodes
  of the user agree on it?
  => normal sync unit ID creation, stored when the user is first created, immutable
- Does the home unit use a new `SyncUnitAuthorization.Type`, a flag in the USER
  unit, or a special authorization rule to enforce "cannot be shared, deleted or
  renamed"?
  => special authorization rule, enforced by the system, not modifiable by users
- How does the root view know that this unit is `home` rather than a normal
  shared folder?
  => user contains the id of the home shared folder

#### GUID mapping

- The mapping tree is `full node hash -> nodeId`. To convert a `ShortGuid` back
  to a full ID, a reverse lookup `nodeId -> nodeHash` is needed. Is there a
  second reverse tree, or is a scan acceptable?
  => use separate tree
- Where exactly is the per-unit `nodeId` counter persisted ("besides the mapping
  tree"): in the unit record, in a separate root, or in a dedicated block?
  => unit record
- `FileTree` stores `int32 nodeId`. Is the valid range `0..2^31-1`, or are
  negative values used as unsigned bits? What exactly happens at exhaustion?
  => unsigned bits; exhaustion triggers en error during synchronization
- When a local entity is created, is the local node's own hash mapped eagerly, or
  lazily on first write?
  => lazily on first write

#### Active node set

- If `lastSeen` is replicated unit content, every heartbeat writes an operation
  into every unit. Is `lastSeen` really replicated, or only membership and
  join/leave events?
  => you are right, `lastSeen` is not replicated; only membership and join/leave events are replicated.
- Which unit's clock orders join/leave? What authorization is required, and is
  it user-key or node-key based?
  => each unit has a single clock; join/leave has to be authorized by a user which has at least viewer access in the unit.
- A node may host several members of a unit. Is it one active-node entry or
  several? Which user signs its join/leave?
  => one entry per node; join/leave is signed by the first user that joins the node.
- How does the active node set relate to `SyncUnitAuthorization` members? Node
  membership and user membership seem to be separate concepts now.
  => nodes membership is separate; at least one user has to sponsor the node's membership in the unit.

#### SyncUnit IDs

- `SyncUnitId.create()` currently produces 32 random bytes. If IDs become
  `nodeHash + counter`, what is the exact format, and where is the node-level
  counter persisted?
  => implementation detail
- Is a `SyncUnitId` a kind of full GUID, or are they unrelated? Does the GUID
  mapping tree also map SyncUnit creators?
  => kind of full guid (node id + counter)
- How is the home unit ID allocated without two nodes of the same user creating
  different home units?
  => a user is always created atomically on a single node (user unit and home folder unit are created together)

#### Operation trees

- What is the proposed `entryType` taxonomy shared by authorization, file
  operations, contacts, messages, todos and snapshots?
- Does one operation produce one header entry plus data/extent entries? Where are
  the signature, author key, logical payload length and operation ID stored?
- If authorization and content operations share one tree, what is the replay
  order within one `OperationId`?
- Is `SNAPSHOT` an accepted operation? Who may author it, and how does it
  interact with `truncateStableLog`?
- During periodic reconciliation, are local and staging operations validated
  before merging into accepted? Are they re-signed or copied verbatim? What
  happens on duplicate IDs or conflicting operations?

#### FileTree authority and snapshots

- Section 9.4 says the `FileTree` is authoritative and rebuilt from operations,
  with snapshots in intervals; 9.8 says to store a full snapshot at the end of
  the local operation history. Is the persisted `FileTree` the primary on-disk
  state, or a cache rebuilt from snapshot plus operations at load?
- Are the interval "file tree snapshots" local checkpoints, synced `SNAPSHOT`
  operations, or both?
- If a full sync transfers materialized file/directory records directly into the
  receiver's B-tree, is the receiver's operation tree also populated, or can it
  start with an empty log plus a snapshot operation?
  => replicate operation log as well
- Does the receiving node preserve the sender's full entity IDs in its per-unit
  mapping, so that later operations referring to them resolve correctly?
  => the mapping per unit, but node-local

#### Root block, blobs and B-trees

- With clocks and entity counters now per SyncUnit, what remains in the root
  block? Only the single node-state registry handle, or also a node-level
  SyncUnit-ID counter?
- Section 9.6 says `RootLongHandle` is "n/a", but section 7 still says TODO use
  the distributed clock and `nextGuidCounter` in the root block. Which text is
  now correct?
- If "there are few reasons to store blobs outside of BTrees", does the blob
  utility still exist? Is it just a root handle to the node-state B-tree, or is
  there still a separate single-blob API for bootstrap data?
- How are large B-tree values handled? A serialized node state or snapshot can
  exceed a META block; is the linked-list-of-blocks representation referenced
  from a B-tree entry?

#### Cross-unit moves and streaming

- A move between units is delete plus create/write. For a directory this is a
  recursive copy. Is partial failure acceptable, or must the copy complete before
  deletion?
- Is the entity ID preserved across the copy, or is a new ID allocated in the
  destination unit?
- For streaming directory reads, what API shape is intended: paged iterators,
  callbacks, or FUSE-level streaming? How are stable listings maintained under
  copy-on-write changes?
- How is the synthetic root view (home plus shared-folder labels) represented to
  FUSE? Is it a real `FileTree`, or generated on the fly?

