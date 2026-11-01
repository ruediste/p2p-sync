package com.github.ruediste.p2psync.node;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.proto.Sync;

final class NodeWorkspaceDialog extends JDialog {
    private static final Color INK = new Color(28, 38, 42);
    private static final Color PAPER = new Color(244, 247, 244);
    private static final Color GREEN = new Color(36, 124, 91);
    private static final Color LINE = new Color(214, 223, 219);

    private final Node node;
    private final Node.UserStatus user;
    private final Path mountPoint;
    private final Runnable onChanged;
    private final UnitTableModel unitModel = new UnitTableModel();
    private final MessageTableModel messageModel = new MessageTableModel();
    private JTable unitTable;
    private JTable messageTable;

    NodeWorkspaceDialog(JFrame owner, Node node, Node.UserStatus user, Path mountPoint, Runnable onChanged) {
        super(owner, "Workspace: " + user.localName(), true);
        this.node = node;
        this.user = user;
        this.mountPoint = mountPoint;
        this.onChanged = onChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 500));
        setSize(980, 620);
        setLocationRelativeTo(owner);
        setContentPane(buildContent());
        refreshAll();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(PAPER);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel heading = new JLabel(user.localName() + " workspace");
        heading.setForeground(INK);
        heading.setFont(new Font(Font.SERIF, Font.BOLD, 24));
        root.add(heading, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Sync units", buildUnitPanel());
        tabs.addTab("Messages", buildMessagePanel());
        root.add(tabs, BorderLayout.CENTER);

        JButton close = button("Close", this::dispose);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(PAPER);
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildUnitPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(PAPER);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        unitTable = new JTable(unitModel);
        styleTable(unitTable);
        unitTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unitTable.getSelectionModel().addListSelectionListener(event -> updateUnitActions());
        unitTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2)
                    openSelectedUnit();
            }
        });
        panel.add(new JScrollPane(unitTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setBackground(PAPER);
        actions.add(button("Create", this::createUnit));
        actions.add(button("Join invite", this::joinSelectedUnit));
        actions.add(button("Members", this::manageMembers));
        actions.add(button("Open", this::openSelectedUnit));
        actions.add(button("Refresh", this::refreshAll));
        actions.add(button("Sync", this::syncNow));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMessagePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(PAPER);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        messageTable = new JTable(messageModel);
        styleTable(messageTable);
        messageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2)
                    showSelectedMessage();
            }
        });
        panel.add(new JScrollPane(messageTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setBackground(PAPER);
        actions.add(button("Compose chat", () -> composeMessage(Sync.UserMessage.Type.CHAT)));
        actions.add(button("Compose mail", () -> composeMessage(Sync.UserMessage.Type.MAIL)));
        actions.add(button("Accept invite", this::acceptSelectedInvitation));
        actions.add(button("Refresh", this::refreshMessages));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private static void styleTable(JTable table) {
        table.setRowHeight(32);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.setForeground(INK);
        table.setBackground(Color.WHITE);
        table.setGridColor(LINE);
        table.setSelectionBackground(new Color(216, 236, 226));
        table.setSelectionForeground(INK);
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(230, 237, 232));
        table.getTableHeader().setForeground(INK);
        table.setFillsViewportHeight(true);
    }

    private JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setForeground(Color.WHITE);
        button.setBackground(GREEN);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        button.addActionListener(event -> action.run());
        return button;
    }

    private void refreshAll() {
        refreshUnits();
        refreshMessages();
    }

    private void refreshUnits() {
        unitModel.setUnits(node.listSyncUnits(user.userId()));
        updateUnitActions();
    }

    private void refreshMessages() {
        var rows = new ArrayList<MessageRow>();
        node.getInboxMessages(user.userId()).forEach(message -> rows.add(new MessageRow("Inbox", message)));
        node.getOutboxMessages(user.userId()).forEach(message -> rows.add(new MessageRow("Outbox", message)));
        messageModel.setMessages(rows);
    }

    private void updateUnitActions() {
        if (unitTable == null)
            return;
        boolean selected = unitTable.getSelectedRow() >= 0;
        // Selection-dependent commands report a clear message when no row is selected.
        unitTable.setToolTipText(selected ? null : "Select a SyncUnit first");
    }

    private Node.SyncUnitStatus selectedUnit() {
        int row = unitTable.getSelectedRow();
        if (row < 0)
            return null;
        return unitModel.units.get(unitTable.convertRowIndexToModel(row));
    }

    private void createUnit() {
        var labels = new String[] { "Shared folder", "Todo list", "Message channel" };
        String selected = (String) JOptionPane.showInputDialog(this, "SyncUnit type", "Create SyncUnit",
                JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (selected == null)
            return;
        var type = switch (selected) {
            case "Shared folder" -> SyncUnitAuthorization.Type.SHARED_FOLDER;
            case "Todo list" -> SyncUnitAuthorization.Type.TODO_LIST;
            case "Message channel" -> SyncUnitAuthorization.Type.MESSAGE_CHANNEL;
            default -> throw new IllegalStateException("Unknown SyncUnit type");
        };
        try {
            var id = node.createSyncUnit(user.userId(), type);
            refreshAll();
            selectUnit(id);
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void joinSelectedUnit() {
        var selected = selectedUnit();
        if (selected == null) {
            showInfo("Select a pending invitation first.");
            return;
        }
        if (!selected.pendingInvitation()) {
            showInfo("The selected unit has no pending invitation.");
            return;
        }
        String peerText = JOptionPane.showInputDialog(this,
                "Peer ID of a node hosting this invitation", "Join SyncUnit", JOptionPane.PLAIN_MESSAGE);
        if (peerText == null)
            return;
        try {
            var peer = PeerId.fromBase58(peerText.trim());
            node.joinSyncUnit(peer, selected.id(), user.userId());
            refreshAll();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void syncNow() {
        try {
            node.syncKnownPeers();
            refreshAll();
            onChanged.run();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void selectUnit(SyncUnitId id) {
        for (int row = 0; row < unitModel.units.size(); row++)
            if (unitModel.units.get(row).id().equals(id)) {
                unitTable.setRowSelectionInterval(row, row);
                return;
            }
    }

    private void manageMembers() {
        var selected = selectedUnit();
        if (selected == null) {
            showInfo("Select a SyncUnit first.");
            return;
        }
        showMembers(selected.id());
    }

    private void showMembers(SyncUnitId id) {
        var unit = node.listSyncUnits(user.userId()).stream()
                .filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (unit == null) {
            showInfo("This SyncUnit is not available on this node.");
            return;
        }
        var members = new MemberTableModel();
        members.setMembers(node.listSyncUnitMembers(id));
        JTable table = new JTable(members);
        styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JDialog dialog = new JDialog(this, "Members: " + shortId(id), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        boolean canManage = unit.role() == SyncUnitAuthorization.Role.MANAGER;
        JButton invite = button("Invite", () -> {
            inviteMember(id);
            members.setMembers(node.listSyncUnitMembers(id));
            refreshAll();
        });
        invite.setEnabled(canManage);
        actions.add(invite);
        JButton changeRole = button("Change role", () -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                showInfo("Select a member first.");
                return;
            }
            var member = members.members.get(table.convertRowIndexToModel(row));
            if (member.pendingInvitation()) {
                showInfo("Accept the invitation before changing its role.");
                return;
            }
            var role = chooseRole(unit.type());
            if (role == null)
                return;
            try {
                node.changeSyncUnitRole(id, user.userId(), member.userId(), role);
                members.setMembers(node.listSyncUnitMembers(id));
                refreshAll();
            } catch (RuntimeException error) {
                showError(error);
            }
        });
        changeRole.setEnabled(canManage);
        actions.add(changeRole);
        JButton remove = button("Remove", () -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                showInfo("Select a member first.");
                return;
            }
            var member = members.members.get(table.convertRowIndexToModel(row));
            if (JOptionPane.showConfirmDialog(dialog, "Remove " + shortKey(member.userId()) + "?",
                    "Remove member", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
                return;
            try {
                node.removeFromSyncUnit(id, user.userId(), member.userId());
                members.setMembers(node.listSyncUnitMembers(id));
                refreshAll();
            } catch (RuntimeException error) {
                showError(error);
            }
        });
        remove.setEnabled(canManage);
        actions.add(remove);
        actions.add(button("Close", dialog::dispose));
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void inviteMember(SyncUnitId id) {
        String keyText = JOptionPane.showInputDialog(this,
                "Invitee public key (hex-encoded marshaled key)", "Invite member", JOptionPane.PLAIN_MESSAGE);
        if (keyText == null)
            return;
        try {
            var invitee = PubKey.from(HexFormat.of().parseHex(keyText.trim()));
            var role = chooseRole(node.syncUnits.get(id).type);
            if (role == null)
                return;
            node.inviteToSyncUnit(id, user.userId(), invitee, role);
            var unitType = node.syncUnits.get(id).type;
            var messageType = unitType == SyncUnitAuthorization.Type.SHARED_FOLDER
                    ? Sync.UserMessage.Type.SHARED_FOLDER_INVITATION
                    : unitType == SyncUnitAuthorization.Type.TODO_LIST
                            ? Sync.UserMessage.Type.TODO_LIST_INVITATION
                            : unitType == SyncUnitAuthorization.Type.MESSAGE_CHANNEL
                                    ? Sync.UserMessage.Type.MESSAGE_CHANNEL_INVITATION
                                    : null;
            if (messageType != null) {
                var text = userHandle().sendMessage(invitee, messageType, "SyncUnit invitation",
                        "You were invited to " + unitType.name().toLowerCase().replace('_', ' ') + ".",
                        List.of(), id);
                if (text.isBlank())
                    throw new IllegalStateException("Invitation message ID was not created");
            }
            refreshAll();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private SyncUnitAuthorization.Role chooseRole(SyncUnitAuthorization.Type type) {
        SyncUnitAuthorization.Role[] roles = type == SyncUnitAuthorization.Type.MESSAGE_CHANNEL
                ? new SyncUnitAuthorization.Role[] { SyncUnitAuthorization.Role.MEMBER,
                        SyncUnitAuthorization.Role.MANAGER }
                : new SyncUnitAuthorization.Role[] { SyncUnitAuthorization.Role.VIEWER,
                        SyncUnitAuthorization.Role.EDITOR, SyncUnitAuthorization.Role.MANAGER };
        return (SyncUnitAuthorization.Role) JOptionPane.showInputDialog(this, "Member role", "Choose role",
                JOptionPane.PLAIN_MESSAGE, null, roles, roles[0]);
    }

    private void openSelectedUnit() {
        var selected = selectedUnit();
        if (selected == null) {
            showInfo("Select a SyncUnit first.");
            return;
        }
        if (selected.pendingInvitation()) {
            showInfo("Accept the invitation before opening this SyncUnit.");
            return;
        }
        try {
            switch (selected.type()) {
                case USER -> Desktop.getDesktop().open(mountPoint.resolve(user.localName()).toFile());
                case SHARED_FOLDER -> openSharedFolder(selected.id());
                case TODO_LIST -> openTodoList(selected.id());
                case MESSAGE_CHANNEL -> openChannel(selected.id());
            }
        } catch (Exception error) {
            showError(error);
        }
    }

    private void openSharedFolder(SyncUnitId id) {
        var model = new FileTableModel();
        var table = new JTable(model);
        styleTable(table);
        var dialog = new JDialog(this, "Shared folder: " + shortId(id), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Upload", () -> {
            var chooser = new JFileChooser();
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
                return;
            var file = chooser.getSelectedFile().toPath();
            String path = JOptionPane.showInputDialog(dialog, "Shared path", file.getFileName().toString());
            if (path == null)
                return;
            try {
                node.putSharedFile(id, user.userId(), path, Files.readAllBytes(file));
                model.setFiles(node.listSharedFiles(id, user.userId()));
            } catch (Exception error) {
                showError(error);
            }
        }));
        actions.add(button("Download", () -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                showInfo("Select a file first.");
                return;
            }
            var path = model.paths.get(table.convertRowIndexToModel(row));
            var chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File(Path.of(path).getFileName().toString()));
            if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION)
                return;
            try {
                Files.write(chooser.getSelectedFile().toPath(), node.readSharedFile(id, user.userId(), path));
            } catch (Exception error) {
                showError(error);
            }
        }));
        actions.add(button("Delete", () -> {
            int row = table.getSelectedRow();
            if (row < 0)
                return;
            try {
                node.deleteSharedFile(id, user.userId(), model.paths.get(table.convertRowIndexToModel(row)));
                model.setFiles(node.listSharedFiles(id, user.userId()));
            } catch (RuntimeException error) {
                showError(error);
            }
        }));
        actions.add(button("Refresh", () -> model.setFiles(node.listSharedFiles(id, user.userId()))));
        actions.add(button("Close", dialog::dispose));
        dialog.add(actions, BorderLayout.SOUTH);
        model.setFiles(node.listSharedFiles(id, user.userId()));
        dialog.setSize(760, 430);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openTodoList(SyncUnitId id) {
        var model = new TodoTableModel();
        var table = new JTable(model);
        styleTable(table);
        var dialog = new JDialog(this, "Todo list: " + shortId(id), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Add / edit", () -> {
            int selectedRow = table.getSelectedRow();
            String selectedItem = selectedRow < 0 ? ""
                    : model.itemIds.get(table.convertRowIndexToModel(selectedRow));
            String itemId = JOptionPane.showInputDialog(dialog, "Todo item ID", selectedItem);
            if (itemId == null)
                return;
            String previous = model.items.containsKey(itemId)
                    ? new String(model.items.get(itemId), StandardCharsets.UTF_8)
                    : "";
            JTextArea content = new JTextArea(previous, 8, 42);
            int result = JOptionPane.showConfirmDialog(dialog, new JScrollPane(content), "Todo item content",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION)
                return;
            try {
                node.putTodoItem(id, user.userId(), itemId, content.getText().getBytes(StandardCharsets.UTF_8));
                model.setItems(node.listTodoItems(id, user.userId()));
            } catch (RuntimeException error) {
                showError(error);
            }
        }));
        actions.add(button("Delete", () -> {
            int row = table.getSelectedRow();
            if (row < 0)
                return;
            try {
                node.deleteTodoItem(id, user.userId(), model.itemIds.get(table.convertRowIndexToModel(row)));
                model.setItems(node.listTodoItems(id, user.userId()));
            } catch (RuntimeException error) {
                showError(error);
            }
        }));
        actions.add(button("Refresh", () -> model.setItems(node.listTodoItems(id, user.userId()))));
        actions.add(button("Close", dialog::dispose));
        dialog.add(actions, BorderLayout.SOUTH);
        model.setItems(node.listTodoItems(id, user.userId()));
        dialog.setSize(760, 430);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openChannel(SyncUnitId id) {
        var model = new ChannelTableModel();
        JTable table = new JTable(model);
        styleTable(table);
        JTextArea compose = new JTextArea(4, 60);
        var attachments = new ArrayList<Sync.SyncUnitAttachment>();
        var dialog = new JDialog(this, "Channel: " + shortId(id), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel composePanel = new JPanel(new BorderLayout(6, 6));
        composePanel.add(new JScrollPane(compose), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("Attach file", () -> addAttachments(dialog, attachments)));
        actions.add(button("Save attachment", () -> saveChannelAttachment(dialog, table, model)));
        actions.add(button("Send", () -> {
            try {
                var message = Sync.SyncUnitMessage.newBuilder().setText(compose.getText());
                attachments.forEach(message::addAttachments);
                node.sendChannelMessage(id, user.userId(), message.build());
                compose.setText("");
                attachments.clear();
                refreshChannelHistory(id, model);
            } catch (RuntimeException error) {
                showError(error);
            }
        }));
        actions.add(button("Close", dialog::dispose));
        composePanel.add(actions, BorderLayout.SOUTH);
        dialog.add(composePanel, BorderLayout.SOUTH);
        refreshChannelHistory(id, model);
        dialog.setSize(780, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refreshChannelHistory(SyncUnitId id, ChannelTableModel model) {
        model.setMessages(node.getChannelMessages(id, user.userId()));
    }

    private void saveChannelAttachment(JDialog dialog, JTable table, ChannelTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) {
            showInfo("Select a channel message first.");
            return;
        }
        var entry = model.messages.get(table.convertRowIndexToModel(row));
        if (entry.message().getAttachmentsCount() == 0) {
            showInfo("The selected message has no attachments.");
            return;
        }
        var attachments = entry.message().getAttachmentsList();
        String[] names = attachments.stream().map(Sync.SyncUnitAttachment::getName).toArray(String[]::new);
        String selected = names.length == 1 ? names[0]
                : (String) JOptionPane.showInputDialog(dialog, "Attachment", "Save attachment",
                        JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (selected == null)
            return;
        var attachment = attachments.stream().filter(item -> item.getName().equals(selected)).findFirst().orElseThrow();
        saveAttachment(dialog, attachment);
    }

    private void composeMessage(Sync.UserMessage.Type type) {
        String recipientText = JOptionPane.showInputDialog(this,
                "Recipient public key (hex-encoded marshaled key)", "Compose message", JOptionPane.PLAIN_MESSAGE);
        if (recipientText == null)
            return;
        PubKey recipient;
        try {
            recipient = PubKey.from(HexFormat.of().parseHex(recipientText.trim()));
        } catch (RuntimeException error) {
            showError(error);
            return;
        }
        String subject = "";
        if (type == Sync.UserMessage.Type.MAIL) {
            subject = JOptionPane.showInputDialog(this, "Subject", "");
            if (subject == null)
                return;
        }
        JTextArea body = new JTextArea(8, 48);
        if (JOptionPane.showConfirmDialog(this, new JScrollPane(body), "Message body", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return;
        var attachments = new ArrayList<Sync.SyncUnitAttachment>();
        if (JOptionPane.showConfirmDialog(this, "Attach files?", "Attachments",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            addAttachments(this, attachments);
        try {
            userHandle().sendMessage(recipient, type, subject, body.getText(), attachments, null);
            refreshMessages();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void addAttachments(java.awt.Component parent, List<Sync.SyncUnitAttachment> attachments) {
        var chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION)
            return;
        for (var file : chooser.getSelectedFiles()) {
            try {
                String mediaType = java.nio.file.Files.probeContentType(file.toPath());
                attachments.add(Sync.SyncUnitAttachment.newBuilder()
                        .setName(file.getName())
                        .setMediaType(mediaType == null ? "application/octet-stream" : mediaType)
                        .setContent(com.google.protobuf.ByteString.copyFrom(Files.readAllBytes(file.toPath())))
                        .build());
            } catch (IOException error) {
                showError(error);
            }
        }
    }

    private void acceptSelectedInvitation() {
        var row = selectedMessage();
        if (row == null || !row.direction.equals("Inbox")) {
            showInfo("Select an invitation in the inbox.");
            return;
        }
        var message = row.message;
        if (message.getInvitedSyncUnitId().size() != 32) {
            showInfo("The selected message is not a SyncUnit invitation.");
            return;
        }
        String peerText = JOptionPane.showInputDialog(this,
                "Peer ID of a node hosting this invitation", "Accept invitation", JOptionPane.PLAIN_MESSAGE);
        if (peerText == null)
            return;
        try {
            var peer = PeerId.fromBase58(peerText.trim());
            var id = SyncUnitId.fromBytes(message.getInvitedSyncUnitId().toByteArray());
            node.joinSyncUnit(peer, id, user.userId());
            refreshAll();
        } catch (RuntimeException error) {
            showError(error);
        }
    }

    private void showSelectedMessage() {
        var row = selectedMessage();
        if (row == null)
            return;
        var message = row.message;
        var content = new StringBuilder();
        content.append(row.direction).append("  ·  ").append(message.getType()).append('\n')
                .append("From: ").append(shortKey(PubKey.from(message.getSenderUserId().toByteArray()))).append('\n')
                .append("To: ").append(shortKey(PubKey.from(message.getRecipientUserId().toByteArray()))).append('\n');
        if (!message.getSubject().isBlank())
            content.append("Subject: ").append(message.getSubject()).append('\n');
        content.append('\n').append(message.getBody());
        JTextArea body = new JTextArea(content.toString(), 18, 58);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        JDialog dialog = new JDialog(this, "Message", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JScrollPane(body), BorderLayout.CENTER);
        JPanel attachments = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (var attachment : message.getAttachmentsList())
            attachments.add(button("Save " + attachment.getName(), () -> saveAttachment(dialog, attachment)));
        attachments.add(button("Close", dialog::dispose));
        dialog.add(attachments, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void saveAttachment(java.awt.Component parent, Sync.SyncUnitAttachment attachment) {
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Save attachment");
        chooser.setSelectedFile(new java.io.File(attachment.getName()));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION)
            return;
        try {
            Files.write(chooser.getSelectedFile().toPath(), attachment.getContent().toByteArray());
        } catch (IOException error) {
            showError(error);
        }
    }

    private MessageRow selectedMessage() {
        int row = messageTable.getSelectedRow();
        if (row < 0)
            return null;
        return messageModel.messages.get(messageTable.convertRowIndexToModel(row));
    }

    private NodeUserHandle userHandle() {
        return node.getUserByName(user.localName());
    }

    private static String shortId(SyncUnitId id) {
        return HexFormat.of().formatHex(id.bytes(), 0, 8);
    }

    private static String shortKey(PubKey key) {
        return HexFormat.of().formatHex(key.bytes(), 0, 8);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "p2p-sync", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), "p2p-sync", JOptionPane.ERROR_MESSAGE);
    }

    private record MessageRow(String direction, Sync.UserMessage message) {
    }

    private static final class UnitTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Type", "Role", "Members", "Invitations", "Unit ID" };
        private List<Node.SyncUnitStatus> units = List.of();

        void setUnits(List<Node.SyncUnitStatus> units) {
            this.units = List.copyOf(units);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return units.size();
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
        public Object getValueAt(int row, int column) {
            var unit = units.get(row);
            return switch (column) {
                case 0 -> unit.type().name().replace('_', ' ');
                case 1 -> unit.pendingInvitation() ? "Invitation pending"
                        : unit.role() == null ? "Participant" : unit.role();
                case 2 -> unit.memberCount();
                case 3 -> unit.pendingInvitationCount();
                case 4 -> HexFormat.of().formatHex(unit.id().bytes(), 0, 8);
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class MemberTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "User key", "Role", "Status" };
        private List<Node.SyncUnitMemberStatus> members = List.of();

        void setMembers(List<Node.SyncUnitMemberStatus> members) {
            this.members = List.copyOf(members);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return members.size();
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
        public Object getValueAt(int row, int column) {
            var member = members.get(row);
            return switch (column) {
                case 0 -> HexFormat.of().formatHex(member.userId().bytes(), 0, 10);
                case 1 -> member.role();
                case 2 -> member.pendingInvitation() ? "Invited" : "Active";
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class FileTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Path", "Size" };
        private List<String> paths = List.of();
        private Map<String, byte[]> files = Map.of();

        void setFiles(Map<String, byte[]> files) {
            this.files = files;
            paths = List.copyOf(files.keySet());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return paths.size();
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
        public Object getValueAt(int row, int column) {
            String path = paths.get(row);
            return column == 0 ? path : files.get(path).length;
        }
    }

    private static final class TodoTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Item ID", "Content" };
        private List<String> itemIds = List.of();
        private Map<String, byte[]> items = Map.of();

        void setItems(Map<String, byte[]> items) {
            this.items = items;
            itemIds = List.copyOf(items.keySet());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return itemIds.size();
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
        public Object getValueAt(int row, int column) {
            String itemId = itemIds.get(row);
            return column == 0 ? itemId : new String(items.get(itemId), StandardCharsets.UTF_8);
        }
    }

    private static final class ChannelTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Message", "Attachments", "ID" };
        private List<Node.ChannelMessageEntry> messages = List.of();

        void setMessages(List<Node.ChannelMessageEntry> messages) {
            this.messages = List.copyOf(messages);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return messages.size();
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
        public Object getValueAt(int row, int column) {
            var message = messages.get(row);
            return switch (column) {
                case 0 -> message.message().getText();
                case 1 -> message.message().getAttachmentsCount();
                case 2 -> message.messageId();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class MessageTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Folder", "Type", "Contact", "Subject", "Message ID" };
        private List<MessageRow> messages = List.of();

        void setMessages(List<MessageRow> messages) {
            this.messages = List.copyOf(messages);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return messages.size();
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
        public Object getValueAt(int row, int column) {
            var entry = messages.get(row);
            var message = entry.message;
            return switch (column) {
                case 0 -> entry.direction;
                case 1 -> message.getType();
                case 2 -> entry.direction.equals("Inbox")
                        ? shortKey(PubKey.from(message.getSenderUserId().toByteArray()))
                        : shortKey(PubKey.from(message.getRecipientUserId().toByteArray()));
                case 3 -> message.getSubject();
                case 4 -> message.getMessageId();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }
}
