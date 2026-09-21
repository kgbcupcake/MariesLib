package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * Per-module "hide" switches, stored like {@link MoveFlags} (one boolean per flag id in the module's own
 * {@link PersistenceProvider}). Only icons for now; {@link ModuleRenderContext} enforces it for every module
 * drawn through {@code MarieModuleSettings.withDisplaySettings}.
 */
@ApiStatus.Internal
public final class HideFlags {

    private static final String SUFFIX = "#hideIcons";

    private HideFlags() {}

    public static boolean iconsHidden(PersistenceProvider persistence, String panelId) {
        return persistence.load(panelId + SUFFIX).map(ComponentState::collapsed).orElse(false);
    }

    public static void setIconsHidden(PersistenceProvider persistence, String panelId, boolean hidden) {
        persistence.save(panelId + SUFFIX, new ComponentState(0, 0, 0, 0, hidden, false, false, 0));
    }
}
