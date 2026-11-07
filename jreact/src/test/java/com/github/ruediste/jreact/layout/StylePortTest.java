package com.github.ruediste.jreact.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Port of style.test.ts. Each TypeScript {@code it} becomes one Java test
 * method; assertions keep the upstream expected values, translated to the
 * typed value objects used by the native engine.
 */
class StylePortTest {

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Style style(Object... keyValues) {
        return new Style(props(keyValues));
    }

    private static Object[] values(Object value) {
        return (Object[]) value;
    }

    private static float number(Object value) {
        return ((Number) value).floatValue();
    }

    // Constructor

    @Test
    void createsStyleWithDefaultsWhenNoArguments() {
        Style style = new Style();
        assertEquals(Display.Flex, style.display);
        assertEquals(Position.Static, style.position);
    }

    @Test
    void createsStyleWithInitialProperties() {
        Style style = new Style(props(
                "display", Display.Grid,
                "flexDirection", FlexDirection.Column,
                "width", 200,
                "marginLeft", 10));
        assertEquals(Display.Grid, style.display);
        assertEquals(FlexDirection.Column, style.flexDirection);
        assertEquals(Dimension.length(200), style.get("width"));
        assertEquals(LengthPercentageAuto.length(10), style.get("marginLeft"));
    }

    @Test
    void createsStyleWithDotNotationProperties() {
        Style style = new Style(props(
                "width", 300,
                "height", "50%",
                "marginLeft", 20,
                "marginRight", "auto"));
        assertEquals(Dimension.length(300), style.get("width"));
        assertEquals(Dimension.percent(50), style.get("height"));
        assertEquals(LengthPercentageAuto.length(20), style.get("marginLeft"));
        assertEquals(LengthPercentageAuto.auto(), style.get("marginRight"));
    }

    // Layout modes

