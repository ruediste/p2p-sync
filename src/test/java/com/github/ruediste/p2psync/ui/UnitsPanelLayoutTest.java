package com.github.ruediste.p2psync.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

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
