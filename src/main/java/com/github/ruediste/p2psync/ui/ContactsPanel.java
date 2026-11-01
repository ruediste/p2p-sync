package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HexFormat;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Contact;
import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.NodeUserHandle;

final class ContactsPanel extends JPanel {
    private final Node node;
    private final Consumer<String> status;
    private Node.UserStatus user;

    private final JTextField search = Theme.textField(20);
    private final DefaultListModel<Contact> contactModel = new DefaultListModel<>();
    private final JList<Contact> contactList = new JList<>(contactModel);
    private final JPanel detail = new JPanel(new BorderLayout());
    private final JTextField userId = Theme.textField(40);
    private final JTextField firstName = Theme.textField(22);
    private final JTextField lastName = Theme.textField(22);
    private final JTextField email = Theme.textField(22);
    private final JTextField phone = Theme.textField(22);
    private final JTextArea notes = Theme.textArea(4, 30);

    private String selectedContactId;
    private boolean editingNew;
    private boolean refreshing;

    ContactsPanel(Node node, Node.UserStatus user, Consumer<String> status) {
        this.node = node;
        this.user = user;
        this.status = status;
        setLayout(new BorderLayout());
        setBackground(Theme.PAPER);
        setBorder(new EmptyBorder(16, 18, 16, 18));
        add(buildHeader(), BorderLayout.NORTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildContactList(), buildDetail());
        split.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        split.setDividerLocation(330);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Theme.PAPER);
        JPanel titles = Theme.column();
        titles.setBackground(Theme.PAPER);
        titles.add(Theme.heading("Contacts"));
        titles.add(Box.createVerticalStrut(3));
        titles.add(Theme.muted("Your replicated address book"));
        header.add(titles, BorderLayout.WEST);

