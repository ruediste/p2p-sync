# Persistence and Synchronization

This document is the unified design and implementation reference for p2p-sync's
storage, identity, synchronization and on-disk formats. It replaces the earlier separate design and persistence documents.

Remaining work is tracked in `TODO.md` at the repository root.

## 1. Concepts

### Users, nodes and SyncUnits

A **user** is an identity with an Ed25519 key pair. The public key identifies the
user and the private key signs operations authored by that user. For replication,
a user is also a SyncUnit: an independently replicated unit of data with its own
operation history, materialized state, clock and membership rules.

A **node** is one process with its own peer key pair and one `node.db` file. A node
can synchronize several users. Each user has a participation list of SyncUnits,
and the node synchronizes the units in those lists.

SyncUnit types are `USER`, `SHARED_FOLDER`, `TODO_LIST` and `MESSAGE_CHANNEL`.
Each user also has a special home `SHARED_FOLDER` unit that owns the user's file
tree. The `USER` unit carries contacts, messages, participation and labels. Shared
folders carry a path/content file map and a per-user label; todo lists and message
channels carry their own content operations.

Any user may create a normal SyncUnit and invite other users. Each received
operation is authenticated by its author's signature and authorized against the
unit's membership and roles before it enters the unit history or materialized
state. SharedFolder and TodoList roles are Viewer, Editor and Manager; message
channels use Member and Manager, where Managers manage membership and the final
Manager cannot be removed.

A node is single-writer. All persisted state for a node lives in one
`DataStorage` file, so one process must own it.

### Identity and ordering

Full entity identifiers are `nodeHash[32] | counter[8]`, where `nodeHash` is
`SHA-256(PeerId bytes)` and `counter` is a per-SyncUnit counter. File-tree and
GUID trees use the node-local short form with a mapped 32-bit node id.

Operations are ordered by a per-SyncUnit Lamport clock and the node hash as tie
breaker. The complete identifier is `lamportClock[8] | nodeHash[32]`.

## 2. Persisted artifacts

| Artifact | Produced by | Content |
| --- | --- | --- |
| `node.db` (any path given to `DesktopMain`) | `Node` | Complete node state: peer key pair, users, signed operation logs in per-SyncUnit B-trees, SyncUnit registry and histories, active node sets, inbox, home/shared-folder file trees and the shared-folder content tree. A single `DataStorage` file. |
| `files.db` (any path given to `SimpleFuseMain`) | `SimpleFuseFS` | One `FileTree` in a `DataStorage` file. Standalone prototype, unrelated to `Node`. |
| exported user credential file | `Node.exportUserKey` | protobuf `UserCredential` (`ciphertext`, `salt`, `iv`), written with POSIX permissions `600`. Passphrase-derived AES-GCM; the passphrase may be empty. |
| attachment export (UI) | `UnitsPanel` / mail view | plain user-chosen files, not node state |

Everything else is in memory only: the libp2p address book (`MemoryAddressBook`),
mounted directories, FUSE path caches, and UI state. There is no configuration
file, no `Preferences`, and no per-user directory in the data path.

## 3. Layer stack

```
UI / NodeFuseFS / SimpleFuseFS
        |  operations (signed protobuf events) or FileTree calls
        v
Node: NodeState metadata blob, per-SyncUnit root registry, OperationTree logs,
      GuidMapper trees, home/shared-folder FileTrees
        |
        v
BlobStorage: B-tree indexed, caller-identified blobs (currently NodeState only)
        |
        v
DataStorage: single-file copy-on-write block store
   superblock -> root block -> { free-space trees, ref-count tree, root ref handles }
                            v
              typed blocks / slots / B+tree nodes (raw binary)
```

Two independent applications sit on `DataStorage`:

- `Node` stores a small protobuf metadata document, per-SyncUnit operation
  B-trees, GUID mappings, and `FileTree` file data.
- `SimpleFuseFS` stores a B+tree of binary key/value entries (`FileTree`) and never
  uses protobuf.

