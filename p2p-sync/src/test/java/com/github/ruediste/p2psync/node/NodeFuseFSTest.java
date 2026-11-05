package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.struct.FileStat;

class NodeFuseFSTest {
    private static Pointer buffer(byte[] bytes) {
        Pointer result = Memory.allocateDirect(Runtime.getSystemRuntime(), Math.max(1, bytes.length));
        result.put(0, bytes, 0, bytes.length);
        return result;
    }

    private static List<String> list(NodeFuseFS fs, String path) {
        List<String> names = new ArrayList<>();
        FuseFillDir filler = (buffer, name, stat, offset) -> {
            byte[] encoded = new byte[name.remaining()];
            name.get(encoded);
            names.add(new String(encoded, StandardCharsets.UTF_8));
            return 0;
        };
        assertEquals(0, fs.readdir(path, buffer(new byte[0]), filler, 0, null));
        return names;
    }

    /**
     * Verifies named user roots expose the combined home/shared-folder root view
     * and route home I/O to isolated trees.
     */
    @Test
    void exposesIsolatedUserTreesByLocalName() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            node.createNewUser("alice");
            node.createNewUser("work");
            var fs = new NodeFuseFS(node);

            List<String> roots = list(fs, "/alice");
            assertTrue(roots.containsAll(List.of(".", "..", "home")));

            assertEquals(0, fs.mkdir("/alice/home/docs", 0755));
            assertEquals(0, fs.create("/alice/home/docs/note.txt", 0644, null));
            byte[] content = "private note".getBytes(StandardCharsets.UTF_8);
            assertEquals(content.length,
                    fs.write("/alice/home/docs/note.txt", buffer(content), content.length, 0, null));
            assertEquals(-ErrorCodes.ENOENT(), fs.open("/work/home/docs/note.txt", null));

            Pointer readBuffer = buffer(new byte[content.length]);
            assertEquals(content.length,
                    fs.read("/alice/home/docs/note.txt", readBuffer, content.length, 0, null));
            byte[] actual = new byte[content.length];
            readBuffer.get(0, actual, 0, actual.length);
            assertArrayEquals(content, actual);

            assertTrue(list(fs, "/").containsAll(List.of("alice", "work")));
            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/alice/home/docs/note.txt", stat));
            assertEquals(content.length, stat.st_size.longValue());

