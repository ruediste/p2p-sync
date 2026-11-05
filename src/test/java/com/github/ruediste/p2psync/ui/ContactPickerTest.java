package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Component;
import java.awt.Container;
import java.util.function.Predicate;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.identity.UserId;
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
            var handle = node.getUser(alice.data.userId());
            handle.createContact(bob.data.userId(), "Bob", "", "", "", "");
            handle.createContact(carol.data.userId(), "Carol", "", "", "", "");
            var user = node.listUsers().stream()
                    .filter(status -> status.userId().equals(alice.data.userId()))
                    .findFirst()
                    .orElseThrow();

            SwingUtilities.invokeAndWait(() -> {
                var picker = new ContactPicker(node, user, message -> {
                });

                picker.setSelectedUserId(bob.data.userId());
                assertEquals(bob.data.userId(), picker.selectedUserId());

                picker.setSelectedUserId(UserId.fromPublicKey(Ed25519PrivateKey.generateKeyPair().publicKey()));
                assertNull(picker.selectedUserId(), "unknown user must clear the stale selection");

                picker.setSelectedUserId(carol.data.userId());
                assertEquals(carol.data.userId(), picker.selectedUserId());

                picker.clearSelection();
                assertNull(picker.selectedUserId(), "clearing must not select the first contact");
            });
        }
    }

    /**
     * Verifies that only contacts with a linked user ID are offered, and that
     * typing in the picker filters them.
     */
    @Test
    void onlyLinkedContactsAreOfferedAndSearchable() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var alice = node.createNewUser("alice");
            var bob = node.createNewUser("bob");
            node.createNewUser("carol");
            var handle = node.getUser(alice.data.userId());
            handle.createContact(bob.data.userId(), "Bob", "", "", "", "");
            handle.createContact(null, "Unlinked", "", "", "", "");
            var user = node.listUsers().stream()
                    .filter(status -> status.userId().equals(alice.data.userId()))
                    .findFirst()
                    .orElseThrow();

            ContactPicker[] picker = new ContactPicker[1];
            SwingUtilities.invokeAndWait(() -> picker[0] = new ContactPicker(node, user, message -> {
            }));
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> matches = find(picker[0], JComboBox.class, ignored -> true);
                assertNotNull(matches);
                assertEquals(1, matches.getModel().getSize(), "only linked contacts must be offered");
                assertEquals("Bob", matches.getItemAt(0).toString());

                JTextField search = find(picker[0], JTextField.class, ignored -> true);
                assertNotNull(search);
                search.setText("unlinked");
            });
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> matches = find(picker[0], JComboBox.class, ignored -> true);
                assertEquals(0, matches.getModel().getSize(), "search must filter out non-matching contacts");
                JTextField search = find(picker[0], JTextField.class, ignored -> true);
                assertNotNull(search);
                search.setText("bob");
            });
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> matches = find(picker[0], JComboBox.class, ignored -> true);
                assertEquals(1, matches.getModel().getSize());

                picker[0].setSelectedUserId(bob.data.userId());
                assertEquals(bob.data.userId(), picker[0].selectedUserId());
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
