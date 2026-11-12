package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.ruediste.jreact.nodes.ComponentNode;
import com.github.ruediste.jreact.nodes.Node;

final class Reconciler {

    private Reconciler() {
    }

    public static class Context {
        JReactRoot root;

        public Context(JReactRoot jReactRoot) {
            this.root = jReactRoot;
        }
    }

    static void renderAndReconcileDirtyComponent(Context context, Fiber fiber) {
        ComponentNode component = fiber.component;
        List<Node> oldChildren = new ArrayList<>(component.children());
        renderBody(fiber.component);
        reconcileChildren(context, new ArrayList<>(component.children()), oldChildren);
    }

    private static void reconcileChildren(Context context, List<Node> newChildren, List<Node> oldChildren) {
        Map<Node.Identity, Node> oldByIdentity = new HashMap<>();
        for (Node oldChild : oldChildren)
            oldByIdentity.putIfAbsent(oldChild.identity(), oldChild);

        for (Node newChild : newChildren) {
            var oldChild = Optional.ofNullable(oldByIdentity.remove(newChild.identity()));
            reconcileNode(context, newChild, oldChild);
        }

        for (Node oldChild : oldByIdentity.values())
            disposeNode(context.root, oldChild);
    }

    private static void reconcileNode(Context context, Node newNode, Optional<Node> oldNode) {
        oldNode.ifPresent(o -> context.root.replaceNode(o, newNode));
        newNode.reconcile(oldNode);

        if (newNode instanceof ComponentNode newComponent) {
            var oldComponent = oldNode.map(ComponentNode.class::cast);
            reconcileComponent(context, newComponent, oldComponent);
            return;
        }

        reconcileChildren(context, newNode.children(), oldNode.map(o -> o.children()).orElse(List.of()));
    }

    private static void reconcileComponent(Context context, ComponentNode newComponent,
            Optional<ComponentNode> oldComponent) {
        boolean rerender = oldComponent.isEmpty()
                || newComponent.fiber.dirty
                || !newComponent.sameProperties(oldComponent.get());

        if (!rerender) {
            newComponent.replaceChildren(oldComponent.get().children());
            return;
        }

        List<Node> oldChildren = oldComponent.map(o -> new ArrayList<>(o.children())).orElseGet(ArrayList::new);
        renderBody(newComponent);
        reconcileChildren(context, new ArrayList<>(newComponent.children()), oldChildren);
    }

    private static void renderBody(ComponentNode component) {

        Ui.Context context = Ui.context();
        var fiber = component.fiber;

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
            component.render();
        } finally {
            context.declarationChain.removeLast();
            context.styleRules = previousRules;
            context.keyCounter = previousKeyCounter;
            context.fiber = previousFiber;
            context.onNodeCreated = previousCallback;
        }

        component.replaceChildren(produced);
        subTree(component).forEach(x -> x.afterRender());
        fiber.clearAfterRender();

    }

    private static List<Node> subTree(Node node) {
        List<Node> result = new ArrayList<>();
        result.add(node);
        for (Node child : node.children())
            result.addAll(subTree(child));
        return result;
    }

    private static void disposeNode(JReactRoot root, Node node) {
        root.invalidateNode(node);
        if (node instanceof ComponentNode component) {
            Fiber fiber = component.fiber;
            if (fiber != null)
                fiber.dispose();
        }
        for (Node child : node.children())
            disposeNode(root, child);
    }

    static void disposeTree(JReactRoot root, Node rootNode) {
        if (rootNode != null)
            disposeNode(root, rootNode);
    }
}