## 4. DataStorage file format

`DataStorage` is a btrfs-inspired, copy-on-write, single-writer store. Classes:
`DataStorage`, `RootBlock`, `SlotBlock`, `BlockType`, `BTree`, `RefCountUpdates`,
`RefMode` (package `com.github.ruediste.p2psync.storage`).

### 4.1 Superblock (offset 0, 4096 bytes)

| Field | Type | Notes |
| --- | --- | --- |
| magic | 16 bytes | `d6 1b 52 d5 6d c6 7e 4f fd 40 c6 d8 f5 ee 03 db` |
| version | uint64 | `FORMAT_VERSION = 1`; a mismatch refuses to open |
| rootBlock | uint64 | file offset of the committed root block |
| dataBlockSize | int32 | default 4096 |
| metaBlockSize | int32 | default 16384 |
| systemBlockSize | int32 | default 4096 |

Block sizes live in the superblock (they are needed to read the root block),
All structs are encoded with `ByteBuffer` default order, i.e.
**big-endian**, and there is **no padding/alignment inside blocks**.

### 4.2 Areas and file layout

Three areas with their own block size and free-space tree:

| Area | Block size | Initial size | Holds |
| --- | --- | --- | --- |
| `SYSTEM` | 4 KiB | 32 KiB | root block, info block |
| `META` | 16 KiB | 2 MiB | all trees (free-space trees, ref-count tree, operation trees, GUID maps, FileTree nodes, unit root registry, deferred lists) |
| `DATA` | 4 KiB | 8 MiB | file data/extents, operation payload blocks, blob chain blocks |

`format(...)` lays the areas out contiguously after the superblock, sets the file
length to the end of `DATA`, seeds a small META bootstrap pool (up to 256 blocks)
and stores the rest of the initial space as free extents in the free-space trees.
When an area runs out of blocks, `extend(area)` appends at least 1 MiB (and at
least 256 blocks) to the end of the file and records it as a free extent. Because
areas are appended in the order they grow, a file is not area-partitioned over
its whole life — only the free-space trees know which blocks belong to which
area.

### 4.3 Root block

There is a new root block for every commit. It is a `SlotBlock` (see 4.5) with
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
| 1 | `BlobStorage` index B+tree | META | `BTree.NodeBlock` (32-byte key) |
| 2 | `UnitRootStore` per-SyncUnit root registry | META | `BTree.NodeBlock` (33-byte key) |
| 1 | `SimpleFuseFS` (`files.db`) | META | `FileTree.BLOCK_TYPE` |

Handle IDs are globally reserved even though `Node` and `SimpleFuseFS` use
separate files.

`guidMappingTree` is now implemented per SyncUnit through `GuidMapper`
(`GUID_FORWARD`/`GUID_REVERSE` roots in `UnitRootStore`). Each SyncUnit also has
its own Lamport clock, entity counter and active node set, persisted in its
`NodeUserState` or `PersistedSyncUnit` metadata. A node-level `RootLongHandle`
(id 3) allocates SyncUnit IDs.

### 4.4 Info block

Currently only one field: `inlineDataThreshold` (int32, 16384). It is separated
from the root block because it changes rarely, and it is what `FileTree` uses to
decide between inline and extent storage for file data.

### 4.5 Typed blocks, slots and blocking

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

### 4.6 B+tree nodes

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
  how `FileTree` and `OperationTree` work).
- Tree blocks declare their child references through `BTree.blockType(...)`, so
  refcounts and `StorageCheck` see internal pointers and entry references.

### 4.7 Reference counting and ownership

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

### 4.8 Deferred bookkeeping blocks (all in META)

