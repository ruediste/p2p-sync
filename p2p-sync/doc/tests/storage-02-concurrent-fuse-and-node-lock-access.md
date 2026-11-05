# STORAGE-02 — Concurrent FUSE and Sync Access

| Field | Value |
| --- | --- |
| **Test ID** | STORAGE-02 |
| **Feature** | Storage / node state locking |
| **Priority** | High |

## Test Case Description

Verify that filesystem requests and synchronization do not deadlock or expose
partially updated state when they overlap, and that the node lock is released at
network boundaries.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A and Node B are synchronized for a user with a non-trivial file tree.
- There is enough data to make a sync round run for several seconds, for example
  a 20 MiB file or a few hundred small files.
- The tester can run a bounded loop of filesystem requests against one node.

## Steps

1. On Node A, create or update enough data that a sync round takes several
   seconds (for example a 20 MiB file).

2. Start a synchronization round on Node B.

3. While the round is running, repeatedly perform filesystem operations on
   Node A or Node B, for example create, stat, read, move, and delete small
   files. Bound each request so a hang is visible:

   ```bash
   timeout 30s curl -s -X POST \
     'http://127.0.0.1:9222/fs/file?path=/alice/home/concurrent.txt'
   timeout 30s curl -s -X DELETE \
     'http://127.0.0.1:9222/fs?path=/alice/home/concurrent.txt'
   ```

4. Continue the overlap for at least 10 seconds.

5. Let synchronization finish, then verify the file tree and run a read of the
   synced large file.

## Expected Result

- Every filesystem request completes within the 30-second bound; no request
  hangs or deadlocks.
- Requests may fail with a valid filesystem error, but do not return `EIO` for
  ordinary operations and do not leave half-created entries.
- The sync round completes and the final file tree is consistent on both nodes.
- A subsequent `StorageCheckMain` run on a stopped node reports no structural
  errors.
- Application logs do not contain `Missing reverse GUID mapping` or similar
  lock-related corruption errors.
