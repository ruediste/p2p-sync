# Disconnected Operation for AFS (Huston & Honeyman, 1993)

This paper describes a concrete mechanism for letting a caching client keep working while cut off from the file server — the client-side half of an offline-capable, multi-master file system. The core technique: when disconnected, the AFS cache manager pretends a server callback exists (issuing "local callbacks" to itself), performs all mutations locally, and logs each successful mutating vnode operation (`afs_create`, `afs_mkdir`, `afs_write`, `afs_remove`, etc.) together with enough metadata (file name, AFS fid, current data version number) to replay it on reconnection. On reconnect, a replay agent iterates the log and replays operations against the server, using the server's monotonically incremented file version numbers to detect conflicts (no timestamps, since clocks cannot be trusted).

Key lessons for building a multi-master, eventually consistent system:

- **Replay-based conflict detection**: before each mutating replay, compare the local cached version number with the server's; a mismatch means concurrent modification. If the file was modified once, modification timestamps can discriminate stale reads; if modified more than once, they cannot — a concrete illustration of version-vector limitations.
- **Preserve-both-then-escalate policy**: instead of overwriting, the replay agent writes the disconnected version to a fresh unique name (e.g., `foo.creat`, suffixes appended until unique) and tracks the rename so later logged operations follow the new name — data preservation over silent last-writer-wins.
- **An "orphanage"** directory for files whose parent directories were deleted elsewhere, ensuring no update is lost.
- **Asymmetric conflicts**: directory conflicts (mkdir/rename name collisions) can often be auto-resolved; data content conflicts are left to the user.

Notably, this is single-master with a server, not multi-master, and conflicts are rare under measured workloads — evidence that optimistic disconnection is practical at small scale. Relevant characteristics: conflict detection, eventual consistency via log replay, fault tolerance through data preservation.
