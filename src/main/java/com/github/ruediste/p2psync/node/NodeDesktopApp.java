package com.github.ruediste.p2psync.node;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/** Desktop management window and system-tray status for one local node. */
public final class NodeDesktopApp implements AutoCloseable {
    private static final Color INK = new Color(28, 38, 42);
    private static final Color PAPER = new Color(244, 247, 244);
    private static final Color GREEN = new Color(36, 124, 91);
    private static final Color MUTED = new Color(102, 117, 119);
    private static final Color LINE = new Color(214, 223, 219);

    private final Node node;
    private final Path mountPoint;
    private final Runnable shutdown;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final UserTableModel tableModel = new UserTableModel();
    private JFrame frame;
    private JTable table;
    private JLabel nodeLabel;
    private JLabel summaryLabel;
    private TrayIcon trayIcon;
    private Timer refreshTimer;
    private JButton syncButton;

    public NodeDesktopApp(Node node, Path mountPoint, Runnable shutdown) {
        this.node = java.util.Objects.requireNonNull(node);
        this.mountPoint = java.util.Objects.requireNonNull(mountPoint);
        this.shutdown = java.util.Objects.requireNonNull(shutdown);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(this::createWindow);
    }

    private void createWindow() {
        if (closed.get() || frame != null)
            return;

        frame = new JFrame("p2p-sync");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                close();
            }
        });
        frame.setMinimumSize(new Dimension(760, 500));
        frame.setSize(900, 600);
        frame.setLocationByPlatform(true);
        frame.setContentPane(buildContent());
        addTrayIcon();
        refresh();
        refreshTimer = new Timer(4000, event -> refresh());
        refreshTimer.start();
        frame.setVisible(true);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAPER);

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBackground(INK);
        header.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        JLabel title = new JLabel("Node console");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SERIF, Font.BOLD, 28));
        nodeLabel = new JLabel();
        nodeLabel.setForeground(new Color(190, 211, 200));
        nodeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        header.add(title, BorderLayout.NORTH);
        header.add(nodeLabel, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(18, 18));
        center.setBackground(PAPER);
        center.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        JPanel sectionHeader = new JPanel(new BorderLayout(8, 8));
        sectionHeader.setBackground(PAPER);
        JLabel usersTitle = new JLabel("Configured users");
        usersTitle.setForeground(INK);
        usersTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        summaryLabel = new JLabel();
        summaryLabel.setForeground(MUTED);
        summaryLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sectionHeader.add(usersTitle, BorderLayout.WEST);
        sectionHeader.add(summaryLabel, BorderLayout.EAST);
        center.add(sectionHeader, BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        table.setForeground(INK);
        table.setBackground(Color.WHITE);
        table.setGridColor(LINE);
        table.setSelectionBackground(new Color(216, 236, 226));
        table.setSelectionForeground(INK);
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(230, 237, 232));
        table.getTableHeader().setForeground(INK);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        center.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 5, 7, 7));
        actions.setBackground(PAPER);
        JButton add = button("Add user");
        add.addActionListener(event -> addUser());
        JButton addClipboard = button("Add from clipboard");
        addClipboard.addActionListener(event -> addFromClipboard());
        JButton join = button("Join remote");
        join.addActionListener(event -> joinRemote());
        JButton rename = button("Rename");
        rename.addActionListener(event -> renameUser());
        JButton export = button("Export key");
        export.addActionListener(event -> exportKey());
        JButton copy = button("Copy key");
        copy.addActionListener(event -> copyKeyToClipboard());
        JButton open = button("Open folder");
        open.addActionListener(event -> openFolder());
        JButton refresh = button("Refresh");
        refresh.addActionListener(event -> refresh());
        syncButton = button("Sync now");
        syncButton.addActionListener(event -> synchronize());
        actions.add(add);
        actions.add(addClipboard);
        actions.add(join);
        actions.add(rename);
        actions.add(export);
        actions.add(copy);
        actions.add(open);
        actions.add(syncButton);
        actions.add(refresh);
        center.add(actions, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JLabel footer = new JLabel("User files are available at " + mountPoint);
        footer.setForeground(MUTED);
        footer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 26, 18, 26));
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JButton button(String label) {
        JButton button = new JButton(label);
        button.setForeground(Color.WHITE);
        button.setBackground(GREEN);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return button;
    }

    private void addUser() {
        String name = JOptionPane.showInputDialog(frame, "Local name", "Add user", JOptionPane.PLAIN_MESSAGE);
        if (name == null)
            return;
        try {
            node.createNewUser(name);
            refresh();
        } catch (IllegalArgumentException error) {
            showError(error);
        }
    }

    private void joinRemote() {
        String peerText = JOptionPane.showInputDialog(frame,
                "Remote peer ID (base58, optional when discovered)", "Join remote user", JOptionPane.PLAIN_MESSAGE);
        if (peerText == null)
            return;
        String addressText = JOptionPane.showInputDialog(frame, "Remote multiaddress (optional when discovered)", "");
        if (addressText == null)
            return;
        String localName = JOptionPane.showInputDialog(frame, "Local name for this user", "joined");
        if (localName == null)
            return;
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Select the user's protected credential file");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION)
            return;
        var userKey = importUserCredential(chooser.getSelectedFile().toPath());
        if (userKey == null)
            return;
        try {
            joinRemoteUser(userKey, peerText, addressText, localName);
        } catch (Exception error) {
            showError(error);
        }
    }

    private void addFromClipboard() {
        String credential;
        try {
            credential = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception error) {
            showError(new IllegalArgumentException("Clipboard does not contain a text credential", error));
            return;
        }
        var userKey = importClipboardCredential(credential);
        if (userKey == null)
            return;
        try {
            promptForRemoteJoin(userKey);
        } catch (Exception error) {
            showError(error);
        }
    }

    private void copyKeyToClipboard() {
        Node.UserStatus selected = selectedUserForAction("Copy key");
        if (selected == null)
            return;
        char[] password = requestPassword("Credential passphrase (leave empty for none)");
        if (password == null)
            return;
        try {
            String credential = node.exportUserKeyToClipboard(selected.userId(), password);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(credential), null);
            JOptionPane.showMessageDialog(frame,
                    "Encrypted credential copied. Share its passphrase separately; it may be empty.",
                    "p2p-sync", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
            showError(error);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void promptForRemoteJoin(com.github.ruediste.p2psync.libp2p.crypto.PrivKey userKey) {
        String peerText = JOptionPane.showInputDialog(frame,
                "Remote peer ID (base58, optional when discovered)", "Join existing user", JOptionPane.PLAIN_MESSAGE);
        if (peerText == null)
            return;
        String addressText = JOptionPane.showInputDialog(frame,
                "Remote multiaddress (optional when discovered by mDNS)", "");
        if (addressText == null)
            return;
        String localName = JOptionPane.showInputDialog(frame, "Local name for this user", "joined");
        if (localName == null)
            return;
        try {
            joinRemoteUser(userKey, peerText, addressText, localName);
        } catch (Exception error) {
            showError(error);
        }
    }

    private void joinRemoteUser(com.github.ruediste.p2psync.libp2p.crypto.PrivKey userKey,
            String peerText, String addressText, String localName) {
        if (peerText.isBlank()) {
            if (!addressText.isBlank())
                throw new IllegalArgumentException("Enter a peer ID when specifying a multiaddress");
            node.joinRemote(userKey, localName);
        } else {
            var peerId = com.github.ruediste.p2psync.libp2p.core.PeerId.fromBase58(peerText);
            if (addressText.isBlank())
                node.joinRemote(userKey, localName, peerId);
            else
                node.joinRemote(userKey, localName, peerId,
                        new com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr(addressText));
        }
        refresh();
    }

    private void exportKey() {
        Node.UserStatus selected = selectedUser();
        if (selected == null)
            return;
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Save protected user credential");
        chooser.setSelectedFile(new java.io.File(selected.localName() + ".userkey"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION)
            return;
        char[] password = requestPassword("Credential passphrase (leave empty for none)");
        if (password == null)
            return;
        try {
            node.exportUserKey(selected.userId(), chooser.getSelectedFile().toPath(), password);
            JOptionPane.showMessageDialog(frame, "Protected credential exported.", "p2p-sync",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
            showError(error);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private char[] requestPassword(String title) {
        JPasswordField field = new JPasswordField(24);
        int result = JOptionPane.showConfirmDialog(frame, field, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION ? field.getPassword() : null;
    }

    private com.github.ruediste.p2psync.libp2p.crypto.PrivKey importUserCredential(Path path) {
        try {
            return Node.importUserKey(path, new char[0]);
        } catch (IllegalArgumentException emptyPassphraseFailed) {
            char[] password = requestPassword("Credential passphrase (leave empty if none)");
            if (password == null)
                return null;
            try {
                return Node.importUserKey(path, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    private com.github.ruediste.p2psync.libp2p.crypto.PrivKey importClipboardCredential(String clipboardText) {
        try {
            return Node.importUserKeyFromClipboard(clipboardText, new char[0]);
        } catch (IllegalArgumentException emptyPassphraseFailed) {
            char[] password = requestPassword("Credential passphrase (leave empty if none)");
            if (password == null)
                return null;
            try {
                return Node.importUserKeyFromClipboard(clipboardText, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    private void renameUser() {
        Node.UserStatus selected = selectedUser();
        if (selected == null)
            return;
        String name = JOptionPane.showInputDialog(frame, "New local name", selected.localName());
        if (name == null)
            return;
        try {
            node.renameUser(selected.userId(), name);
            refresh();
        } catch (IllegalArgumentException error) {
            showError(error);
        }
    }

    private void openFolder() {
        Node.UserStatus selected = selectedUser();
        if (selected == null)
            return;
        try {
            Desktop.getDesktop().open(mountPoint.resolve(selected.localName()).toFile());
        } catch (Exception error) {
            showError(new IllegalStateException("Could not open the user's mounted folder", error));
        }
    }

    private void synchronize() {
        syncButton.setEnabled(false);
        Thread sync = new Thread(() -> {
            try {
                node.syncKnownPeers();
                SwingUtilities.invokeLater(this::refresh);
            } catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> showError(error));
            } finally {
                SwingUtilities.invokeLater(() -> syncButton.setEnabled(true));
            }
        }, "p2p-sync-manual-sync");
        sync.setDaemon(true);
        sync.start();
    }

    private Node.UserStatus selectedUser() {
        int row = table.getSelectedRow();
        if (row < 0)
            return null;
        return tableModel.users.get(table.convertRowIndexToModel(row));
    }

    private Node.UserStatus selectedUserForAction(String action) {
        Node.UserStatus selected = selectedUser();
        if (selected != null)
            return selected;
        if (tableModel.users.size() == 1)
            return tableModel.users.get(0);
        String message = tableModel.users.isEmpty()
                ? "Add or join a user before copying a key."
                : "Select a user row before copying a key.";
        JOptionPane.showMessageDialog(frame, message, action, JOptionPane.INFORMATION_MESSAGE);
        return null;
    }

    private void refresh() {
        List<Node.UserStatus> users = node.listUsers();
        tableModel.setUsers(users);
        if (nodeLabel != null)
            nodeLabel.setText("Peer " + node.peerId.toBase58());
        int pending = users.stream().mapToInt(user -> user.pendingLocalOperationCount()
                + user.pendingIncomingOperationCount()).sum();
        if (summaryLabel != null)
            summaryLabel.setText(users.size() + " users  /  " + pending + " pending operations");
        updateTray(users, pending);
    }

    private void addTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray unavailable: the desktop does not expose an AWT-compatible tray. "
                    + "On XFCE, enable a panel tray with legacy X11 icon support. "
                    + "The dashboard remains available.");
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open dashboard");
        open.addActionListener(event -> frame.setVisible(true));
        menu.add(open);
        menu.addSeparator();
        MenuItem quit = new MenuItem("Quit p2p-sync");
        quit.addActionListener(event -> close());
        menu.add(quit);
        trayIcon = new TrayIcon(createTrayImage(), "p2p-sync", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> frame.setVisible(true));
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (java.awt.AWTException error) {
            trayIcon = null;
            System.err.println("Could not add the p2p-sync system tray icon: " + error.getMessage());
        }
    }

    private void updateTray(List<Node.UserStatus> users, int pending) {
        if (trayIcon == null)
            return;
        trayIcon.setToolTip("p2p-sync: " + users.size() + " users, " + pending + " pending");
        PopupMenu menu = trayIcon.getPopupMenu();
        while (menu.getItemCount() > 2)
            menu.remove(1);
        for (Node.UserStatus user : users) {
            MenuItem status = new MenuItem(user.localName() + "  -  "
                    + (user.pendingIncomingOperationCount() + user.pendingLocalOperationCount()) + " pending");
            status.addActionListener(event -> {
                frame.setVisible(true);
                table.setRowSelectionInterval(findUserRow(user.localName()), findUserRow(user.localName()));
            });
            menu.insert(status, menu.getItemCount() - 1);
        }
    }

    private int findUserRow(String name) {
        for (int index = 0; index < tableModel.users.size(); index++)
            if (tableModel.users.get(index).localName().equals(name))
                return index;
        return 0;
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(INK);
        graphics.fillRoundRect(1, 1, 30, 30, 8, 8);
        graphics.setColor(new Color(111, 201, 151));
        graphics.fillOval(8, 8, 16, 16);
        graphics.setColor(INK);
        graphics.fillRect(15, 8, 3, 16);
        graphics.dispose();
        return image;
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(frame, error.getMessage(), "p2p-sync", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true))
            return;
        if (refreshTimer != null)
            refreshTimer.stop();
        if (trayIcon != null)
            SystemTray.getSystemTray().remove(trayIcon);
        if (frame != null)
            frame.dispose();
        shutdown.run();
    }

    private static final class UserTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Name", "Active nodes", "Local pending", "Incoming pending", "ID" };
        private List<Node.UserStatus> users = List.of();

        void setUsers(List<Node.UserStatus> users) {
            this.users = List.copyOf(users);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return users.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Node.UserStatus user = users.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> user.localName();
                case 1 -> user.activeNodeCount();
                case 2 -> user.pendingLocalOperationCount();
                case 3 -> user.pendingIncomingOperationCount();
                case 4 -> HexFormat.of().formatHex(user.userId().bytes(), 0, 6);
                default -> throw new IndexOutOfBoundsException(columnIndex);
            };
        }
    }
}
