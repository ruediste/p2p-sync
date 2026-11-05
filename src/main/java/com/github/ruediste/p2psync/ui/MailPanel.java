package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitId;
import com.github.ruediste.p2psync.proto.Sync;

/**
 * Email-client style mail view: folders, message list, reading and compose
 * pane.
 */
final class MailPanel extends JPanel {
    private enum Folder {
        INBOX("Inbox"),
        SENT("Sent");

        final String label;

        Folder(String label) {
            this.label = label;
        }
    }

    private final Node node;
    private final Consumer<String> status;
    private Node.UserStatus user;

    private final DefaultListModel<Folder> folderModel = new DefaultListModel<>();
    private final JList<Folder> folderList = new JList<>(folderModel);
    private final DefaultListModel<MailRow> mailModel = new DefaultListModel<>();
    private final JList<MailRow> mailList = new JList<>(mailModel);
    private final JPanel detail = new JPanel(new BorderLayout());
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailMeta = new JLabel();

    private final ContactPicker recipientPicker;
    private final JTextField composeSubject = Theme.textField(26);
    private final JTextArea composeBody = Theme.textArea(12, 48);
    private final JPanel composeAttachments = Theme.column();
    private final List<Sync.SyncUnitAttachmentProto> attachments = new ArrayList<>();

    private Folder folder = Folder.INBOX;
    private boolean composing;
    private boolean refreshing;
    private String pendingSelectionId;

