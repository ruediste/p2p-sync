# W2085631457 — What is a File Synchronizer? (Balakrishnan et al., ICDCS 1998)

A precise specification framework for file synchronizers (the family of tools: Unison, rsync-like tools, offline-caching file systems). It deliberately abstracts away from any particular implementation and defines *what the synchronization problem is* before any algorithm.

## Contributions relevant to the topic

1. **Two-phase decomposition**:
   - **Update detection**: decide, per file, whether each replica's version changed since the last common snapshot — via version metadata or by content comparison. This phase is independent of resolution policy.
   - **Reconciliation**: given detected changes, compute the actions to converge the replicas. Separating these lets detection use history (version vectors / DAG) while resolution policy stays pluggable — exactly the Unison algebra's later structure.
2. **Precise notion of a "conflict"**: a conflict is *not* merely two changed files; it is a set of per-replica updates to the same file that the propagation path cannot order. This makes "conflict" a property of the *detection model*, showing why richer history metadata (version vectors, DAGs) eliminates false conflicts — a key design lesson.
3. **Semantics guarantees a synchronizer should state**: what happens on rename, on directory restructuring, on deletes, on new files; what the user can rely on. Slogan-level guarantees ("propagate non-conflicting updates") are shown to be ambiguous. For the target system this defines the documentation/contract obligation: enumerate behavior for every conflict class.
4. **Preserve-both as a legitimate resolution**: the framework explicitly includes keeping both conflicting versions as a valid reconciliation action, rather than requiring a single winner — aligns with the keep-both content-conflict requirement.
5. **User-visible model**: replicas are knowingly divergent between syncs; synchronization is often under explicit user control. This legitimizes the target system's model of explicit sync sessions rather than pretending single-filesystem semantics.

## Relation to topic characteristics

- **Multi-master / eventual consistency**: defines the general pairwise reconciliation problem that any-to-any anti-entropy sessions solve; convergence is the stated goal of reconciliation.
- **Conflict resolution**: the update-detection/reconciliation split is the cleanest architectural separation available; it maps directly onto the planned design (snapshot/history diff → action plan → CRDT/merge apply).
- Minor caveat: the paper predates version-vector-based move handling and CRDTs, so its rename treatment is shallow compared to Unison/Syncpal/Tofu — its value is the *framework*, not the move mechanics.