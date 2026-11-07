package com.github.ruediste.jreact.layout;

import java.util.Objects;

/** A grid line placement, a span, or automatic placement. */
public final class GridPlacement {

    public enum Kind {
        AUTO, LINE, SPAN
    }

    public final Kind kind;
    public final int line;
    public final int span;
    public final String ident;

    private GridPlacement(Kind kind, int line, int span, String ident) {
        this.kind = kind;
        this.line = line;
        this.span = span;
        this.ident = ident;
    }

    public static GridPlacement auto() {
        return new GridPlacement(Kind.AUTO, 0, 0, null);
    }

    public static GridPlacement line(int line) {
        return new GridPlacement(Kind.LINE, line, 0, null);
    }

    public static GridPlacement line(int line, String ident) {
        return new GridPlacement(Kind.LINE, line, 0, ident);
    }

    public static GridPlacement span(int span) {
        return new GridPlacement(Kind.SPAN, 0, span, null);
    }

    public static GridPlacement span(int span, String ident) {
        return new GridPlacement(Kind.SPAN, 0, span, ident);
    }

    public static GridPlacement of(Object value) {
        if (value instanceof GridPlacement placement) {
            return placement;
        }
        if (value instanceof Number number) {
            return line(number.intValue());
        }
        if (value instanceof CharSequence text) {
            String s = text.toString().trim();
            if (s.equalsIgnoreCase("auto")) {
                return auto();
            }
            if (s.startsWith("span ")) {
                String rest = s.substring(5).trim();
                try {
                    return span(Integer.parseInt(rest));
                } catch (NumberFormatException ignored) {
                    return span(1, rest);
                }
            }
            return line(Integer.parseInt(s));
        }
        throw new IllegalArgumentException("Expected a grid placement, got: " + value);
    }

    public boolean isAuto() {
        return kind == Kind.AUTO;
    }

    public boolean isLine() {
        return kind == Kind.LINE;
    }

    public boolean isSpan() {
        return kind == Kind.SPAN;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case AUTO -> "auto";
            case LINE -> "line(" + line + (ident == null ? "" : ", " + ident) + ")";
            case SPAN -> "span(" + span + (ident == null ? "" : ", " + ident) + ")";
        };
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof GridPlacement other)) {
            return false;
        }
        return kind == other.kind && line == other.line && span == other.span
                && Objects.equals(ident, other.ident);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, line, span, ident);
    }
}
