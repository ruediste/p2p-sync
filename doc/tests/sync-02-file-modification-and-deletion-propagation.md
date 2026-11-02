# SYNC-02 — File Modification and Deletion Propagation

| Field | Value |
| --- | --- |
| **Test ID** | SYNC-02 |
| **Feature** | File synchronization / USER SyncUnit |
| **Priority** | High |

## Test Case Description

Verify that modifying and deleting an existing file propagates correctly from
one node to another.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- SYNC-01 is complete: `/alice/home/hello.txt` exists on Node A and Node B with
  content `Hello P2P`.

## Steps

1. On Node A, overwrite the file:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/hello.txt' \
     --data-binary 'Hello P2P Updated'
   ```

2. Trigger synchronization on Node B, or wait for the background interval.

3. Read the file on Node B and verify it contains `Hello P2P Updated`.

4. On Node B, delete the file:

   ```bash
   curl -s -X DELETE \
     'http://127.0.0.1:9223/fs?path=/alice/home/hello.txt'
   ```

5. Trigger synchronization on Node A, or wait for the background interval.

6. Request the file on Node A:

   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/hello.txt'
   ```

## Expected Result

- The modification appears on Node B with the exact updated content.
- After the deletion syncs, the file is absent on Node A.
- The final `/fs/content` request on Node A returns HTTP `404` (`ENOENT`).
- No duplicate file or stale copy remains on either node.
