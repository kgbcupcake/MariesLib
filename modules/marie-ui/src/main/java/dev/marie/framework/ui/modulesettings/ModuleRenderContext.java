package dev.marie.framework.ui.modulesettings;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import net.minecraft.world.item.ItemStack;

/**
 * Applies a module's display settings to whatever it draws, so a module whose renderer already draws
 * text and icons through a {@link RenderContext} needs no per-draw changes: text and icons are shifted
 * by the module's text and icon offsets, bars by the bar offset and bar size, icons are scaled by the icon size relative to the text size
 * (the module's own renderer already scales both by the text size, so at the default — icon size
 * following text size — the ratio is 1 and nothing changes), and text and icons get their own
 * brightness; icons are skipped entirely while the module's "Hide Icons" toggle is on. Fills, bars, borders and clips pass straight through. {@link #wrap} returns the original
 * context when every setting is at its default.
 */
@ApiStatus.Internal
public final class ModuleRenderContext implements RenderContext {

    private final RenderContext delegate;
    private final int textDx;
    private final int textDy;
    private final int iconDx;
    private final int iconDy;
    private final float iconRatio;
    private final double textBrightness;
    private final double iconBrightness;
    private final int barDx;
    private final int barDy;
    private final float barScale;
    private final boolean hideIcons;

    private ModuleRenderContext(RenderContext delegate, int textDx, int textDy, int iconDx, int iconDy,
                                float iconRatio, double textBrightness, double iconBrightness,
                                int barDx, int barDy, float barScale, boolean hideIcons) {
        this.delegate = delegate;
        this.textDx = textDx;
        this.textDy = textDy;
        this.iconDx = iconDx;
        this.iconDy = iconDy;
        this.iconRatio = iconRatio;
        this.textBrightness = textBrightness;
        this.iconBrightness = iconBrightness;
        this.barDx = barDx;
        this.barDy = barDy;
        this.barScale = barScale;
        this.hideIcons = hideIcons;
    }

    /** {@code delegate} wrapped with {@code panelId}'s settings read from {@code store} now; {@code delegate} itself if all are default. */
    public static RenderContext wrap(RenderContext delegate, PersistenceProvider store, String panelId) {
        int textDx = ModuleOffsets.textX(store, panelId);
        int textDy = ModuleOffsets.textY(store, panelId);
        int iconDx = ModuleOffsets.iconX(store, panelId);
        int iconDy = ModuleOffsets.iconY(store, panelId);
        double text = ModuleScales.textScale(store, panelId);
        float ratio = text > 0 ? (float) (ModuleScales.iconScale(store, panelId) / text) : 1f;
        double textBrightness = ModuleScales.textBrightness(store, panelId);
        double iconBrightness = ModuleScales.iconBrightness(store, panelId);
        int barDx = ModuleOffsets.barX(store, panelId);
        int barDy = ModuleOffsets.barY(store, panelId);
        float barScale = (float) ModuleScales.barScale(store, panelId);
        boolean hideIcons = HideFlags.iconsHidden(store, panelId);
        boolean plain = !hideIcons && textDx == 0 && textDy == 0 && iconDx == 0 && iconDy == 0 && barDx == 0 && barDy == 0
                && Math.abs(ratio - 1f) < 1e-4f && Math.abs(barScale - 1f) < 1e-4f
                && textBrightness == 1.0d && iconBrightness == 1.0d;
        return plain ? delegate : new ModuleRenderContext(delegate, textDx, textDy, iconDx, iconDy, ratio,
                textBrightness, iconBrightness, barDx, barDy, barScale, hideIcons);
    }

    @Override
    public void drawText(String text, int x, int y, int argbColor, float scale) {
        delegate.drawText(text, x + textDx, y + textDy, BrightnessRenderContext.scale(argbColor, textBrightness), scale);
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, float scale) {
        if (hideIcons) {
            return;
        }
        float tint = (float) iconBrightness;
        RenderSystem.setShaderColor(tint, tint, tint, 1f);
        try {
            delegate.drawItem(stack, x + iconDx, y + iconDy, scale * iconRatio);
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
        delegate.drawBar(x + barDx, y + barDy, scaled(width), scaled(height), fillPct, backgroundColor, fillColor);
    }

    @Override
    public void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        delegate.drawVerticalBar(x + barDx, y + barDy, scaled(width), scaled(height), fillPct, backgroundColor, fillColor);
    }

    /** A bar dimension scaled by the module's bar size (never below 1 for a non-empty bar). */
    private int scaled(int dimension) {
        return dimension <= 0 ? dimension : Math.max(1, Math.round(dimension * barScale));
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
