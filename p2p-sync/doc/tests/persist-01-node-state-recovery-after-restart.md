# PERSIST-01 — Node State Recovery after Restart

| Field | Value |
| --- | --- |
| **Test ID** | PERSIST-01 |
| **Feature** | Data persistence |
| **Priority** | High |

## Test Case Description

Verify that configured users, the node peer identity, and file contents are
restored from `node.db` after a clean restart.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A is running with user `alice`.
- `/alice/home/persist.txt` exists with content `Persistence Test`.
- The node's peer ID is recorded from the startup output or `/state`.

## Steps

1. Confirm the baseline state on Node A:

   ```bash
   curl -s http://127.0.0.1:9222/state
   curl -s 'http://127.0.0.1:9222/fs/content?path=/alice/home/persist.txt'
   ```

2. Stop Node A cleanly by closing the application window or terminating the
   process normally and waiting for shutdown to finish.

3. Start Node A again with the same `node.db` path and the same `--drive` port.

4. Inspect the user list and file content:

   ```bash
   curl -s http://127.0.0.1:9222/state
   curl -s 'http://127.0.0.1:9222/fs/content?path=/alice/home/persist.txt'
   ```

## Expected Result

- User `alice` is still configured on Node A.
- The node peer ID is unchanged.
- `/alice/home/persist.txt` exists and contains `Persistence Test`.
- No corruption error, missing-user error, or storage-open error is reported.
- A subsequent `StorageCheckMain` run would report a structurally valid file
  (covered separately by PERSIST-02).
