# Relays

This file documents the future relay system. Note: it is not implemented yet.

In today's internet, NAT (Network Address Translation) is commonly used to allow multiple devices on a local network to share a single public IP address. This creates challenges for peer-to-peer communication, as devices behind NAT cannot be directly reached from the outside internet. In practice, most nodes are only reachable if they are located in the same local network. Nodes with public IPs are rare, especially for consumer devices, which this project is targeting. Relays help by acting as intermediaries, forwarding traffic between peers that cannot connect directly.

To avoid the need for individuals to set up their own relay servers, and to avoid the cost of running dedicated relay infrastructure, this project aims to use existing free services provided by the major cloud providers as relays.

To build a system which is flexible in terms of relay service capabilities, it is important to first formalize the various communication/relay patterns.

## Communication Patterns

We focus on the smallest common denominator: A relay can store blobs (typically files) under a chosen name/id. The available blobs can typically be listed. If this is not possible, index files can be used instead.

A relay can be used by multiple nodes with writing access to the relay. Relays can often be read without authentication. (Relays with restricted read access are possible).

The relays introduce significant latency between one node writing data and another node observing the changes (10s of seconds)

A note on terminology: the relay never establishes a connection. Both patterns are store-and-forward, and nodes do not have to be online at the same time. Anything that looks like a "connection" is a protocol layered on top of polled blobs, and its round-trip cost is a multiple of the polling interval.

### Pattern: Publication (one-to-many, store-and-forward)

_(also called: broadcast, bulletin board, shared log, publish/poll)_

One relay topic is read by a set of participants. Any participant with write access may append; nobody addresses a specific receiver. Receivers discover new data by listing the topic (or by polling an index blob when listing is unavailable), typically comparing the set of names against what they have already retrieved.

Requirements on the data layout:

- **Names carry meaning.** A blob name encodes topic, writer and a monotonically increasing position, for example `unit/<unitId>/log/<sourceNodeId>/<clock>`, `user/<userId>/membership/<sequence>`, or `request/<originNodeId>/<requestId>`. Readers can then compute "what is new to me" from the listing alone and never need to read blobs they already have.
- **Append-only by convention.** Overwriting a blob is avoided where possible, because a relay only offers last-writer-wins semantics and gives no ordering or conflict information. A sequence number in the name pushes conflicts into the application, where they can be handled.
- **Ordering is per writer, not global.** Participants see each writer's stream in order but may interleave writers differently. Consumers must merge, exactly as with the operation logs in `persistence.md`.
- **Retention is a protocol concern.** Publication data grows without bound. The common cycle is: publish operations, truncate at the causally stable timestamp. Participating nodes publish the log timestamps they have retrieved from other nodes periodically, such that the causally stable timestamp can be determined
- **Confidentiality.** Publication channels are often readable without authentication, so payloads are encrypted for the intended audience rather than relying on relay access control.
- **Snapshot Publishing.** For a small metadata payload, governed by a single node, the current state/snapshot can be published as well. Large snapshots may instead be served only by rendezvous, as described in the SyncUnit sections below.

The characteristic strength of this pattern is that the number of participants does not change the cost: one stream serves everyone, and a late participant only has to read the current snapshot plus the recent log tail. Its characteristic weakness is that there is no per-receiver acknowledgement and no way to direct a reply to one peer; anything interactive degenerates into everyone reading everyone's traffic.

Typical uses: SyncUnit operation logs, membership and participation announcements, contact requests and accept/reject notices inside a user unit, and the pointer blobs that tell other nodes where a newcomer's mailbox is.

### Pattern: Rendezvous (pairwise, duplex over two mailboxes)

_(also called: mailbox pair, two-way store-and-forward channel, relay-assisted connection setup)_

When two nodes want to exchange large amounts of data, such as a snapshot of a shared folder when a node joins, it may not be practical to store the whole snapshot on the relay due to space constraints.

The general form is a pair of mailboxes: each side writes only under its own node prefix and polls the other side's index or immutable entries. This works even when the relay only provides "write blobs under a name" and "read/list blobs", and it is the same mechanism used for all request/response exchanges below, not only for large snapshots. Section [Sync Unit specific Relay Patterns](#sync-unit-specific-relay-patterns) defines the concrete request/reply layout and applies it to every SyncUnit.

## Sync Unit specific Relay Patterns

