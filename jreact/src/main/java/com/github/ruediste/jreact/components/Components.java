package com.github.ruediste.jreact.components;

import static com.github.ruediste.jreact.Ui.component;
import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.input;
import static com.github.ruediste.jreact.Ui.select;
import static com.github.ruediste.jreact.Ui.text;

import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.JustifyContent;
import com.github.ruediste.jreact.layout.FlexDirection;
import com.github.ruediste.jreact.layout.FlexWrap;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import com.github.ruediste.jreact.Node;
import com.github.ruediste.jreact.Selector;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.Ui;
import com.github.ruediste.jreact.nodes.SliderNode;

public final class Components {
    public enum Variant {
        PRIMARY, SECONDARY, DANGER
    }

    public enum Tone {
        INFO, SUCCESS, DANGER
    }

    public final Theme theme;

    public Components(Theme theme) {
        this.theme = Objects.requireNonNull(theme);
    }

    public Style typography(Style style) {
        return style.color(theme.text()).fontFamily(theme.fontFamily()).fontSize(14);
    }

    public Node label(String value) {
        return text(value, this::typography);
    }

    public Node heading(String value) {
        return text(value, style -> typography(style).fontSize(22).fontWeight(true));
    }

    public Node muted(String value) {
        return text(value, style -> typography(style).color(theme.muted()).fontSize(12));
    }

    public Node button(String label, Runnable action) {
        return button(label, Variant.PRIMARY, false, action);
    }

    public Node button(String label, Variant variant, boolean disabled, Runnable action) {
        return button(label, variant, disabled, action, style -> {
        });
    }

