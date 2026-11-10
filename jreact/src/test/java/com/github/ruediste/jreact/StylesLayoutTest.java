package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

import com.antepod.taffy.style.FlexDirection;
import com.github.ruediste.jreact.nodes.DivNode;
import com.github.ruediste.jreact.nodes.Node;

class StylesLayoutTest {

    @Test
    void scopedRulesAndLayoutProduceBounds() {
        JReactRoot root = new JReactRoot(() -> {
            Styles.rules(Styles.rule(Selector.type(DivNode.class).className("box"),
                    s -> s.width(50).height(20).backgroundColor(Color.RED)));
            div(s -> s.width(200).height(100).padding(10).flexDirection(FlexDirection.ROW), () -> {
                div(s -> s.className("box"));
                text("hello");
            });
        });

        root.refresh(200, 100);
        Node container = root.tree().children().get(0);
        Node box = container.children().get(0);
        assertEquals(Color.RED, box.style.get(StyleProperties.BACKGROUND, null));
        assertEquals(10, box.bounds.x, 0.01);
        assertEquals(10, box.bounds.y, 0.01);
        assertEquals(50, box.bounds.width, 0.01);
        assertEquals(20, box.bounds.height, 0.01);
        assertTrue(container.children().get(1).bounds.width > 0);
    }
}
