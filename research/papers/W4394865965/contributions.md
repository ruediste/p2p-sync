# W4394865965 — Extending JSON CRDTs with Move Operations

Hu, Sun, Zhao (2024, arXiv/OpenAlex). PDF: `papers/W4394865965/paper.pdf`.
Prototype: https://github.com/LiangrunDa/AutomergeWithMove (Go, simplified
Automerge). Authors plan to integrate into the Rust Automerge library.

## What the paper teaches

The first serious engineering attempt to adapt Kleppmann's tree-move algorithm
(W3206444683) to a *real* JSON CRDT (Automerge's OpSet), where tree children
are ordered lists or maps, not unordered sets. This is the gap between the
theory paper and building an actual filesystem-like document store.

### Key contributions

- **Move ops in JSON trees**: the existing algorithms (Kleppmann tree, list
  move, Nair's tree) treat children as unordered; JSON needs moves between
  maps (keyed children) and lists (ordered children), with interactions with
  concurrent key overwrites/deletes of the moved object's parent reference.
- **Validity tracking**: an `UpdateValidity` pass tracks whether a move
  operation's source/destination remains valid as concurrent operations
  overwrite or delete the edges involved.
- **Optimisations — the practical experience nuggets**:
  - *Batch updating*: when importing a batch of remote ops, apply them
    together instead of one-by-one undo-do-redo per op (huge win; the naive
    per-op undo-do-redo is the main cost).
  - *Lifecycle tracking*: bounds the extra bookkeeping the move algorithm
    adds to non-move operations. Without it, *every* create/delete/overwrite
    of an object pays move-algorithm overhead even if the app never uses
    moves — measured convergence-time overhead for move-support vs disabled
    is small after optimisation.
- **Correctness testing practice**: Jepsen-inspired randomised concurrent op
  generation across actors, checking convergence (same state after merge).
  This is the accepted lightweight alternative to Isabelle proofs.
- Notes Automerge's current move support is deletion+reinsertion — which
  produces exactly the duplication bug (b) from the tree-move paper.

## Relation to the topic

- Directly applicable to representing a filesystem namespace as a JSON CRDT
  (directory = map, files = values) with Kleppmann-style moves; shows the
  concrete costs (per-op overhead, batch import, validity tracking) that a
  real implementation must budget for.
- Demonstrates that the undo-do-redo machinery is the pain point and must be
  optimised (batching) — corroborating the Anjana et al. (W4286307986)
  measurements.
- Still tree/document structure only: file content conflicts remain a
  separate layer; no offline-GC story.
