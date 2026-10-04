# Serverless Network File Systems: xFS (Anderson et al., Berkeley)

xFS demonstrates how to eliminate the central server entirely: workstations cooperate as peers, and any machine can store, cache, or manage any data ("anything, anywhere"). It is LAN-based and assumes continuous connectivity, so it does not address offline operation or merge conflicts — but it is a key reference for distributing metadata and control without a master, and for fault-tolerant recovery.

Concrete design elements:

- **Distributed metadata via a manager map**: a globally replicated table mapping index-number ranges to per-file "managers" (cache-consistency + disk-metadata state). Extracting bits of a file's index number locates its manager, giving one-hop lookups; the map has ~10x more entries than managers so responsibilities can be rebalanced when nodes join/leave. Co-locating a file's manager with its creating client cut network hops by over 40%.
- **Log-based network striping (from Zebra)**: clients append writes to per-client logs, stripe segments across a stripe group of storage servers with software RAID parity, and ship **deltas** (block ID + old/new version pointers) that make multi-machine modifications atomic and replayable during recovery. LFS-style imap/index-node structures locate data; stripe-group maps keep write efficiency and limit failure blast radius.
- **Token-based per-block cache consistency** (Sprite/AFS-like): write ownership per block, revocation on conflict — strong consistency, not eventual, but a reference point for what must change in a multi-master design.
- **Hierarchical bottom-up recovery**: (1) log segments from checkpoints with checksum roll-forward, (2) manager metadata via checkpoint + delta roll-forward, parallelized so each surviving manager recovers one peer's log, (3) cache-consistency state via server-driven querying of clients, (4) cleaner state. Version numbers in deltas order conflicting updates.
- **Reconfiguration by consensus**: leader election (Autonet spanning-tree algorithm) recomputes manager/stripe-group maps after crashes; incremental changes can proceed while reconfiguration runs.

Lessons for a ~10-node multi-master system: the four-map indirection structure, parallel checkpoint/roll-forward recovery from a redundant shared log, and consensus-based role reassignment are directly transferable; the strict per-block consistency model is what an offline-tolerant design would relax into optimistic divergence plus merge.
