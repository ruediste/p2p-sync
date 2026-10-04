# W4286307986 — An Efficient Approach to Move Elements in a Distributed Geo-Replicated Tree

Anjana, Chandrassery, Peri. IEEE CCGrid 2022; arXiv:2203.10285. (OpenAlex also
has a duplicate entry W4292974104 for the same paper — removed.)

## What the paper teaches

This is a direct optimization of Kleppmann et al.'s highly-available tree move
(W3206444683) targeting its main practical weakness: the cost of the
undo-do-redo cycle when operations arrive out of timestamp order. Same system
model: peer-to-peer asynchronous replicas, clients offline/partitioned, no
central server or consensus, insert/delete/move all expressed as a single
`move⟨ts, n, p⟩` with Lamport timestamps (replica id as tiebreaker; hybrid
logical clocks suggested as alternative).

### Algorithm

- Each tree node stores only its single parent and the timestamp of the last
  operation applied to it. A remote op is ignored if `op.ts < node.ts`
  (last-writer-wins by globally unique timestamp), which alone gives
  convergence: the winning parent for every node is the one with the highest
  timestamp.
- Instead of Kleppmann's undo-do-redo of the whole op log, cycles are handled
  by **one compensation operation**: on detecting a cycle (traversing from the
  new parent up to the root to check if the moved node is an ancestor), the
  algorithm finds the node in the cycle with the **highest timestamp** and
  moves it back to a previous parent — looked up in a `present_log`, an
  adjacency list storing the last *m* (constant, m=5 in experiments) previous
  parents per node — choosing a "safe" parent (not in the moved node's
  subtree). If no safe previous parent exists, the node is parked under a
  special **conflict node** (unmovable child of root) so the user can see the
  conflict and resolve it manually. The compensation op is propagated to all
  replicas.
- Delete = move to a `trash` node (child of root). Permanent delete, GC of
  `present_log`, and log truncation are left as future work; the trash can
  grow indefinitely (a known limitation).

### Proofs (§V)

Proof sketches (not mechanized, unlike Kleppmann's Isabelle proofs) of:
no duplication (single parent value), no cycles, no forest split, safety of
compensation, and convergence (same op set ⇒ same parent per node by
max-timestamp).

### Performance (§VI)

Golang/gRPC prototype on three Azure regions, compared against Kleppmann's:
local move apply time ~1.34× faster; remote move apply ~68× faster at high op
rates (µs range vs hundreds of µs), because Kleppmann's approach performs ~200
undo/redo ops per out-of-order remote op while the proposed approach needs at
most one compensation, and only on actual cycles. Conflict count (compensations)
drops with larger trees (~427 at 200 nodes vs ~47 at 2K nodes).

## Relation to the topic's characteristics

- **Multi-master / offline**: identical model to Kleppmann — every replica is
  a master, ops generated locally, asynchronous broadcast, full availability
  under partitions and crashes.
- **Conflict resolution**: directory move conflicts resolved by LWW on
  globally unique timestamps with a cheap cycle-breaking compensation; the
  conflict node is a user-visible "preserve the conflict" mechanism analogous
  to our keep-both philosophy, but only as a last resort.
- **Eventual consistency**: convergence proven (by sketch) — all replicas
  converge to the same tree given the same op set.
- **Fault tolerance / scalability**: compensation cost is constant per cycle
  rather than proportional to log length, and memory is bounded by the m-entry
  `present_log` per node — well suited to ≤10 offline-capable nodes.

## Trade-offs vs Kleppmann (key takeaway for our design)

- Kleppmann: machine-checked proofs, total-order replay semantics (elegant,
  deterministic), but apply cost grows with in-flight log length and
  compensations are frequent (~200 per out-of-order remote op).
- This paper: constant-time remote apply and bounded metadata, but proofs are
  sketches only, determinism across differing message orderings relies on the
  LWW rule, older positions can be lost when the m-entry `present_log`
  overflows (nodes fall to the conflict node), and GC/permanent-delete is
  unsolved. Also assumes ops propagate to all replicas; tie-breaking and
  clock assumptions (Lamport/HLC) must be implemented carefully.
- Both papers cover tree structure only; content conflicts remain a separate
  layer.
