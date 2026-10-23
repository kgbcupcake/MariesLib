package dev.marie.framework.ui.itemeditor;

import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.widget.Bounds;
import dev.marie.framework.api.ApiStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.function.Consumer;

/**
 * EMI counterpart to {@link ItemEditorOverlayExclusionHandler}: reports {@link
 * ItemEditorOverlay}'s own box as an area EMI's sidebars (including its persistent favorites bar)
 * should lay out around, regardless of which real screen the overlay is drawn on top of. Without
 * this, EMI has no reason to avoid the overlay's box, so a sidebar panel can end up positioned
 * underneath it - and since EMI tests a mouse release against its own panel bounds before ever
 * reaching {@link ItemEditorEmiPlugin}'s generic drag-drop handler, releasing a dragged item on what
 * looks like the overlay's slot instead lands on whatever EMI panel happens to occupy that same
 * screen area (e.g. silently adding it as a favorite instead of targeting the slot). Registered
 * from {@link ItemEditorEmiPlugin}.
 */
@ApiStatus.Internal
final class ItemEditorOverlayEmiExclusionZone implements EmiExclusionArea<Screen> {

    @Override
    public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
        Rect2i area = ItemEditorOverlay.occupiedArea();
        if (area != null) {
            consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        }
    }
}
