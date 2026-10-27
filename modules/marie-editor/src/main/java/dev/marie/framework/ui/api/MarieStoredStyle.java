package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.color.MarieColors;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.ModuleStyle;

/**
 * Store-backed screen-wide style: background opacity and text/icon brightness kept in a module
 * {@link PersistenceProvider} instead of a mod config file, so every {@link ModuleProfile} gets its own
 * copy for free. The options-panel side is {@link StandardPanelBuilder#storedOpacity} and
 * {@link StandardPanelBuilder#storedBrightness}; this is the render side.
 *
 * <pre>{@code
 * int bg = MarieStoredStyle.applyOpacity(baseArgb, store, SCREEN_ID, 0.8);
 * RenderContext lit = MarieStoredStyle.withBrightness(context, store, SCREEN_ID);
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieStoredStyle {

    private MarieStoredStyle() {}

    /** Background opacity (0..1) saved for {@code panelId}, or {@code defaultValue} until set. */
    public static double backgroundOpacity(PersistenceProvider store, String panelId, double defaultValue) {
        return ModuleStyle.storedOpacity(store, panelId, defaultValue);
    }

    public static void setBackgroundOpacity(PersistenceProvider store, String panelId, double value) {
        ModuleStyle.setStoredOpacity(store, panelId, Math.max(0.0d, Math.min(1.0d, value)));
    }

    /** {@code rgb} with its alpha replaced by the stored opacity. */
    public static int applyOpacity(int rgb, PersistenceProvider store, String panelId, double defaultValue) {
        return MarieColors.withOpacity(rgb, backgroundOpacity(store, panelId, defaultValue));
    }

    /** Text brightness saved for {@code panelId} (1.0 until set). Same value {@link MarieModuleSettings#textBrightness} reads. */
    public static double textBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.textBrightness(store, panelId);
    }

    public static void setTextBrightness(PersistenceProvider store, String panelId, double value) {
        ModuleScales.setTextBrightness(store, panelId, clampBrightness(value));
    }

    public static double iconBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.iconBrightness(store, panelId);
    }

    public static void setIconBrightness(PersistenceProvider store, String panelId, double value) {
        ModuleScales.setIconBrightness(store, panelId, clampBrightness(value));
    }

    /** {@code context} drawing text and icons at {@code panelId}'s stored brightness; {@code context} itself at 1.0. */
    public static RenderContext withBrightness(RenderContext context, PersistenceProvider store, String panelId) {
        return MarieModuleSettings.withBrightness(context, textBrightness(store, panelId), iconBrightness(store, panelId));
    }

    private static double clampBrightness(double value) {
        return Math.max(MarieModuleSettings.MIN_BRIGHTNESS, Math.min(MarieModuleSettings.MAX_BRIGHTNESS, value));
    }
}
