package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.libp2p.crypto.keys.Ed25519PrivateKey;
import com.github.ruediste.p2psync.node.Node;

class ContactPickerTest {

    /**
     * Verifies that requesting an unknown user, and clearing the selection, do
     * not leave (or install) a different contact as the current target.
     */
    @Test
    void unknownUserAndClearSelectionDoNotKeepStaleTargets() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var alice = node.createNewUser("alice");
            var bob = node.createNewUser("bob");
            var carol = node.createNewUser("carol");
            var user = node.listUsers().stream()
                    .filter(status -> status.userId().equals(alice.data.userId()))
                    .findFirst()
                    .orElseThrow();

            SwingUtilities.invokeAndWait(() -> {
                var picker = new ContactPicker(node, user, message -> {
                });

                picker.setSelectedUserId(bob.data.userId());
                assertEquals(bob.data.userId(), picker.selectedUserId());

                picker.setSelectedUserId(Ed25519PrivateKey.generateKeyPair().publicKey());
                assertNull(picker.selectedUserId(), "unknown user must clear the stale selection");

                picker.setSelectedUserId(carol.data.userId());
                assertEquals(carol.data.userId(), picker.selectedUserId());

                picker.clearSelection();
                assertNull(picker.selectedUserId(), "clearing must not select the first contact");
            });
        }
    }

    /**
     * Verifies that linking a user ID fills in the selected unlinked contact
     * instead of creating a duplicate contact.
     */
    @Test
    void linkKeyUpdatesSelectedUnlinkedContact() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var alice = node.createNewUser("alice");
            var bob = node.createNewUser("bob");
            var handle = node.getUser(alice.data.userId());
            handle.createContact(null, "Bob", "", "bob@example.com", "", "");
            var user = node.listUsers().stream()
                    .filter(status -> status.userId().equals(alice.data.userId()))
                    .findFirst()
                    .orElseThrow();
            CountDownLatch linked = new CountDownLatch(1);

            SwingUtilities.invokeAndWait(() -> {
                var picker = new ContactPicker(node, user, message -> {
                    if ("Contact linked".equals(message))
                        linked.countDown();
                });
                JComboBox<?> matches = find(picker, JComboBox.class, ignored -> true);
                assertNotNull(matches);
                matches.setSelectedIndex(0);
                JTextField linkKey = find(picker, JTextField.class, field -> field.getColumns() == 12);
                assertNotNull(linkKey);
                linkKey.setText(HexFormat.of().formatHex(bob.data.userId().bytes()));
                JButton link = find(picker, JButton.class, button -> "Link".equals(button.getText()));
                assertNotNull(link);
                link.doClick();
            });

            assertTrue(linked.await(5, TimeUnit.SECONDS));
            var contacts = node.getUser(alice.data.userId()).listContacts();
            assertEquals(1, contacts.size());
            assertEquals(bob.data.userId(), contacts.get(0).userId());
            assertEquals("Bob", contacts.get(0).firstName());
            assertEquals("bob@example.com", contacts.get(0).email());
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