    public Node button(String label, Variant variant, boolean disabled, Runnable action, Consumer<Style> inline) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(variant);
        Color background = switch (variant) {
            case PRIMARY -> theme.primary();
            case SECONDARY -> theme.surface();
            case DANGER -> theme.danger();
        };
        Color border = variant == Variant.SECONDARY ? theme.border() : background;
        Color hover = variant == Variant.PRIMARY ? theme.primaryHover() : background.darker();
        Color foreground = variant == Variant.SECONDARY ? theme.text() : theme.onPrimary();
        String className = "jr-button-"
                + Integer.toHexString(Objects.hash(variant, background, border, hover, foreground));
        Styles.rules(Styles.Scope.TRANSITIVE,
                Styles.rule(Selector.className(className),
                        style -> style.backgroundColor(background).border(1, border)),
                Styles.rule(Selector.className(className).hover(), style -> style.backgroundColor(hover)),
                Styles.rule(Selector.className(className).active(),
                        style -> style.backgroundColor(background.darker())),
                Styles.rule(Selector.className(className).disabled(), style -> style.opacity(0.4)));
        return div(style -> {
            style.className(className).height(36).padding(8, 14, 8, 14).borderRadius(6)
                    .alignItems(AlignItems.Center).justifyContent(JustifyContent.Center).flexShrink(0);
            inline.accept(style);
        }, () -> text(label, style -> typography(style).color(foreground).fontWeight(true)))
                .onClick(action).disabled(disabled).accessibleName(label);
    }

    public Node checkbox(String label, boolean checked, boolean disabled, Consumer<Boolean> change) {
        return div(style -> style.height(32).alignItems(AlignItems.Center), () -> {
            div(style -> style.width(20).height(20).borderRadius(4).border(1, theme.border())
                    .backgroundColor(checked ? theme.primary() : theme.surface()).margin(0, 10, 0, 0)
                    .justifyContent(JustifyContent.Center).alignItems(AlignItems.Center), () -> {
                        if (checked)
                            text("\u2713", style -> typography(style).color(theme.onPrimary()).fontSize(15));
                    });
            text(label, style -> typography(style).opacity(disabled ? 0.4 : 1));
        }).onClick(() -> change.accept(!checked)).disabled(disabled)
                .accessibleName(label + (checked ? ", checked" : ", unchecked"));
    }

    public Node textField(String label, String value, boolean disabled, Consumer<String> change) {
        return div(style -> style.flexDirection(FlexDirection.Column).height(66).flexShrink(0), () -> {
            text(label, style -> typography(style).fontSize(12).fontWeight(true).margin(0, 0, 6, 0));
            input(value, change, style -> typography(style).height(36).padding(7, 10, 7, 10)
                    .border(1, theme.border()).borderRadius(5).backgroundColor(theme.surface()))
                    .disabled(disabled).accessibleName(label);
        });
    }

    public Node selectField(String label, String value, List<String> options, boolean disabled,
            Consumer<String> change) {
        return div(style -> style.flexDirection(FlexDirection.Column).height(66).flexShrink(0), () -> {
            text(label, style -> typography(style).fontSize(12).fontWeight(true).margin(0, 0, 6, 0));
            select(value, options, change, style -> typography(style).height(36).padding(4, 8, 4, 8)
                    .backgroundColor(theme.surface()).border(1, theme.border()).borderRadius(5))
                    .disabled(disabled).accessibleName(label);
        });
    }

    public Node slider(String label, double value, double minimum, double maximum, double step, boolean disabled,
            DoubleConsumer change) {
        range(value, minimum, maximum, step);
        double fraction = (value - minimum) / (maximum - minimum);
        SliderNode node = Ui.slider(fraction, theme.primary(),
                style -> style.width(240).height(32).alignItems(AlignItems.Center)
                        .opacity(disabled ? 0.4 : 1),
                () -> {
                    div(style -> style.width(240).height(8).borderRadius(4).backgroundColor(theme.border()), () -> {
                        div(style -> style.width(240 * fraction).height(8).borderRadius(4)
                                .backgroundColor(theme.primary()), () -> {
                                });
                    });
                });
        node.onPointer = position -> change
                .accept(quantize(minimum + position * (maximum - minimum), minimum, maximum, step));
        node.focusable = true;
        node.disabled = disabled;
        node.accessibleName = label + ": " + value;
        node.onKey = key -> {
            double next = switch (key) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_DOWN -> value - step;
                case KeyEvent.VK_RIGHT, KeyEvent.VK_UP -> value + step;
                case KeyEvent.VK_HOME -> minimum;
                case KeyEvent.VK_END -> maximum;
                default -> value;
            };
            change.accept(quantize(next, minimum, maximum, step));
        };
        return node;
    }

    private static void range(double value, double minimum, double maximum, double step) {
        if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || !Double.isFinite(step) || maximum <= minimum || step <= 0 || value < minimum || value > maximum)
            throw new IllegalArgumentException(
                    "Slider requires finite min < max, positive step, and an in-range value");
    }

    private static double quantize(double value, double minimum, double maximum, double step) {
        return Math.max(minimum, Math.min(maximum, minimum + Math.round((value - minimum) / step) * step));
    }

    public Node progress(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1)
            throw new IllegalArgumentException("Progress must be between 0 and 1");
        return div(
                style -> style.width(240).height(8).borderRadius(4).backgroundColor(theme.border()),
                () -> div(
                        style -> style.width(240 * fraction).height(8).backgroundColor(theme.primary()).borderRadius(4),
                        () -> {
                        }));
    }

    public Node badge(String label, Tone tone) {
        Color color = tone(tone);
        return div(style -> style.padding(4, 8, 4, 8).height(26).borderRadius(4).backgroundColor(tint(color)),
                () -> text(label, style -> typography(style).fontSize(12).color(color).fontWeight(true)));
    }

    public Node alert(String message, Tone tone) {
        Color color = tone(tone);
        return div(style -> style.padding(12).border(1, color).backgroundColor(tint(color)).borderRadius(6),
                () -> text(message, style -> typography(style).color(color)));
    }

    public Node card(String title, Runnable content) {
        return div(style -> style.padding(16).border(1, theme.border()).backgroundColor(theme.surface())
                .borderRadius(8).flexDirection(FlexDirection.Column).flexShrink(0), () -> {
                    text(title, style -> typography(style).fontWeight(true).margin(0, 0, 12, 0));
                    content.run();
                });
    }

    public Node tabs(List<String> tabs, String selected, Consumer<String> change) {
        if (tabs.isEmpty() || !tabs.contains(selected) || tabs.stream().distinct().count() != tabs.size())
            throw new IllegalArgumentException("Tabs require unique labels and a valid selected label");
        return div(style -> style.height(38).flexShrink(0), () -> {
            for (String tab : tabs)
                component("tab", tab, () -> button(tab,
                        tab.equals(selected) ? Variant.PRIMARY : Variant.SECONDARY, false, () -> change.accept(tab)));
        });
    }

    public Node stack(double spacing, Runnable children) {
        Node node = div(style -> style.flexDirection(FlexDirection.Column), children);
        for (int index = 0; index < node.children.size() - 1; index++)
            node.children.get(index).inline.margin(0, 0, spacing, 0);
        return node;
    }

    public Node row(double spacing, Runnable children) {
        Node node = div(style -> style.alignItems(AlignItems.Center).flexWrap(FlexWrap.Wrap), children);
        for (int index = 0; index < node.children.size(); index++)
            node.children.get(index).inline.margin(0, spacing, spacing, 0);
        return node;
    }

    private Color tone(Tone tone) {
        return switch (Objects.requireNonNull(tone)) {
            case INFO -> theme.primary();
            case SUCCESS -> theme.success();
            case DANGER -> theme.danger();
        };
    }

    private static Color tint(Color color) {
        return new Color((color.getRed() + 255 * 9) / 10, (color.getGreen() + 255 * 9) / 10,
                (color.getBlue() + 255 * 9) / 10);
    }
}