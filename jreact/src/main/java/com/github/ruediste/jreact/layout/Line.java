package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A start/end line segment, used for grid track positions. */
public final class Line<T> {

    public T start;
    public T end;

    public Line(T start, T end) {
        this.start = start;
        this.end = end;
    }

    public static <T> Line<T> of(T start, T end) {
        return new Line<>(start, end);
    }

    @Override
    public String toString() {
        return "{start=" + start + ", end=" + end + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Line<?> other)) {
            return false;
        }
        return Objects.equals(start, other.start) && Objects.equals(end, other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
