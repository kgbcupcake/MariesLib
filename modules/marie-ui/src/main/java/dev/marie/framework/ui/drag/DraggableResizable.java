package dev.marie.framework.ui.drag;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Size;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Generic drag/resize gesture tracker for a single {@link MarieComponent} target. Extracted from
 * an earlier consumer mod's edit-mode controller, which hand-rolled this same grab-offset drag and
 * origin+diagonal-ratio resize math three times (main panel and two sub-boxes) as static fields
 * on one class.
 *
 * <p>This is a composed helper rather than a mixin base class: {@link MarieComponent} is an
 * interface, so any component that wants gesture support holds a {@code DraggableResizable}
 * field (one per independently-draggable/resizable region, if it has more than one) instead of
 * being forced into an inheritance hierarchy just to get drag/resize behavior. Composition also
 * lets a single component own multiple gesture trackers, which a mixin-via-inheritance shape
 * could not express.
 *
 * <p>This class never persists anything itself. {@link #mouseReleased(int, int)} hands the final
 * committed {@link Bounds} to the {@code onCommit} callback supplied at construction — the caller
 * decides whether/how to run that through a {@link PersistenceProvider}.
 */
@ApiStatus.Experimental
public final class DraggableResizable {

    public static final int RESIZE_HANDLE_SIZE = 8;
    public static final int EDGE_HANDLE_THICKNESS = 4;

    /** Default snap distance in screen pixels — an edge within this of a snap line locks to it. */
    public static final int DEFAULT_SNAP_THRESHOLD_PX = 5;

    /**
     * Default max gap in screen pixels between this box and a registered sibling for the sibling's
     * edges to be snap candidates at all (see {@link SnapRegistry#computeSnapLines(String, Bounds, int)}),
     * so boxes only pull toward each other when they're actually close.
     */
    public static final int DEFAULT_SNAP_PROXIMITY_PX = 24;

    /** {@code CORNER} is bottom-right, {@code CORNER_BOTTOM_LEFT} is bottom-left; the four edge modes move only their own axis, anchored at the opposite fixed edge. */
    private enum ResizeMode {
        CORNER, CORNER_BOTTOM_LEFT, LEFT, RIGHT, TOP, BOTTOM
    }

    private final MarieComponent target;
    private Constraint constraint;
    private final BiConsumer<MarieComponent, Bounds> onCommit;
    private int snapThresholdPx = DEFAULT_SNAP_THRESHOLD_PX;
    private int snapProximityPx = DEFAULT_SNAP_PROXIMITY_PX;
    private List<Integer> snapXLines = List.of();
    private List<Integer> snapYLines = List.of();
    private List<Integer> snapWidths = List.of();
    private List<Integer> snapHeights = List.of();
    private List<Integer> extraXLines = List.of();
    private List<Integer> extraYLines = List.of();
    private Bounds parentBounds;
    private String snapRegistryId;

    private boolean dragging;
    private boolean resizing;
    private ResizeMode resizeMode;

    private int grabOffsetX;
    private int grabOffsetY;

    private int resizeOriginX;
    private int resizeOriginY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private int resizeFixedRight;
    private int resizeFixedBottom;

    private Bounds previewBounds;

    private boolean lastCommitWasResize;
    private ResizeMode lastCommitResizeMode;

    public DraggableResizable(MarieComponent target, Constraint constraint, BiConsumer<MarieComponent, Bounds> onCommit) {
        this(target, constraint, onCommit, null);
    }

    /**
     * As {@link #DraggableResizable(MarieComponent, Constraint, BiConsumer)}, but every subsequent
     * drag/resize preview and commit is clamped to stay fully inside {@code parentBounds} — for a
     * tracker that positions a component's content within its own (separately tracked) box bounds,
     * rather than the box itself. Pass {@code null} for the original unclamped behavior.
     */
    public DraggableResizable(MarieComponent target, Constraint constraint, BiConsumer<MarieComponent, Bounds> onCommit, Bounds parentBounds) {
        this.target = target;
        this.constraint = constraint;
        this.onCommit = onCommit;
        this.parentBounds = parentBounds;
    }

    /**
     * Replaces the clamp region used by every subsequent drag/resize preview until the next call —
     * {@code null} disables clamping. Callers whose parent region can move/resize across a single
     * edit-mode session should call this every frame, same reasoning as {@link #setConstraint}.
     */
    public void setParentBounds(Bounds parentBounds) {
        this.parentBounds = parentBounds;
    }

    /**
     * Replaces the min/max/preferred bounds used by {@link #clampWidth}/{@link #clampHeight} for
     * every subsequent call, including mid-gesture. Callers whose reference size depends on a scale
     * that can change across a single edit-mode session (e.g. the owning panel's own scale, if the
     * panel itself gets resized) should call this every frame with a freshly-computed {@link Constraint} —
     * otherwise the clamp stays frozen at whatever scale was active when this tracker was
     * constructed, and a resize committed at a very different live scale can convert to a wildly
     * wrong persisted size.
     */
    public void setConstraint(Constraint constraint) {
        this.constraint = constraint;
    }

    /**
     * Replaces the candidate snap lines (other components' edges, grid lines, etc., in the same
     * screen-pixel space as {@code mouseDragged}'s bounds) used by every subsequent drag/resize
     * preview until the next call — callers whose siblings can move should call this fresh each
     * frame, same reasoning as {@link #setConstraint}. Empty lists (the default) disable snapping.
     */
    public void setSnapTargets(List<Integer> xLines, List<Integer> yLines) {
        this.snapXLines = xLines;
        this.snapYLines = yLines;
    }

    /**
     * Candidate sizes (typically sibling boxes' widths/heights, same screen-pixel space) a resize
     * locks to when within {@link #snapThresholdPx}, so boxes can be matched in size the same way
     * {@link #setSnapTargets} lets them be lined up by edge. When both apply, whichever snap is
     * closer wins. Empty lists (the default) disable size snapping.
     */
    public void setSizeSnapTargets(List<Integer> widths, List<Integer> heights) {
        this.snapWidths = widths;
        this.snapHeights = heights;
    }

    /** Overrides {@link #DEFAULT_SNAP_THRESHOLD_PX} for this tracker. */
    public void setSnapThresholdPx(int snapThresholdPx) {
        this.snapThresholdPx = snapThresholdPx;
    }

    /** Overrides {@link #DEFAULT_SNAP_PROXIMITY_PX} for this tracker's registry-driven snapping. */
    public void setSnapProximityPx(int snapProximityPx) {
        this.snapProximityPx = snapProximityPx;
    }

    /**
     * Registers this tracker's own id with {@link SnapRegistry}: every subsequent {@link
     * #mouseDragged} call recomputes candidate snap lines from every other registered component
     * within {@link #snapProximityPx} of this one (via {@link SnapRegistry#computeSnapLines(String,
     * Bounds, int)}, excluding this id) and feeds them into {@link
     * #setSnapTargets} before processing the drag, so this tracker's siblings don't need their own
     * per-frame wiring. {@code null} (the default) disables registry-driven snapping; {@link
     * #setSnapTargets} can still be called manually in that case.
     */
    public void setSnapRegistryId(String snapRegistryId) {
        this.snapRegistryId = snapRegistryId;
    }

    /**
     * Lines (e.g. a panel's own edges) kept alongside {@link SnapRegistry}'s on every registry-driven
     * refresh, which would otherwise replace them. Without a registry id this does nothing; pass
     * everything to {@link #setSnapTargets} instead.
     */
    public void setExtraSnapTargets(List<Integer> xLines, List<Integer> yLines) {
        this.extraXLines = xLines;
        this.extraYLines = yLines;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isResizing() {
        return resizing;
    }

    /** Whether the resize handle should render in its "active" (currently being dragged) state. */
    public boolean isHandleActive() {
        return resizing;
    }

    /** Whether (mx, my) is within the bottom-right resize-handle hit region of {@code bounds}, for hover rendering. */
    public boolean isHandleHovered(int mx, int my, Bounds bounds) {
        return !resizing && isOverResizeHandle(mx, my, bounds);
    }

    /** Whether (mx, my) is within the bottom-left resize-handle hit region of {@code bounds}, for hover rendering. */
    public boolean isHandleBottomLeftHovered(int mx, int my, Bounds bounds) {
        return !resizing && isOverResizeHandleBottomLeft(mx, my, bounds);
    }

    /** Whether (mx, my) is within any edge-strip hit region of {@code bounds}, for hover rendering. */
    public boolean isEdgeHandleHovered(int mx, int my, Bounds bounds) {
        return !resizing && findEdgeMode(mx, my, bounds) != null;
    }

    /** Whether (mx, my) is within {@code edge}'s own hit region of {@code bounds}, for per-edge hover rendering. */
    public boolean isEdgeHovered(int mx, int my, Bounds bounds, Edge edge) {
        return !resizing && findEdgeHandle(mx, my, bounds) == edge;
    }

    /** Whether {@code edge} is the one currently being dragged, for per-edge active-state rendering. */
    public boolean isEdgeActive(Edge edge) {
        if (!resizing || resizeMode == null) {
            return false;
        }
        return switch (resizeMode) {
            case LEFT -> edge == Edge.LEFT;
            case RIGHT -> edge == Edge.RIGHT;
            case TOP -> edge == Edge.TOP;
            case BOTTOM -> edge == Edge.BOTTOM;
            case CORNER, CORNER_BOTTOM_LEFT -> false;
        };
    }

    /** Whether the active resize gesture is the bottom-right corner handle. */
    public boolean isCornerActive() {
        return resizing && resizeMode == ResizeMode.CORNER;
    }

    /** Whether the active resize gesture moves the left edge — the LEFT handle or the bottom-left corner. */
    public boolean isLeftEdgeGestureActive() {
        return resizing && (resizeMode == ResizeMode.LEFT || resizeMode == ResizeMode.CORNER_BOTTOM_LEFT);
    }

    /** Whether the active resize gesture is the bottom-left corner handle. */
    public boolean isBottomLeftCornerActive() {
        return resizing && resizeMode == ResizeMode.CORNER_BOTTOM_LEFT;
    }

    /** The 8x8 handle rectangle at the bottom-right corner of {@code bounds}, for render() to draw against. */
    public static Bounds handleBounds(Bounds bounds) {
        return new Bounds(
                bounds.x() + bounds.width() - RESIZE_HANDLE_SIZE,
                bounds.y() + bounds.height() - RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE
        );
    }

    /** The 8x8 handle rectangle at the bottom-left corner of {@code bounds}, for render() to draw against. */
    public static Bounds handleBoundsBottomLeft(Bounds bounds) {
        return new Bounds(
                bounds.x(),
                bounds.y() + bounds.height() - RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE
        );
    }

    public static boolean isOverResizeHandle(int mx, int my, Bounds bounds) {
        return within(mx, my, handleBounds(bounds));
    }

    public static boolean isOverResizeHandleBottomLeft(int mx, int my, Bounds bounds) {
        return within(mx, my, handleBoundsBottomLeft(bounds));
    }

    private static boolean within(int mx, int my, Bounds handle) {
        return mx >= handle.x() && my >= handle.y()
                && mx < handle.x() + handle.width() && my < handle.y() + handle.height();
    }

    /**
     * The thin hit-region strip along one edge of {@code bounds}, for render() to draw against.
     * Stops short of both corner handles so it never overlaps {@link #handleBounds}/{@link
     * #handleBoundsBottomLeft}.
     */
    public static Bounds edgeHandleBounds(Bounds bounds, Edge edge) {
        return switch (edge) {
            case LEFT -> new Bounds(bounds.x(), bounds.y(), EDGE_HANDLE_THICKNESS, bounds.height() - RESIZE_HANDLE_SIZE);
            case TOP -> new Bounds(bounds.x(), bounds.y(), bounds.width(), EDGE_HANDLE_THICKNESS);
            case RIGHT -> new Bounds(
                    bounds.x() + bounds.width() - EDGE_HANDLE_THICKNESS, bounds.y(),
                    EDGE_HANDLE_THICKNESS, bounds.height() - RESIZE_HANDLE_SIZE
            );
            case BOTTOM -> new Bounds(
                    bounds.x() + RESIZE_HANDLE_SIZE, bounds.y() + bounds.height() - EDGE_HANDLE_THICKNESS,
                    bounds.width() - RESIZE_HANDLE_SIZE * 2, EDGE_HANDLE_THICKNESS
            );
        };
    }

    /** Which edge (if any) of {@code bounds} contains (mx, my). Corner handle takes priority; callers should check {@link #isOverResizeHandle} first. */
    public static Edge findEdgeHandle(int mx, int my, Bounds bounds) {
        for (Edge edge : Edge.values()) {
            Bounds strip = edgeHandleBounds(bounds, edge);
            if (mx >= strip.x() && my >= strip.y()
                    && mx < strip.x() + strip.width() && my < strip.y() + strip.height()) {
                return edge;
            }
        }
        return null;
    }

    /** One of the four independently-draggable edges of a resizable region's bounds. */
    public enum Edge {
        LEFT, RIGHT, TOP, BOTTOM
    }

    private ResizeMode findEdgeMode(int mx, int my, Bounds bounds) {
        Edge edge = findEdgeHandle(mx, my, bounds);
        if (edge == null) {
            return null;
        }
        return switch (edge) {
            case LEFT -> ResizeMode.LEFT;
            case RIGHT -> ResizeMode.RIGHT;
            case TOP -> ResizeMode.TOP;
            case BOTTOM -> ResizeMode.BOTTOM;
        };
    }

    private static ResizeMode allowedEdgeMode(int mx, int my, Bounds bounds, Set<Edge> edges) {
        for (Edge edge : edges) {
            if (within(mx, my, edgeHandleBounds(bounds, edge))) {
                return switch (edge) {
                    case LEFT -> ResizeMode.LEFT;
                    case RIGHT -> ResizeMode.RIGHT;
                    case TOP -> ResizeMode.TOP;
                    case BOTTOM -> ResizeMode.BOTTOM;
                };
            }
        }
        return null;
    }

    /**
     * Call from the target's mouseClicked, passing its current committed {@code bounds}. Starts a
     * resize gesture if (mx, my) hits the handle, else a drag gesture if it's inside the bounds.
     * Returns true if a gesture started.
     */
    public boolean mouseClicked(int mx, int my, Bounds bounds) {
        return mouseClicked(mx, my, bounds, true, true, true, EnumSet.allOf(Edge.class));
    }

    /**
     * As {@link #mouseClicked(int, int, Bounds)}, but only the allowed gestures can start: a press on a
     * disallowed handle falls through to a move (if {@code move} is allowed) instead of resizing.
     */
    @ApiStatus.Experimental
    public boolean mouseClicked(int mx, int my, Bounds bounds, boolean move, boolean corner, boolean bottomLeftCorner,
                                Set<Edge> edges) {
        ResizeMode mode = corner && isOverResizeHandle(mx, my, bounds) ? ResizeMode.CORNER
                : bottomLeftCorner && isOverResizeHandleBottomLeft(mx, my, bounds) ? ResizeMode.CORNER_BOTTOM_LEFT
                : allowedEdgeMode(mx, my, bounds, edges);
        if (mode != null) {
            resizing = true;
            dragging = false;
            resizeMode = mode;
            resizeOriginX = bounds.x();
            resizeOriginY = bounds.y();
            resizeStartWidth = bounds.width();
            resizeStartHeight = bounds.height();
            resizeFixedRight = bounds.x() + bounds.width();
            resizeFixedBottom = bounds.y() + bounds.height();
            previewBounds = bounds;
            return true;
        }
        if (move && bounds.contains(mx, my)) {
            dragging = true;
            resizing = false;
            grabOffsetX = mx - bounds.x();
            grabOffsetY = my - bounds.y();
            previewBounds = bounds;
            return true;
        }
        return false;
    }

    /**
     * Call from the target's mouseDragged with absolute mouse coordinates. Returns the live
     * preview {@link Bounds} for the active gesture, or {@code null} if no gesture is active.
     */
    public Bounds mouseDragged(int mx, int my) {
        if (dragging) {
            int rawX = mx - grabOffsetX;
            int rawY = my - grabOffsetY;
            refreshRegistrySnapTargets(new Bounds(rawX, rawY, previewBounds.width(), previewBounds.height()));
            int snappedX = snapPosition(rawX, previewBounds.width(), snapXLines);
            int snappedY = snapPosition(rawY, previewBounds.height(), snapYLines);
            previewBounds = clampToParent(new Bounds(snappedX, snappedY, previewBounds.width(), previewBounds.height()));
            return previewBounds;
        }
        if (resizing) {
            refreshRegistrySnapTargets(previewBounds);
            previewBounds = clampToParent(switch (resizeMode) {
                case CORNER -> {
                    // Width/height each track the cursor directly, like RIGHT+BOTTOM combined — not a
                    // locked-aspect diagonal scale. Matches how every desktop window manager resizes
                    // from a corner grip: the corner follows the mouse 1:1 on both axes.
                    int newWidth = snapWidth(mx - resizeOriginX, resizeOriginX, true);
                    int newHeight = snapHeight(my - resizeOriginY, resizeOriginY, true);
                    yield new Bounds(resizeOriginX, resizeOriginY, newWidth, newHeight);
                }
                case CORNER_BOTTOM_LEFT -> {
                    // LEFT + BOTTOM combined: fixed at the top-right corner, tracking the cursor on both axes.
                    int newWidth = snapWidth(resizeFixedRight - mx, resizeFixedRight, false);
                    int newHeight = snapHeight(my - resizeOriginY, resizeOriginY, true);
                    yield new Bounds(resizeFixedRight - newWidth, resizeOriginY, newWidth, newHeight);
                }
                case RIGHT -> {
                    int newWidth = snapWidth(mx - resizeOriginX, resizeOriginX, true);
                    yield new Bounds(resizeOriginX, resizeOriginY, newWidth, resizeStartHeight);
                }
                case LEFT -> {
                    int newWidth = snapWidth(resizeFixedRight - mx, resizeFixedRight, false);
                    yield new Bounds(resizeFixedRight - newWidth, resizeOriginY, newWidth, resizeStartHeight);
                }
                case BOTTOM -> {
                    int newHeight = snapHeight(my - resizeOriginY, resizeOriginY, true);
                    yield new Bounds(resizeOriginX, resizeOriginY, resizeStartWidth, newHeight);
                }
                case TOP -> {
                    int newHeight = snapHeight(resizeFixedBottom - my, resizeFixedBottom, false);
                    yield new Bounds(resizeOriginX, resizeFixedBottom - newHeight, resizeStartWidth, newHeight);
                }
            });
            return previewBounds;
        }
        return null;
    }

    /** Pulls fresh snap lines from {@link SnapRegistry}, limited to siblings near {@code near}; no-op without a registry id. */
    private void refreshRegistrySnapTargets(Bounds near) {
        if (snapRegistryId != null) {
            SnapRegistry.SnapLines lines = SnapRegistry.computeSnapLines(snapRegistryId, near, snapProximityPx);
            List<Integer> xs = new ArrayList<>(lines.xLines());
            List<Integer> ys = new ArrayList<>(lines.yLines());
            xs.addAll(extraXLines);
            ys.addAll(extraYLines);
            setSnapTargets(xs, ys);
        }
    }

    private int snapPosition(int rawPos, int size, List<Integer> lines) {
        return SnapMath.snapPosition(rawPos, size, lines, snapThresholdPx);
    }

    private int snapWidth(int rawWidth, int anchor, boolean growsPositive) {
        return clampWidth(SnapMath.snapSize(clampWidth(rawWidth), anchor, growsPositive, snapXLines, snapWidths, snapThresholdPx));
    }

    private int snapHeight(int rawHeight, int anchor, boolean growsPositive) {
        return clampHeight(SnapMath.snapSize(clampHeight(rawHeight), anchor, growsPositive, snapYLines, snapHeights, snapThresholdPx));
    }

    /**
     * Call from the target's mouseReleased. Ends any active gesture and hands the final committed
     * {@link Bounds} to the {@code onCommit} callback supplied at construction.
     */
    public void mouseReleased(int mx, int my) {
        if (!dragging && !resizing) {
            return;
        }
        boolean wasResizing = resizing;
        ResizeMode committedMode = resizeMode;
        dragging = false;
        resizing = false;
        if (previewBounds != null) {
            lastCommitWasResize = wasResizing;
            lastCommitResizeMode = wasResizing ? committedMode : null;
            onCommit.accept(target, previewBounds);
        }
        previewBounds = null;
    }

    /** Whether the just-committed gesture changed width — LEFT, RIGHT, or either corner. False for a plain reposition drag. */
    public boolean lastCommitAffectedWidth() {
        return lastCommitWasResize && (lastCommitResizeMode == ResizeMode.LEFT
                || lastCommitResizeMode == ResizeMode.RIGHT
                || lastCommitResizeMode == ResizeMode.CORNER
                || lastCommitResizeMode == ResizeMode.CORNER_BOTTOM_LEFT);
    }

    /** Whether the just-committed gesture changed height — TOP, BOTTOM, or either corner. False for a plain reposition drag. */
    public boolean lastCommitAffectedHeight() {
        return lastCommitWasResize && (lastCommitResizeMode == ResizeMode.TOP
                || lastCommitResizeMode == ResizeMode.BOTTOM
                || lastCommitResizeMode == ResizeMode.CORNER
                || lastCommitResizeMode == ResizeMode.CORNER_BOTTOM_LEFT);
    }

    /** Whether the just-committed gesture moved the LEFT edge — the LEFT handle or the bottom-left corner. */
    public boolean lastCommitWasLeftEdge() {
        return lastCommitWasResize && (lastCommitResizeMode == ResizeMode.LEFT
                || lastCommitResizeMode == ResizeMode.CORNER_BOTTOM_LEFT);
    }

    private Bounds clampToParent(Bounds b) {
        return SnapMath.clampToParent(b, parentBounds);
    }

    private int clampWidth(int width) {
        Size min = constraint.minSize();
        Size max = constraint.maxSize();
        return Math.max(min.width(), Math.min(max.width(), width));
    }

    private int clampHeight(int height) {
        Size min = constraint.minSize();
        Size max = constraint.maxSize();
        return Math.max(min.height(), Math.min(max.height(), height));
    }
}
