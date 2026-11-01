package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;
import com.github.ruediste.p2psync.node.SyncUnitId;

/** The workspace of one user: overview, chat, mail, units and account. */
final class UserWorkspacePanel extends JPanel {
    enum Section {
        OVERVIEW("Overview"),
        CHAT("Chat"),
        MAIL("Mail"),
        CONTACTS("Contacts"),
        UNITS("Sync units"),
        ACCOUNT("Account");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private final Node node;
    private final Path mountPoint;
    private final Consumer<String> status;
    private final Runnable onChanged;
    private final Consumer<Section> onSectionChanged;

    private final JLabel nameLabel = Theme.heading("");
    private final JLabel metaLabel = Theme.muted("");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final ChatPanel chat;
    private final MailPanel mail;
    private final ContactsPanel contacts;
    private final UnitsPanel units;
    private final AccountPanel account;
    private final JPanel overview;
    private final JLabel overviewMeta = Theme.muted("");
    private final JLabel mountPath = Theme.mono("");
    private final JPanel rootList = Theme.column();

    private Node.UserStatus user;
    private Section section = Section.OVERVIEW;

    UserWorkspacePanel(Node node, Node.UserStatus user, Path mountPoint, Consumer<String> status,
            Runnable onChanged, Consumer<Section> onSectionChanged) {
        this.node = node;
        this.user = user;
        this.mountPoint = mountPoint;
        this.status = status;
        this.onChanged = onChanged;
        this.onSectionChanged = onSectionChanged;
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);

        chat = new ChatPanel(node, user, status);
        mail = new MailPanel(node, user, status);
        contacts = new ContactsPanel(node, user, status);
        units = new UnitsPanel(node, user, mountPoint, status);
        account = new AccountPanel(node, user, mountPoint, status, onChanged);
        units.setOpenChannelHandler(id -> selectChannel(id));
        overview = buildOverview();

        cardPanel.setBackground(Theme.PAPER);
        cardPanel.add(overview, Section.OVERVIEW.name());
        cardPanel.add(chat, Section.CHAT.name());
        cardPanel.add(mail, Section.MAIL.name());
        cardPanel.add(contacts, Section.CONTACTS.name());
        cardPanel.add(units, Section.UNITS.name());
        cardPanel.add(account, Section.ACCOUNT.name());

        add(buildHeader(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        updateHeader();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.PAPER);
        header.setBorder(new EmptyBorder(18, 22, 10, 22));
        JPanel labels = Theme.column();
        labels.setBackground(Theme.PAPER);
        nameLabel.setFont(Theme.TITLE);
        labels.add(nameLabel);
        labels.add(Box.createVerticalStrut(2));
        labels.add(metaLabel);
        header.add(labels, BorderLayout.WEST);
        return header;
    }

    private JPanel buildOverview() {
        JPanel content = new JPanel();
        content.setBackground(Theme.PAPER);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(8, 22, 20, 22));

        JPanel welcome = Theme.card();
        welcome.setAlignmentX(LEFT_ALIGNMENT);
        welcome.add(Theme.subheading("Your workspace"));
        welcome.add(Box.createVerticalStrut(4));
        overviewMeta.setAlignmentX(LEFT_ALIGNMENT);
        welcome.add(overviewMeta);
        welcome.add(Box.createVerticalStrut(10));
        JPanel actions = Theme.row(8);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        JButton chatButton = Theme.primaryButton("Open chat");
        chatButton.addActionListener(event -> showSection(Section.CHAT));
        actions.add(chatButton);
        JButton mailButton = Theme.secondaryButton("Open mail");
        mailButton.addActionListener(event -> showSection(Section.MAIL));
        actions.add(mailButton);
        JButton unitsButton = Theme.secondaryButton("Manage SyncUnits");
        unitsButton.addActionListener(event -> showSection(Section.UNITS));
        actions.add(unitsButton);
        welcome.add(actions);
        content.add(welcome);
        content.add(Box.createVerticalStrut(12));

        JPanel shortcuts = new JPanel();
        shortcuts.setLayout(new BoxLayout(shortcuts, BoxLayout.X_AXIS));
        shortcuts.setOpaque(false);
        shortcuts.setAlignmentX(LEFT_ALIGNMENT);
        shortcuts.add(shortcut("Chat", "Direct messages and message channels", Section.CHAT));
        shortcuts.add(Box.createHorizontalStrut(12));
        shortcuts.add(shortcut("Mail", "Inbox, sent mail, invitations and attachments", Section.MAIL));
        shortcuts.add(Box.createHorizontalStrut(12));
        shortcuts.add(shortcut("Contacts", "Address book for mail, chat and invitations", Section.CONTACTS));
        shortcuts.add(Box.createHorizontalStrut(12));
        shortcuts.add(shortcut("Sync units", "Shared folders, todo lists and members", Section.UNITS));
        content.add(shortcuts);
        content.add(Box.createVerticalStrut(12));

