# Designing a Planetary-Scale IMAP Service with CRDTs (Jungnickel, Oldenburg, Loibl, OPODIS 2017)

This paper is an end-to-end case study in building a multi-leader, eventually consistent replicated service, including pitfalls of using a shared file system (GlusterFS) as the replication substrate, which is directly relevant to a distributed file system design.

Key teachings:

- Architecture: worker/storage nodes accept state-changing requests at any replica (multi-master), mutate local state first, then asynchronously broadcast operations downstream. Availability under partitions is chosen over strong consistency (CAP).
- Data model: user mailbox state is modeled as an OR-Set ("structure") of value-tag pairs, using grow-only tag generation with per-replica unique UIDs to deduplicate concurrent identical appends. Counter-intuitive conflict interactions (e.g., expunge racing concurrent delete) are handled by tombstone tags in an add-wins/delete-wins split — a concrete pattern for decomposing operations into tags so concurrent operations compose predictably.
- Verification: the CRDT model is proved convergent in Isabelle/HOL (extending Gomes et al.'s CRDT/network framework), and the implementation is constrained to match the proof — a practical methodology for getting correctness of conflict resolution right.
- Transport: reliable causal-order broadcast between replicas; per-update fsync of a log file enables crash recovery (fault tolerance), and state can be reconstructed from the log.

Relation to topic: directly demonstrates multi-master, eventual consistency, and fault tolerance; provides concrete CRDT encoding, replication protocol, and durability techniques for a small set of geo-distributed nodes, though it does not tackle hierarchical directory rename/move conflicts specifically.
