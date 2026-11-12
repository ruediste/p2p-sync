package com.github.ruediste.jreact;

import java.awt.geom.Rectangle2D;

import com.github.ruediste.jreact.nodes.Node;

public record RefreshResult(Node tree, Object focusKey,
                Rectangle2D dirtyRegion) {
}
