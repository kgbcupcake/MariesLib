package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Reports {@link ItemEditorOverlay}'s own box as an area JEI's ingredient list/bookmark overlay
 * should lay out around, regardless of which real screen the overlay is currently drawn on top of -
 * a {@code IGuiScreenHandler} can't do this since it's keyed to one specific screen class, but the
 * overlay can be opened over any of them. Registered from {@link ItemEditorJeiPlugin}.
 */
@ApiStatus.Internal
final class ItemEditorOverlayExclusionHandler implements IGlobalGuiHandler {

    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        Rect2i area = ItemEditorOverlay.occupiedArea();
        return area != null ? List.of(area) : Collections.emptyList();
    }
}
