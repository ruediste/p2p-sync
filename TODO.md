# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/persistence.md`.

## Future hardening

- **Active-node bootstrap discovery.** Non-user units restrict peers to their
  active node set once it has more than one node, but fall back to probing all
  peers while bootstrapping. A dedicated discovery/bootstrapping rule would make
  this deterministic.
- **Per-block checksums.** The storage layer currently validates structure and
  offsets, but not block content. Add checksums if silent storage corruption must
  be detected.

## Review findings (2026-11-01)

The items below were found while reviewing the code against the documentation.
They are ordered by risk, not by subsystem. File references are from the repository
root.

### Security and authorization

- [ ] **[high] Authenticate remote sync requests.** `Node.handleRemoteSyncRequest`
  trusts `request.getUserId()` / `request.getRequesterUserPublicKey()` and never
  checks the Noise-authenticated remote peer. Anyone who knows a user's public key
  can fetch contacts, history and file-tree entries. Bind the request to the peer
  identity or sign it (`Node.java:286-342`, `UserSyncProtocol.java:37-45`).
- [ ] **[high] Stop broadcasting direct messages to unrelated peers.**
  `deliverPendingMessages` sends the full message (body and attachments) to every
  configured/address-book peer until one reports `directMessageStored`; peers that
  do not host the recipient still receive the plaintext. Resolve the recipient's
  peers first, or encrypt to the recipient (`Node.java:1067-1089`).
- [ ] **[high] Do not trust `joiningNodeId`.** The joining node id is added to the
  user's active set without proof of control of that node key. A malformed value
  also makes `truncateStableLog` throw in `HexFormat.parseHex`
  (`Node.java:362-366`, `NodeUserHandle.java:493-496`).
- [ ] **[high] Restrict `syncUnitUpdate` to members.** An invitee can currently
  push a full unit update, including clock, metadata, partial operations and the
  active-node set, before accepting the invitation. `mergeSyncUnit` also throws on
  invalid content, making this a denial-of-service path
  (`Node.java:288-304`, `Node.java:1414-1456`).
- [ ] **[high] Validate active-node-set merges.** `ActiveNodeSet.mergeFrom` copies
  nodes, leaving state and `lastSeen` from any peer, and accepts higher phases
  without authentication. Removed nodes can be re-added and a peer can advance a
  leave process (`ActiveNodeSet.java:66-119`).
- [ ] **[high] Verify persisted user operations on load.** `loadOperationTree` and
  `loadOperations` copy every stored operation without checking its signature, and
  `restoreState` replays them into the file tree and contacts. A tampered
  `node.db` silently changes replicated state (`Node.java:2056-2072`,
  `Node.java:2403-2417`, `Node.java:2427-2433`).
- [ ] **[medium] Do not expose unpublished operations during initial sync.**
  `includeHistory` serves `data.allOperations()` (accepted plus local plus staging)
  while `doc/persistence.md` says the accepted history is transferred
  (`Node.java:342`, `Node.java:451-455`).

### Data integrity and crash safety

- [ ] **[high] Persist `nextNodeId` after it has been updated.** `persistState`
  builds `NodeState` with the old `user.nextNodeId`/`sharedFolderNextNodeId`, then
  `persistUserFileTrees` advances them. After a crash the persisted value is one
  generation behind, and `GuidMapper` trusts any non-zero value, so node ids can be
  reused. Shared-folder entity ids use a constant counter `0`, so a reused node id
  collapses two paths onto one `ShortGuid`
  (`Node.java:2082,2089` vs. `Node.java:1970-1981`, `GuidMapper.java:31-41`,
  `SharedFolderPersistence.java:225-230`).
- [ ] **[high] Serialize node mutations.** `UserSyncProtocol` starts a virtual
  thread per incoming stream, while the UI and sync worker call `Node` and
  `DataStorage.commit()` concurrently. `persistState`, `commit` and the data
  structures they mutate are not synchronized, despite the single-writer design
  (`UserSyncProtocol.java:37-45`, `Node.java:1921-1932`, `DataStorage.java:1806`).
- [ ] **[medium] Avoid unconditional persist on open.** `restoreState` ends with
  `persistState()`, rewriting the whole node document immediately after loading it
  (`Node.java:2424`).
