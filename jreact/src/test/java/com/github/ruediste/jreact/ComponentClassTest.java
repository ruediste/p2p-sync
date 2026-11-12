package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.TextNode;

class ComponentClassTest {

    static class Label extends Component {
        int value;

        @Override
        public void render() {
            div(() -> text("value=" + value));
        }
    }

    @Test
    void classComponentsExtractFieldProperties() {
        JReactRoot root = new JReactRoot(() -> new Label() {
            {
                value = 1;
            }
        });

        root.refresh(100, 100);
        ComponentNode component = (ComponentNode) root.rootNode().children().get(0);
        assertEquals("value=1", ((TextNode) component.children().get(0).children().get(0)).text);

        root.refresh(100, 100);
        component = (ComponentNode) root.rootNode().children().get(0);
        assertEquals("value=1", ((TextNode) component.children().get(0).children().get(0)).text);
    }
}
