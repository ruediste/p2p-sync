package com.github.ruediste.jreact;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public abstract class Component {

    public Object key;

    protected Component() {
        Ui.component(getClass(), this, this::render);
    }

    public abstract void render();

    final boolean sameProperties(Component other) {
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
