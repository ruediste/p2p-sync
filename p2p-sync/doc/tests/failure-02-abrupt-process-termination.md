# FAILURE-02 — Abrupt Process Termination

| Field | Value |
| --- | --- |
| **Test ID** | FAILURE-02 |
| **Feature** | Persistence / crash resilience |
| **Priority** | High |

## Test Case Description

Verify that a node recovers without structural corruption when its process is
killed abruptly, and that previously committed operations are preserved.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A is running with a user `alice`.
- The node has performed at least one committed local file operation.
- The tester can kill the Java process abruptly (for example `kill -9`).

## Steps

1. On Node A, write a file through the combined file view:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/crash.txt' \
     --data-binary 'Committed before crash'
   ```

2. Confirm that the write succeeded and the content is readable.

3. Kill the Node A process abruptly with `kill -9` (or the platform
   equivalent), without closing the window and without a graceful shutdown.

4. Restart Node A with the same `node.db` path.

5. Verify that the node opens, the user list is present, and the file is
   readable.

6. Stop Node A cleanly and run the storage check:

   ```bash
   mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
     -Dexec.mainClass=com.github.ruediste.p2psync.storage.StorageCheckMain \
     -Dexec.args="/absolute/path/node-a.db"
   ```

## Expected Result

- Node A opens without a storage-format, root-block, or reference-count error.
- User `alice` is still configured and the node peer ID is unchanged.
- `/alice/home/crash.txt` is present with `Committed before crash`, because
  local user operations are committed as part of the operation.
- If the kill interrupted an operation before its commit point, that operation
  may be absent, but no partially written or corrupted tree is exposed.
- The storage check exits with status `0` and reports no structural errors.
- This test applies to the node database. The standalone `SimpleFuseFS`
  prototype has a separate dirty-tree commit window and is not covered here.
