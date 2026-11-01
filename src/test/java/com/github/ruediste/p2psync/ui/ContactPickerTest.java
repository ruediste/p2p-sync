package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
