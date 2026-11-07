package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.component;
import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.span;
import static com.github.ruediste.jreact.Ui.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.FlexDirection;
import com.github.ruediste.jreact.layout.FlexWrap;
import com.github.ruediste.jreact.layout.JustifyContent;
import com.github.ruediste.jreact.layout.TextAlign;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.DoubleStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.ruediste.jreact.nodes.DivNode;
import com.github.ruediste.jreact.nodes.TextualNode;

/**
 * Checks CSS cascade and flex geometry using headless font metrics and exact
 * logical-pixel bounds.
 */
class StyleLayoutTest {
    private Node render(Runnable body, double width, double height) {
        try (var root = new RenderRoot(body, () -> {
        })) {
            root.render();
            root.tree().layout(width, height);
            return root.tree();
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
    void invalidDimensionsAreRejected(double value) {
        assertThrows(IllegalArgumentException.class, () -> new Style().width(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().height(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().padding(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().margin(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().flexGrow(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().flexShrink(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().border(value, Color.BLACK));
        assertThrows(IllegalArgumentException.class, () -> new Style().borderRadius(value));
        assertThrows(IllegalArgumentException.class, () -> new Style().opacity(value));
    }

    @Test
    void validatesFontsOpacityAndShadows() {
        DoubleStream.of(0, -1, Double.NaN).forEach(value -> {
            assertThrows(IllegalArgumentException.class, () -> new Style().fontSize(value));
            assertThrows(IllegalArgumentException.class, () -> new Style().lineHeight(value));
        });
        assertThrows(IllegalArgumentException.class, () -> new Style().opacity(1.1));
        assertThrows(IllegalArgumentException.class, () -> new Style.Shadow(0, 0, -1, Color.BLACK));
        assertThrows(IllegalArgumentException.class, () -> new Style.Shadow(Double.NaN, 0, 1, Color.BLACK));
        assertThrows(NullPointerException.class, () -> new Style().color(null));
        assertTrue(new Style().width(10).widthAuto().size().width.isAuto());
        assertTrue(new Style().fontWeight(true).fontStyle(true).font().isBold());
        assertTrue(new Style().fontStyle(true).font().isItalic());
    }

    @Test
    void growsProportionallyIncludingMarginsAndPadding() {
        Node tree = render(() -> {
            div(style -> style.width(50).flexGrow(1).margin(5), () -> {
            });
            div(style -> style.width(50).flexGrow(3), () -> {
            });
        }, 300, 100);
        assertEquals(97.5, tree.children.get(0).bounds.width, 0.01);
        assertEquals(192.5, tree.children.get(1).bounds.width, 0.01);
        assertEquals(5, tree.children.getFirst().bounds.x);
    }

    @Test
    void shrinkWeightsUseBaseSize() {
        Node tree = render(() -> {
            div(style -> style.width(100), () -> {
            });
            div(style -> style.width(200), () -> {
            });
        }, 150, 100);
        assertEquals(50, tree.children.get(0).bounds.width, 0.01);
        assertEquals(100, tree.children.get(1).bounds.width, 0.01);
    }

    @Test
    void nonShrinkingItemCanOverflowWithoutNegativeSizes() {
        Node tree = render(() -> {
            div(style -> style.width(200).flexShrink(0), () -> {
            });
            div(style -> style.width(50), () -> {
            });
        }, 100, 40);
        assertEquals(200, tree.children.get(0).bounds.width);
        assertEquals(0, tree.children.get(1).bounds.width);
    }

    @ParameterizedTest
    @EnumSource(value = JustifyContent.class, names = { "Start", "Center", "End", "SpaceBetween", "SpaceAround", "SpaceEvenly" })
    void allJustificationsPlaceItems(JustifyContent justify) {
        var parent = new AtomicReference<Node>();
        render(() -> parent.set(div(style -> style.width(300).height(50).justifyContent(justify), () -> {
            div(style -> style.width(50), () -> {
            });
            div(style -> style.width(50), () -> {
            });
        })), 300, 50);
        double first = switch (justify) {
            case Start, SpaceBetween -> 0;
            case Center -> 100;
            case End -> 200;
            case SpaceAround -> 50;
            case SpaceEvenly -> 200.0 / 3;
            default -> throw new IllegalStateException(justify.name());
        };
        assertEquals(first, parent.get().children.getFirst().bounds.x, 0.01);
    }

    @ParameterizedTest
    @EnumSource(value = AlignItems.class, names = { "Start", "Center", "End", "Stretch" })
    void allCrossAxisAlignments(AlignItems align) {
        Node tree = render(() -> div(style -> style.width(100).height(100).alignItems(align),
                () -> div(style -> style.width(20).height(20), () -> {
                })), 100, 100);
        var child = tree.children.getFirst().children.getFirst();
        double expected = switch (align) {
            case Start, Stretch -> 0;
            case Center -> 40;
            case End -> 80;
            default -> throw new IllegalStateException(align.name());
        };
        assertEquals(expected, child.bounds.y);
        assertEquals(20, child.bounds.height);
    }

    @Test
    void columnInsetsIncludePerSideBorders() {
        Node tree = render(() -> div(style -> style.width(100).height(100).flexDirection(FlexDirection.Column)
                .padding(2, 3, 4, 5).border(1, Color.BLACK), () -> {
                    div(style -> style.height(20), () -> {
                    });
                    div(style -> style.height(10).margin(2), () -> {
                    });
                }), 100, 100);
        Node parent = tree.children.getFirst();
        assertEquals(6, parent.children.get(0).bounds.x);
        assertEquals(3, parent.children.get(0).bounds.y);
        assertEquals(90, parent.children.get(0).bounds.width);
        assertEquals(25, parent.children.get(1).bounds.y);
    }

    @Test
    void wrapAndReverseWrapProduceMultipleLines() {
        for (FlexWrap wrap : new FlexWrap[] { FlexWrap.Wrap, FlexWrap.WrapReverse }) {
            Node tree = render(() -> div(style -> style.width(100).height(80).flexWrap(wrap), () -> {
                div(style -> style.width(60).height(20), () -> {
                });
                div(style -> style.width(60).height(20), () -> {
                });
            }), 100, 80);
            Node parent = tree.children.getFirst();
            assertEquals(wrap == FlexWrap.Wrap ? 0 : 60, parent.children.get(0).bounds.y);
            assertEquals(wrap == FlexWrap.Wrap ? 20 : 40, parent.children.get(1).bounds.y);
            assertEquals(60, parent.children.get(0).bounds.width);
        }
    }

    @Test
    void textWrapsUnicodeNewlinesAndAligns() {
        Node tree = render(
                () -> text("hello world\n\n\uD83D\uDE80end",
                        style -> style.width(40).fontSize(14).lineHeight(20).textAlign(TextAlign.LegacyRight)),
                40, 200);
        TextualNode text = (TextualNode) tree.children.getFirst();
        assertTrue(text.lines.size() >= 4);
        assertTrue(text.lines.stream().anyMatch(line -> line.text().isEmpty()));
        assertEquals(20, text.lines.get(1).baseline() - text.lines.get(0).baseline());
        assertEquals("hello world\n\n\uD83D\uDE80end".replace("\n", ""),
                text.lines.stream().map(Node.Line::text).reduce("", String::concat));
    }

    @Test
    void scopeInlinePrecedenceSpecificityAndLaterRules() {
        Node tree = render(() -> {
            Styles.rules(Styles.rule(Selector.type(DivNode.class), style -> style.backgroundColor(Color.RED)));
            Styles.rules(Styles.Scope.TRANSITIVE,
                    Styles.rule(Selector.className("item"), style -> style.color(Color.BLUE)),
                    Styles.rule(Selector.id("special"), style -> style.color(Color.GREEN)),
                    Styles.rule(Selector.id("special"), style -> style.color(Color.YELLOW)));
            div(style -> style.className("item"), () -> {
                text("a", style -> style.className("item"));
                text("b", style -> style.id("special").color(Color.PINK));
                div(() -> text("c", style -> style.id("special")));
            });
        }, 300, 100);
        Node parent = tree.children.getFirst();
        assertEquals(Color.RED, parent.style.get(StyleProperties.BACKGROUND, null));
        assertEquals(Color.RED, parent.children.get(2).style.get(StyleProperties.BACKGROUND, null));
        assertEquals(Color.BLUE, parent.children.get(0).style.color());
        assertEquals(Color.PINK, parent.children.get(1).style.color());
        assertEquals(Color.YELLOW, parent.children.get(2).children.getFirst().style.color());
    }

    /**
     * Verifies that default-scope rules reach every node emitted by the declaring
     * component, including nodes nested below its top-level output.
     */
    @Test
    void defaultScopeReachesNestedNodesOfDeclaringComponent() {
        Node tree = render(() -> {
            Styles.rules(Styles.rule(Selector.className("bar"), style -> style.color(Color.RED)));
            div(() -> div(() -> text("x", style -> style.className("bar"))));
        }, 100, 50);
        Node leaf = tree.children.getFirst().children.getFirst().children.getFirst();
        assertEquals(Color.RED, leaf.style.color());
    }

    /**
     * Verifies that default-scope rules stop at nested component boundaries while
     * transitive rules cross them.
     */
    @Test
    void defaultScopeStopsAtComponentBoundaryButTransitiveScopeCrossesIt() {
        Node defaultTree = render(() -> {
            Styles.rules(Styles.rule(Selector.className("bar"), style -> style.color(Color.RED)));
            component("child", () -> text("x", style -> style.className("bar")));
        }, 100, 50);
        assertNotEquals(Color.RED, defaultTree.children.getFirst().style.color());

        Node transitiveTree = render(() -> {
            Styles.rules(Styles.Scope.TRANSITIVE,
                    Styles.rule(Selector.className("bar"), style -> style.color(Color.RED)));
            component("child", () -> text("x", style -> style.className("bar")));
        }, 100, 50);
        assertEquals(Color.RED, transitiveTree.children.getFirst().style.color());
    }

    @Test
    void nearerComponentWinsTiesButNotHigherSpecificity() {
        Node tree = render(() -> {
            Styles.rules(Styles.Scope.TRANSITIVE,
                    Styles.rule(Selector.className("item"), style -> style.color(Color.RED)),
                    Styles.rule(Selector.id("id"), style -> style.backgroundColor(Color.YELLOW)));
            component("child", () -> {
                Styles.rules(Styles.rule(Selector.className("item"),
                        style -> style.color(Color.BLUE).backgroundColor(Color.RED)));
                text("x", style -> style.className("item").id("id"));
            });
        }, 100, 50);
        assertEquals(Color.BLUE, tree.children.getFirst().style.color());
        assertEquals(Color.YELLOW, tree.children.getFirst().style.get(StyleProperties.BACKGROUND, null));
    }

    @Test
    void noAutomaticInheritanceAndRulesMayFollowChildren() {
        Node tree = render(() -> {
            div(style -> style.color(Color.RED), () -> text("x"));
            Styles.rules(Styles.rule(Selector.type(DivNode.class), style -> style.opacity(0.5)));
        }, 100, 50);
        assertNotEquals(Color.RED, tree.children.getFirst().children.getFirst().style.color());
        assertEquals(0.5, tree.children.getFirst().style.number(StyleProperties.OPACITY, 1));
    }

    @Test
    void childDescendantCompoundAndPseudoSelectors() {
        Node tree = render(() -> div(style -> style.className("outer"),
                () -> div(() -> text("x", style -> style.id("leaf").className("item other")))), 100, 50);
        Node leaf = tree.children.getFirst().children.getFirst().children.getFirst();
        assertFalse(Selector.className("outer").child(Selector.id("leaf")).matches(leaf));
        assertTrue(Selector.className("outer").descendant(Selector.id("leaf")).matches(leaf));
        assertTrue(Selector.id("leaf").and(Selector.className("other")).matches(leaf));
        assertFalse(Selector.id("leaf").hover().matches(leaf));
        leaf.hover = leaf.active = leaf.focus = leaf.disabled = true;
        assertTrue(Selector.id("leaf").hover().active().focus().disabled().matches(leaf));
        assertThrows(IllegalArgumentException.class, () -> Selector.className("two names"));
    }

    @Test
    void spanLaysOutNestedInlineContentAcrossComponentBoundaries() {
        Node tree = render(() -> span(() -> {
            text("Hello ");
            component("inline", () -> span(() -> text("world again", style -> style.color(Color.RED))));
        }), 300, 100);
        Node span = tree.children.getFirst();
        TextualNode first = (TextualNode) span.children.getFirst();
        TextualNode second = (TextualNode) span.children.get(1).children.getFirst();
        assertEquals("Hello ", first.text());
        assertEquals("world again", second.lines.stream().map(Node.Line::text).reduce("", String::concat));
        assertTrue(second.bounds.x > first.bounds.x);
        assertTrue(span.bounds.width > 0 && span.bounds.height > 0);
    }

    @Test
    void spanRejectsBlockChildren() {
        assertThrows(IllegalArgumentException.class, () -> render(() -> span(() -> div(() -> {
        })), 100, 100));
    }

    @Test
    void flexShrinkRemeasuresTextHeightAtAllocatedWidth() {
        Node tree = render(() -> div(style -> style.width(100).alignItems(AlignItems.Start), () -> {
            text("long wrapping text here", style -> style.width(150).lineHeight(20));
            div(style -> style.width(50), () -> {
            });
        }), 100, 200);
        TextualNode text = (TextualNode) tree.children.getFirst().children.getFirst();
        assertEquals(75, text.bounds.width, 0.01);
        assertTrue(text.lines.size() >= 2);
        assertEquals(text.lines.size() * 20, text.bounds.height, 0.01);
    }

    @Test
    void inlineLongWordsUnicodeAndNewlinesRemainIntact() {
        Node tree = render(() -> span(() -> text("ab\uD83D\uDE80c\n\nlongword")), 12, 300);
        TextualNode text = (TextualNode) tree.children.getFirst().children.getFirst();
        assertEquals("ab\uD83D\uDE80clongword", text.lines.stream().map(Node.Line::text).reduce("", String::concat));
        assertTrue(text.bounds.height > 60);
        for (Node.Line line : text.lines)
            assertFalse(line.text().length() == 1 && Character.isSurrogate(line.text().charAt(0)));
    }
}