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

## Desktop node and user filesystem

`com.github.ruediste.p2psync.node.NodeDesktopMain` starts the node dashboard,
system-tray status menu, and a FUSE mount exposing each configured user below
its node-local name. On Linux, install the FUSE 2 compatibility library
(`libfuse2` or `libfuse2t64`), mount tools, and make `/dev/fuse` available to
your user.

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	-Dexec.mainClass=com.github.ruediste.p2psync.node.NodeDesktopMain \
	-Dexec.args="/absolute/path/node-data /absolute/path/user-mount"
```

The mount directory is optional; when omitted, a sibling directory is created.
The node stores its state, operation histories, and content-addressed blocks in
`node.db` inside the data directory. Node storage is not password-locked; protect
`node.db` with filesystem permissions. Run one process per node-data directory.
A dashboard remains available when the desktop environment has no system tray.
The tray uses Java AWT and requires legacy X11 tray support. On XFCE, add the
`Status Tray Plugin` (or `Notification Area` on older versions) to the panel
and ensure legacy tray icons are enabled. A StatusNotifier/AppIndicator-only
tray does not suffice. Startup prints a warning when AWT tray support is unavailable
or the icon cannot be registered.
The launcher prints the node's peer ID and listen address. Export a protected
user credential from the dashboard to join that user from another node; leave
the peer ID blank to probe known mDNS/manual peers, or specify a peer ID and
optional listen multiaddress. The credential file is required, but its
passphrase may be empty. Imports try an empty passphrase first and prompt only
when it does not unlock the credential.
Use `Sync now` to reconcile with known active peers; the tray menu shows per-user
pending operation counts.

The node uses the existing TCP, Noise, Yamux, and mDNS stack for cross-process
user synchronization. `Node.Network` remains as an in-process test transport.

## Standalone storage FUSE prototype

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
