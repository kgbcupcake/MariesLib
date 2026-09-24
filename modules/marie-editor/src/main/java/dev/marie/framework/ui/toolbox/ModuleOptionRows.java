package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.modulesettings.HideFlags;
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
    private static final String MOVE_SECTION = "move";
    private static final String HIDE_SECTION = "hide";

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
        addSizes(layout, p, id, null);
    }

    /**
     * Same, but with the Text size row's label overridden to {@code textLabelKey} ({@code null}: the
     * standard "Text size" label) — for a module whose persisted text scale only ever drives one
     * specific part (e.g. just its header, with everything else it draws following Bar size instead),
     * where the generic "Text size" label would misleadingly suggest it resizes the module's body too.
     */
    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id, String textLabelKey) {
        addSizes(layout, p, id, textLabelKey, true);
    }

    /**
     * Same, but {@code showIconSize} false leaves out the Icon size row entirely — for a module with
     * no separate icon size to adjust. When it's left out and {@code textLabelKey} is {@code null},
     * the remaining slider is labeled "Size" instead of "Text size", since it's then the module's only
     * size control.
     */
    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id, String textLabelKey, boolean showIconSize) {
        addSizes(layout, p, id, textLabelKey, true, showIconSize);
    }

    /**
     * Same, but {@code showTextSize} false additionally leaves out the Text size row itself — for a
     * module whose persisted text scale no longer drives anything of its own (e.g. its only text moved
     * onto a separate Header size slider — see {@link #addHeaderSize}), while it still keeps an
     * independent icon size. {@code showTextSize} and {@code showIconSize} false together leaves out
     * this whole group (the caller should skip calling this at all in that case).
     */
    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id, String textLabelKey, boolean showTextSize, boolean showIconSize) {
        addSizes(layout, p, id, textLabelKey, showTextSize, showIconSize, true);
    }

    /**
     * Same, but {@code iconFollowsText} false makes the icon size fully independent from the start:
     * its Text size row (if shown) never auto-pins icon size on its first edit, and its Icon size row
     * (and any render code reading {@link ModuleScales#iconScale(PersistenceProvider, String, boolean)}
     * the same way) never falls back to the text size while unset — see {@link
     * dev.marie.framework.ui.api.StandardPanelBuilder#independentIconSize} for when to reach for this.
     */
    public static void addSizes(OptionLayout layout, PersistenceProvider p, String id, String textLabelKey, boolean showTextSize, boolean showIconSize, boolean iconFollowsText) {
        if (showTextSize) {
            String defaultLabelKey = showIconSize ? "config.marieslib.moduleoptions.textSize" : "config.marieslib.moduleoptions.size";
            // iconFollowsText=false calls setContentScale directly — bypassing setTextScale's pinIcon
            // branch entirely, rather than just passing it a parameter that disables the branch — so
            // this row's setter has zero shared code with the icon row's own getter/setter below. Two
            // functions that happen to both leave the icon key untouched is a weaker guarantee than
            // two functions that share no code at all.
            SliderOption textSize = new SliderOption(text(textLabelKey != null ? textLabelKey : defaultLabelKey),
                    () -> ModuleScales.textScale(p, id),
                    iconFollowsText
                            ? v -> ModuleScales.setTextScale(p, id, v, true)
                            : v -> ModuleScales.setContentScale(p, id, v),
                    ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, SCALE_STEP, ALREADY_SAVED);
            // Reset without pinning the icon size, so the icon size can be reset (follow the text again) independently.
            textSize.resetWith(() -> ModuleScales.setContentScale(p, id, 1.0d));
            layout.addRow(textSize);
        }
        if (!showIconSize) {
            return;
        }
        SliderOption iconSize = new SliderOption(text("config.marieslib.moduleoptions.iconSize"),
                () -> ModuleScales.iconScale(p, id, iconFollowsText), v -> ModuleScales.setIconScale(p, id, v),
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

    /**
     * Header size slider (a module's title/header text, independent of Text/Icon/Bar size) — for a module with
     * {@link dev.marie.framework.ui.api.StandardPanelBuilder#withHeaderSize}. {@code labelKey} {@code null}: the
     * standard "Header size" label.
     */
    public static void addHeaderSize(OptionLayout layout, PersistenceProvider p, String id, String labelKey) {
        addSlider(layout, new SliderOption(text(labelKey != null ? labelKey : "config.marieslib.moduleoptions.headerSize"),
                () -> ModuleScales.headerScale(p, id), v -> ModuleScales.setHeaderScale(p, id, v),
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

    /** Puts the toggles in a collapsible "Move" group (and, when {@code icons}, a "Hide" group after it). Chooses which toggles appear: "Move Bars", "Move Icons" and "Move Header" are optional; "Move Text" and "Move All" always are. */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header) {
        addMoveToggles(layout, p, id, bars, icons, header, true);
    }

    /** Same, but {@code moveText} false also leaves out "Move Text" — for a module whose body content moves under some other toggle here (e.g. "Move Bars") and has nothing left for "Move Text" to actually move. */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header, boolean moveText) {
        addMoveToggles(layout, p, id, bars, icons, header, moveText, true);
    }

    /** Same, but {@code hideText} false also leaves out "Hide Text" — for a module whose text serves no purpose hiding on its own (e.g. it's purely decorative, or hiding the whole module already covers it). */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header, boolean moveText, boolean hideText) {
        addMoveToggles(layout, p, id, bars, icons, header, moveText, hideText, false);
    }

    /** Same, but {@code hideHeader} additionally adds "Hide Header" — independent of {@code hideText} — for a module with a title/header separate from its body text that should be hideable on its own. */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header, boolean moveText, boolean hideText, boolean hideHeader) {
        addMoveToggles(layout, p, id, bars, icons, header, moveText, hideText, hideHeader, false);
    }

    /**
     * Same, but {@code iconInner} additionally adds "Move Icon" — independent of {@code icons}' "Move
     * Icons" — for a module whose icon draws inside its own small box (e.g. {@code BarRowComponent})
     * and wants the icon draggable within that box without moving the box itself. Read back with
     * {@link dev.marie.framework.ui.api.MarieModuleSettings#isMoveIconInnerEnabled}; the offset it
     * drives is {@link dev.marie.framework.ui.api.MarieModuleSettings#iconInnerOffsetX}.
     */
    public static void addMoveToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean header, boolean moveText, boolean hideText, boolean hideHeader, boolean iconInner) {
        List<String> flagList = new ArrayList<>();
        List<String> labelList = new ArrayList<>();
        if (moveText) {
            flagList.add(id);
            labelList.add("config.marieslib.moduleoptions.moveText");
        }
        if (header) {
            flagList.add(ModuleOffsets.moveHeaderFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveHeader");
        }
        if (icons) {
            flagList.add(ModuleOffsets.moveIconsFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveIcons");
        }
        if (iconInner) {
            flagList.add(ModuleOffsets.moveIconInnerFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveIcon");
        }
        if (bars) {
            flagList.add(ModuleOffsets.moveBarsFlagId(id));
            labelList.add("config.marieslib.moduleoptions.moveBars");
        }
        flagList.add(ModuleOffsets.moveAllFlagId(id));
        labelList.add("config.marieslib.moduleoptions.moveAll");
        String[] flags = flagList.toArray(new String[0]);
        String[] labels = labelList.toArray(new String[0]);
        SectionRow moveSection = layout.section(MOVE_SECTION, text("config.marieslib.moduleoptions.section.move"));
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
            moveSection.add(toggle);
        }
        addHideToggles(layout, p, id, bars, icons, hideText, hideHeader);
    }

    /**
     * The collapsible "Hide" group: "Hide Window" (always), "Hide Text", "Hide Header", "Hide Icons" and "Hide
     * Bars" (each optional, "Hide Text"/"Hide Header" mirroring the Move group's "Move Text"/"Move Header" above)
     * — Text/Icons/Bars/Window enforced for every module by {@code ModuleRenderContext}; Header is always the
     * module's own responsibility to check (see {@link dev.marie.framework.ui.api.MarieModuleSettings#isHeaderHidden}).
     */
    private static void addHideToggles(OptionLayout layout, PersistenceProvider p, String id, boolean bars, boolean icons, boolean showText, boolean showHeader) {
        SectionRow hideSection = layout.section(HIDE_SECTION, text("config.marieslib.moduleoptions.section.hide"));
        if (showText) {
            ToggleOption hideText = new ToggleOption(text("config.marieslib.moduleoptions.hideText"),
                    () -> HideFlags.textHidden(p, id), v -> HideFlags.setTextHidden(p, id, v), ALREADY_SAVED);
            hideText.defaultTo(false);
            hideSection.add(hideText);
        }
        if (showHeader) {
            ToggleOption hideHeader = new ToggleOption(text("config.marieslib.moduleoptions.hideHeader"),
                    () -> HideFlags.headerHidden(p, id), v -> HideFlags.setHeaderHidden(p, id, v), ALREADY_SAVED);
            hideHeader.defaultTo(false);
            hideSection.add(hideHeader);
        }
        if (icons) {
            ToggleOption hideIcons = new ToggleOption(text("config.marieslib.moduleoptions.hideIcons"),
                    () -> HideFlags.iconsHidden(p, id), v -> HideFlags.setIconsHidden(p, id, v), ALREADY_SAVED);
            hideIcons.defaultTo(false);
            hideSection.add(hideIcons);
        }
        if (bars) {
            ToggleOption hideBars = new ToggleOption(text("config.marieslib.moduleoptions.hideBars"),
                    () -> HideFlags.barsHidden(p, id), v -> HideFlags.setBarsHidden(p, id, v), ALREADY_SAVED);
            hideBars.defaultTo(false);
            hideSection.add(hideBars);
        }
        ToggleOption hideWindow = new ToggleOption(text("config.marieslib.moduleoptions.hideWindow"),
                () -> HideFlags.windowHidden(p, id), v -> HideFlags.setWindowHidden(p, id, v), ALREADY_SAVED);
        hideWindow.defaultTo(false);
        hideSection.add(hideWindow);
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
        layout.section(MOVE_SECTION, text("config.marieslib.moduleoptions.section.move")).add(new ButtonOption(text("config.marieslib.moduleoptions.resetPositions"), "RESET", () -> {
            resetOffsetsAndMoveFlags(p, id);
            hostReset.run();
        }, ALREADY_SAVED));
    }

    /**
     * "Reset Module" button: everything {@link #addResetPositions} does (content offsets, move
     * modes), plus the module's own drag/resize position and size, its icon/bar size and text/icon
     * brightness, and every option on every tab that has a default (sizes, background/border, colors
     * — anything filed as an {@link OptionRow} via {@link OptionLayout#allRows}) — the single
     * consolidated reset a module's panel exposes in place of separate per-tab "Reset This Tab"/
     * "Reset Positions" buttons.
     */
    public static void addResetEverything(OptionLayout layout, PersistenceProvider p, String id, Runnable hostReset) {
        layout.addRow(new ButtonOption(text("config.marieslib.moduleoptions.resetModule"), "RESET", () -> {
            resetOffsetsAndMoveFlags(p, id);
            ModuleScales.resetSizesAndBrightness(p, id);
            // Bare panelId key: the module's own drag/resize ComponentState (position, size, and —
            // as fields on that same record — text size and padding), all wiped in one call so it
            // falls back to its natural default on the very next read.
            p.remove(id);
            for (OptionRow row : new ArrayList<>(layout.allRows())) {
                row.resetToDefault();
            }
            hostReset.run();
        }, ALREADY_SAVED));
    }

    private static void resetOffsetsAndMoveFlags(PersistenceProvider p, String id) {
        ModuleOffsets.setBar(p, id, 0, 0);
        ModuleOffsets.commitBar(p, id);
        ModuleOffsets.setIcon(p, id, 0, 0);
        ModuleOffsets.commitIcon(p, id);
        ModuleOffsets.setIconInner(p, id, 0, 0);
        ModuleOffsets.commitIconInner(p, id);
        ModuleOffsets.setText(p, id, 0, 0);
        ModuleOffsets.commitText(p, id);
        ModuleOffsets.setHeader(p, id, 0, 0);
        ModuleOffsets.commitHeader(p, id);
        for (String flag : new String[]{id, ModuleOffsets.moveIconsFlagId(id), ModuleOffsets.moveIconInnerFlagId(id),
                ModuleOffsets.moveBarsFlagId(id), ModuleOffsets.moveHeaderFlagId(id), ModuleOffsets.moveAllFlagId(id)}) {
            MoveFlags.set(p, flag, false);
        }
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
