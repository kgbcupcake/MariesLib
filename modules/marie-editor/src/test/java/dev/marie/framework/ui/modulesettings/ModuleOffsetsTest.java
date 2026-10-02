package dev.marie.framework.ui.modulesettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Characterizes the in-memory-until-commit behavior of the move offsets and the flag id conventions. */
class ModuleOffsetsTest {

    @Test
    void offsetsStartAtZeroAndAreOnlyPersistedOnCommit() {
        InMemoryStore store = new InMemoryStore();
        assertEquals(0, ModuleOffsets.barX(store, "a"));

        ModuleOffsets.setBar(store, "a", 12, -7);
        assertEquals(12, ModuleOffsets.barX(store, "a"));
        assertEquals(-7, ModuleOffsets.barY(store, "a"));
        assertEquals(0, store.saves, "a drag preview must not touch the store");
        assertNull(store.data.get("a#barOffset"));

        ModuleOffsets.commitBar(store, "a");
        assertEquals(1, store.saves);
        assertEquals(12, store.data.get("a#barOffset").x());
        assertEquals(-7, store.data.get("a#barOffset").y());
    }

    @Test
    void textIconAndBarOffsetsAreIndependent() {
        InMemoryStore store = new InMemoryStore();
        ModuleOffsets.setText(store, "b", 1, 2);
        ModuleOffsets.setIcon(store, "b", 3, 4);
        ModuleOffsets.setBar(store, "b", 5, 6);
        assertEquals(1, ModuleOffsets.textX(store, "b"));
        assertEquals(4, ModuleOffsets.iconY(store, "b"));
        assertEquals(5, ModuleOffsets.barX(store, "b"));

        ModuleOffsets.commitIcon(store, "b");
        assertEquals(3, store.data.get("b#iconOffset").x());
        assertNull(store.data.get("b#textOffset"));
        assertNull(store.data.get("b#barOffset"));
    }

    @Test
    void aFreshProviderLoadsWhatWasCommittedAndProvidersAreIsolated() {
        InMemoryStore first = new InMemoryStore();
        ModuleOffsets.setIcon(first, "c", 9, 9);
        ModuleOffsets.commitIcon(first, "c");

        InMemoryStore second = new InMemoryStore();
        second.data.putAll(first.data);          // same saved file, new session
        assertEquals(9, ModuleOffsets.iconX(second, "c"));

        InMemoryStore other = new InMemoryStore();
        assertEquals(0, ModuleOffsets.iconX(other, "c"), "a different store has its own offsets");
    }

    @Test
    void flagIdsFollowTheDocumentedSuffixes() {
        assertEquals("p.bars", ModuleOffsets.moveBarsFlagId("p"));
        assertEquals("p.icons", ModuleOffsets.moveIconsFlagId("p"));
        assertEquals("p.all", ModuleOffsets.moveAllFlagId("p"));
        assertNotEquals(ModuleOffsets.moveBarsFlagId("p"), ModuleOffsets.moveIconsFlagId("p"));
    }
}
