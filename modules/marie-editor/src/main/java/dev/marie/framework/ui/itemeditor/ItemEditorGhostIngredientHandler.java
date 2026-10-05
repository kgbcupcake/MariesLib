package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Lets a player drag an item from JEI's ingredient list onto an open {@link ItemEditorScreen}'s
 * item slot to target it, instead of only ever editing whatever's already in the slot. Registered
 * from {@link ItemEditorJeiPlugin}.
 */
@ApiStatus.Internal
final class ItemEditorGhostIngredientHandler implements IGhostIngredientHandler<ItemEditorScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(ItemEditorScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        Rect2i area = gui.slotScreenArea();
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
                gui.acceptDroppedItem(stack.get());
            }
        });
    }

    @Override
    public void onComplete() {
        // Nothing to clean up: the slot already shows the dropped item as soon as accept() ran.
    }
}
