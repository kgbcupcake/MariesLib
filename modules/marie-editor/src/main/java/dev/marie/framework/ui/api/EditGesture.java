package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * The gesture a {@link ModuleEditHost} is running right now, for a container whose own layout depends
 * on it (e.g. growing a margin only while a left edge is dragged). {@code moveMode} is set only for
 * {@link Kind#CONTENT_MOVE}.
 */
@ApiStatus.Experimental
public record EditGesture(String moduleId, Kind kind, Bounds start, MoveDrag.Mode moveMode,
                          boolean leftEdge, boolean rightEdge, boolean topEdge, boolean bottomEdge) {

    public enum Kind {
        /** The whole box is being dragged. */
        BOX_MOVE,
        /** An edge or corner is being dragged. */
        RESIZE,
        /** A move mode is dragging part of the module's content; the box stays put. */
        CONTENT_MOVE
    }

    /** As an {@link EditCommit} for the box ending at its current position. */
    public EditCommit toCommit() {
        return toCommit(false);
    }

    /** As {@link #toCommit()}, noting whether the host already kept the content in place (see {@link EditCommit}). */
    public EditCommit toCommit(boolean contentKeptInPlace) {
        return new EditCommit(start, kind == Kind.RESIZE, leftEdge, rightEdge, topEdge, bottomEdge, contentKeptInPlace);
    }
}
