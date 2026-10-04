# W3206444683 — A Highly-Available Move Operation for Replicated Trees

Kleppmann, Mulligan, Gomes, Beresford. IEEE TPDS 2021. DOI: 10.1109/tpds.2021.3118603

## What the paper teaches

This is arguably the single most directly relevant paper for the directory
rename/move conflict concern of our research topic. It shows, empirically and
formally, how to build a replicated tree (i.e., a distributed filesystem
directory hierarchy) that supports arbitrary concurrent move operations without
coordination, while never duplicating or losing nodes and never introducing
cycles.

### Motivation and bug report (§2)

The authors experimentally demonstrated real bugs in Google Drive and Dropbox
under concurrent moves between offline clients:

- **Cycles**: two concurrent moves A→child-of-B and B→child-of-A can form a
  cycle; Google Drive entered a permanent error state (refused to sync,
  required manual repair), Dropbox silently duplicated nodes.
- Prior work (Najafzadeh et al.) claimed no filesystem can support an
  unsynchronised move without loss or duplication; this paper refutes that
  claim.

### The algorithm (§3)

- **Single uniform operation**: everything (create, delete, rename, move) is
  expressed as one `Move t p m c` — at timestamp *t*, node *c* becomes a child
  of parent *p* with metadata *m* (filename in a filesystem context). Create =
  move a fresh node ID; delete = move to a designated trash node; rename = move
  with same parent, new metadata. The tree is simply a set of
  (parent, meta, child) triples with globally unique node IDs (UUIDs).
- **Timestamps** must be globally unique and totally ordered (e.g. Lamport
  timestamps). Conflict resolution is timestamp-based: concurrent moves of the
  same node resolve as "last writer by timestamp order wins" — but defined
  deterministically via sequential semantics, not wall clocks.
- **Cycle prevention**: `do_op` ignores a move if the moved node is an ancestor
  of the destination (or the destination itself). Because safety depends on the
  tree state, an operation previously unsafe may become safe later, so **all**
  operations must be kept in the log (until causally stable), even ignored ones.
- **Order independence / convergence**: the core trick is the **undo-do-redo**
  cycle with a log kept in descending timestamp order. When an operation
  arrives with a timestamp older than logged operations, earlier operations are
  undone, the new op is applied, and later ops are redone — resulting in a tree
  *as if* operations had been applied in increasing timestamp order, regardless
  of arrival order. This yields commutativity and convergence.

### Proofs (§4)

Formally verified in Isabelle/HOL:

- Invariants: every node has at most one parent (unique-parent), the graph is
  acyclic (a forest — multiple roots allowed, e.g. separate trash root).
- Convergence: applying the same *set* of operations in any order yields the
  same state (`apply_ops_commutes`), integrated with the Gomes et al. strong
  eventual consistency framework for CRDTs.
- An executable hash-map variant is proven equivalent to the set-based
  specification, and exported to Scala for geo-replicated performance
  evaluation (single-op apply cost tens of µs, growing with log length).

### Practical extensions (§3.6–3.7)

- **Optimisation for creates**: creation ops can skip undo-do-redo (proved
  safe); deletions cannot, since deletion may re-enable previously ignored
  moves.
- **Trash/GC and log truncation**: operations and trashed subtrees can be
  discarded once *causally stable* (min of max timestamp seen from each replica
  over FIFO links); essential for bounding memory in an offline-capable system.
- **Filename collisions**: concurrent creation of the same name in a directory
  is resolved by *keeping both nodes* and disambiguating filenames (e.g. append
  replica ID) — exactly the "preserve both versions, don't merge" philosophy of
  our topic.
- **Hardlinks/symlinks**: leaf nodes reference file inodes rather than holding
  data, so the same inode can appear in multiple places; symlinks are leaves
  containing paths.
- **Sibling ordering**: an explicit child order can be layered on with a list
  CRDT (RGA/Logoot) whose element IDs go into the move metadata field.

## Relation to the topic's characteristics

- **Multi-master**: fully so — every replica generates and applies operations
  independently, no central server, no consensus, no locking; any non-crashed
  subset of replicas can continue working.
- **Conflict resolution**: the primary mechanism for directory
  rename/move conflicts. Deterministic timestamp-based resolution of concurrent
  moves; cycle-creating combinations resolved by dropping the higher-timestamp
  op; name collisions resolved by preserving both. Content conflicts are out of
  scope in the paper (tree structure only), which fits our requirement of
  handling file-content conflicts at a different layer.
- **Eventual consistency**: convergence is proven (strong eventual
  consistency / commutativity), i.e. all replicas converge to the same tree
  given the same set of operations, independent of order.
- **Fault tolerance**: network partitions and arbitrary node crashes are
  tolerated by design; underlying transport must detect/retransmit lost
  messages and suppress duplicates (e.g. TCP, or per-peer queueing).
- **Scalability**: complexity is concentrated in the undo-do-redo path over the
  op log; log length and apply cost grow with operation volume, mitigated by
  causal-stability truncation — fine for ≤10 nodes, though per-op cost is
  super-constant, so large operation volumes need attention.

## Caveats / gaps for our design

- Addresses only the *tree structure* (directory metadata); file content
  synchronization and its conflict handling must come from elsewhere.
- Rename metadata is inside the move op, so concurrent rename + move of the
  same child resolves by timestamp — a subtlety worth studying for user
  experience (one rename may be silently discarded).
- Delete-vs-move races are handled but only via the full undo-do-redo cost; a
  production system would need the causally stable GC discipline described.
- The log is the source of truth for convergence: a cold-joining or
  long-offline node needs to obtain the full (compacted) op history of the
  tree, which the paper does not detail (anti-entropy/sync protocol out of
  scope).
