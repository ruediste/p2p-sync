package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.TextNode;

class ReconciliationTest {

    @Test
    void cleanComponentsAreReusedAndDirtyDescendantsRerender() {
        int[] renders = { 0 };
        AtomicReference<State<String>> childState = new AtomicReference<>();
        AtomicReference<State<String>> property = new AtomicReference<>();
        AtomicReference<State<Boolean>> show = new AtomicReference<>();

        class Child extends Component {
            String property;

            Child(String property) {
                this.property = property;
            }

            @Override
            public void render() {
                renders[0]++;
                childState.set(Hooks.useState("initial"));
                text(childState.get().get());
            }
        }

        JReactRoot root = new JReactRoot(() -> {
            property.set(Hooks.useState("a"));
            show.set(Hooks.useState(true));
            div(() -> {
                if (show.get().get())
                    new Child(property.get().get());
            });
        });

        root.refresh(200, 100);
        ComponentNode first = (ComponentNode) root.rootNode().children().get(0).children().get(0);
        assertEquals(1, renders[0]);

        root.refresh(200, 100);
        ComponentNode second = (ComponentNode) root.rootNode().children().get(0).children().get(0);
        assertEquals(1, renders[0]);
        assertSame(first.fiber, second.fiber);

        childState.get().set("b");
        root.refresh(200, 100);
        assertEquals(2, renders[0]);
        TextNode text = (TextNode) root.rootNode().children().get(0).children().get(0).children().get(0);
        assertEquals("b", text.text);

        property.get().set("c");
        root.refresh(200, 100);
        assertEquals(3, renders[0]);
    }

    @Test
    void keyedChildrenKeepFiberStateAcrossReordering() {
        AtomicReference<State<List<String>>> order = new AtomicReference<>();
        AtomicReference<State<String>> aState = new AtomicReference<>();
        AtomicReference<State<String>> bState = new AtomicReference<>();

        class Item extends Component {
            String id;

            Item(String id) {
                this.id = id;
                key = id;
            }

            @Override
            public void render() {
                State<String> state = Hooks.useState(id);
                if (id.equals("a"))
                    aState.set(state);
                else
                    bState.set(state);
                text(state.get());
            }
        }

        JReactRoot root = new JReactRoot(() -> {
            order.set(Hooks.useState(List.of("a", "b")));
            for (String id : order.get().get()) {
                new Item(id);
            }
        });

        root.refresh(200, 100);
        Fiber aFiber = ((ComponentNode) root.rootNode().children().get(0)).fiber;
        aState.get().set("a2");
        order.get().set(List.of("b", "a"));
        root.refresh(200, 100);

        ComponentNode first = (ComponentNode) root.rootNode().children().get(0);
        ComponentNode second = (ComponentNode) root.rootNode().children().get(1);
        assertEquals("b", first.key);
        assertEquals("a", second.key);
        assertEquals("a2", ((TextNode) second.children().get(0)).text);
        assertSame(aFiber, second.fiber);
    }

    @Test
    void stateWriteAfterReplacementUpdatesCurrentTree() {
        AtomicReference<State<String>> childState = new AtomicReference<>();
        AtomicReference<State<Integer>> parentState = new AtomicReference<>();

        class Child extends Component {
            @Override
            public void render() {
                childState.set(Hooks.useState("a"));
                text(childState.get().get());
            }
        }

        JReactRoot root = new JReactRoot(() -> {
            parentState.set(Hooks.useState(0));
            new Child();
        });

        root.refresh(200, 100);
        ComponentNode first = (ComponentNode) root.rootNode().children().get(0);
        Fiber firstFiber = first.fiber;

        parentState.get().set(1);
        root.refresh(200, 100);
        ComponentNode second = (ComponentNode) root.rootNode().children().get(0);
        assertNotSame(first, second);
        assertSame(firstFiber, second.fiber);

        childState.get().set("b");
        root.refresh(200, 100);
        assertEquals("b", ((TextNode) second.children().get(0)).text);
    }
}
