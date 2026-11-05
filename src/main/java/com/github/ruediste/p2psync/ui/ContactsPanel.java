package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HexFormat;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
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

import com.github.ruediste.p2psync.identity.UserId;
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
    private boolean dirty;
    private boolean loading;

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
        trackDirty(userId, firstName, lastName, email, phone, notes);

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
        boolean changed = this.user == null || !this.user.userId().equals(user.userId());
        this.user = user;
        if (changed) {
            selectedContactId = null;
            editingNew = false;
            dirty = false;
        }
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
        if (editingNew && dirty)
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
                    if (!dirty)
                        showContact(contactModel.get(index));
                    return;
                }
            }
            if (dirty)
                return;
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

    private void trackDirty(javax.swing.text.JTextComponent... fields) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                markDirty();
            }
        };
        for (var field : fields)
            field.getDocument().addDocumentListener(listener);
    }

    private void markDirty() {
        if (!loading)
            dirty = true;
    }

    private void loadFields(String linkedUserId, String first, String last, String mail, String phoneNumber,
            String noteText) {
        loading = true;
        try {
            userId.setText(linkedUserId);
            firstName.setText(first);
            lastName.setText(last);
            email.setText(mail);
            phone.setText(phoneNumber);
            notes.setText(noteText);
        } finally {
            loading = false;
        }
        dirty = false;
    }

    private void startNew() {
        selectedContactId = null;
        editingNew = true;
        contactList.clearSelection();
        loadFields("", "", "", "", "", "");
        renderDetail("New contact", "All fields are optional, but at least one must be filled.", false);
    }

    private void showContact(Contact contact) {
        selectedContactId = contact.contactId();
        editingNew = false;
        loadFields(contact.userId() == null ? "" : HexFormat.of().formatHex(contact.userId().bytes()),
                contact.firstName(), contact.lastName(), contact.email(), contact.phone(), contact.notes());
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
        form.add(Theme.formField("User ID (hex, optional)", userId));
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

    private UserId parseUserId() {
        String text = userId.getText().trim();
        if (text.isEmpty())
            return null;
        return UserId.fromBytes(HexFormat.of().parseHex(text));
    }

    private void save(boolean existing) {
        UserId linked;
        try {
            linked = parseUserId();
        } catch (RuntimeException error) {
            status.accept("Could not save contact: " + error.getMessage());
            return;
        }
        UserId userId = user.userId();
        String contactId = selectedContactId;
        String first = firstName.getText();
        String last = lastName.getText();
        String mail = email.getText();
        String phoneNumber = phone.getText();
        String noteText = notes.getText();
        boolean update = existing && contactId != null;
        Background.run("save contact", () -> {
            NodeUserHandle handle = node.getUser(userId);
            if (update)
                return handle.updateContact(contactId, linked, first, last, mail, phoneNumber, noteText);
            return handle.createContact(linked, first, last, mail, phoneNumber, noteText);
        }, contact -> {
            selectedContactId = contact.contactId();
            editingNew = false;
            dirty = false;
            status.accept(update ? "Contact saved" : "Contact created");
            refresh();
        }, error -> status.accept("Could not save contact: " + error.getMessage()));
    }

    private void delete() {
        if (selectedContactId == null)
            return;
        UserId userId = user.userId();
        String contactId = selectedContactId;
        Background.run("delete contact", () -> node.getUser(userId).deleteContact(contactId), () -> {
            selectedContactId = null;
            dirty = false;
            status.accept("Contact deleted");
            refresh();
        }, error -> status.accept("Could not delete contact: " + error.getMessage()));
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
