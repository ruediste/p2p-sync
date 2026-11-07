package com.github.ruediste.jreact.components;

import java.awt.Color;
import java.util.Objects;

public record Theme(Color canvas, Color surface, Color text, Color muted, Color border,
        Color primary, Color primaryHover, Color onPrimary, Color danger, Color success, String fontFamily) {
    public Theme {
        Objects.requireNonNull(canvas);
        Objects.requireNonNull(surface);
        Objects.requireNonNull(text);
        Objects.requireNonNull(muted);
        Objects.requireNonNull(border);
        Objects.requireNonNull(primary);
        Objects.requireNonNull(primaryHover);
        Objects.requireNonNull(onPrimary);
        Objects.requireNonNull(danger);
        Objects.requireNonNull(success);
        Objects.requireNonNull(fontFamily);
    }

    public static Theme light() {
        return new Theme(new Color(243, 246, 246), Color.WHITE, new Color(28, 39, 43), new Color(99, 113, 119),
                new Color(213, 222, 224), new Color(0, 114, 126), new Color(0, 85, 97), Color.WHITE,
                new Color(187, 50, 71), new Color(32, 127, 81), "DejaVu Sans");
    }

    public static Theme highContrast() {
        return new Theme(Color.WHITE, Color.WHITE, Color.BLACK, new Color(60, 60, 60), Color.BLACK,
                new Color(0, 70, 160), new Color(0, 40, 100), Color.WHITE,
                new Color(155, 0, 25), new Color(0, 100, 30), "DejaVu Sans");
    }
}