| Structure | Purpose |
| --- | --- |
| deferred free block lists (`system`/`meta`/`data`) | linked list of blocks that became free in the last transaction; head offsets in the root block; applied at the start of the next transaction |
| pending META free-tree removal list | blocks allocated in the last transaction that still have to be removed from the META free-space tree; kept pending so allocation never hands them out before the removal is done |
| deferred ref-count update list | absolute refcount updates for blocks that still had to be written when the commit point was reached; each record stores offset, count, area and a `PersistableBlockType` code (1 = META free tree, 2 = ref-count tree) so the children of such a block can be released later |

The point of these lists is that the tail of a commit (persisting them, writing
the final root block) touches only the META free-space tree, which keeps the
commit from recursively needing new free-space updates in all areas.

### 4.9 Commit sequence (`DataStorage.commit()`)

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

### 4.10 Open, validation and crash behavior

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

### 4.11 Inspection and consistency checking

`StorageCheck.check(storage, path)` opens the file read-only and verifies that

- every block with a stored refcount is reachable from the root block,
- stored refcounts (tree plus persisted deferred updates) match the number of
  observed references,
- every block in the file is accounted for exactly once: allocated, on a
  deferred free list, or inside its area's free-space tree (full coverage check),
- free extents are aligned, non-empty and within the committed file length.

`StorageCheckMain` runs it from the command line for a node file or a
`files.db`; `StorageCheckTest` covers normal and broken files.

## 5. Identifiers and clocks

### 5.1 Full and short GUIDs

A full GUID is a 40-byte entity identifier:

```C
struct FullGUID {
    byte nodeHash[32]; // SHA-256 of the PeerId bytes
    uint64_t counter;  // per-SyncUnit entity counter
};

struct ShortGUID {
    uint32_t nodeId;   // node-local 32-bit identifier
    uint64_t counter;
};
```

Each SyncUnit has a private, node-local GUID mapping:

- forward B+tree: full node hash -> 32-bit `nodeId`
- reverse B+tree: 32-bit `nodeId` -> full node hash
- next node id stored in the unit record; 32-bit exhaustion is a hard error

Full GUIDs are used in signed operations and between peers. Short GUIDs are used
only inside local `FileTree`/`GuidTree` blocks and are never sent to other nodes.
A full GUID is mapped lazily when an entity is first written to a local tree.

### 5.2 Lamport clocks

Each SyncUnit maintains a 64-bit Lamport clock. A node increments the unit clock
for every operation it authors in that unit and includes the clock plus its node
hash in the operation ID. Received operations advance the unit clock to at least
the received value. The clock is stored with the unit, not as a root-block field.

## 6. Application formats

### 6.1 `BlobStorage` — caller-identified blobs

`BlobStorage` (handle 1) is a B-tree keyed by a 32-byte caller identifier. Its
only current user is `Node`, which stores the `NodeState` metadata document under
the all-zero key `NODE_STATE_ID`.

A blob is a linked list of DATA blocks. Each chain block stores the offset of the
next block, its own length, and the data. The last block has no successor. The
B-tree value stores the total length and the first block offset. Because the
B-tree owns the reference to the chain, replacing or removing a blob releases the
old chain through normal `DataStorage` reference counting. No content hashing,
deduplication, manifest tree or application-level garbage collection is involved.

The helper is intentionally small: it is for opaque serialized protobuf messages
and other small blobs, not for user file content. File content belongs in
`FileTree`; operation payloads belong in `OperationTree`.

### 6.2 Node state, per-SyncUnit operation trees and file trees

`Node.persistState()` writes three groups of data into one `DataStorage`
transaction:

1. `NodeState` metadata through `BlobStorage`: peer identity/key material, local
   users and their private keys, SyncUnit metadata, per-user snapshots, active
   node sets, inbox state and counters.
2. Per-SyncUnit operation trees through `UnitRootStore` and `OperationTree`.
3. The current user file tree through `FileTree`, for each local user.

`NodeState` no longer contains operation lists or the legacy `rootId` graph.
Operation histories are split into separate B-trees per SyncUnit:

- User/home units: accepted, local and staging operation trees. The USER unit
  holds contacts, messages, participation and labels; the home unit holds
  file-system operations.
