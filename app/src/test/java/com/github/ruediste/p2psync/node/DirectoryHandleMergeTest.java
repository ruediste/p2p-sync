package com.github.ruediste.p2psync.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.github.ruediste.p2psync.clock.VectorClock;
import com.github.ruediste.p2psync.node.DirectoryHandle.DirectoryEntryHandle;
import com.github.ruediste.p2psync.node.DirectoryHandle.FileEntryHandle;
import com.github.ruediste.p2psync.node.Node.Network;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.InvalidProtocolBufferException;

public class DirectoryHandleMergeTest {

    private Network network;
    private Node nodeA;
    private Node nodeB;
    private NodeUserHandle handleA;
    private NodeUserHandle handleB;
    private int nrA;
    private int nrB;

    @Before
    public void before() {
        network = new Network();
        nodeA = new Node(network);
        nodeB = new Node(network);
        // deterministic peer order: nodeA has the lower peer id
        if (nodeA.peerId.compareTo(nodeB.peerId) > 0) {
            var tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
        handleA = nodeA.createNewUser();
        handleB = nodeB.join(handleA.data.userKey);
        nrA = handleA.data.localClock.getOwnNr();
        nrB = handleB.data.localClock.getOwnNr();
    }

    private FileEntryHandle file(String name, int nr, long count) {
        var clock = VectorClock.empty();
        for (long i = 0; i < count; i++)
            clock.increment(nr);
        var file = new FileEntryHandle();
        file.clock = clock;
        file.name = name;
        file.dataBlockId = new BlockId(new byte[] { 1 });
        return file;
    }

    private DirectoryHandle dir(NodeUserHandle handle) {
        return DirectoryHandle.empty(handle);
    }

    private FileEntryHandle get(List<FileEntryHandle> files, String name) {
        return files.stream().filter(f -> f.name.equals(name)).findFirst().orElse(null);
    }

    private DirectoryHandle loadSub(DirectoryHandle parent, String name) {
        var entry = parent.directories.stream().filter(d -> d.name.equals(name)).findFirst().orElseThrow();
        try {
            return DirectoryHandle.fromProto(Sync.Directory.parseFrom(network.getBlock(entry.directoryId)), handleA);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void clocksAreMerged() {
        var a = dir(handleA);
        a.clock.increment(nrA);
        var b = dir(handleB);
        b.clock.increment(nrB);

        a.merge(b);
        assertEquals(1, a.clock.get(nrA));
        assertEquals(1, a.clock.get(nrB));
    }

    @Test
    public void fileOnlyInOther_isAdded() {
        var a = dir(handleA);
        var b = dir(handleB);
        b.files.add(file("foo", nrB, 1));

        a.merge(b);
        assertNotNull(get(a.files, "foo"));
    }

    @Test
    public void fileDeletedByOther_isRemoved() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        // other has seen the file (clock 1:1) and no longer contains it
        var b = dir(handleB);
        b.clock.increment(nrA);
        b.clock.increment(nrB);

        a.merge(b);
        assertNull(get(a.files, "foo"));
    }

    @Test
    public void locallyModifiedFile_survivesRemoteDelete() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 2));
        // other has only seen version 1:1 of the file, but deleted it
        var b = dir(handleB);
        b.clock.increment(nrA);
        b.clock.increment(nrB);

