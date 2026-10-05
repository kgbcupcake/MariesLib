package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.toolbox.OptionRow;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Lets a consuming mod add its own editor rows to {@link ItemEditorPanel} for item fields
 * MariesLib has no concept of — source classification, excluded-from-classification, effects,
 * colors, or anything else that isn't a {@link dev.marie.framework.api.value.ValueDefinition}
 * float — without {@link ItemEditorPanel} hardcoding any of it.
 *
 * <p>A provider builds its rows from the existing {@link OptionRow} implementations (slider,
 * toggle, button, etc.) — the same ones {@link ItemEditorPanel} itself uses. Each row carries
 * its own commit behavior (typically a {@code ButtonOption("Save"/"Reset", ...)} that writes to
 * whatever registry the consuming mod owns); MariesLib never reads or writes that state itself,
 * it only renders the rows and routes input to them. Register with {@link
 * ItemEditorFieldProviderRegistry#register(String, ItemEditorFieldProvider)}.
 */
@ApiStatus.Experimental
public interface ItemEditorFieldProvider {

    /**
     * Builds the rows this provider contributes for the item currently loaded in the editor.
     *
     * @param modId    the mod the editor instance is scoped to (matches the modId this provider
     *                 was registered under)
     * @param sourceId the resolved registry id of {@code stack} (see {@code
     *                 ItemEditorPanel#sourceId()}), for looking up this mod's own stored state
     * @param stack    the item currently loaded in the editor
     * @return rows to append to the editor's value list, in order; empty if this provider has
     *         nothing to show for this item
     */
    List<OptionRow> buildRows(String modId, String sourceId, ItemStack stack);
}
