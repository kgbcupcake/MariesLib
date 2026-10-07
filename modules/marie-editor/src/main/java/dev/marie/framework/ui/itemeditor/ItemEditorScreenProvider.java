package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Lets a consuming mod replace {@link ItemEditorPanel}'s whole body with its own screen — not just
 * add a row to the Values tab the way {@link ItemEditorFieldProvider} does, but switch the editor
 * to an entirely different editing system, e.g. Nourished's "Exclude Food" toggle list. The player
 * reaches it from the File dropdown alongside a "Values" entry that switches back; the dropdown is
 * pure navigation only — the built page's own {@link ItemEditorPage#save()}/{@link
 * ItemEditorPage#revert()} are what the header's Save/Revert controls call while this page is
 * showing. {@link ItemEditorPanel} itself never knows what the screen contains, only how to host
 * it. Register with {@link ItemEditorScreenProviderRegistry#register(String, ItemEditorScreenProvider)}.
 */
@ApiStatus.Experimental
public interface ItemEditorScreenProvider {

    /** Label shown for this screen in the File dropdown, e.g. {@code "Exclude Food"}. */
    String menuLabel();

    /**
     * Builds the screen to show when the player selects {@link #menuLabel()} — called fresh each
     * time, so it always reflects the item currently loaded in the editor.
     *
     * @param modId    the mod the editor instance is scoped to (matches the modId this provider was
     *                 registered under)
     * @param sourceId the resolved registry id of {@code stack} (see {@code
     *                 ItemEditorPanel#sourceId()}), or null if {@code stack} doesn't resolve to one
     * @param stack    the item currently loaded in the editor
     */
    ItemEditorPage buildScreen(String modId, @Nullable String sourceId, ItemStack stack);
}
