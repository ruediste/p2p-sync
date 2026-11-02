package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeUserHandle;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;
import com.github.ruediste.p2psync.node.SyncUnitId;
import com.github.ruediste.p2psync.proto.Sync;

/** Traditional chat view with a conversation list on the left and bubbles on the right. */
final class ChatPanel extends JPanel {
    private final Node node;
    private final Consumer<String> status;
    private Node.UserStatus user;

    private final DefaultListModel<Conversation> conversationModel = new DefaultListModel<>();
    private final JList<Conversation> conversationList = new JList<>(conversationModel);
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JPanel messagesPanel = new JPanel();
    private final JScrollPane messagesScroll;
    private final JTextArea composer = Theme.textArea(3, 40);
    private final JPanel attachmentPanel = Theme.column();
    private final JButton sendButton = Theme.primaryButton("Send");
    private final ContactPicker contactPicker;

    private final List<Sync.SyncUnitAttachment> attachments = new ArrayList<>();
    private Conversation selected;
    private PubKey pendingContact;
    private boolean refreshing;

    ChatPanel(Node node, Node.UserStatus user, Consumer<String> status) {
        this.node = node;
        this.user = user;
        this.status = status;
        this.contactPicker = new ContactPicker(node, user, status);
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);
        setBorder(new EmptyBorder(16, 18, 16, 18));

