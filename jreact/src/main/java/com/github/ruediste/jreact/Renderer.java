package com.github.ruediste.jreact;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Consumer;

/**
 * Paints a styled, laid-out node tree with Java2D.
 *
 * <p>
 * Consumes geometry and text fragments computed by {@link FlexLayout} and
 * draws backgrounds, rounded per-side borders, shadows, text, icons, slider
 * thumbs, and focus outlines. Opacity is composed through the subtree. Graphics
 * state is isolated with disposable copies of the supplied context; the node
 * tree is not modified by painting.
 *
 * <p>
 * This class does not measure layout, run components, or dispatch input.
 * Native input/select content is painted by Swing editors owned by
 * {@link JReactPanel}, rather than by this renderer.
 */
public final class Renderer {
    private Renderer() {
    }

    public static void paint(Node root, Graphics2D graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintNode(root, copy, 1);
        } finally {
            copy.dispose();
        }
    }

    private static Shape shape(Node node) {
        double radius = node.style.number(Style.Property.RADIUS, 0);
        return new RoundRectangle2D.Double(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height,
                radius * 2, radius * 2);
    }

    private static void paintNode(Node node, Graphics2D graphics, float inheritedOpacity) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            float opacity = inheritedOpacity * (float) node.style.number(Style.Property.OPACITY, 1);
            copy.setComposite(AlphaComposite.SrcOver.derive(opacity));
            Shape shape = shape(node);
            Style.Shadow boxShadow = node.style.get(Style.Property.BOX_SHADOW, null);
            if (boxShadow != null)
                shadow(node, copy, boxShadow, layer -> layer.fill(shape));
            Color background = node.style.get(Style.Property.BACKGROUND, null);
            if (background != null) {
                copy.setColor(background);
                copy.fill(shape);
            }
            border(node, copy, shape);
            if (node.kind == Node.Kind.TEXT) {
                Style.Shadow textShadow = node.style.get(Style.Property.TEXT_SHADOW, null);
                if (textShadow != null)
                    shadow(node, copy, textShadow, layer -> text(node, layer));
                copy.setColor(node.style.color());
                text(node, copy);
            }
            if (node.kind == Node.Kind.ICON) {
                Style.Insets inset = node.style.inset();
                node.icon.paintIcon(null, copy, (int) (node.bounds.x + inset.left()),
                        (int) (node.bounds.y + inset.top()));
            }
            node.children.forEach(child -> paintNode(child, copy, opacity));
            if (node.sliderFraction != null) {
                double centerX = node.bounds.x + 8 + node.sliderFraction * Math.max(0, node.bounds.width - 16);
                double centerY = node.bounds.getCenterY();
                var thumb = new java.awt.geom.Ellipse2D.Double(centerX - 8, centerY - 8, 16, 16);
                copy.setColor(node.sliderColor);
                copy.fill(thumb);
                copy.setColor(Color.WHITE);
                copy.setStroke(new BasicStroke(2));
                copy.draw(thumb);
            }
            if (node.focus && node.kind != Node.Kind.INPUT) {
                copy.setColor(new Color(0, 125, 150));
                copy.setStroke(new BasicStroke(2));
                copy.draw(new RoundRectangle2D.Double(node.bounds.x + 2, node.bounds.y + 2,
                        Math.max(0, node.bounds.width - 4), Math.max(0, node.bounds.height - 4), 6, 6));
            }
        } finally {
            copy.dispose();
        }
    }

    private static void text(Node node, Graphics2D graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.clip(node.bounds);
            copy.setFont(node.style.font());
            for (Node.Line line : node.lines)
                copy.drawString(line.text(), (float) (node.bounds.x + line.x()),
                        (float) (node.bounds.y + line.baseline()));
        } finally {
            copy.dispose();
        }
    }

    private static void border(Node node, Graphics2D graphics, Shape outer) {
        Style.Border border = node.style.get(Style.Property.BORDER, null);
        if (border == null)
            return;
        Rectangle2D.Double bounds = node.bounds;
        double left = bounds.x, top = bounds.y, right = left + bounds.width, bottom = top + bounds.height;
        double innerLeft = Math.min(right, left + border.left().width());
        double innerRight = Math.max(innerLeft, right - border.right().width());
        double innerTop = Math.min(bottom, top + border.top().width());
        double innerBottom = Math.max(innerTop, bottom - border.bottom().width());
        double radius = Math.max(0, node.style.number(Style.Property.RADIUS, 0)
                - Math.max(Math.max(border.left().width(), border.right().width()),
                        Math.max(border.top().width(), border.bottom().width())));
        Area ring = new Area(outer);
        ring.subtract(new Area(new RoundRectangle2D.Double(innerLeft, innerTop, innerRight - innerLeft,
                innerBottom - innerTop, radius * 2, radius * 2)));
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.clip(ring);
            double centerX = (left + right) / 2, centerY = (top + bottom) / 2;
            polygon(copy, border.top().color(), left, top, right, top, centerX, centerY);
            polygon(copy, border.right().color(), right, top, right, bottom, centerX, centerY);
            polygon(copy, border.bottom().color(), right, bottom, left, bottom, centerX, centerY);
            polygon(copy, border.left().color(), left, bottom, left, top, centerX, centerY);
        } finally {
            copy.dispose();
        }
    }

    private static void polygon(Graphics2D graphics, Color color, double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2)
            path.lineTo(points[index], points[index + 1]);
        path.closePath();
        graphics.setColor(color);
        graphics.fill(path);
    }

    private static void shadow(Node node, Graphics2D graphics, Style.Shadow shadow, Consumer<Graphics2D> draw) {
        int radius = (int) Math.ceil(shadow.blur());
        int padding = radius * 2 + 2;
        int width = Math.max(1, (int) Math.ceil(node.bounds.width) + padding * 2);
        int height = Math.max(1, (int) Math.ceil(node.bounds.height) + padding * 2);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D layer = image.createGraphics();
        try {
            layer.setRenderingHints(graphics.getRenderingHints());
            layer.translate(padding - node.bounds.x, padding - node.bounds.y);
            layer.setColor(shadow.color());
            draw.accept(layer);
        } finally {
            layer.dispose();
        }
        if (radius > 0) {
            float[] kernel = new float[radius * 2 + 1];
            double sigma = Math.max(0.5, radius / 2.0), sum = 0;
            for (int index = 0; index < kernel.length; index++) {
                double distance = index - radius;
                kernel[index] = (float) Math.exp(-distance * distance / (2 * sigma * sigma));
                sum += kernel[index];
            }
            for (int index = 0; index < kernel.length; index++)
                kernel[index] /= (float) sum;
            image = new ConvolveOp(new Kernel(kernel.length, 1, kernel), ConvolveOp.EDGE_ZERO_FILL, null).filter(image,
                    null);
            image = new ConvolveOp(new Kernel(1, kernel.length, kernel), ConvolveOp.EDGE_ZERO_FILL, null).filter(image,
                    null);
        }
        graphics.drawImage(image, (int) Math.floor(node.bounds.x - padding + shadow.offsetX()),
                (int) Math.floor(node.bounds.y - padding + shadow.offsetY()), null);
    }
}