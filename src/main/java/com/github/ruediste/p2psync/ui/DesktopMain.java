package com.github.ruediste.p2psync.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeFuseFS;

/** Starts the single-window desktop application and its FUSE mount. */
public final class DesktopMain {
    private DesktopMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: DesktopMain <node-db-file> [mount-directory]");
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
            System.err.println("Could not open node storage: " + error.getMessage());
            return;
        }

        try {
            var addresses = node.startRemote("/ip4/0.0.0.0/tcp/0", true);
            System.out.println("Peer ID: " + node.peerId.toBase58());
            addresses.forEach(address -> System.out.println("Listen address: " + address));
        } catch (RuntimeException error) {
            node.close();
            System.err.println("Could not start peer networking: " + error.getMessage());
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

        DesktopApp app = new DesktopApp(node, mountDirectory, () -> {
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
                SwingUtilities.invokeLater(() -> app.reportStatus(
                        "Could not mount the user filesystem: " + error.getMessage()));
            }
        }, "p2p-sync-fuse-mount");
        mount.setDaemon(false);
        mount.start();
    }
}
