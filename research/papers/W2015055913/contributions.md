# W2015055913 — Merging Semantics for Conflict Updates in Geo-Distributed File Systems (Tao, Shapiro, Rancurel — Sysor 2015)

The conference-paper companion to the Tofu thesis (W4392269970): it states the formal FS model, the direct/indirect conflict taxonomy, and the merging semantics in condensed, citable form. Where the thesis gives exhaustive detail, this paper is the precise specification of the conflict-handling rules.

## Contributions relevant to the topic

1. **Formal file system model with hard links**: a complete FS state model (stat / data / names components of inodes, explicit directory and hard-link modeling). Hard links are included, which most synchronizer designs ignore — important for correctness of any namespace CRDT.
2. **Exhaustive direct and indirect conflict taxonomy**:
   - *Direct conflicts*: concurrent updates touching the same inode component (stat-stat, data-data, name-name).
   - *Indirect conflicts*: an update conflicts with the *causal consequence* of a concurrent rename — e.g., replica A renames a directory (changing a path), replica B concurrently updates a file *under the old path*. Detecting these requires causality tracking between the rename and the dependent operation, not just per-inode comparison.
   - This is the topic's primary concern (directory rename/move conflicts) handled with full rigor.
3. **Merging semantics that never discard updates**: for each conflict class, a deterministic resolution rule; file-content (data-data) conflicts are resolved by *preserving both versions* under distinct names — directly matching the requirement that content conflicts are kept, not merged.
4. **Correctness criterion**: defines when a merged state is "correct" relative to the FS invariants and the user-intent semantics, giving a principled standard for evaluating any reconciliation algorithm (including CRDT-based ones).
5. **Tofu system outline**: the rules are implemented in Tofu, showing they are practical, and evaluated on real traces showing such conflicts are rare — evidence for the optimistic design assumption.

## Relation to topic characteristics

- **Multi-master**: any replica performs updates; merging happens pairwise without a coordinator.
- **Conflict resolution**: the strongest available formal treatment of rename/move conflicts, including the indirect-conflict case that naive synchronizers miss; resolution is deterministic and preserves all concurrent updates.
- **Eventual consistency**: merging rules are defined so pairwise application drives all replicas to equivalent states.
- For the candidate architecture, this paper pins down the *state model* (layer 2) and the *conflict taxonomy* (layer 3); the remaining open question is unifying these rules with CRDT move semantics (winner-based vs. these resolution rules).