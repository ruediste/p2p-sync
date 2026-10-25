package com.github.ruediste.p2psync.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ruediste.p2psync.util.ShortGuid;

import jnr.ffi.Pointer;
import jnr.posix.POSIXFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

public class SimpleFuseFS extends FuseStubFS implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SimpleFuseFS.class);
    private static final long UTIME_NOW = (1L << 30) - 1;
    private static final long UTIME_OMIT = (1L << 30) - 2;
    private final DataStorage storage = new DataStorage();
    private final DataStorage.RootRefHandle<BTree.NodeBlock> treeRoot = storage.getRootRefHandle(
            1, DataStorage.Area.META, FileTree.BLOCK_TYPE);
    private final FileTree tree;
    private final ShortGuid root = new ShortGuid();
    private final int uid = POSIXFactory.getNativePOSIX().getuid();
    private final int gid = POSIXFactory.getNativePOSIX().getgid();
    private boolean closed;
    private boolean failed;

    public SimpleFuseFS(Path backingFile) {
        root.counter = 1;
        try {
            storage.open(backingFile.toString());
            tree = new FileTree(storage, treeRoot.blockOffset);
            if (tree.getRootOffset() == 0) {
                tree.createDirectory(root, root, "root", new FileTree.FileMeta());
                commit();
            } else if (tree.getDirectoryMeta(root) == null) {
                throw new IllegalStateException("Backing file does not contain a SimpleFuseFS root directory");
            }
        } catch (RuntimeException | Error exception) {
            storage.close();
            throw exception;
        }
    }

    private void commit() {
        treeRoot.blockOffset = tree.getRootOffset();
        storage.commit();
    }

    private synchronized int execute(boolean mutation, IntSupplier action) {
        if (closed || failed)
            return -ErrorCodes.EIO();
        try {
            int result = action.getAsInt();
            if (mutation)
                commit();
            return result;
        } catch (FsError exception) {
            return -exception.errno;
        } catch (RuntimeException exception) {
            failed = true;
            log.error("FUSE storage operation failed; refusing further operations", exception);
            return -ErrorCodes.EIO();
        }
    }

    private static class FsError extends RuntimeException {
        final int errno;

        FsError(int errno) {
            this.errno = errno;
        }
    }

    private static void require(boolean condition, int errno) {
        if (!condition)
            throw new FsError(errno);
    }

    private String normalize(String path) {
        require(path != null && path.startsWith("/"), ErrorCodes.EINVAL());
        return Path.of(path).normalize().toString();
    }

    private FileTree.DirectoryEntry resolve(String path) {
        var entry = new FileTree.DirectoryEntry(root, true, "");
        for (String name : normalize(path).split("/")) {
            if (name.isEmpty())
                continue;
            require(entry.directory(), ErrorCodes.ENOTDIR());
            entry = tree.lookup(entry.guid(), name);
            require(entry != null, ErrorCodes.ENOENT());
        }
        return entry;
    }

    private record Destination(FileTree.DirectoryEntry parent, String name) {
    }

    private Destination destination(String path) {
        String normalized = normalize(path);
        require(!normalized.equals("/"), ErrorCodes.EBUSY());
        int separator = normalized.lastIndexOf('/');
        var parent = resolve(separator == 0 ? "/" : normalized.substring(0, separator));
        require(parent.directory(), ErrorCodes.ENOTDIR());
        return new Destination(parent, normalized.substring(separator + 1));
    }

    private FileTree.DirectoryEntry file(String path) {
        var entry = resolve(path);
        require(!entry.directory(), ErrorCodes.EISDIR());
        return entry;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        return execute(false, () -> {
            var entry = resolve(path);
            var meta = entry.directory() ? tree.getDirectoryMeta(entry.guid()) : tree.getFileMeta(entry.guid());
            stat.st_mode.set(entry.directory() ? FileStat.S_IFDIR | 0755 : FileStat.S_IFREG | 0644);
            stat.st_nlink.set(entry.directory() ? 2 : 1);
            stat.st_uid.set(uid);
            stat.st_gid.set(gid);
            stat.st_size.set(meta.size);
            stat.st_blksize.set(4096);
            stat.st_blocks.set(meta.size / 512 + (meta.size % 512 == 0 ? 0 : 1));
            stat.st_mtim.tv_sec.set(meta.modificationTime / 1000);
            stat.st_mtim.tv_nsec.set(meta.modificationTime % 1000 * 1_000_000);
            stat.st_ctim.tv_sec.set(meta.creationTime / 1000);
            stat.st_ctim.tv_nsec.set(meta.creationTime % 1000 * 1_000_000);
            stat.st_atim.tv_sec.set(meta.modificationTime / 1000);
            stat.st_atim.tv_nsec.set(meta.modificationTime % 1000 * 1_000_000);
            return 0;
        });
    }

    @Override
    public int readdir(String path, Pointer buffer, FuseFillDir filler, long offset, FuseFileInfo info) {
        return execute(false, () -> {
            var entry = resolve(path);
            require(entry.directory(), ErrorCodes.ENOTDIR());
            require(offset >= 0, ErrorCodes.EINVAL());
            List<String> names = new ArrayList<>(List.of(".", ".."));
            tree.listDirectory(entry.guid()).forEach(child -> names.add(child.name()));
            for (long index = offset; index < names.size(); index++) {
                if (filler.apply(buffer, names.get((int) index), null, index + 1) != 0)
                    break;
            }
            return 0;
        });
    }

    private int createEntry(String path, boolean directory) {
        return execute(true, () -> {
            var target = destination(path);
            require(tree.lookup(target.parent().guid(), target.name()) == null, ErrorCodes.EEXIST());
            UUID uuid = UUID.randomUUID();
            ShortGuid guid = new ShortGuid();
            guid.nodeId = (int) uuid.getMostSignificantBits();
            guid.counter = uuid.getLeastSignificantBits();
            if (directory)
                tree.createDirectory(target.parent().guid(), guid, target.name(), new FileTree.FileMeta());
            else
                tree.createFile(target.parent().guid(), guid, target.name(), new FileTree.FileMeta());
            return 0;
        });
    }

    @Override
    public int mkdir(String path, long mode) {
        return createEntry(path, true);
    }

    @Override
    public int create(String path, long mode, FuseFileInfo info) {
        return createEntry(path, false);
    }

    @Override
    public int open(String path, FuseFileInfo info) {
        return execute(false, () -> {
            file(path);
            return 0;
        });
    }

    @Override
    public int opendir(String path, FuseFileInfo info) {
        return execute(false, () -> {
            require(resolve(path).directory(), ErrorCodes.ENOTDIR());
            return 0;
        });
    }

    @Override
    public int read(String path, Pointer buffer, long size, long offset, FuseFileInfo info) {
        return execute(false, () -> {
            require(size >= 0 && size <= Integer.MAX_VALUE && offset >= 0, ErrorCodes.EINVAL());
            byte[] bytes = tree.read(file(path).guid(), offset, (int) size);
            buffer.put(0, bytes, 0, bytes.length);
            return bytes.length;
        });
    }

    @Override
    public int write(String path, Pointer buffer, long size, long offset, FuseFileInfo info) {
        return execute(true, () -> {
            require(size >= 0 && size <= Integer.MAX_VALUE && offset >= 0, ErrorCodes.EINVAL());
            require(offset <= Long.MAX_VALUE - size, ErrorCodes.EFBIG());
            var entry = file(path);
            byte[] bytes = new byte[(int) size];
            buffer.get(0, bytes, 0, bytes.length);
            tree.write(entry.guid(), offset, bytes);
            return bytes.length;
        });
    }

    @Override
    public int truncate(String path, long size) {
        return execute(true, () -> {
            require(size >= 0, ErrorCodes.EINVAL());
            tree.truncate(file(path).guid(), size);
            return 0;
        });
    }

    @Override
    public int utimens(String path, Timespec[] times) {
        return execute(true, () -> {
            var entry = resolve(path);
            if (times != null)
                require(times.length == 2 && times[1] != null, ErrorCodes.EINVAL());

            long modificationTime;
            if (times == null || times[1].tv_nsec.longValue() == UTIME_NOW) {
                modificationTime = System.currentTimeMillis();
            } else {
                long nanoseconds = times[1].tv_nsec.longValue();
                if (nanoseconds == UTIME_OMIT)
                    return 0;
                require(nanoseconds >= 0 && nanoseconds < 1_000_000_000L, ErrorCodes.EINVAL());
                try {
                    modificationTime = Math.addExact(
                            Math.multiplyExact(times[1].tv_sec.longValue(), 1000), nanoseconds / 1_000_000);
                } catch (ArithmeticException exception) {
                    throw new FsError(ErrorCodes.EOVERFLOW());
                }
            }
            tree.setModificationTime(entry.guid(), modificationTime);
            return 0;
        });
    }

    @Override
    public int unlink(String path) {
        return execute(true, () -> {
            tree.delete(file(path).guid());
            return 0;
        });
    }

    @Override
    public int rmdir(String path) {
        return execute(true, () -> {
            destination(path);
            var entry = resolve(path);
            require(entry.directory(), ErrorCodes.ENOTDIR());
            require(tree.listDirectory(entry.guid()).isEmpty(), ErrorCodes.ENOTEMPTY());
            tree.delete(entry.guid());
            return 0;
        });
    }

    @Override
    public int rename(String path, String newPath) {
        return execute(true, () -> {
            String sourcePath = normalize(path);
            String targetPath = normalize(newPath);
            var sourceParent = destination(sourcePath);
            var source = resolve(sourcePath);
            var target = destination(targetPath);
            if (sourcePath.equals(targetPath))
                return 0;
            require(!source.directory() || !targetPath.startsWith(sourcePath + "/"), ErrorCodes.EINVAL());
            var existing = tree.lookup(target.parent().guid(), target.name());
            if (existing != null) {
                require(!existing.directory() || source.directory(), ErrorCodes.EISDIR());
                require(existing.directory() || !source.directory(), ErrorCodes.ENOTDIR());
                require(!existing.directory() || tree.listDirectory(existing.guid()).isEmpty(), ErrorCodes.ENOTEMPTY());
                tree.delete(existing.guid());
            }
            String temporaryName;
            do {
                temporaryName = UUID.randomUUID().toString();
            } while (tree.lookup(sourceParent.parent().guid(), temporaryName) != null
                    || tree.lookup(target.parent().guid(), temporaryName) != null);
            tree.rename(source.guid(), temporaryName);
            tree.move(source.guid(), target.parent().guid());
            tree.rename(source.guid(), target.name());
            return 0;
        });
    }

    @Override
    public int flush(String path, FuseFileInfo info) {
        return execute(false, () -> 0);
    }

    @Override
    public int fsync(String path, int datasync, FuseFileInfo info) {
        return execute(true, () -> 0);
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            storage.close();
        }
    }
}