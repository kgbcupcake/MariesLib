package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * What a finished box gesture did, handed to a {@link PlacementStrategy} with the committed bounds:
 * where the box started, whether it was resized and which edges moved. Use {@link #widthManual}/{@link
 * #heightManual} to keep an axis auto-sized until the player actually resizes it.
 */
@ApiStatus.Experimental
public record EditCommit(Bounds start, boolean resized, boolean leftEdge, boolean rightEdge, boolean topEdge, boolean bottomEdge) {

    public boolean affectsWidth() {
        return resized && (leftEdge || rightEdge);
    }

    public boolean affectsHeight() {
        return resized && (topEdge || bottomEdge);
    }

    /** Whether the width should now count as player-set: it already was, or this gesture changed it. */
    public boolean widthManual(ComponentState existing) {
        return affectsWidth() || (existing != null && existing.widthManual());
    }

    public boolean heightManual(ComponentState existing) {
        return affectsHeight() || (existing != null && existing.heightManual());
    }
}