- [ ] **[medium] Reduce duplicate persists.** Several operations persist and then
  call `rebuildCurrentState()`, which persists again; `syncFrom` persists in
  `mergeStaging` and again after message delivery. The documentation only mentions
  at most two commits per user write, but paths can do more
  (`NodeUserHandle.java:322-333`, `NodeUserHandle.java:468-489`).
- [ ] **[medium] Avoid zero-length operation segments.** When the byte budget is
  exhausted, `NodeUserData.serve` and `SyncUnitData.serveContent` emit an incomplete
  transfer with zero bytes. This creates a meaningless partial-operation state and
  an extra round trip (`Node.java:479-487`, `Node.java:619-641`).
- [ ] **[medium] Do not sync units twice.** The home unit is added to
  `participatingSyncUnitIds` and then synchronized both through the user path and
  the generic non-USER unit path. Two local users in the same shared unit sync it
  twice (`Node.java:1707`, `Node.java:1880-1899`).
- [ ] **[medium] Make `DataUserRoot.modify()` handle contacts.** `modify` compares
  only status and root id, so edits to `root.contacts` are silently discarded
  (`NodeUserHandle.java:29-44`, `DataUserRoot.java:3-12`).
- [ ] **[medium] Stop swallowing all contact-replay errors.** `ContactList.apply`
  catches every `RuntimeException`; corrupt snapshot state and genuine bugs are
  silently ignored, not just malformed operations (`ContactList.java:74-85`).
- [ ] **[medium] Reconsider `snapshotState` content duplication.** The local
  snapshot contains every file's bytes and is rewritten on every persist, so file
  data is stored both in `FileTree` and in the `NodeState` blob
  (`UserFileTree.java:299-326`, `Node.java:2088`).

### Synchronization semantics

- [ ] **[high] Fetch operations from unknown sources.** The responder only serves
  sources present in `fromClock`, and `syncFromRemote` does not seed sources from
  the response's `maxOperationClock` list. Operations authored by a node this node
  has never seen are therefore never requested
  (`Node.java:467-471`, `NodeUserHandle.java:299-315`).
- [ ] **[medium] Fix the leaving protocol.** The implementation never returns to
  idle: phase `I` is terminal and `leavingProcess` is never cleared, while
  `doc/persistence.md` describes a return to idle. Non-user units never call
  `updateLeaving` at all (`ActiveNodeSet.java:138-184`,
  `NodeUserHandle.java:290,329`).
- [ ] **[medium] Remove replicated `lastSeen` or use it.** `lastSeen` is merged and
  persisted/transferred in every active-node state, although the documentation says
  it is local-only, and no selection or truncation code reads it
  (`ActiveNodeSet.java:111`, `Node.java:2335-2336`, `doc/persistence.md:478-480`).
- [ ] **[medium] Make clocks and counters per unit.** `SyncUnitData.entityCounter`
  is persisted and copied but never incremented or read; all entity ids use the
  user counter. The USER unit and home unit share one `ActiveNodeSet` and one
  clock, and `ensureUnitMapper` creates per-unit mappers that are never flushed
  (`Node.java:569,1691-1726,2082`, `doc/persistence.md:163,307`).
- [ ] **[medium] Preserve documented truncation exclusions.** `truncateStableLog`
  removes `RECEIVE_MESSAGE` receipts (which embed message payloads) and
  `SET_SHARED_FOLDER_LABEL`; `doc/users-and-nodes.md` says direct-message events
  stay out of truncation (`NodeUserHandle.java:503-508`).
- [ ] **[low] Validate node ids before truncation.** Stable-log candidate ids are
  parsed with `HexFormat.parseHex` without checking length or hex validity, which
  is reachable through the unauthenticated `joiningNodeId` path
  (`NodeUserHandle.java:493-496`).

### Storage layer

- [ ] **[high] Enforce block size on typed writes.** `writeBlock(..., T)` uses
  `serialize(data).array()` without checking `remaining()` or the area block size,
  while the byte[] overload does check exactly. A short block is stored short and
  read back as a full block; an oversized one overwrites the next block
  (`DataStorage.java:1192-1205`, `DataStorage.java:1069-1075`,
  `DataStorage.java:1051-1055`).
- [ ] **[high] Fix `writeBlockDirect` for blocks without refcount updates.**
  The typed direct path only calls `writeToDisk` when the block has a refcount
  update; structurally owned blocks are silently left unwritten
  (`DataStorage.java:1177-1179`, `DataStorage.java:1202-1204`,
  `DataStorage.java:922-937`).