        JPanel mount = Theme.card();
        mount.setAlignmentX(LEFT_ALIGNMENT);
        mount.add(Theme.subheading("Files on this device"));
        mount.add(Box.createVerticalStrut(4));
        mountPath.setAlignmentX(LEFT_ALIGNMENT);
        mount.add(mountPath);
        mount.add(Box.createVerticalStrut(4));
        mount.add(Theme.muted("Your mounted files are available in this folder."));
        content.add(mount);
        content.add(Box.createVerticalStrut(12));

        JPanel roots = Theme.card();
        roots.setAlignmentX(LEFT_ALIGNMENT);
        roots.add(Theme.subheading("File roots"));
        roots.add(Box.createVerticalStrut(4));
        roots.add(Theme.muted("The mount exposes the home tree and labeled shared folders together."));
        roots.add(Box.createVerticalStrut(6));
        rootList.setAlignmentX(LEFT_ALIGNMENT);
        roots.add(rootList);
        content.add(roots);
        return content;
    }

    private JPanel shortcut(String title, String description, Section target) {
        JPanel card = Theme.card();
        card.setMaximumSize(new Dimension(250, 120));
        card.setPreferredSize(new Dimension(230, 116));
        card.setAlignmentY(TOP_ALIGNMENT);
        card.add(Theme.subheading(title));
        card.add(Box.createVerticalStrut(4));
        JLabel text = Theme.muted("<html><div style='width:170px'>" + Theme.escape(description) + "</div></html>");
        text.setAlignmentX(LEFT_ALIGNMENT);
        card.add(text);
        JButton open = Theme.ghostButton("Open →");
        open.setAlignmentX(LEFT_ALIGNMENT);
        open.addActionListener(event -> showSection(target));
        card.add(open);
        return card;
    }

    void showSection(Section section) {
        this.section = section;
        cards.show(cardPanel, section.name());
        refreshActive();
        onSectionChanged.accept(section);
    }

    Section currentSection() {
        return section;
    }

    void selectChannel(SyncUnitId id) {
        showSection(Section.CHAT);
        chat.selectChannel(id);
    }

    void refresh() {
        Node.UserStatus current = node.listUsers().stream()
                .filter(item -> item.userId().equals(user.userId()))
                .findFirst()
                .orElse(user);
        user = current;
        chat.setUser(user);
        mail.setUser(user);
        contacts.setUser(user);
        units.setUser(user);
        account.setUser(user);
        updateHeader();
        refreshActive();
    }

    void updateStatus(Node.UserStatus current) {
        user = current;
        chat.setUser(user);
        mail.setUser(user);
        contacts.setUser(user);
        units.setUser(user);
        account.setUser(user);
        updateHeader();
    }

    /** Refreshes only views that are safe to update while the user is working. */
    void backgroundRefresh() {
        switch (section) {
            case OVERVIEW -> refreshOverview();
            case CHAT -> chat.refresh();
            case MAIL -> mail.refresh();
            case CONTACTS -> contacts.refresh();
            case UNITS, ACCOUNT -> {
            }
        }
    }

    private void refreshActive() {
        switch (section) {
            case OVERVIEW -> refreshOverview();
            case CHAT -> chat.refresh();
            case MAIL -> mail.refresh();
            case CONTACTS -> contacts.refresh();
            case UNITS -> units.refresh();
            case ACCOUNT -> account.setUser(user);
        }
    }

    private void refreshOverview() {
        long units = node.listSyncUnits(user.userId()).size();
        long channels = node.listSyncUnits(user.userId()).stream()
                .filter(unit -> unit.type() == SyncUnitAuthorization.Type.MESSAGE_CHANNEL
                        && !unit.pendingInvitation())
                .count();
        overviewMeta.setText(user.activeNodeCount() + " active nodes · "
                + user.pendingLocalOperationCount() + " local pending · "
                + user.pendingIncomingOperationCount() + " incoming pending · "
                + units + " SyncUnits (" + channels + " channels)");
        refreshRoots();
    }

    private void refreshRoots() {
        rootList.removeAll();
        for (var entry : node.listRootEntries(user.userId())) {
            JPanel row = Theme.row(8);
            row.setAlignmentX(LEFT_ALIGNMENT);
            JLabel label = Theme.mono(entry.label());
            label.setPreferredSize(new Dimension(150, 20));
            row.add(label);
            String path = mountPoint.resolve(user.localName()).resolve(entry.label()).toString();
            JLabel target = Theme.muted(path);
            row.add(target);
            if (entry.conflict())
                row.add(Theme.muted("duplicate label; showing the first shared folder"));
            rootList.add(row);
        }
        if (rootList.getComponentCount() == 0)
            rootList.add(Theme.muted("No file roots are available yet."));
        rootList.revalidate();
        rootList.repaint();
    }

    private void updateHeader() {
        nameLabel.setText(user.localName());
        mountPath.setText(mountPoint.resolve(user.localName()).toString());
        metaLabel.setText("User " + HexFormat.of().formatHex(user.userId().bytes(), 0, 8)
                + "  ·  " + user.activeNodeCount() + " active nodes  ·  "
                + (user.pendingLocalOperationCount() + user.pendingIncomingOperationCount()) + " pending");
        metaLabel.setFont(Theme.SMALL.deriveFont(Font.PLAIN));
    }
}
