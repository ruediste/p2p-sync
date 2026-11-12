package com.github.ruediste.jreact.storybook;

import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.input;
import static com.github.ruediste.jreact.Ui.select;
import static com.github.ruediste.jreact.Ui.span;
import static com.github.ruediste.jreact.Ui.text;

import java.awt.Color;
import java.util.List;

import com.antepod.taffy.style.Display;
import com.antepod.taffy.style.FlexDirection;
import com.github.ruediste.jreact.Hooks;
import com.github.ruediste.jreact.State;
import com.github.ruediste.jreact.Styles;
import com.github.ruediste.jreact.events.ClickEvent;
import com.github.ruediste.jreact.nodes.DivNode;
import com.github.ruediste.jreact.nodes.TextNode;

public final class StorybookStories {
    private StorybookStories() {
    }

    public static void story(String name) {
        switch (name) {
            case "Overview" -> Overview();
            case "Buttons" -> Buttons();
            case "Inputs" -> Inputs();
            case "Feedback" -> Feedback();
            case "Layout" -> FlexLayout();
            case "Dialog" -> CustomDialog();
            case "Task Board" -> TaskBoard();
            default -> throw new IllegalArgumentException("Unknown story: " + name);
        }
    }

    private static void Overview() {
        div(s -> s.padding(20).borderRadius(12).backgroundColor(Color.WHITE)
                .flexDirection(FlexDirection.COLUMN), () -> {
                    text("Primitives compose into a synchronous Java UI.");
                    div(s -> s.height(12));
                    span(() -> {
                        text("Div, ");
                        span(s -> s.fontWeight(true), () -> text("Span"));
                        text(", and Text are the base building blocks.");
                    });
                });
    }

    private static void Buttons() {
        State<Integer> count = Hooks.useState(0);
        Styles.type(DivNode.class).className("button-primary").style().backgroundColor(new Color(59, 130, 246));
        Styles.type(DivNode.class).className("button-primary").hover().style().backgroundColor(new Color(37, 99, 235));
        Styles.type(DivNode.class).className("button-secondary").style().backgroundColor(new Color(229, 231, 235));
        Styles.type(DivNode.class).className("button-secondary").hover().style()
                .backgroundColor(new Color(209, 213, 219));
        div(s -> s.flexDirection(FlexDirection.ROW), () -> {
            div(s -> s
                    .className("button-primary")
                    .padding(10)
                    .borderRadius(8),
                    node -> node.on(ClickEvent.class, event -> count.set(count.get() + 1)),
                    () -> text("Clicked " + count.get() + " times", s -> s.color(Color.WHITE)));
            div(s -> s.width(12));
            div(s -> s.className("button-secondary").padding(10).borderRadius(8),
                    () -> text("Secondary"));
        });
    }

    private static void Inputs() {
        State<String> name = Hooks.useState("JReact");
        State<String> variant = Hooks.useState("Primary");
        text("Controlled input", s -> s.fontWeight(true));
        input(name.get(), name::set, s -> s.width(240).padding(8));
        text("Hello, " + name.get());
        div(s -> s.height(16));
        text("Select", s -> s.fontWeight(true));
        select(variant.get(), List.of("Primary", "Secondary", "Danger"), variant::set, s -> s.width(180));
        text("Variant: " + variant.get());
    }

    private static void Feedback() {
        div(s -> s.flexDirection(FlexDirection.ROW), () -> {
            badge("Info", new Color(219, 234, 254), new Color(30, 64, 175));
            badge("Success", new Color(220, 252, 231), new Color(22, 101, 52));
            badge("Warning", new Color(254, 249, 195), new Color(133, 77, 14));
        });
    }

    private static void badge(String label, Color background, Color foreground) {
        div(s -> s.padding(10).margin(0, 8, 0, 0).borderRadius(999).backgroundColor(background),
                () -> text(label, s -> s.color(foreground).fontWeight(true)));
    }

    private static void FlexLayout() {
        // --8<-- [start:flex-layout]
        div(s -> s.display(Display.FLEX)
                .flexDirection(FlexDirection.ROW)
                .padding(16)
                .backgroundColor(new Color(255, 255, 255)), () -> {
                    div(s -> s.flexGrow(1).padding(12).backgroundColor(new Color(219, 234, 254)),
                            () -> text("Flexible"));
                    div(s -> s.width(160).padding(12).backgroundColor(new Color(220, 252, 231)),
                            () -> text("Fixed width"));
                });
        // --8<-- [end:flex-layout]
    }

    private static void CustomDialog() {
        // --8<-- [start:custom-dialog]
        Styles.type(DivNode.class).className("dialog").style()
                .padding(24).borderRadius(12).backgroundColor(Color.WHITE);
        Styles.type(TextNode.class).className("dialog-title").style()
                .fontSize(20).fontWeight(true);
        div(s -> s.className("dialog"), () -> text("Dialog title", s -> s.className("dialog-title")));
        // --8<-- [end:custom-dialog]
    }

    private static void TaskBoard() {
        State<Integer> completed = Hooks.useState(0);
        String[] tasks = { "Design data model", "Wire refresh pipeline", "Paint the tree", "Write tests" };
        for (String task : tasks) {
            div(s -> s.padding(12).margin(0, 0, 8, 0).borderRadius(8).backgroundColor(Color.WHITE),
                    node -> node.on(ClickEvent.class, event -> completed.set(completed.get() + 1)),
                    () -> text(task));
        }
        text(completed.get() + " interactions", s -> s.color(new Color(75, 85, 99)));
    }
}
