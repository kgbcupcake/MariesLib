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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p>Falls back to a flat item icon of the block (same as any other inventory icon, just scaled up
 * to fill the box) instead of a 3D render whenever a true 3D render wouldn't actually show
 * anything: a block whose baked model has no quads at all (common for a "fancy" block — a chest, a
 * cooking pot with an animated lid — whose real visuals come from a {@code BlockEntityRenderer}
 * tied to an actual placed block entity, which this box has no world/level to provide), or any
 * block whose render call throws for some other reason this box can't anticipate. Better to show
 * the right block as a flat icon than a blank box.
 *
 * <p>Scales to the model's own measured bounds (from its baked quads' vertex positions) rather than
 * assuming every block's geometry sits in the usual 1×1×1 cube: a multiblock-looking machine whose
 * model extends past that (e.g. Create's Mechanical Mixer, whose rotating cage reaches well above a
 * normal block's top face) would otherwise render oversized and clip against the box's scissor —
 * exactly the same "reads as oversized/cut off" problem the fixed 0.52 scale was already once tuned
 * to fix for an ordinary block, which a taller model breaks again. Measured per {@link Block} once
 * and cached, since scanning every quad's vertices isn't free and this renders every frame.
 */
@ApiStatus.Experimental
public final class RecipeStationBlockBox {

    private static final int BOX_BACKGROUND = 0xFF15171C;
    /** Same reasoning as {@code EntityPreviewBox.MODEL_Z_LIFT}: keeps the model's depth test clear of the box's own flat fill. */
    private static final float MODEL_Z_LIFT = 200f;
    /** One full turn every 8 seconds — slow enough to read as a calm turntable, not a spinning icon. */
    private static final long ROTATION_PERIOD_MS = 8000L;
    /** How much of the box's smaller dimension an ordinary 1×1×1 block's model fills — what the old fixed scale factor was tuned to; {@link #renderBlock} now derives the actual scale from this relative to each model's own measured extent, so a taller/wider model still fits instead of always being scaled as if it were exactly one block tall. */
    private static final float MODEL_FILL = 0.52f;
    /** How much of the box's smaller dimension the fallback icon fills — bigger than {@link #MODEL_FILL}, since a flat icon reads smaller than a tilted 3D model of the same nominal size. */
    private static final float FALLBACK_ICON_FILL = 0.8f;
    private static final String EMPTY_HINT = "?";
    /** No quads at all — the model-bounds equivalent of "nothing to render", distinct from {@link #DEFAULT_BOUNDS} (bounds couldn't be measured, but the model might still render something). */
    private static final float[] EMPTY_BOUNDS = new float[0];
    /** Assumed bounds when measuring a model's quads throws — the ordinary 1×1×1 cube every block's model is nominally built in, so an unmeasurable model still renders at the old fixed scale rather than being treated as empty. */
    private static final float[] DEFAULT_BOUNDS = {0f, 0f, 0f, 1f, 1f, 1f};
    private static final Map<Block, float[]> MODEL_BOUNDS_CACHE = new ConcurrentHashMap<>();

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
        if (recipe.stationType().prefersFlatIcon()) {
            renderItemFallback(context, box, block);
            return;
        }
        renderBlock(context, box, block);
    }

    private static void renderBlock(RenderContext context, Bounds box, Block block) {
        if (box.width() <= 0 || box.height() <= 0) {
            return;
        }
        BlockState state = block.defaultBlockState();
        float[] bounds = modelBounds(block, state);
        if (bounds.length == 0 || !(context instanceof GuiGraphicsRenderContext graphicsContext)) {
            renderItemFallback(context, box, block);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float centerX = box.x() + box.width() / 2f;
        float centerY = box.y() + box.height() / 2f;
        long nowMs = System.currentTimeMillis();
        float yaw = (nowMs % ROTATION_PERIOD_MS) / (float) ROTATION_PERIOD_MS * (float) (Math.PI * 2);

        // The model's own measured extent, largest axis wins — scaling to this (instead of always
        // assuming a 1×1×1 block) is what keeps a taller/wider model (e.g. Create's Mechanical
        // Mixer) from overflowing the box the way a fixed scale factor would. See the class doc.
        float extentX = bounds[3] - bounds[0];
        float extentY = bounds[4] - bounds[1];
        float extentZ = bounds[5] - bounds[2];
        float maxExtent = Math.max(extentX, Math.max(extentY, extentZ));
        // Floored at 1 (an ordinary 1×1×1 block), never raised: a model smaller than one block (a
        // thin campfire, say) keeps the old fixed-0.52 size instead of being blown up to fill the
        // box, and this is also the guard against a measurement that came out smaller than it should
        // have — whatever the cause, a scale this floor can only ever shrink a model relative to the
        // old fixed scale, never grow one past it, which is the one direction that actually matters:
        // an underestimated extent inflating the scale is a regression, an overestimated one shrinking
        // it just looks a little small, not clipped/broken.
        if (!(maxExtent >= 1f)) {
            maxExtent = 1f;
        }
        float modelCenterX = (bounds[0] + bounds[3]) / 2f;
        float modelCenterY = (bounds[1] + bounds[4]) / 2f;
        float modelCenterZ = (bounds[2] + bounds[5]) / 2f;

        GuiGraphics graphics = graphicsContext.graphics();
        graphics.pose().pushPose();
        graphics.enableScissor(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
        boolean rendered = false;
        try {
            graphics.pose().translate(centerX, centerY, MODEL_Z_LIFT);
            float scale = Math.min(box.width(), box.height()) * MODEL_FILL / maxExtent;
            // Flip Y: model space has +Y up, GUI space has +Y down.
            graphics.pose().scale(scale, -scale, scale);
            // A fixed downward tilt (steeper than a block's own inventory-icon angle, so more of the
            // top face — where most of a station's "tells" are, like a cooking pot's contents — is
            // visible) plus a slow, constant yaw spin — see the class doc for why this turns on its
            // own instead of following the mouse.
            graphics.pose().mulPose(new Quaternionf().rotateX((float) (Math.PI * 2 / 9)));
            graphics.pose().mulPose(new Quaternionf().rotateY(yaw));
            graphics.pose().translate(-modelCenterX, -modelCenterY, -modelCenterZ);
            minecraft.getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            graphics.flush();
            rendered = true;
        } catch (Exception ignored) {
            // Some block-specific quirk this box can't anticipate (a model that needs render-time
            // data this bare BlockState doesn't carry, e.g.) — fall back below instead of leaving the
            // box silently blank, which is what a caught exception here would otherwise look like.
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
        if (!rendered) {
            renderItemFallback(context, box, block);
        }
    }

    /**
     * {@code block}'s baked model's own measured bounds — {@code {minX, minY, minZ, maxX, maxY,
     * maxZ}} in model space, read straight off its baked quads' vertex positions rather than
     * assumed to be the usual 1×1×1 cube — or {@link #EMPTY_BOUNDS} for a block whose model is
     * empty by design (e.g. {@code "builtin/entity"}, used by blocks like chests whose real visuals
     * come entirely from a {@code BlockEntityRenderer}). Checked before attempting a 3D render at
     * all, since an empty model renders nothing but throws nothing either — the render-time {@code
     * try/catch} alone wouldn't catch this case. Cached per {@link Block} (assumes {@link
     * #renderBlock} only ever renders a block's default state, which it does), since this renders
     * every frame and scanning every quad's vertex data isn't free.
     */
    private static float[] modelBounds(Block block, BlockState state) {
        return MODEL_BOUNDS_CACHE.computeIfAbsent(block, b -> computeModelBounds(state));
    }

    private static float[] computeModelBounds(BlockState state) {
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            RandomSource random = RandomSource.create(42L);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            boolean any = false;
            Direction[] faces = Direction.values();
            for (int i = 0; i <= faces.length; i++) {
                // i == faces.length covers the "no particular face" quad list (direction == null) —
                // same two-pass shape as the old per-direction scan this replaced.
                Direction direction = i < faces.length ? faces[i] : null;
                for (BakedQuad quad : model.getQuads(state, direction, random)) {
                    int[] data = quad.getVertices();
                    int stride = data.length / 4;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int base = vertex * stride;
                        float x = Float.intBitsToFloat(data[base]);
                        float y = Float.intBitsToFloat(data[base + 1]);
                        float z = Float.intBitsToFloat(data[base + 2]);
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                        any = true;
                    }
                }
            }
            if (!any) {
                return EMPTY_BOUNDS;
            }
            float[] result = {minX, minY, minZ, maxX, maxY, maxZ};
            return isSane(result) ? result : DEFAULT_BOUNDS;
        } catch (Exception ignored) {
            // Can't tell — assume an ordinary 1×1×1 block and let the render attempt's own try/catch
            // decide; this check exists only to catch the common empty-model case and measure an
            // unusually-shaped model, not to be a second source of truth for every edge case.
            return DEFAULT_BOUNDS;
        }
    }

    /** Rejects a measured bounds array that can't have come from real block geometry (a non-finite vertex float, or a model somehow bigger than a generous multiblock-sized sanity ceiling) — {@link #DEFAULT_BOUNDS} is a safer fallback than scaling/centering on garbage numbers. */
    private static boolean isSane(float[] bounds) {
        for (float value : bounds) {
            if (!Float.isFinite(value) || Math.abs(value) > 8f) {
                return false;
            }
        }
        return true;
    }

    private static void renderItemFallback(RenderContext context, Bounds box, Block block) {
        ItemStack stack = new ItemStack(block.asItem());
        if (stack.isEmpty()) {
            return;
        }
        float targetSize = Math.min(box.width(), box.height()) * FALLBACK_ICON_FILL;
        float iconScale = targetSize / 16f;
        int x = box.x() + Math.round((box.width() - targetSize) / 2f);
        int y = box.y() + Math.round((box.height() - targetSize) / 2f);
        context.drawItem(stack, x, y, iconScale);
    }
}
