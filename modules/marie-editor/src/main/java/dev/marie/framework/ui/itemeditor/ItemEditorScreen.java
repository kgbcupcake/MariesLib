package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;

/**
 * Standalone generic item value editor screen: a small fixed-size dialog hosting one {@link
 * ItemEditorPanel}. Opened via {@link ItemEditorApi#open}; a consuming mod wires its own entry
 * point (a button, a client command) to that call rather than constructing this directly.
 */
@ApiStatus.Internal
public final class ItemEditorScreen extends Screen {

    private static final int WIDTH = 250;
    private static final int HEIGHT = 230;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int CONTENT_PADDING = 6;

    @Nullable
    private final Screen parent;
    private final ItemEditorPanel panel;
    private Bounds screenBounds = new Bounds(0, 0, 0, 0);
    private Bounds contentBounds = new Bounds(0, 0, 0, 0);

    public ItemEditorScreen(@Nullable Screen parent, String modId, ItemStack initial, @Nullable RecipeManager recipeManager) {
        super(Component.translatable("marieslib.itemeditor.title"));
        this.parent = parent;
        this.panel = new ItemEditorPanel("item-editor", modId, initial, recipeManager);
    }

    /** The slot's current screen-space area, for a JEI/REI/EMI ghost-ingredient handler to target. Null while the screen hasn't rendered yet this session. */
    @Nullable
    public Rect2i slotScreenArea() {
        Bounds b = panel.slot().bounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return null;
        }
        return new Rect2i(b.x(), b.y(), b.width(), b.height());
    }

    /** Called by a ghost-ingredient/drag-and-drop host when an item is dropped onto this screen's slot. */
    public void acceptDroppedItem(ItemStack stack) {
        panel.retarget(stack);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: a small floating dialog over the world, not a full-screen modal menu.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null) {
            return;
        }
        RenderContext context = new GuiGraphicsRenderContext(graphics, Minecraft.getInstance(), Theme.DARK, partialTick);
        screenBounds = new Bounds((this.width - WIDTH) / 2, (this.height - HEIGHT) / 2, WIDTH, HEIGHT);
        contentBounds = context.drawWindowChrome(screenBounds.x(), screenBounds.y(), screenBounds.width(), screenBounds.height(),
                this.title.getString(), TITLE_COLOR);
        Bounds inner = new Bounds(contentBounds.x() + CONTENT_PADDING, contentBounds.y() + CONTENT_PADDING,
                Math.max(0, contentBounds.width() - 2 * CONTENT_PADDING), Math.max(0, contentBounds.height() - 2 * CONTENT_PADDING));
        context.pushClip(inner.x(), inner.y(), inner.width(), inner.height());
        try {
            panel.render(context, inner);
        } finally {
            context.popClip();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contentBounds.contains((int) mouseX, (int) mouseY) && panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
