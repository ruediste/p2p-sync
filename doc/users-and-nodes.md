# Users and Nodes

This document describes the relationship between users, SyncUnits, and nodes,
along with the current implementation boundary.

## Identities and Names

A **user** has a cryptographic identity represented by a key pair. The public
key identifies the user across nodes, and the private key signs operations
authored by that user. For replication, a User is also a **SyncUnit**: the
replicated unit containing that user's own data. Identity and replicated data
are distinct concepts even though they are represented together in this model.

A **SyncUnit** is an independently replicated unit of data, such as a User,
SharedFolder, TodoList, or MessageChannel. A **node** is a running network
participant with its own peer identity and local storage. A node may store
multiple users' SyncUnits, and a SyncUnit may be stored on multiple nodes.

A node-local user name is a display and filesystem label, not a network
identity. The same user can have different names on different nodes. Names
must be unique within a node so they can be used as directory names in a
node-level filesystem view.

## Participation and Authorization

Each user maintains a list of SyncUnits they participate in. A node is
configured with the users it synchronizes; it synchronizes every SyncUnit in
those users' participation lists. Thus, unit participation is a user-level
choice, while node selection is made by choosing users.

Operations are authored and signed by users, even when a node relays or stores
them. Before accepting an operation for a SyncUnit, a receiving node verifies
the signature and checks that the author is authorized for that operation under
the unit's membership and permission rules. For example, a shared-folder Editor
may modify contents, while a Viewer may not. The rules for membership changes
and operations arriving out of order must be specified by the SyncUnit
authorization model.

Removing a user's participation removes that SyncUnit's replicated data from
their active local store. It cannot prevent the user from keeping a copy made
before removal.

## User Data and Synchronization

The intended design separates each SyncUnit's replicated operation history
from its materialized view. The User SyncUnit's materialized view is the user's
file tree. For each replicated unit, the design calls for:

- **Main operation history:** the combined history of accepted, authorized
  operations for that SyncUnit.
- **Local operation history:** operations authored on this node that have not
  yet been incorporated into the main history.
- **Incoming staging area:** operations received from other nodes, held until
  they can be validated and reconciled.
- **Materialized view:** the current local view derived from that unit's main
  history. For a User SyncUnit, this is the user's file tree.

Local changes are signed operations ordered by Lamport clock and author identity.
Incoming operations are verified and authorized before reconciliation replays
them in the unit's operation order. A stable snapshot can replace the prefix of
the main log acknowledged by every active node participating in that unit.
`doc/storage-sync.md` describes the transfer, membership, snapshot, and conflict
rules.

## Node Interface

The intended user-facing interface provides a separate directory for each
configured user, named with that node's local user name. Applications working
in a user's directory see that user's materialized file tree, not the operation
logs or staging data. Other SyncUnit types may provide different interfaces.

A node provides APIs for creating and joining a user, setting a unique local
name, and reading redacted node and synchronization status. Its desktop console
shows those statuses, offers local user management, and places a status menu in
the system tray when the desktop environment supports one. Node storage is not
password-locked; private signing keys are stored in the local DataStorage file
and are not included in status summaries. Portable credential exports are
separately password-protected.

## Current Implementation

The current node implementation still models synchronized data primarily as
user data; generic SyncUnit participation, shared-unit authorization, and
cross-user message delivery are planned work. Existing local-node mechanisms
include:

- [`Node`](../src/main/java/com/github/ruediste/p2psync/node/Node.java) identifies
  users by public key in a node-local map. `createNewUser()` generates an
  Ed25519 key pair; `join()` looks for a peer hosting the same public key.
- [`StorageUserRoot`](../src/main/java/com/github/ruediste/p2psync/node/StorageUserRoot.java)
  contains block identifiers for user data structures. [`DataUserRoot`](../src/main/java/com/github/ruediste/p2psync/node/DataUserRoot.java)
  currently contains a status message and a root-directory identifier.
- [`NodeUserHandle`](../src/main/java/com/github/ruediste/p2psync/node/NodeUserHandle.java)
  records signed status and filesystem operations, transfers them in per-origin
  order with resumable truncation, reconciles the main/local/staging histories,
  and creates causally stable snapshots.
- [`BlockStorage`](../src/main/java/com/github/ruediste/p2psync/node/BlockStorage.java)
  is a content-addressed adapter over `DataStorage`. Its hash index and typed
  chunk/manifest blocks are stored in the same file as the node catalog and
  user roots. The node's own DataStorage file is not encrypted; exported user
  credentials use password-derived AES-GCM encryption.
- [`NodeFuseFS`](../src/main/java/com/github/ruediste/p2psync/node/NodeFuseFS.java)
  exposes `/<local-user-name>/...` as isolated per-user trees.
  [`NodeDesktopApp`](../src/main/java/com/github/ruediste/p2psync/node/NodeDesktopApp.java)
  provides the management dashboard and optional tray status menu.
- [`UserSyncProtocol`](../src/main/java/com/github/ruediste/p2psync/node/UserSyncProtocol.java)
  carries framed protobuf sync requests over the existing TCP, Noise, and Yamux
  host. The desktop launcher enables mDNS discovery; remote joins can probe known
  peers for the imported user ID or use an explicit peer ID and optional listen
  multiaddress with a password-protected user credential file. The dashboard can
  initiate synchronization with known active peers and reports pending local and
  incoming operation counts in both the table and tray menu.

The [`Node.Network`](../src/main/java/com/github/ruediste/p2psync/node/Node.java)
in-process network remains for tests. The desktop console runs the real host and
sync protocol for separate processes; peers must be reachable over the configured
TCP multiaddress, and mDNS discovery is limited to the local network.