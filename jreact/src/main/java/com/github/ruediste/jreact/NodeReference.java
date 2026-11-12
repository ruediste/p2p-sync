package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;

import com.github.ruediste.jreact.nodes.Node;

final class NodeReference {

    private final List<Node> path = new ArrayList<>();
    private boolean truncated;

    List<Node> path() {
        return path;
    }

    Node target() {
        return truncated ? null : lastNode();
    }

    Node lastNode() {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    boolean isTruncated() {
        return truncated;
    }

    boolean isEmpty() {
        return path.isEmpty();
    }

    boolean equalsPath(List<Node> candidate) {
        return !truncated && path.equals(candidate);
    }

    boolean contains(Node node) {
        return node.depth >= 0 && node.depth < path.size() && path.get(node.depth) == node;
    }

    void setPath(List<Node> nodes) {
        path.clear();
        path.addAll(nodes);
        truncated = false;
    }

    void clear() {
        path.clear();
        truncated = false;
    }

    void replace(Node oldNode, Node newNode) {
        if (oldNode == newNode)
            return;
        for (int index = 0; index < path.size(); index++) {
            if (path.get(index) == oldNode)
                path.set(index, newNode);
        }
    }

    void invalidateAt(Node removedNode) {
        for (int index = 0; index < path.size(); index++) {
            if (path.get(index) == removedNode) {
                while (path.size() > index)
                    path.removeLast();
                truncated = true;
                return;
            }
        }
    }
}
