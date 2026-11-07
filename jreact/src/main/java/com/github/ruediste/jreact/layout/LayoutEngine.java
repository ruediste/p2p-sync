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
    private static final MeasureFunction<LayoutNode> MEASURE =
            (known, space, node, context, style) -> {
                LayoutNode layoutNode = context.orElse(null);
                Size<java.lang.Float> measured = layoutNode == null ? null
                        : layoutNode.measure(toKnownJReact(known), toJReact(space));
                if (measured == null) {
                    return new com.antepod.taffy.geometry.Size<java.lang.Float>(0f, 0f);
                }
                return new com.antepod.taffy.geometry.Size<>(measured.width, measured.height);
            };

    private LayoutEngine() {
    }

    public static void computeLayout(LayoutNode root, float width, float height) {
        computeLayout(root, Size.of(AvailableSpace.definite(width), AvailableSpace.definite(height)));
    }

    public static void computeLayout(LayoutNode root, Size<AvailableSpace> availableSpace) {
        try (TaffyTree<LayoutNode> tree = new TaffyTree<>()) {
            // The root fills the available space. When its own size is auto, override it
            // with the definite available space so Taffy sizes the root to the viewport
            // rather than to its content.
            Style rootStyle = root.getStyle();
            Size<Dimension> rootSize = rootStyle.size();
            boolean overrideWidth = rootSize.width.kind == Dimension.Kind.AUTO && availableSpace.width.isDefinite();
            boolean overrideHeight = rootSize.height.kind == Dimension.Kind.AUTO && availableSpace.height.isDefinite();
            if (overrideWidth || overrideHeight) {
                rootStyle.size(new Size<>(
                        overrideWidth ? Dimension.length(availableSpace.width.value) : rootSize.width,
                        overrideHeight ? Dimension.length(availableSpace.height.value) : rootSize.height));
            }

            List<LayoutNode> nodes = new ArrayList<>();
            List<NodeId> ids = new ArrayList<>();
            Map<LayoutNode, NodeId> idByNode = new IdentityHashMap<>();
            NodeId rootId = buildTree(tree, root, nodes, ids, idByNode);
            if (rootId == null) {
                return;
            }
            tree.computeLayoutWithMeasure(rootId, toTaffy(availableSpace), MEASURE);
            for (LayoutNode node : nodes) {
                applyLayout(node, tree.layout(idByNode.get(node)), node == root);
            }
            root.getLayout().absX = root.getLayout().x;
            root.getLayout().absY = root.getLayout().y;
            computeAbsolute(root);
            computeContentExtents(root);
            for (LayoutNode node : nodes) {
                if (node.getStyle().display() == Display.Grid) {
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
            com.github.ruediste.jreact.layout.JustifyContent alignment = node.getStyle().layoutJustifyContent();
            return alignment == null ? 0 : contentAlignment(alignment);
        }
        com.github.ruediste.jreact.layout.AlignContent alignment = node.getStyle().alignContent();
        return alignment == null ? 0 : contentAlignment(alignment);
    }

    private static float contentAlignment(com.github.ruediste.jreact.layout.AlignContent alignment) {
        return switch (alignment) {
            case End -> 1;
            case Center -> 0.5f;
            default -> 0;
        };
    }

    private static float contentAlignment(com.github.ruediste.jreact.layout.JustifyContent alignment) {
        return switch (alignment) {
            case End -> 1;
            case Center -> 0.5f;
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
        builder.display(taffyDisplay(style.display()));
        builder.itemIsTable(style.itemIsTable());
        builder.itemIsReplaced(style.itemIsReplaced());
        builder.boxSizing(taffyBoxSizing(style.boxSizing()));
        builder.direction(taffyDirection(style.layoutDirection()));
        builder.overflow(new com.antepod.taffy.geometry.Point<>(taffyOverflow(style.overflow().x),
                taffyOverflow(style.overflow().y)));
        builder.scrollbarWidth(style.scrollbarWidth());
        builder.floatValue(taffyFloatValue(style.floatMode()));
        builder.clear(taffyClear(style.clear()));
        Position position = style.position();
        builder.position(taffyPosition(position));
        boolean ignoresInsets = position == Position.Static || position == Position.Sticky;
        Rect<LengthPercentageAuto> inset = ignoresInsets
                ? new Rect<>(LengthPercentageAuto.auto(), LengthPercentageAuto.auto(), LengthPercentageAuto.auto(),
                        LengthPercentageAuto.auto())
                : style.layoutInset();
        builder.inset(new com.antepod.taffy.geometry.Rect<>(taffyLengthPercentageAuto(inset.left),
                taffyLengthPercentageAuto(inset.right), taffyLengthPercentageAuto(inset.top),
                taffyLengthPercentageAuto(inset.bottom)));
        builder.size(new com.antepod.taffy.geometry.Size<>(taffyDimension(style.size().width),
                taffyDimension(style.size().height)));
        builder.minSize(new com.antepod.taffy.geometry.Size<>(taffyDimension(style.minSize().width),
                taffyDimension(style.minSize().height)));
        builder.maxSize(new com.antepod.taffy.geometry.Size<>(taffyDimension(style.maxSize().width),
                taffyDimension(style.maxSize().height)));
        builder.aspectRatio(style.aspectRatio() == null ? Optional.empty()
                : Optional.of(java.lang.Float.valueOf(style.aspectRatio())));
        builder.margin(new com.antepod.taffy.geometry.Rect<>(taffyLengthPercentageAuto(style.layoutMargin().left),
                taffyLengthPercentageAuto(style.layoutMargin().right),
                taffyLengthPercentageAuto(style.layoutMargin().top),
                taffyLengthPercentageAuto(style.layoutMargin().bottom)));
        builder.padding(new com.antepod.taffy.geometry.Rect<>(taffyLengthPercentage(style.layoutPadding().left),
                taffyLengthPercentage(style.layoutPadding().right), taffyLengthPercentage(style.layoutPadding().top),
                taffyLengthPercentage(style.layoutPadding().bottom)));
        builder.border(new com.antepod.taffy.geometry.Rect<>(taffyLengthPercentage(style.layoutBorder().left),
                taffyLengthPercentage(style.layoutBorder().right), taffyLengthPercentage(style.layoutBorder().top),
                taffyLengthPercentage(style.layoutBorder().bottom)));
        AlignItems alignItems = style.layoutAlignItems();
        if (alignItems != null) {
            builder.alignItems(taffyAlignItems(alignItems));
        }
        AlignSelf alignSelf = style.alignSelf();
        if (alignSelf != AlignSelf.Auto) {
            builder.alignSelf(taffyAlignSelf(alignSelf));
        }
        AlignItems justifyItems = style.justifyItems();
        if (justifyItems != null) {
            builder.justifyItems(taffyAlignItems(justifyItems));
        }
        AlignSelf justifySelf = style.justifySelf();
        if (justifySelf != AlignSelf.Auto) {
            builder.justifySelf(taffyAlignSelf(justifySelf));
        }
        com.github.ruediste.jreact.layout.AlignContent alignContent = style.alignContent();
        if (alignContent != null) {
            builder.alignContent(taffyAlignContent(alignContent));
        }
        com.github.ruediste.jreact.layout.JustifyContent justifyContent = style.layoutJustifyContent();
        if (justifyContent != null) {
            builder.justifyContent(taffyJustifyContent(justifyContent));
        }
        builder.gap(new com.antepod.taffy.geometry.Size<>(taffyLengthPercentage(style.gap().width),
                taffyLengthPercentage(style.gap().height)));
        builder.textAlign(taffyTextAlign(style.layoutTextAlign()));
        builder.flexDirection(taffyFlexDirection(style.layoutFlexDirection()));
        builder.flexWrap(taffyFlexWrap(style.layoutFlexWrap()));
        builder.flexBasis(taffyDimension(style.flexBasis()));
        builder.flexGrow(style.flexGrow());
        builder.flexShrink(style.flexShrink());
        builder.gridTemplateRows(taffyGridTemplateComponents(style.gridTemplateRows()));
        builder.gridTemplateColumns(taffyGridTemplateComponents(style.gridTemplateColumns()));
        builder.gridAutoRows(taffyTrackSizingFunctions(style.gridAutoRows()));
        builder.gridAutoColumns(taffyTrackSizingFunctions(style.gridAutoColumns()));
        builder.gridAutoFlow(taffyGridAutoFlow(style.gridAutoFlow()));
        builder.gridTemplateAreas(Optional.ofNullable(taffyGridTemplateAreas(style)));
        builder.gridTemplateColumnNames(style.gridTemplateColumnNames());
        builder.gridTemplateRowNames(style.gridTemplateRowNames());
        builder.gridRow(new com.antepod.taffy.geometry.Line<>(taffyGridPlacement(style.gridRow().start),
                taffyGridPlacement(style.gridRow().end)));
        builder.gridColumn(new com.antepod.taffy.geometry.Line<>(taffyGridPlacement(style.gridColumn().start),
                taffyGridPlacement(style.gridColumn().end)));
        return builder.build();
    }

    private static com.antepod.taffy.style.GridTemplateAreas taffyGridTemplateAreas(Style style) {
        List<com.antepod.taffy.style.GridTemplateArea> areas = new ArrayList<>();
        for (com.github.ruediste.jreact.layout.GridTemplateArea area : style.getGridTemplateAreas()) {
            areas.add(new com.antepod.taffy.style.GridTemplateArea(area.name, area.rowStart, area.rowEnd,
                    area.columnStart, area.columnEnd));
        }
        return new com.antepod.taffy.style.GridTemplateAreas(areas, style.getGridTemplateAreaRowCount(),
                style.getGridTemplateAreaColumnCount());
    }

    private static List<com.antepod.taffy.style.GridTemplateComponent> taffyGridTemplateComponents(
            List<com.github.ruediste.jreact.layout.GridTemplateComponent> components) {
        List<com.antepod.taffy.style.GridTemplateComponent> result = new ArrayList<>(components.size());
        for (com.github.ruediste.jreact.layout.GridTemplateComponent component : components) {
            result.add(taffyGridTemplateComponent(component));
        }
        return result;
    }

    private static com.antepod.taffy.style.GridTemplateComponent taffyGridTemplateComponent(
            com.github.ruediste.jreact.layout.GridTemplateComponent component) {
        if (component.isSingle()) {
            return com.antepod.taffy.style.GridTemplateComponent.single(taffyTrackSizingFunction(component.track));
        }
        com.antepod.taffy.style.RepetitionCount count;
        if (component.repetition == com.github.ruediste.jreact.layout.GridTemplateComponent.Repetition.AUTO_FILL) {
            count = com.antepod.taffy.style.RepetitionCount.AUTO_FILL;
        } else if (component.repetition == com.github.ruediste.jreact.layout.GridTemplateComponent.Repetition.AUTO_FIT) {
            count = com.antepod.taffy.style.RepetitionCount.AUTO_FIT;
        } else {
            count = com.antepod.taffy.style.RepetitionCount.count(component.repeatCount);
        }
        List<com.antepod.taffy.style.TrackSizingFunction> tracks = new ArrayList<>();
        for (com.github.ruediste.jreact.layout.TrackSizingFunction track : component.tracks) {
            tracks.add(taffyTrackSizingFunction(track));
        }
        return com.antepod.taffy.style.GridTemplateComponent.repeat(count, tracks);
    }

    private static com.antepod.taffy.style.TrackSizingFunction taffyTrackSizingFunction(
            com.github.ruediste.jreact.layout.TrackSizingFunction track) {
        return com.antepod.taffy.style.TrackSizingFunction.minmax(taffyMinTrack(track.min), taffyMaxTrack(track.max));
    }

    private static com.antepod.taffy.style.MinTrackSizingFunction taffyMinTrack(
            com.github.ruediste.jreact.layout.MinTrackSizing min) {
        return switch (min.kind) {
            case LENGTH -> com.antepod.taffy.style.MinTrackSizingFunction.length(min.value);
            case PERCENT -> com.antepod.taffy.style.MinTrackSizingFunction.percent(min.value / 100f);
            case MIN_CONTENT -> com.antepod.taffy.style.MinTrackSizingFunction.minContent();
            case MAX_CONTENT -> com.antepod.taffy.style.MinTrackSizingFunction.maxContent();
            case AUTO -> com.antepod.taffy.style.MinTrackSizingFunction.auto();
        };
    }

    private static com.antepod.taffy.style.MaxTrackSizingFunction taffyMaxTrack(
            com.github.ruediste.jreact.layout.MaxTrackSizing max) {
        return switch (max.kind) {
            case LENGTH -> com.antepod.taffy.style.MaxTrackSizingFunction.length(max.value);
            case PERCENT -> com.antepod.taffy.style.MaxTrackSizingFunction.percent(max.value / 100f);
            case MIN_CONTENT -> com.antepod.taffy.style.MaxTrackSizingFunction.minContent();
            case MAX_CONTENT -> com.antepod.taffy.style.MaxTrackSizingFunction.maxContent();
            case FIT_CONTENT -> com.antepod.taffy.style.MaxTrackSizingFunction.fitContentPx(max.value);
            case FLEX -> com.antepod.taffy.style.MaxTrackSizingFunction.fr(max.value);
            case AUTO -> com.antepod.taffy.style.MaxTrackSizingFunction.auto();
        };
    }

    private static List<com.antepod.taffy.style.TrackSizingFunction> taffyTrackSizingFunctions(
            List<com.github.ruediste.jreact.layout.TrackSizingFunction> tracks) {
        List<com.antepod.taffy.style.TrackSizingFunction> result = new ArrayList<>(tracks.size());
        for (com.github.ruediste.jreact.layout.TrackSizingFunction track : tracks) {
            result.add(taffyTrackSizingFunction(track));
        }
        return result;
    }

    private static com.antepod.taffy.style.GridPlacement taffyGridPlacement(
            com.github.ruediste.jreact.layout.GridPlacement placement) {
        return switch (placement.kind) {
            case AUTO -> com.antepod.taffy.style.GridPlacement.auto();
            case LINE -> com.antepod.taffy.style.GridPlacement.line(placement.line);
            case SPAN -> com.antepod.taffy.style.GridPlacement.span(placement.span);
        };
    }

    private static com.antepod.taffy.style.Display taffyDisplay(com.github.ruediste.jreact.layout.Display display) {
        return switch (display) {
            case Block -> com.antepod.taffy.style.Display.BLOCK;
            case Flex -> com.antepod.taffy.style.Display.FLEX;
            case Grid -> com.antepod.taffy.style.Display.GRID;
            case None -> com.antepod.taffy.style.Display.NONE;
            case FlowRoot -> com.antepod.taffy.style.Display.FLOW_ROOT;
        };
    }

    private static com.antepod.taffy.style.BoxSizing taffyBoxSizing(BoxSizing boxSizing) {
        return switch (boxSizing) {
            case BorderBox -> com.antepod.taffy.style.BoxSizing.BORDER_BOX;
            case ContentBox -> com.antepod.taffy.style.BoxSizing.CONTENT_BOX;
        };
    }

    private static com.antepod.taffy.style.Direction taffyDirection(
            com.github.ruediste.jreact.layout.Direction direction) {
        return switch (direction) {
            case Ltr -> com.antepod.taffy.style.Direction.LTR;
            case Rtl -> com.antepod.taffy.style.Direction.RTL;
        };
    }

    private static com.antepod.taffy.style.Overflow taffyOverflow(Overflow overflow) {
        return switch (overflow) {
            case Visible -> com.antepod.taffy.style.Overflow.VISIBLE;
            case Clip -> com.antepod.taffy.style.Overflow.CLIP;
            case Hidden -> com.antepod.taffy.style.Overflow.HIDDEN;
            case Scroll -> com.antepod.taffy.style.Overflow.SCROLL;
        };
    }

    private static com.antepod.taffy.style.Float taffyFloatValue(Float floatMode) {
        return switch (floatMode) {
            case Left -> com.antepod.taffy.style.Float.LEFT;
            case Right -> com.antepod.taffy.style.Float.RIGHT;
            case None -> com.antepod.taffy.style.Float.NONE;
        };
    }

    private static com.antepod.taffy.style.Clear taffyClear(Clear clear) {
        return switch (clear) {
            case Left -> com.antepod.taffy.style.Clear.LEFT;
            case Right -> com.antepod.taffy.style.Clear.RIGHT;
            case Both -> com.antepod.taffy.style.Clear.BOTH;
            case None -> com.antepod.taffy.style.Clear.NONE;
        };
    }

    private static com.antepod.taffy.style.Position taffyPosition(Position position) {
        return switch (position) {
            case Relative -> com.antepod.taffy.style.Position.RELATIVE;
            case Absolute -> com.antepod.taffy.style.Position.ABSOLUTE;
            case Static -> com.antepod.taffy.style.Position.RELATIVE;
            case Fixed -> com.antepod.taffy.style.Position.ABSOLUTE;
            case Sticky -> com.antepod.taffy.style.Position.RELATIVE;
        };
    }

    private static com.antepod.taffy.style.TextAlign taffyTextAlign(TextAlign textAlign) {
        return switch (textAlign) {
            case Auto -> com.antepod.taffy.style.TextAlign.AUTO;
            case LegacyLeft -> com.antepod.taffy.style.TextAlign.LEGACY_LEFT;
            case LegacyRight -> com.antepod.taffy.style.TextAlign.LEGACY_RIGHT;
            case LegacyCenter -> com.antepod.taffy.style.TextAlign.LEGACY_CENTER;
        };
    }

    private static com.antepod.taffy.style.AlignItems taffyAlignItems(AlignItems alignItems) {
        return switch (alignItems) {
            case Start -> com.antepod.taffy.style.AlignItems.START;
            case End -> com.antepod.taffy.style.AlignItems.END;
            case FlexStart -> com.antepod.taffy.style.AlignItems.FLEX_START;
            case FlexEnd -> com.antepod.taffy.style.AlignItems.FLEX_END;
            case Center -> com.antepod.taffy.style.AlignItems.CENTER;
            case Baseline -> com.antepod.taffy.style.AlignItems.BASELINE;
            case Stretch -> com.antepod.taffy.style.AlignItems.STRETCH;
            case SelfStart -> com.antepod.taffy.style.AlignItems.SELF_START;
            case SelfEnd -> com.antepod.taffy.style.AlignItems.SELF_END;
            case SafeStart -> com.antepod.taffy.style.AlignItems.SAFE_START;
            case SafeEnd -> com.antepod.taffy.style.AlignItems.SAFE_END;
            case SafeFlexStart -> com.antepod.taffy.style.AlignItems.SAFE_FLEX_START;
            case SafeFlexEnd -> com.antepod.taffy.style.AlignItems.SAFE_FLEX_END;
            case SafeCenter -> com.antepod.taffy.style.AlignItems.SAFE_CENTER;
            case SafeSelfStart -> com.antepod.taffy.style.AlignItems.SAFE_SELF_START;
            case SafeSelfEnd -> com.antepod.taffy.style.AlignItems.SAFE_SELF_END;
        };
    }

    private static com.antepod.taffy.style.AlignItems taffyAlignSelf(AlignSelf alignSelf) {
        return switch (alignSelf) {
            case Start -> com.antepod.taffy.style.AlignItems.START;
            case End -> com.antepod.taffy.style.AlignItems.END;
            case FlexStart -> com.antepod.taffy.style.AlignItems.FLEX_START;
            case FlexEnd -> com.antepod.taffy.style.AlignItems.FLEX_END;
            case Center -> com.antepod.taffy.style.AlignItems.CENTER;
            case Baseline -> com.antepod.taffy.style.AlignItems.BASELINE;
            case Stretch -> com.antepod.taffy.style.AlignItems.STRETCH;
            case SelfStart -> com.antepod.taffy.style.AlignItems.SELF_START;
            case SelfEnd -> com.antepod.taffy.style.AlignItems.SELF_END;
            case SafeStart -> com.antepod.taffy.style.AlignItems.SAFE_START;
            case SafeEnd -> com.antepod.taffy.style.AlignItems.SAFE_END;
            case SafeFlexStart -> com.antepod.taffy.style.AlignItems.SAFE_FLEX_START;
            case SafeFlexEnd -> com.antepod.taffy.style.AlignItems.SAFE_FLEX_END;
            case SafeCenter -> com.antepod.taffy.style.AlignItems.SAFE_CENTER;
            case SafeSelfStart -> com.antepod.taffy.style.AlignItems.SAFE_SELF_START;
            case SafeSelfEnd -> com.antepod.taffy.style.AlignItems.SAFE_SELF_END;
            case Auto -> com.antepod.taffy.style.AlignItems.START;
        };
    }

    private static com.antepod.taffy.style.AlignContent taffyAlignContent(
            com.github.ruediste.jreact.layout.AlignContent alignContent) {
        return switch (alignContent) {
            case Start -> com.antepod.taffy.style.AlignContent.START;
            case End -> com.antepod.taffy.style.AlignContent.END;
            case FlexStart -> com.antepod.taffy.style.AlignContent.FLEX_START;
            case FlexEnd -> com.antepod.taffy.style.AlignContent.FLEX_END;
            case Center -> com.antepod.taffy.style.AlignContent.CENTER;
            case Stretch -> com.antepod.taffy.style.AlignContent.STRETCH;
            case SpaceBetween -> com.antepod.taffy.style.AlignContent.SPACE_BETWEEN;
            case SpaceAround -> com.antepod.taffy.style.AlignContent.SPACE_AROUND;
            case SpaceEvenly -> com.antepod.taffy.style.AlignContent.SPACE_EVENLY;
            case SafeStart -> com.antepod.taffy.style.AlignContent.SAFE_START;
            case SafeEnd -> com.antepod.taffy.style.AlignContent.SAFE_END;
            case SafeFlexStart -> com.antepod.taffy.style.AlignContent.SAFE_FLEX_START;
            case SafeFlexEnd -> com.antepod.taffy.style.AlignContent.SAFE_FLEX_END;
            case SafeCenter -> com.antepod.taffy.style.AlignContent.SAFE_CENTER;
        };
    }

    private static com.antepod.taffy.style.AlignContent taffyJustifyContent(
            com.github.ruediste.jreact.layout.JustifyContent justifyContent) {
        return switch (justifyContent) {
            case Start -> com.antepod.taffy.style.AlignContent.START;
            case End -> com.antepod.taffy.style.AlignContent.END;
            case FlexStart -> com.antepod.taffy.style.AlignContent.FLEX_START;
            case FlexEnd -> com.antepod.taffy.style.AlignContent.FLEX_END;
            case Center -> com.antepod.taffy.style.AlignContent.CENTER;
            case Stretch -> com.antepod.taffy.style.AlignContent.STRETCH;
            case SpaceBetween -> com.antepod.taffy.style.AlignContent.SPACE_BETWEEN;
            case SpaceAround -> com.antepod.taffy.style.AlignContent.SPACE_AROUND;
            case SpaceEvenly -> com.antepod.taffy.style.AlignContent.SPACE_EVENLY;
            case SafeStart -> com.antepod.taffy.style.AlignContent.SAFE_START;
            case SafeEnd -> com.antepod.taffy.style.AlignContent.SAFE_END;
            case SafeFlexStart -> com.antepod.taffy.style.AlignContent.SAFE_FLEX_START;
            case SafeFlexEnd -> com.antepod.taffy.style.AlignContent.SAFE_FLEX_END;
            case SafeCenter -> com.antepod.taffy.style.AlignContent.SAFE_CENTER;
        };
    }

    private static com.antepod.taffy.style.Dimension taffyDimension(Dimension dimension) {
        return switch (dimension.kind) {
            case LENGTH -> com.antepod.taffy.style.Dimension.length(dimension.value);
            case PERCENT -> com.antepod.taffy.style.Dimension.percent(dimension.value / 100f);
            case AUTO -> com.antepod.taffy.style.Dimension.AUTO;
        };
    }

    private static com.antepod.taffy.style.Dimension taffyDimension(LengthPercentageAuto lengthPercentageAuto) {
        return switch (lengthPercentageAuto.kind) {
            case LENGTH -> com.antepod.taffy.style.Dimension.length(lengthPercentageAuto.value);
            case PERCENT -> com.antepod.taffy.style.Dimension.percent(lengthPercentageAuto.value / 100f);
            case AUTO -> com.antepod.taffy.style.Dimension.AUTO;
        };
    }

    private static com.antepod.taffy.style.LengthPercentage taffyLengthPercentage(
            LengthPercentage lengthPercentage) {
        return switch (lengthPercentage.kind) {
            case LENGTH -> com.antepod.taffy.style.LengthPercentage.length(lengthPercentage.value);
            case PERCENT -> com.antepod.taffy.style.LengthPercentage.percent(lengthPercentage.value / 100f);
        };
    }

    private static com.antepod.taffy.style.LengthPercentageAuto taffyLengthPercentageAuto(
            LengthPercentageAuto lengthPercentageAuto) {
        return switch (lengthPercentageAuto.kind) {
            case LENGTH -> com.antepod.taffy.style.LengthPercentageAuto.length(lengthPercentageAuto.value);
            case PERCENT -> com.antepod.taffy.style.LengthPercentageAuto.percent(lengthPercentageAuto.value / 100f);
            case AUTO -> com.antepod.taffy.style.LengthPercentageAuto.AUTO;
        };
    }

    private static com.antepod.taffy.style.FlexDirection taffyFlexDirection(
            com.github.ruediste.jreact.layout.FlexDirection flexDirection) {
        return switch (flexDirection) {
            case Row -> com.antepod.taffy.style.FlexDirection.ROW;
            case Column -> com.antepod.taffy.style.FlexDirection.COLUMN;
            case RowReverse -> com.antepod.taffy.style.FlexDirection.ROW_REVERSE;
            case ColumnReverse -> com.antepod.taffy.style.FlexDirection.COLUMN_REVERSE;
        };
    }

    private static com.antepod.taffy.style.FlexWrap taffyFlexWrap(
            com.github.ruediste.jreact.layout.FlexWrap flexWrap) {
        return switch (flexWrap) {
            case NoWrap -> com.antepod.taffy.style.FlexWrap.NO_WRAP;
            case Wrap -> com.antepod.taffy.style.FlexWrap.WRAP;
            case WrapReverse -> com.antepod.taffy.style.FlexWrap.WRAP_REVERSE;
        };
    }

    private static com.antepod.taffy.style.GridAutoFlow taffyGridAutoFlow(
            com.github.ruediste.jreact.layout.GridAutoFlow gridAutoFlow) {
        return switch (gridAutoFlow) {
            case Row -> com.antepod.taffy.style.GridAutoFlow.ROW;
            case Column -> com.antepod.taffy.style.GridAutoFlow.COLUMN;
            case RowDense -> com.antepod.taffy.style.GridAutoFlow.ROW_DENSE;
            case ColumnDense -> com.antepod.taffy.style.GridAutoFlow.COLUMN_DENSE;
        };
    }

    // -- taffy -> jreact conversion -------------------------------------------

    // -- taffy -> jreact conversion -------------------------------------------

    private static com.antepod.taffy.style.AvailableSpace toTaffy(AvailableSpace availableSpace) {
        return switch (availableSpace.kind) {
            case DEFINITE -> com.antepod.taffy.style.AvailableSpace.definite(availableSpace.value);
            case MIN_CONTENT -> com.antepod.taffy.style.AvailableSpace.MIN_CONTENT;
            case MAX_CONTENT -> com.antepod.taffy.style.AvailableSpace.MAX_CONTENT;
        };
    }

    private static com.antepod.taffy.geometry.Size<com.antepod.taffy.style.AvailableSpace> toTaffy(
            Size<AvailableSpace> availableSpace) {
        return new com.antepod.taffy.geometry.Size<com.antepod.taffy.style.AvailableSpace>(
                toTaffy(availableSpace.width),
                toTaffy(availableSpace.height));
    }

    private static AvailableSpace toJReact(com.antepod.taffy.style.AvailableSpace availableSpace) {
        return switch (availableSpace.kind()) {
            case DEFINITE -> AvailableSpace.definite(availableSpace.unwrap());
            case MIN_CONTENT -> AvailableSpace.minContent();
            case MAX_CONTENT -> AvailableSpace.maxContent();
        };
    }

    private static Size<AvailableSpace> toJReact(
            com.antepod.taffy.geometry.Size<com.antepod.taffy.style.AvailableSpace> size) {
        return new Size<>(toJReact(size.width), toJReact(size.height));
    }

    private static Size<AvailableSpace> toKnownJReact(
            com.antepod.taffy.geometry.Size<java.util.Optional<java.lang.Float>> size) {
        return new Size<>(toKnownJReact(size.width), toKnownJReact(size.height));
    }

    private static AvailableSpace toKnownJReact(java.util.Optional<java.lang.Float> value) {
        return value.map(AvailableSpace::definite).orElseGet(AvailableSpace::minContent);
    }
}
