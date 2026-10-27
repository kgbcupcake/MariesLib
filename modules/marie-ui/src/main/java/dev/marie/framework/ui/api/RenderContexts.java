package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

/**
 * Turns a vanilla {@link GuiGraphics} into a MariesLib {@link RenderContext}, so your own screen or
 * HUD layer can draw MariesLib components and edit hosts.
 *
 * <pre>{@code
 * public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
 *     RenderContext context = RenderContexts.of(g, partialTick);
 *     myComponent.render(context, bounds);
 * }
 * }</pre>
 *
 * <p>Wrap the pass in {@code try/finally} and call {@link #resetClip} so a throwing component can't
 * leave a clip active for the rest of the frame. To draw something {@link RenderContext} has no
 * primitive for (an entity model, say), {@link #graphics} hands back the underlying
 * {@code GuiGraphics}.
 */
@ApiStatus.Experimental
public final class RenderContexts {

    private RenderContexts() {}

    /** A context over {@code graphics} using the dark theme. Create one per frame. */
    public static RenderContext of(GuiGraphics graphics, float partialTick) {
        return of(graphics, Theme.DARK, partialTick);
    }

    public static RenderContext of(GuiGraphics graphics, Theme theme, float partialTick) {
        return new GuiGraphicsRenderContext(graphics, Minecraft.getInstance(), theme, partialTick);
    }

    /**
     * Pops every clip still open on {@code context}, however many {@code pushClip} calls are
     * unmatched. Call it once per frame in a {@code finally} after your render pass. A no-op for
     * contexts that didn't come from {@link #of}.
     */
    public static void resetClip(RenderContext context) {
        if (context instanceof GuiGraphicsRenderContext graphicsContext) {
            graphicsContext.resetClip();
        }
    }

    /** The {@code GuiGraphics} behind {@code context}, or empty if it isn't backed by one. */
    public static Optional<GuiGraphics> graphics(RenderContext context) {
        return context instanceof GuiGraphicsRenderContext graphicsContext
                ? Optional.of(graphicsContext.graphics())
                : Optional.empty();
    }
}
