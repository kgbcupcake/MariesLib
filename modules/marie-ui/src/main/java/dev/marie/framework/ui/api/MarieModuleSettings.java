package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.modulesettings.BrightnessRenderContext;
import dev.marie.framework.ui.modulesettings.HideFlags;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.modulesettings.ModuleExtents;
import dev.marie.framework.ui.modulesettings.ModuleOffsets;
import dev.marie.framework.ui.modulesettings.ModuleRenderContext;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Public facade for the display settings a HUD-style module box exposes — independent text and icon
 * size, padding, separately movable bars, and independent text/icon brightness — covering both halves:
 * the read side a module's render code calls each frame, and a ready-made options panel for the edit UI.
 *
 * <p>UI-state settings (sizes, padding, move modes, bar offset) live in the {@link PersistenceProvider}
 * the caller passes, under its own {@code panelId}; nothing here owns storage. Brightness and
 * background opacity are the caller's own values (typically config fields), bound through getters and
 * setters, so they stay in the caller's config file. Brightness is expected to accept
 * {@link #MIN_BRIGHTNESS}..{@link #MAX_BRIGHTNESS}, opacity 0..1.
 *
 * <pre>{@code
 * // render side
 * float textScale = (float) MarieModuleSettings.textScale(store, PANEL_ID);
 * float iconScale = (float) MarieModuleSettings.iconScale(store, PANEL_ID);
 * int barDx = MarieModuleSettings.barOffsetX(store, PANEL_ID);
 * drawPanel(MarieModuleSettings.withBrightness(context, cfg.textBrightness(), cfg.iconBrightness()));
 *
 * // edit UI
 * MarieComponent box = MarieModuleSettings.standardPanel("My HUD", store, PANEL_ID)
 *         .opacity(cfg::opacity, cfg::setOpacity)
 *         .textBrightness(cfg::textBrightness, cfg::setTextBrightness)
 *         .iconBrightness(cfg::iconBrightness, cfg::setIconBrightness)
 *         .onCommit(cfg::save)
 *         .build();
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieModuleSettings {

    /** Lowest and highest brightness the options panel offers (1.0 = unchanged). */
    public static final double MIN_BRIGHTNESS = 0.2d;
    public static final double MAX_BRIGHTNESS = 2.0d;

    private MarieModuleSettings() {}

    /** Text size multiplier for {@code panelId} (the module's persisted {@code contentScale}). */
    public static double textScale(PersistenceProvider store, String panelId) {
        return ModuleScales.textScale(store, panelId);
    }

    /** Icon size multiplier; equal to {@link #textScale} until an icon size has been set, so modules that predate the split look unchanged. */
    public static double iconScale(PersistenceProvider store, String panelId) {
        return ModuleScales.iconScale(store, panelId);
    }

    /** Bar size multiplier for {@code panelId}: bar length and thickness, and the value text at the bar's end. 1.0 until set. */
    public static double barScale(PersistenceProvider store, String panelId) {
        return ModuleScales.barScale(store, panelId);
    }

    /** Text brightness a module keeps in its own store (1.0 until set) — see {@link MarieToolbox.PanelBuilder#storedBrightness}. */
    public static double textBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.textBrightness(store, panelId);
    }

    public static double iconBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.iconBrightness(store, panelId);
    }

    /** Where the module's text sits relative to its default place, for a module that keeps that offset here instead of in its own storage. */
    public static int textOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.textX(store, panelId);
    }

    public static int textOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.textY(store, panelId);
    }

    /** Live drag preview of the text offset; call {@link #commitTextOffset} when the drag ends. */
    public static void setTextOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setText(store, panelId, x, y);
    }

    public static void commitTextOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitText(store, panelId);
    }

    /**
     * {@code context} with everything {@code panelId} keeps in {@code store} applied to what a module draws
     * through it: its text and icon offsets, its icon size relative to its text size, and its stored text
     * and icon brightness. For a module whose renderer scales text and icons together by its text size and
     * draws them through a {@link RenderContext}; {@code context} itself when every setting is default.
     */
    public static RenderContext withDisplaySettings(RenderContext context, PersistenceProvider store, String panelId) {
        return ModuleRenderContext.wrap(context, store, panelId);
    }

    /** Where the module's icons sit relative to their default place. In memory; cheap to call every frame. */
    public static int iconOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.iconX(store, panelId);
    }

    public static int iconOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.iconY(store, panelId);
    }

    /** Live drag preview of the icon offset; call {@link #commitIconOffset} when the drag ends. */
    public static void setIconOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setIcon(store, panelId, x, y);
    }

    public static void commitIconOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitIcon(store, panelId);
    }

    /**
     * Where the module last drew the part {@code mode} moves (text, header, icons, bars — or all four for {@link
     * MoveDrag.Mode#ALL}), in screen coordinates and after its offsets, for an edit screen to outline just that
     * part; {@code null} if the module drew none of it in its latest render (fall back to the whole box). Text,
     * icons and bars are recorded automatically for a module that draws through {@link #withDisplaySettings};
     * a header drawn with its own offset (a module with {@link StandardPanelBuilder#withHeader}) is a separate
     * draw call the module must report itself with {@link #recordHeaderExtent}, the same way {@link
     * #recordBarExtent} covers bars a module draws some other way.
     */
    public static Bounds moveOutline(PersistenceProvider store, String panelId, MoveDrag.Mode mode) {
        return switch (mode) {
            case TEXT -> ModuleExtents.of(store, panelId, ModuleExtents.Kind.TEXT);
            case HEADER -> ModuleExtents.of(store, panelId, ModuleExtents.Kind.HEADER);
            case ICONS -> ModuleExtents.of(store, panelId, ModuleExtents.Kind.ICON);
            case BARS -> ModuleExtents.of(store, panelId, ModuleExtents.Kind.BAR);
            case ALL -> ModuleExtents.all(store, panelId);
        };
    }

    /**
     * Records that the module drew part of its bars at this screen rectangle (after its offsets), for a module whose "bars"
     * are not drawn through {@link RenderContext#drawBar} (e.g. list rows), so {@link #moveOutline} can outline them.
     * Call after {@link #withDisplaySettings} in the same render.
     */
    public static void recordBarExtent(PersistenceProvider store, String panelId, int x, int y, int width, int height) {
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.BAR, x, y, width, height);
    }

    /**
     * Records that the module drew its header at this screen rectangle (after {@link #headerOffsetX}/{@link
     * #headerOffsetY}), for a module with {@link StandardPanelBuilder#withHeader} whose header is a separate draw
     * call from the rest of its text (so it isn't already covered by {@link #withDisplaySettings}'s own text
     * recording), so {@link #moveOutline}'s {@link MoveDrag.Mode#HEADER}/{@link MoveDrag.Mode#ALL} can outline it.
     * Call after {@link #withDisplaySettings} in the same render.
     */
    public static void recordHeaderExtent(PersistenceProvider store, String panelId, int x, int y, int width, int height) {
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.HEADER, x, y, width, height);
    }

    /** Whether the "Move All" toggle is on (one drag moves text, icons and bars together). */
    public static boolean isMoveAllEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveAllFlagId(panelId));
    }

    /** Whether the module's "Hide Icons" toggle is on. {@link #withDisplaySettings} already skips icon draws; this is for a host that lays out or draws icons some other way. */
    public static boolean isIconsHidden(PersistenceProvider store, String panelId) {
        return HideFlags.iconsHidden(store, panelId);
    }

    /** Whether the module's "Hide Bars" toggle is on. {@link #withDisplaySettings} already skips {@code drawBar}/{@code drawVerticalBar} calls; this is for a host whose "bars" are drawn some other way (e.g. plain {@code fillRect} pips, or rows in a hand-rolled HUD). */
    public static boolean isBarsHidden(PersistenceProvider store, String panelId) {
        return HideFlags.barsHidden(store, panelId);
    }

    /** Whether the module's "Hide Text" toggle is on. {@link #withDisplaySettings} already skips {@code drawText} calls; this is for a host that draws text some other way. */
    public static boolean isTextHidden(PersistenceProvider store, String panelId) {
        return HideFlags.textHidden(store, panelId);
    }

    /** Whether the "Move Icons" toggle is on. */
    public static boolean isMoveIconsEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveIconsFlagId(panelId));
    }

    /** Where the module's bars (and their value text) sit relative to their default place. In memory; cheap to call every frame. */
    public static int barOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.barX(store, panelId);
    }

    public static int barOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.barY(store, panelId);
    }

    /** Live drag preview of the bar offset; call {@link #commitBarOffset} when the drag ends. */
    public static void setBarOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setBar(store, panelId, x, y);
    }

    public static void commitBarOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitBar(store, panelId);
    }

    /** Whether the "Move Bars" toggle is on — a host polls this to route dragging to the bar offset. */
    public static boolean isMoveBarsEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveBarsFlagId(panelId));
    }

    /** Where the module's header sits relative to its default place. In memory; cheap to call every frame. */
    public static int headerOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.headerX(store, panelId);
    }

    public static int headerOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.headerY(store, panelId);
    }

    /** Live drag preview of the header offset; call {@link #commitHeaderOffset} when the drag ends. */
    public static void setHeaderOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setHeader(store, panelId, x, y);
    }

    public static void commitHeaderOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitHeader(store, panelId);
    }

    /** Whether the "Move Header" toggle is on. */
    public static boolean isMoveHeaderEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveHeaderFlagId(panelId));
    }

    /** Whether the "Move Text" toggle is on. */
    public static boolean isMoveTextEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, panelId);
    }

    /** The move mode currently switched on (at most one is), or {@code null} if none — for a host routing dragging to the matching offset. */
    public static MoveDrag.Mode activeMoveMode(PersistenceProvider store, String panelId) {
        if (isMoveAllEnabled(store, panelId)) {
            return MoveDrag.Mode.ALL;
        }
        if (isMoveTextEnabled(store, panelId)) {
            return MoveDrag.Mode.TEXT;
        }
        if (isMoveIconsEnabled(store, panelId)) {
            return MoveDrag.Mode.ICONS;
        }
        if (isMoveHeaderEnabled(store, panelId)) {
            return MoveDrag.Mode.HEADER;
        }
        return isMoveBarsEnabled(store, panelId) ? MoveDrag.Mode.BARS : null;
    }

    /** {@code context} whose text and item icons are drawn at the given brightness multipliers; {@code context} itself when both are 1.0. */
    public static RenderContext withBrightness(RenderContext context, double textBrightness, double iconBrightness) {
        return BrightnessRenderContext.wrap(context, textBrightness, iconBrightness);
    }

    /** {@code argb} with RGB scaled by {@code brightness} (alpha untouched, capped at 255). */
    public static int scaleBrightness(int argb, double brightness) {
        return BrightnessRenderContext.scale(argb, brightness);
    }

    /**
     * Starts the standard options panel every module window shares: Layout (Padding), Behavior (collapsible Move group:
     * Move Text/Icons/Bars/All and Reset Positions; collapsible Hide group: Hide Icons) and Style, whose collapsible
     * groups are Sizes (Text/Icon/Bar size), Brightness, Background (opacity, shade) and Border (opacity, shade) — a
     * group appears only when you bind something for it. Consumer-specific rows go in through {@code layoutRows},
     * {@code behaviorRows}, {@code styleRows} and {@code extraTabs}, so no module hand-builds its own layout.
     */
    public static StandardPanelBuilder standardPanel(String title, PersistenceProvider store, String panelId) {
        return new StandardPanelBuilder(title, store, panelId);
    }


}
