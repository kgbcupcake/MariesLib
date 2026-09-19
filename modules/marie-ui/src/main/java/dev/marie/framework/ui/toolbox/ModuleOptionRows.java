package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.modulesettings.ModuleOffsets;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import net.minecraft.network.chat.Component;

/**
 * Ready-made rows for the per-module display settings every HUD-style module box shares — padding,
 * independent text and icon size, and the two move modes — over the module's own {@link
 * PersistenceProvider} store. Each setter saves through the provider immediately, so the rows have
 * nothing left to do on commit.
 */
@ApiStatus.Internal
public final class ModuleOptionRows {

    /** Same step {@code ScaleConfigPanel} uses per scroll notch for its scale sliders. */
    private static final double SCALE_STEP = 0.05d;
    private static final Runnable ALREADY_SAVED = () -> {};

    private ModuleOptionRows() {}

    public static void addPadding(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new SliderOption(text("config.marieslib.scaleconfig.padding"),
                () -> ModuleScales.paddingScale(p, id), v -> ModuleScales.setPaddingScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED));
    }

    /** A single Text Scale slider for modules with no separate icon size (the classic scale-config window's row). */
    public static void addTextScale(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new SliderOption(text("config.marieslib.scaleconfig.textScale"),
                () -> ModuleScales.textScale(p, id), v -> ModuleScales.setContentScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED));
    }

    /** Just the "Move Text and Icons" toggle, without "Move Bars". */
    public static void addMoveTextToggle(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new ToggleOption(text("config.marieslib.scaleconfig.moveContent"),
                () -> MoveFlags.isOn(p, id), v -> MoveFlags.set(p, id, v), ALREADY_SAVED));
    }

    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new SliderOption(text("config.marieslib.moduleoptions.textSize"),
                () -> ModuleScales.textScale(p, id), v -> ModuleScales.setTextScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED));
        layout.addRow(new SliderOption(text("config.marieslib.moduleoptions.iconSize"),
                () -> ModuleScales.iconScale(p, id), v -> ModuleScales.setIconScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED));
    }

    /** Bar size slider (bar length/thickness and the value text at its end). */
    public static void addBarSize(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new SliderOption(text("config.marieslib.moduleoptions.barSize"),
                () -> ModuleScales.barScale(p, id), v -> ModuleScales.setBarScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED));
    }

    /** "Move Text", "Move Icons" and "Move Bars" toggles; turning any on turns the other two off (one drag mode at a time). */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id) {
        String[] flags = {id, ModuleOffsets.moveIconsFlagId(id), ModuleOffsets.moveBarsFlagId(id)};
        String[] labels = {"config.marieslib.moduleoptions.moveText", "config.marieslib.moduleoptions.moveIcons",
                "config.marieslib.moduleoptions.moveBars"};
        for (int i = 0; i < flags.length; i++) {
            String own = flags[i];
            layout.addRow(new ToggleOption(text(labels[i]), () -> MoveFlags.isOn(p, own), v -> {
                MoveFlags.set(p, own, v);
                if (v) {
                    for (String other : flags) {
                        if (!other.equals(own)) {
                            MoveFlags.set(p, other, false);
                        }
                    }
                }
            }, ALREADY_SAVED));
        }
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
