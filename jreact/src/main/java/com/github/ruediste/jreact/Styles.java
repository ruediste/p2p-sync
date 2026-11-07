package com.github.ruediste.jreact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Declares component-scoped style rules and resolves the node-tree cascade.
 *
 * <p>
 * Declarations belong to the active {@link Ui} component context. Direct-child
 * scope limits rules to nodes immediately below the declaration's anchor;
 * transitive scope allows matching throughout the component's rendered subtree,
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
    /** Determines which rendered nodes a component's rules may style. */
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

    /** Holds one component's rule list and its render-entry parent anchor. */
    static final class NodeStyleRules {
        final Node anchor;
        final List<ScopedRule> rules = new ArrayList<>();

        NodeStyleRules(Node anchor) {
            this.anchor = anchor;
        }
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

    static void resolve(Node node) {
        record Match(Rule rule, int depth, int order) {
        }
        var matches = new ArrayList<Match>();
        for (int depth = 0; depth < node.declarations.size(); depth++) {
            var declaration = node.declarations.get(depth);
            for (int order = 0; order < declaration.rules.size(); order++) {
                var scoped = declaration.rules.get(order);
                if ((scoped.scope == Scope.TRANSITIVE || node.parent == declaration.anchor)
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
        node.children.forEach(Styles::resolve);
    }
}