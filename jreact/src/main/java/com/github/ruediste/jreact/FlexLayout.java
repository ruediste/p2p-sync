package com.github.ruediste.jreact;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes node geometry for JReact's pure-Java Flexbox subset.
 *
 * <p>
 * Starting from a root size and resolved styles, measures content, orders
 * children, groups flex lines, distributes grow/shrink space, and applies
 * justification, cross-axis alignment, margins, padding, and borders. Writes
 * root-relative logical-pixel bounds into the existing {@link Node} tree and
 * remeasures text at allocated widths so wrapping follows flex sizing.
 *
 * <p>
 * Delegates standalone text to {@link TextMetrics} and span contents to
 * {@link InlineLayout}. The supplied graphics context provides font metrics;
 * this class does not paint, dispatch events, or reconcile component state.
 * It implements the framework's supported subset, not a complete browser CSS
 * layout engine.
 */
public final class FlexLayout {
    /** Content measurement expressed as border-box width and height. */
    private record Size(double width, double height) {
    }

    /** Working main/cross-axis dimensions and margins for a flex child. */
    private static final class Item {
        final Node node;
        final Style.Insets margin;
        double main, cross;

        Item(Node node, double main, double cross) {
            this.node = node;
            this.main = main;
            this.cross = cross;
            margin = node.style.margin();
        }
    }

    /** Groups flex items and tracks their occupied main and cross-axis space. */
    private static final class Line {
        final List<Item> items = new ArrayList<>();
        double used, cross;
    }

    private FlexLayout() {
    }

    public static void layout(Node root, double width, double height, Graphics2D graphics) {
        Style.nonNegative(width);
        Style.nonNegative(height);
        place(root, 0, 0, width, height, graphics);
    }

    private static Size natural(Node node, double maxWidth, Graphics2D graphics) {
        return natural(node, maxWidth, graphics, false);
    }

    private static Size natural(Node node, double maxWidth, Graphics2D graphics, boolean allocatedWidth) {
        Style style = node.style;
        Style.Insets inset = style.inset();
        double width = allocatedWidth ? maxWidth : style.width(), height = style.height();
        double contentWidth = Double.isNaN(width) ? maxWidth : width;
        if (node.kind == Node.Kind.ICON) {
            if (Double.isNaN(width))
                width = node.icon.getIconWidth() + inset.horizontal();
            if (Double.isNaN(height))
                height = node.icon.getIconHeight() + inset.vertical();
        } else if (node.kind == Node.Kind.TEXT || node.kind == Node.Kind.INPUT || node.kind == Node.Kind.SELECT) {
            if (Double.isNaN(width)) {
                var metrics = graphics.getFontMetrics(style.font());
                width = Math.min(maxWidth, java.util.Arrays.stream(node.text.split("\n", -1))
                        .mapToInt(metrics::stringWidth).max().orElse(0) + inset.horizontal());
                if (node.kind == Node.Kind.INPUT || node.kind == Node.Kind.SELECT)
                    width = Math.max(120, width);
            }
            if (Double.isNaN(height))
                height = TextMetrics.measure(node, graphics, width, false);
        } else if (node.kind == Node.Kind.SPAN) {
            if (Double.isNaN(width))
                width = Math.min(maxWidth, node.descendants().stream()
                        .mapToDouble(child -> child.kind == Node.Kind.TEXT
                                ? graphics.getFontMetrics(child.style.font()).stringWidth(child.text)
                                : child.kind == Node.Kind.ICON ? child.icon.getIconWidth() : 0)
                        .sum() + inset.horizontal());
            if (Double.isNaN(height))
                height = InlineLayout.flow(node, width, graphics, false);
        } else {
            boolean row = isRow(node);
            double main = 0, cross = 0;
            for (Node child : ordered(node)) {
                Size size = natural(child,
                        Math.max(0, contentWidth - inset.horizontal() - child.style.margin().horizontal()), graphics);
                Style.Insets margin = child.style.margin();
                main += row ? size.width + margin.horizontal() : size.height + margin.vertical();
                cross = Math.max(cross, row ? size.height + margin.vertical() : size.width + margin.horizontal());
            }
            if (Double.isNaN(width))
                width = Math.min(maxWidth, (row ? main : cross) + inset.horizontal());
            if (Double.isNaN(height)) {
                height = (row ? cross : main) + inset.vertical();
                if (row && wrap(node) != Style.Wrap.NOWRAP)
                    height = lines(node, Math.max(0, width - inset.horizontal()), graphics).stream()
                            .mapToDouble(line -> line.cross).sum() + inset.vertical();
            }
        }
        return new Size(Math.max(0, width), Math.max(0, height));
    }

    private static boolean isRow(Node node) {
        return node.kind == Node.Kind.SPAN
                || node.style.get(Style.Property.DIRECTION, Style.Direction.ROW) == Style.Direction.ROW;
    }

    private static Style.Wrap wrap(Node node) {
        return node.kind == Node.Kind.SPAN ? Style.Wrap.WRAP : node.style.get(Style.Property.WRAP, Style.Wrap.NOWRAP);
    }

    private static List<Node> ordered(Node node) {
        return node.children.stream().sorted(Comparator.comparingInt(child -> child.style.get(Style.Property.ORDER, 0)))
                .toList();
    }

    private static double mainMargin(Item item, boolean row) {
        return row ? item.margin.horizontal() : item.margin.vertical();
    }

    private static double crossMargin(Item item, boolean row) {
        return row ? item.margin.vertical() : item.margin.horizontal();
    }

