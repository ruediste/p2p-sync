package com.github.ruediste.jreact;

import static com.github.ruediste.jreact.Ui.component;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class EffectTest {

    @Test
    void layoutAndPassiveEffectsRunInOrderWithCleanup() {
        List<String> log = new ArrayList<>();
        AtomicReference<State<Boolean>> show = new AtomicReference<>();
        AtomicReference<State<Integer>> dependency = new AtomicReference<>();
        int[] invalidations = {0};

        JReactRoot root = new JReactRoot(() -> {
            show.set(Hooks.useState(true));
            dependency.set(Hooks.useState(0));
            if (show.get().get())
                component("child", dependency.get().get(), () -> {
                    Hooks.useLayoutEffect(() -> {
                        log.add("layout" + dependency.get().get());
                        return () -> log.add("layout-cleanup");
                    }, dependency.get().get());
                    Hooks.useEffect(() -> {
                        log.add("passive" + dependency.get().get());
                        return () -> log.add("passive-cleanup");
                    }, dependency.get().get());
                });
        });
        root.setInvalidationCallback(() -> invalidations[0]++);

        root.refresh();
        assertEquals(List.of("layout0"), log);
        root.paintCompleted();
        assertEquals(List.of("layout0", "passive0"), log);

        dependency.get().set(1);
        root.refresh();
        assertEquals(List.of("layout0", "passive0", "layout-cleanup", "layout1"), log);
        root.paintCompleted();
        assertEquals(List.of("layout0", "passive0", "layout-cleanup", "layout1", "passive-cleanup", "passive1"), log);

        show.get().set(false);
        root.refresh();
        assertTrue(log.contains("layout-cleanup"));
        assertTrue(log.contains("passive-cleanup"));
        assertTrue(invalidations[0] >= 0);
    }
}
