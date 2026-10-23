package com.github.ruediste.p2psync.storage;

import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleFuseMain {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: SimpleFuseMain <backing-file> <mount-directory>");
            System.exit(2);
            return;
        }
        Path backingFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path mountDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(mountDirectory))
            throw new IllegalArgumentException("Mount directory must already exist: " + mountDirectory);
        if (backingFile.startsWith(mountDirectory))
            throw new IllegalArgumentException("Backing file must be outside the mount directory");
        try (var filesystem = new SimpleFuseFS(backingFile)) {
            try {
                filesystem.mount(mountDirectory, true, false, new String[] { "-o", "default_permissions" });
            } finally {
                filesystem.umount();
            }
        }
    }
}
