package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Converts a module's screen bounds to the {@link ComponentState} a {@link ModuleEditHost} saves, and
 * back again for rendering. Built-ins: {@link PanelRelativePlacement} (local units inside a moving,
 * scaled panel), {@link ScreenAnchoredPlacement} (a HUD box anchored to a screen corner) and {@link
 * PinnedPlacement} (top-left fixed, only size saved). Implement it for any other layout.
 *
 * <p>Every strategy should copy fields it doesn't own (e.g. {@code contentScale}) through from
 * {@code existing}, so a drag never resets settings stored on the same record.
 */
@ApiStatus.Experimental
public interface PlacementStrategy {

    /** The state to save for a box committed at {@code committed}; {@code existing} is null when nothing is saved yet. Return null to skip saving. */
    ComponentState toState(Bounds committed, EditCommit commit, ComponentState existing);

    /** Where to draw a module with saved {@code state} (null: never saved), given its {@code natural} bounds this frame. */
    Bounds toBounds(ComponentState state, Bounds natural);

    /** The region a dragged box is kept inside, or null for none. Re-read every frame. */
    default Bounds clampRegion() {
        return null;
    }

    /** {@link #toBounds} for whatever {@code store} has saved under {@code id}. */
    default Bounds resolve(PersistenceProvider store, String id, Bounds natural) {
        return toBounds(store.load(id).orElse(null), natural);
    }
}
