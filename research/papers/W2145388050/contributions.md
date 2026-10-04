# An Algebraic Approach to File Synchronization (Ramsey & Csirmaz, TR-05-01)

This is the formal foundation of the Unison file synchronizer and the most directly relevant paper for building a multi-master file synchronizer's core logic.

Key teachings:

- Command algebra: synchronization is specified over operations (create, remove, edit, rename) rather than states. `move(α, α′)` is deliberately decomposed into `remove(α); create(α′)` (as Unison does), because a move touches two paths and otherwise breaks commutativity reasoning — a crucial simplification for handling directory moves.
- Sound and complete rewriting laws for command pairs, organized by path relation (prefix, ancestor-of, incomparable). Two sequences relate by equivalence or by "approximation" (S1 ⊑ S2: S2 breaks no more filesystems), enabling safe reordering of concurrent operations from different replicas.
- Three-phase algorithm: (1) update detection recovers each replica's operation sequence Si, (2) reconciliation executes all commands that can be applied safely at every replica, (3) conflict resolution handles leftover conflicting commands via a pluggable policy (remove name, designate master replica, latest-mtime-wins), including the disconnected-repair property — a user fixes conflicts at one replica and resync proceeds automatically.
- Practical guidance: timestamps cannot serve as a global order across replicas (clock skew); requirements are given so synced timestamps stay consistent for Make; a "broken filesystem" bottom element models erroneous command sequences statically.

Relation to topic: directly addresses multi-master conflict detection (equivalent-or-stronger than Unison/Balasubramaniam–Pierce dirty-set detection) and directory-hierarchy conflicts for small replica sets; it assumes pairwise sync rather than continuous replication, so eventual-consistency protocols must come from elsewhere.
