# Manual Test Cases

This directory contains manual test cases for p2p-sync. Each test case is a
separate Markdown file and uses the same fields: **Test ID**, **Feature**,
**Test Case Description**, **Prerequisites**, **Steps**, and **Expected Result**. 
Notes are added only where the implementation detail matters for
interpreting a result.

## Common Prerequisites

- The project builds with `mvn compile` and runs with Java 21.
- Two application instances are available, referred to below as **Node A** and
  **Node B**. Each instance must own its own `node.db` file; never open the same
  database file from two processes.
- For scripted checks, start each node without FUSE and with SwingDrive on a
  distinct port. Example for Node A:

  ```bash
  mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
    -Dexec.args="--no-mount --drive 9222 /absolute/path/node-a.db"
  ```

  Start Node B with a different database path and a different `--drive` port,
  for example `9223`.
- For interactive checks, use the desktop UI and the FUSE mount. On Linux,
  install the FUSE 2 compatibility library (`libfuse2` or `libfuse2t64`),
  mount tools, and make `/dev/fuse` available to the test user.
- Nodes must be able to reach each other over the configured TCP multiaddress.
  mDNS discovery works only on the local network; when discovery is unavailable,
  use an explicit peer ID and, if needed, the peer's listen multiaddress.
- The examples use `http://127.0.0.1:9222` for Node A and
  `http://127.0.0.1:9223` for Node B. Adjust ports to the actual `--drive`
  ports.
- The combined file view is exposed as `/<local-user-name>/<root>/<path>`, where
  roots are `home` and accepted shared folders by their per-user label. The same
  view is served by `/fs/...` and by an active FUSE mount.
- Exported user credentials are password-protected with AES-GCM. The passphrase
  may be empty.
- Node storage is not encrypted at rest. Use disposable test users, test data,
  and properly protected database files.

## General Instructions for Scripted Execution
- Take one screenshot of each screen encountered
- Avoid repeatedly taking screenshots of the same screen, when no significant changes are present
- If a screen shows new data (such a an empty table now containing rows), take a separate screenshot of the changed state
- Write findings into `TODO.md`
- After updating `TODO.md`, inspect the screenshots and report findings in `TODO.md` as well

## Scripted Two-Node Setup With SwingDrive

Most synchronization tests need two nodes that already share a user. The
following setup is the shortest reliable path; run each node in its own terminal
and keep the two databases separate.

1. Compile once and use a disposable directory for the databases:

   ```bash
   mvn -q compile
   mkdir -p /tmp/p2p-sync-test
   rm -f /tmp/p2p-sync-test/node-a.db /tmp/p2p-sync-test/node-b.db
   ```

2. Start Node A on SwingDrive port 9222 and Node B on port 9223:

   ```bash
   mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
     -Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
     -Dexec.args="--no-mount --drive 9222 /tmp/p2p-sync-test/node-a.db"
   ```

   ```bash
   mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
     -Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
     -Dexec.args="--no-mount --drive 9223 /tmp/p2p-sync-test/node-b.db"
   ```

3. Read Node A's peer ID and listen port from `/peer`:

   ```bash
   curl -s http://127.0.0.1:9222/peer
   curl -s http://127.0.0.1:9223/peer
   ```

   The desktop listens on `/ip4/0.0.0.0/tcp/0`, so the reported address is a
   wildcard address. When both nodes run on the same machine, use
   `/ip4/127.0.0.1/tcp/<port>` with the reported port for the explicit join in
   step 6.

4. Create the test user on Node A:

   ```bash
   curl -s -X POST http://127.0.0.1:9222/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Add user"}}'
   curl -s -X POST http://127.0.0.1:9222/ui/type \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"textbox"},"value":"alice","clear":true}'
   curl -s -X POST http://127.0.0.1:9222/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Create user"}}'
   ```

   Confirm that `/state` reports the user and that
   `GET /fs/list?path=/alice/home` succeeds.

5. Export Node A's credential. Select the **Account** workspace section, then
   either accept the prefilled `$HOME/alice.userkey` path or set the **Save to**
   field to a test path. Leave the passphrase empty and click **Export file**:

   ```bash
   curl -s -X POST http://127.0.0.1:9222/ui/select \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"name":"workspace-sections"},"value":"Account"}'
   curl -s -X POST http://127.0.0.1:9222/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Export file"}}'
   ```

   The status bar must report `Protected credential exported`.

6. Join the same user on Node B. Open **Join user**, then set the credential
   file path, Node A's peer ID, Node A's loopback listen multiaddress, and the
   local name:

   ```bash
   curl -s -X POST http://127.0.0.1:9223/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Join user"}}'
   ```

   Several join fields have no component name. Inspect
   `/ui/query?role=textbox` and use the `id` of each `"showing":true` entry with
   `/ui/type` (the account export path can be handled the same way when a
   non-default path is needed):

   ```bash
   curl -s 'http://127.0.0.1:9223/ui/query?role=textbox'
   ```

   Then submit and wait for the status bar to report `User joined`:

   ```bash
   curl -s -X POST http://127.0.0.1:9223/ui/click \
     -H 'Content-Type: application/json' \
     -d '{"selector":{"role":"button","text":"Import and join"}}'
   curl -s http://127.0.0.1:9223/state
   ```

   A successful join shows the user with two active nodes once Node B has
   synchronized with Node A.

7. Before asserting a synchronization result, wait for background work to
   settle. The dashboard synchronizes every four seconds; `Sync now` triggers an
   immediate round, and `/state` reports `syncEnabled:false` only while a round
   is in progress. Poll `/state` until it returns to `true` and the `pending`
   text reports `0 pending`.

Use fresh database files for each new scenario. Never run two nodes against the
same database file, and stop both processes before removing the test directory.

## Index

| Test ID | Area | File |
| --- | --- | --- |
| SYNC-01 | Synchronization | [sync-01-basic-user-file-synchronization.md](sync-01-basic-user-file-synchronization.md) |
| SYNC-02 | Synchronization | [sync-02-file-modification-and-deletion-propagation.md](sync-02-file-modification-and-deletion-propagation.md) |
| PERSIST-01 | Persistence | [persist-01-node-state-recovery-after-restart.md](persist-01-node-state-recovery-after-restart.md) |
| PERSIST-02 | Persistence | [persist-02-storage-consistency-check.md](persist-02-storage-consistency-check.md) |
| CONFLICT-01 | Consistency | [conflict-01-concurrent-file-modification.md](conflict-01-concurrent-file-modification.md) |
| CONFLICT-02 | Consistency | [conflict-02-concurrent-contact-field-updates.md](conflict-02-concurrent-contact-field-updates.md) |
| STORAGE-01 | Storage | [storage-01-large-file-handling-inline-vs-extent.md](storage-01-large-file-handling-inline-vs-extent.md) |
| STORAGE-02 | Storage | [storage-02-concurrent-fuse-and-node-lock-access.md](storage-02-concurrent-fuse-and-node-lock-access.md) |
| SHARE-01 | Collaboration | [share-01-shared-folder-role-enforcement.md](share-01-shared-folder-role-enforcement.md) |
| SHARE-02 | Collaboration | [share-02-shared-folder-label-collision.md](share-02-shared-folder-label-collision.md) |
| FAILURE-01 | Resilience | [failure-01-sync-recovery-after-network-interruption.md](failure-01-sync-recovery-after-network-interruption.md) |
| FAILURE-02 | Resilience | [failure-02-abrupt-process-termination.md](failure-02-abrupt-process-termination.md) |
