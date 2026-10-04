# CRDTs: An Overview

Shapiro's overview organizes CRDT research and practice into three viewpoints — the application developer, the system developer, and the CRDT developer — making it a useful orientation document for designing a distributed file system based on optimistic replication.

Key takeaways for the file-system design:

- **Principled conflict handling.** CRDTs replace ad-hoc, error-prone reconciliation of concurrent updates (as in Dynamo-style read/write stores) with datatype-specific, deterministic merge semantics. A file-system tree can be expressed as CRDT compositions: sets/maps of entries (add-wins or observed-remove semantics for create vs. delete races), MV-registers for file contents (preserving all concurrent versions rather than merging them — matching the required "keep both versions" behavior), and unique per-operation identifiers to distinguish concurrent writes.
- **Eventual consistency by construction.** Replicas that have received the same updates converge to equivalent state regardless of delivery order, so nodes that operate independently offline and later reconnect (multi-master) will converge without a central coordinator.
- **Availability and fault tolerance.** CRDTs allow immediate local updates even under partitions and node failures, with asynchronous anti-entropy propagation when connectivity returns — the exact operating mode of the target system.
- **Scalability framing.** While CRDTs are often motivated by geo-replicated, Internet-scale deployments, the overview clarifies the cost model (metadata growth, semi-lattice state sizes), which helps judge what is unnecessary overhead for a small (≤10 node) deployment and which parts (e.g., causal-history encoding, delta propagation) are still worth adopting.
- **Developer-role separation** suggests structuring the implementation as a reusable CRDT substrate plus a file-system-specific layer.

Based on the abstract only (no full text).