        messagesScroll = Theme.scrollBorderless(messagesPanel);
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(Theme.PAPER);
        messagesPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildConversationPanel(), buildThreadPanel());
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(250);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildConversationPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                new EmptyBorder(14, 12, 14, 12)));

        JLabel heading = Theme.heading("Chats");
        heading.setBorder(new EmptyBorder(0, 4, 0, 0));
        panel.add(heading, BorderLayout.NORTH);

        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new ConversationRenderer());
        conversationList.setBackground(Theme.CARD);
        conversationList.setBorder(BorderFactory.createEmptyBorder());
        conversationList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            Conversation conversation = conversationList.getSelectedValue();
            if (conversation != null) {
                if (selected == null || !selected.key().equals(conversation.key()))
                    clearComposer();
                selected = conversation;
                pendingContact = conversation.channelId() == null ? conversation.contact() : null;
                renderThread();
            }
        });
        panel.add(Theme.scrollBorderless(conversationList), BorderLayout.CENTER);

        JPanel newChat = Theme.column();
        newChat.setBorder(new EmptyBorder(8, 0, 0, 0));
        newChat.setBackground(Theme.CARD);
        newChat.add(Theme.muted("New direct chat"));
        newChat.add(Box.createVerticalStrut(4));
        newChat.add(contactPicker);
        JButton start = Theme.secondaryButton("Start");
        start.setAlignmentX(Component.LEFT_ALIGNMENT);
        start.addActionListener(event -> startDirectChat());
        newChat.add(Box.createVerticalStrut(4));
        newChat.add(start);
        panel.add(newChat, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildThreadPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.PAPER);
        panel.setBorder(new EmptyBorder(0, 14, 0, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.PAPER);
        header.setBorder(new EmptyBorder(2, 4, 10, 4));
        titleLabel.setFont(Theme.HEADING);
        titleLabel.setForeground(Theme.INK);
        subtitleLabel.setFont(Theme.SMALL);
        subtitleLabel.setForeground(Theme.MUTED);
        JPanel titles = Theme.column();
        titles.setBackground(Theme.PAPER);
        titles.add(titleLabel);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subtitleLabel);
        header.add(titles, BorderLayout.WEST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(messagesScroll, BorderLayout.CENTER);
        panel.add(buildComposer(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildComposer() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBackground(Theme.PAPER);
        outer.setBorder(new EmptyBorder(10, 0, 0, 0));

        Theme.RoundedPanel bubble = new Theme.RoundedPanel(Theme.CARD, 14);
        bubble.setLayout(new BorderLayout(6, 6));
        bubble.setBorder(new EmptyBorder(10, 12, 10, 12));
        bubble.add(new JScrollPane(composer) {
            {
                setBorder(BorderFactory.createEmptyBorder());
                getViewport().setBackground(Theme.CARD);
            }
        }, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        attachmentPanel.setBackground(Theme.CARD);
        bottom.add(attachmentPanel, BorderLayout.NORTH);
        JPanel actions = Theme.row(6);
        actions.setBackground(Theme.CARD);
        JButton attach = Theme.ghostButton("Attach file...");
        attach.addActionListener(event -> attachFiles());
        actions.add(attach);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(sendButton);
        sendButton.addActionListener(event -> send());
        bottom.add(actions, BorderLayout.SOUTH);
        bubble.add(bottom, BorderLayout.SOUTH);
        outer.add(bubble, BorderLayout.CENTER);

        composer.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER && !event.isShiftDown()) {
                    event.consume();
                    send();
                }
            }
        });
        return outer;
    }

    void setUser(Node.UserStatus user) {
        boolean changed = this.user == null || !this.user.userId().equals(user.userId());
        this.user = user;
        if (changed) {
            selected = null;
            pendingContact = null;
            clearComposer();
        }
        contactPicker.setUser(user);
    }

    void selectChannel(SyncUnitId id) {
        pendingContact = null;
        selected = null;
        clearComposer();
        refresh();
        for (int index = 0; index < conversationModel.size(); index++) {
            Conversation conversation = conversationModel.get(index);
            if (id.equals(conversation.channelId())) {
                conversationList.setSelectedIndex(index);
                return;
            }
        }
    }

    void refresh() {
        String selectedKey = selected == null ? null : selected.key();
        if (pendingContact != null)
            selectedKey = directKey(pendingContact);
        List<Conversation> conversations = conversations();
        refreshing = true;
        try {
            conversationModel.clear();
            conversations.forEach(conversationModel::addElement);
        } finally {
            refreshing = false;
        }
        if (selectedKey != null) {
            for (int index = 0; index < conversationModel.size(); index++)
                if (conversationModel.get(index).key().equals(selectedKey)) {
                    conversationList.setSelectedIndex(index);
                    break;
                }
        }
        if (conversationList.getSelectedIndex() < 0 && !conversationModel.isEmpty())
            conversationList.setSelectedIndex(0);
        if (conversationModel.isEmpty()) {
            selected = null;
            renderThread();
        }
    }

    private List<Conversation> conversations() {
        NodeUserHandle handle = node.getUser(user.userId());
        List<Conversation> result = new ArrayList<>();
        for (var unit : node.listSyncUnits(user.userId())) {
            if (unit.type() == SyncUnitAuthorization.Type.MESSAGE_CHANNEL && !unit.pendingInvitation())
                result.add(new Conversation("channel:" + unit.id(),
                        "Channel " + Theme.shortId(unit.id()),
                        unit.memberCount() + " members", unit.id(), null));
        }
        Map<String, PubKey> contacts = new java.util.LinkedHashMap<>();
        for (var entry : node.getUserMessageLog(user.userId())) {
            if (entry.message().getType() != Sync.UserMessage.Type.CHAT)
                continue;
            PubKey contact = entry.outgoing()
                    ? PubKey.from(entry.message().getRecipientUserId().toByteArray())
                    : PubKey.from(entry.message().getSenderUserId().toByteArray());
            if (!contact.equals(user.userId()))
                contacts.putIfAbsent(HexFormat.of().formatHex(contact.bytes()), contact);
        }
        if (pendingContact != null)
            contacts.putIfAbsent(HexFormat.of().formatHex(pendingContact.bytes()), pendingContact);
        for (PubKey contact : contacts.values()) {
            String label = handle.contactLabel(contact);
            result.add(new Conversation(directKey(contact), label, "Direct chat", null, contact));
        }
        return result;
    }

    private static String directKey(PubKey contact) {
        return "direct:" + HexFormat.of().formatHex(contact.bytes());
    }

    private void renderThread() {
        Conversation conversation = selected;
        if (conversation == null) {
            titleLabel.setText("Chats");
            subtitleLabel.setText("Select a channel or start a direct chat");
            sendButton.setEnabled(false);
            composer.setEnabled(false);
            messagesPanel.removeAll();
            messagesPanel.add(Theme.emptyState("No conversation selected",
                    "Pick a conversation on the left, or start a direct chat with a contact."));
            messagesPanel.revalidate();
            messagesPanel.repaint();
            return;
        }
        titleLabel.setText(conversation.title());
        subtitleLabel.setText(conversation.subtitle());
        sendButton.setEnabled(true);
        composer.setEnabled(true);

        boolean atBottom = isAtBottom();
        messagesPanel.removeAll();
        if (conversation.channelId() != null) {
            for (var entry : node.getChannelMessages(conversation.channelId(), user.userId())) {
                boolean outgoing = entry.author().equals(user.userId());
                String sender = outgoing ? "You" : senderLabel(entry.author());
                addBubble(outgoing, sender, entry.message().getText(), entry.message().getAttachmentsList());
            }
        } else {
            for (var entry : node.getUserMessageLog(user.userId())) {
                if (entry.message().getType() != Sync.UserMessage.Type.CHAT)
                    continue;
                PubKey contact = entry.outgoing()
                        ? PubKey.from(entry.message().getRecipientUserId().toByteArray())
                        : PubKey.from(entry.message().getSenderUserId().toByteArray());
                if (!contact.equals(conversation.contact()))
                    continue;
                addBubble(entry.outgoing(), entry.outgoing() ? "You" : conversation.title(),
                        entry.message().getBody(), entry.message().getAttachmentsList());
            }
        }
        if (messagesPanel.getComponentCount() == 0)
            messagesPanel.add(Theme.emptyState("No messages yet", "Say hello to get the conversation started."));
        messagesPanel.add(Box.createVerticalStrut(4));
        messagesPanel.revalidate();
        messagesPanel.repaint();
        if (atBottom)
            SwingUtilities.invokeLater(this::scrollToBottom);
    }

    private void addBubble(boolean outgoing, String sender, String text, List<Sync.SyncUnitAttachment> attachments) {
        JPanel row = new JPanel(new FlowLayout(outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 3));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        Theme.RoundedPanel bubble = new Theme.RoundedPanel(
                outgoing ? Theme.OUTGOING_BUBBLE : Theme.INCOMING_BUBBLE, 14);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel meta = new JLabel(sender);
        meta.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        meta.setForeground(outgoing ? Theme.GREEN_DARK : Theme.MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(meta);
        if (!text.isBlank()) {
            JTextArea body = new JTextArea(text);
            body.setFont(Theme.BODY);
            body.setForeground(Theme.INK);
            body.setOpaque(false);
            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setColumns(Math.min(46, Math.max(16, longestLine(text))));
            body.setBorder(new EmptyBorder(3, 0, 2, 0));
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(body);
        }
        if (!attachments.isEmpty())
            bubble.add(Theme.attachmentList(attachments, status));
        row.add(bubble);
        messagesPanel.add(row);
    }

    private String senderLabel(PubKey key) {
        return node.getUser(user.userId()).contactLabel(key);
    }

    private static int longestLine(String text) {
        int longest = 0;
        for (String line : text.split("\n", -1))
            longest = Math.max(longest, line.length());
        return longest;
    }

    private boolean isAtBottom() {
        JScrollBar bar = messagesScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 24;
    }

    private void scrollToBottom() {
        JScrollBar bar = messagesScroll.getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
    }

    private void startDirectChat() {
        PubKey contact = contactPicker.selectedUserId();
        if (contact == null) {
            status.accept("Select a contact with a linked user ID, or link a user ID first");
            return;
        }
        if (contact.equals(user.userId())) {
            status.accept("A direct chat needs another user");
            return;
        }
        clearComposer();
        pendingContact = contact;
        selected = null;
        refresh();
        status.accept("Started a new direct chat");
    }

    private void clearComposer() {
        composer.setText("");
        attachments.clear();
        renderAttachmentChips();
    }

    private void attachFiles() {
        var paths = Theme.chooseOpenFiles(composer, "Attach files");
        if (paths.isEmpty())
            return;
        Conversation conversation = selected;
        Background.run("attach files", () -> {
            List<Sync.SyncUnitAttachment> loaded = new ArrayList<>();
            for (Path path : paths)
                loaded.add(Theme.readAttachment(path));
            return loaded;
        }, loaded -> {
            if (!isCurrent(conversation))
                return;
            attachments.addAll(loaded);
            renderAttachmentChips();
            status.accept("Attached " + loaded.size() + (loaded.size() == 1 ? " file" : " files"));
        }, error -> status.accept("Could not attach file: " + error.getMessage()));
    }

    private boolean isCurrent(Conversation conversation) {
        if (conversation == null)
            return selected == null;
        return selected != null && selected.key().equals(conversation.key());
    }

    private void renderAttachmentChips() {
        attachmentPanel.removeAll();
        for (var attachment : attachments) {
            JPanel chip = Theme.row(6);
            chip.setBackground(Theme.CARD);
            JLabel name = Theme.muted(attachment.getName());
            chip.add(name);
            JButton remove = Theme.ghostButton("x");
            int index = attachments.indexOf(attachment);
            remove.addActionListener(event -> {
                attachments.remove(index);
                renderAttachmentChips();
            });
            chip.add(remove);
            attachmentPanel.add(chip);
        }
        attachmentPanel.revalidate();
        attachmentPanel.repaint();
    }

    private void send() {
        if (selected == null)
            return;
        String text = composer.getText();
        if (text.isBlank() && attachments.isEmpty())
            return;
        Conversation conversation = selected;
        List<Sync.SyncUnitAttachment> toSend = List.copyOf(attachments);
        PubKey userId = user.userId();
        Background.run("send message", () -> {
            if (conversation.channelId() != null) {
                var message = Sync.SyncUnitMessage.newBuilder().setText(text);
                toSend.forEach(message::addAttachments);
                node.sendChannelMessage(conversation.channelId(), userId, message.build());
            } else {
                node.getUser(userId).sendChatMessage(conversation.contact(), text, toSend);
            }
            return null;
        }, ignored -> {
            if (isCurrent(conversation)) {
                composer.setText("");
                attachments.clear();
                renderAttachmentChips();
                if (conversation.channelId() == null)
                    pendingContact = conversation.contact();
                SwingUtilities.invokeLater(this::scrollToBottom);
            }
            refresh();
            status.accept("Message sent");
        }, error -> status.accept("Could not send message: " + error.getMessage()));
    }

    private record Conversation(String key, String title, String subtitle, SyncUnitId channelId, PubKey contact) {
    }

    private final class ConversationRenderer extends JPanel implements ListCellRenderer<Conversation> {
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();

        ConversationRenderer() {
            setLayout(new BorderLayout(0, 2));
            setBorder(new EmptyBorder(9, 10, 9, 10));
            title.setFont(Theme.SUBHEADING);
            subtitle.setFont(Theme.SMALL);
            add(title, BorderLayout.NORTH);
            add(subtitle, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Conversation> list, Conversation value,
                int index, boolean isSelected, boolean cellHasFocus) {
            title.setText(value.title());
            subtitle.setText(value.subtitle());
            boolean active = isSelected;
            setBackground(active ? Theme.GREEN_SOFT : Theme.CARD);
            title.setForeground(active ? Theme.GREEN_DARK : Theme.INK);
            subtitle.setForeground(active ? Theme.GREEN_DARK : Theme.MUTED);
            setPreferredSize(new Dimension(220, 52));
            return this;
        }
    }
}
