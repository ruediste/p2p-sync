package com.github.ruediste.jreact.nodes;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SelectNode extends VisualNode {

    public String value;
    public final List<String> options;
    public Consumer<String> onChange;

    public SelectNode(String value, List<String> options, Consumer<String> onChange) {
        this(null, value, options, onChange);
    }

    public SelectNode(Object key, String value, List<String> options, Consumer<String> onChange) {
        super(key);
        this.value = Objects.requireNonNull(value);
        this.options = List.copyOf(options);
        if (this.options.isEmpty())
            throw new IllegalArgumentException("Select options must not be empty");
        if (!this.options.contains(value))
            throw new IllegalArgumentException("Selected value must be one of the options");
        this.onChange = Objects.requireNonNull(onChange);
    }
}
