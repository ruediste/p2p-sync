Goal:
- A SyncUnit is a unit of data that can be synchronized between nodes
- A User is a Sync Unit
- A SharedFolder is a Sync Unit
- In the future, there can be other Sync units, such as Development Projects (Git, Issues, CI pipelines,...), Message Channels, or TODO lists
- Each user defines which sync units they want to synchronize with their nodes (they want to participate in)
- Each node has a set of users it synchronizes. A node also synchronizes all the sync units of those users.

Shared Folder:
- Permission levels: Viewer, Editor, Manager
- A Viewer can only view the contents of the shared folder
- An Editor can view and modify the contents of the shared folder
- A Manager can in addition manage the membership of the shared folder, including inviting and removing users

Todo Lists:
- same permissions as shared folders

Message Channels:
- A Message Channel is a sync unit for group communication
- Permissions system: only member / non members, not viewer/editor/manager distinction
- Only members can view and send messages in the channel
- Chat Messagesa
- Messages with an attachment (like files, images, etc)

Messaging System:
- There is a general messaging system to exchange messages between users
- The messages of a user are synchronized across all nodes that the user participates in
- The nodes of the sending user try to deliver messages to the nodes of the receiving user
- Once a message is successfully delivered to at least one node of the receiving user, it is considered delivered
- If a message cannot be delivered to any node of the receiving user, it remains in the sending user's outbox until delivery is possible
- There are different types of Messages 
  - Chat Messages
  - Mail Messages (like email: subject, body, attachments)
  - Shared Folder Invitations
  - Message Channel Invitations
  - etc

Out of Scope for now: Development Project (Git, Issues, CI pipelines,...)