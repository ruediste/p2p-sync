package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.Node;

final class Reconciler {

    private Reconciler() {
    }

    static void renderRoot(ComponentNode newRoot, ComponentNode oldRoot) {
        if (newRoot.fiber == null)
            newRoot.fiber = oldRoot == null || oldRoot.fiber == null ? new Fiber() : oldRoot.fiber;
        newRoot.fiber.setParent(null);

        List<Node> oldChildren = oldRoot == null ? List.of() : new ArrayList<>(oldRoot.children());
        renderBody(newRoot, newRoot.fiber);
        reconcileChildren(new ArrayList<>(newRoot.children()), oldChildren, newRoot.fiber);
        if (oldRoot != null)
            oldRoot.clearChildren();
        newRoot.fiber.clearAfterRender();
    }

    static void reconcileDirty(ComponentNode root, Fiber rootFiber) {
        List<Node> children = new ArrayList<>(root.children());
        reconcileChildren(children, children, rootFiber);
        rootFiber.clearAfterRender();
    }

    private static void renderBody(ComponentNode component, Fiber fiber) {
        component.clearChildren();

        Ui.Context context = Ui.context();
        Fiber previousFiber = context.fiber;
        Consumer<Node> previousCallback = context.onNodeCreated;
        Styles.NodeStyleRules previousRules = context.styleRules;
        int previousKeyCounter = context.keyCounter;
        List<Node> produced = new ArrayList<>();

        context.fiber = fiber;
        context.onNodeCreated = produced::add;
        context.styleRules = new Styles.NodeStyleRules();
        context.declarationChain.add(context.styleRules);
        context.keyCounter = 0;
        fiber.beginRender();
        try {
            component.render.run();
        } finally {
            context.declarationChain.removeLast();
            context.styleRules = previousRules;
            context.keyCounter = previousKeyCounter;
            context.fiber = previousFiber;
            context.onNodeCreated = previousCallback;
        }

        component.replaceChildren(produced);
    }

    private static void reconcileChildren(List<Node> newChildren, List<Node> oldChildren, Fiber parentFiber) {
        normalizeComponentKeys(newChildren);
        normalizeComponentKeys(oldChildren);
        Map<Node.Identity, Node> oldByIdentity = new HashMap<>();
        for (Node oldChild : oldChildren)
            oldByIdentity.putIfAbsent(oldChild.identity(), oldChild);

        for (Node newChild : newChildren) {
            Node oldChild = oldByIdentity.remove(newChild.identity());
            reconcileNode(newChild, oldChild, parentFiber);
        }

        for (Node oldChild : oldByIdentity.values())
            disposeNode(oldChild);
    }

    private static void normalizeComponentKeys(List<Node> children) {
        for (Node child : children) {
            if (child instanceof ComponentNode component && component.properties instanceof Component properties
                    && properties.key != null)
                component.key = properties.key;
        }
    }

    private static void reconcileNode(Node newNode, Node oldNode, Fiber parentFiber) {
        if (newNode instanceof ComponentNode newComponent) {
            ComponentNode oldComponent = oldNode instanceof ComponentNode component ? component : null;
            reconcileComponent(newComponent, oldComponent, parentFiber);
            return;
        }

        List<Node> children = new ArrayList<>(newNode.children());
        if (newNode != oldNode) {
            List<Node> oldChildren = oldNode == null ? List.of() : new ArrayList<>(oldNode.children());
            reconcileChildren(children, oldChildren, parentFiber);
            if (oldNode != null)
                oldNode.clearChildren();
            return;
        }
        reconcileChildren(children, children, parentFiber);
    }

    private static void reconcileComponent(ComponentNode newComponent, ComponentNode oldComponent, Fiber parentFiber) {
        Fiber fiber = oldComponent == null || oldComponent.fiber == null ? new Fiber() : oldComponent.fiber;
        fiber.setParent(parentFiber);
        newComponent.fiber = fiber;

        List<Node> oldChildren = oldComponent == null ? List.of() : new ArrayList<>(oldComponent.children());
        boolean rerenderBody = oldComponent == null
                || !propertiesEqual(newComponent.properties, oldComponent.properties)
                || fiber.isDirty();

        if (!rerenderBody) {
            if (fiber.isSubtreeDirty()) {
                List<Node> children = new ArrayList<>(oldChildren);
                reconcileChildren(children, children, fiber);
                adopt(newComponent, oldComponent, children);
            } else {
                adopt(newComponent, oldComponent, oldChildren);
            }
            fiber.clearAfterRender();
            return;
        }

        renderBody(newComponent, fiber);
        reconcileChildren(new ArrayList<>(newComponent.children()), oldChildren, fiber);
        if (oldComponent != null && oldComponent != newComponent)
            oldComponent.clearChildren();
        fiber.clearAfterRender();
    }

    private static boolean propertiesEqual(Object newProperties, Object oldProperties) {
        if (newProperties == oldProperties)
            return true;
        if (newProperties instanceof Component newComponent && oldProperties instanceof Component oldComponent)
            return newComponent.sameProperties(oldComponent);
        return Objects.deepEquals(newProperties, oldProperties);
    }

    private static void adopt(ComponentNode newComponent, ComponentNode oldComponent, List<Node> children) {
        if (newComponent == oldComponent)
            return;
        if (oldComponent != null)
            oldComponent.clearChildren();
        newComponent.replaceChildren(children);
    }

    private static void disposeNode(Node node) {
        if (node instanceof ComponentNode component && component.fiber != null) {
            component.fiber.dispose();
            component.fiber = null;
        }
        List<Node> children = new ArrayList<>(node.children());
        node.clearChildren();
        for (Node child : children)
            disposeNode(child);
    }

    static void disposeTree(Node root) {
        if (root != null)
            disposeNode(root);
    }
}
