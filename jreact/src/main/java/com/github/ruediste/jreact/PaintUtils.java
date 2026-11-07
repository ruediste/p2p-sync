package com.github.ruediste.jreact;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Consumer;

public final class PaintUtils {
    private PaintUtils() {
    }

    public static Shape shape(Node node) {
        double radius = node.style.number(StyleProperties.RADIUS, 0);
        return new RoundRectangle2D.Double(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height,
                radius * 2, radius * 2);
    }

    public static void paintBorder(Node node, Graphics2D graphics, Shape outer) {
        Style.Border border = node.style.get(StyleProperties.BORDER, null);
        if (border == null)
            return;
        java.awt.geom.Rectangle2D.Double bounds = node.bounds;
        double left = bounds.x, top = bounds.y, right = left + bounds.width, bottom = top + bounds.height;
        double innerLeft = Math.min(right, left + border.left().width());
        double innerRight = Math.max(innerLeft, right - border.right().width());
        double innerTop = Math.min(bottom, top + border.top().width());
        double innerBottom = Math.max(innerTop, bottom - border.bottom().width());
        double radius = Math.max(0, node.style.number(StyleProperties.RADIUS, 0)
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

    public static void shadow(Node node, Graphics2D graphics, Style.Shadow shadow, Consumer<Graphics2D> draw) {
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
