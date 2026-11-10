package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.antepod.taffy.geometry.Size;
import com.antepod.taffy.style.AvailableSpace;
import com.antepod.taffy.style.FlexDirection;
import com.antepod.taffy.tree.Layout;
import com.antepod.taffy.tree.NodeId;
import com.antepod.taffy.tree.TaffyTree;
import com.github.ruediste.jreact.TextLayout;
import com.github.ruediste.jreact.nodes.IconNode;
import com.github.ruediste.jreact.nodes.Node;
import com.github.ruediste.jreact.nodes.TextNode;
import com.github.ruediste.jreact.nodes.VisualNode;

public final class LayoutEngine {

    private LayoutEngine() {
    }

    public static void layout(Node root, float viewportWidth, float viewportHeight) {
        if (root == null)
            return;

        Map<Node, NodeId> nodeIds = new HashMap<>();
        Map<NodeId, Node> nodesById = new HashMap<>();

        try (TaffyTree<Node> tree = new TaffyTree<>()) {
            List<NodeId> topLevel = new ArrayList<>();
            for (Node child : visibleChildren(root))
                topLevel.add(build(tree, child, nodeIds, nodesById));

            com.antepod.taffy.style.Style rootStyle = com.antepod.taffy.style.Style.builder()
                    .display(com.antepod.taffy.style.Display.FLEX)
                    .flexDirection(FlexDirection.COLUMN)
                    .size(viewportWidth, viewportHeight)
                    .build();
            NodeId rootId = tree.newWithChildren(rootStyle, topLevel);

            tree.computeLayoutWithMeasure(rootId,
                    new Size<>(AvailableSpace.definite(viewportWidth), AvailableSpace.definite(viewportHeight)),
                    LayoutEngine::measure);

            assignLayouts(tree, rootId, 0, 0, nodesById);
        }

        commitText(root);
    }

    private static List<Node> visibleChildren(Node node) {
        List<Node> result = new ArrayList<>();
        for (Node child : node.children()) {
            if (child instanceof VisualNode)
                result.add(child);
            else
                result.addAll(visibleChildren(child));
        }
        return result;
    }

    private static NodeId build(TaffyTree<Node> tree, Node node, Map<Node, NodeId> nodeIds,
            Map<NodeId, Node> nodesById) {
        List<Node> children = visibleChildren(node);
        NodeId id;
        if (children.isEmpty()) {
            id = tree.newLeafWithContext(com.github.ruediste.jreact.StyleProperties.buildTaffyStyle(node.style), node);
        } else {
            List<NodeId> childIds = new ArrayList<>();
            for (Node child : children)
                childIds.add(build(tree, child, nodeIds, nodesById));
            id = tree.newWithChildren(com.github.ruediste.jreact.StyleProperties.buildTaffyStyle(node.style), childIds);
        }
        nodeIds.put(node, id);
        nodesById.put(id, node);
        return id;
    }

    private static void assignLayouts(TaffyTree<Node> tree, NodeId id, float offsetX, float offsetY,
            Map<NodeId, Node> nodesById) {
        Node node = nodesById.get(id);
        Layout layout = tree.layout(id);
        float nodeX = offsetX;
        float nodeY = offsetY;
        if (node != null) {
            node.setLayout(layout);
            node.bounds.setRect(offsetX + layout.location.x, offsetY + layout.location.y,
                    layout.size.width, layout.size.height);
            nodeX = (float) node.bounds.x;
            nodeY = (float) node.bounds.y;
        }
        for (NodeId child : tree.children(id))
            assignLayouts(tree, child, nodeX, nodeY, nodesById);
    }

    private static Size<java.lang.Float> measure(Size<Optional<java.lang.Float>> knownDimensions,
            Size<AvailableSpace> availableSpace, NodeId id, Optional<Node> context,
            com.antepod.taffy.style.Style style) {
        Node node = context.orElse(null);
        if (node instanceof TextNode text) {
            AvailableSpace knownWidth = AvailableSpace.fromOptional(knownDimensions.width.orElse(null));
            return TextLayout.measure(text.text, text.style, knownWidth, availableSpace.width,
                    knownDimensions.width.orElse(0f));
        }
        if (node instanceof IconNode icon)
            return new Size<>((float) icon.icon.getIconWidth(), (float) icon.icon.getIconHeight());
        return Size.zeroFloat();
    }

    private static void commitText(Node node) {
        if (node instanceof TextNode text)
            TextLayout.commit(text);
        for (Node child : node.children())
            commitText(child);
    }
}
