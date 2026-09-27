package dev.marie.framework.ui.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;

/**
 * The one concrete {@link RenderContext} MarieUI ships: draws through NeoForge's
 * {@link GuiGraphics}. Callers construct a fresh instance per frame.
 */
public final class GuiGraphicsRenderContext implements RenderContext {

    private final GuiGraphics graphics;
    private final Minecraft minecraft;
    private final Theme theme;
    private final float partialTick;

    /**
     * {@code [x1, y1, x2, y2]} per active {@link #pushClip}, innermost on top — a fresh instance is
     * constructed per frame (see class javadoc), so this never leaks state across frames.
     */
    private final ArrayDeque<int[]> clipStack = new ArrayDeque<>();

    public GuiGraphicsRenderContext(GuiGraphics graphics, Minecraft minecraft, Theme theme, float partialTick) {
        this.graphics = graphics;
        this.minecraft = minecraft;
        this.theme = theme;
        this.partialTick = partialTick;
    }

    /**
     * Escape hatch for consumers that must issue raw {@link GuiGraphics} draw calls of their own
     * (e.g. a legacy non-MarieUI renderer invoked mid-frame during a MarieUI-managed drag/resize
     * interaction) rather than going through this class's {@link RenderContext} primitives.
     * Deliberately narrow — not a general invitation to bypass {@link RenderContext}.
     */
    public GuiGraphics graphics() {
        return graphics;
    }

    @Override
    public int screenWidth() {
        return minecraft.getWindow().getGuiScaledWidth();
    }

    @Override
    public int screenHeight() {
        return minecraft.getWindow().getGuiScaledHeight();
    }

    @Override
    public float partialTick() {
        return partialTick;
    }

    @Override
    public Theme theme() {
        return theme;
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argbColor) {
        graphics.fill(x, y, x + width, y + height, argbColor);
    }

