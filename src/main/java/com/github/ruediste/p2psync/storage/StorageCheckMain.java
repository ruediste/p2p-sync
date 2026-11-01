package com.github.ruediste.p2psync.storage;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.ruediste.p2psync.node.Node;

/**
 * Runs a read-only {@link StorageCheck} on a SimpleFuseFS backing file or a
 * node data file and prints the result and block statistics. Do not run on a
 * mounted file.
 */
public class StorageCheckMain {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: StorageCheckMain <backing-file>");
            System.exit(2);
            return;
        }
        System.exit(run(Path.of(args[0]), System.out, System.err));
    }

    /**
     * Checks the given file and returns the process exit code (0 = passed,
     * 1 = check failed, 2 = invalid arguments). Exposed for tests.
     */
    static int run(Path backingFile, PrintStream out, PrintStream err) {
        backingFile = backingFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(backingFile)) {
            err.println("Not a file: " + backingFile);
            return 2;
        }
        var storage = new DataStorage();
        if (isNodeFile(backingFile)) {
            Node.registerStorageCheckHandles(storage);
        } else {
            storage.getRootRefHandle(SimpleFuseFS.TREE_ROOT_HANDLE_ID, DataStorage.Area.META,
                    FileTree.BLOCK_TYPE);
        }
        var report = StorageCheck.check(storage, backingFile.toString());
        report.print(out);
        return report.isOk() ? 0 : 1;
    }

    /**
     * Node data files register reference handle 2 (the unit-root registry) and
     * long handle 3 (the SyncUnit counter); SimpleFuseFS files only use handle 1.
     */
    private static boolean isNodeFile(Path backingFile) {
        var probe = new DataStorage();
        try {
            probe.openReadOnly(backingFile.toString());
            return probe.rootBlock.refHandles.handleIds.contains(2)
                    || probe.rootBlock.longHandleIds.values.contains(3);
        } catch (RuntimeException e) {
            return false;
        } finally {
            probe.close();
        }
    }
}
