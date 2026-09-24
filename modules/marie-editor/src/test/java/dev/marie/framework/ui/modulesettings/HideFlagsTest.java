package dev.marie.framework.ui.modulesettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes how each per-module "hide" switch is stored and that they don't interfere with each other. */
class HideFlagsTest {

    private final InMemoryStore store = new InMemoryStore();

    @Test
    void allFlagsDefaultToNotHidden() {
        assertFalse(HideFlags.textHidden(store, "m"));
        assertFalse(HideFlags.iconsHidden(store, "m"));
        assertFalse(HideFlags.barsHidden(store, "m"));
        assertFalse(HideFlags.headerHidden(store, "m"));
        assertFalse(HideFlags.windowHidden(store, "m"));
    }

    @Test
    void headerHiddenIsStoredUnderItsOwnKeyIndependentOfHideText() {
        HideFlags.setHeaderHidden(store, "m", true);
        assertTrue(HideFlags.headerHidden(store, "m"));
        assertFalse(HideFlags.textHidden(store, "m"), "hiding the header must not hide the text");
        assertTrue(store.data.containsKey("m#hideHeader"));
        assertFalse(store.data.containsKey("m#hideText"));
    }

    @Test
    void settingHideTextDoesNotAffectHideHeader() {
        HideFlags.setTextHidden(store, "m", true);
        assertTrue(HideFlags.textHidden(store, "m"));
        assertFalse(HideFlags.headerHidden(store, "m"));
    }
}
