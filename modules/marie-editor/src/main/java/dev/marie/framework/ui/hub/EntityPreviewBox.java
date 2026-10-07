package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import dev.marie.framework.ui.toolbox.OptionStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A framed box showing a {@link LivingEntity} rendered in 3D, turning to follow the mouse like the
 * inventory screen's own player preview, with a name strip beneath it. Ported out of Thermal
 * Systems' Hub home page so any Hub page (a mod's own, or {@link HubInfoPage}) can show the same
 * "entity in a box" preview without re-deriving the rotation math or the EMF-compat workaround.
 */
@ApiStatus.Experimental
public final class EntityPreviewBox {

    private static final int NAME_STRIP_HEIGHT = 14;
    /**
     * Vanilla draws the model at z=50, sized for the inventory's scale of 30. At a larger scale a
     * tilted head reaches below z=0 and fails the depth test against the box fill, so this lifts the
     * whole model forward before drawing it.
     */
    private static final float MODEL_Z_LIFT = 200f;

    private static final int BOX_BACKGROUND = 0xFF15171C;
    private static final int FLOOR_COLOR = 0xFF22262E;

    private EntityPreviewBox() {
    }

    /**
     * @param name      shown in the strip beneath the preview — the entity's display name, or a
     *                  placeholder when {@code entity} is {@code null}.
     * @param emptyHint shown centered in the box instead of a render, when {@code entity} is {@code
     *                  null} or {@code context} isn't backed by a real {@link GuiGraphics} (e.g. a
     *                  headless/test {@link RenderContext}).
     */
    public static void render(RenderContext context, Bounds box, LivingEntity entity, String name, String emptyHint) {
        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 1, BOX_BACKGROUND, accent);

        int innerX = box.x() + 2;
        int innerY = box.y() + 2;
        int innerW = box.width() - 4;
        int innerH = box.height() - 4 - NAME_STRIP_HEIGHT;
        int floorY = innerY + innerH - innerH / 6;
        context.fillRect(innerX, floorY, innerW, innerY + innerH - floorY, FLOOR_COLOR);
        context.fillRect(innerX, innerY + innerH, innerW, 1, theme.color(ThemeKey.BORDER));

        String fitted = OptionStyle.fit(context, name, 1f, innerW - 4);
        context.drawText(fitted, box.x() + (box.width() - context.textWidth(fitted, 1f)) / 2,
                innerY + innerH + (NAME_STRIP_HEIGHT - 8) / 2 + 1, theme.color(ThemeKey.TEXT_PRIMARY), 1f);

        if (entity == null || !(context instanceof GuiGraphicsRenderContext graphicsContext)) {
            String fittedHint = OptionStyle.fit(context, emptyHint, 0.8f, innerW - 4);
            context.drawText(fittedHint, box.x() + (box.width() - context.textWidth(fittedHint, 0.8f)) / 2,
                    innerY + innerH / 2 - 4, theme.color(ThemeKey.TEXT_SECONDARY), 0.8f);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = (double) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        float mouseX = (float) (minecraft.mouseHandler.xpos() * guiScale);
        float mouseY = (float) (minecraft.mouseHandler.ypos() * guiScale);
        int scale = Math.max(10, (int) (innerH * 0.4f));
        GuiGraphics graphics = graphicsContext.graphics();
        graphics.pose().pushPose();
        graphics.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);
        try {
            graphics.pose().translate(0, 0, MODEL_Z_LIFT);
            renderFollowingMouse(graphics, innerX, innerY, innerX + innerW, innerY + innerH, scale, mouseX, mouseY, entity);
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    /**
     * {@link InventoryScreen#renderEntityInInventoryFollowsMouse}, but also overriding the previous-tick
     * rotations ({@code xRotO}, {@code yRotO}, {@code yBodyRotO}). Vanilla only sets the current ones,
     * which is enough for its own renderer (it's called with partial tick 1), but anything that
     * interpolates with the real frame's partial tick would otherwise see the entity's actual in-world
     * look direction instead of the posed one. Drawn through {@link EmfModelCompat} so an EMF animation
     * pack can't turn the head without its hat layer.
     */
    private static void renderFollowingMouse(GuiGraphics graphics, int x1, int y1, int x2, int y2, int scale,
                                              float mouseX, float mouseY, LivingEntity entity) {
        float centerX = (x1 + x2) / 2f;
        float centerY = (y1 + y2) / 2f;
        float yawInput = (float) Math.atan((centerX - mouseX) / 40f);
        float pitchInput = (float) Math.atan((centerY - mouseY) / 40f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitchInput * 20f * Mth.DEG_TO_RAD);
        pose.mul(camera);

        float bodyRot = entity.yBodyRot;
        float bodyRotO = entity.yBodyRotO;
        float yRot = entity.getYRot();
        float yRotO = entity.yRotO;
        float xRot = entity.getXRot();
        float xRotO = entity.xRotO;
        float headRot = entity.yHeadRot;
        float headRotO = entity.yHeadRotO;
        try {
            float posedBody = 180f + yawInput * 20f;
            float posedYaw = 180f + yawInput * 40f;
            float posedPitch = -pitchInput * 20f;
            entity.yBodyRot = posedBody;
            entity.yBodyRotO = posedBody;
            entity.setYRot(posedYaw);
            entity.yRotO = posedYaw;
            entity.setXRot(posedPitch);
            entity.xRotO = posedPitch;
            entity.yHeadRot = posedYaw;
            entity.yHeadRotO = posedYaw;

            float entityScale = entity.getScale();
            Vector3f offset = new Vector3f(0f, entity.getBbHeight() / 2f + 0.0625f * entityScale, 0f);
            EmfModelCompat.withVanillaModel(entity, () -> InventoryScreen.renderEntityInInventory(
                    graphics, centerX, centerY, scale / entityScale, offset, pose, camera, entity));
        } finally {
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotO;
        }
    }
}
