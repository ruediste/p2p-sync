package com.github.ruediste.p2psync.atomicSpace;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

public class AtomicSpace implements AutoCloseable {
    /*
     * Data Structures
     * ===============
     * 
     * File Layout:
     * - File Header (padded to 4KB)
     * - WAL Header (padded to 4KB)
     * - WAL Area
     * - Data Area (rest of the file)
     * 
     * File Header (padded to 4KB)
     * - Magic number (16 bytes) (0x54d850fd3e3cebc650dd422e2d70380b)
     * - Version (4 bytes) (currently 1)
     * - WAL block count(4 bytes)
     * 
     * WAL Header (padded to 4KB)
     * - Current Generation (4 bytes)
     * 
     * WAL Block (always 64KB)
     * - Generation (4 bytes)
     * - Data length (4 bytes) (without header and checksum)
     * - Data
     * - Checksum (4 bytes) (CRC32 of the whole block, excluding the checksum
     * itself)
     * - unused/padding (rest of the block)
     * 
     * WAL Operation
     * - Position in Data Area (8 bytes)
     * - Length (4 bytes) (0 indicates a commit)
     * - Data (variable length)
     * 
     * Write Path
     * ===============
     * All writes are first recorded in the WAL. The current WAL block is filled up
     * with operations.
     * Once the block is full, the checksum is calculated and the block is written
     * to the WAL area.
     * On commit, a final WAL operation with length 0 is written, and the current
     * WAL block is flushed to the WAL area.
     * Then all the operations recorded in the WAL are applied to the data area.
     * Finally, the WAL
     * is cleared by incrementing the current generation in the WAL header.
     * 
     * Recovery Path
     * ===============
     * On startup, the WAL header is read to determine the current generation. All
     * WAL blocks with a matching
     * generation and valid checksums are read. All operations before the last
     * commit are applied to the data area.
     * Then the WAL is cleared by incrementing the current generation in the WAL
     * header.
     */

    private static final byte[] MAGIC = new byte[] {
            (byte) 0x54, (byte) 0xd8, (byte) 0x50, (byte) 0xfd,
            (byte) 0x3e, (byte) 0x3c, (byte) 0xeb, (byte) 0xc6,
            (byte) 0x50, (byte) 0xdd, (byte) 0x42, (byte) 0x2e,
            (byte) 0x2d, (byte) 0x70, (byte) 0x38, (byte) 0x0b
    };

    private static final int FILE_HEADER_SIZE = 4096;
    private static final int WAL_HEADER_SIZE = 4096;
    public static final int WAL_BLOCK_SIZE = 64 * 1024;
    private static final int WAL_BLOCK_HEADER_SIZE = 4 + 4; // Generation (4 bytes) + Data length (4 bytes)
    private static final int WAL_BLOCK_CHECKSUM_SIZE = 4; // Checksum (4 bytes)

    private static final int WAL_OPERATION_HEADER_SIZE = 8 + 4; // Offset (8 bytes) + Length (4 bytes)
    private static final int COMMIT_OPERATION_LENGTH = 0;

    private RandomAccessFile file;

