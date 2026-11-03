package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    /**
     * Verifies a replicated contact replaces the pristine "New contact" form on
     * refresh. An untouched empty form must not hide the newly listed contact.
     */
    @Test
    void refreshSelectsFirstContactWhileNewContactFormIsEmpty() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var alice = node.createNewUser("alice");
            var user = node.listUsers().get(0);

            SwingUtilities.invokeAndWait(() -> {
                var panel = new ContactsPanel(node, user, message -> {
                });
                panel.refresh();
                assertNull(find(panel, JTextField.class, field -> "Bob".equals(field.getText())),
                        "the new-contact form must start empty");

                node.getUser(alice.data.userId()).createContact(null, "Bob", "", "bob@example.com", "", "");
                panel.refresh();

                assertNotNull(find(panel, JTextField.class, field -> "Bob".equals(field.getText())),
                        "an untouched new-contact form must be replaced by the first contact");
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
