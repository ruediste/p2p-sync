# W4416409788 — CrossFS: CRDT-Based Metadata Synchronization in a Cross-Domain File System

Ke et al., ACM Transactions on Storage 22(2), March 2026. PDF in this directory.

## What it teaches

A recent, concrete (non-CRDT-native) distributed filesystem that uses CRDTs
exactly where our topic needs them: **metadata synchronization**.

- CrossFS (CFS) is a FUSE filesystem for cross-domain (multi-administrative-
  domain, WAN, partition-prone) environments, using CRDTs to achieve strong
  eventual consistency of metadata while staying highly available.
- Metadata is stored in a **sparse table** managed by an **LSM-tree
  (RocksDB)**; batching and async commits reduce write amplification; a
  **Hybrid Tree** index optimizes metadata queries. rpc-based sync across
  domains.
- Claims vs CephFS/GlusterFS: +33.9% metadata throughput, −33.4% query
  latency, −30.7% write amplification.

## Relation to the topic

- Validates the architecture our topic implies: CRDT layer for
  directory/metadata ops (create/rename/delete) on top of per-node storage —
  showing it works in production-style implementations with real storage
  engines (RocksDB/FUSE), not just toy demos.
- Its conflict-resolution for concurrent renames/moves is coarse compared to
  Kleppmann's tree-move algorithm (LWW-flavored SEC); it does not solve the
  directory-move cycle problem. Use it as an engineering blueprint for the
  *persistence/serialization/sync* layers of a small multi-master FS, while
  the tree-move CRDT (W3206444683 + W4286307986) supplies the namespace
  conflict logic.
- Cross-domain rather than peer-to-peer/offline-laptop orientation, but the
  partition-tolerance and SEC goals match.
