package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.api.MariePulse;
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

    /** Retired setting (see {@link #textShadowStrength}); kept only so a reset still clears an old saved value. */
    private static final String TEXT_SHADOW_SUFFIX = "#textShadowStrength";
    private static final String BORDER_SHADOW_SUFFIX = "#borderShadowStrength";
    private static final String TEXT_GLOW_COLOR_SUFFIX = "#textGlowColor";
    private static final String TEXT_GLOW_STRENGTH_SUFFIX = "#textGlowStrength";
    private static final String BORDER_GLOW_COLOR_SUFFIX = "#borderGlowColor";
    private static final String BORDER_GLOW_STRENGTH_SUFFIX = "#borderGlowStrength";
    private static final String BAR_GLOW_COLOR_SUFFIX = "#barGlowColor";
    private static final String BAR_GLOW_STRENGTH_SUFFIX = "#barGlowStrength";
    private static final String PULSE_STRENGTH_SUFFIX = "#pulseStrength";
    private static final String PULSE_SPEED_SUFFIX = "#pulseSpeed";
    private static final String PULSE_STYLE_SUFFIX = "#pulseStyle";
    private static final String PULSE_BORDER_SUFFIX = "#pulseBorder";
    private static final String PULSE_BORDER_GLOW_SUFFIX = "#pulseBorderGlow";
    private static final String PULSE_BAR_GLOW_SUFFIX = "#pulseBarGlow";
    private static final int DEFAULT_GLOW_RGB = 0xFFFFFF;

    private ModuleGlow() {}

    /**
     * Text shadow was retired: no module offers it any more, and any strength a player saved before
     * is ignored (always 0) so old saves can't keep drawing a shadow with no slider left to turn it off.
     */
    public static double textShadowStrength(PersistenceProvider p, String panelId) {
        return 0.0d;
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

    /** Pulse strength, 0..1 — 0 (the default) is off. */
    public static double pulseStrength(PersistenceProvider p, String panelId) {
        return strength(p, panelId, PULSE_STRENGTH_SUFFIX);
    }

    public static void setPulseStrength(PersistenceProvider p, String panelId, double value) {
        setStrength(p, panelId, PULSE_STRENGTH_SUFFIX, value);
    }

    /** Pulse speed, as a {@link MariePulse.Speed} ordinal (default {@code NORMAL}). */
    public static int pulseSpeed(PersistenceProvider p, String panelId) {
        return index(p, panelId, PULSE_SPEED_SUFFIX, MariePulse.Speed.NORMAL.ordinal(), MariePulse.Speed.values().length);
    }

    public static void setPulseSpeed(PersistenceProvider p, String panelId, int ordinal) {
        setStrength(p, panelId, PULSE_SPEED_SUFFIX, ordinal);
    }

    /** Pulse style, as a {@link MariePulse.Style} ordinal (default {@code SMOOTH}). */
    public static int pulseStyle(PersistenceProvider p, String panelId) {
        return index(p, panelId, PULSE_STYLE_SUFFIX, MariePulse.Style.SMOOTH.ordinal(), MariePulse.Style.values().length);
    }

    public static void setPulseStyle(PersistenceProvider p, String panelId, int ordinal) {
        setStrength(p, panelId, PULSE_STYLE_SUFFIX, ordinal);
    }

    /** Whether the pulse animates the border line (default on). */
    public static boolean pulseBorder(PersistenceProvider p, String panelId) {
        return flag(p, panelId, PULSE_BORDER_SUFFIX);
    }

    public static void setPulseBorder(PersistenceProvider p, String panelId, boolean on) {
        setStrength(p, panelId, PULSE_BORDER_SUFFIX, on ? 1 : 0);
    }

    /** Whether the pulse animates the Border glow (default on). */
    public static boolean pulseBorderGlow(PersistenceProvider p, String panelId) {
        return flag(p, panelId, PULSE_BORDER_GLOW_SUFFIX);
    }

    public static void setPulseBorderGlow(PersistenceProvider p, String panelId, boolean on) {
        setStrength(p, panelId, PULSE_BORDER_GLOW_SUFFIX, on ? 1 : 0);
    }

    /** Whether the pulse animates the Bar glow (default on). */
    public static boolean pulseBarGlow(PersistenceProvider p, String panelId) {
        return flag(p, panelId, PULSE_BAR_GLOW_SUFFIX);
    }

    public static void setPulseBarGlow(PersistenceProvider p, String panelId, boolean on) {
        setStrength(p, panelId, PULSE_BAR_GLOW_SUFFIX, on ? 1 : 0);
    }

    /** The module's Pulse as a {@link MariePulse}, before any per-target toggle is applied. */
    public static MariePulse pulse(PersistenceProvider p, String panelId) {
        double strength = pulseStrength(p, panelId);
        if (strength <= 0) {
            return MariePulse.OFF;
        }
        return new MariePulse(strength,
                MariePulse.Speed.values()[pulseSpeed(p, panelId)], MariePulse.Style.values()[pulseStyle(p, panelId)]);
    }

    /** The module's Pulse for its border line — {@link MariePulse#OFF} if that target is switched off. */
    public static MariePulse borderPulse(PersistenceProvider p, String panelId) {
        return pulseBorder(p, panelId) ? pulse(p, panelId) : MariePulse.OFF;
    }

    /** The module's Pulse for its Border glow — {@link MariePulse#OFF} if that target is switched off. */
    public static MariePulse borderGlowPulse(PersistenceProvider p, String panelId) {
        return pulseBorderGlow(p, panelId) ? pulse(p, panelId) : MariePulse.OFF;
    }

    /** The module's Pulse for its Bar glow — {@link MariePulse#OFF} if that target is switched off. */
    public static MariePulse barGlowPulse(PersistenceProvider p, String panelId) {
        return pulseBarGlow(p, panelId) ? pulse(p, panelId) : MariePulse.OFF;
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
        p.remove(panelId + PULSE_STRENGTH_SUFFIX);
        p.remove(panelId + PULSE_SPEED_SUFFIX);
        p.remove(panelId + PULSE_STYLE_SUFFIX);
        p.remove(panelId + PULSE_BORDER_SUFFIX);
        p.remove(panelId + PULSE_BORDER_GLOW_SUFFIX);
        p.remove(panelId + PULSE_BAR_GLOW_SUFFIX);
    }

    private static int index(PersistenceProvider p, String panelId, String suffix, int fallback, int count) {
        int i = p.load(panelId + suffix).map(state -> (int) Math.round(state.contentScale())).orElse(fallback);
        return i >= 0 && i < count ? i : fallback;
    }

    /** A toggle stored like a strength, on (1) until set. */
    private static boolean flag(PersistenceProvider p, String panelId, String suffix) {
        return p.load(panelId + suffix).map(state -> state.contentScale() > 0.5d).orElse(true);
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
