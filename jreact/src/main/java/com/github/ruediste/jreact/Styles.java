package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.ruediste.jreact.nodes.Node;

/**
 * Declares component-scoped style rules and resolves the node-tree cascade.
 *
 * <p>
 * Declarations belong to the active {@link Ui} component context. Direct-child
 * scope applies a rule to every node rendered by the declaring component
 * itself,
 * including nested layout nodes, but not to nodes rendered by nested
 * components.
 * Transitive scope extends matching to the component's entire rendered subtree,
 * including nested components. Scope eligibility is separate from selector
 * matching and does not increase specificity.
 *
 * <p>
 * Resolution applies matching rules by specificity, then by nearest declaring
 * component, then by later rule order. A node's inline properties take final
 * precedence. Resolved styles are replaced on each pass, allowing interaction
 * changes to affect pseudo-class rules without mutating the original
 * declarations.
 */
public final class Styles {
    /**
     * Determines which rendered nodes a component's rules may style.
     *
     * <p>
     * {@link #DIRECT_CHILDREN} rules apply to nodes rendered by the declaring
     * component itself, including nested layout nodes. {@link #TRANSITIVE} rules
     * additionally apply to nodes rendered by nested components.
     */
    public enum Scope {
        DIRECT_CHILDREN, TRANSITIVE
    }

    /** Associates a selector with the property declarations applied on a match. */
    public record Rule(Selector selector, Style style) {
        public Rule {
            Objects.requireNonNull(selector);
            Objects.requireNonNull(style);
        }
    }

    /** Holds one component's rule list. */
    public static final class NodeStyleRules {
        final List<ScopedRule> rules = new ArrayList<>();
    }

    /** Couples a rule with its scope inside a component declaration. */
    record ScopedRule(Scope scope, Rule rule) {
    }

    private Styles() {
    }

    public static Rule rule(Selector selector, Consumer<Style> style) {
        Style result = new Style();
        style.accept(result);
        return new Rule(selector, result);
    }

    public static void rules(Rule... rules) {
        rules(Scope.DIRECT_CHILDREN, rules);
    }

    public static void rules(Scope scope, Rule... rules) {
        Objects.requireNonNull(scope);
        var declaration = Ui.context().styleRules;
        for (Rule rule : rules)
            declaration.rules.add(new ScopedRule(scope, Objects.requireNonNull(rule)));
    }

    /**
     * Resolves and installs the cascaded style for {@code node} and all of its
     * descendants.
     *
     * <p>
     * For each node, candidate rules come from the recorded style-rule chain,
     * outermost declaring component first. A rule is eligible when its scope is
     * {@link Scope#TRANSITIVE} or when it belongs to the nearest declaring
     * component, which owns the node. Eligible rules are then filtered by
     * selector match and applied by specificity, then nearest declaring
     * component, then declaration order. The node's inline properties override
     * all matched rules.
     *
     * <p>
     * The resulting style replaces any previously resolved style, so
     * pseudo-class and other interaction-dependent rules can be re-evaluated
     * without mutating the declared rules. Children are resolved recursively
     * after their parent.
     *
     * @param node root of the subtree whose styles are resolved
     */
    static void resolve(Node node) {
        record Match(Rule rule, int depth, int order) {
        }
        var matches = new ArrayList<Match>();
        var declarations = node.declarations;
        var ownerRules = declarations.isEmpty() ? null : declarations.getLast();
        for (int depth = 0; depth < declarations.size(); depth++) {
            var declaration = declarations.get(depth);
            for (int order = 0; order < declaration.rules.size(); order++) {
                var scoped = declaration.rules.get(order);
                if ((scoped.scope == Scope.TRANSITIVE || declaration == ownerRules)
                        && scoped.rule.selector.matches(node))
                    matches.add(new Match(scoped.rule, depth, order));
            }
        }
        matches.sort((left, right) -> {
            int result = left.rule.selector.compareSpecificity(right.rule.selector);
            if (result == 0)
                result = Integer.compare(left.depth, right.depth);
            if (result == 0)
                result = Integer.compare(left.order, right.order);
            return result;
        });
        Style style = new Style();
        matches.forEach(match -> style.values.putAll(match.rule.style.values));
        style.values.putAll(node.inline.values);
        style.id = node.inline.id;
        style.className = node.inline.className;
        node.style = style;
        node.applyLayoutDefaults();
        node.children().forEach(Styles::resolve);
    }
}