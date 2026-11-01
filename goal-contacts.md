# Goal: Contacts

This document specifies the contacts functionality: a per-user address book used
to identify people when sending mail, starting direct chats, inviting users to
SyncUnits, and displaying user identities. It also records the concrete design
decisions, edge cases, and open questions needed to implement the feature.

## Summary

- Each user has a contact list.
- The list belongs to that user only and is replicated with the user's User
  SyncUnit.
- A contact has an optional user ID and optional descriptive fields.
- Contact changes are signed User SyncUnit operations.
- Contacts are used wherever the UI currently asks for a raw user key.
- Searching contacts matches any field, not only the display name.

## Scope

In scope:

- Creating, reading, updating, searching, and deleting contacts.
- Replicating contacts across all nodes configured for the owning user.
- Creating a contact from a known user (local user, SyncUnit member, message
  peer, or manually entered user key).
- Using contacts in mail, direct chat, and SyncUnit invitation flows.
- Resolving message senders/recipients and SyncUnit members to contact display
  names.

Out of scope for the first implementation:

- Sharing a contact list between users.
- Exporting/importing vCard or CSV files.
- Contact groups, distribution lists, or multiple user IDs per contact.
- Server-side or third-party address book synchronization.
- Automatic contact discovery from the network.

## Terminology

- **Owner:** the user whose contact list is being read or modified.
- **Contact ID:** the immutable internal identity of a contact within the
  owner's list. It is a full GUID (node public-key hash plus per-node counter),
  generated the same way as file/directory entity IDs.
- **Contact user ID:** the public key of the network user represented by the
  contact. This is the value used as a recipient/participant identity.
- **Linked contact:** a contact whose user ID is set.
- **Unlinked contact:** a contact whose user ID is empty.

A contact user ID is not the same as a local user name. Local user names are
node-local labels; they are neither replicated nor used as network identity.

## Data Model

A contact has the following fields:

| Field | Required | Content |
| --- | --- | --- |
| contact ID | yes | Internal immutable GUID, not edited by the user |
| user ID | no | Public key of the represented user |
| first name | no | Free-form text |
| last name | no | Free-form text |
| email | no | Free-form text; not used as a network address |
| phone number | no | Free-form text |
| notes | no | Free-form text |

All user-visible fields are optional in the sense that they may be empty. A
contact with a user ID is usable for network operations. A contact without a
user ID is still a valid address-book entry, for example an email/phone contact
that has not yet been linked to a user.

The first implementation stores at most one email address and at most one phone
number per contact. Multiple values per contact require a later model change.

### Uniqueness

Within one owner's contact list:

- Contact IDs are unique.
- At most one contact may have a given non-empty user ID.
- Any number of contacts may be unlinked (empty user ID).
- A contact must not reference the owner's own user ID as its user ID. The owner
  is displayed as "you" where relevant and does not need a self-contact.

