# SHARE-02 — Shared Folder Label Collision

| Field | Value |
| --- | --- |
| **Test ID** | SHARE-02 |
| **Feature** | Shared folder / combined root view labels |
| **Priority** | Medium |

## Test Case Description

Verify that two accepted shared folders with the same per-user label do not
produce duplicate entries or broken paths in the combined root view, and that
the collision is resolved deterministically.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A is running with a user `alice`.
- Alice participates in two different shared folders. They can be created
  locally, or received as invitations. Both contain distinct test files.
- Alice can set a per-user label for each shared folder.

## Steps

1. Open the units panel and confirm that both shared folder units are listed and
   accessible.
2. Set the local label of both units to `Docs`.
3. List Alice's combined root view:

   ```bash
   curl -s 'http://127.0.0.1:9222/fs/list?path=/alice'
   ```

4. Confirm that the listing contains a single `Docs` directory entry and no
   duplicate `Docs` entries.
5. List `/alice/Docs` and note which unit's test file is visible.
6. In the units panel, inspect both units directly to confirm that both units
   and their content still exist.
7. Rename the label of the second unit and confirm that both folders are now
   visible under distinct labels.

## Expected Result

- The combined root view shows exactly one `Docs` entry.
- The entry resolves to the shared folder with the lowest `SyncUnitId` for that
  label; the other unit is not exposed under the same label. The node records
  the label collision in its root entry metadata (`conflict` is true).
- Accessing `/alice/Docs` works and returns content from the resolved unit.
- No file from the other unit is silently merged into the resolved folder.
- The unit panel still lists both units, and changing one unit's label makes
  it separately accessible.
