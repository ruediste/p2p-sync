# FAILURE-01 — Sync Recovery after Network Interruption

| Field | Value |
| --- | --- |
| **Test ID** | FAILURE-01 |
| **Feature** | Synchronization robustness / resumable transfer |
| **Priority** | High |

## Test Case Description

Verify that an interrupted transfer resumes from the last received operation or
payload offset when connectivity returns, instead of losing the transfer or
duplicating operations.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A and Node B are synchronized for a user with a large file or a large
  number of operations (for example a 20 MiB file in a shared folder or the
  user's home).
- The tester can interrupt and restore network connectivity between the nodes
  without deleting either `node.db`.

## Steps

1. On Node A, create or update a file large enough that the transfer takes
   several seconds, and note its checksum.

2. On Node B, start a synchronization round. Wait until the transfer is in
   progress (for example the pending-operation count is non-zero, or the
   large file is not yet complete).

3. Interrupt connectivity between Node A and Node B while the transfer is
   running.

4. Restore connectivity after a short interruption (for example 5–10 seconds).

5. Trigger synchronization again on Node B, or wait for the background round.

6. Read the transferred file on Node B and compare checksums.

7. Repeat the interruption once more during the resumed transfer, then let the
   transfer finish.

## Expected Result

- The transfer resumes after reconnection; Node B does not restart from an
  inconsistent state or require deleting its database.
- The final file content matches the source exactly (checksum equality).
- No duplicate operations are applied: for example, no duplicate messages,
  duplicate file entries, or repeated metadata changes are visible.
- Node B can complete the sync within a bounded number of rounds after
  connectivity is restored.
- If a partial payload was persisted, it is completed rather than exposed as a
  truncated file.
