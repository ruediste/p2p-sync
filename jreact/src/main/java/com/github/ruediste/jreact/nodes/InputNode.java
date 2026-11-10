package com.github.ruediste.jreact.nodes;

import java.util.Objects;
import java.util.function.Consumer;

public class InputNode extends VisualNode {

    public String value;
    public Consumer<String> onChange;

    public InputNode(String value, Consumer<String> onChange) {
        this(null, value, onChange);
    }

    public InputNode(Object key, String value, Consumer<String> onChange) {
        super(key);
        this.value = Objects.requireNonNull(value);
        this.onChange = Objects.requireNonNull(onChange);
    }
}
