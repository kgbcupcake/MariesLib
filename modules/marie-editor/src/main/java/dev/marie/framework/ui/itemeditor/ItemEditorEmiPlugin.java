package dev.marie.framework.ui.itemeditor;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.marie.framework.api.ApiStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * EMI counterpart to {@link ItemEditorJeiPlugin}: lets a player drag an item out of EMI's list onto
 * an open {@link ItemEditorScreen} or {@link ItemEditorOverlay} the same way JEI's ghost-ingredient
 * handler does. EMI has no per-screen-type registration for a plain, non-container {@code Screen}
 * the way JEI's {@code addGuiScreenHandler} does — {@code addGenericDragDropHandler} is keyed to the
 * generic {@code Screen} class already, and EMI, unlike JEI, doesn't need telling where a non-
 * container screen's own GUI area is to show its list there in the first place, so one handler
 * here covers both hosts with no extra registration (EMI never shows anything at all, standalone
 * host included, over a {@code Screen} that isn't an {@code AbstractContainerScreen} or its own
 * {@code RecipeScreen} - a hard limitation of EMI itself, not something a plugin registration can
 * opt back into). {@link ItemEditorOverlayEmiExclusionZone} additionally keeps EMI's own
 * sidebars/favorites bar from laying out underneath {@link ItemEditorOverlay}'s box when it's open
 * over a screen EMI does show on, which would otherwise swallow a release there as e.g. "add to
 * favorites" before it ever reaches the drag-drop handler below.
 *
 * <p>Discovered via {@link EmiEntrypoint} (a separate plugin from marie-ui's {@code MarieEmiPlugin},
 * since marie-ui has no dependency on marie-editor) — on NeoForge, EMI's actual discovery mechanism
 * ({@code EmiAgnosNeoForge#getPluginsAgnos}) scans every mod file's class annotations for this one
 * via NeoForge's own {@code ModFileScanData}; it never consults {@code ServiceLoader}/{@code
 * META-INF/services} at all (confirmed against EMI's own source - the string {@code
 * "ServiceLoader"} doesn't appear anywhere in it), unlike JEI's {@code @JeiPlugin} which genuinely
 * is classpath-annotation-scanned the way an earlier version of this comment assumed {@code
 * EmiPlugin} was too. Without this annotation, {@code register} below is simply never called.
 */
@ApiStatus.Internal
@EmiEntrypoint
public final class ItemEditorEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea(new ItemEditorOverlayEmiExclusionZone());
        registry.addGenericDragDropHandler(new EmiDragDropHandler<Screen>() {
            @Override
            public boolean dropStack(Screen screen, EmiIngredient ingredient, int x, int y) {
                Rect2i area;
                Consumer<ItemStack> accept;
                if (screen instanceof ItemEditorScreen itemEditorScreen) {
                    area = itemEditorScreen.slotScreenArea();
                    accept = itemEditorScreen::acceptDroppedItem;
                } else if (ItemEditorOverlay.isOpen()) {
                    area = ItemEditorOverlay.slotScreenArea();
                    accept = ItemEditorOverlay::acceptDroppedItem;
                } else {
                    return false;
                }
                if (area == null || !area.contains(x, y)) {
                    return false;
                }
                List<EmiStack> stacks = ingredient.getEmiStacks();
                if (stacks.isEmpty()) {
                    return false;
                }
                ItemStack stack = stacks.get(0).getItemStack();
                if (stack.isEmpty()) {
                    return false;
                }
                accept.accept(stack);
                return true;
            }
        });
    }
}
