package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import net.minecraft.world.item.ItemStack;

/**
 * Layers a module's text glow/shadow and bar glow (see {@link ModuleGlow}) onto a {@link
 * RenderContext} without touching position, scale or brightness — for a module whose renderer
 * already applies its own text/icon/bar offsets and scale by hand (so wrapping with the full
 * {@link ModuleRenderContext}, which applies those same offsets itself, would double them up) but
 * still wants its Glow-tab settings to actually draw. {@link #wrap} returns the original context
 * untouched when every setting is at its default (off), so a module that's never touched Glow keeps
 * drawing exactly as before.
 */
@ApiStatus.Internal
public final class TextGlowRenderContext implements RenderContext {

    private final RenderContext delegate;
    private final double textShadowStrength;
    private final double textGlowStrength;
    private final int textGlowColor;
    private final double barGlowStrength;
    private final int barGlowColor;

    private TextGlowRenderContext(RenderContext delegate, double textShadowStrength, double textGlowStrength,
                                   int textGlowColor, double barGlowStrength, int barGlowColor) {
        this.delegate = delegate;
        this.textShadowStrength = textShadowStrength;
        this.textGlowStrength = textGlowStrength;
        this.textGlowColor = textGlowColor;
        this.barGlowStrength = barGlowStrength;
        this.barGlowColor = barGlowColor;
    }

    public static RenderContext wrap(RenderContext delegate, PersistenceProvider store, String panelId) {
        double textShadowStrength = ModuleGlow.textShadowStrength(store, panelId);
        double textGlowStrength = ModuleGlow.textGlowStrength(store, panelId);
        double barGlowStrength = ModuleGlow.barGlowStrength(store, panelId);
        if (textShadowStrength <= 0 && textGlowStrength <= 0 && barGlowStrength <= 0) {
            return delegate;
        }
        int textGlowColor = ModuleGlow.textGlowColor(store, panelId);
        int barGlowColor = ModuleGlow.barGlowColor(store, panelId);
        return new TextGlowRenderContext(delegate, textShadowStrength, textGlowStrength, textGlowColor, barGlowStrength, barGlowColor);
    }

    /**
     * Same, but {@code drawText} glows with the module's Bar glow instead of its body Text glow — for
     * a module whose renderer draws what's conceptually its "bar" content as plain text rather than
     * through {@link RenderContext#drawBar} (e.g. Recent Meals' row names, which move/size with "Move
     * Bars"/"Bar size" despite being drawn as text), so that content's glow tracks the Bar glow
     * slider a player would expect to control it, not the unrelated Text glow one.
     */
    public static RenderContext wrapBarText(RenderContext delegate, PersistenceProvider store, String panelId) {
        double textShadowStrength = ModuleGlow.textShadowStrength(store, panelId);
        double barGlowStrength = ModuleGlow.barGlowStrength(store, panelId);
        if (textShadowStrength <= 0 && barGlowStrength <= 0) {
            return delegate;
        }
        int barGlowColor = ModuleGlow.barGlowColor(store, panelId);
        return new TextGlowRenderContext(delegate, textShadowStrength, barGlowStrength, barGlowColor, barGlowStrength, barGlowColor);
    }

    @Override
    public void drawText(String text, int x, int y, int argbColor, float scale) {
        TextGlowRenderer.drawGlowAndShadow(delegate, text, x, y, scale, textGlowStrength, textGlowColor, textShadowStrength);
        delegate.drawText(text, x, y, argbColor, scale);
    }

    /** Forwards to {@code delegate} unchanged — see {@link BrightnessRenderContext#drawTextGlow} for why this override exists at all. */
    @Override
    public void drawTextGlow(String text, int x, int y, float scale, int glowColor, double strength) {
        delegate.drawTextGlow(text, x, y, scale, glowColor, strength);
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, float scale) {
        delegate.drawItem(stack, x, y, scale);
    }

    @Override
    public int screenWidth() {
        return delegate.screenWidth();
    }

    @Override
    public int screenHeight() {
        return delegate.screenHeight();
    }

    @Override
    public float partialTick() {
        return delegate.partialTick();
    }

    @Override
    public Theme theme() {
        return delegate.theme();
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argbColor) {
        delegate.fillRect(x, y, width, height, argbColor);
    }

    @Override
    public void drawBorder(int x, int y, int width, int height, int thickness, int argbColor) {
        delegate.drawBorder(x, y, width, height, thickness, argbColor);
    }

    @Override
    public void drawDashedBorder(int x, int y, int width, int height, int argbColor) {
        delegate.drawDashedBorder(x, y, width, height, argbColor);
    }

    @Override
    public void drawGlow(int x, int y, int width, int height, int argbColor) {
        delegate.drawGlow(x, y, width, height, argbColor);
    }

    @Override
    public int textWidth(String text, float scale) {
        return delegate.textWidth(text, scale);
    }

    @Override
    public void drawBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        drawBarGlow(x, y, width, height);
        delegate.drawBar(x, y, width, height, fillPct, backgroundColor, fillColor);
    }

    @Override
    public void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        drawBarGlow(x, y, width, height);
        delegate.drawVerticalBar(x, y, width, height, fillPct, backgroundColor, fillColor);
    }

    private void drawBarGlow(int x, int y, int width, int height) {
        if (barGlowStrength <= 0) {
            return;
        }
        int alpha = Math.min(255, (int) Math.round(barGlowStrength * 255));
        delegate.drawGlow(x, y, width, height, (alpha << 24) | (barGlowColor & 0xFFFFFF));
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        delegate.pushClip(x, y, width, height);
    }

    @Override
    public void popClip() {
        delegate.popClip();
    }
}