            assertEquals(0, fs.rename("/alice/home/docs/note.txt", "/alice/home/docs/note.tmp"));
            assertEquals(0, fs.rename("/alice/home/docs/note.tmp", "/alice/home/docs/note.txt"));
            assertEquals(-ErrorCodes.EXDEV(), fs.rename("/alice/home/docs/note.txt", "/work/home/note.txt"));
        } finally {
            node.close();
        }
    }

    /**
     * Verifies labeled shared folders appear next to home and can be read and
     * written through the same root view.
     */
    @Test
    void exposesSharedFolderContentInCombinedRoot() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.setSharedFolderLabel(handle.data.userId(), folder, "team");
            var fs = new NodeFuseFS(node);

            List<String> roots = list(fs, "/alice");
            assertTrue(roots.containsAll(List.of("home", "team")));

            assertEquals(0, fs.create("/alice/team/notes/todo.txt", 0644, null));
            byte[] content = "shared todo".getBytes(StandardCharsets.UTF_8);
            assertEquals(content.length,
                    fs.write("/alice/team/notes/todo.txt", buffer(content), content.length, 0, null));
            assertArrayEquals(content,
                    node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));

            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/alice/team/notes/todo.txt", stat));
            assertEquals(content.length, stat.st_size.longValue());
            assertEquals(0, fs.getattr("/alice/team/notes", stat));

            List<String> team = list(fs, "/alice/team");
            assertTrue(team.contains("notes"));
            assertTrue(list(fs, "/alice/team/notes").contains("todo.txt"));
            Pointer readBuffer = buffer(new byte[content.length]);
            assertEquals(content.length,
                    fs.read("/alice/team/notes/todo.txt", readBuffer, content.length, 0, null));
            byte[] actual = new byte[content.length];
            readBuffer.get(0, actual, 0, actual.length);
            assertArrayEquals(content, actual);

            assertEquals(0, fs.unlink("/alice/team/notes/todo.txt"));
            assertNull(node.readSharedFile(folder, handle.data.userId(), "notes/todo.txt"));
        } finally {
            node.close();
        }
    }

    /**
     * Verifies a not-yet-synchronized home file is reported as ENOENT. The path
     * resolves through an existing but empty home directory, which previously
     * surfaced as EINVAL from the lower user-tree lookup.
     */
    @Test
    void missingHomeFileReturnsEnoent() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            node.createNewUser("alice");
            var fs = new NodeFuseFS(node);

            var direct = assertThrows(NodeFuseFS.FsException.class,
                    () -> fs.readFile("/alice/home/hello.txt"));
            assertEquals(ErrorCodes.ENOENT(), direct.errno());

            assertEquals(-ErrorCodes.ENOENT(),
                    fs.read("/alice/home/hello.txt", buffer(new byte[5]), 5, 0, null));
            assertEquals(-ErrorCodes.ENOENT(), fs.readdir("/alice/home/missing",
                    buffer(new byte[0]), (buffer, name, stat, offset) -> 0, 0, null));
        } finally {
            node.close();
        }
    }

    /**
     * Verifies a viewer cannot modify a shared folder through the mount. The
     * authorization failure has to surface as EACCES so callers see a permission
     * error instead of EINVAL (invalid argument).
     */
    @Test
    void viewerWritesAreDeniedWithAccessError() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            var bob = node.createNewUser("bob");
            var alice = node.createNewUser("alice");
            var folder = node.createSyncUnit(bob.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.putSharedFile(folder, bob.data.userId(), "notes.txt",
                    "hello".getBytes(StandardCharsets.UTF_8));
            node.inviteToSyncUnit(folder, bob.data.userId(), alice.data.userId(),
                    SyncUnitAuthorization.Role.VIEWER);
            node.joinSyncUnit(folder, alice.data.userId());
            node.setSharedFolderLabel(alice.data.userId(), folder, "team");
            var fs = new NodeFuseFS(node);

            byte[] content = "changed".getBytes(StandardCharsets.UTF_8);
            assertEquals(-ErrorCodes.EACCES(),
                    fs.write("/alice/team/notes.txt", buffer(content), content.length, 0, null));
            assertEquals(-ErrorCodes.EACCES(), fs.create("/alice/team/new.txt", 0644, null));
            assertEquals(-ErrorCodes.EACCES(), fs.mkdir("/alice/team/newdir", 0755));
            assertEquals(-ErrorCodes.EACCES(), fs.unlink("/alice/team/notes.txt"));
            assertEquals("hello", new String(fs.readFile("/alice/team/notes.txt"), StandardCharsets.UTF_8));

            var direct = assertThrows(NodeFuseFS.FsException.class,
                    () -> fs.writeFile("/alice/team/notes.txt", content));
            assertEquals(ErrorCodes.EACCES(), direct.errno());
        } finally {
            node.close();
        }
    }

    /**
     * Verifies the direct helpers used by the development server expose the same
     * combined home/shared-folder tree as the FUSE callbacks.
     */
    @Test
    void directHelpersReadAndWriteTheMountedView() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.setSharedFolderLabel(handle.data.userId(), folder, "team");
            var fs = new NodeFuseFS(node);

            assertTrue(fs.list("/").stream().anyMatch(entry -> entry.name().equals("alice")));
            assertTrue(fs.list("/alice").stream().anyMatch(entry -> entry.name().equals("home")));
            assertTrue(fs.list("/alice").stream().anyMatch(entry -> entry.name().equals("team")));

            fs.createDirectory("/alice/home/docs");
            fs.writeFile("/alice/home/docs/note.txt", "hello".getBytes(StandardCharsets.UTF_8));
            assertEquals("docs", fs.stat("/alice/home/docs").name());
            assertTrue(fs.stat("/alice/home/docs").directory());
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8),
                    fs.readFile("/alice/home/docs/note.txt"));

            fs.writeFile("/alice/home/docs/note.txt", "hi".getBytes(StandardCharsets.UTF_8));
            assertEquals("hi", new String(fs.readFile("/alice/home/docs/note.txt"), StandardCharsets.UTF_8));

            fs.writeFile("/alice/team/notes.txt", "shared".getBytes(StandardCharsets.UTF_8));
            assertTrue(fs.list("/alice/team").stream()
                    .anyMatch(entry -> entry.name().equals("notes.txt") && !entry.directory()));
            assertEquals(6, fs.stat("/alice/team/notes.txt").size());

            fs.move("/alice/home/docs/note.txt", "/alice/home/docs/moved.txt");
            assertEquals("hi", new String(fs.readFile("/alice/home/docs/moved.txt"), StandardCharsets.UTF_8));
            fs.delete("/alice/home/docs", true);
            assertTrue(fs.list("/alice/home").isEmpty());
        } finally {
            node.close();
        }
    }

    /**
     * Verifies shared folders expose real directories: mkdir creates an empty
     * directory, rmdir removes it, recursive delete removes a tree, and moving a
     * directory preserves its empty subdirectories.
     */
    @Test
    void sharedFoldersSupportRealDirectories() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            var handle = node.createNewUser("alice");
            var folder = node.createSyncUnit(handle.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.setSharedFolderLabel(handle.data.userId(), folder, "team");
            var fs = new NodeFuseFS(node);

            assertEquals(0, fs.mkdir("/alice/team/docs", 0755));
            assertEquals(0, fs.mkdir("/alice/team/docs/empty", 0755));
            assertTrue(fs.stat("/alice/team/docs").directory());
            assertTrue(fs.stat("/alice/team/docs/empty").directory());
            assertTrue(list(fs, "/alice/team").contains("docs"));
            assertTrue(list(fs, "/alice/team/docs").contains("empty"));

            assertEquals(-ErrorCodes.ENOTEMPTY(), fs.rmdir("/alice/team/docs"));
            assertEquals(0, fs.rmdir("/alice/team/docs/empty"));
            assertEquals(0, fs.rmdir("/alice/team/docs"));
            assertTrue(fs.list("/alice/team").isEmpty());

            assertEquals(0, fs.mkdir("/alice/team/tree", 0755));
            assertEquals(0, fs.mkdir("/alice/team/tree/sub", 0755));
            assertEquals(0, fs.create("/alice/team/tree/keep.txt", 0644, null));
            assertEquals(0, fs.mkdir("/alice/team/tree/sub/empty", 0755));
            fs.move("/alice/team/tree", "/alice/team/moved");
            assertTrue(fs.stat("/alice/team/moved").directory());
            assertTrue(fs.stat("/alice/team/moved/sub/empty").directory());
            assertTrue(fs.stat("/alice/team/moved/keep.txt").size() == 0);
            fs.delete("/alice/team/moved", true);
            assertTrue(fs.list("/alice/team").isEmpty());
        } finally {
            node.close();
        }
    }
}
