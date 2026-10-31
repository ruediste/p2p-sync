# Implementation Plan

## Goals and Concepts

- A **SyncUnit** is an independently replicated unit of data.
- For replication, a **User** is a SyncUnit. A user also has a cryptographic identity: its public key identifies it and its private key signs its operations.
- A **SharedFolder**, **TodoList**, and **MessageChannel** are also SyncUnits. Other kinds may be added later.
- A user has a participation list of SyncUnits. A node is configured with the users it synchronizes and synchronizes every SyncUnit in those users' participation lists.
- Any user may create a SyncUnit and invite other users to participate in it. The receiving node verifies each operation's signature and checks that its author is authorized for that SyncUnit and operation. For example, only an Editor or Manager may modify a shared folder.
- Removing a participant removes that SyncUnit's replicated data from the participant's active local store. This cannot prevent the participant from retaining a copy made before removal.

## Permissions and Features

### Shared Folders and Todo Lists

- **Viewer:** may view contents.
- **Editor:** may view and modify contents.
- **Manager:** may view and modify contents, and manage membership by inviting or removing users.
- TodoLists use the same permission levels as SharedFolders.

### Message Channels

- A MessageChannel supports group communication with **Member** and **Manager** roles.
- Both Members and Managers may view and send chat messages, including messages with file or image attachments.
- Managers may invite, remove, or change the role of any member, including other Managers. At least one Manager must remain.

### Direct Messaging

- Users exchange messages through their User SyncUnits. A user's messages synchronize to all nodes that synchronize that user.
- Sending nodes automatically retry delivery to nodes of the recipient user. Each message receives a unique ID when created so duplicates can be detected.
- A message is delivered once it has been stored on at least one recipient node. Until then, it remains in the sender's outbox.
- Message types include chat and mail (subject, body, and attachments), plus invitations such as SharedFolder and MessageChannel invitations.

## Milestones

1. **Specify the SyncUnit and authorization model.** Define stable unit identity and type, participation and membership records, operation signatures, authorization checks, and how membership changes affect authorization. Document the distinction between a user's cryptographic identity and the User SyncUnit. Completion: the rules cover create, invite, accept, role change, removal, and an operation arriving out of order, with focused tests for authorization decisions.
2. **Implement SyncUnit storage and replication boundaries.** Scope operation histories, snapshots, and synchronization state to a SyncUnit; validate author signatures and authorization before accepting incoming operations. Completion: tests show independent units do not share operation state and unauthorized or invalidly signed operations are rejected.
3. **Implement participation and node selection.** Store each user's SyncUnit participation list and have a node synchronize all units of its configured users. Completion: tests show adding/removing a participating unit changes synchronization selection, while a node configured for another user is unaffected.
4. **Deliver SharedFolder as the first shared-unit vertical slice.** Implement creation, invitation acceptance, Viewer/Editor/Manager operations, membership changes, and removal of the local replicated copy. Completion: multi-node tests cover role enforcement, invitation flow, replication, and removal; document that retained external copies cannot be recalled.
5. **Add TodoLists and MessageChannels.** Reuse the SyncUnit and membership foundations, applying their specified permissions and channel message/attachment behavior. Completion: tests cover access checks and cross-node convergence for each unit type.
6. **Implement direct messaging and reliable delivery.** Add unique message IDs, recipient-node storage acknowledgements, retry/outbox behavior, duplicate detection, and the defined invitation message types. Completion: tests cover retry after an unavailable recipient, duplicate delivery, and the delivered threshold of one recipient node storing the message.

Development Projects (Git, issues, CI pipelines, and related data) are out of scope for now.