package com.github.ruediste.jreact;

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

        class Child extends Component {
            int dependency;

            Child(int dependency) {
                this.dependency = dependency;
            }

            @Override
            public void render() {
                Hooks.useLayoutEffect(() -> {
                    log.add("layout" + dependency);
                    return () -> log.add("layout-cleanup");
                }, dependency);
                Hooks.useEffect(() -> {
                    log.add("passive" + dependency);
                    return () -> log.add("passive-cleanup");
                }, dependency);
            }
        }

        JReactRoot root = new JReactRoot(() -> {
            show.set(Hooks.useState(true));
            dependency.set(Hooks.useState(0));
            if (show.get().get())
                new Child(dependency.get().get());
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