- [ ] **[high] Replace the root-block size heuristic.** Format/open validation
  only requires `systemBlockSize >= 20 * Long.BYTES`, but the real root block has a
  fixed 96-byte payload plus 10 bytes per ref handle. A root with several handles
  can overflow the SYSTEM block (`DataStorage.java:559,815-817`,
  `RootBlock.java:70-89`).
- [ ] **[high] Detect cycles in deferred-free lists.** `addDeferredFreeBlocksToFreeTree`
  and `addListBlocksToDeferredFreeBlocks` follow `next` in unbounded loops without
  visited sets, contrary to the documented open-time cycle validation
  (`DataStorage.java:1357-1375,1880-1888`, `doc/persistence.md:273-275`).
- [ ] **[medium] Remove or implement deferred refcount updates.** `commit()` always
  writes `refCountUpdateList = 0`; nothing ever persists the pending updates. The
  loader/checker paths are only reachable with hand-edited files, and
  `RefCountUpdates` still documents the opposite (`DataStorage.java:1829,1875`,
  `RefCountUpdates.java:18-24`, `doc/persistence.md:237`).
- [ ] **[medium] Fix BTree endianness documentation.** BTree node headers, item
  counts and offsets are little-endian, while `doc/persistence.md` claims all
  structs and multi-byte fields are big-endian (`BTree.java:1112-1113`,
  `doc/persistence.md:103-104,464`).
- [ ] **[medium] Align refcount-zero behavior with the docs.** Committed blocks
  that reach zero are not freed immediately; only blocks newly allocated in the
  transaction are. The documentation says a zero count frees immediately
  (`DataStorage.java:596-616`, `doc/persistence.md:219-221`).
- [ ] **[medium] Reset and validate long root handles.** `resetInMemoryState`
  does not clear `rootLongHandles`, and `readRootBlock` assumes the id/value lists
  have equal size. A corrupt root can throw `IndexOutOfBoundsException` or ignore
  extra values (`DataStorage.java:514-530,850-855`, `RootBlock.java:88-89`).
- [ ] **[medium] Make `StorageCheck` catch duplicate pending removals.**
  `StorageCheck.run` collects pending META removals in a `TreeSet`, silently
  deduplicating records that the open path explicitly rejects
  (`StorageCheck.java:186-188`, `DataStorage.java:1390-1393`).
- [ ] **[medium] Fix `StorageCheckMain` for node files.** The tool registers only
  handle 1 as `FileTree.BLOCK_TYPE` and cannot read `node.db`, where handle 1 is
  the `BlobStorage` index and handle 2 is `UnitRootStore`
  (`StorageCheckMain.java:24-26`, `Node.java:192-194`,
  `doc/persistence.md:295-296`).
- [ ] **[medium] Add missing `FileTree` invariants.** `removeDirectoryEntry`
  silently ignores a missing `DIR_INDEX`; `nextSpillKey` wraps the unsigned
  32-bit counter to zero; `isDescendant` has an unbounded parent walk; and
  `writeExtentBlocks` has a stale comment plus a hardcoded 64-byte reserve
  (`FileTree.java:465-469,626-637,669-681,756-767`).
- [ ] **[low] Remove the dead extent branch in `FileTree.writeData`.** `writeData`
  is only called for content up to the inline threshold, so its extent branch is
  unreachable (`FileTree.java:198-203,232-234,415-416`).
- [ ] **[low] Stream FUSE `readdir` as documented.** `SimpleFuseFS.readdir`
  materializes a `List<String>` via `listDirectory`, contrary to the documented
  streaming path (`SimpleFuseFS.java:198-204`, `FileTree.java:302-316`,
  `doc/persistence.md:408-411`).
- [ ] **[low] Fix `SimpleFuseFS` block-size reporting.** `getattr` hardcodes
  `st_blksize = 4096` instead of the DATA area block size
  (`SimpleFuseFS.java:185`).
- [ ] **[low] Tighten format validation.** Only `metaBlockSize > 0` is required,
  so a 1-byte META block is accepted even though a BTree node needs a 5-byte header
  (`DataStorage.java:559,815-817`, `BTree.java:39-40`).
- [ ] **[low] Fix direct-write documentation.** `writeNewBlockDirect` says the
  block may not reference other blocks, but the typed path collects and increments
  references (`DataStorage.java:1258-1274`, `DataStorage.java:1192-1201`).
