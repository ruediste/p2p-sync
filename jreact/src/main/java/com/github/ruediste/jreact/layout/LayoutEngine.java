package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antepod.taffy.geometry.Rect;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AlignContent;
import com.antepod.taffy.style.AlignItems;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.style.Dimension;
import com.antepod.taffy.style.LengthPercentageAuto;
import com.antepod.taffy.style.Position;
import com.antepod.taffy.tree.Layout;
import com.antepod.taffy.tree.MeasureFunction;
import com.antepod.taffy.tree.NodeId;
import com.antepod.taffy.tree.TaffyTree;
import com.github.ruediste.jreact.Style;

/**
 * Computes the layout of {@link LayoutNode} trees using the native Taffy layout
 * engine through the Taffy4J bindings.
 *
 * <p>
 * The engine is stateless. Styles, children, measurement callbacks and the
 * resulting layout data all belong to the nodes, so several trees can be laid
 * out independently. Each {@link #computeLayout} call builds a fresh native
 * tree from the node hierarchy, computes layout, and copies the results back
 * into the nodes.
 */
public final class LayoutEngine {
    private static final MeasureFunction<LayoutNode> MEASURE = (known, space, node, context, style) -> {
        LayoutNode layoutNode = context.orElse(null);
        Size<java.lang.Float> measured = layoutNode == null ? null
                : layoutNode.measure(toKnownJReact(known), space);
        if (measured == null) {
            return new com.antepod.taffy.geometry.Size<java.lang.Float>(0f, 0f);
        }
        return new com.antepod.taffy.geometry.Size<>(measured.width, measured.height);
    };

    private LayoutEngine() {
    }

    public static void computeLayout(LayoutNode root, float width, float height) {
        computeLayout(root, new Size<>(AvailableSpace.definite(width), AvailableSpace.definite(height)));
    }

    public static void computeLayout(LayoutNode root, Size<AvailableSpace> availableSpace) {
        try (TaffyTree<LayoutNode> tree = new TaffyTree<>()) {
            // The root fills the available space. When its own size is auto, override it
            // with the definite available space so Taffy sizes the root to the viewport
            // rather than to its content.
            Style rootStyle = root.getStyle();
            Size<Dimension> rootSize = rootStyle.size();
            boolean overrideWidth = rootSize.width.isAuto() && availableSpace.width.isDefinite();
            boolean overrideHeight = rootSize.height.isAuto() && availableSpace.height.isDefinite();
            if (overrideWidth || overrideHeight) {
                rootStyle.size(new Size<>(
                        overrideWidth ? Dimension.length(availableSpace.width.unwrap()) : rootSize.width,
                        overrideHeight ? Dimension.length(availableSpace.height.unwrap()) : rootSize.height));
            }

            List<LayoutNode> nodes = new ArrayList<>();
            List<NodeId> ids = new ArrayList<>();
            Map<LayoutNode, NodeId> idByNode = new IdentityHashMap<>();
            NodeId rootId = buildTree(tree, root, nodes, ids, idByNode);
            if (rootId == null) {
                return;
            }
            tree.computeLayoutWithMeasure(rootId, availableSpace, MEASURE);
            for (LayoutNode node : nodes) {
                applyLayout(node, tree.layout(idByNode.get(node)));
            }
        }
    }

    private static NodeId buildTree(TaffyTree<LayoutNode> tree, LayoutNode node, List<LayoutNode> nodes,
            List<NodeId> ids, Map<LayoutNode, NodeId> idByNode) {
        com.antepod.taffy.style.Style style = buildTaffyStyle(node.getStyle());
        List<LayoutNode> children = node.getChildren();
        NodeId id;
        if (children.isEmpty()) {
            id = tree.newLeafWithContext(style, node);
        } else {
            List<NodeId> childIds = new ArrayList<>(children.size());
            for (LayoutNode child : children) {
                childIds.add(buildTree(tree, child, nodes, ids, idByNode));
            }
            id = tree.newWithChildren(style, childIds);
        }
        nodes.add(node);
        ids.add(id);
        idByNode.put(node, id);
        return id;
    }

    private static void applyLayout(LayoutNode node, Layout taffyLayout) {
        node.setLayout(taffyLayout);
    }

    public static String printTree(LayoutNode node) {
        StringBuilder builder = new StringBuilder();
        printNode(node, "", true, builder);
        return builder.toString();
    }

