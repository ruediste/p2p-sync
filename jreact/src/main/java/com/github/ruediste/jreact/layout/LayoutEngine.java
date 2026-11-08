package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antepod.taffy.tree.DetailedGridItemsInfo;
import com.antepod.taffy.tree.DetailedLayoutInfo;
import com.antepod.taffy.tree.MeasureFunction;
import com.antepod.taffy.tree.NodeId;
import com.antepod.taffy.tree.TaffyTree;
import com.antepod.taffy.geometry.Line;
import com.antepod.taffy.geometry.Rect;
import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AlignContent;
import com.antepod.taffy.style.AlignItems;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.style.Dimension;
import com.antepod.taffy.style.Display;
import com.antepod.taffy.style.LengthPercentageAuto;
import com.antepod.taffy.style.Position;
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
                applyLayout(node, tree.layout(idByNode.get(node)), node == root);
            }
            root.getLayout().absX = root.getLayout().x;
            root.getLayout().absY = root.getLayout().y;
            computeAbsolute(root);
            computeContentExtents(root);
            for (LayoutNode node : nodes) {
                if (node.getStyle().display() == Display.GRID) {
                    setDetailedGridInfo(tree, idByNode.get(node), node);
                } else {
                    node.getLayout().detailedGridInfo = null;
                }
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

    private static void applyLayout(LayoutNode node, com.antepod.taffy.tree.Layout taffyLayout, boolean isRoot) {
        Layout layout = node.getLayout();
        layout.x = taffyLayout.location.x;
        layout.y = taffyLayout.location.y;
        if (isRoot) {
            // Taffy does not displace the root node by its own margin; match the historical
            // behavior where the root's layout position includes its margin.
            layout.x += taffyLayout.margin.left;
            layout.y += taffyLayout.margin.top;
        }
        layout.width = taffyLayout.size.width;
        layout.height = taffyLayout.size.height;
        layout.contentWidth = taffyLayout.contentSize.width;
        layout.contentHeight = taffyLayout.contentSize.height;
        layout.scrollbarWidth = taffyLayout.scrollbarSize.width;
        layout.scrollbarHeight = taffyLayout.scrollbarSize.height;
        layout.border = new Rect<>(taffyLayout.border.left, taffyLayout.border.right, taffyLayout.border.top,
                taffyLayout.border.bottom);
        layout.padding = new Rect<>(taffyLayout.padding.left, taffyLayout.padding.right, taffyLayout.padding.top,
                taffyLayout.padding.bottom);
        layout.margin = new Rect<>(taffyLayout.margin.left, taffyLayout.margin.right, taffyLayout.margin.top,
                taffyLayout.margin.bottom);
        layout.usedContentWidth = layout.contentWidth;
        layout.usedContentHeight = layout.contentHeight;
        layout.placedOutOfFlow = false;
        layout.containingBlock = null;
        layout.detailedGridInfo = null;
    }

    private static void computeAbsolute(LayoutNode node) {
        for (LayoutNode child : node.getChildren()) {
            child.getLayout().absX = node.getLayout().absX + child.getLayout().x;
            child.getLayout().absY = node.getLayout().absY + child.getLayout().y;
            computeAbsolute(child);
        }
    }

    private static void computeContentExtents(LayoutNode node) {
        float maxRight = 0;
        float maxBottom = 0;
        for (LayoutNode descendant : descendants(node)) {
            maxRight = Math.max(maxRight,
                    descendant.getLayout().absX + descendant.getLayout().width - node.getLayout().absX);
            maxBottom = Math.max(maxBottom,
                    descendant.getLayout().absY + descendant.getLayout().height - node.getLayout().absY);
        }
        node.getLayout().contentWidth = Math.max(0, maxRight);
        node.getLayout().contentHeight = Math.max(0, maxBottom);
        for (LayoutNode child : node.getChildren()) {
            computeContentExtents(child);
        }
    }

    private static void setDetailedGridInfo(TaffyTree<LayoutNode> tree, NodeId node, LayoutNode layoutNode) {
        DetailedLayoutInfo detailed = tree.detailedLayoutInfo(node);
        Layout layout = layoutNode.getLayout();
        if (!detailed.grid().isPresent()) {
            layout.detailedGridInfo = null;
            return;
        }
        com.antepod.taffy.tree.DetailedGridInfo taffyInfo = detailed.grid().get();
        DetailedGridInfo info = new DetailedGridInfo();
        fillTrack(info.columns, taffyInfo.columns, trackStart(layoutNode, true), contentSize(layoutNode, true),
                trackAlignment(layoutNode, true));
        fillTrack(info.rows, taffyInfo.rows, trackStart(layoutNode, false), contentSize(layoutNode, false),
                trackAlignment(layoutNode, false));
        for (DetailedGridItemsInfo item : taffyInfo.items) {
            info.items.add(new DetailedGridInfo.ItemInfo(item.rowStart, item.rowEnd, item.columnStart,
                    item.columnEnd));
        }
        layout.detailedGridInfo = info;
    }

    private static void fillTrack(DetailedGridInfo.TrackInfo track,
            com.antepod.taffy.tree.DetailedGridTracksInfo taffyTrack, float start, float axisSize,
            float alignment) {
        track.negativeImplicitTracks = taffyTrack.negativeImplicitTracks;
        track.explicitTracks = taffyTrack.explicitTracks;
        track.positiveImplicitTracks = taffyTrack.positiveImplicitTracks;
        track.gutters.addAll(taffyTrack.gutters);
        track.sizes.addAll(taffyTrack.sizes);
        List<java.lang.Float> sizes = taffyTrack.sizes;
        List<java.lang.Float> gutters = taffyTrack.gutters;
        float free = Math.max(0, axisSize - sum(sizes) - sum(gutters));
        float cursor = start + alignment * free;
        for (int i = 0; i < sizes.size(); i++) {
            float size = sizes.get(i);
            track.positions.add(new Line<>(cursor, cursor + size));
            cursor = cursor + size + gutters.get(Math.min(i + 1, gutters.size() - 1));
        }
    }

    private static float trackStart(LayoutNode node, boolean columns) {
        Layout layout = node.getLayout();
        Rect<java.lang.Float> border = layout.border;
        Rect<java.lang.Float> padding = layout.padding;
        if (columns) {
            return border.left + padding.left;
        }
        return border.top + padding.top;
    }

    private static float contentSize(LayoutNode node, boolean columns) {
        Layout layout = node.getLayout();
        Rect<java.lang.Float> border = layout.border;
        Rect<java.lang.Float> padding = layout.padding;
        float size = columns ? layout.width : layout.height;
        float borderSum = columns ? border.left + border.right : border.top + border.bottom;
        float paddingSum = columns ? padding.left + padding.right : padding.top + padding.bottom;
        return Math.max(0, size - borderSum - paddingSum);
    }

    private static float trackAlignment(LayoutNode node, boolean columns) {
        if (columns) {
            AlignContent alignment = node.getStyle().justifyContent();
            return alignment == null ? 0 : contentAlignment(alignment);
        }
        AlignContent alignment = node.getStyle().alignContent();
        return alignment == null ? 0 : contentAlignment(alignment);
    }

    private static float contentAlignment(AlignContent alignment) {
        return switch (alignment.keyword) {
            case END -> 1;
            case CENTER -> 0.5f;
            default -> 0;
        };
    }

    private static float sum(List<java.lang.Float> values) {
        float sum = 0;
        for (java.lang.Float value : values) {
            sum += value;
        }
        return sum;
    }

    private static List<LayoutNode> descendants(LayoutNode node) {
        List<LayoutNode> result = new ArrayList<>();
        for (LayoutNode child : node.getChildren()) {
            result.add(child);
            result.addAll(descendants(child));
        }
        return result;
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
        builder.append("[x: ").append(fmt(layout.x))
                .append(", y: ").append(fmt(layout.y))
                .append(", w: ").append(fmt(layout.width))
                .append(", h: ").append(fmt(layout.height))
                .append("]");
        builder.append(" content_w: ").append(fmt(layout.contentWidth))
                .append(", content_h: ").append(fmt(layout.contentHeight));
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
