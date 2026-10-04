# W2071958655 — Ivy: A Read/Write Peer-to-Peer File System (Mazières, Kaminsky, Kaashoek, Witchel — OSDI 2002)

A fully decentralized multi-user read/write file system layered on a DHT (DHash, from MIT Chord). No dedicated servers, no central authority — every participant is a peer and maintains its own log.

## Contributions relevant to the topic

1. **Log-per-participant architecture**: each participant (user/host) writes an append-only log of NFS-like mutating operations; the file system state is *derived by replaying/merging all logs*. This is a radically different metadata design from tree-based replication: there is no mutable namespace state to synchronize — only logs, which are trivially mergeable.
2. **Immutable log records + version vectors**: log records are hash-named (content-addressed, forming a chain/DAG like a blockchain); each record carries a version vector. *Conflict detection is purely a function of log merge*: two updates conflict iff neither record is causally after the other — exactly the causality-based detection the topic requires, and it cannot have "false" conflicts the way timestamp schemes can.
3. **Clean separation of convergence and resolution policy**: Ivy guarantees deterministic merged state and version-vector-based conflict *detection* for free, then exposes version information to *application-specific conflict resolvers*. Conflicting updates are kept and surfaced rather than silently discarded — structurally compatible with the keep-both requirement.
4. **Conflict avoidance tools for applications**: inode-level "leases"-like hints (e.g., holding the inode number so concurrent creates don't collide), and mechanisms for applications to avoid frequent conflicts — a pragmatic layer missing from most optimistic systems.
5. **Fault tolerance by construction**: data lives in DHash with erasure-coded replication across peers; any surviving replica keeps the file system usable. Integrity is guaranteed cryptographically without trusting other users.

## Relation to topic characteristics

- **Multi-master**: any peer writes to its own log at any time; no coordinator, no primary — the purest multi-master design surveyed.
- **Eventual consistency**: logs merge deterministically (the merge of the same log set yields identical state), so replicas converge automatically once logs propagate.
- **Conflict resolution**: detection is exact (causality via version vectors over the log DAG); resolution is delegated — the paper's weakest point for this topic is that rename/move semantics and content preservation are left to resolvers, with no namespace model à la Tofu/Unison.
- **Scalability/fault tolerance**: built for many peers on a DHT — overkill for ≤10 nodes, but the *log-as-source-of-truth* idea maps directly onto a small-cluster design: each node's local mutation log + content-addressed records = the Ori-style commit DAG with explicit conflict metadata.
- Key lesson vs. the candidate architecture: Ivy validates "operations log + version vectors + content addressing" as an alternative to CRDT state replication, at the cost of needing a resolution layer for conflicts the log merge cannot decide.