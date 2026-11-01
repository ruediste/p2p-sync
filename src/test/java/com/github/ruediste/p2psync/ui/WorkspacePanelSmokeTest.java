package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;

class WorkspacePanelSmokeTest {
    /**
     * Verifies all workspace sections can be built and refreshed without a display
     * or modal dialogs. This catches missing wiring in the card-based layout.
     */
    @Test
    void allSectionsRenderHeadlessly() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var handle = node.createNewUser("alice");
            var userId = handle.data.userId();
            node.createSyncUnit(userId, SyncUnitAuthorization.Type.MESSAGE_CHANNEL);
            node.createSyncUnit(userId, SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.createSyncUnit(userId, SyncUnitAuthorization.Type.TODO_LIST);
            var user = node.listUsers().get(0);

            SwingUtilities.invokeAndWait(() -> {
                AtomicReference<UserWorkspacePanel.Section> notified = new AtomicReference<>();
                var panel = new UserWorkspacePanel(node, user, Path.of("/tmp/p2p-sync-test"), message -> {
                }, () -> {
                }, notified::set);
                for (UserWorkspacePanel.Section section : UserWorkspacePanel.Section.values()) {
                    panel.showSection(section);
                    assertEquals(section, notified.get());
                }
                assertNotNull(panel);
                panel.refresh();
                panel.backgroundRefresh();
            });
        }
    }
}