    private static void printNode(LayoutNode node, String prefix, boolean last, StringBuilder builder) {
        builder.append(prefix);
        builder.append(last ? "└── " : "├── ");
        Layout layout = node.getLayout();
        builder.append("[x: ").append(fmt(layout.location.x))
                .append(", y: ").append(fmt(layout.location.y))
                .append(", w: ").append(fmt(layout.size.width))
                .append(", h: ").append(fmt(layout.size.height))
                .append("]");
        builder.append(" content_w: ").append(fmt(layout.contentSize.width))
                .append(", content_h: ").append(fmt(layout.contentSize.height));
        builder.append(" border: l:").append(fmt(layout.border.left))
                .append(" r:").append(fmt(layout.border.right))
                .append(" t:").append(fmt(layout.border.top))
                .append(" b:").append(fmt(layout.border.bottom));
        builder.append(" padding: l:").append(fmt(layout.padding.left))
                .append(" r:").append(fmt(layout.padding.right))
                .append(" t:").append(fmt(layout.padding.top))
                .append(" b:").append(fmt(layout.padding.bottom));
        builder.append('\n');
        String childPrefix = prefix + (last ? "    " : "│   ");
        List<LayoutNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNode(children.get(i), childPrefix, i == children.size() - 1, builder);
        }
    }

    private static String fmt(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    // -- style conversion -----------------------------------------------------

    private static com.antepod.taffy.style.Style buildTaffyStyle(Style style) {
        com.antepod.taffy.style.Style.Builder builder = com.antepod.taffy.style.Style.builder();
        builder.display(style.display());
        builder.itemIsTable(style.itemIsTable());
        builder.itemIsReplaced(style.itemIsReplaced());
        builder.boxSizing(style.boxSizing());
        builder.direction(style.layoutDirection());
        builder.overflow(style.overflow());
        builder.scrollbarWidth(style.scrollbarWidth());
        builder.floatValue(style.floatMode());
        builder.clear(style.clear());
        builder.position(style.position());
        boolean ignoresInsets = style.position() == Position.RELATIVE;
        Rect<LengthPercentageAuto> inset = ignoresInsets
                ? new Rect<>(LengthPercentageAuto.AUTO, LengthPercentageAuto.AUTO, LengthPercentageAuto.AUTO,
                        LengthPercentageAuto.AUTO)
                : style.inset();
        builder.inset(inset);
        builder.size(style.size());
        builder.minSize(style.minSize());
        builder.maxSize(style.maxSize());
        builder.aspectRatio(style.aspectRatio() == null ? Optional.empty()
                : Optional.of(java.lang.Float.valueOf(style.aspectRatio())));
        builder.margin(style.margin());
        builder.padding(style.padding());
        builder.border(style.border());
        AlignItems alignItems = style.alignItems();
        if (alignItems != null) {
            builder.alignItems(alignItems);
        }
        AlignItems alignSelf = style.alignSelf();
        builder.alignSelf(alignSelf);
        AlignItems justifyItems = style.justifyItems();
        if (justifyItems != null) {
            builder.justifyItems(justifyItems);
        }
        AlignItems justifySelf = style.justifySelf();
        builder.justifySelf(justifySelf);
        AlignContent alignContent = style.alignContent();
        if (alignContent != null) {
            builder.alignContent(alignContent);
        }
        AlignContent justifyContent = style.justifyContent();
        if (justifyContent != null) {
            builder.justifyContent(justifyContent);
        }
        builder.gap(style.gap());
        builder.textAlign(style.textAlign());
        builder.flexDirection(style.flexDirection());
        builder.flexWrap(style.flexWrap());
        builder.flexBasis(style.flexBasis());
        builder.flexGrow(style.flexGrow());
        builder.flexShrink(style.flexShrink());
        builder.gridTemplateRows(style.gridTemplateRows());
        builder.gridTemplateColumns(style.gridTemplateColumns());
        builder.gridAutoRows(style.gridAutoRows());
        builder.gridAutoColumns(style.gridAutoColumns());
        builder.gridAutoFlow(style.gridAutoFlow());
        builder.gridTemplateAreas(Optional.ofNullable(taffyGridTemplateAreas(style)));
        builder.gridTemplateColumnNames(style.gridTemplateColumnNames());
        builder.gridTemplateRowNames(style.gridTemplateRowNames());
        builder.gridRow(style.gridRow());
        builder.gridColumn(style.gridColumn());
        return builder.build();
    }

    private static com.antepod.taffy.style.GridTemplateAreas taffyGridTemplateAreas(Style style) {
        List<com.antepod.taffy.style.GridTemplateArea> areas = new ArrayList<>(style.getGridTemplateAreas());
        return new com.antepod.taffy.style.GridTemplateAreas(areas, style.getGridTemplateAreaRowCount(),
                style.getGridTemplateAreaColumnCount());
    }

    // -- taffy -> jreact conversion -------------------------------------------

    // -- taffy -> jreact conversion -------------------------------------------

    private static Size<AvailableSpace> toKnownJReact(
            Size<java.util.Optional<java.lang.Float>> size) {
        return new Size<>(toKnownJReact(size.width), toKnownJReact(size.height));
    }

    private static AvailableSpace toKnownJReact(java.util.Optional<java.lang.Float> value) {
        return value.map(AvailableSpace::definite).orElseGet(() -> AvailableSpace.MIN_CONTENT);
    }
}
