package com.github.ruediste.p2psync.storage;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a read-only {@link StorageCheck} on a SimpleFuseFS backing file and
 * prints the result and block statistics. Do not run on a mounted file.
 */
public class StorageCheckMain {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: StorageCheckMain <backing-file>");
            System.exit(2);
            return;
        }
        Path backingFile = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(backingFile)) {
            System.err.println("Not a file: " + backingFile);
            System.exit(2);
            return;
        }
        var storage = new DataStorage();
        storage.getRootRefHandle(SimpleFuseFS.TREE_ROOT_HANDLE_ID, DataStorage.Area.META, FileTree.BLOCK_TYPE);
        var report = StorageCheck.check(storage, backingFile.toString());
        report.print(System.out);
        System.exit(report.isOk() ? 0 : 1);
    }
}
