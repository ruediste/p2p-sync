# Communication Timestamps for File System Synchronization (Cox & Josephson, 2001)

A theory-plus-implementation paper on cheap conflict detection for peer-to-peer file synchronization (the Rumor model: replicas propagate changes along arbitrary indirect paths, no central server) — the exact multi-master, eventual-consistency setting of this research topic.

Key contributions:

- **Formal conflict theory**: reformulates Parker et al.'s version-vector conflict detection as a partial order over per-file modification histories. Two versions of a file are safe to merge by copying iff one history is a prefix (subset) of the other; conflict at a synchronization point iff the two parents are incomparable. Equivalence with the partition-graph formulation is proven.
- **Version vectors recap**: per-file vector of (replica, sequence-number) entries, O(R·F) storage; domination = componentwise ≥; conflict resolution merges histories by taking the elementwise maximum, which correctly records that both conflicting branches were consumed (fixing Parker's m/x ambiguity where earlier resolutions cause spurious re-conflicts).
- **Communication timestamps (the main novelty)**: instead of each replica tracking a full version vector per file, each file stores a vector of *synchronizations* it has participated in. Only files actually involved in a sync pay the R-entry cost; untouched files cost O(1), giving O(R + F) expected storage versus O(R·F). Proofs of correctness for the scheme and several hybrids (e.g., version vectors plus communication timestamps for deletes) are given.
- **Deletion conflicts**: communication timestamps enable a novel way to distinguish "deleted while I was offline" from "never existed", a notoriously tricky case in offline-first file systems.
- **Tra implementation**: a practical sync tool built on these methods, with experience-based pros/cons of each scheme and applications (home-dir maintenance, OS installs, offline work).

Relation to the topic: provides the metadata data structures a multi-master DFS needs per file/replica to detect concurrent updates after reconnection, with explicit storage-complexity guidance suited to ~10 replicas, and explicit treatment of resolution bookkeeping and deletes. It does not address directory rename/move conflicts specifically — pair with Syncpal-style move handling.
