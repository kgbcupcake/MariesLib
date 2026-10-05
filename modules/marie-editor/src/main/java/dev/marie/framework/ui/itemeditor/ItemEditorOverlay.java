package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import javax.annotation.Nullable;

/**
 * Draws the item editor on top of whatever screen is already open (the inventory, with JEI's list,
 * most importantly) instead of replacing it the way {@link ItemEditorScreen} does. Minecraft only
 * ever has one active {@code Screen}; this never touches it, so the real screen underneath keeps
 * rendering and handling input completely normally (JEI included, since JEI's own overlay is wired
 * to whichever screen is actually open, not to anything this chooses to draw over it) — this just
 * layers a window on top via {@code ScreenEvent} hooks and claims the input that lands on it.
 *
 * <p>{@link ItemEditorApi#toggleOverlay} is the entry point; a consuming mod's keybind handler
 * calls it specifically when a screen is already open (when none is, {@link ItemEditorApi#open}'s
 * standalone {@link ItemEditorScreen} is the only option, since there'd be nothing to draw over).
 */
@EventBusSubscriber(modid = MarieCore.MOD_ID, value = Dist.CLIENT)
@ApiStatus.Internal
public final class ItemEditorOverlay {

    @Nullable
    private static ItemEditorWindow window;
    /** The screen this overlay was opened on top of; closed automatically once that screen closes. */
    @Nullable
    private static Screen boundScreen;

    private ItemEditorOverlay() {}

    static boolean isOpen() {
        return window != null;
    }

    /** Opens the overlay on top of {@code Minecraft.getInstance().screen} if closed, closes it if already open. Does nothing if no screen is currently open - there'd be nothing to draw over. */
    static void toggle(String modId, ItemStack initial, @Nullable RecipeManager recipeManager) {
        if (window != null) {
            window = null;
            boundScreen = null;
            return;
        }
        Screen current = Minecraft.getInstance().screen;
        if (current == null) {
            return;
        }
        window = new ItemEditorWindow(modId, initial, recipeManager, Component.translatable("marieslib.itemeditor.title"));
        window.init(current.width, current.height);
        boundScreen = current;
    }

    /** Called by a ghost-ingredient/drag-and-drop host when an item is dropped onto the open overlay's slot. No-op if the overlay isn't open. */
    public static void acceptDroppedItem(ItemStack stack) {
        if (window != null) {
            window.acceptDroppedItem(stack);
        }
    }

    /**
     * Whether the overlay is open <em>and</em> the screen it was opened over is still the one
     * actually on screen right now. {@link #window} alone isn't enough: {@code toggle} only ever
     * gets called again to null it out if the player explicitly re-presses the overlay keybind, so
     * switching to some other screen without doing that (e.g. opening the standalone editor, or just
     * closing the inventory) leaves {@link #window} non-null with a now-stale {@link #boundScreen}.
     * The render/input event handlers below already guard against exactly that with their own
     * {@code event.getScreen() != boundScreen} checks; the area-reporting methods need the same
     * guard, since unlike those handlers they're not reached via a {@code ScreenEvent} for a specific
     * screen at all - a global JEI/EMI exclusion-area query runs for whatever screen is active, stale
     * overlay or not.
     */
    private static boolean isActiveOnCurrentScreen() {
        return window != null && boundScreen != null && Minecraft.getInstance().screen == boundScreen;
    }

    /** The open overlay's slot area, for a JEI/REI/EMI ghost-ingredient handler to target. Null if the overlay isn't open on the current screen or hasn't rendered yet this session. */
    @Nullable
    public static Rect2i slotScreenArea() {
        return isActiveOnCurrentScreen() ? window.slotScreenArea() : null;
    }

    /**
     * The open overlay's own occupied area, for JEI's {@code IGlobalGuiHandler}/EMI's {@code
     * EmiExclusionArea} to report regardless of which real screen it's currently drawn over. Without
     * this, neither mod's layout knows to avoid the overlay's box, so JEI's ingredient list or (more
     * visibly) EMI's sidebar/favorites bar can end up positioned underneath it - and since EMI tests
     * a mouse release against its own panel bounds before ever reaching a generic drag-drop handler,
     * releasing a dragged item on what looks like the overlay's slot instead lands on whatever EMI
     * panel happens to occupy that same screen area, e.g. silently adding it as a favorite instead of
     * targeting the slot. Null if the overlay isn't open on the current screen or hasn't rendered yet
     * this session - reported unconditionally on {@code window != null} alone, this leaked a stale
     * exclusion zone onto every other screen (including the standalone editor) once the overlay had
     * been opened at least once without an explicit re-toggle to close it, which could crowd out
     * JEI's/EMI's own display area entirely depending on where it happened to overlap.
     */
    @Nullable
    public static Rect2i occupiedArea() {
        if (!isActiveOnCurrentScreen()) {
            return null;
        }
        return window.occupiedArea(boundScreen.width, boundScreen.height);
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (window == null || event.getScreen() != boundScreen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderContext context = new GuiGraphicsRenderContext(event.getGuiGraphics(), mc, Theme.DARK, event.getPartialTick());
        window.render(context, event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (window == null || event.getScreen() != boundScreen) {
            return;
        }
        if (window.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (window == null || event.getScreen() != boundScreen) {
            return;
        }
        if (window.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (window == null || event.getScreen() != boundScreen) {
            return;
        }
        if (window.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (window == null || event.getScreen() != boundScreen) {
            return;
        }
        if (window.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    /** Closes the overlay once the screen it was drawn on top of closes, so it doesn't linger over whatever opens next. */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == boundScreen) {
            window = null;
            boundScreen = null;
        }
    }
}
