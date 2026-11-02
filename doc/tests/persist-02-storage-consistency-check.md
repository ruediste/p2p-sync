# PERSIST-02 — Storage Consistency Check

| Field | Value |
| --- | --- |
| **Test ID** | PERSIST-02 |
| **Feature** | Data persistence / storage integrity |
| **Priority** | High |

## Test Case Description

Verify that the node database remains structurally consistent after a mix of
file and metadata operations by running the read-only `StorageCheck` scan.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A has been used for a representative sequence of operations, for example
  creating directories, writing small and large files, moving files, deleting
  files, and changing a user status or contact.
- Node A is stopped. Do not run the check against a mounted or open database.

## Steps

1. Stop Node A and confirm that the process has exited.

2. Run the storage checker against the node database:

   ```bash
   mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
     -Dexec.mainClass=com.github.ruediste.p2psync.storage.StorageCheckMain \
     -Dexec.args="/absolute/path/node-a.db"
   ```

3. Inspect the printed report. The process exit code is `0` for a passing
   check, `1` for a failed check, and `2` for invalid arguments.

4. Optionally repeat after copying the database file, to confirm that the check
   does not modify the file.

## Expected Result

- The command exits with status `0`.
- The report contains no structural errors, no reachability errors, no
  reference-count mismatches, and no unaccounted blocks.
- Block statistics are printed for the checked file.
- Running the check again on the same file produces the same result.
