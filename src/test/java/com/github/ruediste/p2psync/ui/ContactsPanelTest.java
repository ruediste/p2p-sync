package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.util.function.Predicate;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.node.Node;

class ContactsPanelTest {
    /**
     * Verifies that a periodic refresh does not overwrite fields the user is
     * currently editing.
     */
    @Test
    void refreshKeepsEditedContactFields() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var alice = node.createNewUser("alice");
            node.getUser(alice.data.userId()).createContact(null, "Bob", "", "bob@example.com", "", "");
            var user = node.listUsers().get(0);

            SwingUtilities.invokeAndWait(() -> {
                var panel = new ContactsPanel(node, user, message -> {
                });
                panel.refresh();
                JTextField firstName = find(panel, JTextField.class, field -> "Bob".equals(field.getText()));
                assertNotNull(firstName);
                firstName.setText("Bobby");
                panel.refresh();
                assertEquals("Bobby", firstName.getText());
            });
        }
    }

    private static <T extends Component> T find(Component root, Class<T> type, Predicate<T> predicate) {
        if (type.isInstance(root) && predicate.test(type.cast(root)))
            return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type, predicate);
                if (found != null)
                    return found;
            }
        }
        return null;
    }
}
