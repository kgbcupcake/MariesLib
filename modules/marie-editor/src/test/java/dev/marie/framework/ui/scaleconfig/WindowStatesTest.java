package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.modulesettings.InMemoryStoreAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes window clamping (min size, kept on screen, no maximum beyond the screen) and the one-open-window rule. */
class WindowStatesTest {

    private static final Bounds SCREEN = new Bounds(0, 0, 800, 600);

    @Test
    void windowsNeverGoBelowTheMinimumSize() {
        Bounds clamped = WindowStates.clamp(new Bounds(10, 10, 5, 5), SCREEN);
        assertEquals(ScaleConfigPanel.CARD_WIDTH, clamped.width());
        assertEquals(ScaleConfigPanel.CARD_HEIGHT, clamped.height());
    }

    @Test
    void thereIsNoMaximumBeyondTheScreenItself() {
        Bounds big = WindowStates.clamp(new Bounds(0, 0, 700, 550), SCREEN);
        assertEquals(700, big.width());
        assertEquals(550, big.height());

        Bounds tooBig = WindowStates.clamp(new Bounds(0, 0, 5000, 5000), SCREEN);
        assertEquals(800, tooBig.width());
        assertEquals(600, tooBig.height());
    }

    @Test
    void windowsAreKeptFullyOnScreen() {
        Bounds clamped = WindowStates.clamp(new Bounds(790, 590, 224, 89), SCREEN);
        assertEquals(800 - 224, clamped.x());
        assertEquals(600 - 89, clamped.y());
        Bounds negative = WindowStates.clamp(new Bounds(-50, -50, 224, 89), SCREEN);
        assertEquals(0, negative.x());
        assertEquals(0, negative.y());
    }

    @Test
    void anEntryThatWasNeverOpenedIsCollapsed() {
        WindowStates states = new WindowStates(InMemoryStoreAccess.create());
        assertTrue(states.load("ws-never-opened").collapsed());
    }

    @Test
    void openingOneWindowCollapsesEveryOtherKnownOne() {
        WindowStates states = new WindowStates(InMemoryStoreAccess.create());
        states.register("ws-one");
        states.register("ws-two");

        states.open("ws-one", () -> new Bounds(1, 2, 224, 89));
        assertFalse(states.load("ws-one").collapsed());
        assertEquals(1, states.load("ws-one").x());

        states.open("ws-two", () -> new Bounds(3, 4, 224, 89));
        assertFalse(states.load("ws-two").collapsed());
        assertTrue(states.load("ws-one").collapsed(), "the previously open window collapsed");
    }

    @Test
    void reopeningUsesTheSavedBoundsNotTheDefault() {
        WindowStates states = new WindowStates(InMemoryStoreAccess.create());
        states.register("ws-saved");
        states.open("ws-saved", () -> new Bounds(0, 0, 224, 89));
        states.persistBounds("ws-saved", new Bounds(50, 60, 300, 200));
        states.collapse("ws-saved");
        assertTrue(states.load("ws-saved").collapsed());

        states.open("ws-saved", () -> new Bounds(999, 999, 224, 89));
        ComponentState state = states.load("ws-saved");
        assertFalse(state.collapsed());
        assertEquals(50, state.x());
        assertEquals(300, state.width());
    }
}
