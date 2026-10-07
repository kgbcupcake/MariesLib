package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

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
}
