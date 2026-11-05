# CONFLICT-01 — Concurrent File Modification

| Field | Value |
| --- | --- |
| **Test ID** | CONFLICT-01 |
| **Feature** | Conflict resolution / deterministic ordering |
| **Priority** | High |

## Test Case Description

Verify that two nodes modifying the same file while disconnected converge to one
deterministic result after reconnecting, using the operation order defined by the
Lamport clock and `NodeId`.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A and Node B are synchronized for user `alice`.
- `/alice/home/conflict.txt` exists on both nodes with content `Initial`.
- Connectivity between the two nodes can be disabled without stopping either
  node, for example by blocking the peers' TCP traffic or by using separate
  machines and disconnecting their network.

## Steps

1. Disable connectivity between Node A and Node B.

2. On Node A, overwrite the file with `Version A`:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/conflict.txt' \
     --data-binary 'Version A'
   ```

3. On Node B, overwrite the same file with `Version B`:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9223/fs/content?path=/alice/home/conflict.txt' \
     --data-binary 'Version B'
   ```

4. Confirm that each node still shows its local value while disconnected.

5. Re-enable connectivity and trigger synchronization on both nodes, or wait for
   several background sync rounds.

6. Read `/alice/home/conflict.txt` on both nodes and compare the bytes.

## Expected Result

- Both nodes converge to byte-identical file content within a bounded wait
  (for example 60 seconds).
- The final content is exactly one of `Version A` or `Version B`, not a
  concatenation or partially merged byte sequence.
- The winner is the value of the operation that sorts later by the per-SyncUnit
  operation order (`lamportClock | NodeId`). If the operation logs are
  available, verify the winner matches that order.
- Repeating the sync rounds does not change the result.