This section specifies the target design. None of it is implemented yet. It
starts from the requirement that two nodes must be able to run the complete
synchronization protocol through relays alone: direct TCP connections are an
optimization, never a prerequisite. Consequently every protocol exchange has a
mailbox form, and every SyncUnit has a publication form.

### Relay-only invariant

- A node may run with no inbound reachability and no outbound TCP. It still
  reads and writes relay blobs.
- Each node executes the same synchronization algorithm as today, but a
  `RemoteSyncRequest`/`RemoteSyncResponse` round trip is carried by a mailbox
  pair instead of a Yamux stream. The logical protocol stays unchanged:
  per-unit clocks, byte budgets, segmented content offsets and resumable partial
  operations keep working as described in `persistence.md`.
- Relays are untrusted transport. Names, writers and ordering are not trusted.
  Every operation is verified against its author's signature and the unit's
  authorization rules before it is applied. A relay can withhold data, but it
  cannot forge or reorder accepted operations.
- All latency figures below are multiples of the poll interval. A "round trip"
  means: origin writes request, target polls, target writes reply, origin polls.

### Names and streams

Identifiers are lowercase hex. Sequence numbers in names are fixed-width
zero-padded (`%016x` for clocks, `%08x` for chunk/sequence numbers) so that a
listing sorts chronologically and a reader can compute "what is new" by name
comparison alone.

| Name | Writer | Meaning |
| --- | --- | --- |
| `unit/<unitId>/log/<nodeId>/<clock>` | node | One signed operation (or a chunk manifest for an oversized operation). Append-only. |
| `unit/<unitId>/log/<nodeId>/<clock>/<offset>` | node | Payload chunk of an oversized operation. |
| `unit/<unitId>/log/<nodeId>/index` | node | Index of that node's log entries, used when the relay cannot list. |
| `unit/<unitId>/snapshot/<nodeId>/<clock>` | node | Snapshot of the materialized unit state at a causally stable clock. Absent when the unit uses rendezvous-only snapshot transfer. |
| `unit/<unitId>/checkpoint/<nodeId>/<kind>/<clock>` | node | Optional large-state checkpoint (contacts, messages), written only when truncation requires it. |
| `unit/<unitId>/active/<nodeId>` | node | Versioned active-node set of the unit. |
| `unit/<unitId>/pointer/<nodeId>` | node | Relay set and mailbox address of one active node. |
| `unit/<unitId>/cursor/<nodeId>` | node | Per-source accepted clocks of one node. |
| `user/<userId>/node/<nodeId>` | node | User-level pointer: relays, mailbox, hosted units, current home unit ID. |
| `user/<userId>/join/<nodeId>` | joining node | Signed join request for bootstrapping a new node of this user. |
| `user/<userId>/inbox/<senderNodeId>/<messageId>` | sender | Direct-message envelope for the recipient user. |
| `user/<userId>/receipt/<senderNodeId>/<messageId>` | recipient node | Signed delivery receipt, encrypted to the sender user. |
| `request/<originNodeId>/<requestId>` | origin | One mailbox request. |
| `request/<originNodeId>/index` | origin | Outstanding request IDs and their targets. |
| `reply/<responderNodeId>/<requestId>/manifest` | responder | Reply metadata, chunk list, sizes and total length. |
| `reply/<responderNodeId>/<requestId>/<seq>` | responder | Reply chunk. |
| `ack/<originNodeId>/<requestId>` | origin | Origin has received and verified the complete reply. |
| `content/<unitId>/<nodeId>/<sha256>` | node | Content-addressed file/attachment bytes referenced by operations. |

`log`, `inbox`, `receipt` and `reply` entries are immutable. `index`, `pointer`,
`active` and `cursor` are small last-writer-wins snapshots rewritten atomically
by their owner. The owner is always the node whose ID appears in the name, so
the layout also works on relays that only permit each writer to write under its
own prefix.

If listing is available, a reader lists a prefix and computes new entries by
comparing names with its local cursor. If the relay cannot list, the owner
rewrites its `index` blob after every append; the index is a compact list of
`(name, size, digest)` and is the only thing a reader polls.

### Mailbox rendezvous protocol

The mailbox pair is the relay-only replacement for a stream. Both directions
use the same request/reply shape.

