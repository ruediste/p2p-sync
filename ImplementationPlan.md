# p2p-sync Implementation Plan

## 1. Project Overview

**p2p-sync** is a decentralized data synchronization system that turns a user's own devices into a private cloud. It provides encrypted file storage, replication across nodes, and node-to-node communication — all without relying on a central server. The system is built on the IPFS/libp2p stack using TypeScript and targets Linux, Windows, and Android.

The architecture is organized around three encryption layers (Storage, Link, Data), each providing progressively deeper access to user data. Nodes discover each other via Kademlia DHT and mDNS, form per-user dense networks, and replicate sharded data using a custom sync protocol and bitswap. A React-based frontend gives users visibility into their data, NodeTrust configuration, and conflict states, while an Express.js backend exposes a REST/gRPC façade that bridges the frontend with the running libp2p node process.

---

## 2. Project Structure

```
p2p-sync/
├── proto/                          # Protobuf definitions (source of truth for wire formats)
├── src/
│   ├── index.ts                    # Application entry point: wires components, starts node
│   ├── components.ts               # Component registry / dependency injection types
│   ├── crypto/                     # All cryptographic operations, isolated for auditability
│   ├── clock/                      # Vector clock and conflict ordering logic
│   ├── storage/                    # Storage layer: encrypted block storage and replication
│   ├── link/                       # Link layer: block relationships and garbage collection
│   ├── data/                       # Data layer: user-visible data structures and conflict resolution
│   ├── network/                    # Networking, node discovery, and protocol handling
│   │   ├── mdns/                       # Custom mDNS node discovery (already exists, forked from libp2p)
│   ├── user/                       # User identity and multi-device management
│   ├── gen/                        # Auto-generated protobuf TypeScript (from `buf generate`)
│   └── util/                       # Shared utilities
│
├── buf.gen.yaml                    # Buf protobuf codegen config
│
├── apps/
│   ├── frontend/                     # React SPA (file browser, messaging UI, NodeTrust editor)
│   └── backend/                      # Express.js API: auth middleware, REST endpoints
```

Tests are kept along side the implementation in the same directory.

## 3. Classes and Standalone Functions

### 3.1 Crypto (`src/crypto/`)

Based on functionality available from `js-libp2p`.

| Name                                          | Kind     | Responsibility                                                                                                                  |
| --------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `generateUserKeys()`                          | function | Creates a new Ed25519 keypair and three AES-256-GCM symmetric keys (storage, link, data). Returns a `UserKeys` protobuf object. |
| `encryptBlock(key, plaintext)`                | function | AES-GCM encryption with random IV. Used by all three layers with their respective keys.                                         |
| `decryptBlock(key, ciphertext)`               | function | Corresponding decryption.                                                                                                       |
| `signData(privateKey, data)`                  | function | Ed25519 signature over data (used for `StorageUserData` root).                                                                  |
| `verifySignature(publicKey, data, signature)` | function | Verifies an Ed25519 signature.                                                                                                  |

### 3.2 Clock (`src/clock/`)

