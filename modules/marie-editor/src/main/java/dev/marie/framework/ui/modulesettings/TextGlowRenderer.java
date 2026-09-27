package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;

/**
 * Shared glow/shadow draw logic for {@link ModuleRenderContext} and {@link TextGlowRenderContext}.
 * The glow itself is delegated to {@link RenderContext#drawTextGlow} — a real blurred halo on a host
 * capable of off-screen rendering ({@code GuiGraphicsRenderContext}), or a plain offset-copy
 * approximation otherwise — so the actual glow technique lives in exactly one place regardless of
 * which wrapper reaches it. The shadow is simple enough (one dark copy, no blur expected of it) to
 * keep drawing directly here rather than through another indirection.
 */
@ApiStatus.Internal
final class TextGlowRenderer {

    private TextGlowRenderer() {}

    static void drawGlowAndShadow(RenderContext delegate, String text, int x, int y, float scale,
                                   double textGlowStrength, int textGlowColor, double textShadowStrength) {
        if (textGlowStrength > 0) {
            delegate.drawTextGlow(text, x, y, scale, textGlowColor, textGlowStrength);
        }
        if (textShadowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textShadowStrength * 255));
            delegate.drawText(text, x + 1, y + 1, alpha << 24, scale);
        }
    }
}