    MailPanel(Node node, Node.UserStatus user, Consumer<String> status) {
        folderModel.addElement(Folder.INBOX);
        folderModel.addElement(Folder.SENT);
        this.node = node;
        this.user = user;
        this.status = status;
        this.recipientPicker = new ContactPicker(node, user, status);
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);
        setBorder(new EmptyBorder(16, 18, 16, 18));

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildMessageList(), buildDetail());
        right.setBorder(BorderFactory.createEmptyBorder());
        right.setDividerLocation(370);
        right.setResizeWeight(0.25);
        right.setContinuousLayout(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFolderPanel(), right);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(145);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildFolderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                new EmptyBorder(14, 10, 14, 10)));

        JLabel heading = Theme.heading("Mail");
        heading.setBorder(new EmptyBorder(0, 4, 0, 0));
        panel.add(heading, BorderLayout.NORTH);

        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        folderList.setSelectedIndex(0);
        folderList.setCellRenderer(new FolderRenderer());
        folderList.setBackground(Theme.CARD);
        folderList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting() || folderList.getSelectedValue() == null)
                return;
            Folder selectedFolder = folderList.getSelectedValue();
            if (selectedFolder == folder)
                return;
            if (!confirmDiscardDraft()) {
                refreshing = true;
                try {
                    folderList.setSelectedValue(folder, false);
                } finally {
                    refreshing = false;
                }
                return;
            }
            folder = selectedFolder;
            composing = false;
            refresh();
        });
        panel.add(folderList, BorderLayout.CENTER);

        JButton compose = Theme.primaryButton("Compose");
        compose.addActionListener(event -> {
            if (confirmDiscardDraft())
                showCompose(null, "", "");
        });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(compose, BorderLayout.NORTH);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMessageList() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CARD);
        panel.setBorder(new EmptyBorder(0, 12, 0, 0));

        mailList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailList.setFixedCellHeight(64);
        mailList.setCellRenderer(new MailRenderer());
        mailList.setBackground(Theme.CARD);
        mailList.setBorder(BorderFactory.createEmptyBorder());
        mailList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            MailRow row = mailList.getSelectedValue();
            if (row != null) {
                if (!confirmDiscardDraft()) {
                    refreshing = true;
                    try {
                        mailList.clearSelection();
                    } finally {
                        refreshing = false;
                    }
                    return;
                }
                composing = false;
                showReading(row);
            }
        });
        JScrollPane scroll = Theme.scroll(mailList);
        scroll.getViewport().setBackground(Theme.CARD);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDetail() {
        detail.setBackground(Theme.PAPER);
        detail.setBorder(new EmptyBorder(0, 14, 0, 0));
        return detail;
    }

    private boolean confirmDiscardDraft() {
        if (!composing)
            return true;
        int choice = JOptionPane.showConfirmDialog(this, "Discard the current draft?", "Discard draft",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    void setUser(Node.UserStatus user) {
        boolean changed = this.user == null || !this.user.userId().equals(user.userId());
        this.user = user;
        if (changed) {
            composing = false;
            pendingSelectionId = null;
        }
        recipientPicker.setUser(user);
    }

    void refresh() {
        if (composing) {
            refreshFolderCounts();
            return;
        }
        String selectedId = pendingSelectionId;
        MailRow selectedRow = mailList.getSelectedValue();
        if (selectedId == null && selectedRow != null)
            selectedId = selectedRow.messageId();
        pendingSelectionId = null;

        var entries = node.getUserMessageLog(user.userId()).stream()
                .filter(entry -> entry.message().getType() != Sync.UserMessageProto.Type.CHAT)
                .filter(entry -> folder == Folder.INBOX ? !entry.outgoing() : entry.outgoing())
                .sorted((left, right) -> {
                    int clockOrder = Long.compare(right.lamportClock(), left.lamportClock());
                    return clockOrder != 0 ? clockOrder
                            : right.message().getMessageId().compareTo(left.message().getMessageId());
                })
                .map(MailRow::new)
                .toList();

        refreshing = true;
        try {
            mailModel.clear();
            entries.forEach(mailModel::addElement);
        } finally {
            refreshing = false;
        }
        refreshFolderCounts();
        int target = -1;
        if (selectedId != null) {
            for (int index = 0; index < mailModel.size(); index++)
                if (mailModel.get(index).messageId().equals(selectedId)) {
                    target = index;
                    break;
                }
        }
        if (target < 0 && !entries.isEmpty())
            target = 0;
        if (target >= 0) {
            mailList.setSelectedIndex(target);
            showReading(mailModel.get(target));
        } else {
            mailList.clearSelection();
            showEmpty();
        }
    }

    private void refreshFolderCounts() {
        int inbox = 0;
        int sent = 0;
        for (var entry : node.getUserMessageLog(user.userId())) {
            if (entry.message().getType() == Sync.UserMessageProto.Type.CHAT)
                continue;
            if (entry.outgoing())
                sent++;
            else
                inbox++;
        }
        folderList.putClientProperty("inboxCount", inbox);
        folderList.putClientProperty("sentCount", sent);
        refreshing = true;
        try {
            folderList.setSelectedValue(folder, false);
        } finally {
            refreshing = false;
        }
        folderList.repaint();
    }

    private void showEmpty() {
        composing = false;
        detail.removeAll();
        detail.add(Theme.emptyState("No message selected", "Choose a message from the list or compose a new mail."),
                BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private void showReading(MailRow row) {
        composing = false;
        var message = row.entry().message();
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setBackground(Theme.PAPER);

        JPanel header = new JPanel();
        header.setBackground(Theme.PAPER);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel subject = Theme.heading(message.getSubject().isBlank() ? "(no subject)" : message.getSubject());
        subject.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(subject);
        header.add(Box.createVerticalStrut(4));
        JLabel meta = Theme.muted("From " + contactLabel(UserId.fromBytes(message.getSenderUserId().toByteArray()))
                + "  ·  To " + contactLabel(UserId.fromBytes(message.getRecipientUserId().toByteArray()))
                + "  ·  " + typeLabel(message.getType())
                + (row.entry().outgoing() ? "  ·  Sent" : ""));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(meta);
        header.add(Box.createVerticalStrut(2));
        JLabel id = Theme.mono(message.getMessageId());
        id.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(id);
        view.add(header, BorderLayout.NORTH);

        JPanel body = Theme.column();
        body.setBackground(Theme.PAPER);
        JTextArea text = Theme.textArea(10, 52);
        text.setText(message.getBody());
        text.setEditable(false);
        text.setBackground(Theme.CARD);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(Theme.scroll(text));
        if (message.getAttachmentsCount() > 0) {
            body.add(Box.createVerticalStrut(8));
            body.add(Theme.attachmentList(message.getAttachmentsList(), status));
        }
        if (message.getInvitedSyncUnitId().size() == SyncUnitId.LENGTH) {
            body.add(Box.createVerticalStrut(10));
            body.add(InvitationAcceptance.build(node, user,
                    SyncUnitId.fromBytes(message.getInvitedSyncUnitId().toByteArray()), status, this::refresh));
        }
        view.add(body, BorderLayout.CENTER);

        JPanel actions = Theme.row(8);
        actions.setBackground(Theme.PAPER);
        JButton reply = Theme.secondaryButton("Reply");
        reply.setEnabled(!row.entry().outgoing());
        reply.addActionListener(event -> {
            UserId sender = UserId.fromBytes(message.getSenderUserId().toByteArray());
            String replySubject = message.getSubject().isBlank() ? "" : "Re: " + message.getSubject();
            showCompose(sender, replySubject, "");
        });
        actions.add(reply);
        JButton refresh = Theme.ghostButton("Refresh");
        refresh.addActionListener(event -> refresh());
        actions.add(refresh);
        view.add(actions, BorderLayout.SOUTH);

        detail.removeAll();
        detail.add(view, BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private void showCompose(UserId recipient, String subject, String body) {
        composing = true;
        mailList.clearSelection();
        recipientPicker.setSelectedUserId(recipient);
        composeSubject.setText(subject);
        composeBody.setText(body);
        attachments.clear();
        renderAttachmentChips();
        detailTitle.setText("New mail");
        detailMeta.setText("Select a linked contact");
        recipientPicker.requestFocusInWindow();
        renderCompose();
    }

    private void renderCompose() {
        JPanel view = new JPanel(new BorderLayout(0, 10));
        view.setBackground(Theme.PAPER);

        JPanel header = new JPanel();
        header.setBackground(Theme.PAPER);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        detailTitle.setFont(Theme.HEADING);
        detailTitle.setForeground(Theme.INK);
        detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailMeta.setFont(Theme.SMALL);
        detailMeta.setForeground(Theme.MUTED);
        detailMeta.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(detailTitle);
        header.add(Box.createVerticalStrut(2));
        header.add(detailMeta);
        view.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setBackground(Theme.PAPER);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Theme.formField("Recipient", recipientPicker));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Subject", composeSubject));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Message", Theme.scroll(composeBody)));
        form.add(Box.createVerticalStrut(8));
        JPanel attachRow = Theme.row(6);
        JButton attach = Theme.ghostButton("Attach file...");
        attach.addActionListener(event -> attachFiles());
        attachRow.add(attach);
        form.add(attachRow);
        form.add(composeAttachments);
        view.add(form, BorderLayout.CENTER);

        JPanel actions = Theme.row(8);
        actions.setBackground(Theme.PAPER);
        JButton send = Theme.primaryButton("Send");
        send.addActionListener(event -> send());
        actions.add(send);
        JButton cancel = Theme.ghostButton("Cancel");
        cancel.addActionListener(event -> {
            composing = false;
            refresh();
        });
        actions.add(cancel);
        view.add(actions, BorderLayout.SOUTH);

        detail.removeAll();
        detail.add(view, BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private void attachFiles() {
        var paths = Theme.chooseOpenFiles(composeBody, "Attach files");
        if (paths.isEmpty())
            return;
        Background.run("attach files", () -> {
            List<Sync.SyncUnitAttachmentProto> loaded = new ArrayList<>();
            for (Path path : paths)
                loaded.add(Theme.readAttachment(path));
            return loaded;
        }, loaded -> {
            attachments.addAll(loaded);
            renderAttachmentChips();
            status.accept("Attached " + loaded.size() + (loaded.size() == 1 ? " file" : " files"));
        }, error -> status.accept("Could not attach file: " + error.getMessage()));
    }

    private void renderAttachmentChips() {
        composeAttachments.removeAll();
        for (var attachment : attachments) {
            JPanel chip = Theme.row(6);
            chip.add(Theme.muted(attachment.getName()));
            JButton remove = Theme.ghostButton("x");
            int index = attachments.indexOf(attachment);
            remove.addActionListener(event -> {
                attachments.remove(index);
                renderAttachmentChips();
            });
            chip.add(remove);
            composeAttachments.add(chip);
        }
        composeAttachments.revalidate();
        composeAttachments.repaint();
    }

    private void send() {
        UserId recipient = recipientPicker.selectedUserId();
        if (recipient == null) {
            status.accept("Select a contact with a linked user ID, or link a user ID first");
            return;
        }
        UserId userId = user.userId();
        String subject = composeSubject.getText();
        String body = composeBody.getText();
        List<Sync.SyncUnitAttachmentProto> toSend = List.copyOf(attachments);
        Background.run("send mail", () -> node.getUser(userId).sendMailMessage(recipient, subject, body, toSend),
                messageId -> {
                    composing = false;
                    pendingSelectionId = messageId;
                    folder = Folder.SENT;
                    folderList.setSelectedValue(Folder.SENT, true);
                    composeSubject.setText("");
                    composeBody.setText("");
                    attachments.clear();
                    renderAttachmentChips();
                    refresh();
                    status.accept("Mail sent");
                }, error -> status.accept("Could not send mail: " + error.getMessage()));
    }

    private String contactLabel(UserId key) {
        return node.getUser(user.userId()).contactLabel(key);
    }

    private static String typeLabel(Sync.UserMessageProto.Type type) {
        return switch (type) {
            case MAIL -> "Mail";
            case SHARED_FOLDER_INVITATION -> "Folder invitation";
            case MESSAGE_CHANNEL_INVITATION -> "Channel invitation";
            case TODO_LIST_INVITATION -> "Todo invitation";
            case CHAT -> "Chat";
            default -> "Message";
        };
    }

    private record MailRow(Node.UserMessageEntry entry) {
        String messageId() {
            return entry.message().getMessageId();
        }

        String subject() {
            return entry.message().getSubject().isBlank() ? "(no subject)" : entry.message().getSubject();
        }

        String preview() {
            String body = entry.message().getBody().replace('\n', ' ').trim();
            return body.length() > 90 ? body.substring(0, 90) + "…" : body;
        }
    }

    private final class MailRenderer extends JPanel implements ListCellRenderer<MailRow> {
        private final JLabel top = new JLabel();
        private final JLabel subject = new JLabel();
        private final JLabel preview = new JLabel();

        MailRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            top.setFont(Theme.SMALL);
            subject.setFont(Theme.BODY.deriveFont(Font.BOLD));
            preview.setFont(Theme.SMALL);
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            subject.setAlignmentX(Component.LEFT_ALIGNMENT);
            preview.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(top);
            add(Box.createVerticalStrut(2));
            add(subject);
            add(Box.createVerticalStrut(2));
            add(preview);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MailRow> list, MailRow value, int index,
                boolean isSelected, boolean cellHasFocus) {
            var message = value.entry().message();
            UserId counterpart = value.entry().outgoing()
                    ? UserId.fromBytes(message.getRecipientUserId().toByteArray())
                    : UserId.fromBytes(message.getSenderUserId().toByteArray());
            top.setText(contactLabel(counterpart) + "  ·  " + typeLabel(message.getType()));
            subject.setText(value.subject());
            preview.setText(value.preview());
            setBackground(isSelected ? Theme.GREEN_SOFT : Theme.CARD);
            top.setForeground(isSelected ? Theme.GREEN_DARK : Theme.MUTED);
            subject.setForeground(isSelected ? Theme.GREEN_DARK : Theme.INK);
            preview.setForeground(isSelected ? Theme.GREEN_DARK : Theme.MUTED);
            setPreferredSize(new Dimension(280, 64));
            return this;
        }
    }

    private final class FolderRenderer extends JLabel implements ListCellRenderer<Folder> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Folder> list, Folder value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Integer stored = switch (value) {
                case INBOX -> (Integer) list.getClientProperty("inboxCount");
                case SENT -> (Integer) list.getClientProperty("sentCount");
            };
            int count = stored == null ? 0 : stored;
            setText(value.label + "  " + count);
            setFont(Theme.SUBHEADING);
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(true);
            setBackground(isSelected ? Theme.GREEN_SOFT : Theme.CARD);
            setForeground(isSelected ? Theme.GREEN_DARK : Theme.INK);
            return this;
        }
    }
}
