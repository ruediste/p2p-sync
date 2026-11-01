package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import com.github.ruediste.p2psync.libp2p.core.PeerId;
import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;
import com.github.ruediste.p2psync.node.SyncUnitId;
import com.github.ruediste.p2psync.proto.Sync;

/** Inline management of shared folders, todo lists and message channels. */
final class UnitsPanel extends JPanel {
    private final Node node;
    private final Consumer<String> status;
    private Consumer<SyncUnitId> openChannel = id -> {
    };
    private Node.UserStatus user;

    private final UnitTableModel unitModel = new UnitTableModel();
    private final JTable unitTable = new JTable(unitModel);
    private final JPanel detail = new JPanel(new BorderLayout());
    private boolean refreshing;
    private SyncUnitId selectedId;

    UnitsPanel(Node node, Node.UserStatus user, Consumer<String> status) {
        this.node = node;
        this.user = user;
        this.status = status;
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildUnitList(), buildDetailPanel());
        split.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        split.setDividerLocation(300);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.PAPER);
        JPanel titles = Theme.column();
        titles.setBackground(Theme.PAPER);
        titles.add(Theme.heading("Sync units"));
        titles.add(Box.createVerticalStrut(3));
        titles.add(Theme.muted("Shared folders, todo lists and message channels"));
        header.add(titles, BorderLayout.WEST);

        JPanel actions = Theme.row(7);
        actions.setBackground(Theme.PAPER);
        actions.add(createButton("New channel", SyncUnitAuthorization.Type.MESSAGE_CHANNEL));
        actions.add(createButton("New folder", SyncUnitAuthorization.Type.SHARED_FOLDER));
        actions.add(createButton("New todo", SyncUnitAuthorization.Type.TODO_LIST));
        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> refresh());
        actions.add(refresh);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JButton createButton(String label, SyncUnitAuthorization.Type type) {
        JButton button = Theme.secondaryButton(label);
        button.addActionListener(event -> {
            try {
                SyncUnitId id = node.createSyncUnit(user.userId(), type);
                status.accept(Theme.unitType(type) + " created");
                refresh();
                select(id);
            } catch (RuntimeException error) {
                status.accept("Could not create unit: " + error.getMessage());
            }
        });
        return button;
    }

    private JPanel buildUnitList() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.PAPER);
        panel.setBorder(new EmptyBorder(0, 0, 0, 12));
        unitTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unitTable.setRowHeight(34);
        unitTable.setFont(Theme.BODY);
        unitTable.setForeground(Theme.INK);
        unitTable.setBackground(Theme.CARD);
        unitTable.setGridColor(Theme.LINE);
        unitTable.setShowVerticalLines(false);
        unitTable.setFillsViewportHeight(true);
        unitTable.setSelectionBackground(Theme.GREEN_SOFT);
        unitTable.setSelectionForeground(Theme.INK);
        unitTable.getTableHeader().setFont(Theme.SMALL.deriveFont(Font.BOLD));
        unitTable.getTableHeader().setBackground(new Color(230, 237, 232));
        unitTable.getTableHeader().setForeground(Theme.INK);
        unitTable.getSelectionModel().addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            int row = unitTable.getSelectedRow();
            if (row < 0)
                return;
            selectedId = unitModel.units.get(unitTable.convertRowIndexToModel(row)).id();
            showUnit(selectedId);
        });
        panel.add(Theme.scroll(unitTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDetailPanel() {
        detail.setBackground(Theme.PAPER);
        detail.setBorder(new EmptyBorder(0, 12, 0, 0));
        return detail;
    }

    void setUser(Node.UserStatus user) {
        this.user = user;
    }

    void setOpenChannelHandler(Consumer<SyncUnitId> openChannel) {
        this.openChannel = openChannel;
    }

    void refresh() {
        if (selectedId == null && unitTable.getSelectedRow() >= 0)
            selectedId = unitModel.units.get(unitTable.convertRowIndexToModel(unitTable.getSelectedRow())).id();
        List<Node.SyncUnitStatus> units = node.listSyncUnits(user.userId()).stream()
                .filter(unit -> unit.type() != SyncUnitAuthorization.Type.USER)
                .toList();
        refreshing = true;
        try {
            unitModel.setUnits(units);
        } finally {
            refreshing = false;
        }
        if (selectedId == null || units.stream().noneMatch(unit -> unit.id().equals(selectedId))) {
            selectedId = units.stream()
                    .filter(unit -> !unit.pendingInvitation())
                    .map(Node.SyncUnitStatus::id)
                    .findFirst()
                    .orElse(units.isEmpty() ? null : units.get(0).id());
        }
        if (selectedId != null)
            select(selectedId);
        else {
            unitTable.clearSelection();
            showEmpty();
        }
    }

    private void select(SyncUnitId id) {
        selectedId = id;
        for (int index = 0; index < unitModel.units.size(); index++) {
            if (unitModel.units.get(index).id().equals(id)) {
                int view = unitTable.convertRowIndexToView(index);
                refreshing = true;
                try {
                    unitTable.setRowSelectionInterval(view, view);
                } finally {
                    refreshing = false;
                }
                showUnit(id);
                return;
            }
        }
    }

    private Node.SyncUnitStatus findUnit(SyncUnitId id) {
        return node.listSyncUnits(user.userId()).stream()
                .filter(unit -> unit.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void showEmpty() {
        detail.removeAll();
        detail.add(Theme.emptyState("No SyncUnits yet",
                "Create a shared folder, a todo list, or a message channel to get started."), BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private void showUnit(SyncUnitId id) {
        Node.SyncUnitStatus unit = findUnit(id);
        if (unit == null) {
            showEmpty();
            return;
        }
        JPanel content = stack();
        addStack(content, buildUnitHeader(unit), 0);
        if (unit.pendingInvitation()) {
            addStack(content, buildPendingInvitation(unit), 10);
        } else {
            switch (unit.type()) {
                case SHARED_FOLDER -> addStack(content, buildSharedFolder(unit), 10);
                case TODO_LIST -> addStack(content, buildTodoList(unit), 10);
                case MESSAGE_CHANNEL -> addStack(content, buildChannel(unit), 10);
                case USER -> addStack(content, Theme.muted("User SyncUnit"), 10);
            }
            addStack(content, buildMembers(unit), 12);
        }
        JScrollPane scroll = Theme.verticalScroll(content);
        detail.removeAll();
        detail.add(scroll, BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private static JPanel stack() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.PAPER);
        return panel;
    }

    private static void addStack(JPanel panel, java.awt.Component component, int topGap) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = panel.getComponentCount();
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(topGap, 0, 0, 0);
        panel.add(component, constraints);
    }

    private JPanel buildUnitHeader(Node.SyncUnitStatus unit) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBackground(Theme.PAPER);
        JPanel titles = Theme.column();
        titles.setBackground(Theme.PAPER);
        JLabel title = Theme.heading(Theme.unitType(unit.type()) + " · " + Theme.shortId(unit.id()));
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(Theme.muted(unit.memberCount() + " members · "
                + (unit.role() == null ? "participant" : unit.role().name().toLowerCase())));
        panel.add(titles, BorderLayout.WEST);
        if (unit.type() == SyncUnitAuthorization.Type.MESSAGE_CHANNEL && !unit.pendingInvitation()) {
            JButton open = Theme.primaryButton("Open in chat");
            open.addActionListener(event -> openChannel.accept(unit.id()));
            panel.add(open, BorderLayout.EAST);
        }
        return panel;
    }

    private JPanel buildPendingInvitation(Node.SyncUnitStatus unit) {
        JPanel panel = Theme.card(Theme.GREEN_SOFT);
        panel.add(Theme.subheading("Invitation pending"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(Theme.muted("Enter the peer ID of a node hosting this SyncUnit invitation."));
        panel.add(Box.createVerticalStrut(6));
        JPanel row = Theme.row(6);
        row.setBackground(Theme.GREEN_SOFT);
        JTextField peer = Theme.textField(24);
        row.add(peer);
        JButton join = Theme.primaryButton("Join");
        join.addActionListener(event -> {
            try {
                node.joinSyncUnit(PeerId.fromBase58(peer.getText().trim()), unit.id(), user.userId());
                status.accept("Joined " + Theme.unitType(unit.type()));
                refresh();
            } catch (RuntimeException error) {
                status.accept("Could not join unit: " + error.getMessage());
            }
        });
        row.add(join);
        panel.add(row);
        return panel;
    }

    private JPanel buildChannel(Node.SyncUnitStatus unit) {
        JPanel panel = Theme.card();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Theme.subheading("Message channel"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(Theme.muted("<html><div style='width:330px'>Messages, attachments and membership are synchronized "
                + "with all channel members.</div></html>"));
        return panel;
    }

    private JPanel buildSharedFolder(Node.SyncUnitStatus unit) {
        JPanel panel = Theme.card();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Theme.subheading("Files"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(Theme.muted("This folder appears in the mounted root view under its label."));
        panel.add(Box.createVerticalStrut(6));

        JPanel labelRow = Theme.row(6);
        labelRow.setAlignmentX(LEFT_ALIGNMENT);
        labelRow.add(Theme.muted("Root label"));
        JTextField labelField = Theme.textField(14);
        node.listRootEntries(user.userId()).stream()
                .filter(entry -> entry.unitId().equals(unit.id()))
                .findFirst()
                .ifPresent(entry -> labelField.setText(entry.label()));
        labelRow.add(labelField);
        JButton saveLabel = Theme.secondaryButton("Save label");
        saveLabel.addActionListener(event -> {
            try {
                node.setSharedFolderLabel(user.userId(), unit.id(), labelField.getText().trim());
                status.accept("Shared folder label saved");
                refresh();
            } catch (RuntimeException error) {
                status.accept("Could not save label: " + error.getMessage());
            }
        });
        labelRow.add(saveLabel);
        panel.add(labelRow);
        panel.add(Box.createVerticalStrut(8));

        FileTableModel model = new FileTableModel();
        JTable table = new JTable(model);
        styleInnerTable(table);
        model.setInfos(node.listSharedFileInfos(unit.id(), user.userId()));
        JScrollPane scroll = Theme.scroll(table);
        scroll.setPreferredSize(new Dimension(500, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(8));

        JPanel upload = Theme.column();
        upload.setAlignmentX(LEFT_ALIGNMENT);
        JLabel uploadLabel = Theme.muted("Upload file");
        uploadLabel.setAlignmentX(LEFT_ALIGNMENT);
        upload.add(uploadLabel);
        JPanel uploadPathRow = Theme.row(6);
        uploadPathRow.setAlignmentX(LEFT_ALIGNMENT);
        JTextField localPath = Theme.textField(18);
        uploadPathRow.add(localPath);
        JButton browseUpload = Theme.secondaryButton("Browse...");
        browseUpload.addActionListener(event -> {
            Path selected = Theme.chooseOpenFile(localPath, "Select file to upload");
            if (selected != null)
                localPath.setText(selected.toString());
        });
        uploadPathRow.add(browseUpload);
        upload.add(uploadPathRow);
        JPanel uploadTargetRow = Theme.row(6);
        uploadTargetRow.setAlignmentX(LEFT_ALIGNMENT);
        uploadTargetRow.add(Theme.muted("Shared path"));
        JTextField sharedPath = Theme.textField(14);
        uploadTargetRow.add(sharedPath);
        JButton uploadButton = Theme.secondaryButton("Upload");
        uploadButton.addActionListener(event -> {
            try {
                Path file = Path.of(localPath.getText().trim());
                String path = sharedPath.getText().isBlank() ? file.getFileName().toString()
                        : sharedPath.getText().trim();
                node.putSharedFile(unit.id(), user.userId(), path, Files.readAllBytes(file));
                localPath.setText("");
                sharedPath.setText("");
                model.setInfos(node.listSharedFileInfos(unit.id(), user.userId()));
                status.accept("Uploaded " + path);
            } catch (Exception error) {
                status.accept("Could not upload file: " + error.getMessage());
            }
        });
        uploadTargetRow.add(uploadButton);
        upload.add(uploadTargetRow);
        panel.add(upload);
        panel.add(Box.createVerticalStrut(8));

        JPanel download = Theme.column();
        download.setAlignmentX(LEFT_ALIGNMENT);
        JLabel downloadLabel = Theme.muted("Download selected file");
        downloadLabel.setAlignmentX(LEFT_ALIGNMENT);
        download.add(downloadLabel);
        JPanel downloadRow = Theme.row(6);
        downloadRow.setAlignmentX(LEFT_ALIGNMENT);
        JTextField targetPath = Theme.textField(18);
        downloadRow.add(targetPath);
        JButton browseDownload = Theme.secondaryButton("Browse...");
        browseDownload.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.accept("Select a file first");
                return;
            }
            String path = model.paths.get(table.convertRowIndexToModel(row));
            Path selected = Theme.chooseSaveFile(targetPath, "Save shared file",
                    Path.of(path).getFileName().toString());
            if (selected != null)
                targetPath.setText(selected.toString());
        });
        downloadRow.add(browseDownload);
        JButton downloadButton = Theme.secondaryButton("Download");
        downloadButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.accept("Select a file first");
                return;
            }
            String path = model.paths.get(table.convertRowIndexToModel(row));
            try {
                Path target = targetPath.getText().isBlank()
                        ? Theme.chooseSaveFile(downloadButton, "Save shared file",
                                Path.of(path).getFileName().toString())
                        : Path.of(targetPath.getText());
                if (target == null)
                    return;
                Files.write(target, node.readSharedFile(unit.id(), user.userId(), path));
                status.accept("Saved " + target);
            } catch (Exception error) {
                status.accept("Could not download file: " + error.getMessage());
            }
        });
        downloadRow.add(downloadButton);
        JButton delete = Theme.dangerButton("Delete");
        delete.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.accept("Select a file first");
                return;
            }
            try {
                node.deleteSharedFile(unit.id(), user.userId(), model.paths.get(table.convertRowIndexToModel(row)));
                model.setInfos(node.listSharedFileInfos(unit.id(), user.userId()));
            } catch (RuntimeException error) {
                status.accept("Could not delete file: " + error.getMessage());
            }
        });
        downloadRow.add(delete);
        download.add(downloadRow);
        panel.add(download);
        return panel;
    }

    private JPanel buildTodoList(Node.SyncUnitStatus unit) {
        JPanel panel = Theme.card();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Theme.subheading("Todo items"));
        panel.add(Box.createVerticalStrut(6));

        TodoTableModel model = new TodoTableModel();
        JTable table = new JTable(model);
        styleInnerTable(table);
        model.setItems(node.listTodoItems(unit.id(), user.userId()));
        JScrollPane scroll = Theme.scroll(table);
        scroll.setPreferredSize(new Dimension(500, 160));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(8));

        JTextField itemId = Theme.textField(20);
        JTextArea content = Theme.textArea(3, 36);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0)
                return;
            String id = model.itemIds.get(table.convertRowIndexToModel(row));
            itemId.setText(id);
            content.setText(new String(model.items.get(id), StandardCharsets.UTF_8));
        });

        panel.add(Theme.formField("Item ID", itemId));
        panel.add(Box.createVerticalStrut(6));
        panel.add(Theme.formField("Content", Theme.scroll(content)));
        panel.add(Box.createVerticalStrut(6));
        JPanel actions = Theme.row(6);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        JButton save = Theme.primaryButton("Save item");
        save.addActionListener(event -> {
            if (itemId.getText().isBlank()) {
                status.accept("Enter an item ID first");
                return;
            }
            try {
                node.putTodoItem(unit.id(), user.userId(), itemId.getText().trim(),
                        content.getText().getBytes(StandardCharsets.UTF_8));
                model.setItems(node.listTodoItems(unit.id(), user.userId()));
                status.accept("Todo item saved");
            } catch (RuntimeException error) {
                status.accept("Could not save item: " + error.getMessage());
            }
        });
        actions.add(save);
        JButton delete = Theme.dangerButton("Delete item");
        delete.addActionListener(event -> {
            if (itemId.getText().isBlank())
                return;
            try {
                node.deleteTodoItem(unit.id(), user.userId(), itemId.getText().trim());
                itemId.setText("");
                content.setText("");
                model.setItems(node.listTodoItems(unit.id(), user.userId()));
            } catch (RuntimeException error) {
                status.accept("Could not delete item: " + error.getMessage());
            }
        });
        actions.add(delete);
        panel.add(actions);
        return panel;
    }

    private JPanel buildMembers(Node.SyncUnitStatus unit) {
        JPanel panel = Theme.card();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Theme.subheading("Members"));
        panel.add(Box.createVerticalStrut(6));

        MemberTableModel model = new MemberTableModel();
        JTable table = new JTable(model);
        styleInnerTable(table);
        model.setMembers(node.listSyncUnitMembers(unit.id()));
        JScrollPane scroll = Theme.scroll(table);
        scroll.setPreferredSize(new Dimension(500, 140));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(8));

        boolean canManage = unit.role() == SyncUnitAuthorization.Role.MANAGER;
        SyncUnitAuthorization.Role[] roles = rolesFor(unit.type());
        JComboBox<SyncUnitAuthorization.Role> roleBox = new JComboBox<>(roles);

        JPanel invite = Theme.column();
        invite.setAlignmentX(LEFT_ALIGNMENT);
        JLabel inviteLabel = Theme.muted("Invite a contact");
        inviteLabel.setAlignmentX(LEFT_ALIGNMENT);
        invite.add(inviteLabel);
        ContactPicker invitee = new ContactPicker(node, user, status);
        invite.add(invitee);
        JPanel inviteRow = Theme.row(6);
        inviteRow.add(roleBox);
        JButton inviteButton = Theme.secondaryButton("Invite");
        inviteButton.setEnabled(canManage);
        inviteButton.addActionListener(event -> {
            try {
                PubKey key = invitee.selectedUserId();
                if (key == null) {
                    status.accept("Select a contact with a linked user ID, or link a user key first");
                    return;
                }
                SyncUnitAuthorization.Role role = (SyncUnitAuthorization.Role) roleBox.getSelectedItem();
                node.inviteToSyncUnit(unit.id(), user.userId(), key, role);
                var messageType = switch (unit.type()) {
                    case SHARED_FOLDER -> Sync.UserMessage.Type.SHARED_FOLDER_INVITATION;
                    case TODO_LIST -> Sync.UserMessage.Type.TODO_LIST_INVITATION;
                    case MESSAGE_CHANNEL -> Sync.UserMessage.Type.MESSAGE_CHANNEL_INVITATION;
                    case USER -> null;
                };
                if (messageType != null)
                    node.getUserByName(user.localName()).sendMessage(key, messageType, "SyncUnit invitation",
                            "You were invited to " + Theme.unitType(unit.type()).toLowerCase() + ".", List.of(),
                            unit.id());
                invitee.clearSelection();
                model.setMembers(node.listSyncUnitMembers(unit.id()));
                refreshWithoutDetailReset();
                status.accept("Invitation sent");
            } catch (RuntimeException error) {
                status.accept("Could not invite user: " + error.getMessage());
            }
        });
        inviteRow.add(inviteButton);
        invite.add(inviteRow);
        panel.add(invite);
        panel.add(Box.createVerticalStrut(8));

        JPanel change = Theme.column();
        change.setAlignmentX(LEFT_ALIGNMENT);
        JLabel changeLabel = Theme.muted("Selected member");
        changeLabel.setAlignmentX(LEFT_ALIGNMENT);
        change.add(changeLabel);
        JPanel changeRow = Theme.row(6);
        JComboBox<SyncUnitAuthorization.Role> changeBox = new JComboBox<>(roles);
        changeRow.add(changeBox);
        JButton changeButton = Theme.secondaryButton("Change role");
        changeButton.setEnabled(canManage);
        changeButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.accept("Select a member first");
                return;
            }
            var member = model.members.get(table.convertRowIndexToModel(row));
            if (member.pendingInvitation()) {
                status.accept("Accept the invitation before changing its role");
                return;
            }
            try {
                node.changeSyncUnitRole(unit.id(), user.userId(), member.userId(),
                        (SyncUnitAuthorization.Role) changeBox.getSelectedItem());
                model.setMembers(node.listSyncUnitMembers(unit.id()));
                refreshWithoutDetailReset();
            } catch (RuntimeException error) {
                status.accept("Could not change role: " + error.getMessage());
            }
        });
        changeRow.add(changeButton);
        JButton remove = Theme.dangerButton("Remove");
        remove.setEnabled(canManage);
        remove.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.accept("Select a member first");
                return;
            }
            try {
                node.removeFromSyncUnit(unit.id(), user.userId(),
                        model.members.get(table.convertRowIndexToModel(row)).userId());
                model.setMembers(node.listSyncUnitMembers(unit.id()));
                refreshWithoutDetailReset();
            } catch (RuntimeException error) {
                status.accept("Could not remove member: " + error.getMessage());
            }
        });
        changeRow.add(remove);
        change.add(changeRow);
        panel.add(change);
        return panel;
    }

    private void refreshWithoutDetailReset() {
        List<Node.SyncUnitStatus> units = node.listSyncUnits(user.userId());
        unitModel.setUnits(units);
    }

    private static SyncUnitAuthorization.Role[] rolesFor(SyncUnitAuthorization.Type type) {
        return type == SyncUnitAuthorization.Type.MESSAGE_CHANNEL
                ? new SyncUnitAuthorization.Role[] { SyncUnitAuthorization.Role.MEMBER,
                        SyncUnitAuthorization.Role.MANAGER }
                : new SyncUnitAuthorization.Role[] { SyncUnitAuthorization.Role.VIEWER,
                        SyncUnitAuthorization.Role.EDITOR, SyncUnitAuthorization.Role.MANAGER };
    }

    private static void styleInnerTable(JTable table) {
        table.setRowHeight(30);
        table.setFont(Theme.BODY);
        table.setForeground(Theme.INK);
        table.setBackground(Theme.CARD);
        table.setGridColor(Theme.LINE);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(Theme.GREEN_SOFT);
        table.setSelectionForeground(Theme.INK);
        table.getTableHeader().setFont(Theme.SMALL.deriveFont(Font.BOLD));
        table.getTableHeader().setBackground(new Color(230, 237, 232));
        table.getTableHeader().setForeground(Theme.INK);
    }

    private final class UnitTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Unit", "Type", "Role", "Members" };
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
                case 0 -> Theme.shortId(unit.id());
                case 1 -> Theme.shortUnitType(unit.type());
                case 2 -> unit.pendingInvitation() ? "Invitation"
                        : unit.role() == null ? "-" : unit.role().name().toLowerCase();
                case 3 -> unit.memberCount();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class FileTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Path", "Size" };
        private List<Node.SharedFileInfo> files = List.of();
        List<String> paths = List.of();

        void setInfos(List<Node.SharedFileInfo> files) {
            this.files = files;
            paths = files.stream().map(Node.SharedFileInfo::path).toList();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return files.size();
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
            var file = files.get(row);
            return column == 0 ? file.path() : file.size();
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
            String id = itemIds.get(row);
            return column == 0 ? id : new String(items.get(id), StandardCharsets.UTF_8);
        }
    }

    private final class MemberTableModel extends AbstractTableModel {
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
                case 0 -> node.getUser(user.userId()).contactLabel(member.userId());
                case 1 -> member.role().name().toLowerCase();
                case 2 -> member.pendingInvitation() ? "Invited" : "Active";
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }
}
