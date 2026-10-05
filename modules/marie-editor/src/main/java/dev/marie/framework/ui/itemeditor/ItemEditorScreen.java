package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Standalone generic item value editor screen: hosts one {@link ItemEditorWindow} (the actual
 * draggable/resizable box, styled and persisted the same way every other MarieLib/consumer HUD box
 * is) as the sole screen, for the case where nothing else is already open. Opened via {@link
 * ItemEditorApi#open}; a consuming mod wires its own entry point (a button, a client command) to
 * that call rather than constructing this directly.
 *
 * <p>When a screen (e.g. the inventory, with JEI's list) is already open, {@link
 * ItemEditorApi#toggleOverlay} draws the same {@link ItemEditorWindow} on top of it instead via
 * {@link ItemEditorOverlay}, so that screen (and JEI) stays open rather than being replaced by this
 * one — this class is only the no-screen-open path.
 *
 * <p>Extends {@code AbstractContainerScreen} (backed by {@link ItemEditorMenu}, a display-only menu
 * that's never opened through the server) purely so EMI recognizes this as a valid host at all -
 * {@code EmiScreenBase.of} resolves empty, and EMI shows nothing (sidebar, favorites bar, any
 * registered generic handler) at all, for a plain {@code Screen}. Every one of {@code
 * AbstractContainerScreen}'s own slot/network-click behaviors is bypassed below (render, mouse
 * input and {@code keyPressed} are all fully overridden rather than deferring to it) since nothing
 * here ever needs - or safely could - round-trip to a server that never opened a matching menu.
 */
@ApiStatus.Internal
public final class ItemEditorScreen extends AbstractContainerScreen<ItemEditorMenu> {

    @Nullable
    private final Screen parent;
    private final ItemEditorWindow window;

    public ItemEditorScreen(@Nullable Screen parent, String modId, ItemStack initial, @Nullable RecipeManager recipeManager) {
        super(new ItemEditorMenu(initial), Minecraft.getInstance().player.getInventory(), Component.translatable("marieslib.itemeditor.title"));
        this.parent = parent;
        this.window = new ItemEditorWindow(modId, initial, recipeManager, this.title);
    }

    @Override
    protected void init() {
        window.init(this.width, this.height);
    }

    /** The slot's current screen-space area, for a JEI/REI/EMI ghost-ingredient handler to target. Null while the screen hasn't rendered yet this session. */
    @Nullable
    public Rect2i slotScreenArea() {
        return window.slotScreenArea();
    }

    /**
     * This window's own occupied area, reported to JEI (via {@code addGuiScreenHandler} in {@link
     * ItemEditorJeiPlugin}) as the one region of the screen it shouldn't lay its ingredient list
     * over. Without this, JEI has no {@code IGuiProperties} for a plain {@link Screen} and never
     * shows its overlay at all here, leaving nothing to drag from onto the slot.
     */
    public Rect2i occupiedArea() {
        return window.occupiedArea(this.width, this.height);
    }

    /** Called by a ghost-ingredient/drag-and-drop host when an item is dropped onto this screen's slot. */
    public void acceptDroppedItem(ItemStack stack) {
        window.acceptDroppedItem(stack);
        this.getMenu().setTarget(stack);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: a small floating dialog over the world, not a full-screen modal menu.
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Required override (AbstractContainerScreen's own slot-grid background); never invoked
        // since #render below never calls super.render.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately not AbstractContainerScreen#render: it renders this.menu's slots (a real
        // vanilla slot texture at a leftPos/topPos we never set up) and populates hoveredSlot,
        // which would re-enable the hotbar-swap/clone/throw handling in #keyPressed below that this
        // class otherwise bypasses entirely. ItemEditorMenu's one slot exists only for EMI's
        // AbstractContainerScreen check, never to actually be drawn or clicked.
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        // EMI reads this screen's "container area" via a mixin accessor straight onto
        // leftPos/topPos/imageWidth/imageHeight (AbstractContainerScreen#init normally sets these
        // from a fixed imageWidth/imageHeight, which we never call) rather than through anything
        // this class exposes - without keeping them in sync with the window's real, user-movable
        // box every frame, EMI positions its sidebar/favorites bar around a stale 0,0/176x166
        // region instead of the window's actual area.
        Rect2i occupied = window.occupiedArea(this.width, this.height);
        this.leftPos = occupied.getX();
        this.topPos = occupied.getY();
        this.imageWidth = occupied.getWidth();
        this.imageHeight = occupied.getHeight();
        if (minecraft == null) {
            return;
        }
        RenderContext context = new GuiGraphicsRenderContext(graphics, Minecraft.getInstance(), Theme.DARK, partialTick);
        window.render(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Not AbstractContainerScreen#mouseClicked: its slot-click handling would send a network
        // packet for a menu the server never opened. this.window is the only thing on this screen
        // that ever handles input.
        return window.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return window.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return window.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return window.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Not AbstractContainerScreen#keyPressed: replicates plain Screen's own default (ESC closes)
        // instead of also picking up its hotbar-swap/clone/throw-on-hovered-slot handling, which
        // ItemEditorMenu's display-only slot was never meant to support.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        // Not super.onClose(): AbstractContainerScreen's version calls player.closeContainer(),
        // sending a close packet for a menu the server was never told about.
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
