package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;

/**
 * Shared "glow the glyphs, not a box behind them" draw logic for {@link ModuleRenderContext} and
 * {@link TextGlowRenderContext} — redraws {@code text} itself at several small radiating offsets in
 * the glow color (plus a single shadow copy), so the glow/shadow hugs the actual letter shapes
 * instead of a rectangle sized to the text's bounding box (what {@code drawGlow} draws for a box or
 * bar, where there's no glyph shape to hug).
 */
@ApiStatus.Internal
final class TextGlowRenderer {

    /** Single 1px ring (8 directions) — a second, wider ring made the glow read as a blurry smear rather than a crisp outline. */
    private static final int[][] RING = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private TextGlowRenderer() {}

    static void drawGlowAndShadow(RenderContext delegate, String text, int x, int y, float scale,
                                   double textGlowStrength, int textGlowColor, double textShadowStrength) {
        if (textGlowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textGlowStrength * 180));
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
