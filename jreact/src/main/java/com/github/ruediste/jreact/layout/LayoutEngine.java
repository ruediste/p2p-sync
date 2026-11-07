package com.github.ruediste.jreact.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.ruediste.jreact.Style;

/**
 * Computes the layout of {@link Node} trees.
 *
 * <p>
 * The engine is stateless. Styles, children, measurement callbacks and the
 * resulting layout data all belong to the nodes, so several trees can be laid
 * out independently.
 */
public final class LayoutEngine {

    private static final float AUTO = java.lang.Float.NaN;

    private enum Sizing {
        FILL, CONTENT
    }

    private LayoutEngine() {
    }

    public static void computeLayout(Node root, float width, float height) {
        computeLayout(root, Size.of(AvailableSpace.definite(width), AvailableSpace.definite(height)));
    }

    public static void computeLayout(Node root, Size<AvailableSpace> availableSpace) {
        float rootAvailableWidth = availableSpace.width.isDefinite() ? availableSpace.width.value
                : java.lang.Float.POSITIVE_INFINITY;
        float rootAvailableHeight = availableSpace.height.isDefinite() ? availableSpace.height.value
                : java.lang.Float.POSITIVE_INFINITY;
        boolean rootWidthDefinite = availableSpace.width.isDefinite();
        boolean rootHeightDefinite = availableSpace.height.isDefinite();

        reset(root);
        layoutNode(root, 0, 0, rootAvailableWidth, rootAvailableHeight,
                finite(rootAvailableWidth) ? rootAvailableWidth : 0,
                finite(rootAvailableHeight) ? rootAvailableHeight : 0,
                -1, -1, Sizing.FILL, Sizing.CONTENT);

        float marginLeft = solveAuto(root.style.layoutMargin().left, rootAvailableWidth, 0);
        float marginTop = solveAuto(root.style.layoutMargin().top, rootAvailableWidth, 0);
        root.layout.x = marginLeft;
        root.layout.y = marginTop;
        root.layout.absX = root.layout.x;
        root.layout.absY = root.layout.y;

        computeAbsolute(root, root, true);
        layoutOutOfFlow(root, rootAvailableWidth, rootAvailableHeight,
                rootWidthDefinite, rootHeightDefinite);
        computeAbsolute(root, root, true);
        computeContentExtents(root);
    }

    private static void reset(Node node) {
        node.layout.placedOutOfFlow = false;
        node.layout.containingBlock = null;
        node.layout.detailedGridInfo = null;
        node.layout.usedContentHeight = 0;
        node.layout.usedContentWidth = 0;
        for (Node child : node.children) {
            reset(child);
        }
    }

