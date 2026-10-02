package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

/**
 * Persisted position, size, and state for a component, stored externally by component id.
 *
 * <p>Manual size flags indicate whether the user has explicitly overridden an auto-computed axis.
 * {@code leftMargin} is an accumulated screen-pixel offset for components that reserve dead space
 * before their content on one side, grown only by dragging that side's edge — never touched by
 * resizing the opposite edge, so it can't jump when a different edge starts a gesture.
 *
 * <p>{@code contentScale} is a multiplier applied on top of a component's existing box-driven
 * proportional content scale (its own {@code Math.min(widthScale, heightScale)}) rather than an
 * independent absolute scale — see {@code ContentScaleController} (marie-editor).
 * Defaults to {@code 1.0} (no adjustment, proportional scale passes through unchanged) for any
 * component that doesn't use this.
 *
 * <p>{@code paddingScale} is the analogous user multiplier for a component's content padding,
 * also managed by {@code ContentScaleController} (marie-editor). Defaults to
 * {@code 1.0} (no adjustment).
 */
@ApiStatus.Experimental
public record ComponentState(
        int x,
        int y,
        int width,
        int height,
        boolean collapsed,
        boolean widthManual,
        boolean heightManual,
        int leftMargin,
        double contentScale,
        double paddingScale
) {

    /** Default content scale multiplier for components that don't use it: 1.0 (no adjustment). */
    public static final double DEFAULT_CONTENT_SCALE = 1.0;

    /** Default padding scale multiplier for components that don't use it: 1.0 (no adjustment). */
    public static final double DEFAULT_PADDING_SCALE = 1.0;

    public ComponentState(int x, int y, int width, int height, boolean collapsed,
                           boolean widthManual, boolean heightManual, int leftMargin) {
        this(x, y, width, height, collapsed, widthManual, heightManual, leftMargin,
                DEFAULT_CONTENT_SCALE, DEFAULT_PADDING_SCALE);
    }

    /** Back-compat for callers built against the pre-{@code paddingScale} shape. */
    public ComponentState(int x, int y, int width, int height, boolean collapsed,
                           boolean widthManual, boolean heightManual, int leftMargin, double contentScale) {
        this(x, y, width, height, collapsed, widthManual, heightManual, leftMargin,
                contentScale, DEFAULT_PADDING_SCALE);
    }
}
