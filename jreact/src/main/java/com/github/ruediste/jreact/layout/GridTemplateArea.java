package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A named rectangular region in a grid template. Coordinates are 1-based lines. */
public final class GridTemplateArea {

    public final String name;
    public final int rowStart;
    public final int rowEnd;
    public final int columnStart;
    public final int columnEnd;

    public GridTemplateArea(String name, int rowStart, int rowEnd, int columnStart, int columnEnd) {
        this.name = name;
        this.rowStart = rowStart;
        this.rowEnd = rowEnd;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
    }

    @Override
    public String toString() {
        return "{name=" + name + ", rows=" + rowStart + ".." + rowEnd + ", columns=" + columnStart
                + ".." + columnEnd + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof GridTemplateArea other)) {
            return false;
        }
        return rowStart == other.rowStart && rowEnd == other.rowEnd
                && columnStart == other.columnStart && columnEnd == other.columnEnd
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rowStart, rowEnd, columnStart, columnEnd);
    }
}
