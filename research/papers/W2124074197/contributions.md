# Disconnected Operation in the Coda File System (Kistler & Satyanarayanan, 1992)

The foundational paper on optimistic replication with disconnected clients — directly relevant to nodes that go offline and reconnect.

Key contributions:

- **Feasibility of disconnected operation**: clients serve file requests purely from their local cache while disconnected ("emulation" mode), and reintegrate changes on reconnect. Voluntary (laptop) and involuntary (failure) disconnections are handled by a single mechanism — a design stance worth copying.
- **Three-state cache manager (Venus)**: hoarding → emulation → reintegration, per volume. Hoarding uses a prioritized cache algorithm plus user-supplied "hoard profiles" (HDB) to pre-stage critical data before disconnection; periodic "hoard walks" recompute cache contents. This is concrete guidance for what an offline node must persist and prefetch.
- **First-class vs. second-class replicas**: servers hold authoritative replicas; client caches are inferior and must be periodically revalidated. Relevant to deciding which nodes in a small cluster act as durable anchors.
- **Server replication (VSG/AVSG)**: read-write replicas on multiple servers, parallel write propagation to all accessible replicas (AVSG) with asynchronous catch-up to missing ones — a concrete multi-master update protocol with callback-based cache coherence for read freshness.
- **Optimistic replica control**: writes are allowed everywhere; conflicts are detected at reintegration and resolved by rules, with unresolved conflicts preserved for manual repair (conflict isolation per directory). The paper argues leases/pessimistic locking defeat availability and gives a measured justification (low write-sharing ⇒ few conflicts).
- **Scalability principles**: whole-file caching for a simple failure model, push functionality to clients, avoid cluster-wide consensus/election (explicitly rejecting Locus-style partition agreement) — well matched to a ≤10-node system.

Relation to the topic: Coda supplies the architectural template — local autonomy while offline, optimistic multi-master updates, reintegration-time conflict detection and resolution, and eventual convergence via asynchronous propagation — with real measurements of reintegration cost and hoard sizing.
