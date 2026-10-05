package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A purely client-side, never-networked {@link AbstractContainerMenu} whose only purpose is to give
 * {@link ItemEditorScreen} one real {@link Slot} to be an {@code AbstractContainerScreen} with -
 * EMI shows its sidebar/favorites bar (and consults any registered generic drag-drop handler or
 * exclusion area) over a screen at all only when {@code EmiScreenBase.of} resolves it to a non-empty
 * base, which it never does for a plain {@code Screen}; it does for an {@code
 * AbstractContainerScreen} with a non-empty {@code menu.slots}. No {@code MenuType} is registered
 * and this is never opened through the server protocol (constructed directly by {@link
 * ItemEditorScreen}'s constructor, matching how vanilla's own player-inventory menu uses a {@code
 * null} type for a menu that isn't server-opened) - the one slot it holds exists only to be seen as
 * present, never to actually move an item through: {@link #mayPlace}/{@link #mayPickup} are both
 * hard {@code false}, and {@link ItemEditorScreen} never calls {@code AbstractContainerScreen}'s own
 * slot-click handling (which would otherwise send a network packet the server has no matching menu
 * to receive), so nothing here ever reaches the server.
 */
@ApiStatus.Internal
final class ItemEditorMenu extends AbstractContainerMenu {

    private final SimpleContainer container;

    ItemEditorMenu(ItemStack initial) {
        super(null, 0);
        this.container = new SimpleContainer(1);
        this.container.setItem(0, initial == null ? ItemStack.EMPTY : initial.copy());
        this.addSlot(new Slot(container, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
    }

    /** Keeps the display-only slot's contents in step with whatever item the editor is actually targeting. */
    void setTarget(ItemStack stack) {
        container.setItem(0, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