    private static List<Line> lines(Node node, double mainAvailable, Graphics2D graphics) {
        boolean row = isRow(node);
        var result = new ArrayList<Line>();
        Line line = new Line();
        result.add(line);
        double maxWidth = row ? mainAvailable : Math.max(0, node.bounds.width - node.style.inset().horizontal());
        for (Node child : ordered(node)) {
            Size size = natural(child, Math.max(0, maxWidth - child.style.margin().horizontal()), graphics,
                    !row && node.style.get(Style.Property.ALIGN, Style.Align.STRETCH) == Style.Align.STRETCH
                            && Double.isNaN(child.style.width()));
            Item item = new Item(child, row ? size.width : size.height, row ? size.height : size.width);
            double outer = item.main + mainMargin(item, row);
            if (wrap(node) != Style.Wrap.NOWRAP && !line.items.isEmpty() && line.used + outer > mainAvailable + 0.001) {
                line = new Line();
                result.add(line);
            }
            line.items.add(item);
            line.used += outer;
            line.cross = Math.max(line.cross, item.cross + crossMargin(item, row));
        }
        return result;
    }

    private static void distribute(Line line, double available, boolean row) {
        double free = available - line.used;
        if (free >= 0) {
            double total = line.items.stream().mapToDouble(item -> item.node.style.number(Style.Property.GROW, 0))
                    .sum();
            if (total > 0)
                for (Item item : line.items)
                    item.main += free * item.node.style.number(Style.Property.GROW, 0) / total;
        } else {
            var remaining = new ArrayList<>(line.items);
            while (free < -0.001 && !remaining.isEmpty()) {
                double total = remaining.stream()
                        .mapToDouble(item -> item.main * item.node.style.number(Style.Property.SHRINK, 1)).sum();
                if (total == 0)
                    break;
                double consumed = 0;
                for (Item item : remaining) {
                    double reduction = Math.min(item.main,
                            -free * item.main * item.node.style.number(Style.Property.SHRINK, 1) / total);
                    item.main -= reduction;
                    consumed += reduction;
                }
                free += consumed;
                remaining.removeIf(item -> item.main < 0.001);
            }
        }
        line.used = line.items.stream().mapToDouble(item -> item.main + mainMargin(item, row)).sum();
    }

    private static void place(Node node, double x, double y, double width, double height, Graphics2D graphics) {
        node.bounds.setRect(x, y, width, height);
        if (node.kind == Node.Kind.TEXT || node.kind == Node.Kind.INPUT || node.kind == Node.Kind.SELECT) {
            TextMetrics.measure(node, graphics, width, true);
            return;
        }
        if (node.kind == Node.Kind.ICON)
            return;
        if (node.kind == Node.Kind.SPAN) {
            InlineLayout.flow(node, width, graphics, true);
            return;
        }
        Style.Insets inset = node.style.inset();
        boolean row = isRow(node);
        double mainAvailable = Math.max(0, row ? width - inset.horizontal() : height - inset.vertical());
        double crossAvailable = Math.max(0, row ? height - inset.vertical() : width - inset.horizontal());
        List<Line> lines = lines(node, mainAvailable, graphics);
        for (Line line : lines) {
            distribute(line, mainAvailable, row);
            if (row) {
                for (Item item : line.items) {
                    if (Double.isNaN(item.node.style.height()))
                        item.cross = natural(item.node, item.main, graphics, true).height;
                }
                line.cross = line.items.stream().mapToDouble(item -> item.cross + crossMargin(item, row)).max()
                        .orElse(0);
            }
        }
        if (wrap(node) == Style.Wrap.NOWRAP)
            lines.getFirst().cross = crossAvailable;
        double crossCursor = wrap(node) == Style.Wrap.WRAP_REVERSE ? crossAvailable : 0;
        for (Line line : lines) {
            if (wrap(node) == Style.Wrap.WRAP_REVERSE)
                crossCursor -= line.cross;
            double free = Math.max(0, mainAvailable - line.used);
            int count = line.items.size();
            Style.Justify justify = node.style.get(Style.Property.JUSTIFY, Style.Justify.START);
            double gap = switch (justify) {
                case SPACE_BETWEEN -> count > 1 ? free / (count - 1) : 0;
                case SPACE_AROUND -> count > 0 ? free / count : 0;
                case SPACE_EVENLY -> free / (count + 1);
                default -> 0;
            };
            double mainCursor = switch (justify) {
                case CENTER -> free / 2;
                case END -> free;
                case SPACE_AROUND -> gap / 2;
                case SPACE_EVENLY -> gap;
                default -> 0;
            };
            for (Item item : line.items) {
                Style.Align align = node.style.get(Style.Property.ALIGN, Style.Align.STRETCH);
                double cross = item.cross;
                boolean autoCross = Double.isNaN(row ? item.node.style.height() : item.node.style.width());
                if (align == Style.Align.STRETCH && autoCross)
                    cross = Math.max(0, line.cross - crossMargin(item, row));
                double spare = line.cross - cross - crossMargin(item, row);
                double offset = switch (align) {
                    case CENTER -> spare / 2;
                    case END -> spare;
                    default -> 0;
                };
                double main = mainCursor + (row ? item.margin.left() : item.margin.top());
                double across = crossCursor + offset + (row ? item.margin.top() : item.margin.left());
                place(item.node, x + inset.left() + (row ? main : across), y + inset.top() + (row ? across : main),
                        row ? item.main : cross, row ? cross : item.main, graphics);
                mainCursor += item.main + mainMargin(item, row) + gap;
            }
            if (wrap(node) != Style.Wrap.WRAP_REVERSE)
                crossCursor += line.cross;
        }
    }
}