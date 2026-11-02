# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/`.

## Test findings

- **Missing-file lookup returns EINVAL (SYNC-01).** With a joined user whose
  `/alice/home` directory exists but is empty, `GET /fs/content` for a file that
  has not been synchronized yet returns HTTP 400 with
  `{"error":"Filesystem error 22","errno":22}` (`EINVAL`) instead of HTTP 404
  (`ENOENT`). Reproduce by reading `/alice/home/hello.txt` on Node B before the
  file is created on Node A and synchronized. Check the `NodeFuseFS`
  lookup/read path, since SwingDrive already maps `ENOENT` to 404.

- **Single-node invitation acceptance fails without a peer ID (SHARE-01).** On a
  node hosting all three local users (`alice`, `bob`, `charlie`), accepting a
  SyncUnit invitation with the peer field blank fails with
  `Could not accept invitation: No known peer hosts this SyncUnit invitation`,
  although the invitation is hosted by the same node. Entering the node's own
  peer ID in the invitation form works (`joinSyncUnit` then resolves the source
  to the local node). `Node.joinSyncUnit` skips `this` while probing
  `network.nodes`, and the configured-peer fallback has no candidates on a
  single node; it should fall back to accepting a locally hosted invitation.

- **SHARE-01 passed.** On one node with `alice` (manager), `bob` (editor) and
  `charlie` (viewer), each user labeled the shared folder `Team`. Bob wrote
  `/bob/Team/notes.txt`; all three roots showed `Bob was here` (size 12).
  Charlie's overwrite and delete returned HTTP `400` with
  `{"error":"Filesystem error 22","errno":22}` (`EINVAL`) and changed nothing;
  create and move were also rejected. Bob's overwrite succeeded and propagated
  to Charlie's root. The units panel file table only shows externally written
  files after **Refresh**, which is acceptable for test automation but easy to
  mistake for a missing file when checking through the UI.

- **CONFLICT-02 passed with a minor Contacts refresh gap.** Concurrent per-field
  contact updates merged correctly: changing the first name on Node A and the
  email on Node B while both nodes were network-paused converged to `Robert` /
  `bob@new.example` on both nodes. The contact kept one ID (`87c73511`), stayed a
  single list entry, and retained its linked user ID through another linked
  concurrent merge. Same-field conflicts converged deterministically to Node A's
  value in both edit orders (A-then-B and B-then-A), and search matched only the
  merged values. Minor: when the Contacts panel is open in the empty "New
  contact" state, a contact replicated from another node appears in the list but
  the detail form stays empty until the row is selected, because
  `ContactsPanel.refresh()` returns early whenever `editingNew` is set
  (`src/main/java/com/github/ruediste/p2psync/ui/ContactsPanel.java:165`).
  Preserve that form only while `dirty`; otherwise select the first contact.

## Future hardening

- **Active-node bootstrap discovery.** Non-user units restrict peers to their
  active node set once it has more than one node, but fall back to probing all
  peers while bootstrapping. A dedicated discovery/bootstrapping rule would make
  this deterministic.
