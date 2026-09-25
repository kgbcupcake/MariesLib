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
 * brightness; each of text, icons and bars is skipped entirely while the module's matching "Hide" toggle is
 * on (a hidden part also records no extent, so a move outline for it falls back to the whole box, same as a
 * part the module simply hasn't drawn yet). "Hide Window" skips everything above plus {@code fillRect},
 * {@code drawBorder} and {@code drawGlow} — the module's own background box — so the whole module vanishes;
 * {@code drawDashedBorder} (the editor's move outline, not the module's own box) and clips pass straight
 * through regardless. {@link #wrap} also records where each drawn part is ({@link ModuleExtents}).
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
    private final boolean hideBars;
    private final boolean hideText;
    private final boolean hideWindow;
    private final PersistenceProvider store;
    private final String panelId;
    private final double textShadowStrength;
    private final double textGlowStrength;
    private final int textGlowColor;
    private final double barGlowStrength;
    private final int barGlowColor;

    private ModuleRenderContext(RenderContext delegate, int textDx, int textDy, int iconDx, int iconDy,
                                float iconRatio, double textBrightness, double iconBrightness,
                                int barDx, int barDy, float barScale, boolean hideIcons, boolean hideBars, boolean hideText,
                                boolean hideWindow, PersistenceProvider store, String panelId,
                                double textShadowStrength, double textGlowStrength, int textGlowColor,
                                double barGlowStrength, int barGlowColor) {
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
        this.hideBars = hideBars;
        this.hideText = hideText;
        this.hideWindow = hideWindow;
        this.store = store;
        this.panelId = panelId;
        this.textShadowStrength = textShadowStrength;
        this.textGlowStrength = textGlowStrength;
        this.textGlowColor = textGlowColor;
        this.barGlowStrength = barGlowStrength;
        this.barGlowColor = barGlowColor;
    }

    /** {@code delegate} wrapped with {@code panelId}'s settings read from {@code store} now; {@code delegate} itself if all are default. */
    public static RenderContext wrap(RenderContext delegate, PersistenceProvider store, String panelId) {
        return wrap(delegate, store, panelId, true);
    }

    /**
     * Same, but {@code iconFollowsText} false is for a module built with {@link
     * dev.marie.framework.ui.api.StandardPanelBuilder#independentIconSize} whose renderer already
     * resolves its own final icon draw scale via {@link
     * dev.marie.framework.ui.api.MarieModuleSettings#iconScale(PersistenceProvider, String, boolean)}
     * with {@code followText = false} and passes that value straight to {@code drawItem}: the wrapper
     * then applies no icon ratio at all (1:1 passthrough), so that already-resolved value isn't scaled
     * a second time by a ratio computed against the module's (possibly unrelated, or entirely unused)
     * text scale. {@code true} (or the other overload) keeps the original "icon size relative to text
     * size" ratio, for a module whose renderer scales text and icons together by a single text-based
     * scale and relies on the wrapper alone to turn that into the actual icon size.
     */
    public static RenderContext wrap(RenderContext delegate, PersistenceProvider store, String panelId, boolean iconFollowsText) {
        int textDx = ModuleOffsets.textX(store, panelId);
        int textDy = ModuleOffsets.textY(store, panelId);
        int iconDx = ModuleOffsets.iconX(store, panelId);
        int iconDy = ModuleOffsets.iconY(store, panelId);
        double text = ModuleScales.textScale(store, panelId);
        float ratio = iconFollowsText
                ? (text > 0 ? (float) (ModuleScales.iconScale(store, panelId) / text) : 1f)
                : 1f;
        double textBrightness = ModuleScales.textBrightness(store, panelId);
        double iconBrightness = ModuleScales.iconBrightness(store, panelId);
        int barDx = ModuleOffsets.barX(store, panelId);
        int barDy = ModuleOffsets.barY(store, panelId);
        float barScale = (float) ModuleScales.barScale(store, panelId);
        boolean hideIcons = HideFlags.iconsHidden(store, panelId);
        boolean hideBars = HideFlags.barsHidden(store, panelId);
        boolean hideText = HideFlags.textHidden(store, panelId);
        boolean hideWindow = HideFlags.windowHidden(store, panelId);
        double textShadowStrength = ModuleGlow.textShadowStrength(store, panelId);
        double textGlowStrength = ModuleGlow.textGlowStrength(store, panelId);
        int textGlowColor = ModuleGlow.textGlowColor(store, panelId);
        double barGlowStrength = ModuleGlow.barGlowStrength(store, panelId);
        int barGlowColor = ModuleGlow.barGlowColor(store, panelId);
        // Always wrapped, even at all-default settings: the wrapper is what records where the module draws (see ModuleExtents).
        ModuleExtents.begin(store, panelId);
        return new ModuleRenderContext(delegate, textDx, textDy, iconDx, iconDy, ratio,
                textBrightness, iconBrightness, barDx, barDy, barScale, hideIcons, hideBars, hideText, hideWindow, store, panelId,
                textShadowStrength, textGlowStrength, textGlowColor, barGlowStrength, barGlowColor);
    }

    @Override
    public void drawText(String text, int x, int y, int argbColor, float scale) {
        if (hideText || hideWindow) {
            return;
        }
        int drawX = x + textDx;
        int drawY = y + textDy;
        int width = delegate.textWidth(text, scale);
        int height = Math.round(9 * scale);
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.TEXT, drawX, drawY, width, height);
        if (textGlowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textGlowStrength * 255));
            delegate.drawGlow(drawX, drawY, width, height, (alpha << 24) | (textGlowColor & 0xFFFFFF));
        }
        if (textShadowStrength > 0) {
            int alpha = Math.min(255, (int) Math.round(textShadowStrength * 255));
            delegate.drawText(text, drawX + 1, drawY + 1, alpha << 24, scale);
        }
        delegate.drawText(text, drawX, drawY, BrightnessRenderContext.scale(argbColor, textBrightness), scale);
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, float scale) {
        if (hideIcons || hideWindow) {
            return;
        }
        float drawScale = scale * iconRatio;
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.ICON, x + iconDx, y + iconDy, Math.round(16 * drawScale), Math.round(16 * drawScale));
        if (iconBrightness == 1.0d) {
            // No tint to apply, and setting the shader color would overwrite a fade the host has set.
            delegate.drawItem(stack, x + iconDx, y + iconDy, drawScale);
            return;
        }
        float tint = (float) iconBrightness;
        RenderSystem.setShaderColor(tint, tint, tint, 1f);
        try {
            delegate.drawItem(stack, x + iconDx, y + iconDy, drawScale);
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
        if (hideWindow) {
            return;
        }
        delegate.fillRect(x, y, width, height, argbColor);
    }

    @Override
    public void drawBorder(int x, int y, int width, int height, int thickness, int argbColor) {
        if (hideWindow) {
            return;
        }
        delegate.drawBorder(x, y, width, height, thickness, argbColor);
    }

    @Override
    public void drawDashedBorder(int x, int y, int width, int height, int argbColor) {
        delegate.drawDashedBorder(x, y, width, height, argbColor);
    }

    @Override
    public void drawGlow(int x, int y, int width, int height, int argbColor) {
        if (hideWindow) {
            return;
        }
        delegate.drawGlow(x, y, width, height, argbColor);
    }

    @Override
    public int textWidth(String text, float scale) {
        return delegate.textWidth(text, scale);
    }

    @Override
    public void drawBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        if (hideBars || hideWindow) {
            return;
        }
        int drawX = x + barDx;
        int drawY = y + barDy;
        int w = scaled(width);
        int h = scaled(height);
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.BAR, drawX, drawY, w, h);
        drawBarGlow(drawX, drawY, w, h);
        delegate.drawBar(drawX, drawY, w, h, fillPct, backgroundColor, fillColor);
    }

    @Override
    public void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        if (hideBars || hideWindow) {
            return;
        }
        int drawX = x + barDx;
        int drawY = y + barDy;
        int w = scaled(width);
        int h = scaled(height);
        ModuleExtents.add(store, panelId, ModuleExtents.Kind.BAR, drawX, drawY, w, h);
        drawBarGlow(drawX, drawY, w, h);
        delegate.drawVerticalBar(drawX, drawY, w, h, fillPct, backgroundColor, fillColor);
    }

    private void drawBarGlow(int x, int y, int width, int height) {
        if (barGlowStrength <= 0) {
            return;
        }
        int alpha = Math.min(255, (int) Math.round(barGlowStrength * 255));
        delegate.drawGlow(x, y, width, height, (alpha << 24) | (barGlowColor & 0xFFFFFF));
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
