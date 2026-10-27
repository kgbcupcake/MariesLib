package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.drag.DraggableResizable.Edge;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which edit actions an {@link EditableModule} accepts in a {@link ModuleEditHost}: moving the whole
 * box, resizing from each edge and corner, and which {@link MoveDrag.Mode move modes} may drag its
 * content. Immutable; start from a preset and adjust with the {@code with...} methods.
 *
 * <pre>{@code
 * EditCapabilities.full();                                  // move, every edge and corner, every move mode
 * EditCapabilities.sizeOnly();                              // pinned top-left: right/bottom edges + corner
 * EditCapabilities.full().withResizeEdges(Edge.RIGHT).withMoveModes(MoveDrag.Mode.TEXT);
 * }</pre>
 */
@ApiStatus.Experimental
public final class EditCapabilities {

    private final boolean boxMove;
    private final boolean corner;
    private final boolean bottomLeftCorner;
    private final Set<Edge> edges;
    private final Set<MoveDrag.Mode> moveModes;
    private final boolean keepContentOnEdgeResize;

    private EditCapabilities(boolean boxMove, boolean corner, boolean bottomLeftCorner, Set<Edge> edges,
                             Set<MoveDrag.Mode> moveModes, boolean keepContentOnEdgeResize) {
        this.boxMove = boxMove;
        this.corner = corner;
        this.bottomLeftCorner = bottomLeftCorner;
        this.edges = Collections.unmodifiableSet(edges.isEmpty() ? EnumSet.noneOf(Edge.class) : EnumSet.copyOf(edges));
        this.moveModes = Collections.unmodifiableSet(moveModes.isEmpty() ? EnumSet.noneOf(MoveDrag.Mode.class) : EnumSet.copyOf(moveModes));
        this.keepContentOnEdgeResize = keepContentOnEdgeResize;
    }

    /** Move, resize from every edge and both bottom corners, every move mode, content kept in place on a left/top resize. */
    public static EditCapabilities full() {
        return new EditCapabilities(true, true, true, EnumSet.allOf(Edge.class), EnumSet.allOf(MoveDrag.Mode.class), true);
    }

    /** No box move; grows from the right/bottom edges and bottom-right corner only, so the top-left stays pinned. Every move mode. */
    public static EditCapabilities sizeOnly() {
        return new EditCapabilities(false, true, false, EnumSet.of(Edge.RIGHT, Edge.BOTTOM), EnumSet.allOf(MoveDrag.Mode.class), false);
    }

    /** Only the content move modes; the box itself can't be moved or resized. */
    public static EditCapabilities contentOnly() {
        return new EditCapabilities(false, false, false, EnumSet.noneOf(Edge.class), EnumSet.allOf(MoveDrag.Mode.class), false);
    }

    public EditCapabilities withBoxMove(boolean allowed) {
        return new EditCapabilities(allowed, corner, bottomLeftCorner, edges, moveModes, keepContentOnEdgeResize);
    }

    /** Only these edges resize (corners are set separately with {@link #withCorners}). */
    public EditCapabilities withResizeEdges(Edge... allowed) {
        EnumSet<Edge> set = EnumSet.noneOf(Edge.class);
        Collections.addAll(set, allowed);
        return new EditCapabilities(boxMove, corner, bottomLeftCorner, set, moveModes, keepContentOnEdgeResize);
    }

    public EditCapabilities withCorners(boolean bottomRight, boolean bottomLeft) {
        return new EditCapabilities(boxMove, bottomRight, bottomLeft, edges, moveModes, keepContentOnEdgeResize);
    }

    /** Only these move modes drag content; with none, an active move toggle is ignored and the box handles show instead. */
    public EditCapabilities withMoveModes(MoveDrag.Mode... allowed) {
        EnumSet<MoveDrag.Mode> set = EnumSet.noneOf(MoveDrag.Mode.class);
        Collections.addAll(set, allowed);
        return new EditCapabilities(boxMove, corner, bottomLeftCorner, edges, set, keepContentOnEdgeResize);
    }

    /**
     * Whether a resize from the left/top edge shifts the module's content offsets so its content stays
     * put on screen. Turn off for a module that lays its content out from its live bounds anyway.
     */
    public EditCapabilities keepContentOnEdgeResize(boolean keep) {
        return new EditCapabilities(boxMove, corner, bottomLeftCorner, edges, moveModes, keep);
    }

    public boolean canBoxMove() {
        return boxMove;
    }

    public boolean canResize(Edge edge) {
        return edges.contains(edge);
    }

    public Set<Edge> resizeEdges() {
        return edges;
    }

    public boolean bottomRightCorner() {
        return corner;
    }

    public boolean bottomLeftCorner() {
        return bottomLeftCorner;
    }

    public boolean supports(MoveDrag.Mode mode) {
        return mode != null && moveModes.contains(mode);
    }

    public boolean keepsContentOnEdgeResize() {
        return keepContentOnEdgeResize;
    }

    /** Whether any box gesture (move or resize) is allowed at all. */
    public boolean hasBoxGestures() {
        return boxMove || corner || bottomLeftCorner || !edges.isEmpty();
    }
}
