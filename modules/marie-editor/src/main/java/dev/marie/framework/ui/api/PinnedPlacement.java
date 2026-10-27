package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.DoubleSupplier;

/**
 * For a module pinned at a fixed top-left (a hub tab's content, say): only its size is saved, divided
 * by the given scale; its position always comes from the container. Pair it with {@link
 * EditCapabilities#sizeOnly()}. An axis the player never resized follows the natural size.
 */
@ApiStatus.Experimental
public final class PinnedPlacement implements PlacementStrategy {

    private static final ComponentState BLANK = new ComponentState(0, 0, 0, 0, false, false, false, 0);

    private final DoubleSupplier scale;

    private PinnedPlacement(DoubleSupplier scale) {
        this.scale = scale;
    }

    /** Sizes saved in screen pixels (scale 1). */
    public static PinnedPlacement of() {
        return new PinnedPlacement(() -> 1.0d);
    }

    /** Sizes saved in local units at {@code scale} screen pixels each. */
    public static PinnedPlacement of(DoubleSupplier scale) {
        return new PinnedPlacement(scale);
    }

    @Override
    public ComponentState toState(Bounds committed, EditCommit commit, ComponentState existing) {
        ComponentState base = existing != null ? existing : BLANK;
        double s = safeScale();
        return new ComponentState(base.x(), base.y(),
                (int) Math.round(committed.width() / s), (int) Math.round(committed.height() / s),
                base.collapsed(), commit.widthManual(existing), commit.heightManual(existing),
                base.leftMargin(), base.contentScale(), base.paddingScale());
    }

    @Override
    public Bounds toBounds(ComponentState state, Bounds natural) {
        if (state == null) {
            return natural;
        }
        double s = safeScale();
        return new Bounds(natural.x(), natural.y(),
                state.widthManual() ? (int) Math.round(state.width() * s) : natural.width(),
                state.heightManual() ? (int) Math.round(state.height() * s) : natural.height());
    }

    private double safeScale() {
        double s = scale.getAsDouble();
        return s > 0 ? s : 1.0d;
    }
}
