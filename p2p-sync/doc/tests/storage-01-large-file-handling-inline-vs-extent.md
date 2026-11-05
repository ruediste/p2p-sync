# STORAGE-01 — Large File Handling (Inline vs. Extent)

| Field | Value |
| --- | --- |
| **Test ID** | STORAGE-01 |
| **Feature** | Storage engine / `FileTree` |
| **Priority** | Medium |

## Test Case Description

Verify that a file larger than the inline threshold (16 KiB) is stored using
extent blocks and is read back without corruption, including after a restart.

## Prerequisites

- Common prerequisites from [README.md](README.md) are met.
- Node A is running.
- A local temporary directory exists, for example `/tmp/kilo`.

## Steps

1. Generate a 1 MiB random test file and record its checksum:

   ```bash
   dd if=/dev/urandom of=/tmp/kilo/large.bin bs=1M count=1 status=none
   sha256sum /tmp/kilo/large.bin
   ```

2. Upload the file through the file view:

   ```bash
   curl -s -X PUT \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/large.bin' \
     --data-binary @/tmp/kilo/large.bin
   ```

3. Read the file back and compare checksums:

   ```bash
   curl -s \
     'http://127.0.0.1:9222/fs/content?path=/alice/home/large.bin' \
     -o /tmp/kilo/large-read.bin
   sha256sum /tmp/kilo/large-read.bin
   ```

4. Confirm the reported size:

   ```bash
   curl -s 'http://127.0.0.1:9222/fs/stat?path=/alice/home/large.bin'
   ```

5. Stop Node A, restart it with the same `node.db`, and read the file again.

## Expected Result

- The SHA-256 checksum of the read-back file matches the original.
- `GET /fs/stat` reports a regular file of 1048576 bytes.
- No `ENOSPC`, `EIO`, or truncation error occurs.
- The file is still correct after the restart, proving the extent data and
  `FileTree` root were persisted.
- The request body remains below the 64 MiB `/fs/content` limit; larger files
  must use the FUSE mount or a different test.
