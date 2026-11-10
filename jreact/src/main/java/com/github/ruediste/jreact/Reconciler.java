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
        newRoot.depth = 0;
        newRoot.fiber.attach(Ui.context().root, newRoot);

        List<Node> oldChildren = oldRoot == null ? List.of() : new ArrayList<>(oldRoot.children());
        renderBody(newRoot, newRoot.fiber);
        reconcileChildren(new ArrayList<>(newRoot.children()), oldChildren);
        if (oldRoot != null)
            oldRoot.clearChildren();
        newRoot.fiber.clearAfterRender();
    }

    static void reconcileDirtyComponent(ComponentNode component) {
        Fiber fiber = component.fiber;
        List<Node> oldChildren = new ArrayList<>(component.children());
        renderBody(component, fiber);
        reconcileChildren(new ArrayList<>(component.children()), oldChildren);
        fiber.clearAfterRender();
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

    private static void reconcileChildren(List<Node> newChildren, List<Node> oldChildren) {
        normalizeComponentKeys(newChildren);
        normalizeComponentKeys(oldChildren);
        Map<Node.Identity, Node> oldByIdentity = new HashMap<>();
        for (Node oldChild : oldChildren)
            oldByIdentity.putIfAbsent(oldChild.identity(), oldChild);

        for (Node newChild : newChildren) {
            Node oldChild = oldByIdentity.remove(newChild.identity());
            reconcileNode(newChild, oldChild);
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

    private static void reconcileNode(Node newNode, Node oldNode) {
        if (newNode instanceof ComponentNode newComponent) {
            ComponentNode oldComponent = oldNode instanceof ComponentNode component ? component : null;
            reconcileComponent(newComponent, oldComponent);
            return;
        }

        List<Node> children = new ArrayList<>(newNode.children());
        if (newNode != oldNode) {
            List<Node> oldChildren = oldNode == null ? List.of() : new ArrayList<>(oldNode.children());
            reconcileChildren(children, oldChildren);
            if (oldNode != null)
                oldNode.clearChildren();
            return;
        }
        reconcileChildren(children, children);
    }

    private static void reconcileComponent(ComponentNode newComponent, ComponentNode oldComponent) {
        Fiber fiber = oldComponent == null || oldComponent.fiber == null ? new Fiber() : oldComponent.fiber;
        fiber.attach(Ui.context().root, newComponent);
        newComponent.fiber = fiber;

        List<Node> oldChildren = oldComponent == null ? List.of() : new ArrayList<>(oldComponent.children());
        boolean rerenderBody = oldComponent == null
                || !propertiesEqual(newComponent.properties, oldComponent.properties)
                || fiber.isDirty();

        if (!rerenderBody) {
            adopt(newComponent, oldComponent, oldChildren);
            fiber.clearAfterRender();
            return;
        }

        renderBody(newComponent, fiber);
        reconcileChildren(new ArrayList<>(newComponent.children()), oldChildren);
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
