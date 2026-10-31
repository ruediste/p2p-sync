package com.github.ruediste.p2psync.node;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * Verifies named user roots route I/O to isolated trees and appear at mount
     * root.
     */
    @Test
    void exposesIsolatedUserTreesByLocalName() {
        var network = new Node.Network();
        var node = new Node(network);
        try {
            node.createNewUser("alice");
            node.createNewUser("work");
            var fs = new NodeFuseFS(node);

            assertEquals(0, fs.mkdir("/alice/docs", 0755));
            assertEquals(0, fs.create("/alice/docs/note.txt", 0644, null));
            byte[] content = "private note".getBytes(StandardCharsets.UTF_8);
            assertEquals(content.length, fs.write("/alice/docs/note.txt", buffer(content), content.length, 0, null));
            assertEquals(-ErrorCodes.ENOENT(), fs.open("/work/docs/note.txt", null));

            Pointer readBuffer = buffer(new byte[content.length]);
            assertEquals(content.length, fs.read("/alice/docs/note.txt", readBuffer, content.length, 0, null));
            byte[] actual = new byte[content.length];
            readBuffer.get(0, actual, 0, actual.length);
            assertArrayEquals(content, actual);

            List<String> names = new ArrayList<>();
            FuseFillDir filler = (buffer, name, stat, offset) -> {
                byte[] encoded = new byte[name.remaining()];
                name.get(encoded);
                names.add(new String(encoded, StandardCharsets.UTF_8));
                return 0;
            };
            assertEquals(0, fs.readdir("/", buffer(new byte[0]), filler, 0, null));
            assertTrue(names.containsAll(List.of("alice", "work")));
            FileStat stat = new FileStat(Runtime.getSystemRuntime());
            assertEquals(0, fs.getattr("/alice/docs/note.txt", stat));
            assertEquals(content.length, stat.st_size.longValue());

            assertEquals(0, fs.rename("/alice/docs/note.txt", "/alice/docs/note.tmp"));
            assertEquals(0, fs.rename("/alice/docs/note.tmp", "/alice/docs/note.txt"));
            assertEquals(-ErrorCodes.EXDEV(), fs.rename("/alice/docs/note.txt", "/work/note.txt"));
        } finally {
            node.close();
        }
    }
}