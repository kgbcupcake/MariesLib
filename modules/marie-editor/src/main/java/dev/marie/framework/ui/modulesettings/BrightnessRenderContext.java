package dev.marie.framework.ui.modulesettings;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import net.minecraft.world.item.ItemStack;

/**
 * Wraps a {@link RenderContext} so text and item icons drawn through it come out at independent
 * brightness multipliers (text: RGB scaled, alpha untouched; icons: tinted via the GUI shader color).
 * Fills, bars, borders and clips pass straight through, so a module can dim just its icons and text by
 * drawing through the wrapper. {@link #wrap} returns the original context untouched when both are 1.0.
 */
@ApiStatus.Internal
public final class BrightnessRenderContext implements RenderContext {

    private final RenderContext delegate;
    private final double textBrightness;
    private final double iconBrightness;

    private BrightnessRenderContext(RenderContext delegate, double textBrightness, double iconBrightness) {
        this.delegate = delegate;
        this.textBrightness = textBrightness;
        this.iconBrightness = iconBrightness;
    }

    public static RenderContext wrap(RenderContext delegate, double textBrightness, double iconBrightness) {
        return textBrightness == 1.0d && iconBrightness == 1.0d
                ? delegate
                : new BrightnessRenderContext(delegate, textBrightness, iconBrightness);
    }

    /** {@code argb} with its RGB scaled by {@code brightness} (alpha untouched, each channel capped at 255, so values above 1.0 wash colors toward white); 1.0 returns it unchanged. */
    public static int scale(int argb, double brightness) {
        if (brightness == 1.0d) {
            return argb;
        }
        int r = Math.min(255, (int) Math.round(((argb >> 16) & 0xFF) * brightness));
        int g = Math.min(255, (int) Math.round(((argb >> 8) & 0xFF) * brightness));
        int b = Math.min(255, (int) Math.round((argb & 0xFF) * brightness));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void drawText(String text, int x, int y, int argbColor, float scale) {
        delegate.drawText(text, x, y, scale(argbColor, textBrightness), scale);
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, float scale) {
        float tint = (float) iconBrightness;
        RenderSystem.setShaderColor(tint, tint, tint, 1f);
        try {
            delegate.drawItem(stack, x, y, scale);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
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
        delegate.drawBar(x, y, width, height, fillPct, backgroundColor, fillColor);
    }

    @Override
    public void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        delegate.drawVerticalBar(x, y, width, height, fillPct, backgroundColor, fillColor);
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