1. **Request.** Origin `A` writes a signed request blob under its own prefix.
   The request contains `requestId`, origin and target node IDs, the logical
   request type, the user or unit ID, a serialized `RemoteSyncRequest` body and a
   signature by `A`'s node key. `A` updates `request/<A>/index`.
2. **Discovery.** Target `B` polls the request indexes of the nodes it knows:
   the active-node set of each unit it hosts, the user-node pointers of each user
   it hosts, and `user/<userId>/join/` while it is bootstrapping. If the relay
   supports global listing, `B` may instead list `request/` and filter on target.
3. **Reply.** `B` verifies the request signature, processes it with the existing
   sync handler and writes a manifest plus ordered chunks under
   `reply/<B>/<requestId>/`. The manifest carries the total length, chunk names,
   a digest of the complete `RemoteSyncResponse` and a signature. Chunks are
   written in ascending order; the manifest is written last so a reader never
   sees an incomplete set as complete.
4. **Completion.** `A` polls `reply/<B>/<requestId>/manifest`, fetches missing
   chunks, reassembles the response, verifies length, digest and signature, and
   applies it exactly as a response received over a stream. `A` then writes
   `ack/<A>/<requestId>`. `B` may stop serving and let the reply expire.
5. **Retry and resumption.** If a reply or some chunks are missing, `A` repeats
   the request with the same `requestId`; `B` treats repeated requests
   idempotently. If the logical transfer was partial, the next request carries
   the persisted `fromClock`/`offsets`/content offsets, so the responder resumes
   from those positions instead of restarting.
6. **Cleanup.** Blobs have a TTL in their metadata. A responder may delete its
   reply after seeing the ack. On relays without delete, nodes stop referencing
   the names and rotate to fresh request IDs; readers ignore expired entries.

A mailbox round trip costs at least four poll intervals (request visible, reply
manifest visible, chunks fetched, ack visible). The sync loop therefore batches
requests: a node asks for everything it is missing for one unit in one request
and uses the existing byte budget to bound the reply. Small units may be synced
in a single request; large unit histories are paged.

### Relay sets, pointers and discovery

A user or unit may use several relays. The user-level relay set is part of the
exported credential and is refreshed through `user/<userId>/node/<nodeId>`
pointers. Each unit has a relay set, stored in its signed metadata and carried
in invitations, so a node knows where to look before it has any unit history.
A node that adds its own relay publishes a pointer naming every relay it uses;
peers merge pointers and may use any common relay. If two nodes of a unit share
no relay, that unit cannot be synchronized between them, and the situation is
reported in the node status rather than silently retried.

For a known unit, discovery is entirely relay-based:

- The active-node set lists the nodes that participate.
- Each active node's `pointer` names its relays and mailbox.
- Each node's `cursor` tells the others how far it has consumed every source,
  which drives log truncation.

While a unit's active set contains only the local node, the node also polls the
pointers of all nodes that host participating users, plus the unit creator's
pointer. That replaces the current "probe all known peers" bootstrap.

### Common per-unit mechanics

Every unit follows the same cycle, parameterized as described in the unit
sections below:

1. Read the active set and pointers of the unit.
2. Read the log indexes or listings of every active node and fetch operations
   after the local per-source cursor.
3. Verify signatures and authorization, stage and reconcile as in
   `persistence.md`, and advance the local cursor.
4. Publish locally authored operations immediately, one blob per operation (or
   a chunk manifest plus chunks for an oversized operation).
5. Publish a cursor snapshot periodically, subject to the snapshot storage
   policy below. The causally stable timestamp is the minimum of the active
   nodes' cursors; a node may publish or serve a snapshot for that timestamp and
   drop log entries older than it.
6. Serve mailbox requests from other active nodes for snapshots, missed log
   ranges, content bytes, invitations and bootstraps.

A node that has fallen behind the stable timestamp bootstraps from the relevant
snapshot or large-state checkpoint plus the recent log tail instead of replaying
the truncated prefix. A node that was never active bootstraps from a snapshot or
checkpoint plus a mailbox transfer, as described in each unit section.

Large operation payloads are handled by chunking: the log entry becomes a
manifest `unit/<unitId>/log/<nodeId>/<clock>` and the bytes are stored under the
same prefix as `.../<offset>` chunks. File bytes and message attachments may
additionally be published as content-addressed
`content/<unitId>/<nodeId>/<sha256>` blobs so that late joiners can fetch only
the bytes they need; the signed operation records both the digest and the
publishing node ID. The digest is over the plaintext, so encryption for the
relay does not change it. If a content blob has been garbage-collected, a node
asks any active node for the digest through the mailbox protocol.

