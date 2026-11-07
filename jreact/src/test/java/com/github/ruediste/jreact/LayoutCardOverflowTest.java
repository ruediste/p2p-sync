package com.github.ruediste.jreact;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.github.ruediste.jreact.storybook.Storybook;

/**
 * Verifies the Overview story cards no longer overflow the row container or
 * overlap the following Workspace card after the intrinsic-sizing fix.
 */
class LayoutCardOverflowTest {
    @Test
    void overviewRowContainsCardsAndNoSiblingOverlap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var panel = Storybook.panel("Overview")) {
                panel.setSize(1000, 900);
                panel.refresh();

                // Card containers: divs directly containing a story heading
                List<Node> cards = new ArrayList<>();
                for (Node n : panel.tree().descendants()) {
                    if (n.children.isEmpty())
                        continue;
                    boolean hasHeading = n.children.stream().anyMatch(c -> {
                        String t = c.text();
                        return t != null && (t.equals("Primitives") || t.equals("Components")
                                || t.equals("Workspace"));
                    });
                    if (hasHeading)
                        cards.add(n);
                }

                System.out.println("Card containers: " + cards.size());
                for (Node c : cards) {
                    System.out.printf("  '%s' [x=%.0f,y=%.0f,w=%.0f,h=%.0f] parent=%s%n",
                            firstHeadingText(c), c.bounds.x, c.bounds.y, c.bounds.width, c.bounds.height,
                            c.parent != null ? firstHeadingText(c.parent) : "root");
                }

                // Find the row container (parent of Primitives + Components cards)
                Node row = null;
                if (cards.size() >= 2 && cards.get(0).parent == cards.get(1).parent)
                    row = cards.get(0).parent;
                if (row != null)
                    System.out.printf("Row container [x=%.0f,y=%.0f,w=%.0f,h=%.0f]%n", row.bounds.x,
                            row.bounds.y, row.bounds.width, row.bounds.height);

                int bad = 0;
                for (int i = 0; i < cards.size(); i++) {
                    for (int j = i + 1; j < cards.size(); j++) {
                        Node a = cards.get(i);
                        Node b = cards.get(j);
                        if (a.parent == b.parent)
                            continue; // siblings under row are expected side-by-side, not overlapping
                        double ix = Math.min(a.bounds.getMaxX(), b.bounds.getMaxX())
                                - Math.max(a.bounds.x, b.bounds.x);
                        double iy = Math.min(a.bounds.getMaxY(), b.bounds.getMaxY())
                                - Math.max(a.bounds.y, b.bounds.y);
                        if (ix > 1 && iy > 1) {
                            bad++;
                            System.out.printf("OVERLAP: '%s' vs '%s' (ix=%.0f, iy=%.0f)%n",
                                    firstHeadingText(a), firstHeadingText(b), ix, iy);
                        }
                    }
                }
                // Cards must not overflow the row container bottom (only row children)
                if (row != null) {
                    for (Node c : cards) {
                        if (c.parent != row)
                            continue;
                        if (c.bounds.getMaxY() > row.bounds.getMaxY() + 1) {
                            bad++;
                            System.out.printf("CARD '%s' overflows row bottom: cardMaxY=%.0f rowMaxY=%.0f%n",
                                    firstHeadingText(c), c.bounds.getMaxY(), row.bounds.getMaxY());
                        }
                    }
                }
                if (bad > 0)
                    fail("Found " + bad + " card layout problems");
                System.out.println("Card layout check: PASS");
            }
        });
    }

    private static String firstHeadingText(Node n) {
        if (n.text() != null)
            return n.text();
        for (Node c : n.children) {
            String t = c.text();
            if (t != null)
                return t;
        }
        return "?";
    }
}