- [ ] **[low] Remove stale refcount abstractions.** The `Mod` enum is unused, and
  `RefCountUpdates.persistable`/`nonPersistable` only drive a loop that drains
  everything (`DataStorage.java:83-99`, `RefCountUpdates.java:18-24,111-117`).

### Transport and libp2p

- [ ] **[high] Implement Yamux receive-side flow control.** The window is
  replenished as soon as DATA arrives, not when the application consumes it, and
  `incoming` is an unbounded queue. The documented backpressure model does not
  apply (`YamuxSession.java:102-108,177-186`, `YamuxStream.java:24`).
- [ ] **[high] Wake blocked Yamux readers and writers on close.** Closing a stream
  removes it from the session without signalling `incomingAvailable`; closing the
  session signals `windowAvailable` but never sets `reset`, so parked writers wait
  forever (`YamuxStream.java:185-224`, `YamuxSession.java:131-137,249-264`).
- [ ] **[high] Track and close accepted TCP connections.** `TcpServer.connections`
  only grows, and `close()` closes only the `ServerSocket`; accepted sockets in
  flight are not yet tracked by `NetworkImpl` and can remain blocked in the Noise
  handshake (`TcpServer.java:34,84-130`).
- [ ] **[high] Close raw sockets on failed upgrades.** `NetworkImpl.connect` leaks
  the raw connection when `upgrade` fails, notifies the listener before checking
  peer identity, and reuses stale connections whose remote side closed
  (`NetworkImpl.java:119-155,181-184`).
- [ ] **[high] Fix Yamux stream lifecycle.** A failed outbound negotiation leaves
  the stream in the map and never sends RST; inbound SYN spawns unbounded threads;
  duplicate or non-monotonic stream ids are not rejected; non-DATA lengths are
  cast to `int` without validation; DATA larger than the receive window is
  accepted (`YamuxSession.java:111-129,160-175,223-282`,
  `YamuxFrameIO.java:52-70`).
- [ ] **[high] Fix IPv6 support.** `SocketUtils.localAddress/remoteAddress` and
  `TcpServer.getActualListeningAddr` always emit `/ip4/`, even for IPv6 sockets,
  so the resulting multiaddr cannot be parsed back as IPv6
  (`SocketUtils.java:43-56`, `TcpServer.java:42-46`, `Protocol.java:176-181`).
- [ ] **[medium] Use `expectedRemotePeerId` or remove it.** The production
  builder passes `null`; `NetworkImpl` appends `/p2p/<id>` but neither the dialing
  transport nor the Noise binding consumes it, and identity is checked only after
  the application listener has been called (`HostBuilder.java:103`,
  `NetworkImpl.java:126-149`, `DefaultConnectionBuilder.java:32-49`).
- [ ] **[medium] Make dynamic host registration effective.**
  `HostImpl.addProtocolHandler` and `addConnectionEstablishedListener` mutate lists
  that the `Multistream`/`YamuxProtocolBinding`/`NetworkImpl` copied at build time,
  so post-build changes have no effect (`HostImpl.java:121-143`,
  `HostBuilder.java:96-127`).
- [ ] **[medium] Flush request/response framing.** `P2POutputStream.flush()` exists,
  but Multistream and Yamux never call it. The contract says request/response
  protocols must flush; a buffered sink would deadlock
  (`P2POutputStream.java:62-78`, `MultistreamFraming.java:41-52`,
  `YamuxFrameIO.java:77-101`).
- [ ] **[medium] Fix Yamux flag handling.** `FLAG_MAP` rejects valid combinations
  such as SYN|FIN; `handleDataOrControl` uses `else if`; ACK handling is
  unreachable for compliant peers because ACK rides on WINDOW_UPDATE;
  `acknowledged` is never read (`YamuxFlag.java:23-46`,
  `YamuxSession.java:91-100,153-158`, `YamuxStream.java:36`).
- [ ] **[medium] Harden Noise framing.** The `length > 0xFFFF` checks can never
  fire, trailing bytes in msg1 are silently accepted, short AEAD frames are not
  rejected up front, there is no rekey or AEAD usage cap, and malformed Ed25519
  signatures surface as `IllegalStateException` instead of
  `InvalidRemotePubKeyException` (`NoiseXXHandshake.java:194-215,240-256`,
  `NoiseXXFramedInputStream.java:78-95`, `Ed25519PublicKey.java:57-65`).
