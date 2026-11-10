package com.github.ruediste.jreact;

import java.awt.geom.Rectangle2D;
import java.util.Map;

import com.github.ruediste.jreact.nodes.Node;

public record RefreshResult(Node tree, Map<Node.Identity, Node> nodeIndex, Object focusKey,
        Rectangle2D dirtyRegion) {
}
