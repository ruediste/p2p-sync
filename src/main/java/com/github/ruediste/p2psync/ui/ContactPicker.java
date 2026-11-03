package com.github.ruediste.p2psync.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;

import com.github.ruediste.p2psync.libp2p.crypto.PubKey;
import com.github.ruediste.p2psync.node.Contact;
import com.github.ruediste.p2psync.node.Node;

final class ContactPicker extends JPanel {
    private record Option(String label, PubKey userId) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Node node;
    private final Consumer<String> status;
    private Node.UserStatus user;
    private final JTextField search = Theme.textField(20);
    private final JComboBox<Option> matches = new JComboBox<>();
    private PubKey selectedUserId;
    private boolean updating;

    ContactPicker(Node node, Node.UserStatus user, Consumer<String> status) {
        this.node = node;
        this.user = user;
        this.status = status;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        search.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        matches.setEditable(true);
        matches.setEditor(new BasicComboBoxEditor() {
            @Override
            protected JTextField createEditorComponent() {
                return search;
            }
        });
        matches.setRenderer(new OptionRenderer());
        matches.setPrototypeDisplayValue(new Option("A reasonably long contact name", null));
        matches.setToolTipText("Search linked contacts");
        matches.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(matches);
        add(Box.createVerticalStrut(4));

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                searchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                searchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                searchChanged();
            }
        });
        search.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                search.selectAll();
            }
        });
        matches.addActionListener(event -> {
            if (updating)
                return;
            Option option = (Option) matches.getSelectedItem();
            if (option != null)
                select(option);
        });
        rebuild();
    }

    void setUser(Node.UserStatus user) {
        boolean changed = this.user == null || !this.user.userId().equals(user.userId());
        this.user = user;
        if (changed) {
            selectedUserId = null;
            updating = true;
            try {
                search.setText("");
                matches.setSelectedItem(null);
            } finally {
                updating = false;
            }
        }
        rebuild();
    }

    PubKey selectedUserId() {
        return selectedUserId;
    }

    void setSelectedUserId(PubKey userId) {
        if (userId == null) {
            clearSelection();
            return;
        }
        Option option = findOption(options(""), userId);
        if (option == null) {
            clearSelection();
            status.accept("No linked contact for this user");
            return;
        }
        selectedUserId = userId;
        updating = true;
        try {
            search.setText(option.label());
            matches.setModel(new DefaultComboBoxModel<>(options("").toArray(Option[]::new)));
            matches.setSelectedItem(option);
        } finally {
            updating = false;
        }
    }

    void clearSelection() {
        selectedUserId = null;
        updating = true;
        try {
            search.setText("");
            matches.setSelectedItem(null);
        } finally {
            updating = false;
        }
        rebuild();
    }

    private void select(Option option) {
        selectedUserId = option.userId();
        updating = true;
        try {
            search.setText(option.label());
            matches.setSelectedItem(option);
        } finally {
            updating = false;
        }
        matches.hidePopup();
    }

    private void searchChanged() {
        if (updating)
            return;
        selectedUserId = null;
        SwingUtilities.invokeLater(this::refreshMatches);
    }

    private void refreshMatches() {
        rebuild();
        if (!search.getText().isBlank() && matches.isShowing() && !matches.isPopupVisible())
            matches.showPopup();
    }

    private void rebuild() {
        String text = search.getText();
        updating = true;
        try {
            List<Option> options = options(text);
            matches.setModel(new DefaultComboBoxModel<>(options.toArray(Option[]::new)));
            matches.setSelectedItem(null);
            if (!text.equals(search.getText()))
                search.setText(text);
            search.setCaretPosition(Math.min(text.length(), search.getText().length()));
        } finally {
            updating = false;
        }
    }

    private List<Option> options(String query) {
        if (user == null)
            return List.of();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Option> result = new ArrayList<>();
        for (Contact contact : node.getUser(user.userId()).listContacts()) {
            if (contact.userId() == null)
                continue;
            if (!normalized.isEmpty() && !contact.searchText().contains(normalized))
                continue;
            result.add(new Option(contact.displayName(), contact.userId()));
        }
        return result;
    }

    private static Option findOption(List<Option> options, PubKey userId) {
        for (Option option : options) {
            if (userId.equals(option.userId()))
                return option;
        }
        return null;
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
            label.setForeground(Theme.INK);
            setBackground(isSelected ? Theme.GREEN_SOFT : Theme.CARD);
            return this;
        }
    }
}
