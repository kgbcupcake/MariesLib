package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * A module's own Background/Border opacity and shade, self-contained in the module's {@link
 * PersistenceProvider} store exactly like {@link ModuleScales} — for a module with no background/
 * border color of its own in its host's config to bind {@code opacity}/{@code backgroundShade}/
 * {@code borderOpacity}/{@code borderShade} to (see {@link dev.marie.framework.ui.api.StandardPanelBuilder#withOwnStyle}).
 * Opacity is 0..1 (1.0 = unchanged); shade is -1..1 (0.0 = unchanged, negative darker, positive
 * lighter) — see {@link dev.marie.framework.ui.api.MarieModuleSettings#styledBackground}/{@link
 * dev.marie.framework.ui.api.MarieModuleSettings#styledBorder} for how they're applied.
 */
@ApiStatus.Internal
public final class ModuleStyle {

    private static final String BG_OPACITY_SUFFIX = "#styleBgOpacity";
    private static final String BG_SHADE_SUFFIX = "#styleBgShade";
    private static final String BORDER_OPACITY_SUFFIX = "#styleBorderOpacity";
    private static final String BORDER_SHADE_SUFFIX = "#styleBorderShade";

    private ModuleStyle() {}

    public static double backgroundOpacity(PersistenceProvider p, String panelId) {
        return p.load(panelId + BG_OPACITY_SUFFIX).map(ComponentState::contentScale).orElse(1.0d);
    }

    public static void setBackgroundOpacity(PersistenceProvider p, String panelId, double value) {
        save(p, panelId, BG_OPACITY_SUFFIX, value);
    }

    public static double backgroundShade(PersistenceProvider p, String panelId) {
        return p.load(panelId + BG_SHADE_SUFFIX).map(ComponentState::contentScale).orElse(0.0d);
    }

    public static void setBackgroundShade(PersistenceProvider p, String panelId, double value) {
        save(p, panelId, BG_SHADE_SUFFIX, value);
    }

    public static double borderOpacity(PersistenceProvider p, String panelId) {
        return p.load(panelId + BORDER_OPACITY_SUFFIX).map(ComponentState::contentScale).orElse(1.0d);
    }

    public static void setBorderOpacity(PersistenceProvider p, String panelId, double value) {
        save(p, panelId, BORDER_OPACITY_SUFFIX, value);
    }

    public static double borderShade(PersistenceProvider p, String panelId) {
        return p.load(panelId + BORDER_SHADE_SUFFIX).map(ComponentState::contentScale).orElse(0.0d);
    }

    public static void setBorderShade(PersistenceProvider p, String panelId, double value) {
        save(p, panelId, BORDER_SHADE_SUFFIX, value);
    }

    /** Clears the module's own Background/Border opacity and shade, for a "reset this whole module" action. */
    public static void reset(PersistenceProvider p, String panelId) {
        p.remove(panelId + BG_OPACITY_SUFFIX);
        p.remove(panelId + BG_SHADE_SUFFIX);
        p.remove(panelId + BORDER_OPACITY_SUFFIX);
        p.remove(panelId + BORDER_SHADE_SUFFIX);
    }

    private static void save(PersistenceProvider p, String panelId, String suffix, double value) {
        p.save(panelId + suffix,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }
}
