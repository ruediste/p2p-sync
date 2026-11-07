package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A four-sided rectangle of values. */
public final class Rect<T> {

    public T left;
    public T right;
    public T top;
    public T bottom;

    public Rect(T left, T right, T top, T bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    public static <T> Rect<T> all(T value) {
        return new Rect<>(value, value, value, value);
    }

    public Rect<T> copy() {
        return new Rect<>(left, right, top, bottom);
    }

    @Override
    public String toString() {
        return "{left=" + left + ", right=" + right + ", top=" + top + ", bottom=" + bottom + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Rect<?> other)) {
            return false;
        }
        return Objects.equals(left, other.left) && Objects.equals(right, other.right)
                && Objects.equals(top, other.top) && Objects.equals(bottom, other.bottom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, top, bottom);
    }
}
