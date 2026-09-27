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

    private static final int[][] RINGS = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private TextGlowRenderer() {}

    static void drawGlowAndShadow(RenderContext delegate, String text, int x, int y, float scale,
                                   double textGlowStrength, int textGlowColor, double textShadowStrength) {
        if (textGlowStrength > 0) {
            int baseAlpha = Math.min(255, (int) Math.round(textGlowStrength * 200));
            int glowRgb = textGlowColor & 0xFFFFFF;
            for (int[] ring : RINGS) {
                int dist = Math.max(Math.abs(ring[0]), Math.abs(ring[1]));
                int ringAlpha = dist >= 2 ? baseAlpha / 3 : baseAlpha * 2 / 3;
                delegate.drawText(text, x + ring[0], y + ring[1], (ringAlpha << 24) | glowRgb, scale);
            }
        }
        if (textShadowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textShadowStrength * 255));
            delegate.drawText(text, x + 1, y + 1, alpha << 24, scale);
        }
    }
}
