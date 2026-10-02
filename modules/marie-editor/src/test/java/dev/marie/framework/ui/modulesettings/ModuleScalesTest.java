package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.ui.component.ComponentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes how a module's size/brightness settings are stored and how they relate to each other. */
class ModuleScalesTest {

    private final InMemoryStore store = new InMemoryStore();

    @Test
    void defaultsAreUnchangedAndIconFollowsText() {
        assertEquals(1.0, ModuleScales.textScale(store, "m"));
        assertEquals(1.0, ModuleScales.iconScale(store, "m"));
        assertEquals(1.0, ModuleScales.barScale(store, "m"));
        assertEquals(1.0, ModuleScales.headerScale(store, "m"));
        assertEquals(1.0, ModuleScales.paddingScale(store, "m"));
        assertEquals(1.0, ModuleScales.textBrightness(store, "m"));
        assertEquals(1.0, ModuleScales.iconBrightness(store, "m"));
    }

    @Test
    void headerScaleDefaultsToOneAndDoesNotFollowTextSize() {
        // Unlike icon size, header size has no "follow the text until set" fallback — it mirrors bar
        // size instead, since it's a brand-new independent slider with no legacy shared-scale history.
        ModuleScales.setContentScale(store, "m", 1.5);
        assertEquals(1.0, ModuleScales.headerScale(store, "m"), "header size stays 1.0 even after text size changes");
    }

    @Test
    void headerScaleIsStoredUnderItsOwnKeyInTheContentScaleField() {
        ModuleScales.setHeaderScale(store, "m", 0.9);
        ComponentState saved = store.data.get("m#headerScale");
        assertEquals(0.9, saved.contentScale());
        assertEquals(0.9, ModuleScales.headerScale(store, "m"));
    }

    @Test
    void resetSizesAndBrightnessClearsHeaderScaleToo() {
        ModuleScales.setHeaderScale(store, "m", 0.6);
        ModuleScales.resetSizesAndBrightness(store, "m");
        assertEquals(1.0, ModuleScales.headerScale(store, "m"));
        assertFalse(store.data.containsKey("m#headerScale"));
    }

    @Test
    void iconFollowsTextUntilContentScaleIsWrittenDirectly() {
        ModuleScales.setContentScale(store, "m", 1.5);
        assertEquals(1.5, ModuleScales.textScale(store, "m"));
        assertEquals(1.5, ModuleScales.iconScale(store, "m"), "no separate icon size yet, so it follows the text size");
        assertFalse(store.data.containsKey("m#iconScale"));
    }

    @Test
    void firstTextSizeEditPinsTheIconSizeAtItsCurrentValue() {
        ModuleScales.setContentScale(store, "m", 1.2);       // legacy profile: icons follow 1.2
        ModuleScales.setTextScale(store, "m", 2.0);          // first edit through the split UI

        assertEquals(2.0, ModuleScales.textScale(store, "m"));
        assertEquals(1.2, ModuleScales.iconScale(store, "m"), "icon size stays where it was");
        ModuleScales.setTextScale(store, "m", 0.5);
        assertEquals(1.2, ModuleScales.iconScale(store, "m"), "and stays independent afterwards");
    }

    @Test
    void iconSizeIsStoredUnderItsOwnKeyInTheContentScaleField() {
        ModuleScales.setIconScale(store, "m", 0.8);
        ComponentState saved = store.data.get("m#iconScale");
        assertEquals(0.8, saved.contentScale());
        assertEquals(0.8, ModuleScales.iconScale(store, "m"));
    }

    @Test
    void writingOnePaddingOrTextFieldNeverClobbersTheOthers() {
        store.save("m", new ComponentState(10, 20, 30, 40, false, true, true, 5, 1.5, 2.0));
        ModuleScales.setPaddingScale(store, "m", 3.0);
        ComponentState afterPadding = store.data.get("m");
        assertEquals(new ComponentState(10, 20, 30, 40, false, true, true, 5, 1.5, 3.0), afterPadding);

        ModuleScales.setContentScale(store, "m", 0.7);
        assertEquals(new ComponentState(10, 20, 30, 40, false, true, true, 5, 0.7, 3.0), store.data.get("m"));
    }

    @Test
    void barScaleAndBrightnessHaveTheirOwnKeys() {
        ModuleScales.setBarScale(store, "m", 1.4);
        ModuleScales.setTextBrightness(store, "m", 0.6);
        ModuleScales.setIconBrightness(store, "m", 1.8);
        assertEquals(1.4, store.data.get("m#barScale").contentScale());
        assertEquals(0.6, store.data.get("m#textBrightness").contentScale());
        assertEquals(1.8, store.data.get("m#iconBrightness").contentScale());
        assertTrue(store.data.keySet().stream().noneMatch(k -> k.equals("m")), "the module's own state is untouched");
    }
}