### Snapshot storage policy

Whether a snapshot is stored on the relay is not fixed. It is a per-unit setting
(including each shared folder and the home unit) constrained by each relay's
capability record:

- **`relay`** — publish the snapshot at
  `unit/<unitId>/snapshot/<nodeId>/<clock>`. This is the default for small units
  such as the USER unit, todo lists and message channels.
- **`rendezvous`** — never publish the snapshot blob. A joining or lagging node
  requests it from an active node through the mailbox protocol; the host
  serializes the snapshot, pages it into reply chunks and the requester
  reassembles and stores it locally. The reply chunks are deleted or expire
  after the ack, so the relay never holds the complete snapshot at rest.
- **`auto`** — use `relay` when the snapshot fits the relay's advertised blob
  limit, storage budget and retention, otherwise use `rendezvous`.

Large shared folders default to `rendezvous`, avoiding full snapshot storage on
the relay at any time. The policy is part of the signed unit metadata and is
replicated to members; the home unit uses the user's default. A relay can force
`rendezvous` when it cannot store the snapshot, but it cannot force relay
storage when the folder policy says `rendezvous`. Rendezvous transfer requires
an active node that can serve the snapshot to come online; until then the join
waits, while publication logs and content blobs remain available. A relay-stored
snapshot is a compaction aid only, never the sole durable copy of active data.

### USER SyncUnit

The USER unit is the user's control plane. It is deterministic from the user
key (`SyncUnitId.forUser`), so any node with the user credential can name it
without out-of-band unit metadata. It holds status, contacts, participation,
shared-folder labels and direct messages. File-system operations live in the
separate home unit (below).

- **Publication.** Every node hosting the user appends signed `UserOperation`s
  to `unit/<userUnitId>/log/<nodeId>/<clock>`. The author is always the user,
  the node records its node hash in the operation ID, and the existing
  Lamport-clock/node-hash ordering applies. A node must never accept an
  operation whose author is not the user key. Since the relay may be publicly
  readable, operation payloads are encrypted with a key derived from the user
  key; only nodes holding the user credential can read status, contacts and
  message contents. Operation IDs, clocks and node IDs remain visible.
- **Snapshot.** `unit/<userUnitId>/snapshot/<nodeId>/<clock>` contains only the
  small control-plane state: status, participation list, label map and the
  per-source clocks, plus cursors for the contact and message streams. It
  deliberately excludes the contact list, the message history and file data.
- **Contacts.** The contact list can become large, so it is replicated as
  `CONTACT_CREATE`/`CONTACT_UPDATE`/`CONTACT_DELETE` operations in the log, not
  as part of every snapshot. A late node pages through the contact operations
  using the mailbox byte budget and then follows the log incrementally.
  Concurrent field updates are merged by the contact rules in `contacts.md`.
- **Labels.** Shared-folder labels are ordinary small operations, so they stay
  in the log and in the routine snapshot.
- **Messages.** Direct-message and receipt operations are the authoritative
  message history. They are not copied into the routine snapshot, so a snapshot
  refresh never retransfers the whole conversation. A node that joins or falls
  behind reads the message operations incrementally by their per-source clocks.
- **Large-state checkpoints.** Contact and message checkpoints are written at
  `unit/<userUnitId>/checkpoint/<nodeId>/<kind>/<clock>` only when log
  truncation requires them, not at every causally stable timestamp. Routine
  snapshots therefore stay small, while the large state is transferred once as
  operations (or once as an explicit checkpoint) and is never repeatedly
  retransferred through snapshots.
- **Participation.** `PARTICIPATE_SYNC_UNIT` and `LEAVE_SYNC_UNIT` operations are
  published in this unit. They are what tells every node of the user which
  SyncUnits to synchronize. Participation changes are therefore visible to a
  newly joined node as part of its snapshot and subsequent log.
