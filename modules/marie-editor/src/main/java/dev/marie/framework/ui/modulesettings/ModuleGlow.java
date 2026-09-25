package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * A module's text/border shadow and text/border/bar glow settings, self-contained in the module's
 * own {@link PersistenceProvider} store exactly like {@link ModuleScales} — no per-module config
 * field required. Shadow and glow strength are 0..1 (0 = off, today's unchanged appearance); glow
 * colors are plain RGB ints, packed into the same {@code ComponentState#contentScale} double slot
 * {@link ModuleScales} already reuses for unrelated single-value settings.
 */
@ApiStatus.Internal
public final class ModuleGlow {

    private static final String TEXT_SHADOW_SUFFIX = "#textShadowStrength";
    private static final String BORDER_SHADOW_SUFFIX = "#borderShadowStrength";
    private static final String TEXT_GLOW_COLOR_SUFFIX = "#textGlowColor";
    private static final String TEXT_GLOW_STRENGTH_SUFFIX = "#textGlowStrength";
    private static final String BORDER_GLOW_COLOR_SUFFIX = "#borderGlowColor";
    private static final String BORDER_GLOW_STRENGTH_SUFFIX = "#borderGlowStrength";
    private static final String BAR_GLOW_COLOR_SUFFIX = "#barGlowColor";
    private static final String BAR_GLOW_STRENGTH_SUFFIX = "#barGlowStrength";
    private static final int DEFAULT_GLOW_RGB = 0xFFFFFF;

    private ModuleGlow() {}

    public static double textShadowStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, TEXT_SHADOW_SUFFIX);
    }

    public static void setTextShadowStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, TEXT_SHADOW_SUFFIX, value);
    }

    public static double borderShadowStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, BORDER_SHADOW_SUFFIX);
    }

    public static void setBorderShadowStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, BORDER_SHADOW_SUFFIX, value);
    }

    public static int textGlowColor(PersistenceProvider p, String panelId) {
        return color(p, panelId, TEXT_GLOW_COLOR_SUFFIX);
    }

    public static void setTextGlowColor(PersistenceProvider p, String panelId, int rgb) {
        setColor(p, panelId, TEXT_GLOW_COLOR_SUFFIX, rgb);
    }

    public static double textGlowStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, TEXT_GLOW_STRENGTH_SUFFIX);
    }

    public static void setTextGlowStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, TEXT_GLOW_STRENGTH_SUFFIX, value);
    }

    public static int borderGlowColor(PersistenceProvider p, String panelId) {
        return color(p, panelId, BORDER_GLOW_COLOR_SUFFIX);
    }

    public static void setBorderGlowColor(PersistenceProvider p, String panelId, int rgb) {
        setColor(p, panelId, BORDER_GLOW_COLOR_SUFFIX, rgb);
    }

    public static double borderGlowStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, BORDER_GLOW_STRENGTH_SUFFIX);
    }

    public static void setBorderGlowStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, BORDER_GLOW_STRENGTH_SUFFIX, value);
    }

    public static int barGlowColor(PersistenceProvider p, String panelId) {
        return color(p, panelId, BAR_GLOW_COLOR_SUFFIX);
    }

    public static void setBarGlowColor(PersistenceProvider p, String panelId, int rgb) {
        setColor(p, panelId, BAR_GLOW_COLOR_SUFFIX, rgb);
    }

    public static double barGlowStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, BAR_GLOW_STRENGTH_SUFFIX);
    }

    public static void setBarGlowStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, BAR_GLOW_STRENGTH_SUFFIX, value);
    }

    /** Clears every shadow/glow key for this module, for a "reset this whole module" action. */
    public static void reset(PersistenceProvider p, String panelId) {
        p.remove(panelId + TEXT_SHADOW_SUFFIX);
        p.remove(panelId + BORDER_SHADOW_SUFFIX);
        p.remove(panelId + TEXT_GLOW_COLOR_SUFFIX);
        p.remove(panelId + TEXT_GLOW_STRENGTH_SUFFIX);
        p.remove(panelId + BORDER_GLOW_COLOR_SUFFIX);
        p.remove(panelId + BORDER_GLOW_STRENGTH_SUFFIX);
        p.remove(panelId + BAR_GLOW_COLOR_SUFFIX);
        p.remove(panelId + BAR_GLOW_STRENGTH_SUFFIX);
    }

    private static double strength(PersistenceProvider p, String panelId, String suffix) {
        return p.load(panelId + suffix).map(ComponentState::contentScale).orElse(0.0d);
    }

    private static void setStrength(PersistenceProvider p, String panelId, String suffix, double value) {
        p.save(panelId + suffix,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    private static int color(PersistenceProvider p, String panelId, String suffix) {
        return p.load(panelId + suffix).map(state -> (int) Math.round(state.contentScale())).orElse(DEFAULT_GLOW_RGB);
    }

    private static void setColor(PersistenceProvider p, String panelId, String suffix, int rgb) {
        p.save(panelId + suffix,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, (double) (rgb & 0xFFFFFF), ComponentState.DEFAULT_PADDING_SCALE));
    }
}
