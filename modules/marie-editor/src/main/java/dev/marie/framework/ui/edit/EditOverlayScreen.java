package dev.marie.framework.ui.edit;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import dev.marie.framework.ui.scaleconfig.colorpicker.PickerLayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Transparent full-screen overlay that captures all input while one or more {@link MarieComponent}
 * targets are being edited (dragged/resized). Generalizes two earlier consumer-specific overlay
 * screens, which were structurally identical aside from their hint text, exit key,
 * and delegate target. isPauseScreen()=false keeps the world ticking; input is forwarded to every
 * target in {@code targets} rather than handled here — each target is trusted to self-gate on
 * mouseX/mouseY internally (same as today's single-target behavior), so targets not hit by a given
 * event are expected to no-op rather than this screen picking one via its own bounds check.
 */
public final class EditOverlayScreen extends Screen {

    private final List<MarieComponent> targets;
    private final String hintText;
    private final int exitKeyCode;
    private final Runnable onExit;
    private final Theme theme;

    public EditOverlayScreen(MarieComponent target, String hintText, int exitKeyCode, Runnable onExit) {
        this(List.of(target), hintText, exitKeyCode, onExit, Theme.DARK);
    }

    public EditOverlayScreen(MarieComponent target, String hintText, int exitKeyCode, Runnable onExit, Theme theme) {
        this(List.of(target), hintText, exitKeyCode, onExit, theme);
    }

    public EditOverlayScreen(List<MarieComponent> targets, String hintText, int exitKeyCode, Runnable onExit) {
        this(targets, hintText, exitKeyCode, onExit, Theme.DARK);
    }

    public EditOverlayScreen(List<MarieComponent> targets, String hintText, int exitKeyCode, Runnable onExit, Theme theme) {
        super(Component.empty());
        this.targets = targets;
        this.hintText = hintText;
        this.exitKeyCode = exitKeyCode;
        this.onExit = onExit;
        this.theme = theme;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Suppress the default dirt/blur background — the overlay must stay fully transparent. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null) {
            return;
        }
        RenderContext context = new GuiGraphicsRenderContext(graphics, minecraft, theme, partialTick);
        Bounds fullScreen = new Bounds(0, 0, context.screenWidth(), context.screenHeight());
        // Color-picker windows are drawn after every target so no other panel paints over them.
        PickerLayer.begin();
        try {
            for (MarieComponent target : targets) {
                target.render(context, fullScreen);
            }
            PickerLayer.flush();
        } finally {
            PickerLayer.end();
        }
        drawHintBanner(context);
    }

    /** Ported from HudDrawHelpers#drawEditBanner: centered text over a fitted background fill. */
    private void drawHintBanner(RenderContext context) {
        if (minecraft == null || minecraft.font == null || hintText == null || hintText.isEmpty()) {
            return;
        }
        int textW = minecraft.font.width(hintText);
        int bannerX = (context.screenWidth() - textW) / 2 - 4;
        context.fillRect(bannerX, 4, textW + 8, 13, context.theme().color(ThemeKey.EDIT_BANNER_BACKGROUND));
        context.drawText(hintText, bannerX + 4, 8, context.theme().color(ThemeKey.EDIT_BANNER_TEXT), 1f);
    }

    /** exitKeyCode (or Escape) exits edit mode; all other keys are consumed to freeze player movement. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == exitKeyCode || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onExit.run();
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return true;
        }
        for (MarieComponent target : targets) {
            target.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (PickerLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (MarieComponent target : targets) {
            target.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (PickerLayer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        for (MarieComponent target : targets) {
            target.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (PickerLayer.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        for (MarieComponent target : targets) {
            target.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (PickerLayer.mouseScrolled(mouseX, mouseY)) {
            return true;
        }
        for (MarieComponent target : targets) {
            target.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }

    /** Called when Minecraft closes the screen externally (e.g. player death). */
    @Override
    public void onClose() {
        onExit.run();
    }
}
