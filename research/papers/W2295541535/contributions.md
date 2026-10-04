# W2295541535 — Optimistic Replication (Saito & Shapiro, ACM Computing Surveys 2005)

The canonical survey of optimistic replication. It organizes the entire design space into a modular architecture with six functional layers, which serves as a checklist for any multi-master, offline-capable system design.

## Contributions relevant to the topic

1. **Six-layer architecture (the "optimistic replication stack"):**
   - **Application semantic support** (optional): application-supplied conflict detection/resolution (Bayou merge procedures are the prime example).
   - **Consistency unit**: whole-object vs. sub-object (region/state-partition) granularity — for a file system the natural unit is per-file/per-directory-entry, not per-volume.
   - **Update propagation**: push vs. pull vs. epidemic; transaction-log shipping vs. state transfer; pair-wise (anti-entropy) vs. multicast.
   - **Encoding of update information**: operation transfer (a log of mutating operations) vs. state transfer (dirty objects, version histories). Analyzes the trade-off: state transfer is more robust to message loss and reconfiguration; operation transfer is bandwidth-efficient.
   - **Conflict detection and resolution**: version vectors / vector clocks / timestamps / application semantics; resolution as overwriting, merging, or deferring (manual, user-driven — matching the keep-both requirement).
   - **Replica placement and reconciliation scheduling**.
2. **Taxonomy of conflict types**: write-write, read-write, and — crucially — *semantic* conflicts (updates individually valid but jointly invalid, e.g., moving a directory into its own subtree). This taxonomy anticipates the namespace-structural conflicts that are the topic's primary concern (directory rename/move).
3. **Formal conflict detection**: careful treatment of version vectors, their variants (dotted version vectors, pairwise consistency vs. causality), and why timestamps are unsafe under clock skew — corroborates the "use causality, never wall clock" lesson.
4. **Propagation design patterns for intermittent connectivity**: the survey explicitly covers pair-wise anti-entropy with ordering/closure constraints (Bayou), epidemic rumor spreading, and tolerance of unidirectional/occasionally-connected links — exactly the connectivity model of the target system.
5. **Reconciliation as an explicit phase**: separates "detect divergence" from "resolve divergence" and catalogs resolution policies, including *preserve both conflicting versions* rather than last-writer-wins.

## Relation to topic characteristics

- **Multi-master / eventual consistency**: the entire survey assumes any-replica writes and convergence without central authority; it is the conceptual umbrella under which Bayou, Coda, Unison, and the CRDT literature all sit.
- **Fault tolerance / scalability**: state-based transfer and pairwise anti-entropy survive node loss and message loss; scale analysis confirms that for a small replica set the O(R·F) version-vector cost is acceptable, and simpler designs (full replication) beat sharding machinery.
- **Conflict resolution**: its semantic-conflict category and its recommendation to defer unresolved conflicts to the user directly support the keep-both requirement for file contents and the careful treatment of move/rename conflicts.

Essentially: this paper is the map; Coda/Bayou/Unison/Tofu/CRDT papers are the territories it points to.