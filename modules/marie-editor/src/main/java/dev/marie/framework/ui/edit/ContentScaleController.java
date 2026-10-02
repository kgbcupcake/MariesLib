package dev.marie.framework.ui.edit;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Generic accessor for a component's persisted, box-independent content scale and padding
 * multipliers, keyed by component id.
 *
 * <p>Content size is driven by the user's persisted adjustment alone, full stop — never by box
 * size. Every component still computes its own proportional scale as {@code Math.min(widthScale,
 * heightScale)} against its resolved {@link Bounds}, and that box-driven number is still the
 * foundation for the component's own box sizing and coordinate mapping, but it plays no part in
 * content/text scale: {@link #resolveContentScale} just passes the user's chosen scale through
 * (sanity-clamped). If the box is too small to fit that scale, the content overflows the box's
 * natural extent and gets cut off by the component's own clip region instead of shrinking.
 *
 * <p>Editing the persisted contentScale/paddingScale happens exclusively through each component's
 * {@code ScaleConfigPanel} card — this class no longer recognizes any gesture of its own.
 */
@ApiStatus.Experimental
public final class ContentScaleController {

    /** Valid range for the persisted contentScale/paddingScale multipliers themselves — the correct bound for anything editing those fields directly (e.g. a slider), as opposed to {@link #resolvePadding}'s sanity clamp, which is in different (already-resolved, caller-specific pixel) units. */
    public static final double SCALE_STORAGE_MIN = 0.1d;
    public static final double SCALE_STORAGE_MAX = 5.0d;

    private final PersistenceProvider persistence;

    public ContentScaleController(PersistenceProvider persistence) {
        this.persistence = persistence;
    }

    /** {@code componentId}'s persisted contentScale multiplier, or {@link ComponentState#DEFAULT_CONTENT_SCALE} if never set. */
    public double contentScaleAdjustment(String componentId) {
        return persistence.load(componentId).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    /** {@code componentId}'s persisted paddingScale multiplier, or {@link ComponentState#DEFAULT_PADDING_SCALE} if never set. */
    public double paddingScaleAdjustment(String componentId) {
        return persistence.load(componentId).map(ComponentState::paddingScale).orElse(ComponentState.DEFAULT_PADDING_SCALE);
    }

    /** Sanity clamp for {@link #resolveContentScale} — a multiplier, so degenerate values are caught close to zero/five. */
    private static final double CONTENT_SCALE_SANITY_MIN = 0.1d;
    private static final double CONTENT_SCALE_SANITY_MAX = 5.0d;

    /** Sanity clamp for {@link #resolvePadding} — already-resolved local-pixel units (caller's reference padding times its multiplier), so the range is wide rather than a multiplier-shaped [0.1, 5.0]. */
    private static final double PADDING_SANITY_MIN = 0.0d;
    private static final double PADDING_SANITY_MAX = 1000.0d;

    /**
     * Pass-through for the user's persisted content-scale adjustment: box size plays no part in this
     * — {@code userScaleAdjustment} is returned as-is, only sanity-clamped to {@code
     * [CONTENT_SCALE_SANITY_MIN, CONTENT_SCALE_SANITY_MAX]} to stop degenerate (e.g. corrupted-save)
     * values, not to constrain it to whatever the box can currently fit. If the box is too small for
     * the resulting content, the caller's own clip region is what cuts it off, not this method.
     */
    public static float resolveContentScale(double userScaleAdjustment) {
        return (float) clamp(userScaleAdjustment, CONTENT_SCALE_SANITY_MIN, CONTENT_SCALE_SANITY_MAX);
    }

    /**
     * Analogous to {@link #resolveContentScale}, for a component's padding: {@code userPaddingAmount}
     * (the persisted paddingScale multiplier already applied to the component's reference padding) is
     * returned as-is, only sanity-clamped to {@code [PADDING_SANITY_MIN, PADDING_SANITY_MAX]}.
     */
    public static float resolvePadding(double userPaddingAmount) {
        return (float) clamp(userPaddingAmount, PADDING_SANITY_MIN, PADDING_SANITY_MAX);
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
