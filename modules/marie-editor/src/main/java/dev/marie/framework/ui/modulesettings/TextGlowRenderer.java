package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;

/**
 * Shared "glow the glyphs, not a box behind them" draw logic for {@link ModuleRenderContext} and
 * {@link TextGlowRenderContext} — redraws {@code text} itself at a few small radiating offsets in
 * the glow color (plus a single shadow copy), so the glow/shadow hugs the actual letter shapes
 * instead of a rectangle sized to the text's bounding box (what {@code drawGlow} draws for a box or
 * bar, where there's no glyph shape to hug).
 *
 * <p>There is no real blur here — {@link RenderContext} has no offscreen buffer or shader pass to
 * blur into, only immediate-mode fills and text draws, so this approximates a glow by stacking
 * translucent copies of the same glyphs. Diagonal offsets are deliberately left out: at a letter's
 * corner, a diagonal offset overlaps BOTH of its neighboring cardinal offsets on the same pixels,
 * and alpha-blended overlaps compound toward full opacity fast (three ~45%-alpha copies stacked on
 * one pixel is already ~83% opaque) — that compounding is what read as a muddy/blurred blob rather
 * than a clean halo at any strength above "barely visible". Four cardinal offsets keep the
 * worst-case overlap at any single pixel to two copies, which stays soft.
 */
@ApiStatus.Internal
final class TextGlowRenderer {

    private static final int[][] RING = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    /** Alpha ceiling at 100% strength — high enough to read clearly, low enough that the worst-case two-copy overlap (~78% opaque) still looks like a soft edge, not a solid outline. */
    private static final int MAX_GLOW_ALPHA = 130;

    private TextGlowRenderer() {}

    static void drawGlowAndShadow(RenderContext delegate, String text, int x, int y, float scale,
                                   double textGlowStrength, int textGlowColor, double textShadowStrength) {
        if (textGlowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textGlowStrength * MAX_GLOW_ALPHA));
            int glowArgb = (alpha << 24) | (textGlowColor & 0xFFFFFF);
            for (int[] offset : RING) {
                delegate.drawText(text, x + offset[0], y + offset[1], glowArgb, scale);
            }
        }
        if (textShadowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textShadowStrength * 255));
            delegate.drawText(text, x + 1, y + 1, alpha << 24, scale);
        }
    }
}
