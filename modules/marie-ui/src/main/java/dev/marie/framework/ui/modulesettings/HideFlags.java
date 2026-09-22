package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * Per-module "hide" switches, stored like {@link MoveFlags} (one boolean per flag id in the module's own
 * {@link PersistenceProvider}): icons, bars, text and the whole window. {@link ModuleRenderContext} enforces
 * all four for every module drawn through {@code MarieModuleSettings.withDisplaySettings}; a module that draws
 * some of its content some other way (raw {@code fillRect} pips, a hand-rolled HUD row, ...) checks the
 * matching getter itself, the same way icons already had to be checked by such modules before bars/text/window
 * existed here.
 */
@ApiStatus.Internal
public final class HideFlags {

    private static final String ICONS_SUFFIX = "#hideIcons";
    private static final String BARS_SUFFIX = "#hideBars";
    private static final String TEXT_SUFFIX = "#hideText";
    private static final String WINDOW_SUFFIX = "#hideWindow";

    private HideFlags() {}

    public static boolean iconsHidden(PersistenceProvider persistence, String panelId) {
        return isSet(persistence, panelId, ICONS_SUFFIX);
    }

    public static void setIconsHidden(PersistenceProvider persistence, String panelId, boolean hidden) {
        set(persistence, panelId, ICONS_SUFFIX, hidden);
    }

    public static boolean barsHidden(PersistenceProvider persistence, String panelId) {
        return isSet(persistence, panelId, BARS_SUFFIX);
    }

    public static void setBarsHidden(PersistenceProvider persistence, String panelId, boolean hidden) {
        set(persistence, panelId, BARS_SUFFIX, hidden);
    }

    public static boolean textHidden(PersistenceProvider persistence, String panelId) {
        return isSet(persistence, panelId, TEXT_SUFFIX);
    }

    public static void setTextHidden(PersistenceProvider persistence, String panelId, boolean hidden) {
        set(persistence, panelId, TEXT_SUFFIX, hidden);
    }

    public static boolean windowHidden(PersistenceProvider persistence, String panelId) {
        return isSet(persistence, panelId, WINDOW_SUFFIX);
    }

    public static void setWindowHidden(PersistenceProvider persistence, String panelId, boolean hidden) {
        set(persistence, panelId, WINDOW_SUFFIX, hidden);
    }

    private static boolean isSet(PersistenceProvider persistence, String panelId, String suffix) {
        return persistence.load(panelId + suffix).map(ComponentState::collapsed).orElse(false);
    }

    private static void set(PersistenceProvider persistence, String panelId, String suffix, boolean hidden) {
        persistence.save(panelId + suffix, new ComponentState(0, 0, 0, 0, hidden, false, false, 0));
    }
}
