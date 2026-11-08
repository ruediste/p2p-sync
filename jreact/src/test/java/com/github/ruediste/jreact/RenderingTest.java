package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.input;
import static com.github.ruediste.jreact.Ui.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Verifies actual pixels, mouse activation, native editing, and lifecycle on
 * Swing's EDT without a display.
 */
class RenderingTest {
    private BufferedImage image(Runnable body) {
        var image = new BufferedImage(120, 100, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try (var root = new JReactRoot(body, () -> {
        })) {
            root.render();
            root.tree().layout(120, 100);
            root.tree().paint(graphics, 1);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Test
    void paintsBackgroundRoundedCornersAndBorder() {
        BufferedImage image = image(() -> div(style -> style.width(80).height(60).backgroundColor(Color.RED)
                .border(4, Color.BLUE).borderRadius(12).margin(10), () -> {
                }));
        assertEquals(Color.RED.getRGB(), image.getRGB(30, 30));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(40, 11));
        assertEquals(0, image.getRGB(10, 10));
        assertTrue(new Color(image.getRGB(14, 14), true).getBlue() > 0);
    }

    @Test
    void opacityComposesThroughSubtree() {
        BufferedImage image = image(() -> div(style -> style.width(80).height(60).opacity(0.5),
                () -> div(style -> style.width(60).height(40).opacity(0.5).backgroundColor(Color.RED), () -> {
                })));
        assertEquals(64, new Color(image.getRGB(20, 20), true).getAlpha(), 1);
    }

    @Test
    void paintsPerSideBorders() {
        BufferedImage image = image(() -> div(style -> style.width(80).height(60).border(new Style.Border(
                new Style.Edge(3, Color.RED), new Style.Edge(4, Color.GREEN),
                new Style.Edge(5, Color.BLUE), new Style.Edge(6, Color.YELLOW))), () -> {
                }));
        assertEquals(Color.RED.getRGB(), image.getRGB(40, 1));
        assertEquals(Color.GREEN.getRGB(), image.getRGB(78, 30));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(40, 58));
        assertEquals(Color.YELLOW.getRGB(), image.getRGB(1, 30));
    }

    @Test
    void blurredBoxShadowExtendsBeyondBounds() {
        BufferedImage image = image(() -> div(style -> style.width(40).height(30).margin(10)
                .backgroundColor(Color.WHITE).boxShadow(new Style.Shadow(8, 8, 4, Color.BLACK)), () -> {
                }));
        assertTrue(new Color(image.getRGB(52, 35), true).getAlpha() > 0);
        assertEquals(Color.WHITE.getRGB(), image.getRGB(25, 25));
    }

    @Test
    void paintsTextAndTextShadowWithSelectedFont() {
        BufferedImage image = image(() -> text("JReact", style -> style.width(100).height(40).fontSize(24)
                .fontWeight(true).color(Color.RED).textShadow(new Style.Shadow(3, 3, 2, Color.BLUE))));
        int red = 0, blue = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() > 0 && color.getRed() > color.getBlue())
                    red++;
                if (color.getAlpha() > 0 && color.getBlue() > color.getRed())
                    blue++;
            }
        assertTrue(red > 20);
        assertTrue(blue > 20);
    }

    @Test
    void clicksBubbleToControlAndDisabledControlsIgnoreClicks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clicks = new AtomicInteger();
            var disabled = new AtomicReference<>(false);
            try (var panel = new JReactPanel(() -> div(style -> style.width(100).height(50), () -> text("button"))
                    .onClick(event -> clicks.incrementAndGet()).disabled(disabled.get()))) {
                panel.setSize(120, 100);
                panel.refresh();
                click(panel, 10, 10);
                assertEquals(1, clicks.get());
                assertTrue(panel.tree().children.getFirst().focus);
            }
            try (var panel = new JReactPanel(() -> div(style -> style.width(100).height(50), () -> text("button"))
                    .onClick(event -> clicks.incrementAndGet()).disabled(true))) {
                panel.setSize(120, 100);
                panel.refresh();
                click(panel, 10, 10);
                assertEquals(1, clicks.get());
            }
        });
    }

    @Test
    void releaseOutsideDoesNotClickAndHoverRulesUpdate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var clicks = new AtomicInteger();
            try (var panel = new JReactPanel(() -> {
                Styles.rules(
                        Styles.rule(Selector.className("button").hover(), style -> style.backgroundColor(Color.RED)));
                div(style -> style.width(50).height(30).className("button"), () -> {
                }).onClick(event -> clicks.incrementAndGet());
            })) {
                panel.setSize(120, 100);
                panel.refresh();
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 10, 10, 0, false));
                assertEquals(Color.RED, panel.tree().children.getFirst().style.get(StyleProperties.BACKGROUND, null));
                panel.dispatchEvent(
                        new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false, MouseEvent.BUTTON1));
                panel.dispatchEvent(
                        new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, 110, 90, 1, false, MouseEvent.BUTTON1));
                assertEquals(0, clicks.get());
            }
        });
    }

    @Test
    void nativeComponentIsControlledAndRemovedOnUnmount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var visible = new AtomicReference<State<Boolean>>();
            var value = new AtomicReference<State<String>>();
            try (var panel = new JReactPanel(() -> {
                visible.set(Hooks.useState(true));
                value.set(Hooks.useState("start"));
                if (visible.get().get())
                    input(value.get().get(), value.get()::set, style -> style.width(100).height(30));
            })) {
                panel.setSize(120, 100);
                panel.refresh();
                JTextField field = (JTextField) panel.getComponent(0);
                field.setText("edited");
                panel.refresh();
                assertEquals("edited", value.get().get());
                value.get().set("external");
                panel.refresh();
                assertSame(field, panel.getComponent(0));
                assertEquals("external", field.getText());
                visible.get().set(false);
                panel.refresh();
                assertEquals(0, panel.getComponentCount());
            }
        });
    }

    @Test
    void requiresEdt() {
        assertThrows(IllegalStateException.class, () -> new JReactPanel(() -> {
        }));
    }

    private void key(JReactPanel panel, int code, int modifiers) {
        KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
        for (var listener : panel.getKeyListeners())
            listener.keyPressed(event);
    }

    @Test
    void tabSkipsDisabledControlsAndEnterSpaceActivate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger();
            try (var panel = new JReactPanel(() -> {
                div(style -> style.width(30).height(30), () -> {
                }).onClick(event -> calls.incrementAndGet());
                div(style -> style.width(30).height(30), () -> {
                }).onClick(event -> calls.incrementAndGet()).disabled(true);
                div(style -> style.width(30).height(30), () -> {
                }).onClick(event -> calls.incrementAndGet());
            })) {
                panel.setSize(120, 100);
                panel.refresh();
                key(panel, KeyEvent.VK_TAB, 0);
                assertTrue(panel.tree().children.get(0).focus);
                key(panel, KeyEvent.VK_ENTER, 0);
                assertEquals(1, calls.get());
                key(panel, KeyEvent.VK_TAB, 0);
                assertTrue(panel.tree().children.get(2).focus);
                key(panel, KeyEvent.VK_SPACE, 0);
                assertEquals(2, calls.get());
                key(panel, KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK);
                assertTrue(panel.tree().children.get(0).focus);
            }
        });
    }

    @Test
    void shiftTabWithoutFocusStartsAtLastControlAndSlidersUseArrowKeys() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var value = new AtomicReference<State<Double>>();
            var ui = new com.github.ruediste.jreact.components.Components(
                    com.github.ruediste.jreact.components.Theme.light());
            try (var panel = new JReactPanel(() -> {
                value.set(Hooks.useState(50.0));
                ui.button("Other", () -> {
                });
                ui.slider("Level", value.get().get(), 0, 100, 5, false, value.get()::set);
            })) {
                panel.setSize(400, 100);
                panel.refresh();
                key(panel, KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK);
                assertTrue(panel.tree().children.get(1).focus);
                key(panel, KeyEvent.VK_RIGHT, 0);
                assertEquals(55, value.get().get());
                key(panel, KeyEvent.VK_HOME, 0);
                assertEquals(0, value.get().get());
            }
        });
    }

    @Test
    void changedLeafRepaintsOnlyItsBoundsAndRemovedNodeIsCleared() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var value = new AtomicReference<State<String>>();
            var visible = new AtomicReference<State<Boolean>>();
            var regions = new ArrayList<Rectangle>();
            try (var panel = new JReactPanel(() -> {
                value.set(Hooks.useState("first"));
                visible.set(Hooks.useState(true));
                div(style -> style.width(200).height(100), () -> {
                    text(value.get().get(), style -> style.width(80).height(20));
                    if (visible.get().get())
                        div(style -> style.width(30).height(20).backgroundColor(Color.RED), () -> {
                        });
                });
            }) {
                @Override
                public void repaint(long delay, int x, int y, int width, int height) {
                    regions.add(new Rectangle(x, y, width, height));
                }
            }) {
                panel.setSize(400, 200);
                panel.refresh();
                regions.clear();
                value.get().set("changed");
                panel.refresh();
                assertEquals(new Rectangle(0, 0, 80, 20), regions.getLast());
                regions.clear();
                visible.get().set(false);
                panel.refresh();
                assertTrue(regions.getLast().contains(90, 10));
            }
        });
    }

    @Test
    void rightClickDoesNotActivateAndTooltipUsesControlName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger();
            try (var panel = new JReactPanel(() -> div(style -> style.width(100).height(30), () -> text("Save"))
                    .onClick(event -> calls.incrementAndGet()).accessibleName("Save document"))) {
                panel.setSize(120, 100);
                panel.refresh();
                panel.dispatchEvent(
                        new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false, MouseEvent.BUTTON3));
                panel.dispatchEvent(
                        new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, 10, 10, 1, false, MouseEvent.BUTTON3));
                assertEquals(0, calls.get());
                assertEquals("Save document",
                        panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 10, 10, 0, false)));
            }
        });
    }

    static void click(JReactPanel panel, int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}