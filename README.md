# p2p-sync app

Java implementation of the p2p-sync peer-to-peer file storage and synchronization protocol. It ports parts of jvm-libp2p from Kotlin to Java and extends it with functionality from nabu (reference sources in `../upstream/`). Project documentation lives in `doc`.

## Requirements

- Java 21
- Maven

## Building

```bash
mvn compile
```

If protobuf code generation misbehaves, a `mvn clean` usually helps.

## Simple FUSE filesystem

`com.github.ruediste.p2psync.storage.SimpleFuseMain` mounts a persistent `FileTree`
using jnr-fuse. On Linux, install the FUSE 2 compatibility library (`libfuse2` or
`libfuse2t64`, depending on your distribution) and the FUSE mount tools. The
`/dev/fuse` device must be available to your user.

Create an empty mount directory, then run with Java 21:

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	-Dexec.mainClass=com.github.ruediste.p2psync.storage.SimpleFuseMain \
	-Dexec.args="/absolute/path/files.db /absolute/path/mount"
```

The first argument is the `DataStorage` backing file, created if absent or empty;
the second is an existing mount directory. Keep the backing file outside the
mount directory, and use only one process per backing file. Mounting runs in the
foreground. Unmount from another terminal with `fusermount -u /absolute/path/mount`.
Remount with the same backing file to recover committed files and directories.

The filesystem supports regular files, directories, offset reads/writes,
truncation, rename, and removal. Each mutation is committed. Permissions are fixed
at 0644 for files and 0755 for directories, owned by the mounting user. This is a
simple path-based filesystem: links, extended attributes, permission changes, and
continued access through open descriptors after unlink or rename are not supported.

## Running tests

### Unit tests

```bash
mvn test
```

Runs the fast unit tests (excludes integration tests). Takes roughly 10 seconds.

### Integration tests

Integration tests are classes named `*IT.java` (currently the slow mDNS loopback tests: `MDnsDiscoveryIT`, `MDnsWireUpIT`). They need working multicast networking and rely on JmDNS's multi-second probe/announce state machine, so they are not part of the default test run.

Run them with the `it` profile:

```bash
mvn test -Pit
```

To run everything (unit + integration tests):

```bash
mvn test test -Pit
```

Integration tests skip gracefully (via JUnit assumptions) when multicast is not available in the environment.
