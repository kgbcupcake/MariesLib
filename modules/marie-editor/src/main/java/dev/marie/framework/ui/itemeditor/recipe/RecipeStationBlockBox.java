package dev.marie.framework.ui.itemeditor.recipe;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * A small framed box rendering a {@link RecipeDisplay}'s station block in 3D — the crafting table,
 * furnace, cooking pot, monster pot, ... whatever block the matched recipe is actually made at —
 * turning on its own at a slow, constant rate, like a display-case turntable, rather than tracking
 * the mouse the way {@link dev.marie.framework.ui.hub.EntityPreviewBox}'s entity does: an entity
 * turning to "look at" the cursor reads as alive and responsive, but a crafting-station block has
 * no such relationship to the player's attention, so following the mouse there just looked like
 * the model sliding around instead of presenting itself. Separate from {@link
 * RecipeStationPreviewBox} (the JEI/EMI-style ingredient grid): that box answers "what goes in and
 * what comes out", this one answers "where do I even make this" — showing both lets a player
 * recognize an unfamiliar station (e.g. Dungeon's Delight's monster pot) at a glance instead of
 * having to already know what block a given {@link RecipeStationType} refers to.
 */
@ApiStatus.Experimental
public final class RecipeStationBlockBox {

    private static final int BOX_BACKGROUND = 0xFF15171C;
    /** Same reasoning as {@code EntityPreviewBox.MODEL_Z_LIFT}: keeps the model's depth test clear of the box's own flat fill. */
    private static final float MODEL_Z_LIFT = 200f;
    /** One full turn every 8 seconds — slow enough to read as a calm turntable, not a spinning icon. */
    private static final long ROTATION_PERIOD_MS = 8000L;
    private static final String EMPTY_HINT = "?";

    private RecipeStationBlockBox() {
    }

    /** No-op (draws nothing) when {@code recipe} is {@code null} — a host with nothing to show shouldn't reserve space for this box at all. */
    public static void render(RenderContext context, Bounds box, @Nullable RecipeDisplay recipe) {
        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 1, BOX_BACKGROUND, accent);

        if (recipe == null) {
            return;
        }
        Block block = recipe.stationType().block();
        if (block == null) {
            String text = EMPTY_HINT;
            context.drawText(text, box.x() + (box.width() - context.textWidth(text, 0.8f)) / 2,
                    box.y() + (box.height() - 7) / 2, theme.color(ThemeKey.TEXT_SECONDARY), 0.8f);
            return;
        }
        renderBlock(context, box, block);
    }

    private static void renderBlock(RenderContext context, Bounds box, Block block) {
        if (box.width() <= 0 || box.height() <= 0 || !(context instanceof GuiGraphicsRenderContext graphicsContext)) {
            return;
        }
        BlockState state = block.defaultBlockState();

        Minecraft minecraft = Minecraft.getInstance();
        float centerX = box.x() + box.width() / 2f;
        float centerY = box.y() + box.height() / 2f;
        long nowMs = System.currentTimeMillis();
        float yaw = (nowMs % ROTATION_PERIOD_MS) / (float) ROTATION_PERIOD_MS * (float) (Math.PI * 2);

        GuiGraphics graphics = graphicsContext.graphics();
        graphics.pose().pushPose();
        graphics.enableScissor(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
        try {
            graphics.pose().translate(centerX, centerY, MODEL_Z_LIFT);
            float scale = Math.min(box.width(), box.height()) * 0.52f;
            // Flip Y: model space has +Y up, GUI space has +Y down.
            graphics.pose().scale(scale, -scale, scale);
            // A fixed downward tilt (steeper than a block's own inventory-icon angle, so more of the
            // top face — where most of a station's "tells" are, like a cooking pot's contents — is
            // visible) plus a slow, constant yaw spin — see the class doc for why this turns on its
            // own instead of following the mouse.
            graphics.pose().mulPose(new Quaternionf().rotateX((float) (Math.PI * 2 / 9)));
            graphics.pose().mulPose(new Quaternionf().rotateY(yaw));
            graphics.pose().translate(-0.5, -0.5, -0.5);
            minecraft.getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            graphics.flush();
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }
}
