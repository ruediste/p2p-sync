package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Port of alignment-enums.test.ts. The TypeScript suite runs every case under
 * several update modes; only direct field assignment remains here.
 */
class AlignmentEnumsPortTest {

    static Stream<Arguments> selfAlignmentCases() {
        List<Arguments> cases = new ArrayList<>();
        for (AlignSelf value : List.of(
                AlignSelf.Start,
                AlignSelf.End,
                AlignSelf.FlexStart,
                AlignSelf.FlexEnd,
                AlignSelf.Center,
                AlignSelf.Baseline,
                AlignSelf.Stretch)) {
            cases.add(Arguments.of(value));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "self alignment: {0}")
    @MethodSource("selfAlignmentCases")
    void returnsThePublicSelfAlignmentValue(AlignSelf value) {
        Style style = new Style();
        style.alignSelf = value;
        style.justifySelf = value;

        assertEquals(value, style.alignSelf);
        assertEquals(value, style.justifySelf);
    }

    static Stream<Arguments> distributionCases() {
        List<Arguments> cases = new ArrayList<>();
        List<Arguments> values = List.of(
                Arguments.of("SpaceAround", AlignContent.SpaceAround, JustifyContent.SpaceAround),
                Arguments.of("SpaceEvenly", AlignContent.SpaceEvenly, JustifyContent.SpaceEvenly));
        for (Arguments value : values) {
            cases.add(Arguments.of(value.get()[0], value.get()[1], value.get()[2]));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} without swapping distribution modes")
    @MethodSource("distributionCases")
    void returnsPublicDistributionValuesWithoutSwappingDistributionModes(String name,
            AlignContent alignContent, JustifyContent justifyContent) {
        Style style = new Style();
        style.alignContent = alignContent;
        style.justifyContent = justifyContent;

        assertEquals(alignContent, style.alignContent);
        assertEquals(justifyContent, style.justifyContent);
    }

    @Test
    void roundTripsPublicDisplayAndAlignmentValues() {
        assertEquals(List.of(0, 1, 2, 3, 4), List.of(
                Display.Block.value,
                Display.Flex.value,
                Display.Grid.value,
                Display.None.value,
                Display.FlowRoot.value));

        Style style = new Style();
        style.display = Display.FlowRoot;
        style.alignItems = AlignItems.SafeSelfStart;
        style.alignSelf = AlignSelf.SafeCenter;
        style.alignContent = AlignContent.SafeEnd;
        style.justifyContent = JustifyContent.SafeFlexEnd;
        style.justifyItems = AlignItems.SelfEnd;
        style.justifySelf = AlignSelf.SafeSelfEnd;

        assertEquals(Display.FlowRoot, style.display);
        assertEquals(AlignItems.SafeSelfStart, style.alignItems);
        assertEquals(AlignSelf.SafeCenter, style.alignSelf);
        assertEquals(AlignContent.SafeEnd, style.alignContent);
        assertEquals(JustifyContent.SafeFlexEnd, style.justifyContent);
        assertEquals(AlignItems.SelfEnd, style.justifyItems);
        assertEquals(AlignSelf.SafeSelfEnd, style.justifySelf);
    }
}