- Other SyncUnits: authorization and content operation trees.

Each B-tree key is `lamportClock (8 bytes, big-endian) | nodeHash (32 bytes)`.
Values store the signature inline and reference DATA blocks containing the
payload, so oversized operations are supported. A stored operation is reconstructed
from those blocks when loaded.

`UnitRootStore` is a node-level B-tree, rooted by handle 2, keyed by
`SyncUnitId (32 bytes) | rootKind (1 byte)`. It stores the root offsets for:

| Root kind | Tree |
| --- | --- |
| accepted/local/staging operations | `OperationTree` |
| authorization/content operations | `OperationTree` |
| file tree | `FileTree` |
| forward/reverse GUID mapping | `GuidTree` |

Every entry declares its referenced root block in `getReferencedBlocks`, so root
ownership and `StorageCheck` continue to work.

`GuidMapper` implements the per-SyncUnit, node-local short-GUID mapping:

- forward key: full node hash (`SHA-256(PeerId bytes)`) → unsigned 32-bit node id
- reverse key: node id → full node hash
- next node id is persisted in `NodeUserState.nextNodeId`

Full entity IDs remain `nodeHash[32] | counter[8]`; `FileTree` and `GuidTree` use
`ShortGuid(nodeId[4] | counter[8])`. Short GUIDs never leave the node. A full ID
is mapped lazily when it is first written to a `FileTree`.

The user-visible `UserFileTree` is now backed directly by `FileTree`: path
lookups, directory listings, writes, truncations, moves and deletes operate on
the B-tree, and file contents are read on demand. Reopening a node uses the
persisted `FILE_TREE` root without first materializing the tree in memory.
`snapshotState` is still a full protobuf image of the tree, used locally for
stable-log checkpoints and for restoring a node before replaying operations. It is
no longer part of initial remote synchronization; peers transfer the entry list
plus contacts instead.

Directory iteration is streamed through `FileTree.forEachDirectory`,
`UserFileTree.forEachDirectory`, `NodeUserHandle.forEachDirectory` and the FUSE
`readdir` path; only the legacy `listDirectory` convenience methods still collect
a list.

Each user has a home SyncUnit ID stored in `NodeUserState`. The user's `FileTree`
root, GUID mappings and file-system operations now live under this home unit. The
USER unit holds contacts, messages, participation and labels. Internally the two
histories are separate B-trees; `NodeUserData.operationTree` is a combined view so
the existing API still sees one history.

Shared-folder labels are USER-unit operations, and
`Node.listRootEntries`/`NodeUserHandle.listRoot` combine the home entry with
accepted shared folders, resolving label collisions by the lowest `SyncUnitId`.
Shared-folder content is backed exclusively by a single node-level `FileTree`
keyed by `unitIdHex/path`, rooted through a reserved unit id and GUID mapping.
Listings stream `(path, size)` directly from that tree and file content is read on
demand. Accepted content operations update the tree in place; no path/content map
is materialized.

Initial remote sync transfers the file-tree snapshot as a list of
`(entityId, parentId, name, directory, content)` entries plus the contact list.
`snapshotState` is not sent.

### 6.3 SyncUnit histories

The in-memory `SyncUnitData` still exposes authorization operations as a list and
content operations as a sorted map. `Node.persistState()` mirrors both into
`OperationTree`s and `Node.restoreState()` reconstructs the in-memory form from
them. Authorization operations are replayed before content operations, exactly as
before.

Incremental unit synchronization filters content operations by per-source content
clocks and by the request byte budget while always transferring authorization
operations. Content transfers are segmented with per-operation offsets and the
receiver persists partial payloads so a large operation resumes across requests.

### 6.4 `FileTree` — binary inode tree

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

## 7. Synchronization

### 7.1 Active nodes

Each SyncUnit tracks a replicated set of active node keys. Membership is based on
node keys; at least one user participating in the unit sponsors a node. A node may
host several users in the same unit and is represented once.

