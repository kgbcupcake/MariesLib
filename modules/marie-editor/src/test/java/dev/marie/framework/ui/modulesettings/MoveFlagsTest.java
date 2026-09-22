package dev.marie.framework.ui.modulesettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the stored key and format of the move-mode flags, which saved profiles depend on. */
class MoveFlagsTest {

    @Test
    void flagsAreStoredUnderHashMoveContentAsTheCollapsedField() {
        InMemoryStore store = new InMemoryStore();
        assertFalse(MoveFlags.isOn(store, "x"));

        MoveFlags.set(store, "x", true);
        assertTrue(MoveFlags.isOn(store, "x"));
        assertTrue(store.data.containsKey("x#moveContent"));
        assertTrue(store.data.get("x#moveContent").collapsed());

        MoveFlags.set(store, "x", false);
        assertFalse(MoveFlags.isOn(store, "x"));
        assertEquals(1, store.data.size());
    }
}