Uniqueness is a materialized-state invariant, not just a UI check. It must be
enforced identically on every node that replays the owner's operations. See
[Conflict Resolution](#conflict-resolution).

### Display Name

The UI derives a display name in this order:

1. `first name + last name` (omit empty parts).
2. The email address.
3. The phone number.
4. `User <short user key>` if a user ID is present.
5. `Contact <short contact ID>` for an otherwise empty contact.

The display name is derived and is not stored. Search uses the individual
stored fields, so searching works even when a field is not part of the displayed
name.

## Replication and Storage

Contacts are part of the owning user's replicated data, not node-local state and
not SyncUnit content of another unit.

### Operation types

Add two signed User SyncUnit operation kinds:

- `CONTACT_SET`: create or fully replace one contact.
- `CONTACT_DELETE`: delete the contact with the given contact ID.

`CONTACT_SET` carries the contact ID and all contact fields. It is a full
replacement rather than a partial patch. This keeps replay deterministic and
avoids merging rules for individual fields.

`CONTACT_DELETE` carries only the contact ID. Deleting an unknown contact is a
no-op.

Contact operations are authored and signed by the owner, exactly like status,
file-system, direct-message, and participation operations. The existing receive
path already verifies that the operation signature belongs to the User
SyncUnit's user, so no new cross-user authorization rule is needed. A node must
not apply a contact operation authored by any other user.

### Materialized state

The owner's materialized contact list is rebuilt from:

1. the contact list stored in the user snapshot state, and
2. the owner's operation log applied in total operation order.

Do not represent contacts as files in the user file tree. Doing so would lose
structured uniqueness/search guarantees and would let file operations bypass
contact validation.

`DataUserRoot` (or an equivalent user state message) must expose the
materialized contact list to readers. The contact list is not included in file
listings and must not be exposed as a pseudo-file unless a later requirement
explicitly asks for it.

### Snapshot and log truncation

Contact operations are ordinary truncatable operations. They must not be
excluded from stable-log truncation the way direct messages and participation
operations currently are.

When a contact operation is removed from the main user operation log, its effect
must already be present in the snapshot state. Concretely:

- Extend the user snapshot state (currently `UserTreeState`, or introduce a
  `UserState` wrapper) with the materialized contact list.
- `truncateStableLog()` must replay file-system and contact operations into the
  new snapshot before removing them.
- `mergeStaging()`, `rebuildCurrentState()`, and persisted-state loading must
  rebuild the contact list from the snapshot plus remaining operations.

Without this, contacts would disappear from a node after log truncation even
though their operations were removed as "stable".

### Messages and invitations

Mail and invitation messages continue to carry only user IDs, not contact
details. Contact data stays on the owner's nodes. Recipient/sender labels are
resolved against the current local user's contact list when displaying a
message.

## Conflict Resolution

Operations are applied in the existing total order: Lamport clock first, then
node hash. Within one contact ID, the last operation in that order wins.

- Concurrent `CONTACT_SET` operations on the same contact: the later operation
  replaces the earlier contact completely.
- `CONTACT_SET` after `CONTACT_DELETE` for the same contact ID: the set
  recreates the contact. This is intentional; the set is a later user action.
- `CONTACT_DELETE` after `CONTACT_SET`: the contact is removed.
- Concurrent `CONTACT_SET` operations that assign the same non-empty user ID to
  different contact IDs: the later operation keeps the user ID; the earlier
  contact loses its user ID (becomes unlinked) and otherwise remains unchanged.

The duplicate-user-ID rule must be deterministic during replay. A local edit
should reject an attempt to assign an already-used user ID and tell the user
which contact already owns it. Remote operations that create a duplicate during
replay are resolved with the clearing rule above rather than rejecting the
operation, so all nodes converge.

All nodes must derive the same result from the same operation set. Conflict
resolution must not depend on local contact IDs, local user names, wall-clock
time, or whether a referenced user is currently configured.

## API

Add contact operations to `NodeUserHandle` (or a small `ContactService` used by
it). Proposed surface:

- `List<Contact> listContacts()`
- `Optional<Contact> getContact(ContactId contactId)`
- `Optional<Contact> findContactByUserId(PubKey userId)`
- `List<Contact> searchContacts(String query)`
- `Contact createContact(...)` — all fields optional
- `Contact createContactFromUser(PubKey userId)` — creates or returns the
  existing contact for that user ID
- `void updateContact(ContactId contactId, ...)`
- `void deleteContact(ContactId contactId)`

Rules:

- Local mutations validate user-ID uniqueness and reject self-references.
- Creating a contact from a user ID that already has a contact returns the
  existing contact instead of creating a duplicate.
- Updating a contact with a user ID already owned by another contact is
  rejected locally with a clear error.
- All mutations record a signed User SyncUnit operation and rebuild the
  materialized view. No contact mutation is node-local.

The GUI should use `searchContacts`/`listContacts`, not construct contact
records itself.

## User Interface

### Contacts panel

Add a per-user contacts view, reachable from the existing per-user navigation.
It provides:

- A search field matching all contact fields.
- A list showing display name, email/phone (when present), and linked-user
  status.
- Create/edit form with all fields, including a user-key field.
- Delete action.
- "New contact from user" action accepting a user key or selecting a known
  user.

The panel must not require the user to scroll through raw keys for ordinary
selection.

### Recipient selection

Replace raw recipient-key entry in these flows with a searchable contact
picker:

- Mail compose "To" field.
- Direct chat creation.
- SyncUnit invitation.

Picker behavior:

- Searches all contact fields case-insensitively.
- Shows unlinked contacts but marks them as not usable for network operations.
- Allows selecting only linked contacts for mail/chat/invitation.
- Offers an inline "create contact from this key" action when the user has a
  user key that is not yet a contact.
- Disambiguates contacts with the same display name by showing the short user
  key or email.

Whether raw-key sending must remain available as an advanced path is an open
question. If it remains, it should create or link a contact rather than silently
bypassing the contact list.

### Label resolution

Where a user ID is displayed, resolve it as follows using the current local
user's contacts:

1. If it is the current user, show "you".
2. If a contact with that user ID exists, show its display name.
3. If a local user with that user ID exists, show that node-local name.
4. Otherwise show `User <short key>`.

This applies to mail headers, chat sender names, direct-chat conversation
titles, SyncUnit member lists, invitation confirmations, and message selection
lists.

### Creating contacts from existing users

A contact can be created from:

- a SyncUnit member's user ID,
- a mail/chat sender or recipient,
- a local user's user ID,
- a manually entered user key.

Only the user ID is pre-filled. Node-local names and message content are not
copied into contact names, because they are local or may be misleading. The user
may edit the names afterwards.

Inbound messages and existing conversations do not automatically create
contacts. They display a fallback label and offer an "add contact" action. This
avoids silently growing the address book with every peer.

## Validation and Limits

- Trim leading/trailing whitespace from all text fields on local edit.
- Preserve user-entered capitalization in stored values.
- Search is case-insensitive.
- Email is not validated as deliverable mail; only a basic sanity check is
  needed because it is a descriptive field.
- Phone numbers are stored as entered; no E.164 normalization in the first
  implementation.
- Validate a user ID as an encoded public key before recording an operation.
  Do not require the referenced user to be configured locally or reachable;
  contacts may be prepared for future invitations.
- Reject malformed contact operations before they can corrupt materialized
  state. A bad payload must not abort replay of unrelated operations.
- Enforce field and total-record size limits so a single user operation cannot
  grow unbounded. The exact limits are an open question; initial suggestions are
  1 KiB per text field and 8 KiB per contact.

An empty contact (no user ID and no text fields) is probably not useful. Reject
empty local creates, or require at least one field; see open questions.

## Security and Privacy

- Contacts are visible only to the owning user's nodes. They are not shared with
  other users on the same node and are not published to peers that do not
  synchronize the owner.
- Other users on the same node must not be able to read or modify a user's
  contacts through the API or UI.
- A contact's email/phone/notes are never transmitted as part of a message or
  invitation.
- User IDs are public keys and are safe to share; contact lists are not.
- The DataStorage file remains unencrypted at rest, consistent with existing
  user data. Contact privacy therefore depends on local file protection, not on
  application-level encryption.

## Edge Cases

- Deleting a contact that does not exist: no-op; do not emit an error to remote
  replays.
- Editing a contact that was concurrently deleted on another node: the
  operation order decides. A later set recreates it; a later delete removes it.
- Two contacts assigned the same user ID concurrently: later operation wins;
  earlier contact is unlinked deterministically.
- Contact references a user ID that is not known locally: allowed; the contact
  is still displayed and can be used to send messages if transport can reach
  the user.
- Contact references the owner: reject on local edit.
- Malformed operation payload: reject that operation without preventing other
  operations from being processed.
- Log truncation after contact edits: contacts must survive via snapshot state.
- Replaying the same operation twice: idempotent, because `CONTACT_SET` fully
  replaces and `CONTACT_DELETE` is a no-op for missing contacts.

## Implementation Notes

Likely touch points in the current code:

- `src/main/proto/sync.proto`:
  - add `UserOperation.Kind.CONTACT_SET` and `CONTACT_DELETE`;
  - add contact fields or a nested `Contact` message to `UserOperation`;
  - add the materialized contact list to `DataUserRoot` and to the snapshot
    state (`UserTreeState` or a new `UserState`).
- `NodeUserHandle` / `NodeUserData`: materialized contacts, mutation API, replay.
- `Node`: recording operations, rebuilding state, snapshot truncation, loading
  and persisting contacts.
- `UserFileTree` / snapshot handling: either keep contacts outside the file tree
  and combine them in a new user-state builder, or extend the snapshot type
  carefully without treating contacts as files.
- `MailPanel`, `ChatPanel`, `UnitsPanel`: contact picker and label resolution.
- New `ContactsPanel` and per-user navigation entry.

Suggested implementation order:

1. Proto and materialized contact state.
2. Replay, uniqueness, and snapshot/truncation support.
3. `NodeUserHandle` contact API and unit tests.
4. Display-name resolution in existing panels.
5. Contact picker in mail/chat/invitation flows.
6. Contacts management panel.
7. Cross-node integration test for replication and truncation.

## Acceptance Criteria

- A user can create, edit, search, and delete contacts.
- All contact fields except the internal contact ID are optional.
- A contact can be created from a user ID and pre-fills that user ID.
- Two contacts cannot share a non-empty user ID after local edits.
- Contacts replicate between nodes configured for the same user and converge
  after concurrent edits/deletes.
- Contact operations survive stable-log truncation via the snapshot.
- Another local user cannot see or modify the owner's contacts.
- Mail, direct chat, and SyncUnit invitations can select a linked contact by
  searching any contact field.
- Message and member views show contact display names, with a stable fallback
  for unknown users.
- Unit tests cover replay determinism, duplicate user-ID resolution, idempotent
  apply, and snapshot round-trips.
- An integration test covers contact replication across two nodes.

## Open Questions

1. **Replicated vs node-local contacts.** This document assumes contacts are
   replicated with the owner's User SyncUnit. Should contacts instead be
   node-local? Replication is recommended so a user's address book follows them
   across devices. => replicated
2. **Duplicate-user-ID behavior.** Is the "later operation wins, earlier contact
   becomes unlinked" replay rule acceptable, or should a duplicate remote
   operation be ignored entirely? The current rule guarantees convergence while
   preserving the earlier contact's descriptive data.
3. **Multiple values.** Should email and phone be single-valued (recommended for
   the first implementation) or lists of values? => single-valued
4. **Empty contacts.** Should a contact with no fields at all be rejected? => reject
5. **Raw-key sending.** Should mail/chat/invitation still allow sending to a raw
   user key without creating a contact? Recommended: require linking a contact,
   with an inline "create contact from key" shortcut. => use recommended
6. **Automatic contact creation.** Should inbound message senders be added to
   contact list automatically? Recommended: no; display a fallback label and
   offer to add. => use recommended
7. **Local users in the picker.** Should locally configured users appear in the
   contact picker even if they are not contacts? Recommended: show them in a
   separate "local users" group or via the same picker with clear labeling. => use recommended
8. **Self-contact.** Should a contact for the owner's own user ID be allowed?
   Recommended: no. => use recommended
9. **Field limits and normalization.** What exact per-field and per-contact size
   limits should apply, and should email/phone be normalized at all? => no limits/no normalization
10. **Restricting user IDs.** The original goal said a user ID "can be set to the
    id of a user the node synchronizes". Should linked contacts be restricted to
    any valid key, because invitations and future contacts need to be prepared
    in advance. => use recommended
    in advance.
11. **Contact list representation.** Should the contact list ever be exposed as a
    file or export format in the user tree, or remain API/UI-only? => remain API/UI-only
12. **Mail recipients.** The current mail flow has one recipient. Should the
    contact work introduce multiple recipients or contact groups at the same
    time? Recommended: defer to a separate goal. => use recommended

General: for contact updates, use updates of individual fields instead of whole contact replacement.