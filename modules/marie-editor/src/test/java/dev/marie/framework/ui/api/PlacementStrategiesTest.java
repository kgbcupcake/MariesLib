package dev.marie.framework.ui.api;

import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementStrategiesTest {

    private static final Bounds SCREEN = new Bounds(0, 0, 400, 300);
    private static final Bounds NATURAL = new Bounds(50, 60, 80, 20);

    /** What ModuleScales saves when a text size is set on a box that was never moved. */
    private static final ComponentState STYLE_ONLY = new ComponentState(0, 0, 0, 0, false, false, false, 0,
            1.5, ComponentState.DEFAULT_PADDING_SCALE);

    private static EditCommit moved(Bounds start) {
        return new EditCommit(start, false, false, false, false, false);
    }

    @Test
    void screenAnchoredStyleOnlyStateKeepsNaturalPosition() {
        ScreenAnchoredPlacement placement = ScreenAnchoredPlacement.of(() -> SCREEN, Anchor.TOP_LEFT);
        assertEquals(NATURAL, placement.toBounds(STYLE_ONLY, NATURAL));
    }

    @Test
    void panelRelativeStyleOnlyStateKeepsNaturalPosition() {
        PanelRelativePlacement placement = PanelRelativePlacement.of(() -> SCREEN, () -> 1.0d);
        assertEquals(NATURAL, placement.toBounds(STYLE_ONLY, NATURAL));
    }

    @Test
    void boxMovedToTheOriginStaysThere() {
        ScreenAnchoredPlacement placement = ScreenAnchoredPlacement.of(() -> SCREEN, Anchor.TOP_LEFT);
        ComponentState state = placement.toState(new Bounds(0, 0, 80, 20), moved(NATURAL), null);
        assertEquals(new Bounds(0, 0, 80, 20), placement.toBounds(state, NATURAL));
    }

    @Test
    void clampedBoxBiggerThanTheScreenShrinksToFit() {
        ScreenAnchoredPlacement placement = ScreenAnchoredPlacement.of(() -> SCREEN, Anchor.TOP_LEFT).clampedToScreen();
        ComponentState state = new ComponentState(-20, -10, 500, 400, false, true, true, 0);
        assertEquals(SCREEN, placement.toBounds(state, NATURAL));
    }

    @Test
    void leftEdgeResizeGrowsTheMarginOnlyWhenContentWasNotKeptInPlace() {
        ScreenAnchoredPlacement placement = ScreenAnchoredPlacement.of(() -> SCREEN, Anchor.TOP_LEFT);
        Bounds start = new Bounds(100, 50, 80, 20);
        Bounds committed = new Bounds(80, 50, 100, 20);

        EditCommit plain = new EditCommit(start, true, true, false, false, false, false);
        assertEquals(20, placement.toState(committed, plain, null).leftMargin());

        EditCommit kept = new EditCommit(start, true, true, false, false, false, true);
        assertEquals(0, placement.toState(committed, kept, null).leftMargin());
    }
}
