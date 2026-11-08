package com.github.ruediste.jreact;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Connects a freshly rendered node tree to the previous tree by node key.
 *
 * <p>
 * Reconciliation transfers retained state from each previous node to its
 * successor. Dirty-region computation then compares each new node's current
 * paint state with the state it inherited, and adds the paint bounds of
 * previous nodes that no longer have a successor.
 */
final class NodeReconciler {
    static final class Result {
        final Node previous;
        final Map<Node.Key, Node> previousByKey;
        final Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        private Result(Node previous, Map<Node.Key, Node> previousByKey) {
            this.previous = previous;
            this.previousByKey = previousByKey;
        }
    }

    private NodeReconciler() {
    }

    static Result reconcile(Node current, Node previous) {
        if (previous == null)
            return new Result(null, Map.of());
        var previousByKey = new LinkedHashMap<Node.Key, Node>();
        previous.descendants().forEach(node -> previousByKey.put(node.key, node));
        var result = new Result(previous, previousByKey);
        for (Node node : current.descendants()) {
            Node old = previousByKey.get(node.key);
            if (old != null) {
                result.visited.add(old);
                node.reconcile(old);
            }
        }
        // todo: add closing of nodes that are no longer present in the current tree
        return result;
    }

    static Rectangle dirtyRegion(Node current, Result result) {
        Rectangle dirty = null;
        for (Node node : current.descendants())
            dirty = union(dirty, node.dirtyRegion());
        if (result.previous != null)
            for (Node old : result.previous.descendants())
                if (!result.visited.contains(old))
                    dirty = union(dirty, old.lastCommittedBounds());
        return dirty;
    }

    private static Rectangle union(Rectangle left, Rectangle right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return left.union(right);
    }
}
