package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.modulesettings.ModuleOffsets;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

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
        addSlider(layout, new SliderOption(text("config.marieslib.scaleconfig.padding"),
                () -> ModuleScales.paddingScale(p, id), v -> ModuleScales.setPaddingScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED), 1.0d);
    }

    /** A single Text Scale slider for modules with no separate icon size (the classic scale-config window's row). */
    public static void addTextScale(OptionLayout layout, PersistenceProvider p, String id) {
        addSlider(layout, new SliderOption(text("config.marieslib.scaleconfig.textScale"),
                () -> ModuleScales.textScale(p, id), v -> ModuleScales.setContentScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED), 1.0d);
    }

    /** Just the "Move Text and Icons" toggle, without "Move Bars". */
    public static void addMoveTextToggle(OptionLayout layout, PersistenceProvider p, String id) {
        layout.addRow(new ToggleOption(text("config.marieslib.scaleconfig.moveContent"),
                () -> MoveFlags.isOn(p, id), v -> MoveFlags.set(p, id, v), ALREADY_SAVED));
    }

    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id) {
        SliderOption textSize = new SliderOption(text("config.marieslib.moduleoptions.textSize"),
                () -> ModuleScales.textScale(p, id), v -> ModuleScales.setTextScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED);
        // Reset without pinning the icon size, so the icon size can be reset (follow the text again) independently.
        textSize.resetWith(() -> ModuleScales.setContentScale(p, id, 1.0d));
        layout.addRow(textSize);
        SliderOption iconSize = new SliderOption(text("config.marieslib.moduleoptions.iconSize"),
                () -> ModuleScales.iconScale(p, id), v -> ModuleScales.setIconScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED);
        iconSize.resetWith(() -> ModuleScales.clearIconScale(p, id));
        layout.addRow(iconSize);
    }

    /** Bar size slider (bar length/thickness and the value text at its end). */
    public static void addBarSize(OptionLayout layout, PersistenceProvider p, String id) {
        addSlider(layout, new SliderOption(text("config.marieslib.moduleoptions.barSize"),
                () -> ModuleScales.barScale(p, id), v -> ModuleScales.setBarScale(p, id, v),
                ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED), 1.0d);
    }

    /** "Move Text", "Move Icons", "Move Bars" and "Move All" toggles; turning any on turns the others off. */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id) {
        addMoveToggles(layout, p, id, true);
    }

    /** Same, but {@code bars} false leaves out "Move Bars" (for modules that have no bars). */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars) {
        addMoveToggles(layout, p, id, bars, true, false);
    }

    /** Chooses which toggles appear: "Move Bars", "Move Icons" and "Move Header" are optional; "Move Text" and "Move All" always are. */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header) {
        List<String> flagList = new ArrayList<>();
        List<String> labelList = new ArrayList<>();
        flagList.add(id);
        labelList.add("config.marieslib.moduleoptions.moveText");
        if (header) {
            flagList.add(ModuleOffsets.moveHeaderFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveHeader");
        }
        if (icons) {
            flagList.add(ModuleOffsets.moveIconsFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveIcons");
        }
        if (bars) {
            flagList.add(ModuleOffsets.moveBarsFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveBars");
        }
        flagList.add(ModuleOffsets.moveAllFlagId(id));
        labelList.add("config.marieslib.moduleoptions.moveAll");
        String[] flags = flagList.toArray(new String[0]);
        String[] labels = labelList.toArray(new String[0]);
        for (int i = 0; i < flags.length; i++) {
            String own = flags[i];
            ToggleOption toggle = new ToggleOption(text(labels[i]), () -> MoveFlags.isOn(p, own), v -> {
                MoveFlags.set(p, own, v);
                if (v) {
                    for (String other : flags) {
                        if (!other.equals(own)) {
                            MoveFlags.set(p, other, false);
                        }
                    }
                }
            }, ALREADY_SAVED);
            toggle.defaultTo(false);
            layout.addRow(toggle);
        }
    }

    /** Text and icon brightness sliders over values the module keeps in its own store (see {@link ModuleScales#textBrightness}). */
    public static void addStoredBrightness(OptionLayout layout, PersistenceProvider p, String id) {
        addSlider(layout, new SliderOption(text("config.marieslib.moduleoptions.textBrightness"),
                () -> ModuleScales.textBrightness(p, id), v -> ModuleScales.setTextBrightness(p, id, v),
                0.2d, 2.0d, 0.01d, ALREADY_SAVED), 1.0d);
        addSlider(layout, new SliderOption(text("config.marieslib.moduleoptions.iconBrightness"),
                () -> ModuleScales.iconBrightness(p, id), v -> ModuleScales.setIconBrightness(p, id, v),
                0.2d, 2.0d, 0.01d, ALREADY_SAVED), 1.0d);
    }

    /**
     * "Reset Positions" button: puts the module's icon and bar offsets back to zero (saved), switches every
     * move mode off, then runs {@code hostReset} for whatever the host stores itself (typically its own
     * text offset).
     */
    public static void addResetPositions(OptionLayout layout, PersistenceProvider p, String id, Runnable hostReset) {
        layout.addRow(new ButtonOption(text("config.marieslib.moduleoptions.resetPositions"), "RESET", () -> {
            ModuleOffsets.setBar(p, id, 0, 0);
            ModuleOffsets.commitBar(p, id);
            ModuleOffsets.setIcon(p, id, 0, 0);
            ModuleOffsets.commitIcon(p, id);
            ModuleOffsets.setText(p, id, 0, 0);
            ModuleOffsets.commitText(p, id);
            ModuleOffsets.setHeader(p, id, 0, 0);
            ModuleOffsets.commitHeader(p, id);
            for (String flag : new String[]{id, ModuleOffsets.moveIconsFlagId(id), ModuleOffsets.moveBarsFlagId(id),
                    ModuleOffsets.moveHeaderFlagId(id), ModuleOffsets.moveAllFlagId(id)}) {
                MoveFlags.set(p, flag, false);
            }
            hostReset.run();
        }, ALREADY_SAVED));
    }

    private static void addSlider(OptionLayout layout, SliderOption row, double defaultValue) {
        row.defaultTo(defaultValue);
        layout.addRow(row);
    }

    /** "Reset This Tab" button: puts every option on the current tab that has a default back to it. */
    public static void addResetTab(OptionLayout layout) {
        List<OptionRow> rows = layout.currentTabRows();
        layout.addRow(new ButtonOption(text("config.marieslib.moduleoptions.resetTab"), "RESET", () -> {
            for (OptionRow row : new ArrayList<>(rows)) {
                row.resetToDefault();
            }
        }, ALREADY_SAVED));
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
