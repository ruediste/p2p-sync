package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A two-dimensional size. */
public final class Size<T> {

    public T width;
    public T height;

    public Size(T width, T height) {
        this.width = width;
        this.height = height;
    }

    public static <T> Size<T> of(T width, T height) {
        return new Size<>(width, height);
    }

    public Size<T> copy() {
        return new Size<>(width, height);
    }

    @Override
    public String toString() {
        return "{width=" + width + ", height=" + height + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Size<?> other)) {
            return false;
        }
        return Objects.equals(width, other.width) && Objects.equals(height, other.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }
}
