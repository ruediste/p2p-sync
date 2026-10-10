package com.github.ruediste.p2psync.atomicSpace;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AtomicSpaceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private byte[] generate(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }

    Path path;

    AtomicSpace space;

    @Before
    public void before() throws IOException {
        path = tmp.newFolder().toPath().resolve("space.bin");
        space = new AtomicSpace();
    }

    @After
    public void after() {
        space.close();
    }

    @Test
    public void newFileDoesNotThrow() throws IOException {
        space.open(path);
    }

    @Test
    public void openInvalidMagicFails() throws IOException {
        space.open(path);
        space.close();
        space = new AtomicSpace();
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
            f.write(new byte[16]);
        }

        try {
            space.open(path);
            fail("expected exception");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void openUnsupportedVersionFails() throws IOException {
        space.open(path);
        space.close();
        space = new AtomicSpace();
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
            f.seek(16);
            f.writeInt(2);
        }

        try {
            space.open(path);
            fail("expected exception");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void writeAndCommitPersistsData() throws IOException {
        space.open(path);

        byte[] data = generate(1000, 42);
        space.write(data, 100);
        space.commit();

        assertArrayEquals(data, space.read(100, data.length));
        // untouched area stays zero
        assertArrayEquals(new byte[100], space.read(0, 100));
    }

    @Test
    public void uncommittedWritesAreNotAppliedAfterReopen() throws IOException {
        space.open(path);

        byte[] original = generate(4096, 1);
        space.writeDirect(original, 0);

        // write enough uncommitted data to force WAL blocks to be written,
        // then "crash" by abandoning the instance without commit
        byte[] uncommitted = generate(2 * AtomicSpace.WAL_BLOCK_SIZE, 2);
        space.write(uncommitted, 0);

        space = new AtomicSpace();
        space.open(path);

        // the data area still contains the old content
        assertArrayEquals(original, space.read(0, original.length));

        // a subsequent write and commit is applied
        byte[] committed = generate(1000, 3);
        space.write(committed, 0);
        space.commit();
        assertArrayEquals(committed, space.read(0, committed.length));
    }

    @Test
    public void committedDataSurvivesReopen() throws IOException {
        space.open(path);

        byte[] data = generate(5000, 7);
        space.write(data, 12345);
        space.commit();

        space = new AtomicSpace();
        space.open(path);

        assertArrayEquals(data, space.read(12345, data.length));
    }

    @Test
    public void largeWriteSpanningMultipleWalBlocks() throws IOException {
        space.open(path);

        byte[] data = generate(3 * AtomicSpace.WAL_BLOCK_SIZE, 11);
        space.write(data, 0);
        space.commit();

        assertArrayEquals(data, space.read(0, data.length));
    }

    @Test
    public void multipleCommitsAccumulate() throws IOException {

        space.open(path);

        byte[] first = generate(1000, 21);
        space.write(first, 0);
        space.commit();

        byte[] second = generate(1000, 22);
        space.write(second, 5000);
        space.commit();

        assertArrayEquals(first, space.read(0, first.length));
        assertArrayEquals(second, space.read(5000, second.length));
    }

    @Test
    public void writeOverwritesExistingData() throws IOException {

        space.open(path);

        byte[] first = generate(2048, 31);
        space.write(first, 0);
        space.commit();

        byte[] second = generate(2048, 32);
        space.write(second, 0);
        space.commit();

        assertArrayEquals(second, space.read(0, second.length));
    }

    @Test
    public void resizeChangesFileLength() throws IOException {

        space.open(path);

        long newSize = 8 * 1024 * 1024;
        space.resize(newSize);

        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r")) {
            assertTrue(newSize < f.length());
        }
    }

    @Test
    public void writeDirectBypassesWal() throws IOException {

        space.open(path);

        byte[] data = generate(256, 51);
        space.writeDirect(data, 4096);

        assertArrayEquals(data, space.read(4096, data.length));
    }

    @Test
    public void commitAfterReopenWithoutWalContent() throws IOException {

        space.open(path);

        byte[] data = generate(1000, 61);
        space.write(data, 0);
        space.commit();

        space = new AtomicSpace();
        space.open(path);

        // reopen clears the WAL; a fresh commit cycle must work
        byte[] data2 = generate(1000, 62);
        space.write(data2, 1000);
        space.commit();

        assertArrayEquals(data, space.read(0, data.length));
        assertArrayEquals(data2, space.read(1000, data2.length));
    }
}
