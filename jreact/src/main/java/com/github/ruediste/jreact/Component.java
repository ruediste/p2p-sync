package com.github.ruediste.jreact;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

import com.github.ruediste.jreact.nodes.ComponentNode;

public abstract class Component {

    public Object key;

    protected Component() {
        Ui.addNode(new ComponentNodeImpl(getClass()));
    }

    public abstract void render();

    private class ComponentNodeImpl extends ComponentNode {

        protected ComponentNodeImpl(Class<?> componentClass) {
            super(componentClass);
        }

        public Component getComponent() {
            return Component.this;
        }

        @Override
        public void render() {
            Component.this.render();
        }

        @Override
        public void afterRender() {
            super.afterRender();
            if (Component.this.key != null)
                key = Component.this.key;
        }

        @Override
        public boolean sameProperties(ComponentNode other) {
            if (other == null || getClass() != other.getClass())
                return false;
            return Component.this.sameProperties(((ComponentNodeImpl) other).getComponent());
        }
    }

    private final boolean sameProperties(Component other) {
        if (other == null || getClass() != other.getClass())
            return false;
        for (Class<?> type = getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                    continue;
                try {
                    field.setAccessible(true);
                    if (!Objects.deepEquals(field.get(this), field.get(other)))
                        return false;
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Cannot read component property " + field, exception);
                }
            }
        }
        return true;
    }
}