        JPanel actions = Theme.row(7);
        actions.setBackground(Theme.PAPER);
        actions.add(search);
        JButton newContact = Theme.primaryButton("New contact");
        newContact.addActionListener(event -> startNew());
        actions.add(newContact);
        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> refresh());
        actions.add(refresh);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContactList() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.PAPER);
        panel.setBorder(new EmptyBorder(0, 0, 0, 12));
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.setCellRenderer(new ContactRenderer());
        contactList.setBackground(Theme.CARD);
        contactList.setFixedCellHeight(56);
        contactList.setBorder(BorderFactory.createEmptyBorder());
        contactList.addListSelectionListener(event -> {
            if (refreshing || event.getValueIsAdjusting())
                return;
            Contact contact = contactList.getSelectedValue();
            if (contact != null && !contact.contactId().equals(selectedContactId)) {
                selectedContactId = contact.contactId();
                showContact(contact);
            }
        });
        JScrollPane scroll = Theme.scroll(contactList);
        scroll.getViewport().setBackground(Theme.CARD);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDetail() {
        detail.setBackground(Theme.PAPER);
        detail.setBorder(new EmptyBorder(0, 12, 0, 0));
        return detail;
    }

    void setUser(Node.UserStatus user) {
        this.user = user;
        refresh();
    }

    void refresh() {
        if (user == null)
            return;
        NodeUserHandle handle = node.getUser(user.userId());
        java.util.List<Contact> contacts = handle.searchContacts(search.getText());
        refreshing = true;
        try {
            contactModel.clear();
            contacts.forEach(contactModel::addElement);
        } finally {
            refreshing = false;
        }
        if (editingNew)
            return;
        if (selectedContactId != null) {
            for (int index = 0; index < contactModel.size(); index++) {
                if (contactModel.get(index).contactId().equals(selectedContactId)) {
                    refreshing = true;
                    try {
                        contactList.setSelectedIndex(index);
                    } finally {
                        refreshing = false;
                    }
                    showContact(contactModel.get(index));
                    return;
                }
            }
        }
        if (!contactModel.isEmpty()) {
            contactList.setSelectedIndex(0);
        } else if (search.getText() == null || search.getText().isBlank()) {
            startNew();
        } else {
            selectedContactId = null;
            detail.removeAll();
            detail.add(Theme.emptyState("No matching contacts", "Change the search or create a new contact."),
                    BorderLayout.CENTER);
            detail.revalidate();
            detail.repaint();
        }
    }

    private void startNew() {
        selectedContactId = null;
        editingNew = true;
        contactList.clearSelection();
        userId.setText("");
        firstName.setText("");
        lastName.setText("");
        email.setText("");
        phone.setText("");
        notes.setText("");
        renderDetail("New contact", "All fields are optional, but at least one must be filled.", false);
    }

    private void showContact(Contact contact) {
        selectedContactId = contact.contactId();
        editingNew = false;
        userId.setText(contact.userId() == null ? "" : HexFormat.of().formatHex(contact.userId().bytes()));
        firstName.setText(contact.firstName());
        lastName.setText(contact.lastName());
        email.setText(contact.email());
        phone.setText(contact.phone());
        notes.setText(contact.notes());
        renderDetail("Edit contact", "Contact ID " + contact.contactId().substring(0, 8), true);
    }

    private void renderDetail(String title, String subtitle, boolean existing) {
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setBackground(Theme.PAPER);
        JPanel header = Theme.column();
        header.setBackground(Theme.PAPER);
        header.add(Theme.heading(title));
        header.add(Box.createVerticalStrut(3));
        header.add(Theme.muted(subtitle));
        view.add(header, BorderLayout.NORTH);

        JPanel form = Theme.column();
        form.setBackground(Theme.PAPER);
        form.add(Theme.formField("User key (hex, optional)", userId));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("First name", firstName));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Last name", lastName));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Email", email));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Phone number", phone));
        form.add(Box.createVerticalStrut(8));
        form.add(Theme.formField("Notes", Theme.scroll(notes)));
        view.add(form, BorderLayout.CENTER);

        JPanel actions = Theme.row(8);
        actions.setBackground(Theme.PAPER);
        JButton save = Theme.primaryButton(existing ? "Save changes" : "Create contact");
        save.addActionListener(event -> save(existing));
        actions.add(save);
        JButton delete = Theme.dangerButton("Delete");
        delete.setEnabled(existing);
        delete.addActionListener(event -> delete());
        actions.add(delete);
        view.add(actions, BorderLayout.SOUTH);

        detail.removeAll();
        detail.add(view, BorderLayout.CENTER);
        detail.revalidate();
        detail.repaint();
    }

    private PubKey parseUserId() {
        String text = userId.getText().trim();
        if (text.isEmpty())
            return null;
        return PubKey.from(HexFormat.of().parseHex(text));
    }

    private void save(boolean existing) {
        try {
            PubKey linked = parseUserId();
            NodeUserHandle handle = node.getUser(user.userId());
            Contact contact;
            if (existing && selectedContactId != null) {
                contact = handle.updateContact(selectedContactId, linked, firstName.getText(), lastName.getText(),
                        email.getText(), phone.getText(), notes.getText());
            } else {
                contact = handle.createContact(linked, firstName.getText(), lastName.getText(), email.getText(),
                        phone.getText(), notes.getText());
            }
            selectedContactId = contact.contactId();
            editingNew = false;
            status.accept(existing ? "Contact saved" : "Contact created");
            refresh();
        } catch (RuntimeException error) {
            status.accept("Could not save contact: " + error.getMessage());
        }
    }

    private void delete() {
        if (selectedContactId == null)
            return;
        try {
            node.getUser(user.userId()).deleteContact(selectedContactId);
            selectedContactId = null;
            status.accept("Contact deleted");
            refresh();
        } catch (RuntimeException error) {
            status.accept("Could not delete contact: " + error.getMessage());
        }
    }

    private final class ContactRenderer extends JPanel implements ListCellRenderer<Contact> {
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();

        ContactRenderer() {
            setLayout(new BorderLayout(0, 2));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            title.setFont(Theme.SUBHEADING);
            subtitle.setFont(Theme.SMALL);
            add(title, BorderLayout.NORTH);
            add(subtitle, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Contact> list, Contact value, int index,
                boolean isSelected, boolean cellHasFocus) {
            title.setText(value == null ? "" : value.displayName());
            subtitle.setText(value == null ? "" : subtitle(value));
            boolean active = isSelected;
            setBackground(active ? Theme.GREEN_SOFT : Theme.CARD);
            title.setForeground(active ? Theme.GREEN_DARK : Theme.INK);
            subtitle.setForeground(active ? Theme.GREEN_DARK : Theme.MUTED);
            setPreferredSize(new Dimension(300, 56));
            return this;
        }

        private String subtitle(Contact contact) {
            String detail = !contact.email().isBlank() ? contact.email() : contact.phone();
            String linked = contact.userId() == null ? "Not linked" : "Linked";
            return linked + (detail.isBlank() ? "" : "  ·  " + detail);
        }
    }
}
