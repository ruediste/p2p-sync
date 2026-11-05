# SYNC-01 — Basic User File Synchronization

| Field | Value |
| --- | --- |
| **Test ID** | SYNC-01 |
| **Feature** | File synchronization / USER SyncUnit |
| **Priority** | High |

## Test Case Description

Verify that a file created in a user's home directory on Node A appears on
Node B after synchronization.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A and Node B are running.
- User `alice` is configured on Node A.
- Node B has joined the same user (`alice`) using a credential exported from
  Node A. The node-local user name is `alice` on both nodes for the paths below.
- The two nodes know each other as peers through mDNS or explicit peer IDs.
- `/alice/home` exists on both nodes.

## Steps

1. On Node A, create the file:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/hello.txt' \
     --data-binary 'Hello P2P'
   ```

2. Confirm the write on Node A:

   ```bash
   curl -s 'http://127.0.0.1:9222/fs/content?path=/alice/home/hello.txt'
   ```

3. Trigger synchronization on Node B by clicking **Sync now** through the UI,
   or wait for the background synchronization interval:

   ```bash
   curl -s -X POST http://127.0.0.1:9223/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Sync now"}}'
   ```

4. Poll Node B until the file appears or a 30-second timeout expires:

   ```bash
   curl -s 'http://127.0.0.1:9223/fs/content?path=/alice/home/hello.txt'
   ```

5. Compare the content on both nodes.

## Expected Result

- `/alice/home/hello.txt` exists on Node B.
- The content is exactly `Hello P2P` on both nodes.
- `GET /fs/stat` on Node B reports a regular file of 9 bytes.
- No error is shown in either status bar, and the pending-operation count
  returns to zero after the sync round completes.
