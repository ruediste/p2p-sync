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