- [ ] **[medium] Fix mDNS lifecycle and wildcard detection.** IPv4 wildcard
  `0.0.0.0` is not detected, so interfaces that should be filtered are advertised;
  `stop()` never shuts down the executor; `serviceRemoved` is ignored; and
  `MemoryAddressBook` ignores TTL (`MDnsDiscovery.java:139-149,404-424,456-459`,
  `MemoryAddressBook.java:32-46`).
- [ ] **[medium] Do not derive mDNS identity from the mutable instance name.**
  JmDNS can rename a conflicting instance (for example `<peerId> (2)`), after which
  the peer is silently ignored; the TXT record is never checked for consistency
  (`MDnsDiscovery.java:462-492`).
- [ ] **[medium] Bound Multistream negotiation.** The responder accepts an
  unbounded number of proposals, the initiator accepts any non-echoed message as
  `na` without checking the literal, and the pre-check uses `String.length()`
  instead of UTF-8 byte length (`Multistream.java:98-165`).
- [ ] **[medium] Make `PeerId` immutable and validate hex.** The constructor keeps
  the caller's array and `getBytes()` returns it; `fromHex` accepts odd lengths and
  non-hex characters (`PeerId.java:24-37,90-98`).
- [ ] **[medium] Replace the fake `SecureRandom` key derivation.** `Ed25519PrivateKey.unmarshal`
  relies on SunEC consuming exactly the seed bytes through
  `FixedSeedSecureRandom.nextBytes(byte[])`, which is provider-internal behavior
  (`Ed25519PrivateKey.java:106-147`).
- [ ] **[low] Tighten TCP/Yamux error handling.** No dial timeout, a bare
  `RuntimeException` and magic backlog 50 in the listening binding, a two-branch
  catch that rethrows in both branches, `System.err` for reader failures, and an
  empty catch around per-stream handlers (`TcpDiallingTransport.java:24-30`,
  `TcpListeningTransportBinding.java:24-30`, `YamuxFrameIO.java:33-40`,
  `YamuxSession.java:73-79,121-128`).

### UI and launcher

- [ ] **[high] Fix ContactPicker default and stale selections.**
  `clearSelection` selects index 0, and `setSelectedUserId` leaves a previous
  selection when the requested user is not in the list. New mail, reply, chat and
  invitation paths can therefore target the wrong contact
  (`ContactPicker.java:109-131`, `MailPanel.java:290-296,440-448`).
- [ ] **[high] Stop the periodic refresh from discarding contact edits.**
  `ContactsPanel.refresh` reloads the selected contact and overwrites all form
  fields; it runs from the 4-second refresh timer, `setUser`, and every search
  keystroke. Only the “new contact” state is protected
  (`ContactsPanel.java:61-76,144-168`, `DesktopApp.java:137,624-626`,
  `UserWorkspacePanel.java:222-230`).
- [ ] **[high] Keep blocking work off the Swing EDT.** User creation, joins,
  persistence, attachment reads/writes and uploads/downloads all run in action
  listeners; only “Sync now” uses the background worker
  (`DesktopApp.java:455-515`, `Node.java:1921-1932`, `MailPanel.java:402-420`,
  `UnitsPanel.java:374-435`, `Theme.java:362-374`).
- [ ] **[high] Fix the default credential export path.** The default is
  `<mount>/<user>/<user>.userkey`, but the user root exposes only `home` and shared
  folder labels, so exporting there fails; browsing into `home` writes the encrypted
  key into the replicated tree (`AccountPanel.java:55,161`,
  `NodeFuseFS.java:74-100,231-232`).
- [ ] **[medium] Clear chat drafts and attachments on conversation switch.**
  Switching conversations or channels leaves the composer and attachment list
  intact, so a draft can be sent to the next selected peer
  (`ChatPanel.java:102-110,201-212,393-458`).
- [ ] **[medium] Stop discarding mail drafts on navigation.** Selecting a folder
  or message cancels compose without confirmation. Invitation acceptance is
  duplicated and requires a peer id, unlike the documented blank-peer probing
  (`MailPanel.java:111-147,282-332`, `UnitsPanel.java:274-297`).
- [ ] **[medium] Apply the USER-unit filter consistently.** After invitation,
  role change or member removal, `refreshWithoutDetailReset` skips the
  `Type.USER` filter and can show the user's own SyncUnit in the management table
  (`UnitsPanel.java:153-178,644-647`).
