package com.github.ruediste.p2psync.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
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
    public void writeFillingWalCapacitySucceeds() throws IOException {
        space.open(path);

        // 14 full blocks fit within the 16-block WAL area
        byte[] data = generate(14 * AtomicSpace.WAL_BLOCK_SIZE, 81);
        space.write(data, 0);
        space.commit();

        assertArrayEquals(data, space.read(0, data.length));
    }

    @Test
    public void walOverflowFails() throws IOException {
        space.open(path);

        byte[] committed = generate(1000, 71);
        space.write(committed, 0);
        space.commit();

        // fill the WAL beyond its capacity without committing
        byte[] overflow = generate(17 * AtomicSpace.WAL_BLOCK_SIZE, 72);
        try {
            space.write(overflow, 1000);
            fail("expected exception");
        } catch (Exception e) {
            // expected: WAL overflow detected
        }

        // reopen: committed data is preserved, uncommitted overflow is discarded
        space = new AtomicSpace();
        space.open(path);
        assertArrayEquals(committed, space.read(0, committed.length));

        // and the space is usable again
        byte[] committed2 = generate(1000, 73);
        space.write(committed2, 0);
        space.commit();
        assertArrayEquals(committed2, space.read(0, committed2.length));
    }

    @Test
    public void rollbackDiscardsUncommittedWrites() throws IOException {
        space.open(path);

        byte[] original = generate(4096, 91);
        space.writeDirect(original, 0);
        space.commit();

        // uncommitted writes, large enough to spill into multiple WAL blocks
        byte[] uncommitted = generate(2 * AtomicSpace.WAL_BLOCK_SIZE, 92);
        space.write(uncommitted, 0);

        space.rollback();

        // data area is unchanged after rollback
        assertArrayEquals(original, space.read(0, original.length));

        // rollback is durable: a reopen still shows the old content
        space = new AtomicSpace();
        space.open(path);
        assertArrayEquals(original, space.read(0, original.length));

        // and the space is usable again
        byte[] committed = generate(1000, 93);
        space.write(committed, 0);
        space.commit();
        assertArrayEquals(committed, space.read(0, committed.length));
    }

    @Test
    public void readSeesUncommittedWrites() throws IOException {
        space.open(path);

        byte[] committed = generate(4096, 101);
        space.writeDirect(committed, 0);
        space.commit();

        byte[] uncommitted = generate(1000, 102);
        space.write(uncommitted, 500);

        // read sees the uncommitted write instead of the committed content
        assertArrayEquals(uncommitted, space.read(500, uncommitted.length));

        // reads overlapping the uncommitted write mix both states
        assertArrayEquals(Arrays.copyOf(committed, 500), space.read(0, 500));
        assertArrayEquals(uncommitted, space.read(500, 1000));
        assertArrayEquals(Arrays.copyOfRange(committed, 1500, 4096), space.read(1500, 2596));

        // partial overlap in the middle of the write
        byte[] middle = space.read(600, 300);
        assertArrayEquals(Arrays.copyOfRange(uncommitted, 100, 400), middle);

        // after commit, the data area reflects the write
        space.commit();
        assertArrayEquals(uncommitted, space.read(500, uncommitted.length));
    }

    @Test
    public void uncommittedWritesApplyInWriteOrder() throws IOException {
        space.open(path);

        byte[] first = generate(1000, 111);
        space.write(first, 0);
        byte[] second = generate(1000, 112);
        space.write(second, 0);

        // the later write wins in the overlay
        assertArrayEquals(second, space.read(0, 1000));

        space.rollback();
        // after rollback the writes are gone
        assertArrayEquals(new byte[1000], space.read(0, 1000));
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
