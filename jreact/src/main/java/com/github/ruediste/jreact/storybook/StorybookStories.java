package com.github.ruediste.jreact.storybook;

import static com.github.ruediste.jreact.Ui.component;
import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.icon;
import static com.github.ruediste.jreact.Ui.text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;

import com.github.ruediste.jreact.Hooks;
import com.github.ruediste.jreact.components.Components;
import com.github.ruediste.jreact.components.Theme;
import com.github.ruediste.jreact.layout.AlignItems;
import com.github.ruediste.jreact.layout.FlexDirection;
import com.github.ruediste.jreact.layout.FlexWrap;
import com.github.ruediste.jreact.layout.JustifyContent;
import com.github.ruediste.jreact.layout.TextAlign;

public final class StorybookStories {
    private StorybookStories() {
    }

    public static void Overview(Theme theme) {
        Components ui = new Components(theme);
        ui.stack(20, () -> {
            ui.row(12, () -> {
                ui.card("Primitives", () -> {
                    ui.heading("Div / Span / Text");
                    ui.muted("Java + Java2D");
                });
                ui.card("Components", () -> {
                    ui.heading("11 controls");
                    ui.badge("Swing native", Components.Tone.SUCCESS);
                });
            });
            ui.card("Workspace", () -> {
                ui.row(12, () -> {
                    icon(UIManager.getIcon("FileView.directoryIcon"), style -> style.margin(0, 8, 0, 0));
                    ui.label("Design system");
                    ui.badge("Ready", Components.Tone.SUCCESS);
                });
                ui.label("Components, interactions, and layout");
            });
            ui.alert("All systems operational", Components.Tone.SUCCESS);
        });
    }

    public static void Buttons(Theme theme) {
        Components ui = new Components(theme);
        var count = Hooks.useState(0);
        var disabled = Hooks.useState(false);
        ui.stack(22, () -> {
            ui.card("Variants", () -> ui.row(10, () -> {
                ui.button("Primary", Components.Variant.PRIMARY, disabled.get(),
                        () -> count.update(value -> value + 1));
                ui.button("Secondary", Components.Variant.SECONDARY, disabled.get(),
                        () -> count.update(value -> value + 1));
                ui.button("Danger", Components.Variant.DANGER, disabled.get(), () -> count.update(value -> value + 1));
            }));
            ui.card("Counter", () -> {
                ui.row(10, () -> {
                    ui.button("-", Components.Variant.SECONDARY, disabled.get(),
                            () -> count.update(value -> value - 1));
                    text(count.get(), style -> ui.typography(style).fontSize(24).width(72).height(36)
                            .textAlign(TextAlign.LegacyCenter));
                    ui.button("+", Components.Variant.SECONDARY, disabled.get(),
                            () -> count.update(value -> value + 1));
                    ui.button("Reset", Components.Variant.SECONDARY, disabled.get(), () -> count.set(0));
                });
                ui.checkbox("Disable buttons", disabled.get(), false, disabled::set);
            });
        });
    }

    public static void Inputs(Theme theme) {
        Components ui = new Components(theme);
        var name = Hooks.useState("Ada Lovelace");
        var role = Hooks.useState("Editor");
        var enabled = Hooks.useState(true);
        var saved = Hooks.useState(false);
        ui.stack(18, () -> {
            ui.card("Profile", () -> ui.stack(10, () -> {
                ui.textField("Display name", name.get(), !enabled.get(), value -> {
                    name.set(value);
                    saved.set(false);
                });
                ui.selectField("Role", role.get(), List.of("Viewer", "Editor", "Administrator"), !enabled.get(),
                        role::set);
                ui.checkbox("Editing enabled", enabled.get(), false, enabled::set);
                ui.button("Save profile", Components.Variant.PRIMARY, !enabled.get() || name.get().isBlank(),
                        () -> saved.set(true));
            }));
            ui.card("Preview", () -> {
                ui.heading(name.get().isBlank() ? "Untitled" : name.get());
                ui.badge(role.get(), Components.Tone.INFO);
            });
            if (saved.get())
                ui.alert("Profile saved", Components.Tone.SUCCESS);
        });
    }

