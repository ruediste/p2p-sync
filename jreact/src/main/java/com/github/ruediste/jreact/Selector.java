package com.github.ruediste.jreact;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable node-matching expression for scoped style rules.
 *
 * <p>
 * Composes primitive-type, ID, class, and interaction-state predicates with
 * compound, direct-child, and descendant relationships. Relationship selectors
 * match the right-hand node and inspect its ancestors. Matching reads the node
 * tree and its current interaction flags; it does not modify them.
 *
 * <p>
 * Tracks specificity as ID, class/pseudo-class, and type counts, compared in
 * that order. {@link Styles} separately determines scope eligibility and breaks
 * specificity ties by declaration proximity and rule order.
 */
public final class Selector {
    /** Interaction states supplied by the host and available to style rules. */
    public enum Pseudo {
        HOVER, ACTIVE, FOCUS, DISABLED
    }

    private final Predicate<Node> test;
    private final int ids, classes, types;

    private Selector(Predicate<Node> test, int ids, int classes, int types) {
        this.test = test;
        this.ids = ids;
        this.classes = classes;
        this.types = types;
    }

    public static Selector type(Class<? extends Node> type) {
        Objects.requireNonNull(type);
        return new Selector(type::isInstance, 0, 0, 1);
    }

    public static Selector id(String id) {
        Objects.requireNonNull(id);
        return new Selector(node -> id.equals(node.inline.id), 1, 0, 0);
    }

    public static Selector className(String name) {
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("A selector class must be a single non-empty name");
        return new Selector(node -> java.util.Arrays.asList(node.inline.className.split("\\s+")).contains(name), 0, 1,
                0);
    }

    public Selector and(Selector other) {
        return new Selector(node -> matches(node) && other.matches(node),
                ids + other.ids, classes + other.classes, types + other.types);
    }

    public Selector pseudo(Pseudo pseudo) {
        Objects.requireNonNull(pseudo);
        return new Selector(node -> matches(node) && switch (pseudo) {
            case HOVER -> node.hover;
            case ACTIVE -> node.active;
            case FOCUS -> node.focus;
            case DISABLED -> node.disabled;
        }, ids, classes + 1, types);
    }

    public Selector hover() {
        return pseudo(Pseudo.HOVER);
    }

    public Selector active() {
        return pseudo(Pseudo.ACTIVE);
    }

    public Selector focus() {
        return pseudo(Pseudo.FOCUS);
    }

    public Selector disabled() {
        return pseudo(Pseudo.DISABLED);
    }

    public Selector child(Selector child) {
        return new Selector(node -> child.matches(node) && node.parent != null && matches(node.parent),
                ids + child.ids, classes + child.classes, types + child.types);
    }

    public Selector descendant(Selector descendant) {
        return new Selector(node -> {
            if (!descendant.matches(node))
                return false;
            for (Node ancestor = node.parent; ancestor != null; ancestor = ancestor.parent)
                if (matches(ancestor))
                    return true;
            return false;
        }, ids + descendant.ids, classes + descendant.classes, types + descendant.types);
    }

    public boolean matches(Node node) {
        return test.test(node);
    }

    int compareSpecificity(Selector other) {
        int result = Integer.compare(ids, other.ids);
        if (result == 0)
            result = Integer.compare(classes, other.classes);
        if (result == 0)
            result = Integer.compare(types, other.types);
        return result;
    }
}