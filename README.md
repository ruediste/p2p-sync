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

`com.github.ruediste.p2psync.ui.DesktopMain` starts the single-window desktop
application, optional system-tray status menu, and a FUSE mount exposing each
configured user below its node-local name. Each user directory is a combined root
view: `home` plus the user's accepted shared folders by label. Users and workspace
sections are selected in the left navigation. Chat, mail (an email-client style
inbox/sent view), contacts, SyncUnits, and account settings stay in the main
window; workflows use inline forms, and file selection and discard confirmations
use standard dialogs. On Linux, install
the FUSE 2 compatibility library
(`libfuse2` or `libfuse2t64`), mount tools, and make `/dev/fuse` available to
your user.

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	-Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
	-Dexec.args="/absolute/path/node.db /absolute/path/user-mount"
```

The first argument is the node database file, created if absent. The mount
directory is optional; when omitted, a sibling directory named
`<node-db-file>-mount` is created. The node stores its state, per-SyncUnit
operation B-trees, GUID mappings, and `FileTree` file data in that single file.
Node storage is not password-locked; protect the database file with filesystem
permissions. Run one process per node database file.

Use `--no-mount` to run without mounting the FUSE filesystem, and `--drive PORT`
(or `--drive=PORT`) to start the local [SwingDrive](doc/swingdrive.md)
development server, which inspects, drives, and screenshots the Swing UI over
HTTP and exposes the same combined file view through `/fs/...` endpoints.
`--drive` alone uses port 9222, and port 0 selects a random free port. For
example:

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
	-Dexec.mainClass=com.github.ruediste.p2psync.ui.DesktopMain \
	-Dexec.args="--no-mount --drive 9222 /absolute/path/node.db"
```

The main window remains available when the desktop environment has no system tray.

The tray uses Java AWT and requires legacy X11 tray support. On XFCE, add the
`Status Tray Plugin` (or `Notification Area` on older versions) to the panel
and ensure legacy tray icons are enabled. A StatusNotifier/AppIndicator-only
tray does not suffice. Startup prints a warning when AWT tray support is unavailable
or the icon cannot be registered.
The launcher prints the node's peer ID and listen address. Export a protected
user credential from the Account section to join that user from another node; leave
the peer ID blank to probe known mDNS/manual peers, or specify a peer ID and
optional listen multiaddress. A credential file or pasted credential is required,
but its passphrase may be empty. Import errors and other action results are shown in the
window's status bar.
The dashboard also synchronizes with known active peers in the background every
four seconds and refreshes the user list and active workspace on the same
interval. Use `Sync now` to trigger an immediate round; the tray menu shows a
combined per-user pending operation count (local plus incoming).

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
truncation, rename, and removal. Mutations mark the tree dirty rather than
committing immediately: the filesystem commits on `flush`/`fsync`, every five
seconds, and when the mount is shut down after the kernel flushes it. A dirty
tree is not committed by `close()` itself, so a crash or abrupt exit can lose up
to five seconds of changes. Permissions are fixed
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

Integration tests are classes named `*IT.java` or tests tagged `integration` (currently the slow mDNS loopback tests and the remote libp2p synchronization test). They need working networking; the mDNS tests additionally need multicast and rely on JmDNS's multi-second probe/announce state machine, so they are not part of the default test run.

Run them with the `it` profile:

```bash
mvn test -Pit
```

To run everything (unit + integration tests), run the two default test goals in
sequence:

```bash
mvn test
mvn test -Pit
```

Integration tests skip gracefully (via JUnit assumptions) when multicast is not available in the environment.