- **Direct messages.** A message is first a signed `DIRECT_MESSAGE` operation in
  the sender's USER log, which makes it available to the sender's other nodes
  and lets any of them deliver it. To reach the recipient, the sender also
  publishes an envelope at
  `user/<recipientUserId>/inbox/<senderNodeId>/<messageId>`. The envelope
  contains the signed operation and is encrypted to the recipient user, because
  the relay may be publicly readable. Recipient nodes poll their inbox, verify
  the sender's user signature, store the message and publish a signed
  `RECEIVE_MESSAGE` operation in the recipient's USER log. The same receipt is
  published at
  `user/<recipientUserId>/receipt/<senderNodeId>/<messageId>` so the sender's
  nodes can stop redelivering even though they do not host the recipient's USER
  unit. The receipt is encrypted to the sender user and contains the original
  signed operation, so it is self authenticating after decryption and the
  message body is not exposed on a public relay. Inbox blobs expire after the
  receipt is observed; the receipt and the recipient's encrypted USER log retain
  the message for the recipient's other nodes.
- **Invitations.** Shared-folder, todo-list and message-channel invitations are
  direct messages with an invitation type. The payload carries the target unit
  ID, unit type, offered role and the unit's relay set. The invitee's nodes
  receive it through the recipient inbox, and the invitee then bootstraps the
  shared unit through the mailbox protocol.
- **Active set.** The user's active-node set is published as
  `unit/<userUnitId>/active/<nodeId>` by each node and merged as described in
  `persistence.md`. A joining node is admitted only if its join request carries
  a USER-unit operation signed by the user that announces the new node key. This
  prevents an arbitrary node from adding itself to a user's active set.
- **Retention.** Status, labels and participation are compacted into the routine
  snapshot at the stable timestamp. Contacts are compacted only into an explicit
  contact checkpoint; until then all contact operations remain in the log.
  `DIRECT_MESSAGE` and `RECEIVE_MESSAGE` operations are not truncated until an
  explicit message checkpoint replaces them. Checkpoints are not part of routine
  snapshots, so compaction of large state does not cause repeated snapshot
  transfer.

#### Adding a user to a node

This is the relay-only equivalent of `Node.joinRemote`/`Node.join`. It requires
exactly two out-of-band facts: the user credential (the user private key) and at
least one relay address. In this design the credential file is extended to carry
the relay hints, so no peer ID or listening address is needed. The new node has
its own peer key pair before it starts.

1. The new node `N` imports the credential, derives the USER unit ID and
   publishes a signed join request at `user/<userId>/join/<N>`. The request
   names `N`'s peer key, relays, mailbox pointer and the home-unit ID it is
   looking for (it may be empty).
2. Every existing node of the user polls `user/<userId>/join/`. The node with
   the lowest node ID, or the first to answer, serves the join. If it fails,
   `N` retries; the next node answers. This avoids requiring a specific host.
   First contact therefore needs a listable `user/<userId>/join/` prefix; if the
   relay cannot list it, the credential must name one host node and `N` writes
   the join request into that node's request mailbox instead.
3. The host replies through the mailbox protocol with a `RemoteSyncResponse`
   equivalent containing:
   - the accepted USER history, including contact and message operations, or
     the routine USER snapshot plus that history tail,
   - the home-unit ID, the file-tree snapshot entries and the file content
     references,
   - status, participation list and shared-folder labels (contacts and messages
     arrive as operations, not as a repeated contact/message snapshot),
   - the active-node set and pointers of the other nodes,
   - metadata for every invited-but-not-yet-accepted unit, including relay sets.
4. `N` verifies every signature, rebuilds its local state, writes
   `user/<userId>/node/<N>`, publishes a USER-unit operation announcing its node
   key, and publishes its own active-set entry and cursor.
5. `N` fetches the home-unit log tail and any file content it still misses,
   either from `content/<homeUnitId>/<nodeId>/<sha256>` or from the host by mailbox, then
   enters the normal sync loop.
6. Pending units are joined by asking any active member for the unit snapshot and
   log, then publishing the invitee's `ACCEPT_INVITATION` operation. A unit for
   which no active member can be found remains listed as pending; it does not
   block the user join.

An existing node may also hand the credential to `N` through a relay by
publishing `user/<userId>/enroll/<N>` containing the exported credential
encrypted to `N`'s peer key. This only replaces the out-of-band credential
transfer; the join sequence above is unchanged.

### Home SHARED_FOLDER SyncUnit

Each user has one home unit of type `SHARED_FOLDER` with `home = true`. It is not
shareable and has no invitations; its membership is exactly the nodes hosting
the user.

