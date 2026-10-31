# Users and Nodes
This document describes how nodes and users interact, and what data structures exist to support these interactions.

- A node can support multiple users
- each user has a unique name associated. The name is local to the node
- In the FUSE filesystem, each user is represented by a separate directory under the root directory of the node. The directory name corresponds to the user's unique name on the node

For each user
- There is the main operation list for the user, which tracks the combined operations performed by the user across all nodes it interacts with
- There is the local operation list, which contains the operations performed by the user on the current node
- The local file system, containing the current state of the user's files on the node, including the local operations. This is the basis for the FUSE filesystem representation of the user's files on the node. 
- The operation staging area, which temporarily holds operations which are being downloaded from other nodes
- Periodically, the local operations and the operations in the staging area are merged into the main operation list for the user. After the merge, the local file system is updated to reflect the combined state of the user's files. The local operation list and the staging area are then cleared.

The node offers a management interface. On startup, the node mounts the FUSE filesystem and hooks into status tray etc.

Summary Recommendation for a P2P Tool
- Go with JavaFX for a lightweight, fast, native-feeling desktop app. 
- Pair it with dorkbox/SystemTray to solve Linux-specific tray/indicator challenges reliably.

The interface allows to show the user private key, and add user accounts to the node.