- [ ] **[medium] Make “Link key” link the selected contact.** It always calls
  `createContactFromUser`, creating a duplicate instead of filling in an unlinked
  contact (`ContactPicker.java:133-149`, `doc/contacts.md:168-177`).
- [ ] **[medium] Fix UI terminology.** “Link key”, “User key (hex, optional)”,
  “user ID”, and a member column labeled “User key” that displays a display name
  are inconsistent (`ContactPicker.java:64`, `ContactsPanel.java:223`,
  `AccountPanel.java:130-132`, `UnitsPanel.java:777-804`).
- [ ] **[medium] Resolve users by public key, not local name.**
  Chat, mail and invitation send paths call `node.getUserByName(user.localName())`
  even though `Node.getUser(PubKey)` exists; a rename can target the wrong handle
  (`ChatPanel.java:445`, `MailPanel.java:447`, `UnitsPanel.java:574`,
  `Node.java:1779-1781`).
- [ ] **[medium] Document or remove background sync.** `DesktopApp` syncs every
  4 seconds and also runs a separate 4-second refresh timer; neither is mentioned
  in the README (`DesktopApp.java:107-114,137`, `README.md:53-54`).
- [ ] **[low] Do not reset a custom credential path on rename.**
  `AccountPanel.setUser` rewrites `exportPath` whenever the local name changes
  (`AccountPanel.java:49-56`).
- [ ] **[low] Fix launcher exit codes and messages.** A mount-directory creation
  failure is reported as “Could not open node storage”, fatal startup errors return
  exit code 0, and `umount()` failures are swallowed (`DesktopMain.java:36-50,58-61`).
- [ ] **[low] Fix tray separator handling.** The refresh removes menu item 1
  while the count is greater than 2, which deletes the separator permanently
  (`DesktopApp.java:713-715`).
- [ ] **[low] Avoid whole-file reads with no size limit.** Attachments, uploads and
  downloads use `Files.readAllBytes`/`readAllBytes` on the EDT
  (`MailPanel.java:402-420`, `ChatPanel.java:393-411`, `UnitsPanel.java:374-435`).
- [ ] **[low] Correct the pending-count description.** The UI shows one combined
  pending count per user; `doc/users-and-nodes.md` says separate local and incoming
  counts appear in the table and tray (`DesktopApp.java:717-718,773-775`,
  `doc/users-and-nodes.md:135-136`).
- [ ] **[low] Correct the “inline forms rather than modal dialogs” claim.**
  Credential, attachment and shared-file workflows still use modal
  `JFileChooser` dialogs (`Theme.java:316-341`, `README.md:26-27`,
  `doc/users-and-nodes.md:128-129`).

### Documentation and README

- [ ] **[high] Fix the README launch command.** The README passes a node-data
  directory, but `DesktopMain` expects `<node-db-file> [mount-directory]` and
  `doc/persistence.md` correctly calls `node.db` a single file path
  (`README.md:31-40`, `DesktopMain.java:18-27`).
- [ ] **[high] Fix the “run everything” test command.** `mvn test test -Pit` does
  not run the default unit tests: the `it` profile restricts Surefire to the
  `integration` group and `*Test.java`/`*IT.java` includes. Use `mvn test` followed
  by `mvn test -Pit` (`README.md:106-110`, `pom.xml:146-165`).
- [ ] **[high] Correct the `SimpleFuseFS` durability claim.** The README and
  `doc/persistence.md` say every mutation is committed, but mutations only set
  `dirty`; commits happen on `flush`/`fsync`, every 5 seconds, or not at all on
  `close` (`README.md:80-81`, `doc/persistence.md:466-468,536`,
  `SimpleFuseFS.java:92-107,368-392`).
- [ ] **[medium] Fix stale class references.** `doc/users-and-nodes.md` links
  `StorageUserRoot` and `BlockStorage`, neither of which exists; the current class
  is `BlobStorage`. It also describes `DataUserRoot` as status plus root id, while
  it now exposes contacts (`doc/users-and-nodes.md:94-96,119-123`,
  `BlobStorage.java`, `DataUserRoot.java:8-12`). `doc/contacts.md` refers to a
  `UserSnapshot` class that is only the protobuf message (`doc/contacts.md:95,110`).
- [ ] **[medium] Fix the root-handle documentation.** The table lists handle 1 for
  both `BlobStorage` and `SimpleFuseFS` and then says ids are globally reserved;
  that is only true per file (`doc/persistence.md:152-159`). The documented
  `refHandles` layout also omits the second count written by `RootRefsSlot`
  (`doc/persistence.md:142`, `RootBlock.java:34-42`, `SlotBlock.java:291-296`).
