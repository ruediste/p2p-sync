# Remaining Work

This file tracks deferred implementation work. Design and implementation details
live in `doc/persistence.md`.

## Storage and synchronization

- **Segment-level resumable SyncUnit transfers.** Content operations are currently
  transferred at operation granularity with a byte budget; authorization
  operations are transferred in full. Add segmented transfers with per-operation
  offsets so a single large operation can resume across requests, analogous to
  user operation transfers.
- **Remove the duplicate file-tree snapshot on initial sync.** Remote initial sync
  currently sends both the file-tree entry list and `snapshotState`. Drop the
  full-tree blob from the transfer once all peers use the entry-list path, and
  keep `snapshotState` only as a local stable-log checkpoint if still needed.
- **Remove the shared-folder path/content map.** `SyncUnitData.files` is still
  materialized for content replay and the compatibility `listSharedFiles` API.
  Make shared-folder content exclusively `FileTree`-backed and remove the map.
- **Wire the combined root view into user interfaces.** `Node.listRootEntries`
  and the label-conflict rules exist, but `NodeFuseFS` and the desktop UI still
  expose users directly rather than the `home` plus shared-folder root view.

## Future hardening

- **Active-node bootstrap discovery.** Non-user units restrict peers to their
  active node set once it has more than one node, but fall back to probing all
  peers while bootstrapping. A dedicated discovery/bootstrapping rule would make
  this deterministic.
- **Per-block checksums.** The storage layer currently validates structure and
  offsets, but not block content. Add checksums if silent storage corruption must
  be detected.
