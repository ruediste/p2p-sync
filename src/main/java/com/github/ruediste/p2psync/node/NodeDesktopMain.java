package com.github.ruediste.p2psync.node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class NodeDesktopMain {
    private NodeDesktopMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: NodeDesktopMain <node-db-file> [mount-directory]");
            System.exit(2);
            return;
        }

        Path dataFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path mountDirectory = args.length == 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
            : dataFile.resolveSibling(dataFile.getFileName() + "-mount");
        if (dataFile.startsWith(mountDirectory) || mountDirectory.startsWith(dataFile)) {
            System.err.println("The mount directory must not overlap the node database file");
            System.exit(2);
            return;
        }

        Node node;
        try {
            Files.createDirectories(mountDirectory);
            node = new Node(new Node.Network(), dataFile);
        } catch (Exception error) {
            showError("Could not open node storage", error);
            return;
        }

        try {
            var addresses = node.startRemote("/ip4/0.0.0.0/tcp/0", true);
            System.out.println("Peer ID: " + node.peerId.toBase58());
            addresses.forEach(address -> System.out.println("Listen address: " + address));
        } catch (RuntimeException error) {
            node.close();
            showError("Could not start peer networking", error);
            return;
        }

        NodeFuseFS filesystem = new NodeFuseFS(node);
        AtomicBoolean stopping = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!stopping.compareAndSet(false, true))
                return;
            try {
                filesystem.umount();
            } catch (RuntimeException ignored) {
            }
            node.close();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(cleanup, "p2p-sync-shutdown-hook"));

        NodeDesktopApp app = new NodeDesktopApp(node, mountDirectory, () -> {
            Thread shutdown = new Thread(() -> {
                try {
                    cleanup.run();
                } finally {
                    System.exit(0);
                }
            }, "p2p-sync-shutdown");
            shutdown.setDaemon(false);
            shutdown.start();
        });
        app.showWindow();

        Thread mount = new Thread(() -> {
            try {
                filesystem.mount(mountDirectory, true, false, new String[] { "-o", "default_permissions" });
            } catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> {
                    showError("Could not mount the user filesystem", error);
                    app.close();
                });
            }
        }, "p2p-sync-fuse-mount");
        mount.setDaemon(false);
        mount.start();
    }

    private static void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(null, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
