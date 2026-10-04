# Controlled Conflict Resolution for Replicated Documents (Martin, Ahmed-Nacer, Urso, 2012)

This paper proposes a layered architecture for building optimistic-replication systems where conflict behavior is a design choice rather than an accident, which is directly applicable to a multi-master distributed file system.

Key teachings:

- Layered data types: a bottom replication layer (state-based or operation-based) owns inter-replica communication and merging; adaptation layers sit on top, each wrapping an inner layer's data and enforcing a constraint (e.g., schema validity, tree structure). A layer's lookup is a deterministic computation over inner-layer state, so if the inner layer is eventually consistent, the composed type is too — a compositional proof of convergence.
- Adaptation layers can be non-incremental (recompute on lookup, suited to state-based sync) or incremental (observer-pattern updates, suited to operation-based sync), letting the designer trade CPU for bandwidth.
- Orphan handling in replicated trees: a tree is represented as a path set over an underlying CRDT set. When concurrent operations create orphan paths (e.g., a parent removed while children remain), four explicit reconciliation policies are defined: skip (drop orphans), reappear (recreate ancestors as ghosts), root (move orphans to a lost-and-found/root), compact (merge orphan subtree under nearest surviving ancestor). Incremental implementations of reappear (ghost marking/unmarking, recursive leaf cleanup) and root are given.

Relation to topic: this is one of the most concrete treatments of directory-rename/move-style conflicts — parent deletion vs. child updates — exactly the problem area the target system cares about. Multi-master and eventual consistency are inherent in the design; scalability is addressed via layer complexity analysis, though it targets documents rather than full file-system semantics (no file contents, timestamps, or fault-tolerance machinery).
