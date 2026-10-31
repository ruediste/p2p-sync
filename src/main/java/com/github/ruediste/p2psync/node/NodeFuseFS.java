package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import jnr.ffi.Pointer;
import jnr.posix.POSIXFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/** Exposes each configured user below a directory named by its local name. */
public class NodeFuseFS extends FuseStubFS {
    private final Node node;
    private final int uid = POSIXFactory.getNativePOSIX().getuid();
    private final int gid = POSIXFactory.getNativePOSIX().getgid();

    private static final class FsError extends RuntimeException {
        final int errno;

        FsError(int errno) {
            this.errno = errno;
        }
    }

    private record UserPath(NodeUserHandle user, String path) {
    }

    private record Destination(NodeUserHandle user, String parent, String name) {
    }

    public NodeFuseFS(Node node) {
        this.node = java.util.Objects.requireNonNull(node);
    }

    private int execute(IntSupplier action) {
        try {
            return action.getAsInt();
        } catch (FsError error) {
            return -error.errno;
        } catch (IllegalArgumentException error) {
            return -ErrorCodes.EINVAL();
        }
    }

    private UserPath resolve(String path) {
        if (path == null || !path.startsWith("/"))
            throw new FsError(ErrorCodes.EINVAL());
        var components = new ArrayList<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty())
                continue;
            if (part.equals(".") || part.equals(".."))
                throw new FsError(ErrorCodes.EINVAL());
            components.add(part);
        }
        if (components.isEmpty())
            return new UserPath(null, "/");
        NodeUserHandle user;
        try {
            user = node.getUserByName(components.get(0));
        } catch (IllegalArgumentException error) {
            throw new FsError(ErrorCodes.ENOENT());
        }
        return new UserPath(user, "/" + String.join("/", components.subList(1, components.size())));
    }

    private Destination destination(String path) {
        UserPath target = resolve(path);
        if (target.user() == null || target.path().equals("/"))
            throw new FsError(ErrorCodes.EBUSY());
        int separator = target.path().lastIndexOf('/');
        String parent = separator == 0 ? "/" : target.path().substring(0, separator);
        String name = target.path().substring(separator + 1);
        if (!target.user().isDirectory(parent))
            throw new FsError(ErrorCodes.ENOENT());
        return new Destination(target.user(), parent, name);
    }

    @Override
    public int getattr(String path, FileStat stat) {
        return execute(() -> {
            UserPath target = resolve(path);
            boolean directory;
            long size;
            if (target.user() == null || target.path().equals("/")) {
                directory = true;
                size = 0;
            } else {
                if (!target.user().exists(target.path()))
                    throw new FsError(ErrorCodes.ENOENT());
                directory = target.user().isDirectory(target.path());
                size = directory ? 0 : target.user().fileSize(target.path());
            }
            stat.st_mode.set(directory ? FileStat.S_IFDIR | 0755 : FileStat.S_IFREG | 0644);
            stat.st_nlink.set(directory ? 2 : 1);
            stat.st_uid.set(uid);
            stat.st_gid.set(gid);
            stat.st_size.set(size);
            stat.st_blksize.set(4096);
            stat.st_blocks.set(size / 512 + (size % 512 == 0 ? 0 : 1));
            return 0;
        });
    }

    @Override
    public int readdir(String path, Pointer buffer, FuseFillDir filler, long offset, FuseFileInfo info) {
        return execute(() -> {
            if (offset < 0)
                throw new FsError(ErrorCodes.EINVAL());
            UserPath target = resolve(path);
            List<String> names = new ArrayList<>(List.of(".", ".."));
            if (target.user() == null)
                node.listUsers().forEach(user -> names.add(user.localName()));
            else {
                if (!target.user().isDirectory(target.path()))
                    throw new FsError(ErrorCodes.ENOTDIR());
                target.user().listDirectory(target.path()).forEach(entry -> names.add(entry.name()));
            }
            for (long index = offset; index < names.size(); index++)
                if (filler.apply(buffer, names.get((int) index), null, index + 1) != 0)
                    break;
            return 0;
        });
    }

    private int createEntry(String path, boolean directory) {
        return execute(() -> {
            Destination target = destination(path);
            String fullPath = target.parent().equals("/") ? "/" + target.name()
                    : target.parent() + "/" + target.name();
            if (target.user().exists(fullPath))
                throw new FsError(ErrorCodes.EEXIST());
            try {
                if (directory)
                    target.user().createDirectory(fullPath);
                else
                    target.user().createFile(fullPath);
            } catch (IllegalArgumentException error) {
                throw new FsError(ErrorCodes.ENOENT());
            }
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
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || !target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            return 0;
        });
    }

    @Override
    public int opendir(String path, FuseFileInfo info) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() != null && !target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.ENOTDIR());
            return 0;
        });
    }

    @Override
    public int read(String path, Pointer buffer, long size, long offset, FuseFileInfo info) {
        return execute(() -> {
            if (size < 0 || size > Integer.MAX_VALUE || offset < 0)
                throw new FsError(ErrorCodes.EINVAL());
            UserPath target = resolve(path);
            if (target.user() == null || !target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            byte[] content = target.user().readFile(target.path());
            if (offset >= content.length)
                return 0;
            int count = (int) Math.min(size, content.length - offset);
            buffer.put(0, content, (int) offset, count);
            return count;
        });
    }

    @Override
    public int write(String path, Pointer buffer, long size, long offset, FuseFileInfo info) {
        return execute(() -> {
            if (size < 0 || size > Integer.MAX_VALUE || offset < 0)
                throw new FsError(ErrorCodes.EINVAL());
            if (offset > Long.MAX_VALUE - size)
                throw new FsError(ErrorCodes.EFBIG());
            UserPath target = resolve(path);
            if (target.user() == null || !target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            byte[] bytes = new byte[(int) size];
            buffer.get(0, bytes, 0, bytes.length);
            target.user().writeFile(target.path(), offset, bytes);
            return bytes.length;
        });
    }

    @Override
    public int truncate(String path, long size) {
        return execute(() -> {
            if (size < 0)
                throw new FsError(ErrorCodes.EINVAL());
            UserPath target = resolve(path);
            if (target.user() == null || !target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            target.user().truncateFile(target.path(), size);
            return 0;
        });
    }

    @Override
    public int unlink(String path) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || target.path().equals("/"))
                throw new FsError(ErrorCodes.EBUSY());
            if (!target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            target.user().delete(target.path());
            return 0;
        });
    }

    @Override
    public int rmdir(String path) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || target.path().equals("/"))
                throw new FsError(ErrorCodes.EBUSY());
            if (!target.user().exists(target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            if (!target.user().isDirectory(target.path()))
                throw new FsError(ErrorCodes.ENOTDIR());
            if (!target.user().listDirectory(target.path()).isEmpty())
                throw new FsError(ErrorCodes.ENOTEMPTY());
            target.user().delete(target.path());
            return 0;
        });
    }

    @Override
    public int rename(String path, String newPath) {
        return execute(() -> {
            UserPath source = resolve(path);
            Destination target = destination(newPath);
            if (source.user() == null || source.path().equals("/")
                    || !source.user().data.userId().equals(target.user().data.userId()))
                throw new FsError(ErrorCodes.EXDEV());
            if (!source.user().exists(source.path()))
                throw new FsError(ErrorCodes.ENOENT());
            String targetPath = target.parent().equals("/") ? "/" + target.name()
                    : target.parent() + "/" + target.name();
            if (source.path().equals(targetPath))
                return 0;
            if (source.user().exists(targetPath)) {
                boolean sourceDirectory = source.user().isDirectory(source.path());
                boolean targetDirectory = source.user().isDirectory(targetPath);
                if (sourceDirectory && !targetDirectory)
                    throw new FsError(ErrorCodes.ENOTDIR());
                if (!sourceDirectory && targetDirectory)
                    throw new FsError(ErrorCodes.EISDIR());
                if (targetDirectory && !source.user().listDirectory(targetPath).isEmpty())
                    throw new FsError(ErrorCodes.ENOTEMPTY());
                source.user().delete(targetPath);
            }
            source.user().move(source.path(), targetPath);
            return 0;
        });
    }
}