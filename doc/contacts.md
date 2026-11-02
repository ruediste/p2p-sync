# Contacts

This document describes the per-user contact list: its data model,
replication, conflict handling, API, and desktop UI integration.

## Overview

Each user has a contact list that is replicated with the user's User SyncUnit.
Contacts are not node-local and are not shared with other users on the same
node. They are used to identify recipients for mail and direct chat, to invite
users to SyncUnits, and to display user names throughout the desktop UI.

A contact may be linked to a network user through a user ID or may exist only as
a descriptive address-book entry with an email address, phone number, or notes.

## Data Model

Each contact has a stable internal contact ID and the following fields:

| Field | Required | Description |
| --- | --- | --- |
| contact ID | yes | Internal immutable GUID, 40 bytes, stored as a hex string |
| user ID | no | Public key of the represented network user |
| first name | no | Free-form text |
| last name | no | Free-form text |
| email | no | Free-form text; not used as a transport address |
| phone number | no | Free-form text |
| notes | no | Free-form text |

The contact ID is generated like file and directory entity IDs: the node public
key hash followed by a per-node counter. Local edits trim leading and trailing
whitespace from text fields. Empty values are stored as empty strings; an unset
user ID is stored as `null`.

A contact must have at least one non-empty field. Empty local creates and
updates that would clear the last field are rejected. An update replayed from a
remote node that leaves a contact empty removes that contact, preserving the
same invariant on every node.

The first implementation stores at most one email address and one phone number
per contact. There are no size limits on text fields and no email or phone
normalization.

### Uniqueness

Within one user's contact list:

- Contact IDs are unique.
- At most one contact may have a given non-empty user ID.
- Any number of contacts may be unlinked.
- A contact must not reference the owner's own user ID.

Local mutations reject duplicate or self-referencing user IDs. Replay resolves
conflicts deterministically as described below.

### Display Name

The display name is derived and not stored:

1. First name and last name, omitting empty parts.
2. Email address.
3. Phone number.
4. `User <short user ID>` when a user ID is present.
5. `Contact <short contact ID>` for an otherwise empty contact.

Search uses the individual fields plus the contact and user IDs, so a match is
possible even when the matching text is not part of the display name.

## Replication

Contact changes are signed User SyncUnit operations authored by the owning user,
like status, file-system, direct-message, and participation operations. The
existing operation receive path verifies that the signature belongs to the
User SyncUnit's user, so a contact operation authored by any other user is
rejected.

The operation kinds are:

- `CONTACT_CREATE`: creates a contact with its initial field values. The
  operation carries the complete contact record and is the only operation that
  does so.
- `CONTACT_UPDATE`: updates one individual field of an existing contact. A text
  field is cleared with an empty value; the user ID is cleared with an empty
  byte value. Updates to different fields are independent.
- `CONTACT_DELETE`: deletes the contact with the given contact ID. Deleting a
  missing contact is a no-op.

Using per-field updates avoids replacing unrelated fields when two nodes edit
the same contact concurrently.

### Materialized State

The materialized contact list is rebuilt from:

1. the contact list stored in the user snapshot (the `UserSnapshot` protobuf
   message, not a separate class), and
2. contact operations in the user's main/local/staging operation histories,
   applied in total operation order.

`DataUserRoot` exposes the materialized contact list to readers. Contacts are
not represented as files in the user file tree and are not included in file
listings.

`UserState` combines the file tree and the contact list. It is used by staging
merges, local-state rebuilds, snapshot creation, joining, and state restoration.

### Snapshot and Log Truncation

Contact operations are ordinary truncatable operations. When an operation is
removed from the main user history during stable-log truncation, its effect is
already present in the user snapshot. The `UserSnapshot` protobuf message stores
both the file-tree snapshot and the materialized contact list, so contacts
survive truncation and node restarts.

## Conflict Resolution

Operations are applied in the existing total order: Lamport clock first, then
node hash. Within one contact and one field, the later operation wins.

- Concurrent updates to the same field: the later value wins.
- Concurrent updates to different fields: both changes survive.
- `CONTACT_CREATE` after `CONTACT_DELETE` for the same contact ID recreates the
  contact; the create is the later user action.
- `CONTACT_DELETE` after a create or update removes the contact.
- Concurrent operations that assign the same non-empty user ID to different
  contacts: the later operation keeps the user ID, and the earlier contact
  becomes unlinked while retaining its other fields.

Resolution is deterministic and depends only on the operation set, not on local
contact IDs, local user names, wall-clock time, or whether a referenced user is
configured locally.

Malformed contact operations are ignored during replay so that they cannot break
processing of the rest of the user's history.

## API

Contact operations are exposed through `NodeUserHandle`:

- `listContacts()`
- `searchContacts(String query)`
- `getContact(String contactId)`
- `findContactByUserId(PubKey userId)`
- `createContact(PubKey userId, String firstName, String lastName, String email, String phone, String notes)`
- `createContactFromUser(PubKey userId)`
- `updateContact(String contactId, PubKey userId, ...)`
- `deleteContact(String contactId)`
- `contactLabel(PubKey userId)`

`createContact` rejects empty contacts, self-references, and user IDs already
linked to another contact. `createContactFromUser` returns the existing contact
when the user is already linked. `updateContact` emits one signed operation per
changed field and returns the rebuilt contact. `contactLabel` resolves "you",
the linked contact's display name, a local user name, or a short user ID.

`Node.getUser(PubKey)` returns a handle for a configured user. `NodeUserHandle`
does not expose another user's contact list.

## Desktop UI

The per-user **Contacts** section provides:

- A search field matching any contact field.
- A list showing display name, email or phone, and linked status.
- An inline form for creating and editing contacts.
- A user-ID field for linking a contact.
- Delete support.

`ContactPicker` is used where the UI previously asked for a raw user ID:

- Mail compose recipient.
- New direct chat.
- SyncUnit invitation.

The picker searches contacts and also lists other locally configured users.
Unlinked contacts remain visible but cannot be selected for network operations.
A user ID can be linked inline. Sending mail, starting a chat, or inviting a
user requires a linked contact or a linked local user.

Labels in mail headers, chat conversations, SyncUnit member lists, and message
views resolve through the current user's contacts first, then local user names,
then a short user ID. Inbound messages do not automatically create contacts;
the user can add one explicitly.

## Privacy

Contacts are visible only to nodes configured for the owning user. A contact's
email, phone number, and notes are never included in messages or invitations.
Mail and invitation messages carry only user IDs. As with other user data, the
DataStorage file is not encrypted at rest; contact privacy depends on local file
protection.

## Tests

`ContactsTest` covers creation, per-field updates, search, deletion, user-ID
uniqueness, self-reference rejection, replication between nodes, snapshot
survival across truncation and restart, deterministic duplicate-user-ID
resolution, and idempotent replay.