| Name          | Kind  | Responsibility                                                                                                                                                                                                                                                                                                                      |
| ------------- | ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VectorClock` | class | Immutable vector clock. Methods: `increment(nodeNr)`, `merge(other)`, `compare(other)` → `{before, after, equal, concurrent}`, `clear()`, `prepareModify(nodeNr)`, `removeNode(nodeNr)`. Compact representation using `nodeNr` integers.                                                                                            |
| `NodeMap`     | class | Bidirectional map between `nodeNr` (small integer) and node public key. Manages `nextNodeNr`, join/leave protocol, and the `leaving` flag lifecycle. Each entry and the map itself carry a `VectorClock`. Methods: `join(nodeId)`, `markLeaving(nodeNr)`, `tryCompleteLeave(activeClocks)`, `merge(other)`, `remap(nodeNrMapping)`. |

### 3.3 Storage Layer (`src/storage/`)

| Name                    | Kind  | Responsibility                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ----------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `StorageUserData`       | class | The signed, versioned root of a user's merkle tree. Contains the vector clock visible at the storage layer, references to `DataShard`s, the serialized `NodeTrustSet`, and the encrypted payload linking to the data layer. Methods: `verify(userPublicKey)`, `isNewerThan(other)`.                                                                                                                                                                                        |
| `DataShard`             | class | An immutable list of `StorageBlock` references for a given hash-prefix shard. Methods: `containsBlock(hash)`, `union(other)` (for merging during replication).                                                                                                                                                                                                                                                                                                             |
| `StorageBlock`          | class | A single encrypted content block. Thin wrapper providing CID computation and serialization.                                                                                                                                                                                                                                                                                                                                                                                |
| `StorageClaim`          | class | Declaration by a node that it stores certain shards. Contains `pFailure`, version, shard bitmap. Used by the replication protocol to evaluate redundancy.                                                                                                                                                                                                                                                                                                                  |
| `ReplicationController` | class | Periodically evaluates shard health for each user this node replicates. Loads `StorageClaim`s from all trusted nodes, computes per-shard failure probability, and decides whether to begin replicating additional shards. Triggers bitswap fetches for missing blocks.                                                                                                                                                                                                     |
| `NodeTrustSet`          | class | Represents the authoritative NodeTrust configuration embedded inside `UserData`. Each entry has nodeId, trust level, and a vector clock. Merge strategy: per-nodeId last-writer-wins based on timestamp                                                                                                                                                                                                                                                                    |
| `BlockStore`            | class | Thin abstraction over the Helia/IPFS blockstore. Methods: `put(cid, data)`, `get(cid)`, `has(cid)`, `delete(cid)`. Allows swapping between `FsBlockstore` and in-memory for tests.                                                                                                                                                                                                                                                                                         |
| `StorageQuota`          | class | stores the per user how much data can be stored. The maximum can be unlimited                                                                                                                                                                                                                                                                                                                                                                                              |
| `ManagedNodeEntry`      | class | Each `UserData` carries a `managedNodes` map keyed by nodeId. The value is a `ManagedNodeEntry` data object with its own vector clock plus a set of per-user `QuotaPolicy` records (`userPublicKey → {maxBytes, maxShards, burstBytes}`) describing what that node is allowed to store for that replicated user. Entries are signed with the controlling user’s key (as part of the surrounding `StorageUserData`) so they travel through the normal replication pipeline. |

- **Merge semantics:** `ManagedNodeEntry` implements `DataObject.merge`. Vector clocks ensure deterministic ordering; if two users edit the same node concurrently, the merge walks every `QuotaPolicy` and keeps the **most generous** limit per dimension (max of `maxBytes`, `burstBytes`, etc.) so that “highest quota wins” as described in the technical overview. Removing a node configuration from a users stores a tombstone policy (`maxBytes = 0, tombstoned = true`) that propagates until all peers observe it.
- **Quota application loop:** `ReplicationController` consumes the merged `managedNodes` view. Every evaluation cycle it calculates the effective quota for each `replicatedUser` by folding over all policies that mention that user and retaining the maximums. Because the entries live inside `UserData`, the user-data merge flow automatically propagates new quotas or removals to every participating node. |

### 3.4 Link Layer (`src/link/`)

| Name               | Kind  | Responsibility                                                                                                                                                                                                                                                                                               |
| ------------------ | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `LinkBlock`        | class | Decrypted representation of a link-layer block. Contains `referencedLinkBlocks`, `referencedLeafBlocks`, and an opaque `data` payload (encrypted at the data layer). Methods: `encrypt(linkKey)`, static `decrypt(linkKey, storageBlock)`.                                                                   |
| `LinkTraversal`    | class | Traverses the `LinkBlock` graph starting from a root, collecting all reachable `StorageBlock` CIDs. Used by garbage collection. Methods: `collectReachable(rootCid)` → `Set<CID>`.                                                                                                                           |
| `GarbageCollector` | class | Coordinates garbage collection for a user. Publishes a `GarbageCollectionClaim`, waits, traverses the link graph, rebuilds `DataShard`s with only reachable blocks, and passes the result back for data-layer merging. Handles claim conflicts (sorted by version, timestamp, nodeId) and aborts gracefully. |

### 3.5 Data Layer (`src/data/`)

| Name               | Kind           | Responsibility                                                                                                                                                                                                                                                                                                                               |
| ------------------ | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UserData`         | class          | The root data object. References subsystem roots: file tree root `Directory`, contacts, node map, etc. Carries a `VectorClock`. Methods: `encrypt(dataKey)`, static `decrypt(dataKey, linkBlock)`.                                                                                                                                           |
| `DataObject`       | interface/base | Common interface for all versioned objects in the data layer. Has a `clock: VectorClock` and a `merge(other): DataObject` method. Subsystem-specific objects implement their own merge semantics.                                                                                                                                            |
| `ConflictResolver` | class          | Generic tree-traversal merge engine. Given two `UserData` roots, walks the object graph comparing vector clocks. Delegates to subsystem-specific merge logic when conflicts are found. Stops traversal when clocks are ordered.                                                                                                              |
| `Directory`        | class          | A named directory node. Contains a map of child names to `File` or `Directory` references. Implements `DataObject`. Merge: union of children; per-child recursive merge on conflict with stable tie-breaking on nodeId + timestamp to avoid oscillation.                                                                                     |
| `File`             | class          | File metadata (name, size, mime type, modification time) plus a list of chunk references. Implements `DataObject`. Merge strategy: last-writer-wins on metadata; chunk list conflicts emit deterministic "conflict siblings" (e.g., `filename (node-<id>-<clock>)`) so both versions stay accessible until the user resolves them in the UI. |
| `FileChunker`      | class          | Stateless utility. `chunk(stream, blockSize)` → sequence of `StorageBlock`s. `reassemble(chunkRefs)` → readable stream. Handles content-defined chunking for deduplication in future versions.                                                                                                                                               |

