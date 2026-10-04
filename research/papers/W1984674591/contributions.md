# W1984674591 — Taming Aggressive Replication in the Pangaea Wide-Area File System (Saito, Karamanolis, Karlsson, Mahalingam — OSDI 2002)

A symmetrically decentralized wide-area file system: every user's commodity machine is a peer server serving its local user, replicating data aggressively to maximize availability, with optimistic (eventual) consistency.

## Contributions relevant to the topic

1. **Sparse replication ("targeted")**: instead of full replication everywhere, each file specifies a *target replica count*; replicas are recruited among reachable peers (random for load spreading, same-domain for locality). Update propagation follows the live-node-list graph. For a ≤10-node system this suggests a knob: default full replication, degrade to sparse under failure — a middle ground the topic's small scale makes feasible.
2. **Update propagation with deltas + "harbingers"**: updates ship as *delta timestamps + metadata first*; the data body is fetched on demand. Harbingers (notification records) eliminate redundant/duplicate update delivery (~2% bandwidth waste measured) and opportunistically prune stale replica links. This is a concrete, measured design for cheap pairwise anti-entropy with metadata-first ordering.
3. **Conflict detection and resolution (Section 5.2) — the substantive lesson**: on mismatch, Pangaea falls back to full-state transfer, then applies version vectors to separate *true conflicts* from other divergences:
   - Directory entries: last-writer-wins per entry (explicitly acknowledged as lossy).
   - Rename of a directory whose children changed concurrently: reconciled by *backpointers* — children carry pointers to their parent; on detecting a missing parent entry, entries are re-created and re-parented. A concrete mechanism for the directory-move-under-concurrent-update case.
   - The paper candidly documents the residual hard cases (concurrent directory-update conflicts, move conflicts) — a useful negative reference: it shows LWW + heuristics reaches only so far, motivating the Tofu/CRDT approaches.
4. **Decentralized directory/state**: live-node lists, replica sets, and file placement are all maintained without a server; any peer can serve any user. Failure handling is implicit: stale peers are evicted from live-node lists and data re-recruited onto healthy ones.
5. **Evaluation honesty**: quantifies the cost of aggressive replication (recovery time ~seconds of inconsistency window, bandwidth overhead) — empirical grounding for the optimistic assumptions.

## Relation to topic characteristics

- **Multi-master**: symmetric peer architecture; any node updates, any node serves — matches the requirement directly, unlike hierarchical/primary schemes.
- **Eventual consistency**: guaranteed via version-vector-checked propagation with delta/harbinger anti-entropy.
- **Conflict resolution**: weaker than Tofu/Unison (LWW on directory entries, lossy), but the *backpointer re-parenting* mechanism and the explicit taxonomy of remaining hard rename/move conflicts are directly relevant to the topic's primary concern.
- **Fault tolerance / scalability**: replica re-recruitment over live-node lists gives self-healing without central control; the sparse-replication machinery is unnecessary at ≤10 nodes but its degradation behavior is instructive.