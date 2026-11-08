package com.github.ruediste.jreact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.ruediste.jreact.storybook.Storybook;

/**
 * Renders every story at two sizes, saves PNG evidence, and drives
 * representative end-to-end workflows.
 */
class StorybookTest {
    @BeforeAll
    static void installAgent() {
        JReactAgent.install();
    }

    static Stream<Arguments> stories() {
        return Storybook.STORIES.stream()
                .flatMap(story -> Stream.of(Arguments.of(story, 400), Arguments.of(story, 1000)));
    }

    @ParameterizedTest
    @MethodSource("stories")
    void rendersEveryStoryAtNarrowAndDesktopSizes(String story, int width) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try (var panel = Storybook.panel(story)) {
                panel.setSize(width, 900);
                panel.refresh();
                BufferedImage image = new BufferedImage(width, 900, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    panel.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                long distinct = java.util.stream.IntStream.range(0, width * 900)
                        .map(index -> image.getRGB(index % width, index / width)).distinct().count();
                assertTrue(distinct > 100, "Story must contain painted text and controls");
                for (Node node : panel.tree().descendants()) {
                    assertTrue(Double.isFinite(node.bounds.width) && Double.isFinite(node.bounds.height));
                    assertTrue(node.bounds.x >= -0.1 && node.bounds.getMaxX() <= width + 0.1,
                            "Horizontal overflow: " + node.text() + " " + node.bounds);
                    assertTrue(node.bounds.y >= -0.1 && node.bounds.getMaxY() <= 900.1,
                            "Vertical overflow: " + node.text() + " " + node.bounds);
                }
                Path directory = Path.of("target", "storybook");
                Files.createDirectories(directory);
                ImageIO.write(image, "png", directory.resolve(story.replace(' ', '-') + "-" + width + ".png").toFile());
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null)
            throw new AssertionError(failure.get());
    }

    private void activate(JReactPanel panel, String name) {
        Node node = panel.tree().descendants().stream().filter(item -> name.equals(item.accessibleName)).findFirst()
                .orElseThrow();
        click(panel, (int) node.bounds.getCenterX(), (int) node.bounds.getCenterY());
        panel.refresh();
    }

    private static void click(JReactPanel panel, int x, int y) {
        panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1,
                false, java.awt.event.MouseEvent.BUTTON1));
        panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1,
                false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test
    void counterButtonsAndDisablingWorkThroughRuntimeComponents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var panel = Storybook.panel("Buttons")) {
                panel.setSize(1000, 900);
                panel.refresh();
                activate(panel, "+");
                activate(panel, "+");
                assertTrue(panel.tree().descendants().stream().anyMatch(node -> "2".equals(node.text())));
                activate(panel, "Disable buttons, unchecked");
                activate(panel, "+");
                assertTrue(panel.tree().descendants().stream().anyMatch(node -> "2".equals(node.text())));
                activate(panel, "Disable buttons, checked");
                activate(panel, "Reset");
                assertTrue(panel.tree().descendants().stream().anyMatch(node -> "0".equals(node.text())));
            }
        });
    }

    @Test
    void taskBoardAddsCompletesAndFiltersTasks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var panel = Storybook.panel("Task Board")) {
                panel.setSize(1000, 900);
                panel.refresh();
                JTextField field = (JTextField) panel.getComponent(0);
                field.setText("New work");
                panel.refresh();
                activate(panel, "Add task");
                assertSame(field, panel.getComponent(0));
                assertEquals("", field.getText());
                activate(panel, "New work, unchecked");
                activate(panel, "Done");
                assertTrue(panel.tree().descendants().stream().anyMatch(node -> "New work".equals(node.text())));
                assertFalse(panel.tree().descendants().stream()
                        .anyMatch(node -> "Ship component library".equals(node.text())));
            }
        });
    }

    @Test
    void navigationUnmountsAndRemountsStoriesWithoutHookErrors() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var panel = Storybook.panel()) {
                panel.setSize(1000, 900);
                panel.refresh();
                for (String story : Storybook.STORIES)
                    activate(panel, story);
                activate(panel, "Overview");
                activate(panel, "High contrast, unchecked");
                assertTrue(panel.tree().descendants().stream().anyMatch(node -> "11 controls".equals(node.text())));
            }
        });
    }
}
