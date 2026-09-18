package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Size;

/**
 * Public utility for the "shrink content together to fit a box smaller than its natural size"
 * pattern — deliberately the opposite number to {@link ContentScaleController}, whose user-driven
 * {@code contentScale}/{@code paddingScale} is intentionally box-independent (per that class's own
 * doc, content overflows and gets clipped rather than shrinking when the box is too small). A
 * component that instead wants its content — icon size, row height, bar dimensions, font scale —
 * to shrink as one unit when dragged below natural size, rather than staying pinned and clipping
 * mid-row, multiplies this scale into its own content-scale factor alongside (not instead of) the
 * user's {@link ContentScaleController#resolveContentScale} adjustment.
 *
 * <p>Small enough to live directly here as its own utility rather than a subsystem-package
 * implementation behind a separate facade, same as {@link SnapRegistry}.
 *
 * <pre>{@code
 * double contentScale = ContentScaleController.resolveContentScale(userAdjustment)
 *         * BoxFitScale.of(bounds, naturalSize, MIN_SHRINK_SCALE);
 * }</pre>
 */
@ApiStatus.Experimental
public final class BoxFitScale {

    private BoxFitScale() {}

    /**
     * How far below {@code 1.0} content should shrink to fit a {@code boxWidth}x{@code boxHeight}
     * box against a {@code naturalWidth}x{@code naturalHeight} natural size — {@code 1.0} once the
     * box is at or above natural size on both axes (no shrink; a box dragged larger than natural is
     * the caller's own business, e.g. extra margin), floored at {@code minScale} so content never
     * shrinks past that point. Whatever still doesn't fit once the floor is hit is left entirely to
     * the caller (e.g. scrolling, or simply clipping) — this method only ever returns a single
     * uniform multiplier, never a per-axis one.
     */
    public static double of(int boxWidth, int boxHeight, int naturalWidth, int naturalHeight, double minScale) {
        double raw = Math.min(1.0, Math.min((double) boxWidth / naturalWidth, (double) boxHeight / naturalHeight));
        return Math.max(minScale, raw);
    }

    /** {@link Bounds}/{@link Size}-typed convenience overload of {@link #of(int, int, int, int, double)}. */
    public static double of(Bounds box, Size natural, double minScale) {
        return of(box.width(), box.height(), natural.width(), natural.height(), minScale);
    }
}
