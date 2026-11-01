package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Contact;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeUserHandle;

final class ContactPicker extends JPanel {
    private record Option(String label, Contact contact, PubKey userId, boolean usable) {
    }

    private final Node node;
    private final Consumer<String> status;
    private Node.UserStatus user;
    private final JTextField search = Theme.textField(14);
    private final JComboBox<Option> matches = new JComboBox<>();
    private final JTextField linkKey = Theme.textField(12);
    private boolean refreshing;

    ContactPicker(Node node, Node.UserStatus user, Consumer<String> status) {
        this.node = node;
        this.user = user;
        this.status = status;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel row = Theme.row(6);
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(search);
        matches.setRenderer(new OptionRenderer());
        matches.setPrototypeDisplayValue(new Option("A reasonably long contact name", null, null, false));
        row.add(matches);
        add(row);

        add(Box.createVerticalStrut(4));
        JPanel link = Theme.row(6);
        link.setOpaque(false);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.add(Theme.muted("Link key"));
        link.add(linkKey);
        JButton linkButton = Theme.secondaryButton("Add");
        linkButton.addActionListener(event -> linkKey());
        link.add(linkButton);
        add(link);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuild();
            }
        });
        rebuild();
    }

    void setUser(Node.UserStatus user) {
        this.user = user;
        rebuild();
    }

    void refresh() {
        rebuild();
    }

    Contact selectedContact() {
        Option option = (Option) matches.getSelectedItem();
        return option == null ? null : option.contact();
    }

    PubKey selectedUserId() {
        Option option = (Option) matches.getSelectedItem();
        return option == null ? null : option.userId();
    }

    void setSelectedUserId(PubKey userId) {
        if (userId == null) {
            clearSelection();
            return;
        }
        for (int index = 0; index < matches.getItemCount(); index++) {
            Option option = matches.getItemAt(index);
            if (userId.equals(option.userId())) {
                matches.setSelectedIndex(index);
                return;
            }
        }
        linkKey.setText(HexFormat.of().formatHex(userId.bytes()));
        status.accept("No contact for this user yet. Add it with the Link key button.");
    }

    void clearSelection() {
        search.setText("");
        linkKey.setText("");
        rebuild();
        if (matches.getItemCount() > 0)
            matches.setSelectedIndex(0);
    }

    private void linkKey() {
        try {
            PubKey key = PubKey.from(HexFormat.of().parseHex(linkKey.getText().trim()));
            if (key.equals(user.userId())) {
                status.accept("A contact cannot reference the owner");
                return;
            }
            NodeUserHandle handle = node.getUser(user.userId());
            handle.createContactFromUser(key);
            linkKey.setText("");
            rebuild();
            setSelectedUserId(key);
            status.accept("Contact linked");
        } catch (RuntimeException error) {
            status.accept("Could not link contact: " + error.getMessage());
        }
    }

    private void rebuild() {
        if (refreshing)
            return;
        PubKey selected = selectedUserId();
        refreshing = true;
        try {
            List<Option> options = options();
            matches.setModel(new DefaultComboBoxModel<>(options.toArray(Option[]::new)));
            if (selected != null) {
                for (int index = 0; index < matches.getItemCount(); index++) {
                    if (selected.equals(matches.getItemAt(index).userId())) {
                        matches.setSelectedIndex(index);
                        break;
                    }
                }
            }
        } finally {
            refreshing = false;
        }
    }

    private List<Option> options() {
        if (user == null)
            return List.of();
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        NodeUserHandle handle = node.getUser(user.userId());
        List<Option> result = new ArrayList<>();
        Set<PubKey> contactUsers = new HashSet<>();
        for (Contact contact : handle.listContacts()) {
            if (!query.isEmpty() && !contact.searchText().contains(query))
                continue;
            if (contact.userId() != null)
                contactUsers.add(contact.userId());
            String label = contact.displayName() + (contact.userId() == null ? "  (no user ID)" : "");
            result.add(new Option(label, contact, contact.userId(), contact.userId() != null));
        }
        for (Node.UserStatus local : node.listUsers()) {
            if (local.userId().equals(user.userId()) || contactUsers.contains(local.userId()))
                continue;
            if (!query.isEmpty() && !local.localName().toLowerCase(Locale.ROOT).contains(query))
                continue;
            result.add(new Option(local.localName() + "  (local user)", null, local.userId(), true));
        }
        return result;
    }

    private final class OptionRenderer extends JPanel implements ListCellRenderer<Option> {
        private final JLabel label = new JLabel();

        OptionRenderer() {
            setLayout(new BorderLayout());
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Option> list, Option value, int index,
                boolean isSelected, boolean cellHasFocus) {
            label.setText(value == null ? "" : value.label());
            label.setFont(Theme.BODY);
            label.setForeground(value != null && !value.usable() ? Theme.MUTED : Theme.INK);
            setBackground(isSelected ? Theme.GREEN_SOFT : Theme.CARD);
            return this;
        }
    }
}
