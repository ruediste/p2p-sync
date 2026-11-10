package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.nodes.DivNode;

class PaintingPanelTest {

    @Test
    void visualNodesPaintBackground() {
        DivNode node = new DivNode();
        node.style.backgroundColor(Color.RED);
        node.bounds.setRect(10, 10, 40, 20);
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 100, 50);
        node.paint(graphics, new Rectangle(0, 0, 100, 50));
        graphics.dispose();
        assertEquals(Color.RED.getRGB(), image.getRGB(20, 20));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(80, 40));
    }

    @Test
    void panelSynchronizesNativeEditors() throws Exception {
        AtomicReference<JReactPanel> panel = new AtomicReference<>();
        AtomicReference<State<String>> state = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JReactPanel created = new JReactPanel(() -> {
                State<String> value = Hooks.useState("");
                state.set(value);
                input(value.get(), value::set);
            });
            created.setSize(200, 100);
            panel.set(created);
            created.requestRefresh();
        });
        SwingUtilities.invokeAndWait(() -> {
        });
        SwingUtilities.invokeAndWait(() -> {
        });

        JTextField field = null;
        for (java.awt.Component component : panel.get().getComponents())
            if (component instanceof JTextField textField)
                field = textField;
        assertNotNull(field);
        JTextField textField = field;
        SwingUtilities.invokeAndWait(() -> textField.setText("abc"));
        SwingUtilities.invokeAndWait(() -> {
        });
        SwingUtilities.invokeAndWait(() -> {
        });
        assertEquals("abc", state.get().get());
        SwingUtilities.invokeAndWait(panel.get()::close);
    }
}
