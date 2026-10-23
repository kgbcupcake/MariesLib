package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * A screen {@link ItemEditorScreenProvider} builds to replace {@link ItemEditorPanel}'s body,
 * with its own Save/Revert behavior wired to the header's Save/Revert controls (see {@link
 * ItemEditorPanel#renderMenuBar}) whenever this page is the one currently showing — the File
 * dropdown itself is pure navigation between pages and never saves or reverts anything on its own.
 *
 * <p>Both default to a no-op: a page with nothing to persist (e.g. a pure info/readout screen)
 * only needs to implement {@link #render}/input handling and can leave Save/Revert doing nothing.
 * A page that does implement them is responsible for refreshing its own displayed state after
 * {@link #revert()} — unlike {@link #retarget}, switching pages doesn't rebuild this instance, so
 * a revert that doesn't update whatever field the page renders from would leave stale values on
 * screen even though the underlying data changed back.
 */
@ApiStatus.Experimental
public interface ItemEditorPage extends MarieComponent {

    /** Persists this page's current edits. Called when the header's Save control is clicked while this page is active. */
    default void save() {}

    /** Discards this page's edits back to what's persisted, and refreshes whatever this page renders from. Called when the header's Revert control is clicked while this page is active. */
    default void revert() {}

    /**
     * Draws content that must escape this page's own clipped content area — e.g. a floating
     * color-picker window positioned beside the item editor's box the way {@link
     * dev.marie.framework.ui.scaleconfig.colorpicker.PickerWindow} does for a {@code
     * ScaleConfigPanel} entry. Called by the host <em>after</em> {@link #render} and after the
     * host's own clip around this page's body has already been popped, with {@code screenBounds}
     * covering the whole screen rather than just this page's body — the same way {@code
     * ItemEditorPanel#renderFileMenuOverlay} draws the File dropdown outside {@link #render}'s own
     * bounds. A page with nothing to draw outside its own body leaves this a no-op.
     */
    default void renderOverlay(RenderContext context, Bounds screenBounds) {}

    /** Mouse-click counterpart to {@link #renderOverlay}, offered before the host's normal (clipped) hit-testing so a floating overlay positioned outside this page's body can still be clicked. */
    default boolean overlayMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean overlayMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean overlayMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean overlayMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Offered an item dragged in from JEI/EMI (or dropped onto the editor's own slot) before
     * {@link ItemEditorPanel#retarget} runs its normal single-item behavior — swapping the targeted
     * item, resetting back to the built-in Values view, and discarding whichever mod screen was
     * active. Returning {@code true} claims the drop entirely: the panel does none of that, and this
     * page is responsible for whatever it wants to do with {@code stack} itself (e.g. appending it
     * to a multi-item selection list instead of replacing a single target). The default {@code
     * false} is the existing behavior every other page already has: the panel retargets as normal.
     */
    default boolean acceptDraggedItem(ItemStack stack) {
        return false;
    }

    /**
     * Handed a callback, right after this page is built, that switches the editor to a different
     * item the same way dragging one in from JEI/EMI would ({@link ItemEditorPanel#retarget}) — for
     * a page that lists other items (e.g. a "recently edited" log) and wants clicking one to jump
     * straight to editing it, without needing a reference to the host panel itself. The second
     * argument picks which page to land on for the newly-targeted item: {@code null} for the
     * built-in Values view (the default every other retarget already lands on), or a specific
     * {@link ItemEditorScreenProvider} to open that mod's own page directly — e.g. jumping straight
     * to the page a listed item's override actually lives on, instead of Values regardless. A page
     * with nothing to navigate to leaves this a no-op.
     */
    default void attachRetargetHandler(BiConsumer<ItemStack, ItemEditorScreenProvider> retarget) {}
}
