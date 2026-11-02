package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
