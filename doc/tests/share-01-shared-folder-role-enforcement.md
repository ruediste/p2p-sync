# SHARE-01 — Shared Folder Role Enforcement

| Field | Value |
| --- | --- |
| **Test ID** | SHARE-01 |
| **Feature** | SyncUnit authorization / roles |
| **Priority** | High |

## Test Case Description

Verify that a shared-folder member with the **Editor** role can modify content,
while a member with the **Viewer** role cannot modify or delete it.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- One node is running with three local users: `alice` (owner), `bob`, and
  `charlie`. The same test can be repeated with the users on separate nodes.
- Alice can create a shared folder and invite the other local users through the
  contact picker.
- Bob and Charlie can accept their invitations and set the shared folder's local
  label to `Team` so it appears below their combined root view.
- The labels result in paths `/alice/Team`, `/bob/Team`, and `/charlie/Team`.

## Steps

1. As `alice`, open the units panel and create a shared folder (**New folder**).
2. Invite `bob` with the **Editor** role and `charlie` with the **Viewer** role.
3. Accept both invitations from the respective user's mail/invitation view.
4. As each user, set the shared folder label to `Team`.
5. As `bob`, write a file:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/bob/Team/notes.txt' \
     --data-binary 'Bob was here'
   ```

6. Confirm that `alice` and `charlie` can read `/Team/notes.txt`.
7. As `charlie`, attempt to overwrite the file:

   ```bash
   curl -s -i -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/charlie/Team/notes.txt' \
     --data-binary 'Charlie was here'
   ```

8. As `charlie`, attempt to delete the file.

## Expected Result

- Bob's write succeeds and the content is visible to all members.
- Charlie's write and delete are rejected; the content remains `Bob was here`
  and the file still exists.
- The rejection is a client error. When using `/fs`, the bridge may return
  HTTP `400` because `NodeFuseFS` maps authorization `IllegalArgumentException`
  to `EINVAL`; HTTP `403` is expected only when a FUSE permission error is
  propagated. In either case the operation must not succeed.
- No partial write or deletion is visible to the other members.