The active-node set carries a version (node key + clock), a three-phase leaving
process (`A`, `B`, `I`), and locally tracked `lastSeen` timestamps. `lastSeen` is
not replicated. Sets are exchanged when their versions differ; a received strict
superset replaces the local version, otherwise a local change increments the
version.

Leaving uses the three-phase confirmation protocol documented by the
`ActiveNodeSet` implementation: nodes confirm the leaving status, then removal,
then return to idle. Concurrent phase-A processes are resolved by process id.

Non-user-unit peer selection uses the unit active set once it contains more than
the local node. While a unit only knows itself, it probes all known peers to
bootstrap its active set.

### 7.2 Operation synchronization

Each node seeks all operations of all nodes for the units it participates in.
Within a SyncUnit operations are retrieved in order per source node without gaps;
a node tracks the maximum retrieved clock per source.

The user/home SyncUnit history is split internally into accepted, local and staging
operation trees. A single public view combines them. Retrieved complete operations
go to staging and are periodically reconciled into the accepted tree; the last
operation of a response may be partial and is stored in the retrieval tree until
the remaining bytes arrive. User-operation transfers are byte-budgeted and
segmented with per-operation offsets.

SyncUnit authorization and content operations are stored in their own B-trees.
Responses always carry authorization operations and transfer content operations
incrementally using per-source content clocks and the request byte budget.
Content transfers are segmented with per-operation offsets: an oversized
operation is split across responses and the receiver persists the partial payload
and resumes from the stored received length.

### 7.3 Initial synchronization and snapshots

Initial user synchronization transfers the accepted operation history plus a
file-tree snapshot as `(entityId, parentId, name, directory, content)` entries and
the contact list. The receiver reconstructs the local `FileTree` from those
entries and replays/merges operations. The local `snapshotState` remains the
baseline for stable-log checkpoints but is not transferred.

### 7.4 Log truncation

The causally stable timestamp is the minimum `(maximumOperationClock, nodeId)` over
the active nodes. A snapshot is created for that timestamp and accepted operations
older than it may be removed. Direct-message and participation operations remain
in the USER log until a materialized message snapshot replaces them.

## 8. When a write becomes durable

| Trigger | What is written |
| --- | --- |
| any local user operation (`recordLocalOperation`: FUSE create/write/truncate/rename/delete, status change, message send) | operation B-tree, `FileTree`, `NodeState` metadata, `DataStorage.commit()` |
| `rebuildCurrentState()` after an operation | a second `persistState()` |
| receiving/staging/merging remote operations, SyncUnit joins, invitations, role changes | `persistState()` |
| sync with peers (`Sync now`) | `persistState()` after each ingested batch |
| stable-log truncation (`truncateStableLog`) | new `snapshotState` + `persistState()` |
| `SimpleFuseFS` mutation / 5 s timer | `DataStorage.commit()` of the `FileTree` root handle |
| explicit close (`Node.close`) | one final `persistState()` before the storage file is closed |

The node writes new tree roots and rewrites the `NodeState` metadata blob on
every persist. The shared-folder tree is updated in place by accepted content
operations and its root handle is committed with the rest of the node state;
user file trees are updated copy-on-write. One user-visible write can still cause
two commits of the node document.

## 9. Security properties of the stored data

- **No encryption at rest.** `node.db` contains the peer private key, every user's
  private key, all logs, messages and file contents in clear text. The README and
  `doc/users-and-nodes.md` explicitly rely on filesystem permissions.
- Exported credentials are the only encrypted artifact: `UserCredential` with
  passphrase-derived AES-GCM (`CredentialProtection`), salt and IV stored beside
  the ciphertext.
- Integrity of *replicated* data is enforced cryptographically: operation
  signatures (Ed25519) in the operation B-trees.
- Integrity of the *storage layer* is structural only: magic/version checks,
  offset and length validation, refcount bookkeeping, plus the optional
  `StorageCheck` scan. There are no per-block checksums.