# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/persistence.md`.

## Future hardening

- **Active-node bootstrap discovery.** Non-user units restrict peers to their
  active node set once it has more than one node, but fall back to probing all
  peers while bootstrapping. A dedicated discovery/bootstrapping rule would make
  this deterministic.
- **Per-block checksums.** The storage layer currently validates structure and
  offsets, but not block content. Add checksums if silent storage corruption must
  be detected.
