package com.github.ruediste.jreact.storybook;

import static com.github.ruediste.jreact.Ui.component;
import static com.github.ruediste.jreact.Ui.div;
import static com.github.ruediste.jreact.Ui.select;
import static com.github.ruediste.jreact.Ui.text;
import static com.github.ruediste.jreact.Ui.viewportHeight;
import static com.github.ruediste.jreact.Ui.viewportWidth;

import com.antepod.taffy.style.AlignContent;
import com.antepod.taffy.style.AlignItems;
import com.antepod.taffy.style.FlexDirection;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.github.ruediste.jreact.Hooks;
import com.github.ruediste.jreact.JReactAgent;
import com.github.ruediste.jreact.JReactPanel;
import com.github.ruediste.jreact.Style;
import com.github.ruediste.jreact.components.Components;
import com.github.ruediste.jreact.components.Theme;

public final class Storybook {
    public static final List<String> STORIES = List.of("Overview", "Buttons", "Inputs", "Feedback", "Layout",
            "Task Board");

    private Storybook() {
    }

    public static JReactPanel panel() {
        return panel("Overview");
    }

    public static JReactPanel panel(String initialStory) {
        if (!STORIES.contains(initialStory))
            throw new IllegalArgumentException("Unknown story: " + initialStory);
        return new JReactPanel(() -> app(initialStory));
    }

    private static void app(String initialStory) {
        var selected = Hooks.useState(initialStory);
        var contrast = Hooks.useState(false);
        Theme theme = contrast.get() ? Theme.highContrast() : Theme.light();
        Components ui = new Components(theme);
        boolean narrow = viewportWidth() < 720;
        div(style -> style.width(viewportWidth()).height(viewportHeight()).flexDirection(FlexDirection.COLUMN)
                .backgroundColor(theme.canvas()), () -> {
                    div(style -> style.height(68).flexShrink(0).padding(16, 20, 16, 20).backgroundColor(theme.surface())
                            .alignItems(AlignItems.CENTER).justifyContent(AlignContent.SPACE_BETWEEN), () -> {
                                ui.heading("JReact");
                                ui.checkbox("High contrast", contrast.get(), false, contrast::set);
                            });
                    if (narrow) {
                        select(selected.get(), STORIES, selected::set, style -> ui.typography(style).height(38)
                                .margin(12, 20, 0, 20).backgroundColor(theme.surface())).accessibleName("Story");
                    }
                    div(style -> style.flexGrow(1).flexDirection(FlexDirection.ROW), () -> {
                        if (!narrow)
                            div(style -> style.width(190).flexShrink(0).padding(24, 12, 24, 12)
                                    .backgroundColor(theme.surface()).flexDirection(FlexDirection.COLUMN), () -> {
                                        ui.muted("COMPONENTS");
                                        for (String story : STORIES)
                                            component("navigation", story,
                                                    () -> ui.button(story,
                                                            story.equals(selected.get()) ? Components.Variant.PRIMARY
                                                                    : Components.Variant.SECONDARY,
                                                            false, () -> selected.set(story),
                                                            style -> style.width(166).margin(10, 0, 0, 0)));
                                    });
                    div(style -> style.flexGrow(1).padding(narrow ? 20 : 28).flexDirection(FlexDirection.COLUMN),
                            () -> {
                                text(selected.get(), style -> ui.typography(style).fontSize(26).fontWeight(true)
                                        .margin(0, 0, 22, 0));
                                switch (selected.get()) {
                                    case "Overview" -> component("story", "Overview",
                                            () -> StorybookStories.Overview(theme));
                                    case "Buttons" -> component("story", "Buttons",
                                            () -> StorybookStories.Buttons(theme));
                                    case "Inputs" -> component("story", "Inputs",
                                            () -> StorybookStories.Inputs(theme));
                                    case "Feedback" -> component("story", "Feedback",
                                            () -> StorybookStories.Feedback(theme));
                                    case "Layout" -> component("story", "Layout",
                                            () -> StorybookStories.Layout(theme));
                                    case "Task Board" -> component("story", "Task Board",
                                            () -> StorybookStories.Tasks(theme));
                                    default -> throw new IllegalStateException("Unknown story");
                                }
                            });
                    });
                });
    }

    public static void main(String[] args) {
        JReactAgent.install();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("JReact | Component Storybook");
            JReactPanel panel = panel();
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    panel.close();
                }
            });
            frame.setContentPane(panel);
            frame.setMinimumSize(new Dimension(400, 760));
            frame.setSize(1100, 880);
            frame.setLocationByPlatform(true);
            var icon = UIManager.getIcon("FileView.computerIcon");
            if (icon instanceof javax.swing.ImageIcon image)
                frame.setIconImage(image.getImage());
            frame.setVisible(true);
        });
    }
}