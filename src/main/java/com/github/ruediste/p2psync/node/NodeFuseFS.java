package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import jnr.ffi.Pointer;
import jnr.posix.POSIXFactory;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/**
 * Exposes each configured user below a directory named by its local name. Every
 * user directory is a combined root view containing the user's {@code home}
 * tree and the accepted shared folders by their local labels.
 */
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

    private record UserPath(NodeUserHandle user, Node.RootEntry root, String path) {
    }

    private record Destination(NodeUserHandle user, Node.RootEntry root, String parent, String name) {
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
            return new UserPath(null, null, "/");
        NodeUserHandle user;
        try {
            user = node.getUserByName(components.get(0));
        } catch (IllegalArgumentException error) {
            throw new FsError(ErrorCodes.ENOENT());
        }
        if (components.size() == 1)
            return new UserPath(user, null, "/");
        Node.RootEntry root = findRoot(user, components.get(1));
        if (root == null)
            throw new FsError(ErrorCodes.ENOENT());
        String relative = components.size() == 2 ? "/"
                : "/" + String.join("/", components.subList(2, components.size()));
        return new UserPath(user, root, relative);
    }

    private Node.RootEntry findRoot(NodeUserHandle user, String label) {
        for (var entry : node.listRootEntries(user.data.userId()))
            if (entry.label().equals(label))
                return entry;
        return null;
    }

    private Destination destination(String path) {
        UserPath target = resolve(path);
        if (target.user() == null || target.root() == null || target.path().equals("/"))
            throw new FsError(ErrorCodes.EBUSY());
        String parent = parentPath(target.path());
        String name = leafName(target.path());
        if (target.root().home() && !isDirectory(target.user(), target.root(), parent))
            throw new FsError(ErrorCodes.ENOENT());
        return new Destination(target.user(), target.root(), parent, name);
    }

    private static String parentPath(String path) {
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    private static String leafName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String childPath(String parent, String name) {
        return parent.equals("/") ? "/" + name : parent + "/" + name;
    }

    private static String sharedKey(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private boolean isDirectory(NodeUserHandle user, Node.RootEntry root, String path) {
        if (root.home())
            return user.isDirectory(path);
        if (sharedKey(path).isEmpty())
            return true;
        if (sharedFileExists(user, root, path))
            return false;
        return sharedDirectoryExists(user, root, path);
    }

    private boolean sharedFileExists(NodeUserHandle user, Node.RootEntry root, String path) {
        String key = sharedKey(path);
        return !key.isEmpty() && node.sharedFileSize(root.unitId(), user.data.userId(), key) >= 0;
    }

    private boolean sharedDirectoryExists(NodeUserHandle user, Node.RootEntry root, String path) {
        String key = sharedKey(path);
        if (key.isEmpty())
            return true;
        if (node.readSharedFile(root.unitId(), user.data.userId(), key) != null)
            return false;
        boolean[] found = { false };
        String prefix = key + "/";
        node.forEachSharedFile(root.unitId(), user.data.userId(),
                (filePath, size) -> {
                    if (filePath.startsWith(prefix))
                        found[0] = true;
                });
        return found[0];
    }

    private void forEachSharedDirectory(NodeUserHandle user, Node.RootEntry root, String path,
            Consumer<DirectoryEntry> consumer) {
        String prefix = sharedKey(path);
        String normalized = prefix.isEmpty() ? "" : prefix + "/";
        var names = new TreeMap<String, Boolean>();
        node.forEachSharedFile(root.unitId(), user.data.userId(), (filePath, size) -> {
            if (!filePath.startsWith(normalized))
                return;
            String rest = filePath.substring(normalized.length());
            int separator = rest.indexOf('/');
            if (separator < 0)
                names.merge(rest, false, (left, right) -> left || right);
            else
                names.merge(rest.substring(0, separator), true, (left, right) -> left || right);
        });
        names.forEach((name, directory) -> consumer.accept(new DirectoryEntry(name, directory)));
    }

    private record DirectoryEntry(String name, boolean directory) {
    }

    @Override
    public int getattr(String path, FileStat stat) {
        return execute(() -> {
            UserPath target = resolve(path);
            boolean directory;
            long size;
            if (target.user() == null || target.root() == null) {
                directory = true;
                size = 0;
            } else if (target.root().home()) {
                if (!target.user().exists(target.path()))
                    throw new FsError(ErrorCodes.ENOENT());
                directory = target.user().isDirectory(target.path());
                size = directory ? 0 : target.user().fileSize(target.path());
            } else if (sharedKey(target.path()).isEmpty()) {
                directory = true;
                size = 0;
            } else {
                long fileSize = node.sharedFileSize(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()));
                if (fileSize >= 0) {
                    directory = false;
                    size = fileSize;
                } else if (sharedDirectoryExists(target.user(), target.root(), target.path())) {
                    directory = true;
                    size = 0;
                } else {
                    throw new FsError(ErrorCodes.ENOENT());
                }
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
            long[] index = { 0 };
            boolean[] stopped = { false };
            Consumer<String> emit = name -> {
                long current = index[0]++;
                if (stopped[0] || current < offset)
                    return;
                if (filler.apply(buffer, name, null, current + 1) != 0)
                    stopped[0] = true;
            };
            emit.accept(".");
            emit.accept("..");
            if (target.user() == null) {
                node.listUsers().forEach(user -> emit.accept(user.localName()));
            } else if (target.root() == null) {
                node.listRootEntries(target.user().data.userId()).forEach(entry -> emit.accept(entry.label()));
            } else if (target.root().home()) {
                if (!target.user().isDirectory(target.path()))
                    throw new FsError(ErrorCodes.ENOTDIR());
                target.user().forEachDirectory(target.path(), entry -> emit.accept(entry.name()));
            } else {
                if (!sharedKey(target.path()).isEmpty()
                        && !sharedDirectoryExists(target.user(), target.root(), target.path()))
                    throw new FsError(ErrorCodes.ENOTDIR());
                forEachSharedDirectory(target.user(), target.root(), target.path(),
                        entry -> emit.accept(entry.name()));
            }
            return 0;
        });
    }

    private int createEntry(String path, boolean directory) {
        return execute(() -> {
            Destination target = destination(path);
            String fullPath = childPath(target.parent(), target.name());
            if (target.root().home()) {
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
            } else {
                boolean file = sharedFileExists(target.user(), target.root(), fullPath);
                boolean existingDirectory = sharedDirectoryExists(target.user(), target.root(), fullPath);
                if (directory) {
                    if (file || existingDirectory)
                        throw new FsError(ErrorCodes.EEXIST());
                } else {
                    if (existingDirectory && !file)
                        throw new FsError(ErrorCodes.EISDIR());
                    if (!file)
                        node.putSharedFile(target.root().unitId(), target.user().data.userId(),
                                sharedKey(fullPath), new byte[0]);
                }
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
            if (target.user() == null || target.root() == null
                    || !exist(target) || isDirectory(target.user(), target.root(), target.path()))
                throw new FsError(ErrorCodes.ENOENT());
            return 0;
        });
    }

    @Override
    public int opendir(String path, FuseFileInfo info) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null)
                return 0;
            if (!isDirectory(target.user(), target.root(), target.path()))
                throw new FsError(ErrorCodes.ENOTDIR());
            return 0;
        });
    }

    private boolean exist(UserPath target) {
        if (target.user() == null || target.root() == null)
            return true;
        if (target.root().home())
            return target.user().exists(target.path());
        return sharedFileExists(target.user(), target.root(), target.path())
                || sharedDirectoryExists(target.user(), target.root(), target.path());
    }

    @Override
    public int read(String path, Pointer buffer, long size, long offset, FuseFileInfo info) {
        return execute(() -> {
            if (size < 0 || size > Integer.MAX_VALUE || offset < 0)
                throw new FsError(ErrorCodes.EINVAL());
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null
                    || isDirectory(target.user(), target.root(), target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            byte[] content = target.root().home()
                    ? target.user().readFile(target.path())
                    : node.readSharedFile(target.root().unitId(), target.user().data.userId(),
                            sharedKey(target.path()));
            if (content == null)
                throw new FsError(ErrorCodes.ENOENT());
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
            if (size < 0 || offset < 0)
                throw new FsError(ErrorCodes.EINVAL());
            if (size > Integer.MAX_VALUE || offset > Integer.MAX_VALUE - size)
                throw new FsError(ErrorCodes.EFBIG());
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null
                    || isDirectory(target.user(), target.root(), target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            byte[] bytes = new byte[(int) size];
            buffer.get(0, bytes, 0, bytes.length);
            if (target.root().home()) {
                target.user().writeFile(target.path(), offset, bytes);
            } else {
                byte[] current = node.readSharedFile(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()));
                if (current == null)
                    throw new FsError(ErrorCodes.ENOENT());
                int end = (int) (offset + bytes.length);
                byte[] updated = current.length >= end ? current.clone() : java.util.Arrays.copyOf(current, end);
                System.arraycopy(bytes, 0, updated, (int) offset, bytes.length);
                node.putSharedFile(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()), updated);
            }
            return bytes.length;
        });
    }

    @Override
    public int truncate(String path, long size) {
        return execute(() -> {
            if (size < 0 || size > Integer.MAX_VALUE)
                throw new FsError(ErrorCodes.EINVAL());
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null
                    || isDirectory(target.user(), target.root(), target.path()))
                throw new FsError(ErrorCodes.EISDIR());
            if (target.root().home()) {
                target.user().truncateFile(target.path(), size);
            } else {
                byte[] current = node.readSharedFile(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()));
                if (current == null)
                    throw new FsError(ErrorCodes.ENOENT());
                byte[] updated = current.length == size ? current.clone() : java.util.Arrays.copyOf(current, (int) size);
                node.putSharedFile(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()), updated);
            }
            return 0;
        });
    }

    @Override
    public int unlink(String path) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null || target.path().equals("/"))
                throw new FsError(ErrorCodes.EBUSY());
            if (target.root().home()) {
                if (!target.user().exists(target.path()))
                    throw new FsError(ErrorCodes.ENOENT());
                if (target.user().isDirectory(target.path()))
                    throw new FsError(ErrorCodes.EISDIR());
                target.user().delete(target.path());
            } else {
                if (!sharedFileExists(target.user(), target.root(), target.path()))
                    throw new FsError(sharedDirectoryExists(target.user(), target.root(), target.path())
                            ? ErrorCodes.EISDIR()
                            : ErrorCodes.ENOENT());
                node.deleteSharedFile(target.root().unitId(), target.user().data.userId(),
                        sharedKey(target.path()));
            }
            return 0;
        });
    }

    @Override
    public int rmdir(String path) {
        return execute(() -> {
            UserPath target = resolve(path);
            if (target.user() == null || target.root() == null || target.path().equals("/"))
                throw new FsError(ErrorCodes.EBUSY());
            if (target.root().home()) {
                if (!target.user().exists(target.path()))
                    throw new FsError(ErrorCodes.ENOENT());
                if (!target.user().isDirectory(target.path()))
                    throw new FsError(ErrorCodes.ENOTDIR());
                if (!target.user().listDirectory(target.path()).isEmpty())
                    throw new FsError(ErrorCodes.ENOTEMPTY());
                target.user().delete(target.path());
            } else {
                if (!sharedDirectoryExists(target.user(), target.root(), target.path()))
                    throw new FsError(sharedFileExists(target.user(), target.root(), target.path())
                            ? ErrorCodes.ENOTDIR()
                            : ErrorCodes.ENOENT());
                if (sharedDirectoryHasFiles(target.user(), target.root(), target.path()))
                    throw new FsError(ErrorCodes.ENOTEMPTY());
            }
            return 0;
        });
    }

    private boolean sharedDirectoryHasFiles(NodeUserHandle user, Node.RootEntry root, String path) {
        String prefix = sharedKey(path);
        String normalized = prefix.isEmpty() ? "" : prefix + "/";
        boolean[] found = { false };
        node.forEachSharedFile(root.unitId(), user.data.userId(),
                (filePath, size) -> {
                    if (filePath.startsWith(normalized))
                        found[0] = true;
                });
        return found[0];
    }

    @Override
    public int rename(String path, String newPath) {
        return execute(() -> {
            UserPath source = resolve(path);
            Destination target = destination(newPath);
            if (source.user() == null || source.root() == null || source.path().equals("/"))
                throw new FsError(ErrorCodes.EXDEV());
            if (!source.user().data.userId().equals(target.user().data.userId()))
                throw new FsError(ErrorCodes.EXDEV());
            boolean sourceHome = source.root().home();
            boolean targetHome = target.root().home();
            if (sourceHome != targetHome
                    || (!sourceHome && !source.root().unitId().equals(target.root().unitId())))
                throw new FsError(ErrorCodes.EXDEV());
            String targetPath = childPath(target.parent(), target.name());
            if (source.path().equals(targetPath))
                return 0;
            if (sourceHome) {
                if (!source.user().exists(source.path()))
                    throw new FsError(ErrorCodes.ENOENT());
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
            } else {
                if (!exist(source))
                    throw new FsError(ErrorCodes.ENOENT());
                if (exist(target.user(), target.root(), targetPath)) {
                    boolean sourceDirectory = sharedDirectoryExists(source.user(), source.root(), source.path());
                    boolean targetDirectory = sharedDirectoryExists(target.user(), target.root(), targetPath);
                    if (sourceDirectory && !targetDirectory)
                        throw new FsError(ErrorCodes.ENOTDIR());
                    if (!sourceDirectory && targetDirectory)
                        throw new FsError(ErrorCodes.EISDIR());
                    if (targetDirectory && sharedDirectoryHasFiles(target.user(), target.root(), targetPath))
                        throw new FsError(ErrorCodes.ENOTEMPTY());
                    deleteSharedTree(target.user(), target.root(), targetPath);
                }
                moveSharedTree(source.user(), source.root(), source.path(), targetPath);
            }
            return 0;
        });
    }

    private boolean exist(NodeUserHandle user, Node.RootEntry root, String path) {
        return exist(new UserPath(user, root, path));
    }

    private void deleteSharedTree(NodeUserHandle user, Node.RootEntry root, String path) {
        if (sharedFileExists(user, root, path)) {
            node.deleteSharedFile(root.unitId(), user.data.userId(), sharedKey(path));
            return;
        }
        if (!sharedDirectoryExists(user, root, path))
            return;
        var files = new ArrayList<String>();
        String prefix = sharedKey(path).isEmpty() ? "" : sharedKey(path) + "/";
        node.forEachSharedFile(root.unitId(), user.data.userId(), (filePath, size) -> {
            if (filePath.startsWith(prefix))
                files.add(filePath);
        });
        files.forEach(file -> node.deleteSharedFile(root.unitId(), user.data.userId(), file));
    }

    private void moveSharedTree(NodeUserHandle user, Node.RootEntry root, String source, String target) {
        if (sharedFileExists(user, root, source)) {
            byte[] content = node.readSharedFile(root.unitId(), user.data.userId(), sharedKey(source));
            node.putSharedFile(root.unitId(), user.data.userId(), sharedKey(target), content);
            node.deleteSharedFile(root.unitId(), user.data.userId(), sharedKey(source));
            return;
        }
        var files = new ArrayList<String>();
        String prefix = sharedKey(source) + "/";
        String targetPrefix = sharedKey(target);
        node.forEachSharedFile(root.unitId(), user.data.userId(), (filePath, size) -> {
            if (filePath.startsWith(prefix))
                files.add(filePath);
        });
        for (String file : files) {
            byte[] content = node.readSharedFile(root.unitId(), user.data.userId(), file);
            String destination = targetPrefix + "/" + file.substring(prefix.length());
            node.putSharedFile(root.unitId(), user.data.userId(), destination, content);
        }
        for (String file : files)
            node.deleteSharedFile(root.unitId(), user.data.userId(), file);
    }
}