- **Publication.** File-system operations (create, write, truncate, rename,
  move, delete) are published in
  `unit/<homeUnitId>/log/<nodeId>/<clock>`. They are signed by the user and
  verified like USER-unit operations.
- **File content.** Small files stay inline in the operation. Above the inline
  threshold, the operation references `content/<homeUnitId>/<nodeId>/<sha256>` blobs
  published by the writing node. Because only the user's nodes read this unit,
  those blobs are encrypted with a key derived from the user key.
- **Snapshot.** When the snapshot policy is `relay`,
  `unit/<homeUnitId>/snapshot/<nodeId>/<clock>` is a file-tree entry manifest
  `(entityId, parentId, name, directory, contentDigest, size)`, not the file
  bytes; the snapshot plus content blobs reconstructs the tree. A large home
  tree may instead use the `rendezvous` policy, in which case no snapshot blob is
  stored and the manifest is transferred only from an active node. This splits
  the currently bundled `UserSnapshot` into unit-local state: the USER unit
  carries status, labels and participation in its routine snapshot, contacts and
  messages as operations (with occasional checkpoints), and the home unit
  carries the file tree.
- **Catch-up.** A node reads the newest relay-stored snapshot, or requests a
  rendezvous snapshot from an active node, then reads the log tail and fetches
  missing content digests from the relay or, if a blob has expired, from any
  active node by mailbox. This is the same data the current initial sync
  transfers as `UserTreeEntry` list plus contacts.
- **Bootstrap.** The home unit is transferred as part of "adding a user to a
  node". There is no separate invitation. If the home unit uses `rendezvous`
  snapshots, the bootstrap host also serves the file-tree manifest through the
  mailbox protocol; the joining node waits for an active host if necessary.
- **Retention.** Snapshots at the stable timestamp allow truncation of old
  file-system operations. A node that was inactive longer than retention
  bootstraps from a relay-stored snapshot or requests a rendezvous snapshot, then
  fetches the referenced content.

### SHARED_FOLDER SyncUnit

A shared folder is a normal unit with Viewer/Editor/Manager roles and
`SHARED_FILE_SET`/`SHARED_FILE_DELETE` content operations.

- **Publication.** Authorization operations (`INVITE`, `ACCEPT_INVITATION`,
  `CHANGE_ROLE`, `REMOVE_MEMBER`) and content operations share the per-source
  ordered log `unit/<unitId>/log/<nodeId>/<clock>`. Members read all active
  nodes' logs; authorization operations are always applied before content
  operations, exactly as today.
- **File content.** A `SHARED_FILE_SET` carries small content inline and
  references `content/<unitId>/<nodeId>/<sha256>` for larger content. The content blobs
  are encrypted with the unit key, which is distributed in the invitation and
  rotation is described under membership changes.
- **Snapshot.** When the folder uses the `relay` policy, the snapshot contains
  the authorization state (members, roles, pending invitations), the accepted
  operation clocks and a file index `(path, contentDigest, size)`; it is enough
  for a new member to build the folder without reading the full history. Large
  shared folders default to `rendezvous`: no snapshot is stored on the relay at
  any time, and the snapshot is streamed directly from an active member through
  the mailbox protocol. The policy is configurable per folder and per relay as
  described above.
- **Bootstrap of a new member.**
  1. The inviter publishes the `INVITE` authorization operation and sends the
     invitation direct message through the USER inbox, including unit ID, type,
     role, relay set and snapshot policy.
  2. The invitee's node requests the unit state from the inviter (or any active
     member) through the mailbox protocol and receives the snapshot plus the
     authorization log. For `rendezvous` folders this transfer is the only copy
     of the snapshot; the reply chunks expire after the ack and are never a
     durable relay snapshot.
  3. It verifies the invitation chain, replays authorization in deterministic
     order and applies the file index.
  4. It publishes its `ACCEPT_INVITATION` operation and active-set entry, then
     fetches missing content blobs.
  5. It may now serve other members' snapshot requests as well.
- **Membership changes.** Role changes and removals are ordinary published
  authorization operations. A removed node observes its own removal and deletes
  its local copy; it cannot delete relay history it already published. To limit
  future disclosure, the remaining members rotate the unit key and publish the
  new key encrypted to each remaining member. Old ciphertext remains readable by
  the removed member, matching the existing limitation in `users-and-nodes.md`.
