package com.github.ruediste.jreact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.ruediste.jreact.components.Components;
import com.github.ruediste.jreact.components.Theme;
import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.events.KeyboardEvent;
import com.github.ruediste.jreact.events.PointerEvent;

/**
 * Exercises controlled component callbacks, disabled states, range validation,
 * and native selection.
 */
class ComponentsTest {
    final Components ui = new Components(Theme.light());

    @Test
    void nestedButtonVariantsKeepTheirScopedThemeColors() {
        try (var root = new JReactRoot(() -> ui.card("Buttons", () -> ui.row(8, () -> {
            for (Components.Variant variant : Components.Variant.values())
                ui.button(variant.name(), variant, false, () -> {
                });
        })), () -> {
        })) {
            root.render();
            Node card = root.tree().children.getFirst();
            Node row = card.children.get(1);
            List<Color> backgrounds = row.children.stream()
                    .map(node -> node.style.get(StyleProperties.BACKGROUND, (Color) null)).toList();
            assertEquals(ui.theme.primary(), backgrounds.get(0));
            assertEquals(ui.theme.surface(), backgrounds.get(1));
            assertEquals(ui.theme.danger(), backgrounds.get(2));
        }
    }

    @ParameterizedTest
    @EnumSource(Components.Variant.class)
    void buttonVariantsActivateAndInlineOverridesRules(Components.Variant variant) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger();
            try (var panel = new JReactPanel(() -> ui.button("Run", variant, false, calls::incrementAndGet,
                    style -> style.backgroundColor(java.awt.Color.MAGENTA)))) {
                panel.setSize(300, 100);
                panel.refresh();
                RenderingTest.click(panel, 20, 15);
                assertEquals(1, calls.get());
                assertEquals(java.awt.Color.MAGENTA,
                        panel.tree().children.getFirst().style.get(StyleProperties.BACKGROUND, null));
            }
        });
    }

    @Test
    void checkboxAndTabsAreControlled() {
        var checked = new AtomicReference<>(false);
        var selected = new AtomicReference<>("One");
        try (var root = new JReactRoot(() -> {
            ui.checkbox("Enabled", checked.get(), false, checked::set);
            ui.tabs(List.of("One", "Two"), selected.get(), selected::set);
        }, () -> {
        })) {
            root.render();
            root.tree().children.get(0).dispatchEvent(new ClickEvent());
            root.tree().children.get(1).children.get(1).dispatchEvent(new ClickEvent());
            root.render();
            assertTrue(checked.get());
            assertEquals("Two", selected.get());
            assertTrue(root.tree().children.get(0).accessibleName.contains("checked"));
        }
    }

    @Test
    void sliderQuantizesClampsAndSupportsKeyboard() {
        var value = new AtomicReference<>(0.0);
        try (var root = new JReactRoot(() -> ui.slider("Level", value.get(), 0, 100, 5, false, value::set), () -> {
        })) {
            root.render();
            Node slider = root.tree().children.getFirst();
            slider.dispatchEvent(new PointerEvent(0.123));
            assertEquals(10, value.get());
            root.render();
            slider = root.tree().children.getFirst();
            slider.dispatchEvent(new KeyboardEvent(KeyEvent.VK_RIGHT));
            assertEquals(15, value.get());
            root.render();
            root.tree().children.getFirst().dispatchEvent(new KeyboardEvent(KeyEvent.VK_END));
            assertEquals(100, value.get());
            root.render();
            root.tree().children.getFirst().dispatchEvent(new KeyboardEvent(KeyEvent.VK_RIGHT));
            assertEquals(100, value.get());
            root.tree().children.getFirst().dispatchEvent(new KeyboardEvent(KeyEvent.VK_HOME));
            assertEquals(0, value.get());
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY })
    void invalidProgressIsRejected(double value) {
        assertThrows(IllegalArgumentException.class, () -> ui.progress(value));
    }

    @Test
    void invalidRangesAndSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ui.slider("x", 0, 0, 0, 1, false, value -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> ui.slider("x", 2, 0, 1, 1, false, value -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> ui.slider("x", 0, 0, 1, 0, false, value -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> ui.tabs(List.of("a", "a"), "a", value -> {
        }));
        try (var root = new JReactRoot(() -> Ui.select("missing", List.of("a"), value -> {
        }, style -> {
        }), () -> {
        })) {
            assertThrows(IllegalArgumentException.class, root::render);
        }
    }

    @Test
    void nativeSelectUpdatesValueAndOptionsWithoutFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var value = new AtomicReference<State<String>>();
            var options = new AtomicReference<>(List.of("One", "Two"));
            var calls = new AtomicInteger();
            try (var panel = new JReactPanel(() -> {
                value.set(Hooks.useState("One"));
                ui.selectField("Choice", value.get().get(), options.get(), false, next -> {
                    calls.incrementAndGet();
                    value.get().set(next);
                });
            })) {
                panel.setSize(300, 100);
                panel.refresh();
                var combo = (JComboBox<?>) panel.getComponent(0);
                assertEquals(0, calls.get());
                combo.setSelectedItem("Two");
                panel.refresh();
                assertEquals("Two", value.get().get());
                assertEquals(1, calls.get());
                options.set(List.of("Two", "Three"));
                value.get().set("Two");
                panel.setSize(301, 100);
                panel.refresh();
                assertEquals(2, combo.getItemCount());
                assertEquals("Three", combo.getItemAt(1));
                assertEquals(1, calls.get());
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Components.Tone.class)
    void feedbackComponentsRenderForEveryTone(Components.Tone tone) {
        try (var root = new JReactRoot(() -> ui.card("Status", () -> {
            ui.badge("Ready", tone);
            ui.alert("Operation complete", tone);
            ui.progress(0.5);
        }), () -> {
        })) {
            root.render();
            assertEquals(4, root.tree().children.getFirst().children.size());
        }
    }
}