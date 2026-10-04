# Delta State Replicated Data Types

Almeida, Shoker, and Baquero introduce δ-CRDTs, which reconcile the two CRDT dissemination styles: small incremental messages (like op-based) over unreliable, non-causal channels (like state-based). This is the core sync mechanism needed for a file system whose nodes connect intermittently.

Key contributions for the file-system design:

- **δ-mutators.** Each update computes a *delta-state* — the minimal join-semilattice fragment capturing the update's effect — returned alongside the local state change. Deltas are buffered and shipped individually or grouped, then *joined* into remote states. Because deltas are join-idempotent states, duplicated or out-of-order delivery is harmless: no exactly-once delivery guarantee is needed, unlike op-based CRDTs. This makes anti-entropy between offline nodes robust and simple.
- **Anti-entropy algorithms.** A basic algorithm proves convergence when all deltas are eventually delivered and joined; a refined one preserves *causal consistency* by grouping deltas into causal *delta-intervals* and applying a *causal delta-merging condition*, so effects are applied only after their causes — important for metadata like "create then rename" sequences.
- **Portfolio of δ-CRDTs** usable as file-system building blocks: GSet, 2PSet, Add-Wins LWW Set, PNCounter, Lexicographic Counter, Enable-Wins Flag, Multi-Value Register (retains concurrent writes — matches the "preserve both conflicting contents" requirement), Add-Wins and Remove-Wins Sets, and a generic **causal δ-CRDT map** composed over dot stores (version-vector-like per-entry causal contexts). A map of entries with causal contexts is a natural model for directory contents with concurrent creates, deletes, and renames.
- **Delta-interval batching** keeps message sizes small even after long offline periods, addressing fault tolerance and state growth at ~10 nodes.

Reference C++ library accompanies the paper.
