# CONFLICT-02 — Concurrent Contact Field Updates

| Field | Value |
| --- | --- |
| **Test ID** | CONFLICT-02 |
| **Feature** | Conflict resolution / per-field contact updates |
| **Priority** | High |

## Test Case Description

Verify that simultaneous updates to different fields of the same contact survive
replication, and that updates to the same field resolve deterministically.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A and Node B are synchronized for user `alice`.
- Alice's contact list contains `bob` with first name `Bob` and email
  `bob@old.example`.
- Connectivity between the nodes can be disabled without stopping either node.

## Steps

1. Disable connectivity between Node A and Node B.

2. On Node A, edit contact `bob` and change the first name to `Robert`. Leave
   the email unchanged.

3. On Node B, edit the same contact and change the email to
   `bob@new.example`. Leave the first name unchanged.

4. Confirm that each node shows its local change while disconnected.

5. Re-enable connectivity and synchronize both nodes.

6. Inspect contact `bob` in the **Contacts** section on both nodes.

7. Repeat the test with both nodes changing the *same* field to different values.

## Expected Result

- After the first merge, both nodes show first name `Robert` and email
  `bob@new.example`; both field changes survive.
- The contact remains linked to the same user ID and is not duplicated.
- When both nodes change the same field, both nodes converge to the value from
  the operation that sorts later by the contact operation order; the result is
  deterministic and identical on both nodes.
- No malformed contact operation is observed, and search by either the old or
  new field behaves according to the merged state.