    private static void layoutOutOfFlow(Node root, float rootAvailableWidth,
            float rootAvailableHeight, boolean rootWidthDefinite, boolean rootHeightDefinite) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node candidate : preorder(root)) {
                if (isOutOfFlow(candidate) && !candidate.layout.placedOutOfFlow) {
                    placeOutOfFlow(candidate, root, rootAvailableWidth, rootAvailableHeight,
                            rootWidthDefinite, rootHeightDefinite);
                    candidate.layout.placedOutOfFlow = true;
                    changed = true;
                }
            }
        }
    }

    private static List<Node> preorder(Node node) {
        List<Node> result = new ArrayList<>();
        collect(node, result);
        return result;
    }

    private static void collect(Node node, List<Node> result) {
        result.add(node);
        for (Node child : node.children) {
            collect(child, result);
        }
    }

    private static boolean isOutOfFlow(Node node) {
        return node.style.position() == Position.Absolute || node.style.position() == Position.Fixed;
    }

    private static void placeOutOfFlow(Node node, Node root, float rootAvailableWidth,
            float rootAvailableHeight, boolean rootWidthDefinite, boolean rootHeightDefinite) {
        Style style = node.style;
        Node containingBlock = null;
        if (style.position() != Position.Fixed) {
            Node ancestor = node.parent;
            while (ancestor != null && ancestor.style.position() == Position.Static) {
                ancestor = ancestor.parent;
            }
            containingBlock = ancestor;
        }
        node.layout.containingBlock = containingBlock;

        float cbReferenceX;
        float cbReferenceY;
        float cbWidth;
        float cbHeight;
        float reportOffsetX;
        float reportOffsetY;

        if (containingBlock != null) {
            reportOffsetX = containingBlock.layout.absX;
            reportOffsetY = containingBlock.layout.absY;
            cbReferenceX = containingBlock.layout.absX + containingBlock.layout.border.left
                    + containingBlock.layout.padding.left;
            cbReferenceY = containingBlock.layout.absY + containingBlock.layout.border.top
                    + containingBlock.layout.padding.top;
            cbWidth = Math.max(0, containingBlock.layout.width
                    - containingBlock.layout.border.left - containingBlock.layout.border.right
                    - containingBlock.layout.padding.left - containingBlock.layout.padding.right);
            cbHeight = Math.max(0, containingBlock.layout.height
                    - containingBlock.layout.border.top - containingBlock.layout.border.bottom
                    - containingBlock.layout.padding.top - containingBlock.layout.padding.bottom);
        } else {
            reportOffsetX = root.layout.absX;
            reportOffsetY = root.layout.absY;
            cbReferenceX = 0;
            cbReferenceY = 0;
            if (rootWidthDefinite) {
                cbWidth = rootAvailableWidth;
            } else {
                cbReferenceX = root.layout.absX + root.layout.border.left;
                cbWidth = Math.max(0, root.layout.width - root.layout.border.left
                        - root.layout.border.right);
            }
            if (rootHeightDefinite) {
                cbHeight = rootAvailableHeight;
            } else {
                cbReferenceY = root.layout.absY + root.layout.border.top;
                cbHeight = Math.max(0, root.layout.height - root.layout.border.top
                        - root.layout.border.bottom);
            }
        }

        float marginLeft = solveAuto(style.layoutMargin().left, cbWidth, 0);
        float marginRight = solveAuto(style.layoutMargin().right, cbWidth, 0);
        float marginTop = solveAuto(style.layoutMargin().top, cbWidth, 0);
        float marginBottom = solveAuto(style.layoutMargin().bottom, cbWidth, 0);

        float left = solveAuto(style.layoutInset().left, cbWidth, AUTO);
        float right = solveAuto(style.layoutInset().right, cbWidth, AUTO);
        float top = solveAuto(style.layoutInset().top, cbHeight, AUTO);
        float bottom = solveAuto(style.layoutInset().bottom, cbHeight, AUTO);

        float forcedWidth = -1;
        float forcedHeight = -1;
        float width = resolveDimension(style.size().width, cbWidth, 0, style.boxSizing());
        float height = resolveDimension(style.size().height, cbHeight, 0, style.boxSizing());
        if (!finite(width) && finite(left) && finite(right)) {
            forcedWidth = Math.max(0, cbWidth - left - right - marginLeft - marginRight);
        }
        if (!finite(height) && finite(top) && finite(bottom)) {
            forcedHeight = Math.max(0, cbHeight - top - bottom - marginTop - marginBottom);
        }

        float reportX;
        if (finite(left)) {
            reportX = cbReferenceX + left + marginLeft - reportOffsetX;
        } else if (finite(right)) {
            float usedWidth = forcedWidth >= 0 ? forcedWidth : finite(width) ? width : 0;
            reportX = cbReferenceX + cbWidth - right - usedWidth - marginRight - reportOffsetX;
        } else {
            reportX = 0;
        }

        float reportY;
        if (finite(top)) {
            reportY = cbReferenceY + top + marginTop - reportOffsetY;
        } else if (finite(bottom)) {
            float usedHeight = forcedHeight >= 0 ? forcedHeight : finite(height) ? height : 0;
            reportY = cbReferenceY + cbHeight - bottom - usedHeight - marginBottom - reportOffsetY;
        } else {
            reportY = 0;
        }

        layoutNode(node, reportX, reportY, cbWidth, cbHeight, cbWidth, cbHeight,
                forcedWidth, forcedHeight, Sizing.CONTENT, Sizing.CONTENT);
        node.layout.absX = reportOffsetX + reportX;
        node.layout.absY = reportOffsetY + reportY;
        for (Node child : node.children) {
            child.layout.absX = node.layout.absX + child.layout.x;
            child.layout.absY = node.layout.absY + child.layout.y;
        }
    }

    private static Size<java.lang.Float> layoutNode(Node node, float x, float y, float availableWidth,
            float availableHeight, float containingBlockWidth, float containingBlockHeight,
            float forcedWidth, float forcedHeight, Sizing widthSizing, Sizing heightSizing) {

        Style style = node.style;
        detailReset(node);

        if (style.display() == Display.None) {
            node.layout.x = x;
            node.layout.y = y;
            node.layout.width = 0;
            node.layout.height = 0;
            node.layout.border = Rect.all(0f);
            node.layout.padding = Rect.all(0f);
            node.layout.margin = Rect.all(0f);
            return Size.of(0f, 0f);
        }

        float referenceWidth = finite(containingBlockWidth) ? containingBlockWidth : 0;
        float referenceHeight = finite(containingBlockHeight) ? containingBlockHeight : 0;

        float paddingLeft = style.layoutPadding().left.resolve(referenceWidth);
        float paddingRight = style.layoutPadding().right.resolve(referenceWidth);
        float paddingTop = style.layoutPadding().top.resolve(referenceWidth);
        float paddingBottom = style.layoutPadding().bottom.resolve(referenceWidth);
        float borderLeft = style.layoutBorder().left.resolve(referenceWidth);
        float borderRight = style.layoutBorder().right.resolve(referenceWidth);
        float borderTop = style.layoutBorder().top.resolve(referenceWidth);
        float borderBottom = style.layoutBorder().bottom.resolve(referenceWidth);
        float horizontalExtra = paddingLeft + paddingRight + borderLeft + borderRight;
        float verticalExtra = paddingTop + paddingBottom + borderTop + borderBottom;

        float specifiedWidth = resolveDimension(style.size().width, referenceWidth, horizontalExtra,
                style.boxSizing());
        float specifiedHeight = resolveDimension(style.size().height, referenceHeight, verticalExtra,
                style.boxSizing());

        if (style.aspectRatio() != null && style.aspectRatio() > 0) {
            if (finite(specifiedWidth) && !finite(specifiedHeight)) {
                specifiedHeight = specifiedWidth / style.aspectRatio();
            } else if (!finite(specifiedWidth) && finite(specifiedHeight)) {
                specifiedWidth = specifiedHeight * style.aspectRatio();
            }
        }

        float width;
        if (forcedWidth >= 0) {
            width = forcedWidth;
        } else if (finite(specifiedWidth)) {
            width = specifiedWidth;
        } else if (widthSizing == Sizing.FILL && finite(availableWidth)) {
            width = availableWidth;
        } else {
            width = intrinsicWidth(node, availableWidth, availableHeight, referenceWidth,
                    referenceHeight) + horizontalExtra;
        }
        width = clampDimension(width, style.minSize().width, style.maxSize().width, referenceWidth,
                horizontalExtra, style.boxSizing());
        width = Math.max(0, width);
        float contentWidth = Math.max(0, width - horizontalExtra);

        float height;
        if (forcedHeight >= 0) {
            height = forcedHeight;
        } else if (finite(specifiedHeight)) {
            height = specifiedHeight;
        } else if (style.aspectRatio() != null && style.aspectRatio() > 0 && finite(width)) {
            height = width / style.aspectRatio();
        } else if (heightSizing == Sizing.FILL && finite(availableHeight)) {
            height = availableHeight;
        } else {
            height = intrinsicHeight(node, contentWidth, availableWidth, availableHeight,
                    referenceWidth, referenceHeight) + verticalExtra;
        }
        height = clampDimension(height, style.minSize().height, style.maxSize().height, referenceHeight,
                verticalExtra, style.boxSizing());
        height = Math.max(0, height);
        float contentHeight = Math.max(0, height - verticalExtra);

        node.layout.x = x;
        node.layout.y = y;
        node.layout.width = width;
        node.layout.height = height;
        node.layout.border = new Rect<>(borderLeft, borderRight, borderTop, borderBottom);
        node.layout.padding = new Rect<>(paddingLeft, paddingRight, paddingTop, paddingBottom);
        node.layout.margin = new Rect<>(
                solveAuto(style.layoutMargin().left, referenceWidth, 0),
                solveAuto(style.layoutMargin().right, referenceWidth, 0),
                solveAuto(style.layoutMargin().top, referenceWidth, 0),
                solveAuto(style.layoutMargin().bottom, referenceWidth, 0));
        node.layout.scrollbarWidth = style.scrollbarWidth();
        node.layout.scrollbarHeight = 0;

        boolean leafMeasured = false;
        if (node.children.isEmpty() && node.measure != null && style.display() != Display.None
                && forcedWidth < 0 && forcedHeight < 0) {
            float knownContentWidth = finite(specifiedWidth)
                    ? Math.max(0, specifiedWidth - horizontalExtra)
                    : AUTO;
            float knownContentHeight = finite(specifiedHeight)
                    ? Math.max(0, specifiedHeight - verticalExtra)
                    : AUTO;
            float availableContentWidth = finite(knownContentWidth) ? knownContentWidth
                    : Math.max(0, availableWidth - horizontalExtra);
            float availableContentHeight = finite(knownContentHeight) ? knownContentHeight
                    : Math.max(0, availableHeight - verticalExtra);
            Size<java.lang.Float> measured = node.measure.measure(
                    Size.of(available(knownContentWidth), available(knownContentHeight)),
                    Size.of(available(availableContentWidth), available(availableContentHeight)),
                    node);
            if (measured != null) {
                width = forcedWidth >= 0 ? forcedWidth : measured.width + horizontalExtra;
                height = forcedHeight >= 0 ? forcedHeight : measured.height + verticalExtra;
                width = Math.max(0, width);
                height = Math.max(0, height);
                node.layout.width = width;
                node.layout.height = height;
                contentWidth = Math.max(0, width - horizontalExtra);
                contentHeight = Math.max(0, height - verticalExtra);
                leafMeasured = true;
            }
        }

        switch (style.display()) {
            case Flex -> layoutFlex(node, contentWidth, contentHeight);
            case Grid -> layoutGrid(node, contentWidth, contentHeight);
            case Block, FlowRoot -> layoutBlock(node, contentWidth, contentHeight);
            case None -> {
            }
        }

        boolean heightAuto = forcedHeight < 0 && !finite(specifiedHeight)
                && !(style.aspectRatio() != null && style.aspectRatio() > 0);
        if (heightAuto && !leafMeasured && style.display() != Display.None) {
            float contentBottom = 0;
            float contentRight = 0;
            for (Node child : node.children) {
                if (child.style.display() == Display.None || isOutOfFlow(child)) {
                    continue;
                }
                contentBottom = Math.max(contentBottom,
                        child.layout.y + child.layout.height
                                + solveAuto(child.style.layoutMargin().bottom, referenceWidth, 0));
                contentRight = Math.max(contentRight,
                        child.layout.x + child.layout.width
                                + solveAuto(child.style.layoutMargin().right, referenceWidth, 0));
            }
            if (contentBottom > 0 || contentRight > 0) {
                height = Math.max(height, contentBottom + paddingBottom + borderBottom);
                node.layout.height = height;
                contentHeight = Math.max(0, height - verticalExtra);
            }
        }

        node.layout.usedContentWidth = contentWidth;
        node.layout.usedContentHeight = contentHeight;
        return Size.of(width, height);
    }

    private static void detailReset(Node node) {
        node.layout.detailedGridInfo = null;
    }

    private static AvailableSpace available(float value) {
        return finite(value) ? AvailableSpace.definite(value) : AvailableSpace.maxContent();
    }

    private static float resolveDimension(Dimension dimension, float basis, float extra,
            BoxSizing boxSizing) {
        if (dimension == null || dimension.isAuto()) {
            return AUTO;
        }
        float value = dimension.resolve(basis);
        return boxSizing == BoxSizing.ContentBox ? value + extra : value;
    }

    private static float clampDimension(float value, Dimension min, Dimension max, float basis,
            float extra, BoxSizing boxSizing) {
        float result = value;
        if (min != null && !min.isAuto()) {
            float minimum = resolveDimension(min, basis, extra, boxSizing);
            if (finite(minimum) && result < minimum) {
                result = minimum;
            }
        }
        if (max != null && !max.isAuto()) {
            float maximum = resolveDimension(max, basis, extra, boxSizing);
            if (finite(maximum) && result > maximum) {
                result = maximum;
            }
        }
        return result;
    }

    private static float solveAuto(LengthPercentageAuto value, float basis, float fallback) {
        if (value == null || value.isAuto()) {
            return fallback;
        }
        return value.resolve(basis);
    }

    private static boolean finite(float value) {
        return !java.lang.Float.isNaN(value) && !java.lang.Float.isInfinite(value);
    }

    private static float intrinsicWidth(Node node, float availableWidth, float availableHeight,
            float referenceWidth, float referenceHeight) {
        Style style = node.style;
        float paddingLeft = style.layoutPadding().left.resolve(referenceWidth);
        float paddingRight = style.layoutPadding().right.resolve(referenceWidth);
        float borderLeft = style.layoutBorder().left.resolve(referenceWidth);
        float borderRight = style.layoutBorder().right.resolve(referenceWidth);
        float extra = paddingLeft + paddingRight + borderLeft + borderRight;

        if (!style.size().width.isAuto()) {
            return Math.max(0,
                    resolveDimension(style.size().width, referenceWidth, extra, style.boxSizing()) - extra);
        }
        if (node.children.isEmpty()) {
            return 0;
        }
        boolean row = style.layoutFlexDirection() == FlexDirection.Row
                || style.layoutFlexDirection() == FlexDirection.RowReverse;
        if (style.display() == Display.Flex) {
            float mainGap = row ? style.gap().width.resolve(availableWidth) : 0;
            float total = 0;
            float max = 0;
            for (Node child : node.children) {
                if (child.style.display() == Display.None || isOutOfFlow(child)) {
                    continue;
                }
                float childWidth = intrinsicWidth(child, availableWidth, availableHeight,
                        availableWidth, availableHeight);
                childWidth += solveAuto(child.style.layoutMargin().left, availableWidth, 0)
                        + solveAuto(child.style.layoutMargin().right, availableWidth, 0);
                if (row) {
                    total += childWidth + mainGap;
                } else {
                    max = Math.max(max, childWidth);
                }
            }
            return row ? Math.max(0, total - mainGap) : max;
        }
        if (style.display() == Display.Grid) {
            float total = 0;
            for (GridTemplateComponent component : style.gridTemplateColumns()) {
                if (component.isSingle()) {
                    total += fixedTrackSize(component.track, availableWidth);
                }
            }
            return total;
        }
        float max = 0;
        for (Node child : node.children) {
            if (child.style.display() == Display.None || isOutOfFlow(child)) {
                continue;
            }
            max = Math.max(max, intrinsicWidth(child, availableWidth, availableHeight,
                    availableWidth, availableHeight)
                    + solveAuto(child.style.layoutMargin().left, availableWidth, 0)
                    + solveAuto(child.style.layoutMargin().right, availableWidth, 0));
        }
        return max;
    }

    private static float intrinsicHeight(Node node, float contentWidth, float availableWidth,
            float availableHeight, float referenceWidth, float referenceHeight) {
        Style style = node.style;
        float paddingTop = style.layoutPadding().top.resolve(referenceWidth);
        float paddingBottom = style.layoutPadding().bottom.resolve(referenceWidth);
        float borderTop = style.layoutBorder().top.resolve(referenceWidth);
        float borderBottom = style.layoutBorder().bottom.resolve(referenceWidth);
        float extra = paddingTop + paddingBottom + borderTop + borderBottom;

        if (!style.size().height.isAuto()) {
            return Math.max(0,
                    resolveDimension(style.size().height, referenceHeight, extra, style.boxSizing()) - extra);
        }
        if (node.children.isEmpty()) {
            return 0;
        }
        boolean row = style.layoutFlexDirection() == FlexDirection.Row
                || style.layoutFlexDirection() == FlexDirection.RowReverse;
        if (style.display() == Display.Flex) {
            float gap = row ? style.gap().height.resolve(availableHeight) : style.gap().height.resolve(
                    availableHeight);
            float total = 0;
            float max = 0;
            for (Node child : node.children) {
                if (child.style.display() == Display.None || isOutOfFlow(child)) {
                    continue;
                }
                float childHeight = intrinsicHeight(child, contentWidth, availableWidth,
                        availableHeight, availableWidth, availableHeight);
                childHeight += solveAuto(child.style.layoutMargin().top, availableWidth, 0)
                        + solveAuto(child.style.layoutMargin().bottom, availableWidth, 0);
                if (row) {
                    max = Math.max(max, childHeight);
                } else {
                    total += childHeight + gap;
                }
            }
            return row ? max : Math.max(0, total - gap);
        }
        if (style.display() == Display.Grid) {
            float total = 0;
            for (GridTemplateComponent component : style.gridTemplateRows()) {
                if (component.isSingle()) {
                    total += fixedTrackSize(component.track, availableHeight);
                }
            }
            return total;
        }
        float total = 0;
        for (Node child : node.children) {
            if (child.style.display() == Display.None || isOutOfFlow(child)) {
                continue;
            }
            float childHeight = layoutNode(child, 0, 0, contentWidth, availableHeight,
                    contentWidth, availableHeight, -1, -1, Sizing.CONTENT, Sizing.CONTENT).height;
            total += childHeight + solveAuto(child.style.layoutMargin().top, availableWidth, 0)
                    + solveAuto(child.style.layoutMargin().bottom, availableWidth, 0);
        }
        return total;
    }

    private static float fixedTrackSize(TrackSizingFunction track, float basis) {
        return switch (track.max.kind) {
            case LENGTH -> track.max.value;
            case PERCENT -> basis * track.max.value / 100f;
            default -> 0;
        };
    }


    private static void layoutBlock(Node node, float contentWidth, float contentHeight) {
        Style style = node.style;
        float contentLeft = node.layout.border.left + node.layout.padding.left;
        float contentTop = node.layout.border.top + node.layout.padding.top;
        float cursorY = contentTop;
        float contentBottomUsed = contentTop;
        float floatLeftBottom = contentTop;
        float floatRightBottom = contentTop;

        for (Node child : node.children) {
            if (child.style.display() == Display.None || isOutOfFlow(child)) {
                continue;
            }
            Style childStyle = child.style;
            float marginLeft = solveAuto(childStyle.layoutMargin().left, contentWidth, 0);
            float marginRight = solveAuto(childStyle.layoutMargin().right, contentWidth, 0);
            float marginTop = solveAuto(childStyle.layoutMargin().top, contentWidth, 0);
            float marginBottom = solveAuto(childStyle.layoutMargin().bottom, contentWidth, 0);
            boolean leftAuto = childStyle.layoutMargin().left.isAuto();
            boolean rightAuto = childStyle.layoutMargin().right.isAuto();

            boolean floating = childStyle.floatMode() != null
                    && childStyle.floatMode() != Float.None;
            if (!floating) {
                if (childStyle.clear() == Clear.Left || childStyle.clear() == Clear.Both) {
                    cursorY = Math.max(cursorY, floatLeftBottom);
                }
                if (childStyle.clear() == Clear.Right || childStyle.clear() == Clear.Both) {
                    cursorY = Math.max(cursorY, floatRightBottom);
                }
            }

            float specifiedWidth = resolveDimension(childStyle.size().width, contentWidth, 0,
                    childStyle.boxSizing());
            float specifiedHeight = resolveDimension(childStyle.size().height, contentHeight, 0,
                    childStyle.boxSizing());
            float forcedWidth = -1;
            float childX;
            if (!finite(specifiedWidth) && childStyle.aspectRatio() != null
                    && childStyle.aspectRatio() > 0 && finite(specifiedHeight)) {
                forcedWidth = specifiedHeight * childStyle.aspectRatio();
                childX = contentLeft + marginLeft;
            } else if (floating) {
                if (finite(specifiedWidth)) {
                    forcedWidth = specifiedWidth;
                } else {
                    forcedWidth = Math.max(0,
                            contentWidth - marginLeft - marginRight);
                }
                childX = childStyle.floatMode() == Float.Right
                        ? contentLeft + contentWidth - forcedWidth - marginRight
                        : contentLeft + marginLeft;
            } else if (!finite(specifiedWidth)) {
                forcedWidth = Math.max(0, contentWidth - marginLeft - marginRight);
                childX = contentLeft + marginLeft;
            } else {
                float availableForMargins = Math.max(0, contentWidth - specifiedWidth);
                if (leftAuto && rightAuto) {
                    marginLeft = availableForMargins / 2;
                    marginRight = availableForMargins / 2;
                } else if (leftAuto) {
                    marginLeft = availableForMargins;
                } else if (rightAuto) {
                    marginRight = availableForMargins;
                }
                childX = contentLeft + marginLeft;
            }

            if (childStyle.layoutTextAlign() == TextAlign.LegacyCenter
                    || node.style.layoutTextAlign() == TextAlign.LegacyCenter) {
                childX = contentLeft + Math.max(0,
                        (contentWidth - child.layout.width) / 2);
            } else if (childStyle.layoutTextAlign() == TextAlign.LegacyRight
                    || node.style.layoutTextAlign() == TextAlign.LegacyRight) {
                childX = contentLeft + Math.max(0, contentWidth - child.layout.width);
            }

            float childY = cursorY + marginTop;
            layoutNode(child, childX, childY, contentWidth, contentHeight, contentWidth,
                    contentHeight, forcedWidth, -1, Sizing.FILL, Sizing.CONTENT);

            if (childStyle.position() == Position.Relative) {
                child.layout.x += solveAuto(childStyle.layoutInset().left, contentWidth, 0);
                child.layout.y += solveAuto(childStyle.layoutInset().top, contentHeight, 0);
            }

            if (floating) {
                float bottom = child.layout.y + child.layout.height + marginBottom;
                if (childStyle.floatMode() == Float.Right) {
                    floatRightBottom = Math.max(floatRightBottom, bottom);
                } else {
                    floatLeftBottom = Math.max(floatLeftBottom, bottom);
                }
            } else {
                cursorY = child.layout.y + child.layout.height + marginBottom;
                contentBottomUsed = cursorY;
            }
        }

        float usedHeight = Math.max(contentBottomUsed, Math.max(floatLeftBottom, floatRightBottom))
                - contentTop;
        node.layout.usedContentHeight = usedHeight;

        float extraHeight = contentHeight - usedHeight;
        if (style.alignContent() != null && extraHeight > 0) {
            float offset = alignmentOffset(style.alignContent(), 0, extraHeight);
            for (Node child : node.children) {
                if (child.style.display() == Display.None || isOutOfFlow(child)) {
                    continue;
                }
                child.layout.y += offset;
            }
            contentBottomUsed += offset;
        } else if (style.layoutJustifyContent() != null && extraHeight > 0) {
            float offset = justifyOffset(style.layoutJustifyContent(), 0, extraHeight);
            for (Node child : node.children) {
                if (child.style.display() == Display.None || isOutOfFlow(child)) {
                    continue;
                }
                child.layout.y += offset;
            }
            contentBottomUsed += offset;
        }
    }


    private static final class FlexItem {
        Node node;
        float baseMain;
        float mainSize;
        float crossSize;
        float marginMainStart;
        float marginMainEnd;
        float marginCrossStart;
        float marginCrossEnd;
        boolean crossAuto;
        float grow;
        float shrink;
    }

    private static final class FlexLine {
        final List<FlexItem> items = new ArrayList<>();
        float crossSize;
        float crossOffset;
    }

    private static void layoutFlex(Node node, float contentWidth, float contentHeight) {
        Style style = node.style;
        boolean row = style.layoutFlexDirection() == FlexDirection.Row
                || style.layoutFlexDirection() == FlexDirection.RowReverse;
        boolean mainStartIsRight = row && (style.layoutDirection() == Direction.Rtl)
                != (style.layoutFlexDirection() == FlexDirection.RowReverse);
        boolean mainStartIsBottom = !row
                && style.layoutFlexDirection() == FlexDirection.ColumnReverse;
        boolean crossStartIsBottom = row && style.layoutFlexWrap() == FlexWrap.WrapReverse;
        boolean crossStartIsRight = !row
                && ((style.layoutDirection() == Direction.Rtl) != (style.layoutFlexWrap() == FlexWrap.WrapReverse));

        float mainSize = row ? contentWidth : contentHeight;
        float crossSize = row ? contentHeight : contentWidth;
        float mainGap = row ? style.gap().width.resolve(contentWidth)
                : style.gap().height.resolve(contentHeight);
        float crossGap = row ? style.gap().height.resolve(contentHeight)
                : style.gap().width.resolve(contentWidth);

        List<FlexItem> items = new ArrayList<>();
        for (Node child : node.children) {
            if (child.style.display() == Display.None || isOutOfFlow(child)) {
                continue;
            }
            FlexItem item = measureFlexItem(child, row, contentWidth, contentHeight, mainSize);
            items.add(item);
        }
        if (items.isEmpty()) {
            node.layout.usedContentHeight = 0;
            return;
        }

        List<FlexLine> lines = new ArrayList<>();
        boolean wrap = style.layoutFlexWrap() != FlexWrap.NoWrap;
        if (wrap && finite(mainSize)) {
            FlexLine line = new FlexLine();
            float used = 0;
            for (FlexItem item : items) {
                float outer = item.baseMain + item.marginMainStart + item.marginMainEnd;
                float add = line.items.isEmpty() ? outer : outer + mainGap;
                if (!line.items.isEmpty() && used + add > mainSize) {
                    lines.add(line);
                    line = new FlexLine();
                    used = outer;
                } else {
                    used += add;
                }
                line.items.add(item);
            }
            lines.add(line);
        } else {
            FlexLine line = new FlexLine();
            line.items.addAll(items);
            lines.add(line);
        }

        for (FlexLine line : lines) {
            resolveFlexLine(line, mainSize, mainGap);
            float lineCross = 0;
            for (FlexItem item : line.items) {
                lineCross = Math.max(lineCross,
                        item.crossSize + item.marginCrossStart + item.marginCrossEnd);
            }
            line.crossSize = wrap ? lineCross : crossSize;
        }

        if (!wrap) {
            lines.get(0).crossSize = crossSize;
        }

        float totalCross = 0;
        for (int i = 0; i < lines.size(); i++) {
            totalCross += lines.get(i).crossSize;
            if (i > 0) {
                totalCross += crossGap;
            }
        }
        float crossFree = crossSize - totalCross;
        float crossCursor = 0;
        for (int i = 0; i < lines.size(); i++) {
            FlexLine line = lines.get(i);
            if (i > 0) {
                crossCursor += crossGap;
            }
            if (crossFree > 0) {
                crossCursor += crossAlignmentOffset(style.alignContent(), i, lines.size(),
                        crossFree, line.crossSize);
            }
            line.crossOffset = crossCursor;
            crossCursor += line.crossSize;
        }

        for (FlexLine line : lines) {
            float free = mainSize - usedMain(line, mainGap);
            float offset = mainFreeOffset(style.layoutJustifyContent(), free, line.items.size());
            float cursor = offset;
            int count = line.items.size();
            float distributed = distributedMainSpace(style.layoutJustifyContent(), free, count);
            for (int index = 0; index < count; index++) {
                FlexItem item = line.items.get(index);
                float outerMain = item.mainSize + item.marginMainStart + item.marginMainEnd;
                float mainPosition = cursor;
                float childWidth;
                float childHeight;
                if (row) {
                    childWidth = item.mainSize;
                    childHeight = item.crossSize;
                } else {
                    childWidth = item.crossSize;
                    childHeight = item.mainSize;
                }

                float crossOffset = line.crossOffset + crossItemOffset(item, line.crossSize, crossFree,
                        style.layoutAlignItems());
                float childX;
                float childY;
                if (row) {
                    float x = mainStartIsRight
                            ? contentWidth - mainPosition - outerMain + item.marginMainStart
                            : mainPosition + item.marginMainStart;
                    float y = crossStartIsBottom
                            ? contentHeight - crossOffset - item.crossSize
                                    - item.marginCrossEnd
                            : crossOffset + item.marginCrossStart;
                    childX = node.layout.border.left + node.layout.padding.left + x;
                    childY = node.layout.border.top + node.layout.padding.top + y;
                } else {
                    float y = mainStartIsBottom
                            ? contentHeight - mainPosition - outerMain + item.marginMainStart
                            : mainPosition + item.marginMainStart;
                    float x = crossStartIsRight
                            ? contentWidth - crossOffset - item.crossSize
                                    - item.marginCrossEnd
                            : crossOffset + item.marginCrossStart;
                    childX = node.layout.border.left + node.layout.padding.left + x;
                    childY = node.layout.border.top + node.layout.padding.top + y;
                }

                if (item.node.style.position() == Position.Relative) {
                    childX += solveAuto(item.node.style.layoutInset().left, contentWidth, 0);
                    childY += solveAuto(item.node.style.layoutInset().top, contentHeight, 0);
                }

                layoutNode(item.node, childX, childY, contentWidth, contentHeight, contentWidth,
                        contentHeight, childWidth, childHeight, Sizing.CONTENT, Sizing.CONTENT);
                cursor += outerMain + mainGap + distributed;
            }
        }
        node.layout.usedContentHeight = contentHeight;
        node.layout.usedContentWidth = contentWidth;
    }

    private static FlexItem measureFlexItem(Node child, boolean row, float contentWidth,
            float contentHeight, float mainSize) {
        FlexItem item = new FlexItem();
        item.node = child;
        Style style = child.style;
        item.grow = style.flexGrow();
        item.shrink = style.flexShrink();

        float marginLeft = solveAuto(style.layoutMargin().left, contentWidth, 0);
        float marginRight = solveAuto(style.layoutMargin().right, contentWidth, 0);
        float marginTop = solveAuto(style.layoutMargin().top, contentWidth, 0);
        float marginBottom = solveAuto(style.layoutMargin().bottom, contentWidth, 0);

        if (row) {
            item.marginMainStart = marginLeft;
            item.marginMainEnd = marginRight;
            item.marginCrossStart = marginTop;
            item.marginCrossEnd = marginBottom;
        } else {
            item.marginMainStart = marginTop;
            item.marginMainEnd = marginBottom;
            item.marginCrossStart = marginLeft;
            item.marginCrossEnd = marginRight;
        }

        float mainBasis = row ? contentWidth : contentHeight;
        float crossBasis = row ? contentHeight : contentWidth;
        Dimension mainDimension = row ? style.size().width : style.size().height;
        Dimension crossDimension = row ? style.size().height : style.size().width;

        float base = AUTO;
        if (!style.flexBasis().isAuto()) {
            base = style.flexBasis().resolve(mainBasis);
        }
        if (!finite(base) && !mainDimension.isAuto()) {
            base = resolveDimension(mainDimension, mainBasis, 0, style.boxSizing());
        }
        if (!finite(base)) {
            Size<java.lang.Float> measured = layoutNode(child, 0, 0, contentWidth, contentHeight,
                    contentWidth, contentHeight, -1, -1, Sizing.CONTENT, Sizing.CONTENT);
            base = row ? measured.width : measured.height;
        }
        item.baseMain = Math.max(0, base);

        float crossValue = resolveDimension(crossDimension, crossBasis, 0, style.boxSizing());
        if (finite(crossValue)) {
            item.crossSize = crossValue;
            item.crossAuto = false;
        } else {
            Size<java.lang.Float> measured = layoutNode(child, 0, 0, contentWidth, contentHeight,
                    contentWidth, contentHeight, -1, -1, Sizing.CONTENT, Sizing.CONTENT);
            item.crossSize = row ? measured.height : measured.width;
            item.crossAuto = true;
        }
        item.mainSize = item.baseMain;
        return item;
    }

    private static void resolveFlexLine(FlexLine line, float mainSize, float gap) {
        float used = 0;
        float grow = 0;
        float shrinkWeight = 0;
        for (FlexItem item : line.items) {
            used += item.baseMain + item.marginMainStart + item.marginMainEnd;
            grow += item.grow;
            shrinkWeight += item.shrink * item.baseMain;
        }
        if (line.items.size() > 1) {
            used += gap * (line.items.size() - 1);
        }
        float free = mainSize - used;
        for (FlexItem item : line.items) {
            item.mainSize = item.baseMain;
        }
        if (free > 0 && grow > 0) {
            for (FlexItem item : line.items) {
                item.mainSize += free * item.grow / grow;
            }
        } else if (free < 0 && shrinkWeight > 0) {
            for (FlexItem item : line.items) {
                item.mainSize += free * (item.shrink * item.baseMain) / shrinkWeight;
            }
        }
        for (FlexItem item : line.items) {
            item.mainSize = Math.max(0, item.mainSize);
        }
    }

    private static float usedMain(FlexLine line, float gap) {
        float used = 0;
        for (int i = 0; i < line.items.size(); i++) {
            FlexItem item = line.items.get(i);
            used += item.mainSize + item.marginMainStart + item.marginMainEnd;
            if (i > 0) {
                used += gap;
            }
        }
        return used;
    }

    private static float alignmentOffset(AlignContent align, int index, float free) {
        if (align == null || free <= 0) {
            return 0;
        }
        return switch (align) {
            case End, FlexEnd, SafeEnd, SafeFlexEnd -> free;
            case Center, SafeCenter -> free / 2;
            case SpaceBetween -> 0;
            case SpaceAround -> free / 2;
            case SpaceEvenly -> free / 3;
            default -> 0;
        };
    }

    private static float justifyOffset(JustifyContent justify, int index, float free) {
        if (justify == null || free <= 0) {
            return 0;
        }
        return switch (justify) {
            case End, FlexEnd, SafeEnd, SafeFlexEnd -> free;
            case Center, SafeCenter -> free / 2;
            case SpaceBetween -> 0;
            case SpaceAround -> free / 2;
            case SpaceEvenly -> free / 3;
            default -> 0;
        };
    }

    private static float distributedMainSpace(JustifyContent justify, float free, int count) {        if (justify == null || count <= 1) {
            return 0;
        }
        if (free <= 0) {
            return 0;
        }
        return switch (justify) {
            case SpaceBetween -> free / (count - 1);
            case SpaceAround -> free / count;
            case SpaceEvenly -> free / (count + 1);
            default -> 0;
        };
    }

    private static float mainFreeOffset(JustifyContent justify, float free, int count) {
        if (justify == null) {
            return 0;
        }
        if (free <= 0) {
            return switch (justify) {
                case SafeCenter, SafeEnd, SafeFlexEnd, Center, End, FlexEnd -> 0;
                default -> 0;
            };
        }
        return switch (justify) {
            case End, FlexEnd, SafeEnd, SafeFlexEnd -> free;
            case Center, SafeCenter -> free / 2;
            case SpaceBetween -> 0;
            case SpaceAround -> free / (2f * count);
            case SpaceEvenly -> free / (count + 1f);
            default -> 0;
        };
    }

    private static float crossAlignmentOffset(AlignContent align, int lineIndex, int lineCount,
            float free, float lineCross) {
        if (align == null) {
            return 0;
        }
        return switch (align) {
            case End, FlexEnd, SafeEnd, SafeFlexEnd -> free;
            case Center, SafeCenter -> free / 2;
            case SpaceBetween -> lineCount > 1 ? free / (lineCount - 1) * lineIndex : 0;
            case SpaceAround -> lineCount > 0
                    ? free / lineCount * lineIndex + free / (2f * lineCount)
                    : 0;
            case SpaceEvenly -> lineCount > 0
                    ? free / (lineCount + 1f) * (lineIndex + 1)
                    : 0;
            default -> 0;
        };
    }

    private static float crossItemOffset(FlexItem item, float lineCross, float free,
            AlignItems parentAlign) {
        AlignSelf self = item.node.style.alignSelf();
        AlignItems align = parentAlign;
        float freeCross = lineCross - item.crossSize - item.marginCrossStart
                - item.marginCrossEnd;
        if (self != null && self != AlignSelf.Auto) {
            return switch (self) {
                case End, FlexEnd -> Math.max(0, freeCross);
                case Center, SafeCenter -> Math.max(0, freeCross) / 2;
                case SelfStart -> selfStartOffset(item, freeCross);
                case SelfEnd -> selfEndOffset(item, freeCross);
                case SafeStart, SafeFlexStart -> freeCross < 0 ? 0 : 0;
                case SafeEnd, SafeFlexEnd -> freeCross < 0 ? 0 : Math.max(0, freeCross);
                case Stretch -> 0;
                default -> 0;
            };
        }
        if (align == null || align == AlignItems.Stretch) {
            return 0;
        }
        return switch (align) {
            case End, FlexEnd -> Math.max(0, freeCross);
            case Center, SafeCenter -> Math.max(0, freeCross) / 2;
            case SelfStart -> selfStartOffset(item, freeCross);
            case SelfEnd -> selfEndOffset(item, freeCross);
            case SafeStart, SafeFlexStart -> freeCross < 0 ? 0 : 0;
            case SafeEnd, SafeFlexEnd -> freeCross < 0 ? 0 : Math.max(0, freeCross);
            default -> 0;
        };
    }

    private static float selfStartOffset(FlexItem item, float freeCross) {
        if (item.node.style.layoutDirection() == Direction.Rtl) {
            return Math.max(0, freeCross);
        }
        return 0;
    }

    private static float selfEndOffset(FlexItem item, float freeCross) {
        if (item.node.style.layoutDirection() == Direction.Rtl) {
            return 0;
        }
        return Math.max(0, freeCross);
    }


    private static final class GridTrack {
        TrackSizingFunction definition;
        float size;
        float flexFactor;
        boolean autoFit;
        boolean used;
    }

    private static final class GridItemPlacement {
        Node node;
        int rowStart;
        int rowEnd;
        int columnStart;
        int columnEnd;
        boolean rowAuto;
        boolean columnAuto;
        int rowSpan = 1;
        int columnSpan = 1;
        float marginLeft;
        float marginRight;
        float marginTop;
        float marginBottom;
        float measuredWidth;
        float measuredHeight;
    }

    private static void layoutGrid(Node node, float contentWidth, float contentHeight) {
        Style style = node.style;
        List<GridTrack> columns = buildTracks(style.gridTemplateColumns(), style.gridAutoColumns(),
                contentWidth, style.gap().width.resolve(contentWidth), style.getGridTemplateAreaColumnCount());
        List<GridTrack> rows = buildTracks(style.gridTemplateRows(), style.gridAutoRows(),
                contentHeight, style.gap().height.resolve(contentHeight), style.getGridTemplateAreaRowCount());
        int explicitColumns = columns.size();
        int explicitRows = rows.size();

        List<GridItemPlacement> items = new ArrayList<>();
        for (Node child : node.children) {
            if (child.style.display() == Display.None || isOutOfFlow(child)) {
                continue;
            }
            GridItemPlacement item = new GridItemPlacement();
            item.node = child;
            item.marginLeft = solveAuto(child.style.layoutMargin().left, contentWidth, 0);
            item.marginRight = solveAuto(child.style.layoutMargin().right, contentWidth, 0);
            item.marginTop = solveAuto(child.style.layoutMargin().top, contentWidth, 0);
            item.marginBottom = solveAuto(child.style.layoutMargin().bottom, contentWidth, 0);
            Size<java.lang.Float> measured = layoutNode(child, 0, 0, contentWidth, contentHeight,
                    contentWidth, contentHeight, -1, -1, Sizing.CONTENT, Sizing.CONTENT);
            item.measuredWidth = measured.width;
            item.measuredHeight = measured.height;
            items.add(item);
        }

        placeGridItems(items, style, explicitColumns, explicitRows);

        int columnCount = explicitColumns;
        int rowCount = explicitRows;
        for (GridItemPlacement item : items) {
            columnCount = Math.max(columnCount, item.columnEnd);
            rowCount = Math.max(rowCount, item.rowEnd);
        }
        columnCount = Math.max(1, columnCount);
        rowCount = Math.max(1, rowCount);
        if (items.isEmpty() && explicitColumns == 0 && explicitRows == 0) {
            columnCount = 0;
            rowCount = 0;
        }
        while (columns.size() < columnCount) {
            columns.add(implicitTrack(style.gridAutoColumns(), columns.size() - explicitColumns,
                    style.gridAutoFlow() == GridAutoFlow.Column
                            || style.gridAutoFlow() == GridAutoFlow.ColumnDense));
        }
        while (rows.size() < rowCount) {
            rows.add(implicitTrack(style.gridAutoRows(), rows.size() - explicitRows,
                    style.gridAutoFlow() == GridAutoFlow.Row
                            || style.gridAutoFlow() == GridAutoFlow.RowDense));
        }

        float columnGap = style.gap().width.resolve(contentWidth);
        float rowGap = style.gap().height.resolve(contentHeight);
        sizeTracks(columns, items, true, contentWidth, columnGap,
                style.layoutJustifyContent() == null);
        sizeTracks(rows, items, false, contentHeight, rowGap, style.alignContent() == null);
        ColumnLayout columnLayout = alignTracks(columns, contentWidth, columnGap,
                style.layoutJustifyContent(), style.layoutDirection() == Direction.Rtl);
        RowLayout rowLayout = alignTracks(rows, contentHeight, rowGap, style.alignContent(), false);

        for (GridItemPlacement item : items) {
            float cellX = columnLayout.trackStart[item.columnStart];
            float cellEnd = columnLayout.trackEnd[item.columnEnd - 1];
            float cellWidth = Math.max(0,
                    cellEnd - cellX - item.marginLeft - item.marginRight);
            float cellY = rowLayout.trackStart[item.rowStart];
            float cellEndY = rowLayout.trackEnd[item.rowEnd - 1];
            float cellHeight = Math.max(0, cellEndY - cellY - item.marginTop - item.marginBottom);

            Style childStyle = item.node.style;
            float forcedWidth = -1;
            float forcedHeight = -1;
            boolean widthAuto = childStyle.size().width.isAuto();
            boolean heightAuto = childStyle.size().height.isAuto();
            AlignItems justifyItems = childStyle.justifyItems() == null ? style.justifyItems()
                    : childStyle.justifyItems();
            AlignItems alignItems = childStyle.layoutAlignItems() == null ? style.layoutAlignItems()
                    : childStyle.layoutAlignItems();
            AlignSelf justifySelf = childStyle.justifySelf();
            AlignSelf alignSelf = childStyle.alignSelf();

            if (widthAuto && isStretch(justifyItems, justifySelf)) {
                forcedWidth = Math.max(0, cellWidth);
            } else if (!widthAuto) {
                forcedWidth = resolveDimension(childStyle.size().width, cellWidth, 0,
                        childStyle.boxSizing());
            }
            if (heightAuto && isStretch(alignItems, alignSelf)) {
                forcedHeight = Math.max(0, cellHeight);
            } else if (!heightAuto) {
                forcedHeight = resolveDimension(childStyle.size().height, cellHeight, 0,
                        childStyle.boxSizing());
            }

            float childWidth = forcedWidth >= 0 ? forcedWidth : item.measuredWidth;
            float childHeight = forcedHeight >= 0 ? forcedHeight : item.measuredHeight;

            float x = cellX + item.marginLeft + itemAlignmentOffset(justifyItems, justifySelf,
                    cellWidth, childWidth);
            float y = cellY + item.marginTop + itemAlignmentOffset(alignItems, alignSelf,
                    cellHeight, childHeight);

            layoutNode(item.node, x, y, cellWidth, cellHeight, cellWidth, cellHeight,
                    forcedWidth, forcedHeight, Sizing.CONTENT, Sizing.CONTENT);
        }

        if (!node.children.isEmpty()) {
            buildDetailedGridInfo(node, columns, rows, items, columnLayout, rowLayout,
                    explicitColumns, explicitRows, style.layoutDirection() == Direction.Rtl,
                    style.gridTemplateColumns(), style.gridTemplateRows());
        }
        node.layout.usedContentWidth = contentWidth;
        node.layout.usedContentHeight = contentHeight;
    }

    private static boolean isStretch(AlignItems items, AlignSelf self) {
        if (self != null && self != AlignSelf.Auto) {
            return self == AlignSelf.Stretch;
        }
        return items == null || items == AlignItems.Stretch;
    }

    private static float itemAlignmentOffset(AlignItems items, AlignSelf self, float cellSize,
            float itemSize) {
        float free = cellSize - itemSize;
        if (free <= 0) {
            return 0;
        }
        if (self != null && self != AlignSelf.Auto) {
            return switch (self) {
                case Center -> free / 2;
                case End, FlexEnd -> free;
                default -> 0;
            };
        }
        if (items == null) {
            return 0;
        }
        return switch (items) {
            case Center -> free / 2;
            case End, FlexEnd -> free;
            default -> 0;
        };
    }

    private static List<GridTrack> buildTracks(List<GridTemplateComponent> template,
            List<TrackSizingFunction> autoTracks, float axisSize, float gap, int areaCount) {
        List<GridTrack> tracks = new ArrayList<>();
        for (GridTemplateComponent component : template) {
            if (component.isSingle()) {
                tracks.add(track(component.track, false));
            } else if (component.repetition == GridTemplateComponent.Repetition.NONE) {
                for (int i = 0; i < component.repeatCount; i++) {
                    for (TrackSizingFunction function : component.tracks) {
                        tracks.add(track(function, false));
                    }
                }
            } else {
                int repeated = autoRepeatCount(component, axisSize, gap);
                for (int i = 0; i < repeated; i++) {
                    for (TrackSizingFunction function : component.tracks) {
                        tracks.add(track(function,
                                component.repetition == GridTemplateComponent.Repetition.AUTO_FIT));
                    }
                }
            }
        }
        while (tracks.size() < areaCount) {
            tracks.add(track(new TrackSizingFunction(MinTrackSizing.auto(), MaxTrackSizing.auto()),
                    false));
        }
        return tracks;
    }

    private static int autoRepeatCount(GridTemplateComponent component, float axisSize,
            float gap) {
        float trackTotal = 0;
        for (TrackSizingFunction function : component.tracks) {
            trackTotal += fixedTrackSize(function, axisSize);
        }
        if (trackTotal <= 0 || !finite(axisSize)) {
            return 1;
        }
        int perRepeat = component.tracks.size();
        if (perRepeat == 0) {
            return 1;
        }
        float denominator = trackTotal + gap * perRepeat;
        if (denominator <= 0) {
            return 1;
        }
        int count = (int) Math.floor((axisSize + gap) / denominator);
        return Math.max(1, count);
    }

    private static GridTrack track(TrackSizingFunction function, boolean autoFit) {
        GridTrack track = new GridTrack();
        track.definition = function;
        track.autoFit = autoFit;
        return track;
    }

    private static GridTrack implicitTrack(List<TrackSizingFunction> definitions, int index,
            boolean primaryAxis) {
        TrackSizingFunction function;
        if (definitions.isEmpty()) {
            function = new TrackSizingFunction(MinTrackSizing.auto(), MaxTrackSizing.auto());
        } else {
            function = definitions.get(index % definitions.size());
        }
        return track(function, false);
    }

    private static void placeGridItems(List<GridItemPlacement> items, Style style, int explicitColumns,
            int explicitRows) {
        for (GridItemPlacement item : items) {
            resolvePlacement(item, explicitColumns, explicitRows);
        }
        boolean columnFlow = style.gridAutoFlow() == GridAutoFlow.Column
                || style.gridAutoFlow() == GridAutoFlow.ColumnDense;
        boolean dense = style.gridAutoFlow() == GridAutoFlow.RowDense
                || style.gridAutoFlow() == GridAutoFlow.ColumnDense;
        int columns = Math.max(1, explicitColumns);
        int rows = Math.max(1, explicitRows);
        Set<Long> occupied = new HashSet<>();
        for (GridItemPlacement item : items) {
            if (!item.rowAuto && !item.columnAuto) {
                occupy(occupied, item);
            }
        }
        int cursorRow = 0;
        int cursorColumn = 0;
        for (GridItemPlacement item : items) {
            if (!item.rowAuto && !item.columnAuto) {
                continue;
            }
            if (dense) {
                cursorRow = 0;
                cursorColumn = 0;
            }
            int spanRows = Math.max(1, item.rowSpan);
            int spanColumns = Math.max(1, item.columnSpan);
            if (columnFlow) {
                int flowRows = Math.max(1, explicitRows);
                rows = Math.max(rows, flowRows);
                int row = cursorRow;
                int column = cursorColumn;
                int guard = 0;
                while (guard++ < 10000) {
                    if (row >= flowRows) {
                        row = 0;
                        column++;
                    }
                    if (column >= columns) {
                        columns = column + spanColumns;
                    }
                    if (row + spanRows > rows) {
                        rows = row + spanRows;
                    }
                    if (item.rowAuto && item.columnAuto) {
                        if (areaFree(occupied, row, column, spanRows, spanColumns)) {
                            item.rowStart = row;
                            item.rowEnd = row + spanRows;
                            item.columnStart = column;
                            item.columnEnd = column + spanColumns;
                            occupy(occupied, item);
                            cursorColumn = column;
                            cursorRow = row + spanRows;
                            if (cursorRow >= flowRows) {
                                cursorRow = 0;
                                cursorColumn++;
                            }
                            break;
                        }
                    } else if (item.rowAuto) {
                        int fixedColumn = item.columnStart;
                        columns = Math.max(columns, fixedColumn + spanColumns);
                        if (areaFree(occupied, row, fixedColumn, spanRows, spanColumns)) {
                            item.rowStart = row;
                            item.rowEnd = row + spanRows;
                            occupy(occupied, item);
                            cursorColumn = fixedColumn;
                            cursorRow = row + spanRows;
                            if (cursorRow >= flowRows) {
                                cursorRow = 0;
                                cursorColumn++;
                            }
                            break;
                        }
                    } else {
                        int fixedRow = item.rowStart;
                        if (areaFree(occupied, fixedRow, column, spanRows, spanColumns)) {
                            item.columnStart = column;
                            item.columnEnd = column + spanColumns;
                            occupy(occupied, item);
                            cursorColumn = column + spanColumns;
                            cursorRow = fixedRow;
                            break;
                        }
                    }
                    row += spanRows;
                    if (row >= flowRows) {
                        row = 0;
                        column++;
                    }
                }
            } else {
                int flowColumns = Math.max(1, explicitColumns);
                columns = Math.max(columns, flowColumns);
                int row = cursorRow;
                int column = cursorColumn;
                int guard = 0;
                while (guard++ < 10000) {
                    if (column >= flowColumns) {
                        column = 0;
                        row++;
                    }
                    if (row + spanRows > rows) {
                        rows = row + spanRows;
                    }
                    if (column + spanColumns > columns) {
                        columns = column + spanColumns;
                    }
                    if (item.rowAuto && item.columnAuto) {
                        if (areaFree(occupied, row, column, spanRows, spanColumns)) {
                            item.rowStart = row;
                            item.rowEnd = row + spanRows;
                            item.columnStart = column;
                            item.columnEnd = column + spanColumns;
                            occupy(occupied, item);
                            cursorRow = row;
                            cursorColumn = column + spanColumns;
                            if (cursorColumn >= flowColumns) {
                                cursorColumn = 0;
                                cursorRow++;
                            }
                            break;
                        }
                    } else if (item.columnAuto) {
                        int fixedRow = item.rowStart;
                        rows = Math.max(rows, fixedRow + spanRows);
                        if (areaFree(occupied, fixedRow, column, spanRows, spanColumns)) {
                            item.columnStart = column;
                            item.columnEnd = column + spanColumns;
                            occupy(occupied, item);
                            cursorRow = fixedRow;
                            cursorColumn = column + spanColumns;
                            if (cursorColumn >= flowColumns) {
                                cursorColumn = 0;
                                cursorRow++;
                            }
                            break;
                        }
                    } else {
                        int fixedColumn = item.columnStart;
                        columns = Math.max(columns, fixedColumn + spanColumns);
                        if (areaFree(occupied, row, fixedColumn, spanRows, spanColumns)) {
                            item.rowStart = row;
                            item.rowEnd = row + spanRows;
                            occupy(occupied, item);
                            cursorRow = row + spanRows;
                            cursorColumn = fixedColumn;
                            break;
                        }
                    }
                    column += spanColumns;
                    if (column >= flowColumns) {
                        column = 0;
                        row++;
                    }
                }
            }
        }
    }

    private static void resolvePlacement(GridItemPlacement item, int explicitColumns,
            int explicitRows) {
        GridPlacement columnStart = item.node.style.gridColumn().start;
        GridPlacement columnEnd = item.node.style.gridColumn().end;
        GridPlacement rowStart = item.node.style.gridRow().start;
        GridPlacement rowEnd = item.node.style.gridRow().end;

        item.columnAuto = columnStart.isAuto() && columnEnd.isAuto();
        item.rowAuto = rowStart.isAuto() && rowEnd.isAuto();

        if (columnStart.isSpan()) {
            item.columnSpan = columnStart.span;
        }
        if (columnEnd.isSpan()) {
            item.columnSpan = Math.max(item.columnSpan, columnEnd.span);
        }
        if (rowStart.isSpan()) {
            item.rowSpan = rowStart.span;
        }
        if (rowEnd.isSpan()) {
            item.rowSpan = Math.max(item.rowSpan, rowEnd.span);
        }

        if (columnStart.isLine()) {
            item.columnStart = resolveLine(columnStart.line, explicitColumns);
        }
        if (columnEnd.isLine()) {
            item.columnEnd = resolveLine(columnEnd.line, explicitColumns);
        }
        if (rowStart.isLine()) {
            item.rowStart = resolveLine(rowStart.line, explicitRows);
        }
        if (rowEnd.isLine()) {
            item.rowEnd = resolveLine(rowEnd.line, explicitRows);
        }

        if (columnStart.isLine() && !columnEnd.isLine()) {
            item.columnEnd = item.columnStart + item.columnSpan;
            item.columnAuto = false;
        } else if (!columnStart.isLine() && columnEnd.isLine()) {
            item.columnStart = Math.max(0, item.columnEnd - item.columnSpan);
            item.columnAuto = false;
        } else if (columnStart.isLine() && columnEnd.isLine()) {
            item.columnAuto = false;
            if (item.columnEnd <= item.columnStart) {
                item.columnEnd = item.columnStart + 1;
            }
        }
        if (rowStart.isLine() && !rowEnd.isLine()) {
            item.rowEnd = item.rowStart + item.rowSpan;
            item.rowAuto = false;
        } else if (!rowStart.isLine() && rowEnd.isLine()) {
            item.rowStart = Math.max(0, item.rowEnd - item.rowSpan);
            item.rowAuto = false;
        } else if (rowStart.isLine() && rowEnd.isLine()) {
            item.rowAuto = false;
            if (item.rowEnd <= item.rowStart) {
                item.rowEnd = item.rowStart + 1;
            }
        }

        item.columnStart = Math.max(0, item.columnStart);
        item.rowStart = Math.max(0, item.rowStart);
        item.columnEnd = Math.max(item.columnStart + 1, item.columnEnd);
        item.rowEnd = Math.max(item.rowStart + 1, item.rowEnd);
    }

    private static int resolveLine(int line, int explicitTracks) {
        if (line > 0) {
            return line - 1;
        }
        if (line < 0) {
            return Math.max(0, explicitTracks + line + 1);
        }
        return 0;
    }

    private static void occupy(Set<Long> occupied, GridItemPlacement item) {
        for (int row = item.rowStart; row < item.rowEnd; row++) {
            for (int column = item.columnStart; column < item.columnEnd; column++) {
                occupied.add(key(row, column));
            }
        }
    }

    private static boolean areaFree(Set<Long> occupied, int row, int column, int rowSpan,
            int columnSpan) {
        for (int r = row; r < row + rowSpan; r++) {
            for (int c = column; c < column + columnSpan; c++) {
                if (occupied.contains(key(r, c))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long key(int row, int column) {
        return ((long) row << 32) | (column & 0xffffffffL);
    }

    private static void sizeTracks(List<GridTrack> tracks, List<GridItemPlacement> items, boolean columns,
            float axisSize, float gap, boolean stretchAuto) {
        for (GridTrack track : tracks) {
            if (track.definition == null) {
                continue;
            }
            track.size = switch (track.definition.min.kind) {
                case LENGTH -> track.definition.min.value;
                case PERCENT -> axisSize * track.definition.min.value / 100f;
                default -> 0;
            };
            switch (track.definition.max.kind) {
                case LENGTH -> track.size = Math.max(track.size, track.definition.max.value);
                case PERCENT -> track.size = Math.max(track.size,
                        axisSize * track.definition.max.value / 100f);
                case FLEX -> track.flexFactor = track.definition.max.value;
                default -> {
                }
            }
        }

        boolean[] hasExplicitItem = new boolean[tracks.size()];
        boolean[] hasPercentItem = new boolean[tracks.size()];
        float[] percentValues = new float[tracks.size()];
        for (GridItemPlacement item : items) {
            float contribution = columns ? item.measuredWidth + item.marginLeft + item.marginRight
                    : item.measuredHeight + item.marginTop + item.marginBottom;
            Dimension along = columns ? item.node.style.size().width : item.node.style.size().height;
            boolean explicitSize = along != null && !along.isAuto();
            boolean percentSize = along != null && along.isPercent();
            int start = columns ? item.columnStart : item.rowStart;
            int end = columns ? item.columnEnd : item.rowEnd;
            int span = Math.max(1, end - start);
            float perTrack = contribution / span;
            for (int i = start; i < end && i < tracks.size(); i++) {
                GridTrack track = tracks.get(i);
                track.used = true;
                if (explicitSize) {
                    hasExplicitItem[i] = true;
                }
                if (percentSize) {
                    hasPercentItem[i] = true;
                    percentValues[i] = Math.max(percentValues[i], along.value);
                }
                if (!percentSize && track.definition != null
                        && (track.definition.max.kind == MaxTrackSizing.Kind.AUTO
                                || track.definition.max.kind == MaxTrackSizing.Kind.MIN_CONTENT
                                || track.definition.max.kind == MaxTrackSizing.Kind.MAX_CONTENT
                                || track.definition.max.kind == MaxTrackSizing.Kind.FIT_CONTENT)) {
                    track.size = Math.max(track.size, perTrack);
                }
                if (track.definition != null && track.definition.max.kind == MaxTrackSizing.Kind.PERCENT) {
                    track.size = Math.min(track.size,
                            axisSize * track.definition.max.value / 100f);
                }
            }
        }

        for (GridTrack track : tracks) {
            if (track.autoFit && !track.used) {
                track.size = 0;
            }
        }

        float total = totalWithGaps(tracks, gap);
        float flexTotal = 0;
        for (GridTrack track : tracks) {
            flexTotal += track.flexFactor;
        }
        float remaining = axisSize - total;
        if (remaining > 0 && flexTotal > 0) {
            for (GridTrack track : tracks) {
                if (track.flexFactor > 0) {
                    track.size += remaining * track.flexFactor / flexTotal;
                }
            }
        }

        remaining = axisSize - totalWithGaps(tracks, gap);
        if (remaining > 0) {
            int zeroAuto = 0;
            for (int i = 0; i < tracks.size(); i++) {
                GridTrack track = tracks.get(i);
                if (track.size <= 0 && !hasExplicitItem[i]
                        && (track.definition == null
                                || track.definition.max.kind == MaxTrackSizing.Kind.AUTO)) {
                    zeroAuto++;
                }
            }
            if (zeroAuto > 0) {
                float share = remaining / zeroAuto;
                for (int i = 0; i < tracks.size(); i++) {
                    GridTrack track = tracks.get(i);
                    if (track.size <= 0 && !hasExplicitItem[i]
                            && (track.definition == null
                                    || track.definition.max.kind == MaxTrackSizing.Kind.AUTO)) {
                        track.size = share;
                    }
                }
            }
        }

        boolean anyPercent = false;
        for (boolean value : hasPercentItem) {
            anyPercent |= value;
        }
        if (anyPercent) {
            for (int i = 0; i < tracks.size(); i++) {
                GridTrack track = tracks.get(i);
                if (!hasPercentItem[i] || track.definition == null
                        || track.definition.max.kind != MaxTrackSizing.Kind.AUTO) {
                    continue;
                }
                float otherSize = 0;
                for (int j = 0; j < tracks.size(); j++) {
                    if (j != i) {
                        otherSize += tracks.get(j).size;
                    }
                }
                float available = axisSize - otherSize - gap * Math.max(0, tracks.size() - 1);
                float contribution = percentValues[i] / 100f * Math.max(0, available);
                track.size = Math.max(track.size, contribution);
            }
        }

        if (stretchAuto && !anyPercent) {
            remaining = axisSize - totalWithGaps(tracks, gap);
            if (remaining > 0) {
                int autoCount = 0;
                for (GridTrack track : tracks) {
                    if (track.size >= 0 && (track.definition == null
                            || track.definition.max.kind == MaxTrackSizing.Kind.AUTO)) {
                        autoCount++;
                    }
                }
                if (autoCount > 0) {
                    float share = remaining / autoCount;
                    for (GridTrack track : tracks) {
                        if (track.definition == null
                                || track.definition.max.kind == MaxTrackSizing.Kind.AUTO) {
                            track.size += share;
                        }
                    }
                }
            }
        }
    }

    private static float totalWithGaps(List<GridTrack> tracks, float gap) {
        float total = 0;
        for (GridTrack track : tracks) {
            total += track.size;
        }
        if (tracks.size() > 1) {
            total += gap * (tracks.size() - 1);
        }
        return total;
    }

    private static class TrackLayout {
        float[] trackStart;
        float[] trackEnd;
        float[] gutters;
    }

    private static final class ColumnLayout extends TrackLayout {
    }

    private static final class RowLayout extends TrackLayout {
    }

    private static ColumnLayout alignTracks(List<GridTrack> tracks, float axisSize, float gap,
            JustifyContent justify, boolean rtl) {
        ColumnLayout layout = new ColumnLayout();
        if (hasCollapsedAutoFit(tracks) && justify == JustifyContent.SpaceBetween) {
            autoFitCollapsedLayout(tracks, axisSize, gap, rtl, layout);
            return layout;
        }
        int count = tracks.size();
        layout.trackStart = new float[count];
        layout.trackEnd = new float[count];
        layout.gutters = new float[count + 1];
        float total = 0;
        for (GridTrack track : tracks) {
            total += track.size;
        }
        if (count > 1) {
            total += gap * (count - 1);
        }
        float free = axisSize - total;
        float leading = 0;
        float extra = 0;
        if (free > 0 && justify != null) {
            switch (justify) {
                case End, FlexEnd, SafeEnd, SafeFlexEnd -> leading = free;
                case Center, SafeCenter -> leading = free / 2;
                case SpaceBetween -> extra = count > 1 ? free / (count - 1) : 0;
                case SpaceAround -> {
                    leading = free / (2f * count);
                    extra = free / count;
                }
                case SpaceEvenly -> {
                    leading = free / (count + 1f);
                    extra = free / (count + 1f);
                }
                default -> {
                }
            }
        } else if (free < 0 && justify != null) {
            leading = 0;
        }
        layout.gutters[0] = 0;
        float cursor = leading;
        for (int i = 0; i < count; i++) {
            layout.trackStart[i] = cursor;
            cursor += tracks.get(i).size;
            layout.trackEnd[i] = cursor;
            if (i < count - 1) {
                float gutter = gap + extra;
                layout.gutters[i + 1] = gutter;
                cursor += gutter;
            }
        }
        layout.gutters[count] = 0;
        if (rtl) {
            for (int i = 0; i < count; i++) {
                float start = layout.trackStart[i];
                float end = layout.trackEnd[i];
                layout.trackStart[i] = axisSize - end;
                layout.trackEnd[i] = axisSize - start;
            }
            for (int i = 0; i < layout.gutters.length / 2; i++) {
                float tmp = layout.gutters[i];
                layout.gutters[i] = layout.gutters[layout.gutters.length - 1 - i];
                layout.gutters[layout.gutters.length - 1 - i] = tmp;
            }
        }
        return layout;
    }

    private static RowLayout alignTracks(List<GridTrack> tracks, float axisSize, float gap,
            AlignContent align, boolean unused) {
        RowLayout layout = new RowLayout();
        if (hasCollapsedAutoFit(tracks) && align == AlignContent.SpaceBetween) {
            autoFitCollapsedLayout(tracks, axisSize, gap, false, layout);
            return layout;
        }
        int count = tracks.size();
        layout.trackStart = new float[count];
        layout.trackEnd = new float[count];
        layout.gutters = new float[count + 1];
        float total = 0;
        for (GridTrack track : tracks) {
            total += track.size;
        }
        if (count > 1) {
            total += gap * (count - 1);
        }
        float free = axisSize - total;
        float leading = 0;
        float extra = 0;
        if (free > 0 && align != null) {
            switch (align) {
                case End, FlexEnd, SafeEnd, SafeFlexEnd -> leading = free;
                case Center, SafeCenter -> leading = free / 2;
                case SpaceBetween -> extra = count > 1 ? free / (count - 1) : 0;
                case SpaceAround -> {
                    leading = free / (2f * count);
                    extra = free / count;
                }
                case SpaceEvenly -> {
                    leading = free / (count + 1f);
                    extra = free / (count + 1f);
                }
                default -> {
                }
            }
        }
        layout.gutters[0] = 0;
        float cursor = leading;
        for (int i = 0; i < count; i++) {
            layout.trackStart[i] = cursor;
            cursor += tracks.get(i).size;
            layout.trackEnd[i] = cursor;
            if (i < count - 1) {
                float gutter = gap + extra;
                layout.gutters[i + 1] = gutter;
                cursor += gutter;
            }
        }
        layout.gutters[count] = 0;
        return layout;
    }

    private static boolean hasCollapsedAutoFit(List<GridTrack> tracks) {
        for (GridTrack track : tracks) {
            if (track.autoFit && !track.used) {
                return true;
            }
        }
        return false;
    }

    private static void autoFitCollapsedLayout(List<GridTrack> tracks, float axisSize, float gap,
            boolean rtl, TrackLayout layout) {
        int count = tracks.size();
        layout.trackStart = new float[count];
        layout.trackEnd = new float[count];
        layout.gutters = new float[count + 1];
        int[] used = new int[count];
        int usedCount = 0;
        float sumUsed = 0;
        for (int i = 0; i < count; i++) {
            if (tracks.get(i).used) {
                used[usedCount++] = i;
                sumUsed += tracks.get(i).size;
            }
        }
        if (usedCount == 0) {
            return;
        }
        float spacing = usedCount > 1 ? (axisSize - sumUsed) / (usedCount - 1) : 0;
        if (!rtl) {
            float cursor = 0;
            for (int u = 0; u < usedCount; u++) {
                int index = used[u];
                layout.trackStart[index] = cursor;
                layout.trackEnd[index] = cursor + tracks.get(index).size;
                cursor = layout.trackEnd[index];
                if (u == usedCount - 1) {
                    for (int k = index + 1; k < count; k++) {
                        layout.trackStart[k] = cursor;
                        layout.trackEnd[k] = cursor;
                    }
                    break;
                }
                int next = used[u + 1];
                float nextStart = cursor + spacing;
                float firstGap = Math.min(spacing, gap);
                layout.gutters[index + 1] = firstGap;
                float collapsed = cursor + firstGap;
                for (int k = index + 1; k < next; k++) {
                    layout.trackStart[k] = collapsed;
                    layout.trackEnd[k] = collapsed;
                    layout.gutters[k + 1] = k == next - 1 ? nextStart - collapsed : 0;
                }
                cursor = nextStart;
            }
        } else {
            float[] ltrStart = new float[count];
            float cursor = 0;
            for (int u = 0; u < usedCount; u++) {
                int index = used[u];
                ltrStart[index] = cursor;
                cursor += tracks.get(index).size + (u + 1 < usedCount ? spacing : 0);
            }
            for (int u = 0; u < usedCount; u++) {
                int index = used[u];
                float ltr = ltrStart[index];
                layout.trackStart[index] = axisSize - (ltr + tracks.get(index).size);
                layout.trackEnd[index] = axisSize - ltr;
            }
            for (int u = 0; u < usedCount - 1; u++) {
                int index = used[u];
                int next = used[u + 1];
                float collapsed = axisSize - ltrStart[next];
                for (int k = index + 1; k < next; k++) {
                    layout.trackStart[k] = collapsed;
                    layout.trackEnd[k] = collapsed;
                }
                layout.gutters[index + 1] = spacing;
            }
            int last = used[usedCount - 1];
            for (int k = last + 1; k < count; k++) {
                layout.trackStart[k] = 0;
                layout.trackEnd[k] = 0;
            }
        }
    }

    private static void buildDetailedGridInfo(Node node, List<GridTrack> columns, List<GridTrack> rows,
            List<GridItemPlacement> items, ColumnLayout columnLayout, RowLayout rowLayout,
            int explicitColumns, int explicitRows, boolean rtl,
            List<GridTemplateComponent> templateColumns, List<GridTemplateComponent> templateRows) {
        DetailedGridInfo info = new DetailedGridInfo();
        float contentLeft = node.layout.border.left + node.layout.padding.left;
        float contentTop = node.layout.border.top + node.layout.padding.top;
        fillTrackInfo(info.columns, columns, columnLayout, contentLeft, explicitColumns, rtl);
        fillTrackInfo(info.rows, rows, rowLayout, contentTop, explicitRows, false);
        for (GridItemPlacement item : items) {
            info.items.add(new DetailedGridInfo.ItemInfo(item.rowStart + 1, item.rowEnd + 1,
                    item.columnStart + 1, item.columnEnd + 1));
        }
        node.layout.detailedGridInfo = info;
    }

    private static void fillTrackInfo(DetailedGridInfo.TrackInfo info, List<GridTrack> tracks,
            ColumnLayout layout, float contentOrigin, int explicitTracks, boolean rtl) {
        fillTrackInfo(info, tracks, layout.trackStart, layout.trackEnd, layout.gutters,
                contentOrigin, explicitTracks);
    }

    private static void fillTrackInfo(DetailedGridInfo.TrackInfo info, List<GridTrack> tracks,
            RowLayout layout, float contentOrigin, int explicitTracks, boolean rtl) {
        fillTrackInfo(info, tracks, layout.trackStart, layout.trackEnd, layout.gutters,
                contentOrigin, explicitTracks);
    }

    private static void fillTrackInfo(DetailedGridInfo.TrackInfo info, List<GridTrack> tracks,
            float[] starts, float[] ends, float[] gutters, float contentOrigin,
            int explicitTracks) {
        info.explicitTracks = explicitTracks;
        info.positiveImplicitTracks = Math.max(0, tracks.size() - explicitTracks);
        info.negativeImplicitTracks = 0;
        for (GridTrack track : tracks) {
            info.sizes.add(track.size);
        }
        for (float gutter : gutters) {
            info.gutters.add(gutter);
        }
        for (int i = 0; i < tracks.size(); i++) {
            info.positions.add(Line.of(contentOrigin + starts[i], contentOrigin + ends[i]));
        }
    }


    private static void computeAbsolute(Node node, Node root, boolean isRoot) {
        if (isRoot) {
            node.layout.absX = node.layout.x;
            node.layout.absY = node.layout.y;
        }
        for (Node child : node.children) {
            if (isOutOfFlow(child)) {
                Node cb = child.layout.containingBlock;
                if (cb == null) {
                    child.layout.absX = root.layout.absX + child.layout.x;
                    child.layout.absY = root.layout.absY + child.layout.y;
                } else {
                    child.layout.absX = cb.layout.absX + child.layout.x;
                    child.layout.absY = cb.layout.absY + child.layout.y;
                }
            } else {
                child.layout.absX = node.layout.absX + child.layout.x;
                child.layout.absY = node.layout.absY + child.layout.y;
            }
            computeAbsolute(child, root, false);
        }
    }

    private static void computeContentExtents(Node node) {
        float maxRight = 0;
        float maxBottom = 0;
        for (Node descendant : descendants(node)) {
            maxRight = Math.max(maxRight,
                    descendant.layout.absX + descendant.layout.width - node.layout.absX);
            maxBottom = Math.max(maxBottom,
                    descendant.layout.absY + descendant.layout.height - node.layout.absY);
        }
        node.layout.contentWidth = Math.max(0, maxRight);
        node.layout.contentHeight = Math.max(0, maxBottom);
        for (Node child : node.children) {
            computeContentExtents(child);
        }
    }

    private static List<Node> descendants(Node node) {
        List<Node> result = new ArrayList<>();
        for (Node child : node.children) {
            result.add(child);
            result.addAll(descendants(child));
        }
        return result;
    }

    public static String printTree(Node node) {
        StringBuilder builder = new StringBuilder();
        printNode(node, "", true, builder);
        return builder.toString();
    }

    private static void printNode(Node node, String prefix, boolean last, StringBuilder builder) {
        builder.append(prefix);
        builder.append(last ? "└── " : "├── ");
        Layout layout = node.layout;
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
        List<Node> children = node.children;
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
}
