package dev.marie.framework.ui.component.widgets;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * A single slot-sized square that displays an {@link ItemStack} icon and accepts a new one via
 * {@link #setItem}, for anything that lets a player target an item by dropping it in rather than
 * typing an id — a JEI/REI/EMI ghost-ingredient handler, or a plain inventory drag, calls {@link
 * #setItem} on drop. Read-only display only; this component itself never reads from the player's
 * inventory.
 */
@ApiStatus.Experimental
public final class ItemSlotComponent implements MarieComponent {

    /** Outer size of the slot, border included. */
    public static final int SIZE = 20;
    private static final int ICON_INSET = 2;

    private final String id;
    private ItemStack item;
    private Consumer<ItemStack> onItemChanged = stack -> {};
    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public ItemSlotComponent(String id, ItemStack initial) {
        this.id = id;
        this.item = initial == null ? ItemStack.EMPTY : initial;
    }

    /** Runs whenever {@link #setItem} places a new (non-empty) stack — including from {@link #acceptDrop}. */
    public ItemSlotComponent onItemChanged(Consumer<ItemStack> listener) {
        this.onItemChanged = listener != null ? listener : stack -> {};
        return this;
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        this.item = stack == null ? ItemStack.EMPTY : stack;
        if (!this.item.isEmpty()) {
            onItemChanged.accept(this.item);
        }
    }

    /** Same as {@link #setItem}, named for drop-target callers (a ghost-ingredient handler, a drag-and-drop host). Ignores an empty/null drop. */
    public boolean acceptDrop(ItemStack dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return false;
        }
        setItem(dropped.copy());
        return true;
    }

    /** This frame's screen-space bounds, for a host that needs to expose this slot as an external drop target (e.g. a JEI ghost-ingredient area). */
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.fixed(SIZE, SIZE);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        this.bounds = bounds;
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1,
                context.theme().color(ThemeKey.PANEL_BACKGROUND), context.theme().color(ThemeKey.BORDER));
        if (!item.isEmpty()) {
            context.drawItem(item, bounds.x() + ICON_INSET, bounds.y() + ICON_INSET, 1f);
        }
    }
}
