package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A two-dimensional point. */
public final class Point<T> {

    public T x;
    public T y;

    public Point(T x, T y) {
        this.x = x;
        this.y = y;
    }

    public static <T> Point<T> of(T x, T y) {
        return new Point<>(x, y);
    }

    public Point<T> copy() {
        return new Point<>(x, y);
    }

    @Override
    public String toString() {
        return "{x=" + x + ", y=" + y + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Point<?> other)) {
            return false;
        }
        return Objects.equals(x, other.x) && Objects.equals(y, other.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