    @Test
    void displayDefaultsToFlexSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Display.Flex, style.display);

        style.display = Display.None;
        assertEquals(Display.None, style.display);

        style.display = Display.Flex;
        assertEquals(Display.Flex, style.display);

        style.display = Display.Grid;
        assertEquals(Display.Grid, style.display);

        style.display = Display.Block;
        assertEquals(Display.Block, style.display);
    }

    @Test
    void positionDefaultsToStaticSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Position.Static, style.position);

        for (Position position : List.of(
                Position.Relative,
                Position.Absolute,
                Position.Fixed,
                Position.Sticky,
                Position.Static)) {
            style.position = position;
            assertEquals(position, style.position);
            assertEquals(position, style.get("position"));
        }
    }

    @Test
    void boxSizingDefaultsToBorderBoxSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(BoxSizing.BorderBox, style.boxSizing);

        style.boxSizing = BoxSizing.ContentBox;
        assertEquals(BoxSizing.ContentBox, style.boxSizing);

        style.boxSizing = BoxSizing.BorderBox;
        assertEquals(BoxSizing.BorderBox, style.boxSizing);
    }

    @Test
    void overflowDefaultsToVisibleSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Overflow.Visible, style.overflow.x);
        assertEquals(Overflow.Visible, style.overflow.y);

        style.overflow = Point.of(Overflow.Hidden, Overflow.Scroll);
        assertEquals(Overflow.Hidden, style.overflow.x);
        assertEquals(Overflow.Scroll, style.overflow.y);

        style.overflow = Point.of(Overflow.Clip, Overflow.Visible);
        assertEquals(Overflow.Clip, style.overflow.x);
        assertEquals(Overflow.Visible, style.overflow.y);
    }

    // Flexbox

    @Test
    void flexDirectionDefaultsToRowSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(FlexDirection.Row, style.flexDirection);

        style.flexDirection = FlexDirection.Column;
        assertEquals(FlexDirection.Column, style.flexDirection);

        style.flexDirection = FlexDirection.RowReverse;
        assertEquals(FlexDirection.RowReverse, style.flexDirection);

        style.flexDirection = FlexDirection.ColumnReverse;
        assertEquals(FlexDirection.ColumnReverse, style.flexDirection);
    }

    @Test
    void flexWrapDefaultsToNoWrapSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(FlexWrap.NoWrap, style.flexWrap);

        style.flexWrap = FlexWrap.Wrap;
        assertEquals(FlexWrap.Wrap, style.flexWrap);

        style.flexWrap = FlexWrap.WrapReverse;
        assertEquals(FlexWrap.WrapReverse, style.flexWrap);
    }

    @Test
    void flexGrowDefaultsToZeroSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(0f, style.flexGrow, 0f);

        style.flexGrow = 1;
        assertEquals(1f, style.flexGrow, 0f);

        style.flexGrow = 2.5f;
        assertEquals(2.5f, style.flexGrow, 0f);
    }

    @Test
    void flexShrinkDefaultsToOneSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(1f, style.flexShrink, 0f);

        style.flexShrink = 0;
        assertEquals(0f, style.flexShrink, 0f);

        style.flexShrink = 3.5f;
        assertEquals(3.5f, style.flexShrink, 0f);
    }

    @Test
    void flexBasisDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.flexBasis);

        style.setFlexBasis(100);
        assertEquals(LengthPercentageAuto.length(100), style.flexBasis);

        style.setFlexBasis("50%");
        assertEquals(LengthPercentageAuto.percent(50), style.flexBasis);

        style.setFlexBasis("auto");
        assertEquals(LengthPercentageAuto.auto(), style.flexBasis);
    }

    // Alignment properties

    @Test
    void alignItemsSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.alignItems);

        style.alignItems = AlignItems.Start;
        assertEquals(AlignItems.Start, style.alignItems);

        style.alignItems = null;
        assertNull(style.alignItems);

        style.alignItems = AlignItems.Center;
        assertEquals(AlignItems.Center, style.alignItems);
    }

    @Test
    void alignSelfSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(AlignSelf.Auto, style.getAlignSelf());

        style.setAlignSelf(AlignSelf.FlexStart);
        assertEquals(AlignSelf.FlexStart, style.getAlignSelf());

        style.setAlignSelf(AlignSelf.Auto);
        assertEquals(AlignSelf.Auto, style.getAlignSelf());

        style.setAlignSelf(null);
        assertEquals(AlignSelf.Auto, style.getAlignSelf());
    }

    @Test
    void alignContentSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.alignContent);

        style.alignContent = AlignContent.SpaceBetween;
        assertEquals(AlignContent.SpaceBetween, style.alignContent);

        style.alignContent = null;
        assertNull(style.alignContent);
    }

    @Test
    void justifyContentSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.justifyContent);

        style.justifyContent = JustifyContent.SpaceAround;
        assertEquals(JustifyContent.SpaceAround, style.justifyContent);

        style.justifyContent = null;
        assertNull(style.justifyContent);
    }

    @Test
    void justifyItemsSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.justifyItems);

        style.justifyItems = AlignItems.Center;
        assertEquals(AlignItems.Center, style.justifyItems);

        style.justifyItems = AlignItems.Start;
        assertEquals(AlignItems.Start, style.justifyItems);

        style.justifyItems = null;
        assertNull(style.justifyItems);
    }

    @Test
    void justifySelfSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(AlignSelf.Auto, style.getJustifySelf());

        style.setJustifySelf(AlignSelf.End);
        assertEquals(AlignSelf.End, style.getJustifySelf());

        style.setJustifySelf(AlignSelf.Auto);
        assertEquals(AlignSelf.Auto, style.getJustifySelf());

        style.setJustifySelf(null);
        assertEquals(AlignSelf.Auto, style.getJustifySelf());
    }

    // Sizing and spacing

    @Test
    void sizeDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.size.width);
        assertEquals(Dimension.auto(), style.size.height);

        style.size = Size.of(Dimension.length(100), Dimension.percent(50));
        assertEquals(Dimension.length(100), style.size.width);
        assertEquals(Dimension.percent(50), style.size.height);

        style.size = Size.of(Dimension.auto(), Dimension.length(200));
        assertEquals(Dimension.auto(), style.size.width);
        assertEquals(Dimension.length(200), style.size.height);
    }

    @Test
    void minSizeDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.minSize.width);
        assertEquals(Dimension.auto(), style.minSize.height);

        style.minSize = Size.of(Dimension.length(50), Dimension.length(50));
        assertEquals(Dimension.length(50), style.minSize.width);
        assertEquals(Dimension.length(50), style.minSize.height);

        style.minSize = Size.of(Dimension.percent(10), Dimension.auto());
        assertEquals(Dimension.percent(10), style.minSize.width);
        assertEquals(Dimension.auto(), style.minSize.height);
    }

    @Test
    void maxSizeDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.maxSize.width);
        assertEquals(Dimension.auto(), style.maxSize.height);

        style.maxSize = Size.of(Dimension.length(500), Dimension.percent(100));
        assertEquals(Dimension.length(500), style.maxSize.width);
        assertEquals(Dimension.percent(100), style.maxSize.height);
    }

    @Test
    void aspectRatioSetsAndGetsCorrectly() {
        Style style = new Style();
        assertNull(style.aspectRatio);

        style.aspectRatio = 16f / 9f;
        assertEquals(16f / 9f, style.aspectRatio, 1e-5f);

        style.aspectRatio = null;
        assertNull(style.aspectRatio);
    }

    @Test
    void gapSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.gap.width);
        assertEquals(LengthPercentage.zero(), style.gap.height);

        style.gap = Size.of(LengthPercentage.length(10), LengthPercentage.percent(5));
        assertEquals(LengthPercentage.length(10), style.gap.width);
        assertEquals(LengthPercentage.percent(5), style.gap.height);

        style.gap = Size.of(LengthPercentage.zero(), LengthPercentage.zero());
        assertEquals(LengthPercentage.zero(), style.gap.width);
        assertEquals(LengthPercentage.zero(), style.gap.height);
    }

    @Test
    void marginSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.length(0), style.margin.left);
        assertEquals(LengthPercentageAuto.length(0), style.margin.right);
        assertEquals(LengthPercentageAuto.length(0), style.margin.top);
        assertEquals(LengthPercentageAuto.length(0), style.margin.bottom);

        style.margin = new Rect<>(
                LengthPercentageAuto.length(10),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.percent(5),
                LengthPercentageAuto.length(0));
        Rect<LengthPercentageAuto> margin = style.margin;
        assertEquals(LengthPercentageAuto.length(10), margin.left);
        assertEquals(LengthPercentageAuto.auto(), margin.right);
        assertEquals(LengthPercentageAuto.percent(5), margin.top);
        assertEquals(LengthPercentageAuto.length(0), margin.bottom);
    }

    @Test
    void paddingSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.padding.left);
        assertEquals(LengthPercentage.zero(), style.padding.right);
        assertEquals(LengthPercentage.zero(), style.padding.top);
        assertEquals(LengthPercentage.zero(), style.padding.bottom);

        style.padding = new Rect<>(
                LengthPercentage.length(10),
                LengthPercentage.length(10),
                LengthPercentage.percent(2),
                LengthPercentage.percent(2));
        Rect<LengthPercentage> padding = style.padding;
        assertEquals(LengthPercentage.length(10), padding.left);
        assertEquals(LengthPercentage.percent(2), padding.top);
    }

    @Test
    void borderSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.border.left);
        assertEquals(LengthPercentage.zero(), style.border.right);
        assertEquals(LengthPercentage.zero(), style.border.top);
        assertEquals(LengthPercentage.zero(), style.border.bottom);

        style.border = new Rect<>(
                LengthPercentage.length(1),
                LengthPercentage.length(1),
                LengthPercentage.length(1),
                LengthPercentage.length(1));
        Rect<LengthPercentage> border = style.border;
        assertEquals(LengthPercentage.length(1), border.left);

        style.border = new Rect<>(
                LengthPercentage.percent(10),
                LengthPercentage.length(1),
                LengthPercentage.length(1),
                LengthPercentage.length(1));
        assertEquals(LengthPercentage.percent(10), style.border.left);
    }

    @Test
    void insetSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.inset.left);
        assertEquals(LengthPercentageAuto.auto(), style.inset.right);
        assertEquals(LengthPercentageAuto.auto(), style.inset.top);
        assertEquals(LengthPercentageAuto.auto(), style.inset.bottom);

        style.inset = new Rect<>(
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.length(20),
                LengthPercentageAuto.percent(10),
                LengthPercentageAuto.length(0));
        Rect<LengthPercentageAuto> inset = style.inset;
        assertEquals(LengthPercentageAuto.auto(), inset.left);
        assertEquals(LengthPercentageAuto.length(20), inset.right);
        assertEquals(LengthPercentageAuto.percent(10), inset.top);
        assertEquals(LengthPercentageAuto.length(0), inset.bottom);
    }

    // Block layout properties

    @Test
    void itemIsTableDefaultsToFalseSetsAndGetsCorrectly() {
        Style style = new Style();
        assertFalse(style.itemIsTable);

        style.itemIsTable = true;
        assertTrue(style.itemIsTable);

        style.itemIsTable = false;
        assertFalse(style.itemIsTable);
    }

    @Test
    void itemIsReplacedDefaultsToFalseSetsAndGetsCorrectly() {
        Style style = new Style();
        assertFalse(style.itemIsReplaced);

        style.itemIsReplaced = true;
        assertTrue(style.itemIsReplaced);

        style.itemIsReplaced = false;
        assertFalse(style.itemIsReplaced);
    }

    @Test
    void scrollbarWidthDefaultsToZeroSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(0f, style.scrollbarWidth, 0f);

        style.scrollbarWidth = 15;
        assertEquals(15f, style.scrollbarWidth, 0f);

        style.scrollbarWidth = 0;
        assertEquals(0f, style.scrollbarWidth, 0f);
    }

    @Test
    void textAlignDefaultsToAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(TextAlign.Auto, style.textAlign);

        style.textAlign = TextAlign.LegacyLeft;
        assertEquals(TextAlign.LegacyLeft, style.textAlign);

        style.textAlign = TextAlign.LegacyRight;
        assertEquals(TextAlign.LegacyRight, style.textAlign);

        style.textAlign = TextAlign.LegacyCenter;
        assertEquals(TextAlign.LegacyCenter, style.textAlign);

        style.textAlign = TextAlign.Auto;
        assertEquals(TextAlign.Auto, style.textAlign);
    }

    // Grid layout properties

    @Test
    void gridAutoFlowDefaultsToRowSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(GridAutoFlow.Row, style.gridAutoFlow);

        style.gridAutoFlow = GridAutoFlow.Column;
        assertEquals(GridAutoFlow.Column, style.gridAutoFlow);

        style.gridAutoFlow = GridAutoFlow.RowDense;
        assertEquals(GridAutoFlow.RowDense, style.gridAutoFlow);

        style.gridAutoFlow = GridAutoFlow.ColumnDense;
        assertEquals(GridAutoFlow.ColumnDense, style.gridAutoFlow);

        style.gridAutoFlow = GridAutoFlow.Row;
        assertEquals(GridAutoFlow.Row, style.gridAutoFlow);
    }

    @Test
    void gridRowDefaultsToAutoAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(GridPlacement.auto(), style.gridRow.start);
        assertEquals(GridPlacement.auto(), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.line(3));
        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.line(3), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.line(2), GridPlacement.span(2));
        assertEquals(GridPlacement.line(2), style.gridRow.start);
        assertEquals(GridPlacement.span(2), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.auto(), GridPlacement.auto());
        assertEquals(GridPlacement.auto(), style.gridRow.start);
        assertEquals(GridPlacement.auto(), style.gridRow.end);
    }

    @Test
    void gridColumnDefaultsToAutoAutoSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(GridPlacement.auto(), style.gridColumn.start);
        assertEquals(GridPlacement.auto(), style.gridColumn.end);

        style.gridColumn = Line.of(GridPlacement.line(1), GridPlacement.line(4));
        assertEquals(GridPlacement.line(1), style.gridColumn.start);
        assertEquals(GridPlacement.line(4), style.gridColumn.end);

        style.gridColumn = Line.of(GridPlacement.auto(), GridPlacement.span(3));
        assertEquals(GridPlacement.auto(), style.gridColumn.start);
        assertEquals(GridPlacement.span(3), style.gridColumn.end);
    }

    @Test
    void gridTemplateRowsDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertTrue(style.gridTemplateRows instanceof List);
        assertEquals(0, style.gridTemplateRows.size());

        TrackSizingFunction track = TrackSizingFunction.of(10, "auto");
        style.gridTemplateRows = List.of(GridTemplateComponent.single(track));
        assertEquals(1, style.gridTemplateRows.size());
        GridTemplateComponent readback = style.gridTemplateRows.get(0);
        assertEquals(track, readback.track);
        assertEquals(MinTrackSizing.Kind.LENGTH, readback.track.min.kind);
        assertEquals(10f, readback.track.min.value, 0f);
    }

    @Test
    void gridTemplateColumnsDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertTrue(style.gridTemplateColumns instanceof List);
        assertEquals(0, style.gridTemplateColumns.size());

        style.gridTemplateColumns = List.of(
                GridTemplateComponent.single(TrackSizingFunction.of("min-content", "max-content")));
        assertEquals(1, style.gridTemplateColumns.size());
    }

    @Test
    void gridAutoRowsDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertTrue(style.gridAutoRows instanceof List);
        assertEquals(0, style.gridAutoRows.size());

        style.gridAutoRows = List.of(TrackSizingFunction.of(20, "auto"));
        assertEquals(1, style.gridAutoRows.size());
        TrackSizingFunction readback = style.gridAutoRows.get(0);
        assertEquals(MinTrackSizing.Kind.LENGTH, readback.min.kind);
        assertEquals(20f, readback.min.value, 0f);
    }

    @Test
    void gridAutoColumnsDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertTrue(style.gridAutoColumns instanceof List);
        assertEquals(0, style.gridAutoColumns.size());

        style.gridAutoColumns = List.of(TrackSizingFunction.of("auto", "1fr"));
        assertEquals(1, style.gridAutoColumns.size());
    }

    @Test
    void gridTemplateAreasDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(List.of(), style.getGridTemplateAreas());

        style.setGridTemplateAreas(List.of(
                new GridTemplateArea("header", 1, 2, 1, 4),
                new GridTemplateArea("main", 2, 4, 2, 4)));
        assertEquals(2, style.getGridTemplateAreas().size());
        assertEquals("header", style.getGridTemplateAreas().get(0).name);

        style.setGridTemplateAreas(List.of());
        assertEquals(List.of(), style.getGridTemplateAreas());
    }

    @Test
    void gridTemplateRowNamesDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(List.of(), style.gridTemplateRowNames);

        style.setGridTemplateRowNames(List.of(
                List.of("header-start"),
                List.of("header-end", "main-start"),
                List.of("main-end")));
        assertEquals(3, style.gridTemplateRowNames.size());
        assertEquals(List.of("header-start"), style.gridTemplateRowNames.get(0));
        assertEquals(List.of("header-end", "main-start"), style.gridTemplateRowNames.get(1));

        style.setGridTemplateRowNames(List.of());
        assertEquals(List.of(), style.gridTemplateRowNames);
    }

    @Test
    void gridTemplateColumnNamesDefaultsToEmptySetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(List.of(), style.gridTemplateColumnNames);

        style.setGridTemplateColumnNames(List.of(
                List.of("sidebar-start"),
                List.of("sidebar-end", "main-start"),
                List.of("main-end")));
        assertEquals(3, style.gridTemplateColumnNames.size());
        assertEquals(List.of("sidebar-start"), style.gridTemplateColumnNames.get(0));

        style.setGridTemplateColumnNames(List.of());
        assertEquals(List.of(), style.gridTemplateColumnNames);
    }

    @Test
    void gridRowHandlesNegativeLineIndices() {
        Style style = new Style();

        style.gridRow = Line.of(GridPlacement.line(-1), GridPlacement.line(-2));
        assertEquals(GridPlacement.line(-1), style.gridRow.start);
        assertEquals(GridPlacement.line(-2), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.line(-1));
        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.line(-1), style.gridRow.end);
    }

    @Test
    void gridColumnHandlesNegativeLineIndices() {
        Style style = new Style();

        style.gridColumn = Line.of(GridPlacement.line(-3), GridPlacement.line(-1));
        assertEquals(GridPlacement.line(-3), style.gridColumn.start);
        assertEquals(GridPlacement.line(-1), style.gridColumn.end);
    }

    @Test
    void gridRowHandlesSpanWithVariousValues() {
        Style style = new Style();

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.span(1));
        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.span(1), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.span(10));
        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.span(10), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.span(3), GridPlacement.line(5));
        assertEquals(GridPlacement.span(3), style.gridRow.start);
        assertEquals(GridPlacement.line(5), style.gridRow.end);
    }

    @Test
    void gridColumnHandlesSpanWithVariousValues() {
        Style style = new Style();

        style.gridColumn = Line.of(GridPlacement.span(2), GridPlacement.span(3));
        assertEquals(GridPlacement.span(2), style.gridColumn.start);
        assertEquals(GridPlacement.span(3), style.gridColumn.end);
    }

    @Test
    void gridRowAllAutoPlacement() {
        Style style = new Style();

        style.gridRow = Line.of(GridPlacement.auto(), GridPlacement.auto());
        assertEquals(GridPlacement.auto(), style.gridRow.start);
        assertEquals(GridPlacement.auto(), style.gridRow.end);

        style.gridRow = Line.of(GridPlacement.auto(), GridPlacement.line(3));
        assertEquals(GridPlacement.auto(), style.gridRow.start);
        assertEquals(GridPlacement.line(3), style.gridRow.end);
    }

    @Test
    void gridTemplateAreasHandlesComplexGridLayout() {
        Style style = new Style();

        style.setGridTemplateAreas(List.of(
                new GridTemplateArea("header", 1, 2, 1, 4),
                new GridTemplateArea("nav", 2, 3, 1, 2),
                new GridTemplateArea("main", 2, 3, 2, 3),
                new GridTemplateArea("aside", 2, 3, 3, 4),
                new GridTemplateArea("footer", 3, 4, 1, 4)));

        assertEquals(5, style.getGridTemplateAreas().size());
        assertEquals("header", style.getGridTemplateAreas().get(0).name);
        assertEquals("nav", style.getGridTemplateAreas().get(1).name);
        assertEquals("main", style.getGridTemplateAreas().get(2).name);
        assertEquals("aside", style.getGridTemplateAreas().get(3).name);
        assertEquals("footer", style.getGridTemplateAreas().get(4).name);

        GridTemplateArea header = style.getGridTemplateAreas().get(0);
        assertEquals(1, header.rowStart);
        assertEquals(2, header.rowEnd);
        assertEquals(1, header.columnStart);
        assertEquals(4, header.columnEnd);
    }

    @Test
    void gridTemplateRowNamesHandlesEmptyLineNames() {
        Style style = new Style();

        style.setGridTemplateRowNames(List.of(
                List.of(),
                List.of("named-line"),
                List.of(),
                List.of("another-name")));
        assertEquals(4, style.gridTemplateRowNames.size());
        assertEquals(List.of(), style.gridTemplateRowNames.get(0));
        assertEquals(List.of("named-line"), style.gridTemplateRowNames.get(1));
        assertEquals(List.of(), style.gridTemplateRowNames.get(2));
        assertEquals(List.of("another-name"), style.gridTemplateRowNames.get(3));
    }

    @Test
    void gridTemplateColumnNamesHandlesMultipleNamesPerLine() {
        Style style = new Style();

        style.setGridTemplateColumnNames(List.of(
                List.of("content-start", "sidebar-end", "main-start"),
                List.of("content-end", "sidebar-start")));
        assertEquals(2, style.gridTemplateColumnNames.size());
        assertEquals(List.of("content-start", "sidebar-end", "main-start"),
                style.gridTemplateColumnNames.get(0));
        assertEquals(List.of("content-end", "sidebar-start"),
                style.gridTemplateColumnNames.get(1));
    }

    @Test
    void gridAutoFlowAllVariantsWorkCorrectly() {
        Style style = new Style();

        for (GridAutoFlow variant : List.of(
                GridAutoFlow.Row,
                GridAutoFlow.Column,
                GridAutoFlow.RowDense,
                GridAutoFlow.ColumnDense)) {
            style.gridAutoFlow = variant;
            assertEquals(variant, style.gridAutoFlow);
        }
    }

    // Block layout edge cases

    @Test
    void scrollbarWidthHandlesDecimalValues() {
        Style style = new Style();

        style.scrollbarWidth = 15.5f;
        assertEquals(15.5f, style.scrollbarWidth, 1e-5f);

        style.scrollbarWidth = 0.5f;
        assertEquals(0.5f, style.scrollbarWidth, 1e-5f);
    }

    @Test
    void scrollbarWidthHandlesLargeValues() {
        Style style = new Style();

        style.scrollbarWidth = 100;
        assertEquals(100f, style.scrollbarWidth, 0f);
    }

    @Test
    void itemIsTableAndItemIsReplacedCanBeToggledMultipleTimes() {
        Style style = new Style();

        for (int i = 0; i < 5; i++) {
            style.itemIsTable = true;
            assertTrue(style.itemIsTable);
            style.itemIsTable = false;
            assertFalse(style.itemIsTable);
        }

        for (int i = 0; i < 5; i++) {
            style.itemIsReplaced = true;
            assertTrue(style.itemIsReplaced);
            style.itemIsReplaced = false;
            assertFalse(style.itemIsReplaced);
        }
    }

    @Test
    void textAlignAllVariantsWorkCorrectly() {
        Style style = new Style();

        for (TextAlign variant : List.of(
                TextAlign.Auto,
                TextAlign.LegacyLeft,
                TextAlign.LegacyRight,
                TextAlign.LegacyCenter)) {
            style.textAlign = variant;
            assertEquals(variant, style.textAlign);
        }
    }

    // Additional alignment edge cases

    @Test
    void justifyItemsAllAlignItemsVariantsWork() {
        Style style = new Style();

        for (AlignItems variant : List.of(
                AlignItems.Start,
                AlignItems.End,
                AlignItems.FlexStart,
                AlignItems.FlexEnd,
                AlignItems.Center,
                AlignItems.Baseline,
                AlignItems.Stretch)) {
            style.justifyItems = variant;
            assertEquals(variant, style.justifyItems);
        }

        style.justifyItems = null;
        assertNull(style.justifyItems);
    }

    @Test
    void justifySelfAllAlignSelfVariantsWork() {
        Style style = new Style();

        for (AlignSelf variant : List.of(
                AlignSelf.Auto,
                AlignSelf.Start,
                AlignSelf.End,
                AlignSelf.FlexStart,
                AlignSelf.FlexEnd,
                AlignSelf.Center,
                AlignSelf.Baseline,
                AlignSelf.Stretch)) {
            style.justifySelf = variant;
            assertEquals(variant, style.justifySelf);
        }
    }

    // Grid with display mode

    @Test
    void gridPropertiesWorkWhenDisplayIsGrid() {
        Style style = new Style();
        style.display = Display.Grid;

        style.gridAutoFlow = GridAutoFlow.Column;
        assertEquals(GridAutoFlow.Column, style.gridAutoFlow);

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.line(3));
        style.gridColumn = Line.of(GridPlacement.line(2), GridPlacement.line(4));

        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.line(3), style.gridRow.end);
        assertEquals(GridPlacement.line(2), style.gridColumn.start);
        assertEquals(GridPlacement.line(4), style.gridColumn.end);

        style.setGridTemplateAreas(List.of(
                new GridTemplateArea("content", 1, 2, 1, 2)));
        assertEquals(1, style.getGridTemplateAreas().size());

        style.setGridTemplateRowNames(List.of(List.of("top"), List.of("bottom")));
        style.setGridTemplateColumnNames(List.of(List.of("left"), List.of("right")));
        assertEquals(2, style.gridTemplateRowNames.size());
        assertEquals(2, style.gridTemplateColumnNames.size());
    }

    @Test
    void gridPropertiesCanBeSetEvenWhenDisplayIsNotGrid() {
        Style style = new Style();
        assertEquals(Display.Flex, style.display);

        style.gridAutoFlow = GridAutoFlow.RowDense;
        assertEquals(GridAutoFlow.RowDense, style.gridAutoFlow);

        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.line(2));
        assertEquals(GridPlacement.line(1), style.gridRow.start);
    }

    // Batch property reading (Style.get)

    @Test
    void readsSingleTopLevelProperty() {
        Style style = new Style();
        style.display = Display.Flex;
        assertEquals(Display.Flex, style.get("display"));
    }

    @Test
    void readsSingleNumericProperty() {
        Style style = new Style();
        style.flexGrow = 2.5f;
        assertEquals(2.5f, number(style.get("flexGrow")), 0f);
    }

    @Test
    void readsMultipleTopLevelProperties() {
        Style style = new Style();
        style.display = Display.Grid;
        style.flexGrow = 3;
        style.flexShrink = 0.5f;

        Object[] values = values(style.get("display", "flexGrow", "flexShrink"));
        assertEquals(Display.Grid, values[0]);
        assertEquals(3f, number(values[1]), 0f);
        assertEquals(0.5f, number(values[2]), 1e-5f);
    }

    @Test
    void readsNestedSizePropertiesWithDotNotation() {
        Style style = new Style();
        style.size = Size.of(Dimension.length(100), Dimension.percent(50));

        assertEquals(Dimension.length(100), style.get("width"));
        assertEquals(Dimension.percent(50), style.get("height"));
    }

    @Test
    void readsNestedMarginPropertiesWithDotNotation() {
        Style style = new Style();
        style.margin = new Rect<>(
                LengthPercentageAuto.length(10),
                LengthPercentageAuto.auto(),
                LengthPercentageAuto.percent(5),
                LengthPercentageAuto.length(0));

        assertEquals(LengthPercentageAuto.length(10), style.get("marginLeft"));
        assertEquals(LengthPercentageAuto.auto(), style.get("marginRight"));
        assertEquals(LengthPercentageAuto.percent(5), style.get("marginTop"));
        assertEquals(LengthPercentageAuto.length(0), style.get("marginBottom"));
    }

    @Test
    void readsMixedTopLevelAndNestedProperties() {
        Style style = new Style();
        style.display = Display.Flex;
        style.flexDirection = FlexDirection.Column;
        style.size = Size.of(Dimension.length(200), Dimension.length(100));
        style.padding = new Rect<>(
                LengthPercentage.length(10),
                LengthPercentage.length(10),
                LengthPercentage.length(5),
                LengthPercentage.length(5));

        Object[] values = values(style.get("display", "width", "paddingLeft"));
        assertEquals(Display.Flex, values[0]);
        assertEquals(Dimension.length(200), values[1]);
        assertEquals(LengthPercentage.length(10), values[2]);
    }

    @Test
    void readsFullNestedObjectSize() {
        Style style = new Style();
        style.size = Size.of(Dimension.length(150), Dimension.percent(75));

        @SuppressWarnings("unchecked")
        Size<Dimension> size = (Size<Dimension>) style.get("size");
        assertEquals(Dimension.length(150), size.width);
        assertEquals(Dimension.percent(75), size.height);
    }

    @Test
    void readsOverflowNestedProperties() {
        Style style = new Style();
        style.overflow = Point.of(Overflow.Hidden, Overflow.Scroll);

        assertEquals(Overflow.Hidden, style.get("overflowX"));
        assertEquals(Overflow.Scroll, style.get("overflowY"));
    }

    @Test
    void readsGapNestedProperties() {
        Style style = new Style();
        style.gap = Size.of(LengthPercentage.length(10), LengthPercentage.percent(5));

        assertEquals(LengthPercentage.length(10), style.get("columnGap"));
        assertEquals(LengthPercentage.percent(5), style.get("rowGap"));
    }

    @Test
    void readsBooleanProperties() {
        Style style = new Style();
        style.itemIsTable = true;
        style.itemIsReplaced = true;

        assertEquals(true, style.get("itemIsTable"));
        assertEquals(true, style.get("itemIsReplaced"));
    }

    @Test
    void returnsUndefinedForOptionalPropertiesThatAreNotSet() {
        Style style = new Style();
        assertNull(style.get("alignItems"));
        assertNull(style.get("aspectRatio"));
    }

    @Test
    void handlesGridRowColumnProperties() {
        Style style = new Style();
        style.gridRow = Line.of(GridPlacement.line(1), GridPlacement.line(3));
        style.gridColumn = Line.of(GridPlacement.line(2), GridPlacement.line(4));

        assertEquals(GridPlacement.line(1), style.get("gridRowStart"));
        assertEquals(GridPlacement.line(3), style.get("gridRowEnd"));
        assertEquals(GridPlacement.line(2), style.get("gridColumnStart"));
        assertEquals(GridPlacement.line(4), style.get("gridColumnEnd"));
    }

    @Test
    void returnsUndefinedForEmptyKeysArray() {
        Style style = new Style();
        assertNull(style.get());
    }

    @Test
    void throwsErrorForUnknownPropertyPath() {
        Style style = new Style();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> style.get("unknownProperty"));
        assertEquals("Unknown property path: unknownProperty", error.getMessage());
    }

    // Batch property writing (Style.set)

    @Test
    void setsSingleTopLevelProperty() {
        Style style = new Style();
        style.set(props("display", Display.Grid));
        assertEquals(Display.Grid, style.display);
    }

    @Test
    void setsMultipleTopLevelProperties() {
        Style style = new Style();
        style.set(props(
                "display", Display.Flex,
                "flexDirection", FlexDirection.Column,
                "flexGrow", 2,
                "flexShrink", 0.5));

        assertEquals(Display.Flex, style.display);
        assertEquals(FlexDirection.Column, style.flexDirection);
        assertEquals(2f, style.flexGrow, 0f);
        assertEquals(0.5f, style.flexShrink, 1e-5f);
    }

    @Test
    void setsNestedSizePropertiesWithDotNotation() {
        Style style = new Style();
        style.set(props(
                "width", 100,
                "height", "50%"));

        assertEquals(Dimension.length(100), style.size.width);
        assertEquals(Dimension.percent(50), style.size.height);
    }

    @Test
    void setsNestedMarginPropertiesWithDotNotation() {
        Style style = new Style();
        style.set(props(
                "marginLeft", 10,
                "marginRight", "auto",
                "marginTop", "5%",
                "marginBottom", 0));

        assertEquals(LengthPercentageAuto.length(10), style.margin.left);
        assertEquals(LengthPercentageAuto.auto(), style.margin.right);
        assertEquals(LengthPercentageAuto.percent(5), style.margin.top);
        assertEquals(LengthPercentageAuto.length(0), style.margin.bottom);
    }

    @Test
    void setsMixedTopLevelAndNestedProperties() {
        Style style = new Style();
        style.set(props(
                "display", Display.Flex,
                "flexDirection", FlexDirection.Row,
                "width", 200,
                "paddingLeft", 10,
                "paddingRight", 10));

        assertEquals(Display.Flex, style.display);
        assertEquals(FlexDirection.Row, style.flexDirection);
        assertEquals(Dimension.length(200), style.size.width);
        assertEquals(LengthPercentage.length(10), style.padding.left);
        assertEquals(LengthPercentage.length(10), style.padding.right);
    }

    @Test
    void setsFullNestedObjectSize() {
        Style style = new Style();
        style.set(props("size", props("width", 150, "height", "75%")));

        assertEquals(Dimension.length(150), style.size.width);
        assertEquals(Dimension.percent(75), style.size.height);
    }

    @Test
    void setsOverflowNestedProperties() {
        Style style = new Style();
        style.set(props(
                "overflowX", Overflow.Hidden,
                "overflowY", Overflow.Scroll));

        assertEquals(Overflow.Hidden, style.overflow.x);
        assertEquals(Overflow.Scroll, style.overflow.y);
    }

    @Test
    void setsBooleanProperties() {
        Style style = new Style();
        style.set(props(
                "itemIsTable", true,
                "itemIsReplaced", true));

        assertTrue(style.itemIsTable);
        assertTrue(style.itemIsReplaced);
    }

    @Test
    void setThenGetRoundtrip() {
        Style style = new Style();
        style.set(props(
                "display", Display.Grid,
                "width", 300,
                "marginLeft", 20,
                "flexGrow", 1.5));

        Object[] values = values(style.get("display", "width", "marginLeft", "flexGrow"));
        assertEquals(Display.Grid, values[0]);
        assertEquals(Dimension.length(300), values[1]);
        assertEquals(LengthPercentageAuto.length(20), values[2]);
        assertEquals(1.5f, number(values[3]), 1e-5f);
    }

    @Test
    void setHandlesGridRowColumnProperties() {
        Style style = new Style();
        style.set(props(
                "gridRowStart", 1,
                "gridRowEnd", 3,
                "gridColumnStart", 2,
                "gridColumnEnd", 4));

        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.line(3), style.gridRow.end);
        assertEquals(GridPlacement.line(2), style.gridColumn.start);
        assertEquals(GridPlacement.line(4), style.gridColumn.end);
    }

    @Test
    void setThrowsErrorForUnknownPropertyKey() {
        Style style = new Style();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> style.set(props("unknownProperty", 1)));
        assertEquals("Unknown property path for set: unknownProperty", error.getMessage());
    }

    // Individual overflow properties

    @Test
    void overflowXSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Overflow.Visible, style.getOverflowX());

        style.setOverflowX(Overflow.Hidden);
        assertEquals(Overflow.Hidden, style.getOverflowX());

        style.setOverflowX(Overflow.Scroll);
        assertEquals(Overflow.Scroll, style.getOverflowX());

        style.setOverflowY(Overflow.Clip);
        assertEquals(Overflow.Scroll, style.getOverflowX());
        assertEquals(Overflow.Clip, style.getOverflowY());
    }

    @Test
    void overflowYSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Overflow.Visible, style.getOverflowY());

        style.setOverflowY(Overflow.Hidden);
        assertEquals(Overflow.Hidden, style.getOverflowY());

        style.setOverflowY(Overflow.Scroll);
        assertEquals(Overflow.Scroll, style.getOverflowY());
    }

    @Test
    void overflowXAndOverflowYAreIndependentOfEachOther() {
        Style style = new Style();
        style.setOverflowX(Overflow.Hidden);
        style.setOverflowY(Overflow.Scroll);

        assertEquals(Overflow.Hidden, style.getOverflowX());
        assertEquals(Overflow.Scroll, style.getOverflowY());
        assertEquals(Overflow.Hidden, style.overflow.x);
        assertEquals(Overflow.Scroll, style.overflow.y);
    }

    // Individual size properties

    @Test
    void widthSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getWidth());

        style.setWidth(200);
        assertEquals(Dimension.length(200), style.getWidth());

        style.setWidth("50%");
        assertEquals(Dimension.percent(50), style.getWidth());

        style.setWidth("auto");
        assertEquals(Dimension.auto(), style.getWidth());
    }

    @Test
    void heightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getHeight());

        style.setHeight(100);
        assertEquals(Dimension.length(100), style.getHeight());

        style.setHeight("75%");
        assertEquals(Dimension.percent(75), style.getHeight());

        style.setHeight("auto");
        assertEquals(Dimension.auto(), style.getHeight());
    }

    @Test
    void widthAndHeightAreIndependentOfEachOther() {
        Style style = new Style();
        style.setWidth(300);
        style.setHeight("50%");

        assertEquals(Dimension.length(300), style.getWidth());
        assertEquals(Dimension.percent(50), style.getHeight());
        assertEquals(Dimension.length(300), style.size.width);
        assertEquals(Dimension.percent(50), style.size.height);
    }

    @Test
    void minWidthSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getMinWidth());

        style.setMinWidth(100);
        assertEquals(Dimension.length(100), style.getMinWidth());

        style.setMinWidth("10%");
        assertEquals(Dimension.percent(10), style.getMinWidth());

        style.setMinWidth("auto");
        assertEquals(Dimension.auto(), style.getMinWidth());
    }

    @Test
    void minHeightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getMinHeight());

        style.setMinHeight(50);
        assertEquals(Dimension.length(50), style.getMinHeight());

        style.setMinHeight("auto");
        assertEquals(Dimension.auto(), style.getMinHeight());
    }

    @Test
    void maxWidthSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getMaxWidth());

        style.setMaxWidth(500);
        assertEquals(Dimension.length(500), style.getMaxWidth());

        style.setMaxWidth("100%");
        assertEquals(Dimension.percent(100), style.getMaxWidth());

        style.setMaxWidth("auto");
        assertEquals(Dimension.auto(), style.getMaxWidth());
    }

    @Test
    void maxHeightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(Dimension.auto(), style.getMaxHeight());

        style.setMaxHeight(300);
        assertEquals(Dimension.length(300), style.getMaxHeight());

        style.setMaxHeight("auto");
        assertEquals(Dimension.auto(), style.getMaxHeight());
    }

    @Test
    void minMaxWidthHeightAreAllIndependent() {
        Style style = new Style();
        style.setMinWidth(100);
        style.setMaxWidth(500);
        style.setMinHeight(50);
        style.setMaxHeight(300);

        assertEquals(Dimension.length(100), style.getMinWidth());
        assertEquals(Dimension.length(500), style.getMaxWidth());
        assertEquals(Dimension.length(50), style.getMinHeight());
        assertEquals(Dimension.length(300), style.getMaxHeight());
    }

    // Individual margin properties

    @Test
    void marginLeftSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.length(0), style.getMarginLeft());

        style.setMarginLeft(10);
        assertEquals(LengthPercentageAuto.length(10), style.getMarginLeft());

        style.setMarginLeft("5%");
        assertEquals(LengthPercentageAuto.percent(5), style.getMarginLeft());

        style.setMarginLeft("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getMarginLeft());
    }

    @Test
    void marginRightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.length(0), style.getMarginRight());

        style.setMarginRight(20);
        assertEquals(LengthPercentageAuto.length(20), style.getMarginRight());

        style.setMarginRight("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getMarginRight());
    }

    @Test
    void marginTopSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.length(0), style.getMarginTop());

        style.setMarginTop(5);
        assertEquals(LengthPercentageAuto.length(5), style.getMarginTop());

        style.setMarginTop("10%");
        assertEquals(LengthPercentageAuto.percent(10), style.getMarginTop());
    }

    @Test
    void marginBottomSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.length(0), style.getMarginBottom());

        style.setMarginBottom(15);
        assertEquals(LengthPercentageAuto.length(15), style.getMarginBottom());

        style.setMarginBottom("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getMarginBottom());
    }

    @Test
    void allMarginPropertiesAreIndependentOfEachOther() {
        Style style = new Style();
        style.setMarginLeft(10);
        style.setMarginRight("auto");
        style.setMarginTop("5%");
        style.setMarginBottom(0);

        assertEquals(LengthPercentageAuto.length(10), style.getMarginLeft());
        assertEquals(LengthPercentageAuto.auto(), style.getMarginRight());
        assertEquals(LengthPercentageAuto.percent(5), style.getMarginTop());
        assertEquals(LengthPercentageAuto.length(0), style.getMarginBottom());
        assertEquals(LengthPercentageAuto.length(10), style.margin.left);
        assertEquals(LengthPercentageAuto.auto(), style.margin.right);
        assertEquals(LengthPercentageAuto.percent(5), style.margin.top);
        assertEquals(LengthPercentageAuto.length(0), style.margin.bottom);
    }

    // Individual padding properties

    @Test
    void paddingLeftSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getPaddingLeft());

        style.setPaddingLeft(20);
        assertEquals(LengthPercentage.length(20), style.getPaddingLeft());

        style.setPaddingLeft("10%");
        assertEquals(LengthPercentage.percent(10), style.getPaddingLeft());
    }

    @Test
    void paddingRightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getPaddingRight());

        style.setPaddingRight(25);
        assertEquals(LengthPercentage.length(25), style.getPaddingRight());
    }

    @Test
    void paddingTopSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getPaddingTop());

        style.setPaddingTop(10);
        assertEquals(LengthPercentage.length(10), style.getPaddingTop());

        style.setPaddingTop("5%");
        assertEquals(LengthPercentage.percent(5), style.getPaddingTop());
    }

    @Test
    void paddingBottomSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getPaddingBottom());

        style.setPaddingBottom(15);
        assertEquals(LengthPercentage.length(15), style.getPaddingBottom());
    }

    @Test
    void allPaddingPropertiesAreIndependentOfEachOther() {
        Style style = new Style();
        style.setPaddingLeft(20);
        style.setPaddingRight(30);
        style.setPaddingTop("5%");
        style.setPaddingBottom(10);

        assertEquals(LengthPercentage.length(20), style.getPaddingLeft());
        assertEquals(LengthPercentage.length(30), style.getPaddingRight());
        assertEquals(LengthPercentage.percent(5), style.getPaddingTop());
        assertEquals(LengthPercentage.length(10), style.getPaddingBottom());
    }

    // Individual border properties

    @Test
    void borderLeftSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getBorderLeft());

        style.setBorderLeft(1);
        assertEquals(LengthPercentage.length(1), style.getBorderLeft());

        style.setBorderLeft("2%");
        assertEquals(LengthPercentage.percent(2), style.getBorderLeft());
    }

    @Test
    void borderRightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getBorderRight());

        style.setBorderRight(2);
        assertEquals(LengthPercentage.length(2), style.getBorderRight());
    }

    @Test
    void borderTopSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getBorderTop());

        style.setBorderTop(1);
        assertEquals(LengthPercentage.length(1), style.getBorderTop());
    }

    @Test
    void borderBottomSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getBorderBottom());

        style.setBorderBottom(3);
        assertEquals(LengthPercentage.length(3), style.getBorderBottom());
    }

    @Test
    void allBorderPropertiesAreIndependentOfEachOther() {
        Style style = new Style();
        style.setBorderLeft(1);
        style.setBorderRight(2);
        style.setBorderTop(3);
        style.setBorderBottom(4);

        assertEquals(LengthPercentage.length(1), style.getBorderLeft());
        assertEquals(LengthPercentage.length(2), style.getBorderRight());
        assertEquals(LengthPercentage.length(3), style.getBorderTop());
        assertEquals(LengthPercentage.length(4), style.getBorderBottom());
    }

    // Individual inset properties

    @Test
    void leftSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.getLeft());

        style.position = Position.Absolute;
        style.setLeft(0);
        assertEquals(LengthPercentageAuto.length(0), style.getLeft());

        style.setLeft("10%");
        assertEquals(LengthPercentageAuto.percent(10), style.getLeft());

        style.setLeft("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getLeft());
    }

    @Test
    void rightSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.getRight());

        style.position = Position.Absolute;
        style.setRight(20);
        assertEquals(LengthPercentageAuto.length(20), style.getRight());

        style.setRight("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getRight());
    }

    @Test
    void topSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.getTop());

        style.position = Position.Absolute;
        style.setTop(0);
        assertEquals(LengthPercentageAuto.length(0), style.getTop());

        style.setTop("5%");
        assertEquals(LengthPercentageAuto.percent(5), style.getTop());
    }

    @Test
    void bottomSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentageAuto.auto(), style.getBottom());

        style.position = Position.Absolute;
        style.setBottom(10);
        assertEquals(LengthPercentageAuto.length(10), style.getBottom());

        style.setBottom("auto");
        assertEquals(LengthPercentageAuto.auto(), style.getBottom());
    }

    @Test
    void allInsetPropertiesAreIndependentOfEachOther() {
        Style style = new Style();
        style.position = Position.Absolute;
        style.setLeft(0);
        style.setRight(20);
        style.setTop("10%");
        style.setBottom("auto");

        assertEquals(LengthPercentageAuto.length(0), style.getLeft());
        assertEquals(LengthPercentageAuto.length(20), style.getRight());
        assertEquals(LengthPercentageAuto.percent(10), style.getTop());
        assertEquals(LengthPercentageAuto.auto(), style.getBottom());
        assertEquals(LengthPercentageAuto.length(0), style.inset.left);
        assertEquals(LengthPercentageAuto.length(20), style.inset.right);
        assertEquals(LengthPercentageAuto.percent(10), style.inset.top);
        assertEquals(LengthPercentageAuto.auto(), style.inset.bottom);
    }

    // Individual gap properties

    @Test
    void columnGapSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getColumnGap());

        style.setColumnGap(10);
        assertEquals(LengthPercentage.length(10), style.getColumnGap());

        style.setColumnGap("5%");
        assertEquals(LengthPercentage.percent(5), style.getColumnGap());
    }

    @Test
    void rowGapSetsAndGetsCorrectly() {
        Style style = new Style();
        assertEquals(LengthPercentage.zero(), style.getRowGap());

        style.setRowGap(15);
        assertEquals(LengthPercentage.length(15), style.getRowGap());

        style.setRowGap("10%");
        assertEquals(LengthPercentage.percent(10), style.getRowGap());
    }

    @Test
    void columnGapAndRowGapAreIndependentOfEachOther() {
        Style style = new Style();
        style.setColumnGap(10);
        style.setRowGap("5%");

        assertEquals(LengthPercentage.length(10), style.getColumnGap());
        assertEquals(LengthPercentage.percent(5), style.getRowGap());
        assertEquals(LengthPercentage.length(10), style.gap.width);
        assertEquals(LengthPercentage.percent(5), style.gap.height);
    }

    // Individual grid row/column properties

    @Test
    void gridRowStartSetsAndGetsCorrectly() {
        Style style = new Style();
        style.display = Display.Grid;
        assertEquals(GridPlacement.auto(), style.getGridRowStart());

        style.setGridRowStart(1);
        assertEquals(GridPlacement.line(1), style.getGridRowStart());

        style.setGridRowStart("auto");
        assertEquals(GridPlacement.auto(), style.getGridRowStart());

        style.setGridRowStart(GridPlacement.span(2));
        assertEquals(GridPlacement.span(2), style.getGridRowStart());
    }

    @Test
    void gridRowEndSetsAndGetsCorrectly() {
        Style style = new Style();
        style.display = Display.Grid;
        assertEquals(GridPlacement.auto(), style.getGridRowEnd());

        style.setGridRowEnd(3);
        assertEquals(GridPlacement.line(3), style.getGridRowEnd());

        style.setGridRowEnd(GridPlacement.span(2));
        assertEquals(GridPlacement.span(2), style.getGridRowEnd());
    }

    @Test
    void gridColumnStartSetsAndGetsCorrectly() {
        Style style = new Style();
        style.display = Display.Grid;
        assertEquals(GridPlacement.auto(), style.getGridColumnStart());

        style.setGridColumnStart(1);
        assertEquals(GridPlacement.line(1), style.getGridColumnStart());

        style.setGridColumnStart("auto");
        assertEquals(GridPlacement.auto(), style.getGridColumnStart());
    }

    @Test
    void gridColumnEndSetsAndGetsCorrectly() {
        Style style = new Style();
        style.display = Display.Grid;
        assertEquals(GridPlacement.auto(), style.getGridColumnEnd());

        style.setGridColumnEnd(4);
        assertEquals(GridPlacement.line(4), style.getGridColumnEnd());

        style.setGridColumnEnd(GridPlacement.span(3));
        assertEquals(GridPlacement.span(3), style.getGridColumnEnd());
    }

    @Test
    void allGridRowColumnPropertiesAreIndependentOfEachOther() {
        Style style = new Style();
        style.display = Display.Grid;
        style.setGridRowStart(1);
        style.setGridRowEnd(3);
        style.setGridColumnStart(2);
        style.setGridColumnEnd(4);

        assertEquals(GridPlacement.line(1), style.getGridRowStart());
        assertEquals(GridPlacement.line(3), style.getGridRowEnd());
        assertEquals(GridPlacement.line(2), style.getGridColumnStart());
        assertEquals(GridPlacement.line(4), style.getGridColumnEnd());
        assertEquals(GridPlacement.line(1), style.gridRow.start);
        assertEquals(GridPlacement.line(3), style.gridRow.end);
        assertEquals(GridPlacement.line(2), style.gridColumn.start);
        assertEquals(GridPlacement.line(4), style.gridColumn.end);
    }

    // Individual properties with batch get/set

    @Test
    void getWorksWithNewIndividualPropertyNames() {
        Style style = new Style();
        style.setWidth(200);
        style.setMarginLeft(10);
        style.setPaddingTop(5);
        style.setOverflowX(Overflow.Hidden);

        assertEquals(Dimension.length(200), style.get("width"));
        assertEquals(LengthPercentageAuto.length(10), style.get("marginLeft"));
        assertEquals(LengthPercentage.length(5), style.get("paddingTop"));
        assertEquals(Overflow.Hidden, style.get("overflowX"));
    }

    @Test
    void setWorksWithNewIndividualPropertyNames() {
        Style style = new Style();
        style.set(props(
                "width", 300,
                "height", "50%",
                "marginLeft", 20,
                "marginRight", "auto",
                "paddingTop", 10,
                "paddingBottom", 15,
                "borderLeft", 1,
                "borderRight", 2,
                "overflowX", Overflow.Scroll,
                "overflowY", Overflow.Hidden));

        assertEquals(Dimension.length(300), style.getWidth());
        assertEquals(Dimension.percent(50), style.getHeight());
        assertEquals(LengthPercentageAuto.length(20), style.getMarginLeft());
        assertEquals(LengthPercentageAuto.auto(), style.getMarginRight());
        assertEquals(LengthPercentage.length(10), style.getPaddingTop());
        assertEquals(LengthPercentage.length(15), style.getPaddingBottom());
        assertEquals(LengthPercentage.length(1), style.getBorderLeft());
        assertEquals(LengthPercentage.length(2), style.getBorderRight());
        assertEquals(Overflow.Scroll, style.getOverflowX());
        assertEquals(Overflow.Hidden, style.getOverflowY());
    }

    @Test
    void getMultipleIndividualPropertiesAtOnce() {
        Style style = new Style();
        style.setWidth(200);
        style.setHeight(100);
        style.setMarginLeft(10);
        style.setPaddingTop(5);

        Object[] values = values(style.get("width", "height", "marginLeft", "paddingTop"));
        assertEquals(Dimension.length(200), values[0]);
        assertEquals(Dimension.length(100), values[1]);
        assertEquals(LengthPercentageAuto.length(10), values[2]);
        assertEquals(LengthPercentage.length(5), values[3]);
    }

    // Invalid enum input

    @Test
    void ignoresInvalidRawEnumValuesWithoutCorruptingState() {
        Style style = new Style(props(
                "display", Display.Grid,
                "direction", Direction.Rtl,
                "float", Float.Right,
                "clear", Clear.Both,
                "alignItems", AlignItems.Center));

        style.set(props(
                "display", 255,
                "direction", 1.5,
                "float", java.lang.Float.NaN,
                "clear", -1,
                "alignItems", 99));

        Object[] values = values(style.get("display", "direction", "float", "clear", "alignItems"));
        assertEquals(Display.Grid, values[0]);
        assertEquals(Direction.Rtl, values[1]);
        assertEquals(Float.Right, values[2]);
        assertEquals(Clear.Both, values[3]);
        assertEquals(AlignItems.Center, values[4]);
    }

    // Dimension constraints and tree style updates

    @Test
    void preservesMinMaxDimensionsThroughLayoutAndTreeStyleEdits() {
        Node child = new Node(new Style(props(
                "width", 10,
                "height", 100,
                "minSize", props("width", "50%", "height", "auto"),
                "maxSize", props("width", "auto", "height", "50%"))));
        Node root = new Node(
                new Style(props("display", Display.Block, "width", 200, "height", 100)),
                List.of(child));

        root.computeLayout(200f, 100f);
        assertEquals(100f, child.layout.width, 0.01f);
        assertEquals(50f, child.layout.height, 0.01f);

        Style constrained = child.style.copy();
        Object[] minMax = values(constrained.get("minSize", "maxSize"));
        assertEquals(Size.of(Dimension.percent(50), Dimension.auto()), minMax[0]);
        assertEquals(Size.of(Dimension.auto(), Dimension.percent(50)), minMax[1]);
        assertEquals(Dimension.percent(50), constrained.getMinWidth());
        assertEquals(Dimension.percent(50), constrained.getMaxHeight());
        constrained.setMinWidth(60);
        constrained.setMaxHeight(30);
        child.style = constrained.copy();
        root.computeLayout(200f, 100f);
        assertEquals(60f, child.layout.width, 0.01f);
        assertEquals(30f, child.layout.height, 0.01f);

        Style unconstrained = child.style.copy();
        unconstrained.set(props(
                "minSize", props("width", "auto", "height", "auto"),
                "maxSize", props("width", "auto", "height", "auto")));
        child.style = unconstrained.copy();
        root.computeLayout(200f, 100f);
        assertEquals(10f, child.layout.width, 0.01f);
        assertEquals(100f, child.layout.height, 0.01f);
    }

    @Test
    void appliesPercentageMinHeightAndMaxWidthFromScalarProperties() {
        Style style = new Style(props("width", 300, "height", 1));
        style.set(props("minHeight", "20%", "maxWidth", "75%"));
        Node child = new Node(style);
        Node root = new Node(
                new Style(props("display", Display.Block, "width", 200, "height", 100)),
                List.of(child));

        root.computeLayout(200f, 100f);
        assertEquals(150f, child.layout.width, 0.01f);
        assertEquals(20f, child.layout.height, 0.01f);
        Object[] minMax = values(child.style.copy().get("minHeight", "maxWidth"));
        assertEquals(Dimension.percent(20), minMax[0]);
        assertEquals(Dimension.percent(75), minMax[1]);

        style.minSize = Size.of(Dimension.auto(), Dimension.length(30));
        style.maxSize = Size.of(Dimension.length(120), Dimension.auto());
        child.style = style.copy();
        root.computeLayout(200f, 100f);
        assertEquals(120f, child.layout.width, 0.01f);
        assertEquals(30f, child.layout.height, 0.01f);
    }
}
