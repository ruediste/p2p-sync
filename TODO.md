# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/persistence.md`.

## Test findings

- **Missing-file lookup returns EINVAL (SYNC-01).** With a joined user whose
  `/alice/home` directory exists but is empty, `GET /fs/content` for a file that
  has not been synchronized yet returns HTTP 400 with
  `{"error":"Filesystem error 22","errno":22}` (`EINVAL`) instead of HTTP 404
  (`ENOENT`). Reproduce by reading `/alice/home/hello.txt` on Node B before the
  file is created on Node A and synchronized. Check the `NodeFuseFS`
  lookup/read path, since SwingDrive already maps `ENOENT` to 404.

## Future hardening

- **Active-node bootstrap discovery.** Non-user units restrict peers to their
  active node set once it has more than one node, but fall back to probing all
  peers while bootstrapping. A dedicated discovery/bootstrapping rule would make
  this deterministic.
- **Per-block checksums.** The storage layer currently validates structure and
  offsets, but not block content. Add checksums if silent storage corruption must
  be detected.
