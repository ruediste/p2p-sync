package com.github.ruediste.p2psync.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeFuseFS;
import com.github.ruediste.p2psync.ui.driver.SwingDriveServer;

/** Starts the single-window desktop application, its FUSE mount, and SwingDrive. */
public final class DesktopMain {
    private static final int DEFAULT_DRIVE_PORT = 9222;

    private DesktopMain() {
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            printUsage();
            System.exit(2);
            return;
        }
        if (options == null)
            return;

        Path dataFile = options.dataFile();
        Path mountDirectory = options.mountDirectory();
        if (options.mount()) {
            if (dataFile.startsWith(mountDirectory) || mountDirectory.startsWith(dataFile)) {
                System.err.println("The mount directory must not overlap the node database file");
                System.exit(2);
                return;
            }
            try {
                Files.createDirectories(mountDirectory);
            } catch (Exception error) {
                System.err.println("Could not create mount directory: " + error.getMessage());
                System.exit(1);
                return;
            }
        }

        Node node;
        try {
            node = new Node(new Node.Network(), dataFile);
        } catch (Exception error) {
            System.err.println("Could not open node storage: " + error.getMessage());
            System.exit(1);
            return;
        }

        try {
            var addresses = node.startRemote("/ip4/0.0.0.0/tcp/0", true);
            System.out.println("Peer ID: " + node.peerId.toBase58());
            addresses.forEach(address -> System.out.println("Listen address: " + address));
        } catch (RuntimeException error) {
            try {
                node.close();
            } catch (RuntimeException closeError) {
                error.addSuppressed(closeError);
            }
            System.err.println("Could not start peer networking: " + error.getMessage());
            System.exit(1);
            return;
        }

        NodeFuseFS filesystem = new NodeFuseFS(node);
        AtomicReference<SwingDriveServer> drive = new AtomicReference<>();
        AtomicBoolean stopping = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!stopping.compareAndSet(false, true))
                return;
            SwingDriveServer server = drive.getAndSet(null);
            if (server != null) {
                try {
                    server.close();
                } catch (RuntimeException error) {
                    System.err.println("Could not stop SwingDrive: " + error.getMessage());
                }
            }
            if (options.mount()) {
                try {
                    filesystem.umount();
                } catch (RuntimeException error) {
                    System.err.println("Could not unmount the user filesystem: " + error.getMessage());
                }
            }
            try {
                node.close();
            } catch (RuntimeException error) {
                System.err.println("Could not close the node: " + error.getMessage());
            }
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

        if (options.drivePort() != null) {
            try {
                SwingDriveServer server = SwingDriveServer.start(options.drivePort(), app::frame, filesystem, node);
                drive.set(server);
                System.out.println("SwingDrive: http://127.0.0.1:" + server.port() + "/");
            } catch (IOException error) {
                System.err.println("Could not start SwingDrive on port " + options.drivePort() + ": "
                        + error.getMessage());
                cleanup.run();
                System.exit(1);
                return;
            }
        }

        app.showWindow();

        if (options.mount()) {
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

    private record Options(Path dataFile, Path mountDirectory, boolean mount, Integer drivePort) {
    }

    private static Options parse(String[] args) {
        if (args.length == 0)
            throw new IllegalArgumentException("Missing node database file");
        List<String> positional = new ArrayList<>();
        boolean mount = true;
        Integer drivePort = null;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("-h") || argument.equals("--help")) {
                printUsage();
                return null;
            }
            if (argument.equals("--no-mount") || argument.equals("--no-fuse")) {
                mount = false;
            } else if (argument.equals("--drive") || argument.equals("--drive-port")) {
                if (index + 1 < args.length && isPort(args[index + 1])) {
                    drivePort = parsePort(args[++index]);
                } else if (argument.equals("--drive")) {
                    drivePort = DEFAULT_DRIVE_PORT;
                } else {
                    throw new IllegalArgumentException("--drive-port requires a port number");
                }
            } else if (argument.startsWith("--drive=") || argument.startsWith("--drive-port=")) {
                drivePort = parsePort(argument.substring(argument.indexOf('=') + 1));
            } else if (argument.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option " + argument);
            } else {
                positional.add(argument);
            }
        }
        if (positional.isEmpty())
            throw new IllegalArgumentException("Missing node database file");
        if (positional.size() > 2)
            throw new IllegalArgumentException("Too many positional arguments");
        Path dataFile = Path.of(positional.get(0)).toAbsolutePath().normalize();
        Path mountDirectory = positional.size() == 2
                ? Path.of(positional.get(1)).toAbsolutePath().normalize()
                : dataFile.resolveSibling(dataFile.getFileName() + "-mount");
        return new Options(dataFile, mountDirectory, mount, drivePort);
    }

    private static boolean isPort(String value) {
        try {
            parsePort(value);
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65535)
                throw new NumberFormatException();
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid port: " + value);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage: p2p-sync <data-file> [mount-directory] [options]

                Development server options:
                  --drive[=PORT]       Start the SwingDrive UI/web bridge (default 9222)
                  --drive-port PORT    Start the SwingDrive UI/web bridge on PORT (0 = random)
                  --no-mount           Do not mount the FUSE filesystem
                  -h, --help           Show this help

                The first positional argument is the node database file (created if
                absent). The optional second argument is the FUSE mount directory;
                when omitted a sibling directory named <data-file>-mount is used.
                """);
    }
}
