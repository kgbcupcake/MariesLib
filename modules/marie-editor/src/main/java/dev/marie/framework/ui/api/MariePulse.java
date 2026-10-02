package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;

/**
 * A breathing "pulse" for a border, reusable by anything that draws through a {@link RenderContext}.
 * It has no color of its own and draws nothing extra: it makes what's already there breathe — the
 * border line and its glow ring fade down and back up together, each in its own color — exactly like
 * dragging a glow slider up and down by hand.
 *
 * <p>A pulse is a strength (0..1: 0 is off and leaves things exactly as they are; 1 fades them all the
 * way out at the trough), a {@link Speed} and a {@link Style}. Every pulse runs off one shared
 * real-time clock, so everything pulsing on screen — across modules, screens and mods — stays in sync
 * at any frame rate; the speeds are exact multiples of that clock, so pulses on different speeds still
 * peak together.
 *
 * <pre>{@code
 * MariePulse pulse = new MariePulse(0.8, MariePulse.Speed.NORMAL, MariePulse.Style.SMOOTH);
 * pulse.drawGlow(context, x, y, w, h, glowRgb, glowStrength);              // the border's glow ring
 * context.drawRoundedRect(x, y, w, h, 1, fill, pulse.apply(borderArgb));   // and its line, in step
 * }</pre>
 *
 * Module boxes built on {@code MarieModuleSettings} get this without calling it themselves: the Pulse
 * tab's settings are applied by {@code MarieModuleSettings#styledBorder} (the border line), {@code
 * MarieModuleSettings#drawBoxGlow} (Border glow) and the bar drawing of its render-context wrappers
 * (Bar glow).
 */
@ApiStatus.Experimental
public record MariePulse(double strength, Speed speed, Style style) {

    /** Seconds for one full pulse at {@link Speed#NORMAL}. */
    public static final double PERIOD_SECONDS = 2.0d;

    /** A pulse that changes nothing. */
    public static final MariePulse OFF = new MariePulse(0.0d, Speed.NORMAL, Style.SMOOTH);

    private static final long EPOCH_NANOS = System.nanoTime();

    /** How fast a pulse cycles — exact multiples of the shared clock, so different speeds stay in step. */
    public enum Speed {
        SLOW(0.5d), NORMAL(1.0d), FAST(2.0d);

        private final double multiplier;

        Speed(double multiplier) {
            this.multiplier = multiplier;
        }

        public double multiplier() {
            return multiplier;
        }
    }

    /** The shape of one cycle, as a level from 0 (dimmest) to 1 (brightest) over its phase 0..1. */
    public enum Style {
        /** A gentle, even breathe in and out. */
        SMOOTH,
        /** Two quick beats — a strong one, then a softer one — then a rest. */
        HEARTBEAT,
        /** A sharp flare that fades away. */
        FLASH;

        double level(double phase) {
            return switch (this) {
                case SMOOTH -> 0.5d - 0.5d * Math.cos(2.0d * Math.PI * phase);
                case HEARTBEAT -> Math.min(1.0d, bump(phase, 0.12d) + 0.6d * bump(phase, 0.34d));
                case FLASH -> phase < 0.08d ? phase / 0.08d : Math.exp(-(phase - 0.08d) * 6.0d);
            };
        }

        private static double bump(double phase, double center) {
            double d = (phase - center) / 0.07d;
            return Math.exp(-d * d);
        }
    }

    /** Whether this pulse changes anything at all. */
    public boolean isOn() {
        return strength > 0;
    }

    /** Where this pulse is right now, 0 (dimmest) to 1 (brightest). */
    public double level() {
        double seconds = (System.nanoTime() - EPOCH_NANOS) / 1_000_000_000.0d;
        double cycles = seconds * speed.multiplier() / PERIOD_SECONDS;
        return style.level(cycles - Math.floor(cycles));
    }

    /**
     * How much of what it pulses is showing right now: 1 at the peak (exactly as set), down to {@code
     * 1 - strength} at the trough; always 1 when off.
     */
    public double factor() {
        return isOn() ? 1.0d - Math.min(1.0d, strength) * (1.0d - level()) : 1.0d;
    }

    /** {@code argb} (e.g. a border line) breathing in its own color — its alpha scaled by {@link #factor}. Unchanged when off. */
    public int apply(int argb) {
        if (!isOn()) {
            return argb;
        }
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * factor());
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** A glow strength breathing with the pulse ({@code glowStrength × factor()}); a glow that's off stays off. */
    public double glowStrength(double glowStrength) {
        return glowStrength <= 0 ? glowStrength : glowStrength * factor();
    }

    /**
     * Draws a glow ring ({@link RenderContext#drawGlow}) of {@code glowRgb} at {@code glowStrength},
     * breathing with this pulse — the same one ring, in its own color, never a second one. Exactly the
     * plain glow when off; nothing when the glow is off. Use {@link #apply} on the same border's line
     * so line and ring breathe in step.
     */
    public void drawGlow(RenderContext context, int x, int y, int width, int height, int glowRgb, double glowStrength) {
        double s = glowStrength(glowStrength);
        if (s <= 0) {
            return;
        }
        int alpha = Math.min(255, (int) Math.round(s * 255));
        context.drawGlow(x, y, width, height, (alpha << 24) | (glowRgb & 0xFFFFFF));
    }
}
