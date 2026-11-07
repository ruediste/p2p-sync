package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Port of alignment-enums.test.ts. The TypeScript suite runs every case under
 * three update modes ("constructor", "set", "assignment"); those become
 * parameterized cases here.
 */
class AlignmentEnumsPortTest {

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Style updated(String update, Map<String, Object> properties) {
        Style style;
        if (update.equals("constructor")) {
            style = new Style(properties);
        } else {
            style = new Style();
            if (update.equals("set")) {
                style.set(properties);
            } else {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    applyByAssignment(style, entry.getKey(), entry.getValue());
                }
            }
        }
        return style;
    }

    private static void applyByAssignment(Style style, String key, Object value) {
        switch (key) {
            case "alignSelf" -> style.alignSelf = (AlignSelf) value;
            case "justifySelf" -> style.justifySelf = (AlignSelf) value;
            case "alignContent" -> style.alignContent = (AlignContent) value;
            case "justifyContent" -> style.justifyContent = (JustifyContent) value;
            default -> throw new IllegalArgumentException("Unsupported assignment key: " + key);
        }
    }

    static Stream<Arguments> selfAlignmentCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String update : List.of("constructor", "set", "assignment")) {
            for (AlignSelf value : List.of(
                    AlignSelf.Start,
                    AlignSelf.End,
                    AlignSelf.FlexStart,
                    AlignSelf.FlexEnd,
                    AlignSelf.Center,
                    AlignSelf.Baseline,
                    AlignSelf.Stretch)) {
                cases.add(Arguments.of(update, value));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "self alignment after {0}: {1}")
    @MethodSource("selfAlignmentCases")
    void returnsThePublicSelfAlignmentValue(String update, AlignSelf value) {
        Style style = updated(update, props("alignSelf", value, "justifySelf", value));

        assertEquals(value, style.alignSelf);
        assertEquals(value, style.justifySelf);
        assertEquals(value, style.get("alignSelf"));
        assertEquals(value, style.get("justifySelf"));
        assertArrayEquals(new Object[] { value, value },
                (Object[]) style.get("alignSelf", "justifySelf"));
    }

    static Stream<Arguments> distributionCases() {
        List<Arguments> cases = new ArrayList<>();
        List<Arguments> values = List.of(
                Arguments.of("SpaceAround", AlignContent.SpaceAround, JustifyContent.SpaceAround),
                Arguments.of("SpaceEvenly", AlignContent.SpaceEvenly, JustifyContent.SpaceEvenly));
        for (String update : List.of("constructor", "set", "assignment")) {
            for (Arguments value : values) {
                cases.add(Arguments.of(update, value.get()[0], value.get()[1], value.get()[2]));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} without swapping distribution modes after {1}")
    @MethodSource("distributionCases")
    void returnsPublicDistributionValuesWithoutSwappingDistributionModes(String name, String update,
            AlignContent alignContent, JustifyContent justifyContent) {
        Style style = updated(update,
                props("alignContent", alignContent, "justifyContent", justifyContent));

        assertEquals(alignContent, style.alignContent);
        assertEquals(justifyContent, style.justifyContent);
        assertEquals(alignContent, style.get("alignContent"));
        assertEquals(justifyContent, style.get("justifyContent"));
        assertArrayEquals(new Object[] { alignContent, justifyContent },
                (Object[]) style.get("alignContent", "justifyContent"));
    }

    @Test
    void roundTripsPublicDisplayAndAlignmentValues() {
        assertEquals(List.of(0, 1, 2, 3, 4), List.of(
                Display.Block.value,
                Display.Flex.value,
                Display.Grid.value,
                Display.None.value,
                Display.FlowRoot.value));

        Style style = new Style(props(
                "display", Display.FlowRoot,
                "alignItems", AlignItems.SafeSelfStart,
                "alignSelf", AlignSelf.SafeCenter,
                "alignContent", AlignContent.SafeEnd,
                "justifyContent", JustifyContent.SafeFlexEnd,
                "justifyItems", AlignItems.SelfEnd,
                "justifySelf", AlignSelf.SafeSelfEnd));

        assertArrayEquals(new Object[] {
                Display.FlowRoot,
                AlignItems.SafeSelfStart,
                AlignSelf.SafeCenter,
                AlignContent.SafeEnd,
                JustifyContent.SafeFlexEnd,
                AlignItems.SelfEnd,
                AlignSelf.SafeSelfEnd
        }, (Object[]) style.get(
                "display",
                "alignItems",
                "alignSelf",
                "alignContent",
                "justifyContent",
                "justifyItems",
                "justifySelf"));
    }
}