#### Conflict Resolution Details

- **Files:** The `ConflictResolver` walks the directory tree and, when it encounters concurrent `File` objects, produces both versions plus metadata describing the conflict origin. The React frontend surfaces these conflicts, and once a user chooses a winner the losing chunk references are garbage-collected via the link layer.
- **NodeTrust:** Because NodeTrust entries live inside `UserData`, the same vector-clock merge applies. Concurrent edits to the same nodeId are resolved deterministically: higher clock wins; equal clocks fall back to lexicographic ordering of nodeId to keep merges stable. Deletes outweigh adds by setting a `removed` flag with a higher clock, ensuring that revoked trust propagates even if another node is offline. The entry is remove once the `UserData` of all nodes shows a higher clock than the one of the `NodeTrust`

### 3.6 Network (`src/network/`)

| Name                 | Kind               | Responsibility                                                                                                                                                                                                                                                                            |
| -------------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `createNode()`       | function           | Factory that configures and starts a libp2p node with Helia. Sets up transports (TCP, circuit relay), encryption (Noise), muxing (Yamux), DHT (Amino, LAN), mDNS, identify, autoNAT, ping. Returns `[datastore, blockstore, heliaNode]`.                                                  |
| `SyncProtocol`       | constant + helpers | Protocol ID (`/p2p-sync/v1/`), protobuf registry, and `encode`/`decode` helper functions for sync messages.                                                                                                                                                                               |
| `UserNodeEntry`      | class (internal)   | Tracks a single remote node: multiaddrs, dial state, retry backoff, active stream. Runs an async processing loop: dial → setup message handlers → exchange sync messages. Serializable for persistence.                                                                                   |
| `UserNodeController` | class              | Manages the set of `UserNodeEntry` instances for all users this node serves. Forms the per-user dense network. Coordinates connection attempts, reacts to node discovery events, and delegates incoming protocol streams. Implements `Component` for lifecycle management.                |
| `DHTPublisher`       | class              | Async loop that periodically calls `aminoDHT.provide(userCid)` to advertise this node as a provider for each user.                                                                                                                                                                        |
| `DHTDiscovery`       | class              | Async loop that periodically calls `aminoDHT.findProviders(userCid)` and feeds discovered nodes into `UserNodeController`.                                                                                                                                                                |
| `SyncMessageHandler` | class              | Processes incoming sync protocol messages on a stream. Dispatches `WantUserDatas` → replies with `HaveUserDatas`; processes incoming `HaveUserDatas` → updates local `StorageUserData` set and triggers replication if needed. Handles `WantNodeList` / `HaveNodeList` for node exchange. |

### 3.7 User (`src/user/`)

| Name                 | Kind               | Responsibility                                                                                                                                                                                                                                                                                                                                              |
| -------------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UserKeysManagement` | module (functions) | `loadOrCreateUserKeys()`: reads key material from disk or generates fresh keys and persists them. Uses protobuf serialization.                                                                                                                                                                                                                              |
| `UserController`     | class              | Manages the set of users this node is configured to replicate for. Loads user configurations from the datastore on startup. Provides `addUser(publicKey, keys)`, `removeUser(publicKey)`, `getUsers()`. Emits events when the user set changes so other components (DHT publisher, replication controller) can react. Manages the known keys for each user. |
|                      |

**Local Node Config:** Every node persists a `NodeConfigAccess` list alongside its user configuration (owned by `UserController`). This list states which users are allowed to configure the node. Mutations happen locally only and require a physical operator on the device, preventing remote takeover.

### 3.8 Component System (`src/components.ts`)

| Name                  | Kind      | Responsibility                                                                                                                                                                                  |
| --------------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Component`           | interface | Lifecycle interface: `initialize()`, optionally `stop()`. All major controllers implement this.                                                                                                 |
| `InstanceComponents`  | interface | Components available immediately: `libp2p`, `dataStore`, `blockStore`.                                                                                                                          |
| `LifecycleComponents` | interface | Components that require initialization and depend on `InstanceComponents`: `userController`, `userNodeController`, `replicationController`, `garbageCollector`, `dhtPublisher`, `dhtDiscovery`. |
| `Components`          | type      | Union of `InstanceComponents & LifecycleComponents`. Passed to constructors for dependency injection.                                                                                           |

