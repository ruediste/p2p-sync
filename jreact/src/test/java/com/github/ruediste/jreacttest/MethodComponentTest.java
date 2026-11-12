package com.github.ruediste.jreacttest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.JReactAgent;
import com.github.ruediste.jreact.JReactRoot;
import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.TextNode;

class MethodComponentTest {

    @Test
    void methodComponentsCreateDeferredBoundaries() throws Exception {
        JReactAgent.install();
        Method card = Class.forName("com.github.ruediste.jreacttest.MethodComponentFixture").getMethod("Card");
        JReactRoot root = new JReactRoot(() -> {
            try {
                card.invoke(null);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException(exception);
            }
        });
        root.refresh(100, 100);
        assertTrue(root.rootNode().children().get(0) instanceof ComponentNode);
        ComponentNode component = (ComponentNode) root.rootNode().children().get(0);
        assertEquals("method component", ((TextNode) component.children().get(0)).text);
    }
}