    @Override
    public void drawBorder(int x, int y, int width, int height, int thickness, int argbColor) {
        graphics.fill(x, y, x + width, y + thickness, argbColor);
        graphics.fill(x, y + height - thickness, x + width, y + height, argbColor);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, argbColor);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, argbColor);
    }

    @Override
    public void drawDashedBorder(int x, int y, int width, int height, int argbColor) {
        int step = 4;
        int seg = 2;
        for (int i = 0; i < width; i += step) {
            graphics.fill(x + i, y, x + Math.min(i + seg, width), y + 1, argbColor);
            graphics.fill(x + i, y + height - 1, x + Math.min(i + seg, width), y + height, argbColor);
        }
        for (int i = 0; i < height; i += step) {
            graphics.fill(x, y + i, x + 1, y + Math.min(i + seg, height), argbColor);
            graphics.fill(x + width - 1, y + i, x + width, y + Math.min(i + seg, height), argbColor);
        }
    }

    @Override
    public void drawGlow(int x, int y, int width, int height, int argbColor) {
        int alpha = (argbColor >>> 24) & 0xFF;
        int rgb = argbColor & 0x00FFFFFF;
        int[] falloff = {100, 65, 35, 15};
        for (int ring = 0; ring < falloff.length; ring++) {
            int ringAlpha = alpha * falloff[ring] / 100;
            int ringColor = (ringAlpha << 24) | rgb;
            int rx = x - ring;
            int ry = y - ring;
            int rw = width + ring * 2;
            int rh = height + ring * 2;
            graphics.fill(rx, ry, rx + rw, ry + 1, ringColor);
            graphics.fill(rx, ry + rh - 1, rx + rw, ry + rh, ringColor);
            graphics.fill(rx, ry + 1, rx + 1, ry + rh - 1, ringColor);
            graphics.fill(rx + rw - 1, ry + 1, rx + rw, ry + rh - 1, ringColor);
        }
    }

    /** Padding (local, pre-scale pixels) reserved around the glyphs in the offscreen texture so the blurred passes below have room to spread into instead of clipping at the texture edge. */
    private static final int GLOW_TEXTURE_PADDING = 4;
    /** Composited back at each of these scale factors (of the small glyph texture) with the paired alpha fraction — larger/fainter passes underneath a smaller/stronger one approximate a soft radial falloff via hardware bilinear upsampling, since this pipeline has no real blur shader. */
    private static final float[] GLOW_PASS_SCALES = {2.2f, 1.6f, 1.15f};
    private static final float[] GLOW_PASS_ALPHA_FRACTIONS = {0.16f, 0.30f, 0.55f};

    /**
     * Real blurred glow, unlike the interface's plain offset-copy default: renders {@code text} into
     * a small offscreen texture, then composites that texture back over the real screen position at a
     * few increasing scales with decreasing alpha — magnifying a small texture with bilinear filtering
     * softens it, which is the closest approximation to an actual blur this pipeline can do without a
     * dedicated blur shader. The real crisp text is not drawn here; the caller still draws it on top
     * afterward, same contract as every other {@code drawTextGlow} implementation.
     *
     * <p>Allocates and destroys a GL framebuffer+texture on every call — correctness-first for now;
     * a hot path calling this every frame for several simultaneously-glowing texts is a real
     * candidate for caching a reusable target later, but isn't done here yet.
     */
    @Override
    public void drawTextGlow(String text, int x, int y, float scale, int glowColor, double strength) {
        if (strength <= 0 || text.isEmpty()) {
            return;
        }
        int rawWidth = minecraft.font.width(text);
        if (rawWidth <= 0) {
            return;
        }
        int texW = rawWidth + GLOW_TEXTURE_PADDING * 2;
        int texH = 9 + GLOW_TEXTURE_PADDING * 2;

        TextureTarget target = new TextureTarget(texW, texH, false, Minecraft.ON_OSX);
        try {
            renderGlyphsToTexture(target, text, texW, texH);
            compositeGlowPasses(target, x, y, texW, texH, glowColor, strength);
        } finally {
            target.destroyBuffers();
        }
    }

    /**
     * Renders {@code text} (plain white — the glow color is applied later while compositing, via
     * per-vertex tint) into {@code target}, then restores the main render target/viewport/projection
     * exactly as {@link net.minecraft.client.renderer.GameRenderer} itself does after its own
     * off-screen post-effect passes — every state change here MUST be undone before returning,
     * regardless of how drawString behaves, or every draw call for the rest of this frame (ours and
     * every other mod's) inherits a wrong projection/viewport pointed at a since-destroyed texture.
     */
    private void renderGlyphsToTexture(TextureTarget target, String text, int texW, int texH) {
        target.setClearColor(1f, 1f, 1f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        try {
            Matrix4f projection = new Matrix4f().setOrtho(0f, texW, texH, 0f, 1000f, 21000f);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.translation(0f, 0f, -11000f);
                RenderSystem.applyModelViewMatrix();
                GuiGraphics inner = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
                inner.drawString(minecraft.font, text, GLOW_TEXTURE_PADDING, GLOW_TEXTURE_PADDING, 0xFFFFFFFF, false);
                inner.flush();
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
            target.unbindWrite();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    /** Blits {@code target}'s glyph texture back over the real on-screen position at {@link #GLOW_PASS_SCALES}, tinted by {@code glowColor} and scaled by {@code strength}. */
    private void compositeGlowPasses(TextureTarget target, int x, int y, int texW, int texH, int glowColor, double strength) {
        int baseAlpha = Math.min(255, (int) Math.round(strength * 255));
        for (int i = 0; i < GLOW_PASS_SCALES.length; i++) {
            int alpha = Math.round(baseAlpha * GLOW_PASS_ALPHA_FRACTIONS[i]);
            if (alpha <= 0) {
                continue;
            }
            float s = GLOW_PASS_SCALES[i];
            float w = texW * s;
            float h = texH * s;
            float dx = x - GLOW_TEXTURE_PADDING + (texW - w) / 2f;
            float dy = y - GLOW_TEXTURE_PADDING + (texH - h) / 2f;
            blitTintedTexture(target.getColorTextureId(), dx, dy, w, h, (alpha << 24) | (glowColor & 0xFFFFFF));
        }
        RenderSystem.disableBlend();
    }

    /** A tinted textured quad at the current pose — the same shader/vertex format {@code GuiGraphics#innerBlit} uses, just against a raw GL texture id instead of a registered {@link net.minecraft.resources.ResourceLocation}. */
    private void blitTintedTexture(int glTextureId, float x, float y, float width, float height, int argbColor) {
        RenderSystem.setShaderTexture(0, glTextureId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float a = ((argbColor >>> 24) & 0xFF) / 255f;
        float r = ((argbColor >>> 16) & 0xFF) / 255f;
        float g = ((argbColor >>> 8) & 0xFF) / 255f;
        float b = (argbColor & 0xFF) / 255f;
        Matrix4f matrix4f = graphics.pose().last().pose();
        float x2 = x + width;
        float y2 = y + height;
        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.addVertex(matrix4f, x, y, 0f).setUv(0f, 0f).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix4f, x, y2, 0f).setUv(0f, 1f).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix4f, x2, y2, 0f).setUv(1f, 1f).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix4f, x2, y, 0f).setUv(1f, 0f).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
    }

    @Override
    public void drawText(String text, int x, int y, int argbColor, float scale) {
        // pushPose/popPose MUST be paired even if drawString throws partway through (e.g. a
        // malformed component/font-provider edge case) — GuiGraphics' PoseStack is the same shared
        // instance every RenderGuiEvent.Post subscriber this frame draws through. An unmatched
        // pushPose here leaves that translate+scale applied to every later fill()/drawString()/
        // renderItem() call for the rest of the frame (fill() applies the current pose transform to
        // its quad), which is exactly how a tiny rect can end up stretched into a full-screen quad —
        // see the matching comment on drawItem below, and GuiGraphicsRenderContext#resetClip's
        // analogous reasoning for the scissor stack.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            graphics.drawString(minecraft.font, text, 0, 0, argbColor, false);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public int textWidth(String text, float scale) {
        return (int) Math.ceil(minecraft.font.width(text) * scale);
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, float scale) {
        // Same pairing requirement as drawText above: graphics.renderItem() resolves the stack's
        // BakedModel and can throw (a transiently-unbaked/unregistered item id — plausible for a
        // consumer mod's value-key icon resolved mid-sync while its values are updating rapidly,
        // e.g. in response to a fast-repeating trigger). Without this try/finally, that throw skips
        // popPose(), leaving this translate+scale baked into GuiGraphics' shared PoseStack for every
        // remaining fill()/drawString()/renderItem() call this frame (fill() draws its quad through
        // the current pose transform) — a small bar/icon fill elsewhere can then be stretched into
        // covering the whole screen, which self-heals next successful call and re-corrupts on the
        // next throw, producing the rapid full-screen black flashing reported when the fast-repeating
        // trigger retriggers the same edge case.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            graphics.renderItem(stack, 0, 0);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void drawBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        graphics.fill(x, y + 1, x + width, y + height - 1, backgroundColor);
        graphics.fill(x + 1, y, x + width - 1, y + 1, backgroundColor);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, backgroundColor);

        int filled = Mth.clamp((int) (width * fillPct), 0, width);
        if (filled <= 0) {
            return;
        }
        graphics.fill(x, y + 1, Math.min(x + filled, x + width), y + height - 1, fillColor);
        graphics.fill(x + 1, y, Math.min(x + filled, x + width - 1), y + 1, fillColor);
        graphics.fill(x + 1, y + height - 1, Math.min(x + filled, x + width - 1), y + height, fillColor);
        if (filled >= width) {
            graphics.fill(x + width - 1, y, x + width, y + 1, fillColor);
            graphics.fill(x + width - 1, y + height - 1, x + width, y + height, fillColor);
        }
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        int x1 = x;
        int y1 = y;
        int x2 = x + width;
        int y2 = y + height;
        if (!clipStack.isEmpty()) {
            int[] parent = clipStack.peek();
            x1 = Math.max(x1, parent[0]);
            y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]);
            y2 = Math.min(y2, parent[3]);
        }
        x2 = Math.max(x1, x2);
        y2 = Math.max(y1, y2);
        clipStack.push(new int[]{x1, y1, x2, y2});
        graphics.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        clipStack.pop();
        // GuiGraphics keeps its own scissor stack: enableScissor pushes an entry, disableScissor pops
        // one and restores the parent region itself.
        graphics.disableScissor();
    }

    /**
     * Resets the scissor stack to empty and disables scissoring, regardless of how many
     * {@link #pushClip} calls are outstanding. Call this once per frame after a consumer's
     * render pass completes (in a {@code finally} block around it) so that an exception thrown
     * by that consumer between a {@link #pushClip}/{@link #popClip} pair — e.g. a HUD overlay
     * whose backing data mutates mid-render — can never leave the GL scissor rect clamped for
     * the rest of the frame.
     */
    public void resetClip() {
        while (!clipStack.isEmpty()) {
            clipStack.pop();
            graphics.disableScissor();
        }
    }

    @Override
    public void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor) {
        graphics.fill(x + 1, y, x + width - 1, y + height, backgroundColor);
        graphics.fill(x, y + 1, x + 1, y + height - 1, backgroundColor);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, backgroundColor);

        int filled = Mth.clamp((int) (height * fillPct), 0, height);
        if (filled <= 0) {
            return;
        }
        int fillTop = y + height - filled;
        graphics.fill(x + 1, fillTop, x + width - 1, y + height, fillColor);
        graphics.fill(x, fillTop + 1, x + 1, y + height - 1, fillColor);
        graphics.fill(x + width - 1, fillTop + 1, x + width, y + height - 1, fillColor);
        if (filled >= height) {
            graphics.fill(x + 1, y, x + width - 1, y + 1, fillColor);
        }
    }
}
