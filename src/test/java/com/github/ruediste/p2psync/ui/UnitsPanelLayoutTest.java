package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.p2psync.node.Node;
import com.github.ruediste.p2psync.node.SyncUnitAuthorization;

class UnitsPanelLayoutTest {
    /**
     * Verifies that the unit, file, and member tables keep a usable height when
     * the detail pane is narrower than the card's preferred width. Grid-based
     * stacks used to shrink the whole card to its minimum size in that case,
     * collapsing the tables to a few pixels.
     */
    @Test
    void tablesKeepTheirHeightWhenDetailPaneIsNarrow() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var handle = node.createNewUser("alice");
            var userId = handle.data.userId();
            node.createSyncUnit(userId, SyncUnitAuthorization.Type.SHARED_FOLDER);
            var user = node.listUsers().get(0);

            SwingUtilities.invokeAndWait(() -> {
                var panel = new UnitsPanel(node, user, message -> {
                });
                panel.setSize(760, 700);
                layoutTree(panel);
                panel.refresh();
                layoutTree(panel);

                List<JTable> tables = new ArrayList<>();
                collect(panel, JTable.class, tables);
                assertFalse(tables.isEmpty());
                for (JTable table : tables) {
                    JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                    assertNotNull(scroll);
                    assertTrue(scroll.getHeight() >= 100,
                            () -> "table " + table.getName() + " collapsed to " + scroll.getHeight() + "px");
                }
            });
        }
    }

    /**
     * Verifies viewers of a shared folder only get the download control, while
     * the owner keeps the upload and delete controls.
     */
    @Test
    void sharedFolderHidesWriteControlsForViewers() throws Exception {
        try (var node = new Node(new Node.Network())) {
            var bob = node.createNewUser("bob");
            var alice = node.createNewUser("alice");
            var folder = node.createSyncUnit(bob.data.userId(), SyncUnitAuthorization.Type.SHARED_FOLDER);
            node.putSharedFile(folder, bob.data.userId(), "notes.txt",
                    "hello".getBytes(StandardCharsets.UTF_8));
            node.inviteToSyncUnit(folder, bob.data.userId(), alice.data.userId(),
                    SyncUnitAuthorization.Role.VIEWER);
            node.joinSyncUnit(folder, alice.data.userId());

            var aliceStatus = node.listUsers().stream()
                    .filter(user -> user.localName().equals("alice"))
                    .findFirst()
                    .orElseThrow();
            var bobStatus = node.listUsers().stream()
                    .filter(user -> user.localName().equals("bob"))
                    .findFirst()
                    .orElseThrow();

            SwingUtilities.invokeAndWait(() -> {
                var viewerPanel = new UnitsPanel(node, aliceStatus, message -> {
                });
                viewerPanel.setSize(760, 700);
                layoutTree(viewerPanel);
                viewerPanel.refresh();
                layoutTree(viewerPanel);
                List<JButton> viewerButtons = new ArrayList<>();
                collect(viewerPanel, JButton.class, viewerButtons);
                assertFalse(hasButton(viewerButtons, "Upload"));
                assertFalse(hasButton(viewerButtons, "Delete"));
                assertTrue(hasButton(viewerButtons, "Download"));

                var ownerPanel = new UnitsPanel(node, bobStatus, message -> {
                });
                ownerPanel.setSize(760, 700);
                layoutTree(ownerPanel);
                ownerPanel.refresh();
                layoutTree(ownerPanel);
                List<JButton> ownerButtons = new ArrayList<>();
                collect(ownerPanel, JButton.class, ownerButtons);
                assertTrue(hasButton(ownerButtons, "Upload"));
                assertTrue(hasButton(ownerButtons, "Delete"));
                assertTrue(hasButton(ownerButtons, "Download"));
            });
        }
    }

    private static boolean hasButton(List<JButton> buttons, String text) {
        return buttons.stream().anyMatch(button -> text.equals(button.getText()));
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents())
                layoutTree(child);
        }
    }

    private static <T> void collect(Component component, Class<T> type, List<T> result) {
        if (type.isInstance(component))
            result.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents())
                collect(child, type, result);
        }
    }
}
