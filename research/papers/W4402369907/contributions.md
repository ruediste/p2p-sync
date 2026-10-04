# Approaches to Conflict-free Replicated Data Types

This tutorial/survey by Paulo Sérgio Almeida gives the conceptual foundation for building an eventually consistent, multi-master system like the target distributed file system. It explains how replicas can accept updates locally at any node (multi-master), stay available during partitions and offline periods (fault tolerance), and still converge deterministically once they reconnect (eventual consistency).

Key contributions for the file-system design:

- **Operation-based vs. state-based CRDTs.** Op-based CRDTs (prepare/effect phases) need small messages but require reliable, exactly-once causal broadcast — hard to guarantee between nodes that go offline. State-based CRDTs ship full states and merge via a join-semilattice; they are idempotent and tolerate lossy, ad-hoc anti-entropy, which fits intermittent connectivity. This trade-off analysis directly informs the sync-protocol choice.
- **Delta-state and pure op-based CRDTs** as the "best of both worlds": small incremental messages while retaining the idempotent, unreliable-channel tolerance of state-based designs.
- **Concrete data structures** for concurrency semantics: the observed-remove (add-wins) set, where elements carry unique operation ids and removes cancel only the adds observed at the removal site; concurrent adds "survive". The Multi-Value Register keeps all concurrently written values — exactly the "keep both versions" semantics required for conflicting file contents.
- **Clarifications that prevent design bugs**: operations need not commute; state mutators must be *inflations*; role of causal stability for garbage collection (compacting the PO-Log once operations become stable).
- **Identity management discussion** toward scalability, relevant even at ~10 nodes for generating unique, compact identifiers per file/entry.

Essentially: the taxonomy and semantic toolkit (OR-set, MV-register, causal contexts) from which file-system metadata structures (directory tree, entries, tombstones) should be composed.