- **Retention.** The stable cursor is the minimum over active members.
  Relay-stored snapshots compact operations. A `rendezvous` folder compacts logs
  against a snapshot that members materialize on demand, but keeps at least one
  active member able to serve it; an active member that falls behind retention
  must rejoin from a relay-stored or rendezvous snapshot.

### TODO_LIST SyncUnit

Todo items are small and have no large payloads, so this unit uses publication
only.

- **Publication.** Authorization operations plus `TODO_SET`/`TODO_DELETE` are
  appended to `unit/<unitId>/log/<nodeId>/<clock>`. Payloads are encrypted with
  the unit key, since the relay may be publicly readable.
- **Snapshot.** The snapshot is the authorization state, item map
  `(itemId, payload)` and per-source clocks. It is normally a single small blob.
- **Bootstrap of a new member.** Invitation arrives as a direct message; the
  invitee requests the snapshot plus authorization log by mailbox, verifies and
  replays it, publishes `ACCEPT_INVITATION`, and then polls the log. No
  rendezvous for bulk data is needed unless the item map exceeds the relay's blob
  limit, in which case the snapshot itself is chunked.
- **Retention.** Snapshots at the stable cursor allow dropping old `TODO_*`
  operations.

### MESSAGE_CHANNEL SyncUnit

Message channels append messages and may carry large attachments.

- **Publication.** Authorization operations and `CHANNEL_MESSAGE` operations are
  appended to `unit/<unitId>/log/<nodeId>/<clock>`. Small message text stays
  inline; attachments are published as `content/<unitId>/<nodeId>/<sha256>` and referenced
  from the signed message. Message text and attachments are encrypted with the
  unit key.
- **Snapshot.** The snapshot contains the authorization state, the conversation
  prefix (message IDs, authors, clocks and text) and the per-source clocks.
  Attachments are not embedded; only their digests are.
- **Bootstrap of a new member.** The invitation direct message names the unit
  and relays. The invitee requests the snapshot plus authorization log and the
  recent message tail by mailbox. If the relay can store the snapshot and the
  member can fetch the content blobs, no further transfer is needed; otherwise
  the inviter pages the message history through the mailbox protocol.
  The invitee then publishes `ACCEPT_INVITATION` and enters the normal loop.
- **Read cursors and truncation.** Each node publishes
  `unit/<unitId>/cursor/<nodeId>` with the highest message clock it has applied
  per source. The stable clock is the minimum over active members, and snapshots
  at that clock allow log truncation. Unlike direct messages, channel messages
  have no per-recipient acknowledgement; the cursor is the only receipt.
- **Attachments and retention.** Attachment blobs may expire earlier than the
  message log. A reader that needs an expired attachment requests the digest from
  any active member by mailbox; if no member still has it, the message remains
  but the attachment is reported as unavailable.

### Capability degradation

The patterns above assume only "store, read, optionally list, optionally
delete". The following fallbacks keep the system usable on weaker relays:

- **No listing.** Owners maintain the `index` blobs. Readers poll indexes only.
  For first contact during a user join, a relay without listing requires the
  credential to name a host node, because the new node has no peer whose index it
  could poll yet.
- **Own-prefix writes only.** All request, reply, log and pointer blobs are under
  the writer's own node prefix. No pattern requires writing into another node's
  namespace.
- **No delete.** Expired names are ignored by readers. A relay that never deletes
  is rotated when its storage cost grows, and nodes republish their pointers.
- **Small blob limit.** Operations, snapshots and replies are chunked with
  manifests. The relay's limit is part of its advertised capability record.
- **Slow or unreliable polling.** Requests are idempotent and resumable, so a
  missed poll only delays the transfer. A node backs off exponentially per relay
  and switches to another relay from the unit pointer set.
- **Read-only relays.** They can still be used as extra publication replicas for
  immutable log entries, snapshots and content blobs, while mailbox traffic uses
  a writable relay from the same set.

### Relationship to the current protocol

The mailbox body is the existing `RemoteSyncRequest`; the mailbox reply is the
existing `RemoteSyncResponse`. That keeps `UserSyncProtocol`, the per-source
clocks, the byte budget, segmented operations, content offsets and partial-state
persistence unchanged. The current direct TCP, Noise and Yamux path remains as a
low-latency transport when two nodes happen to be reachable, but the sync worker
must not depend on it: for every unit the relay path above is sufficient to
bootstrap, synchronize and exchange direct messages between two nodes.