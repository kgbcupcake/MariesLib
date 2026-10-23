package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Lets a player drag an item from JEI's ingredient list onto {@link ItemEditorOverlay}'s slot when
 * it's open on top of some other screen (e.g. the inventory). Registered against the generic {@link
 * Screen} class rather than one specific screen type, since the overlay can be drawn over whichever
 * screen was already open when it was toggled on — {@link ItemEditorOverlay#isOpen} gates whether
 * this offers a target at all. Registered from {@link ItemEditorJeiPlugin}, alongside (not instead
 * of) {@link ItemEditorGhostIngredientHandler}, which still covers standalone {@link
 * ItemEditorScreen}.
 */
@ApiStatus.Internal
final class ItemEditorOverlayGhostIngredientHandler implements IGhostIngredientHandler<Screen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(Screen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!ItemEditorOverlay.isOpen()) {
            return List.of();
        }
        Optional<ItemStack> stack = ingredient.getItemStack();
        Rect2i area = ItemEditorOverlay.slotScreenArea();
        if (stack.isEmpty() || area == null) {
            return List.of();
        }
        return List.of(new Target<I>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ignored) {
                ItemEditorOverlay.acceptDroppedItem(stack.get());
            }
        });
    }

    @Override
    public void onComplete() {
        // Nothing to clean up: the slot already shows the dropped item as soon as accept() ran.
    }
}