- [ ] **[medium] Fix storage commit/open documentation.** `writeBlockDirect` and
  `writeNewBlockDirect` write immediately, contrary to “all writes are buffered”
  (`doc/persistence.md:260-261`, `DataStorage.java:1168-1179`). `open` truncates
  before replaying the lists, not after (`doc/persistence.md:266-270`,
  `DataStorage.java:659-664`).
- [ ] **[medium] Fix the transport architecture document.** It claims there is no
  `flush()` (it exists), that `readFully` loops `read()` (it loops bulk reads),
  that reads are unsynchronized (several are synchronized/locked), and that
  shutdown wakes all blocked threads (it does not). The thread diagram says the
  connection worker becomes the Yamux reader, but a separate thread is started
  (`doc/transport-streams-threading.md:63-67,88-90,108-127`,
  `P2PInputStream.java:82-92`, `P2POutputStream.java:62-78`,
  `YamuxSession.java:47`).
- [ ] **[medium] Remove references to files/classes that do not exist.**
  `ImplementationPlan.md` and `ARCHITECTURE.md` are referenced throughout the
  libp2p code and `doc/transport-streams-threading.md`; `ProtocolSelect`,
  `TcpTransport`, `NoiseXXSecureChannel` and `YamuxStreamMuxer` are also named but
  do not exist (`doc/transport-streams-threading.md:30-33,99,153,156`,
  `P2PInputStream.java:12,27`, `Multistream.java:20-22`).
- [ ] **[low] Correct `doc/users-and-nodes.md` join description.** `Node.join()`
  only searches the in-process `Node.Network`; the desktop path is `joinRemote()`
  (`doc/users-and-nodes.md:91-93`, `Node.java:760-771,807-890`).

### Dead code, duplication and test gaps

- [ ] **[low] Remove or wire up dead code.** `App` (Hello World), `StorageShard`,
  `JoinId`, `Node.guidCounter`, `RemoteSyncResponse.snapshotState`,
  `UserMessage.sharedFolderLabel` (the label-restore branch is unreachable),
  `SocketP2PInputStream/OutputStream`, `Stream`/`StreamHandler`,
  `ContactPicker.selectedContact/refresh`, and `UnitsPanel.mountPoint` are unused
  (`App.java:7-12`, `StorageShard.java`, `JoinId.java`, `Node.java:46,2081`,
  `sync.proto:326`, `Node.java:1231-1237`, `SocketUtils.java:16-19`,
  `ContactPicker.java:95-102`, `UnitsPanel.java:42,57`).
- [ ] **[low] Deduplicate shared utilities.** `CryptoUtils` duplicates the Noise
  handshake's SHA-256/HMAC helpers, stream-close logic is duplicated in
  `YamuxStream`/`StreamOutput`, mDNS wildcard logic is split between
  `isWildcard` and `covers`, and `Connection.getRemotePeerId` duplicates
  `SecureSession.getRemoteId` (`CryptoUtils.java:15-31`,
  `NoiseXXHandshake.java:696-712`, `YamuxStream.java:185-224`,
  `MDnsDiscovery.java:250-258,404-424`, `Connection.java:13-15`).
- [ ] **[medium] Replace the mock active-node test.** `NodeAlgorithmTest` defines
  its own active-set implementation with a two-phase leave process and
  `leavingProcess = null`, so it cannot catch the production three-phase protocol
  bugs (`NodeAlgorithmTest.java:366-422`, `ActiveNodeSet.java:138-184`).
- [ ] **[low] Add missing regression tests.** Add an IPv4 wildcard mDNS test,
  typed-block-size/direct-write storage tests, a deferred-free-list cycle test, a
  node-file `StorageCheckMain` test, and a ContactPicker misselection test
  (`MDnsDiscoveryTest.java:33-43`, `DataStorageAllocatorTest.java`,
  `SimpleFuseFSTest.java`, `ContactPicker.java:109-131`).
- [ ] **[low] Replace busy polling and duplicated test helpers.** `YamuxTest`
  waits for remote streams with `Thread.sleep(10)` loops, and `readExact` is
  duplicated across Yamux, Noise and end-to-end tests
  (`YamuxTest.java:226-265`, `NoiseXXTest.java:422-433`).
