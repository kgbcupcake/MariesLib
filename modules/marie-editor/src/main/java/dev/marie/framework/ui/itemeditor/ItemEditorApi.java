package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;

/**
 * Entry point for opening the generic item value editor from a consuming mod's own client-side UI
 * (a button, a client command) — MarieLib itself opens nothing on its own. Everything this editor
 * shows is scoped to {@code modId}: see {@link ItemEditorPanel} for the per-mod {@code MarieContext}
 * scoping that makes this safe in a multi-mod setup.
 */
@ApiStatus.Experimental
public final class ItemEditorApi {

    private ItemEditorApi() {}

    /**
     * Opens the item value editor for {@code item}, owned by {@code modId}. Runs entirely
     * client-side (reads/writes the local {@code source_classifications.json}); call it from
     * client-only code only.
     *
     * @param parent        the screen to return to on close, or {@code null} for none
     * @param modId         the mod whose {@code MarieContext} and value registrations this editor reads/writes
     * @param item          the item to open the editor targeting
     * @param recipeManager the active recipe manager, for recipe-inheritance resolution steps in the trace; {@code null} degrades that step only
     */
    public static void open(@Nullable Screen parent, String modId, ItemStack item, @Nullable RecipeManager recipeManager) {
        Minecraft.getInstance().setScreen(new ItemEditorScreen(parent, modId, item, recipeManager));
    }
}