    @Override
    public void close() {
        try {
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int walBlockCount;
    private int currentGeneration;
    private int nextWalBlockIndex;

    private long dataAreaStart;

    private ByteBuffer currentWalBlock = ByteBuffer.allocateDirect(WAL_BLOCK_SIZE);
    {
        currentWalBlock.position(WAL_BLOCK_HEADER_SIZE);
    }

    public void open(Path path) {
        try {
            boolean created = false;
            if (!path.toFile().exists()) {
                path.toFile().createNewFile();
                created = true;
            }

            file = new RandomAccessFile(path.toFile(), "rw");
            if (created) {
                walBlockCount = 16;
                // the empty file should be all zeroes, thus generation 1 is safe to use
                currentGeneration = 1;
                nextWalBlockIndex = 0;

                // Set the initial file length to accommodate the file header, WAL area, and 1MB
                // of data area
                file.setLength(FILE_HEADER_SIZE + WAL_BLOCK_SIZE * walBlockCount + 1024 * 1024);
                // Initialize file header and WAL headers
                // Write magic number
                file.seek(0);
                file.write(MAGIC);
                // Write version
                file.writeInt(1);
                // Write WAL block count
                file.writeInt(walBlockCount);
                // Initialize WAL header with current generation 0
                file.seek(FILE_HEADER_SIZE);
                file.writeInt(currentGeneration);

            } else {
                // Open existing file, read headers if necessary
                file.seek(0);
                byte[] magic = new byte[16];
                file.readFully(magic);
                if (!Arrays.equals(magic, MAGIC)) {
                    throw new IOException("Invalid magic number");
                }
                int version = file.readInt();
                if (version != 1) {
                    throw new IOException("Unsupported version: " + version);
                }
                walBlockCount = file.readInt();

                file.seek(FILE_HEADER_SIZE);
                currentGeneration = file.readInt();

                // after recovery, the WAL is empty and cleared, so writing restarts at
                // block 0
                nextWalBlockIndex = 0;

                performRecovery();
            }
            dataAreaStart = FILE_HEADER_SIZE + WAL_HEADER_SIZE + (long) WAL_BLOCK_SIZE * walBlockCount;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open file " + path, e);
        }
    }

    public void resize(long newSize) {
        // resize the underlying file to the new size
        try {
            file.setLength(newSize + dataAreaStart);
        } catch (IOException e) {
            throw new RuntimeException("Failed to resize file", e);
        }
    }

    public void write(byte[] data, long positionInDataArea) throws IOException {
        // Write data to the WAL first, then to the data area at the specified offset

        // Create WAL operations. Split data if the operation exceeds the remaining
        // space in the current WAL block.

        int offset = 0;
        while (offset < data.length) {

            // Check if there is enough space in the current WAL block for the next
            // operation, including 16 bytes for a minimal sensible operation length
            if (currentWalBlock.position() + WAL_OPERATION_HEADER_SIZE + 16 >= WAL_BLOCK_SIZE
                    - WAL_BLOCK_CHECKSUM_SIZE) {
                writeCurrentWalBlock();
            }

            int nextOperationLength = Math.min(data.length - offset,
                    WAL_BLOCK_SIZE - WAL_BLOCK_CHECKSUM_SIZE - currentWalBlock.position()
                            - WAL_OPERATION_HEADER_SIZE);

            // write operation into current wal block
            currentWalBlock.putLong(positionInDataArea + offset);
            currentWalBlock.putInt(nextOperationLength);
            currentWalBlock.put(data, offset, nextOperationLength);
            offset += nextOperationLength;
        }
    }

    public void commit() throws IOException {
        // Write a commit operation to the WAL
        if (currentWalBlock.position() + WAL_OPERATION_HEADER_SIZE >= WAL_BLOCK_SIZE
                - WAL_BLOCK_CHECKSUM_SIZE) {
            writeCurrentWalBlock();
        }

        // write commit operation into current wal block
        currentWalBlock.putLong(0);
        currentWalBlock.putInt(COMMIT_OPERATION_LENGTH);

        // Write the current WAL block to the file to ensure the commit operation is
        // persisted
        writeCurrentWalBlock();

        // apply the changes from the WAL to the data area
        applyWal(null);

        // clear the WAL by incrementing the current generation in the WAL header
        clearWal();
    }

    /**
     * Clear the WAL by incrementing the current generation in the WAL header and
     * resetting the block index.
     */
    private void clearWal() throws IOException {
        // make sure all changes are durable before clearing the WAL
        file.getChannel().force(false);

        currentGeneration++;
        file.seek(FILE_HEADER_SIZE);
        file.writeInt(currentGeneration);
        file.getChannel().force(false);
        nextWalBlockIndex = 0;
        currentWalBlock.clear();
        currentWalBlock.position(WAL_BLOCK_HEADER_SIZE);
    }

    /**
     * Result of a WAL scan: the position (block index and offset within the block)
     * of the last commit operation found.
     */
    private static final class WalPosition {
        final int blockIndex;
        final int offsetInBlock;

        WalPosition(int blockIndex, int offsetInBlock) {
            this.blockIndex = blockIndex;
            this.offsetInBlock = offsetInBlock;
        }
    }

    /**
     * Read WAL blocks with the current generation and valid checksums, starting at
     * block 0. Returns the position of the last commit operation found, or
     * {@code null} if no commit is present.
     */
    private WalPosition scanWalForLastCommit() throws IOException {
        ByteBuffer block = ByteBuffer.allocateDirect(WAL_BLOCK_SIZE);
        CRC32 crc32 = new CRC32();
        WalPosition lastCommit = null;

        for (int blockIndex = 0; blockIndex < walBlockCount; blockIndex++) {
            block.clear();
            long bytesRead = file.getChannel().read(block, walBlockPosition(blockIndex));
            if (bytesRead < WAL_BLOCK_HEADER_SIZE) {
                break;
            }
            block.flip();

            // blocks are only valid if the generation matches
            int generation = block.getInt();
            if (generation != currentGeneration) {
                break;
            }
            int dataLength = block.getInt();
            if (dataLength < 0 || dataLength > WAL_BLOCK_SIZE - WAL_BLOCK_HEADER_SIZE - WAL_BLOCK_CHECKSUM_SIZE) {
                break;
            }

            // validate the checksum (over the whole block except the checksum itself)
            int expectedChecksum = block.getInt(WAL_BLOCK_HEADER_SIZE + dataLength);
            block.position(0);
            block.limit(WAL_BLOCK_HEADER_SIZE + dataLength);
            crc32.reset();
            crc32.update(block);
            if ((int) crc32.getValue() != expectedChecksum) {
                break;
            }

            // search for the last commit operation in this block
            block.limit(WAL_BLOCK_HEADER_SIZE + dataLength);
            block.position(WAL_BLOCK_HEADER_SIZE);
            while (block.remaining() >= WAL_OPERATION_HEADER_SIZE) {
                block.getLong();
                int length = block.getInt();

                if (length == COMMIT_OPERATION_LENGTH) {
                    lastCommit = new WalPosition(blockIndex,
                            block.position() - WAL_OPERATION_HEADER_SIZE);
                } else if (length < 0 || block.remaining() < length) {
                    // corrupted operation, stop scanning
                    return lastCommit;
                } else {
                    block.position(block.position() + length);
                }
            }
        }

        return lastCommit;
    }

    /**
     * Read WAL blocks starting at the given block index and apply their operations
     * to the data area. Operations are applied up to (but not including) the given
     * end position. If {@code end} is {@code null}, all operations in all blocks up
     * to {@code nextWalBlockIndex} are applied.
     * 
     * Does not validate the blocks themselves. They are presumed to be correct
     * during
     * normal operation. During recovery, the last commit scan already performs the
     * validations.
     */
    private void applyWal(WalPosition end) throws IOException {
        // reuse the WAL buffer
        ByteBuffer block = currentWalBlock;

        int lastBlockIndex = end == null ? nextWalBlockIndex : end.blockIndex + 1;
        for (int blockIndex = 0; blockIndex < lastBlockIndex; blockIndex++) {
            block.clear();
            file.getChannel().read(block, walBlockPosition(blockIndex));
            block.flip();

            // skip generation
            block.getInt();

            // read data length
            int dataLength = block.getInt();
            block.limit(WAL_BLOCK_HEADER_SIZE + dataLength);

            // determine the end of the operations to apply in this block
            int dataEnd = WAL_BLOCK_HEADER_SIZE + dataLength;
            int stopAt = dataEnd;
            if (end != null && end.blockIndex == blockIndex) {
                stopAt = WAL_BLOCK_HEADER_SIZE + end.offsetInBlock;
            }

            // iterate the operations contained in the block
            block.position(WAL_BLOCK_HEADER_SIZE);
            while (block.position() < stopAt) {
                long positionInDataArea = block.getLong();
                int length = block.getInt();

                if (length == COMMIT_OPERATION_LENGTH) {
                    // commit operation: nothing to apply
                    // We should have reached the stopAt as well
                    continue;
                }

                // apply the operation to the data area
                block.limit(block.position() + length);
                file.getChannel().write(block, dataAreaStart + positionInDataArea);
                block.limit(dataEnd);
            }
        }
    }

    private void writeCurrentWalBlock() throws IOException {
        // set the generation
        currentWalBlock.putInt(0, currentGeneration);
        // set the data length
        currentWalBlock.putInt(4, currentWalBlock.position() - WAL_BLOCK_HEADER_SIZE);

        // calculate and set the checksum for the current WAL block
        var pos = currentWalBlock.position();
        currentWalBlock.limit(pos);
        currentWalBlock.position(0);
        CRC32 crc32 = new CRC32();
        crc32.update(currentWalBlock);
        currentWalBlock.limit(currentWalBlock.capacity());
        currentWalBlock.putInt(pos, (int) crc32.getValue());

        // set the limit to the end of the block including
        // the checksum
        currentWalBlock.position(0);
        currentWalBlock.limit(pos + WAL_BLOCK_CHECKSUM_SIZE);

        // write the block to the file
        file.getChannel().write(currentWalBlock,
                walBlockPosition(nextWalBlockIndex));

        // Move to the next WAL block
        nextWalBlockIndex++;
        currentWalBlock.clear();
        currentWalBlock.position(WAL_BLOCK_HEADER_SIZE);
    }

    private int walBlockPosition(int blockIndex) {
        return FILE_HEADER_SIZE + WAL_HEADER_SIZE + WAL_BLOCK_SIZE * blockIndex;
    }

    public void writeDirect(byte[] data, long offset) throws IOException {
        // write directly to the data area, bypassing the WAL
        file.getChannel().write(ByteBuffer.wrap(data), dataAreaStart + offset);
    }

    public byte[] read(long positionInDataArea, int length) throws IOException {
        byte[] result = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        long position = dataAreaStart + positionInDataArea;
        while (buffer.hasRemaining()) {
            int bytesRead = file.getChannel().read(buffer, position);
            if (bytesRead < 0) {
                throw new IOException("Unexpected end of file while reading data area");
            }
            position += bytesRead;
        }
        return result;
    }

    private void performRecovery() throws IOException {
        // find the last commit in the WAL
        WalPosition lastCommit = scanWalForLastCommit();

        // apply all operations before the last commit to the data area
        if (lastCommit != null) {
            applyWal(lastCommit);
        }

        // clear the WAL by incrementing the current generation in the WAL header
        clearWal();
    }
}
