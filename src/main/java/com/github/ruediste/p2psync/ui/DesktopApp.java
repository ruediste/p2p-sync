package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.core.multiaddr.Multiaddr;
import com.github.ruediste.p2psync.libp2p.crypto.PrivKey;
import com.github.ruediste.p2psync.node.Node;

/**
 * Single-window desktop application. Users and workspace sections are selected
 * in the left navigation; workflows are rendered inline in the content area.
 * File selection and discard confirmations still use standard dialogs.
 */
public final class DesktopApp implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DesktopApp.class);

    private static final String WELCOME = "welcome";
    private static final String ADD_USER = "add-user";
    private static final String JOIN_USER = "join-user";

    private final Node node;
    private final Path mountPoint;
    private final Runnable shutdown;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService syncWorker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "p2p-sync-background-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UserId, UserWorkspacePanel> workspaces = new ConcurrentHashMap<>();

    private final DefaultListModel<Node.UserStatus> userModel = new DefaultListModel<>();
    private final JList<Node.UserStatus> userList = new JList<>(userModel);
    private final DefaultListModel<UserWorkspacePanel.Section> sectionModel = new DefaultListModel<>();
    private final JList<UserWorkspacePanel.Section> sectionList = new JList<>(sectionModel);
    private final java.awt.CardLayout contentCards = new java.awt.CardLayout();
    private final JPanel contentPanel = new JPanel(contentCards);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel pendingLabel = Theme.muted("");
    private final JLabel peerLabel = Theme.mono("");
    private final JButton syncButton = Theme.primaryButton("Sync now");

    private volatile JFrame frame;
    private TrayIcon trayIcon;
    private MenuItem trayOpen;
    private MenuItem trayQuit;
    private Timer refreshTimer;
    private UserId selectedUserId;
    private boolean refreshing;

    private final JTextField addName = Theme.textField(22);
    private final JTextField joinCredentialPath = Theme.textField(24);
    private final JTextArea joinCredentialText = Theme.textArea(4, 30);
    private final JPasswordField joinPassphrase = Theme.passwordField(18);
    private final JTextField joinPeer = Theme.textField(24);
    private final JTextField joinAddress = Theme.textField(24);
    private final JTextField joinName = Theme.textField(18);

    public DesktopApp(Node node, Path mountPoint, Runnable shutdown) {
        this(node, mountPoint, shutdown, Duration.ofSeconds(4));
    }

    public DesktopApp(Node node, Path mountPoint, Runnable shutdown, Duration syncDelay) {
        this.node = java.util.Objects.requireNonNull(node);
        this.mountPoint = java.util.Objects.requireNonNull(mountPoint);
        this.shutdown = java.util.Objects.requireNonNull(shutdown);
        statusLabel.setName("status");
        pendingLabel.setName("pending");
        peerLabel.setName("peer");
        userList.setName("users");
        sectionList.setName("workspace-sections");
        syncButton.setName("sync-now");
        for (UserWorkspacePanel.Section section : UserWorkspacePanel.Section.values())
            sectionModel.addElement(section);
        syncWorker.scheduleWithFixedDelay(() -> runSync(false), 0, syncDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(this::createWindow);
    }

    /** The live application window, or {@code null} before it has been created. */
    public JFrame frame() {
        return frame;
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
        frame.setMinimumSize(new Dimension(1000, 640));
        frame.setSize(1180, 780);
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
        root.setBackground(Theme.PAPER);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMainArea(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(250, 0));
        sidebar.add(buildSidebarTop(), BorderLayout.NORTH);
        sidebar.add(buildSidebarSections(), BorderLayout.CENTER);
        sidebar.add(buildSidebarBottom(), BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildSidebarTop() {
        JPanel top = new JPanel();
        top.setBackground(Theme.SIDEBAR);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(22, 18, 8, 18));

        JLabel brand = new JLabel("p2p-sync");
        brand.setFont(new Font(Font.SERIF, Font.BOLD, 25));
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(brand);
        JLabel caption = new JLabel("Secure peer-to-peer workspace");
        caption.setFont(Theme.SMALL);
        caption.setForeground(Theme.MUTED_LIGHT);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(caption);
        top.add(Box.createVerticalStrut(22));

        JLabel users = new JLabel("USERS");
        users.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        users.setForeground(Theme.MUTED_LIGHT);
        users.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(users);
        top.add(Box.createVerticalStrut(6));

        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setCellRenderer(new UserRenderer());
        userList.setBackground(Theme.SIDEBAR);
        userList.setForeground(Color.WHITE);
        userList.setBorder(BorderFactory.createEmptyBorder());
        userList.setFixedCellHeight(54);
        userList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            Node.UserStatus selected = userList.getSelectedValue();
            if (selected != null)
                openWorkspace(selected.userId());
        });
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(BorderFactory.createEmptyBorder());
        userScroll.getViewport().setBackground(Theme.SIDEBAR);
        userScroll.setPreferredSize(new Dimension(214, 150));
        userScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        userScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(userScroll);
        top.add(Box.createVerticalStrut(8));

        JPanel userActions = new JPanel();
        userActions.setBackground(Theme.SIDEBAR);
        userActions.setLayout(new BoxLayout(userActions, BoxLayout.X_AXIS));
        userActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton add = darkButton("Add user");
        add.addActionListener(event -> showAddUser());
        JButton join = darkButton("Join user");
        join.addActionListener(event -> showJoinUser());
        userActions.add(add);
        userActions.add(Box.createHorizontalStrut(6));
        userActions.add(join);
        userActions.add(Box.createHorizontalGlue());
        top.add(userActions);
        return top;
    }

    private JPanel buildSidebarSections() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.SIDEBAR);
        panel.setBorder(new EmptyBorder(8, 18, 8, 18));

        JLabel label = new JLabel("WORKSPACE");
        label.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        label.setForeground(Theme.MUTED_LIGHT);
        panel.add(label, BorderLayout.NORTH);

        sectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sectionList.setCellRenderer(new SectionRenderer());
        sectionList.setBackground(Theme.SIDEBAR);
        sectionList.setBorder(BorderFactory.createEmptyBorder());
        sectionList.setFixedCellHeight(40);
        sectionList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            UserWorkspacePanel.Section section = sectionList.getSelectedValue();
            UserWorkspacePanel workspace = selectedUserId == null ? null : workspaces.get(selectedUserId);
            if (section != null && workspace != null)
                workspace.showSection(section);
        });
        JScrollPane scroll = new JScrollPane(sectionList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SIDEBAR);
        panel.add(scroll, BorderLayout.CENTER);
        sectionList.setEnabled(false);
        return panel;
    }

    private JPanel buildSidebarBottom() {
        JPanel bottom = new JPanel();
        bottom.setBackground(Theme.SIDEBAR);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(10, 18, 18, 18));

        peerLabel.setForeground(Theme.MUTED_LIGHT);
        peerLabel.setFont(Theme.SMALL);
        peerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(peerLabel);
        bottom.add(Box.createVerticalStrut(8));

        syncButton.addActionListener(event -> synchronize());
        syncButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bottom.add(syncButton);
        bottom.add(Box.createVerticalStrut(6));

        JButton quit = darkButton("Quit p2p-sync");
        quit.setAlignmentX(Component.LEFT_ALIGNMENT);
        quit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        quit.addActionListener(event -> close());
        bottom.add(quit);
        return bottom;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.PAPER);
        contentPanel.setBackground(Theme.PAPER);

        JPanel welcome = buildWelcome();
        contentPanel.add(welcome, WELCOME);
        contentPanel.add(buildAddUser(), ADD_USER);
        contentPanel.add(buildJoinUser(), JOIN_USER);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Theme.CARD);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(7, 18, 7, 18)));
        statusLabel.setFont(Theme.SMALL);
        statusLabel.setForeground(Theme.MUTED);
        statusBar.add(statusLabel, BorderLayout.WEST);
        pendingLabel.setHorizontalAlignment(JLabel.RIGHT);
        statusBar.add(pendingLabel, BorderLayout.EAST);

        main.add(contentPanel, BorderLayout.CENTER);
        main.add(statusBar, BorderLayout.SOUTH);
        return main;
    }

    private JPanel buildWelcome() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.PAPER);
        outer.setBorder(new EmptyBorder(40, 50, 40, 50));
        Theme.RoundedPanel card = new Theme.RoundedPanel(Theme.CARD, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(34, 40, 34, 40));

        JLabel title = Theme.title("One window, all your work");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));
        JLabel text = Theme.muted("<html><div style='width:520px'>Pick a user in the left navigation to open "
                + "chat, mail, shared folders and todo lists. Everything happens in this window.</div></html>");
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(text);
        card.add(Box.createVerticalStrut(22));
        JPanel actions = Theme.row(8);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton add = Theme.primaryButton("Create a user");
        add.addActionListener(event -> showAddUser());
        actions.add(add);
        JButton join = Theme.secondaryButton("Join an existing user");
        join.addActionListener(event -> showJoinUser());
        actions.add(join);
        card.add(actions);
        outer.add(card, BorderLayout.NORTH);
        return outer;
    }

    private JPanel buildAddUser() {
        JPanel outer = page("Add user");
        JPanel card = Theme.card();
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(560, 240));
        card.add(Theme.subheading("Create a new local identity"));
        card.add(Box.createVerticalStrut(4));
        card.add(Theme.muted("The user name is local to this node. A new key pair and User SyncUnit are created."));
        card.add(Box.createVerticalStrut(12));
        card.add(Theme.formField("Local name", addName));
        card.add(Box.createVerticalStrut(10));
        JPanel actions = Theme.row(8);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton create = Theme.primaryButton("Create user");
        create.addActionListener(event -> createUser());
        actions.add(create);
        JButton cancel = Theme.ghostButton("Cancel");
        cancel.addActionListener(event -> showWelcome());
        actions.add(cancel);
        card.add(actions);
        outer.add(card, BorderLayout.NORTH);
        return outer;
    }

    private JPanel buildJoinUser() {
        JPanel outer = page("Join user");
        JPanel card = Theme.card();
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(760, 700));
        card.add(Theme.subheading("Import a protected credential"));
        card.add(Box.createVerticalStrut(4));
        card.add(Theme.muted("Use a credential file or paste the clipboard credential. The passphrase may be empty. "
                + "Leave the peer ID blank to probe known peers, or specify a peer and optional listen address."));
        card.add(Box.createVerticalStrut(12));
        JPanel credentialPathRow = Theme.row(6);
        credentialPathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        credentialPathRow.add(joinCredentialPath);
        JButton browseCredential = Theme.secondaryButton("Browse...");
        browseCredential.addActionListener(event -> {
            Path selected = Theme.chooseOpenFile(joinCredentialPath, "Select protected credential");
            if (selected != null)
                joinCredentialPath.setText(selected.toString());
        });
        credentialPathRow.add(browseCredential);
        card.add(Theme.formField("Credential file path", credentialPathRow));
        card.add(Box.createVerticalStrut(8));
        card.add(Theme.formField("Or credential text", Theme.scroll(joinCredentialText)));
        card.add(Box.createVerticalStrut(8));
        card.add(Theme.formField("Passphrase", joinPassphrase));
        card.add(Box.createVerticalStrut(8));
        card.add(Theme.formField("Peer ID (optional)", joinPeer));
        card.add(Box.createVerticalStrut(8));
        card.add(Theme.formField("Listen multiaddress (optional)", joinAddress));
        card.add(Box.createVerticalStrut(8));
        card.add(Theme.formField("Local name", joinName));
        card.add(Box.createVerticalStrut(12));
        JPanel actions = Theme.row(8);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton join = Theme.primaryButton("Import and join");
        join.addActionListener(event -> joinUser());
        actions.add(join);
        JButton paste = Theme.secondaryButton("Paste credential");
        paste.addActionListener(event -> pasteCredential());
        actions.add(paste);
        JButton cancel = Theme.ghostButton("Cancel");
        cancel.addActionListener(event -> showWelcome());
        actions.add(cancel);
        card.add(actions);
        outer.add(card, BorderLayout.NORTH);
        return outer;
    }

    private JPanel page(String title) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.PAPER);
        outer.setBorder(new EmptyBorder(28, 36, 28, 36));
        JLabel heading = Theme.title(title);
        heading.setBorder(new EmptyBorder(0, 0, 18, 0));
        outer.add(heading, BorderLayout.NORTH);
        return outer;
    }

    private JButton darkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        button.setForeground(Color.WHITE);
        button.setBackground(Theme.SIDEBAR_ACTIVE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 10, 8, 10));
        return button;
    }

    private void showWelcome() {
        selectedUserId = null;
        userList.clearSelection();
        sectionList.clearSelection();
        sectionList.setEnabled(false);
        contentCards.show(contentPanel, WELCOME);
    }

    private void showAddUser() {
        selectedUserId = null;
        userList.clearSelection();
        sectionList.clearSelection();
        sectionList.setEnabled(false);
        addName.setText("");
        contentCards.show(contentPanel, ADD_USER);
        addName.requestFocusInWindow();
    }

    private void showJoinUser() {
        selectedUserId = null;
        userList.clearSelection();
        sectionList.clearSelection();
        sectionList.setEnabled(false);
        contentCards.show(contentPanel, JOIN_USER);
        joinCredentialPath.requestFocusInWindow();
    }

    private void createUser() {
        String name = addName.getText().trim();
        Background.run("create user", () -> node.createNewUser(name), handle -> {
            if (closed.get())
                return;
            refresh();
            openWorkspace(handle.data.userId());
            addName.setText("");
            status("User created");
        }, error -> status("Could not create user: " + error.getMessage()));
    }

    private void pasteCredential() {
        try {
            joinCredentialText.setText((String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor));
            status("Credential pasted from the clipboard");
        } catch (Exception error) {
            status("Clipboard does not contain text: " + error.getMessage());
        }
    }

    private void joinUser() {
        char[] passphrase = joinPassphrase.getPassword();
        String credentialPath = joinCredentialPath.getText().trim();
        String credentialText = joinCredentialText.getText().trim();
        String localName = joinName.getText().trim().isEmpty() ? "joined" : joinName.getText().trim();
        String peerText = joinPeer.getText().trim();
        String addressText = joinAddress.getText().trim();
        Runnable clearPassphrase = () -> {
            Arrays.fill(passphrase, '\0');
            joinPassphrase.setText("");
        };
        Background.run("join user", () -> {
            PrivKey userKey = importCredential(credentialPath, credentialText, passphrase);
            if (peerText.isEmpty()) {
                if (!addressText.isEmpty())
                    throw new IllegalArgumentException("Enter a peer ID when specifying a multiaddress");
                node.joinRemote(userKey, localName);
            } else {
                PeerId peer = PeerId.fromBase58(peerText);
                if (addressText.isEmpty())
                    node.joinRemote(userKey, localName, peer);
                else
                    node.joinRemote(userKey, localName, peer, new Multiaddr(addressText));
            }
            return localName;
        }, joinedName -> {
            clearPassphrase.run();
            if (closed.get())
                return;
            joinCredentialPath.setText("");
            joinCredentialText.setText("");
            joinPeer.setText("");
            joinAddress.setText("");
            joinName.setText("");
            refresh();
            for (int index = 0; index < userModel.size(); index++) {
                Node.UserStatus joined = userModel.get(index);
                if (joined.localName().equals(joinedName))
                    openWorkspace(joined.userId());
            }
            status("User joined");
        }, error -> {
            clearPassphrase.run();
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            if (message.contains("passphrase") || message.contains("decrypt"))
                status("The credential could not be unlocked. Check the passphrase.");
            else
                status("Could not join user: " + message);
        });
    }

    private PrivKey importCredential(String path, String text, char[] passphrase) {
        if (!path.isBlank())
            return Node.importUserKey(Path.of(path), passphrase);
        if (!text.isBlank())
            return Node.importUserKeyFromClipboard(text, passphrase);
        throw new IllegalArgumentException("Provide a credential file path or paste the credential text");
    }

    private void openWorkspace(UserId userId) {
        Node.UserStatus user = null;
        for (int index = 0; index < userModel.size(); index++)
            if (userModel.get(index).userId().equals(userId))
                user = userModel.get(index);
        if (user == null)
            return;
        selectedUserId = userId;
        refreshing = true;
        try {
            for (int index = 0; index < userModel.size(); index++)
                if (userModel.get(index).userId().equals(userId))
                    userList.setSelectedIndex(index);
            sectionList.setEnabled(true);
        } finally {
            refreshing = false;
        }
        Node.UserStatus selectedUser = user;
        UserWorkspacePanel workspace = workspaces.computeIfAbsent(userId,
                id -> new UserWorkspacePanel(node, selectedUser, mountPoint, this::status, this::refresh,
                        this::selectSection));
        String cardName = "user:" + HexFormat.of().formatHex(userId.bytes());
        if (!containsCard(cardName)) {
            workspace.setName(cardName);
            contentPanel.add(workspace, cardName);
        }
        contentCards.show(contentPanel, cardName);
        UserWorkspacePanel.Section section = sectionList.getSelectedValue();
        if (section == null) {
            section = UserWorkspacePanel.Section.OVERVIEW;
            refreshing = true;
            try {
                sectionList.setSelectedValue(section, true);
            } finally {
                refreshing = false;
            }
        }
        workspace.showSection(section);
    }

    private void selectSection(UserWorkspacePanel.Section section) {
        refreshing = true;
        try {
            sectionList.setSelectedValue(section, true);
        } finally {
            refreshing = false;
        }
    }

    private boolean containsCard(String name) {
        for (var component : contentPanel.getComponents())
            if (name.equals(component.getName()))
                return true;
        return false;
    }

    private void refresh() {
        if (closed.get())
            return;
        UserId keepSelection = selectedUserId;
        List<Node.UserStatus> users = node.listUsers();
        refreshing = true;
        try {
            userModel.clear();
            users.forEach(userModel::addElement);
            if (keepSelection != null) {
                for (int index = 0; index < userModel.size(); index++)
                    if (userModel.get(index).userId().equals(keepSelection))
                        userList.setSelectedIndex(index);
            }
        } finally {
            refreshing = false;
        }
        peerLabel.setText("Peer " + node.peerId.toBase58().substring(0, Math.min(14,
                node.peerId.toBase58().length())) + "…");
        int pending = users.stream().mapToInt(user -> user.pendingLocalOperationCount()
                + user.pendingIncomingOperationCount()).sum();
        pendingLabel.setText(users.size() + " users  /  " + pending + " pending");
        if (keepSelection != null) {
            UserWorkspacePanel workspace = workspaces.get(keepSelection);
            if (workspace != null) {
                Node.UserStatus current = users.stream()
                        .filter(user -> user.userId().equals(keepSelection))
                        .findFirst()
                        .orElse(null);
                if (current != null)
                    workspace.updateStatus(current);
                workspace.backgroundRefresh();
            }
        }
        updateTray(users, pending);
    }

    private void synchronize() {
        if (closed.get())
            return;
        syncButton.setEnabled(false);
        syncWorker.execute(() -> runSync(true));
    }

    private void runSync(boolean manual) {
        if (closed.get())
            return;
        try {
            node.syncKnownPeers();
            SwingUtilities.invokeLater(() -> {
                if (closed.get())
                    return;
                refresh();
                if (manual)
                    refreshWorkspaceContent();
            });
        } catch (RuntimeException error) {
            LOG.warn("Synchronization failed; will retry on the next background round", error);
            if (manual)
                SwingUtilities.invokeLater(() -> {
                    if (!closed.get())
                        status("Synchronization failed: " + error.getMessage());
                });
        } finally {
            if (manual)
                SwingUtilities.invokeLater(() -> {
                    if (!closed.get())
                        syncButton.setEnabled(true);
                });
        }
    }

    private void refreshWorkspaceContent() {
        if (selectedUserId != null) {
            UserWorkspacePanel workspace = workspaces.get(selectedUserId);
            if (workspace != null)
                workspace.refresh();
        }
    }

    private void status(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /** Shows a non-modal message in the status bar. */
    public void reportStatus(String message) {
        status(message);
    }

    private void addTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray unavailable: the desktop does not expose an AWT-compatible tray. "
                    + "The single-window dashboard remains available.");
            return;
        }
        PopupMenu menu = new PopupMenu();
        trayOpen = new MenuItem("Open p2p-sync");
        trayOpen.addActionListener(event -> frame.setVisible(true));
        menu.add(trayOpen);
        menu.addSeparator();
        trayQuit = new MenuItem("Quit p2p-sync");
        trayQuit.addActionListener(event -> close());
        menu.add(trayQuit);
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
        menu.removeAll();
        menu.add(trayOpen);
        menu.addSeparator();
        for (Node.UserStatus user : users) {
            MenuItem item = new MenuItem(user.localName() + "  -  "
                    + (user.pendingIncomingOperationCount() + user.pendingLocalOperationCount()) + " pending");
            item.addActionListener(event -> {
                frame.setVisible(true);
                SwingUtilities.invokeLater(() -> openWorkspace(user.userId()));
            });
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(trayQuit);
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Theme.INK);
        graphics.fillRoundRect(1, 1, 30, 30, 8, 8);
        graphics.setColor(new Color(111, 201, 151));
        graphics.fillOval(8, 8, 16, 16);
        graphics.setColor(Theme.INK);
        graphics.fillRect(15, 8, 3, 16);
        graphics.dispose();
        return image;
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
        syncWorker.execute(shutdown);
        syncWorker.shutdown();
    }

    private final class UserRenderer extends JPanel implements ListCellRenderer<Node.UserStatus> {
        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();

        UserRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(7, 8, 7, 8));
            name.setFont(Theme.SUBHEADING);
            meta.setFont(Theme.SMALL);
            add(name);
            add(Box.createVerticalStrut(1));
            add(meta);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends Node.UserStatus> list,
                Node.UserStatus value, int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value.localName());
            int pending = value.pendingLocalOperationCount() + value.pendingIncomingOperationCount();
            meta.setText(value.activeNodeCount() + " node" + (value.activeNodeCount() == 1 ? "" : "s")
                    + (pending == 0 ? "" : " · " + pending + " pending"));
            setBackground(isSelected ? Theme.SIDEBAR_ACTIVE : Theme.SIDEBAR);
            name.setForeground(isSelected ? Color.WHITE : new Color(228, 238, 233));
            meta.setForeground(isSelected ? new Color(183, 218, 199) : Theme.MUTED_LIGHT);
            return this;
        }
    }

    private final class SectionRenderer extends JLabel implements ListCellRenderer<UserWorkspacePanel.Section> {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<? extends UserWorkspacePanel.Section> list,
                UserWorkspacePanel.Section value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value.label);
            setFont(Theme.BODY.deriveFont(isSelected ? Font.BOLD : Font.PLAIN));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(true);
            setBackground(isSelected ? Theme.SIDEBAR_ACTIVE : Theme.SIDEBAR);
            setForeground(isSelected ? Color.WHITE : new Color(205, 219, 213));
            return this;
        }
    }
}
