# Replication, History, and Grafting in the Ori File System (SOSP 2013)

Ori is the closest concrete blueprint for a fully peer-to-peer, offline-capable file system: every device holds a full repository replica, synchronization is pairwise and opportunistic, and there is no server or central authority — multi-master by construction. Its core idea is to make file system history the foundation of everything (sync, conflict handling, backup, repair).

Concrete mechanisms worth adopting:

- **Content-addressable storage (CAS) with SHA-256** holding immutable objects: Commit (root hash, parent commit hashes, time, user), Tree (directories), Blob/LargeBlob (files, with sub-file chunking for dedup and bandwidth savings). Commits form a Merkle tree / Git-like DAG, so a snapshot is cheap and replicas compare by exchanging hashes only.
- **Conflict handling via three-way merge**: divergence is detected by comparing commit DAGs; because history is kept, Ori always has a "merge-base" ancestor — something Coda and version-number schemes cannot provide. Many conflicts (including directory operations, since directories are Tree objects in the same DAG) resolve automatically; unresolvable ones are exposed to the user with `:base` and `:conflict` files, in VCS fashion.
- **orisync daemon**: mDNS/Zeroconf discovery plus ~5-second encrypted announcements carrying file-system UUID and head commit; any pair of reachable replicas converges, giving eventual consistency even with intermittent connectivity.
- **Fault tolerance**: any single surviving replica suffices; corrupted objects are detected by hash mismatch and repaired by fetching the correct object from peers (background fetch). Distributed fetch lets nearby peers act as object caches. "Carrier" vs. default replication policies order snapshot vs. history transfer for slow links.
- **Grafting**: cross-file-system subtree copy that preserves commit history via graft commit records (source fsid, path, commit hash), enabling later three-way reconciliation of copied directories — directly useful for handling moves/copies across replicas.

Background fetch (synchronous conflict check first, bulk data on demand) is a practical pattern for making sync appear instant. Evaluation reports ~30 ms merges of two diverged commits.