    public static void Feedback(Theme theme) {
        Components ui = new Components(theme);
        var progress = Hooks.useState(65.0);
        ui.stack(20, () -> {
            ui.row(8, () -> {
                ui.badge("Connected", Components.Tone.SUCCESS);
                ui.badge("Pending", Components.Tone.INFO);
                ui.badge("Offline", Components.Tone.DANGER);
            });
            ui.card("Transfer", () -> ui.stack(12, () -> {
                ui.label("design-system.zip");
                ui.progress(progress.get() / 100);
                ui.slider("Transfer progress", progress.get(), 0, 100, 1, false, progress::set);
                ui.muted(String.format("%.0f%% complete", progress.get()));
            }));
            ui.alert("Changes synchronized", Components.Tone.SUCCESS);
            ui.alert("Waiting for a peer", Components.Tone.INFO);
            ui.alert("Connection interrupted", Components.Tone.DANGER);
        });
    }

    public static void Layout(Theme theme) {
        Components ui = new Components(theme);
        var direction = Hooks.useState("Row");
        var justification = Hooks.useState("Start");
        ui.stack(18, () -> {
            ui.tabs(List.of("Row", "Column"), direction.get(), direction::set);
            ui.selectField("Justification", justification.get(),
                    java.util.Arrays.stream(JustifyContent.values()).map(Enum::name).toList(),
                    false, justification::set);
            div(style -> style.height(260).flexShrink(0).padding(16).backgroundColor(theme.surface())
                    .border(1, theme.border())
                    .flexDirection(direction.get().equals("Row") ? FlexDirection.Row : FlexDirection.Column)
                    .flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.valueOf(justification.get())), () -> {
                        List<Color> colors = List.of(theme.primary(), new Color(194, 142, 26), theme.danger());
                        for (int index = 0; index < 3; index++) {
                            int number = index;
                            div(style -> style.width(72).height(56).margin(6).backgroundColor(colors.get(number))
                                    .borderRadius(6)
                                    .alignItems(AlignItems.Center).justifyContent(JustifyContent.Center),
                                    () -> text(number + 1, style -> ui.typography(style).color(Color.WHITE)
                                            .fontWeight(true).fontSize(18)));
                        }
                    });
        });
    }

    public record Task(int id, String title, boolean done) {
    }

    public static void Tasks(Theme theme) {
        Components ui = new Components(theme);
        var tasks = Hooks.useState(
                List.of(new Task(1, "Review layout engine", true), new Task(2, "Ship component library", false),
                        new Task(3, "Write release notes", false)));
        var draft = Hooks.useState("");
        var filter = Hooks.useState("All");
        var nextId = Hooks.useRef(4);
        ui.stack(18, () -> {
            ui.tabs(List.of("All", "Active", "Done"), filter.get(), filter::set);
            ui.card("Sprint / 01", () -> ui.stack(8, () -> {
                for (Task task : tasks.get()) {
                    if (filter.get().equals("Active") && task.done() || filter.get().equals("Done") && !task.done())
                        continue;
                    component("task", task.id(), () -> ui.checkbox(task.title(), task.done(), false,
                            checked -> tasks.update(previous -> previous.stream().map(item -> item.id() == task.id()
                                    ? new Task(item.id(), item.title(), checked)
                                    : item).toList())));
                }
                if (tasks.get().stream()
                        .noneMatch(task -> filter.get().equals("All") || task.done() == filter.get().equals("Done")))
                    ui.muted("No tasks");
            }));
            ui.card("New task", () -> ui.stack(10, () -> {
                ui.textField("Title", draft.get(), false, draft::set);
                ui.button("Add task", Components.Variant.PRIMARY, draft.get().isBlank(), () -> {
                    var updated = new ArrayList<>(tasks.get());
                    updated.add(new Task(nextId.current++, draft.get().strip(), false));
                    tasks.set(List.copyOf(updated));
                    draft.set("");
                    filter.set("All");
                });
            }));
            ui.badge(tasks.get().stream().filter(Task::done).count() + " / " + tasks.get().size() + " complete",
                    Components.Tone.SUCCESS);
        });
    }
}