        a.merge(b);
        assertNotNull(get(a.files, "foo"));
    }

    @Test
    public void newerOtherFile_wins() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        var b = dir(handleB);
        // the other file has seen our version and was modified afterwards
        var otherFile = file("foo", nrB, 2);
        otherFile.clock.merge(a.files.get(0).clock);
        b.files.add(otherFile);

        a.merge(b);
        var result = get(a.files, "foo");
        assertNotNull(result);
        assertEquals(1, result.clock.get(nrA));
        assertEquals(2, result.clock.get(nrB));
    }

    @Test
    public void newerLocalFile_isKept() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 2));
        var b = dir(handleB);
        // the other file is an older version of ours
        b.files.add(file("foo", nrA, 1));

        a.merge(b);
        var result = get(a.files, "foo");
        assertNotNull(result);
        assertEquals(2, result.clock.get(nrA));
    }

    @Test
    public void equalClocks_keepLocalFile() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        var b = dir(handleB);
        b.files.add(file("foo", nrA, 1));

        a.merge(b);
        assertEquals(1, a.files.size());
        assertEquals(1, a.files.get(0).clock.get(nrA));
    }

    @Test
    public void concurrentFiles_lowerPeerIdKeepsOwn_noConflictSuffix() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        var b = dir(handleB);
        b.files.add(file("foo", nrB, 1));

        a.merge(b);
        assertEquals(2, a.files.size());
        // nodeA has the lower peer id: its version keeps the original name
        var main = get(a.files, "foo");
        assertNotNull(main);
        assertEquals(1, main.clock.get(nrA));
        assertNotNull(get(a.files, "foo_conflict"));
    }

    @Test
    public void concurrentFiles_higherPeerIdTakesOtherAsMain() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        var b = dir(handleB);
        b.files.add(file("foo", nrB, 1));

        b.merge(a);
        assertEquals(2, b.files.size());
        // nodeB has the higher peer id: the other version (nodeA's) keeps the
        // original name
        var main = get(b.files, "foo");
        assertNotNull(main);
        assertEquals(1, main.clock.get(nrA));
        assertNotNull(get(b.files, "foo_conflict"));
    }

    @Test
    public void multipleConflicts_getDistinctNames() {
        var a = dir(handleA);
        a.files.add(file("foo", nrA, 1));
        a.files.add(file("bar", nrA, 1));
        var b = dir(handleB);
        b.files.add(file("foo", nrB, 1));
        b.files.add(file("bar", nrB, 1));

        a.merge(b);
        assertEquals(4, a.files.size());
        assertNotNull(get(a.files, "foo"));
        assertNotNull(get(a.files, "foo_conflict"));
        assertNotNull(get(a.files, "bar"));
        assertNotNull(get(a.files, "bar_conflict"));
    }

    @Test
    public void directoryOnlyInOther_isAdded() {
        var a = dir(handleA);
        var b = dir(handleB);
        var entry = new DirectoryEntryHandle();
        entry.name = "sub";
        entry.clock = VectorClock.empty();
        entry.clock.increment(nrB);
        entry.directoryId = nodeB.storage.store(dir(handleB).toProto());
        b.directories.add(entry);

        a.merge(b);
        assertNotNull(a.directories.stream().filter(d -> d.name.equals("sub")).findFirst().orElse(null));
    }

    @Test
    public void concurrentDirectories_areMergedRecursively() {
        var a = dir(handleA);
        var b = dir(handleB);

        var subA = dir(handleA);
        subA.files.add(file("x", nrA, 1));
        var entryA = new DirectoryEntryHandle();
        entryA.name = "sub";
        entryA.clock = VectorClock.empty();
        entryA.clock.increment(nrA);
        entryA.directoryId = nodeA.storage.store(subA.toProto());
        a.directories.add(entryA);

        var subB = dir(handleB);
        subB.files.add(file("x", nrB, 1));
        var entryB = new DirectoryEntryHandle();
        entryB.name = "sub";
        entryB.clock = VectorClock.empty();
        entryB.clock.increment(nrB);
        entryB.directoryId = nodeB.storage.store(subB.toProto());
        b.directories.add(entryB);

        a.merge(b);

        assertEquals(1, a.directories.size());
        var merged = loadSub(a, "sub");
        assertEquals(2, merged.files.size());
        // nodeA has the lower peer id: its file keeps the original name
        var main = get(merged.files, "x");
        assertNotNull(main);
        assertEquals(1, main.clock.get(nrA));
        assertNotNull(get(merged.files, "x_conflict"));
    }

    @Test
    public void concurrentSubdirectoryModification_respectsLocalModification() {
        var a = dir(handleA);
        var entryA = new DirectoryEntryHandle();
        entryA.name = "sub";
        entryA.clock = VectorClock.empty();
        entryA.clock.increment(nrA);
        entryA.clock.increment(nrA);
        entryA.directoryId = nodeA.storage.store(dir(handleA).toProto());
        a.directories.add(entryA);

        var b = dir(handleB);
        var entryB = new DirectoryEntryHandle();
        entryB.name = "sub";
        entryB.clock = VectorClock.empty();
        entryB.clock.increment(nrA);
        entryB.directoryId = nodeB.storage.store(dir(handleB).toProto());
        b.directories.add(entryB);

        a.merge(b);
        // our subdirectory is strictly newer, keep it without recursive merge
        assertEquals(1, a.directories.size());
        assertSame(entryA, a.directories.get(0));
    }

    private void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual);
    }
}