### 3.9 Entry Point (`src/index.ts`)

| Name     | Kind     | Responsibility                                                                                                                                                                                                                                                               |
| -------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `main()` | function | Orchestrates startup: (1) load/create user keys, (2) create libp2p/Helia node, (3) construct `InstanceComponents`, (4) construct and initialize `LifecycleComponents` in dependency order, (5) register protocol handler, (6) start async loops (DHT publish, DHT discover). |

### 3.10 Frontend (`apps/frontend/`)

| Component/Module        | Responsibility                                                                                                                                                                                        |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AppShell`              | React Router layout with authenticated routes, global status toasts, and a WebSocket client that streams sync/replication events from the Express backend.                                            |
| `FileBrowser`           | Displays the directory tree, file metadata, and conflict badges. Supports drag-and-drop uploads/downloads by calling backend REST endpoints.                                                          |
| `NodeTrustPanel`        | CRUD UI for trusted nodes. Edits are optimistic in the UI, then persisted via backend API calls that mutate the `NodeTrustSet`. Shows quorum/replication status per node.                             |
| `ConflictCenter`        | Lists active file and NodeTrust conflicts surfaced by the backend. Allows the user to choose winners, rename conflicted files, or revoke nodes.                                                       |
| `Settings & Onboarding` | Guides a new device through key import/creation, surfaces QR codes for linking devices, and renders live node/network diagnostics.                                                                    |
| `State Management`      | React Query + Zustand store that caches user data snapshots, tracks pending mutations, and reconciles responses from the backend to keep the UI reactive without reading directly from the datastore. |

### 3.11 Backend (`apps/backend/`)

| Module             | Responsibility                                                                                                                                                         |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `server.ts`        | Express app bootstrap. Wires authentication middleware (local user keys / session tokens), JSON body parsing, and error handling.                                      |
| `fileRouter.ts`    | REST endpoints for browsing directories, uploading/downloading blocks (streamed), and triggering manual resync/garbage-collection passes.                              |
| `trustRouter.ts`   | REST endpoints for listing and mutating NodeTrust entries. Enforces validation (quota ceilings, signature requirements) before delegating to `NodeTrust` domain logic. |
| `eventsGateway.ts` | WebSocket server that streams replication progress, conflict notifications, and node-online/offline events to the frontend.                                            |
| `auth.ts`          | Handles device linking tokens, session refresh, and ensures only authorized frontends can mutate user data.                                                            |

---

## 4. Key Design Decisions

1. **Layer separation is enforced by directory structure.** Code in `storage/` must not import from `data/` or `link/`. The layers communicate through well-defined interfaces (encrypted blocks going down, decrypted objects going up).

2. **Dependency injection via `Components`.** All controllers receive a `Components` object (or a `Pick<>` subset). This avoids circular dependencies and makes every class testable with mocks or in-memory implementations.

3. **Protobuf for all serialization.** Both wire messages and persisted structures use protobuf. This gives schema evolution, compact binary encoding, and cross-language compatibility for free.

4. **Vector clocks are pure, immutable data structures.** The `VectorClock` class has no side effects, making it straightforward to unit test the core conflict resolution logic independently of networking and storage.

5. **Async processing loops with backoff.** Node connections, DHT operations, and replication checks all run as `async` loops with exponential backoff on failure. This provides resilience without complex state machines.

6. **Existing code is kept and reorganized.** Current files (`createNode.ts`, `UserNodeController.ts`, `UsersController.ts`, `userKeysManagement.ts`, `syncProtocol.ts`, `utils.ts`, `mdns/`) map cleanly to the proposed structure. The main refactoring is splitting the monolithic `UserNodeController.ts` into `UserPeerManager`, `UserPeerEntry`, `UserNodeController`, and `SyncMessageHandler`, and moving files into their respective layer directories.

7. **NodeTrust is first-class replicated data.** Trust updates are part of the signed `StorageUserData`, travel through the same replication pipeline as files, and rely on vector clocks plus deterministic merge rules so that revocations and quota changes never get lost.
