# Users and Nodes

This document describes the relationship between users, nodes, and the user data
exposed by a node, along with the current implementation boundary.

## Identities and Names

A **user** is an identity represented by a cryptographic key pair. The public
key identifies the user across nodes; the private key authorizes operations on
that user's data. A **node** is a running participant in the network, associated
with its own peer identity and local storage. A node may host data for several
users, and one user's data may be hosted by several nodes.

A node-local user name is a display and filesystem label, not a network
identity. The same user can have different names on different nodes. Names
must be unique within a node so they can be used as directory names in a
node-level filesystem view.

## User Data and Synchronization

The intended design separates the replicated history of a user's data from the
materialized file tree shown to local applications. For each user on a node,
the design calls for:

- **Main operation history:** the combined history of accepted operations from
  all nodes participating in that user's data.
- **Local operation history:** operations authored on this node that have not
  yet been incorporated into the main history.
- **Incoming staging area:** operations received from other nodes, held until
  they can be validated and reconciled.
- **Materialized file tree:** the current local view of the user's files,
  derived from the main history and exposed to applications.

detection, conflict resolution, and failure-recovery rules are part of the sync
Local changes are signed operations ordered by Lamport clock and node hash.
Incoming operations are staged and verified before reconciliation replays them
in that total order. A stable snapshot can replace the prefix of the main log
acknowledged by every active node. `doc/storage-sync.md` describes the transfer,
membership, snapshot, and conflict rules.

## Node Interface

The intended user-facing interface provides a separate directory for each
configured user, named with that node's local user name. Applications working
in a user's directory see that user's materialized file tree, not the operation
logs or staging data.

A node provides APIs for creating and joining a user, setting a unique local
name, and reading redacted node and synchronization status. Its desktop console
shows those statuses, offers local user management, and places a status menu in
the system tray when the desktop environment supports one. Node storage is not
password-locked; private signing keys are stored in the local DataStorage file
and are not included in status summaries. Portable credential exports are
separately password-protected.

## Current Implementation

The current node implementation provides the following local-node mechanisms:

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