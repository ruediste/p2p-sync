# Users and Nodes

This document describes the intended relationship between users, nodes, and the
user data exposed by a node. It distinguishes that design from the current
prototype, which does not yet implement the complete synchronization workflow.

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

Local changes are recorded as operations. Remote operations are first placed in
the incoming staging area. A reconciliation step incorporates eligible local
and incoming operations into the main history, then updates the materialized
file tree. Local and staged entries can be cleared only after their operations
are durably represented in the main history. The exact ordering, duplicate
detection, conflict resolution, and failure-recovery rules are part of the sync
protocol and must be specified there; this document does not prescribe them.

## Node Interface

The intended user-facing interface provides a separate directory for each
configured user, named with that node's local user name. Applications working
in a user's directory see that user's materialized file tree, not the operation
logs or staging data.

A node also needs a management interface for adding or joining user identities,
configuring local names, and reporting node and synchronization status. Private
keys should be handled as credentials and must not be exposed in ordinary
status views. Mounting a filesystem or showing a desktop status indicator are
deployment and interface choices; they are not prerequisites for the identity
or synchronization model.

## Current Implementation

The current node code is a prototype of the identity and root-data model:

- [`Node`](../src/main/java/com/github/ruediste/p2psync/node/Node.java) identifies
  users by public key in a node-local map. `createNewUser()` generates an
  Ed25519 key pair; `join()` looks for a peer hosting the same public key.
- [`StorageUserRoot`](../src/main/java/com/github/ruediste/p2psync/node/StorageUserRoot.java)
  contains block identifiers for user data structures. [`DataUserRoot`](../src/main/java/com/github/ruediste/p2psync/node/DataUserRoot.java)
  currently contains a status message and a root-directory identifier.
- [`NodeUserHandle`](../src/main/java/com/github/ruediste/p2psync/node/NodeUserHandle.java)
  provides prototype read and modify operations. Its synchronization method is
  incomplete, and the node's [`BlockStorage`](../src/main/java/com/github/ruediste/p2psync/node/BlockStorage.java)
  is currently in-memory storage.
- The operation histories, incoming staging area, reconciliation process,
  node-local user names, and multi-user filesystem view described above are not
  implemented yet. [`SimpleFuseFS`](../src/main/java/com/github/ruediste/p2psync/storage/SimpleFuseFS.java)
  is a standalone storage-backed filesystem prototype, not the per-user view.

Consequently, the synchronization lifecycle and management interface above
describe the intended architecture, not functionality available in the current
prototype.