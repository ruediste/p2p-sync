package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.div;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.events.KeyboardEvent;
import com.github.ruediste.jreact.nodes.Node;

class InteractionTest {

    @Test
    void hoverActiveFocusAndEventsWork() {
        JReactRoot root = new JReactRoot(() -> div(s -> s.width(100).height(100)));
        root.refresh(200, 200);
        Node node = root.rootNode().children().get(0);
        int[] clicks = { 0 };
        int[] keys = { 0 };
        node.on(ClickEvent.class, event -> clicks[0]++);
        node.on(KeyboardEvent.class, event -> keys[0]++);

        root.pointerMoved(50, 50);
        root.refresh();
        assertTrue(node.hover);
        root.pointerPressed(50, 50);
        root.refresh();
        assertTrue(node.active);
        assertTrue(node.focus);
        root.pointerReleased(50, 50);
        assertEquals(1, clicks[0]);
        root.refresh();
        assertFalse(node.active);
        root.keyPressed(65);
        assertEquals(1, keys[0]);
        root.pointerMoved(150, 150);
        root.refresh();
        assertFalse(node.hover);
    }
}
