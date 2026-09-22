package dev.marie.framework.ui.animations;

import dev.marie.framework.api.ApiStatus;

/**
 * Frame-rate-independent value smoother: eases a float toward whatever target it's fed, at a fixed
 * time constant, instead of jumping to it immediately. Generalizes the dt-based lerp Nourished's
 * Diet Screen and HUD nutrient bars each hand-rolled independently as a private {@code Map<String,
 * Float>} field updated once per render frame ({@code cur + (target - cur) * min(1, dt / duration)})
 * — this is that same formula as a small reusable primitive, for any bar/value display that wants a
 * value to visibly ease toward a changing target rather than snapping to it.
 *
 * <p>Not thread-safe; intended for one instance per animated value, updated from the render thread.
 * A consumer whose backing component is rebuilt every frame (e.g. a {@code SelfPositioningModule}
 * following the {@code BalanceComponent} pattern) keeps the {@link AnimatedFloat} itself in a
 * longer-lived cache (keyed by whatever identifies the value, e.g. a nutrient key) and calls {@link
 * #update} once per frame from the freshly-built component's constructor, rather than creating a new
 * instance each frame — a fresh instance has no memory of the eased value, so it would never animate.
 *
 * <p>This is the first class in a dedicated {@code dev.marie.framework.ui.animations} package,
 * intended as the home for future animation-related primitives in MariesLib generally, not just
 * this one utility.
 */
@ApiStatus.Experimental
public final class AnimatedFloat {

    private final float durationSeconds;
    private float current;
    private float previous;
    private long lastNanos;
    private boolean hasLast;

    /** Starts at {@code 0f} and eases toward whatever target {@link #update} is first called with, over {@code durationSeconds}. */
    public AnimatedFloat(float durationSeconds) {
        this(durationSeconds, 0f);
    }

    /** As {@link #AnimatedFloat(float)}, but starting from {@code initialValue} instead of {@code 0f}. */
    public AnimatedFloat(float durationSeconds, float initialValue) {
        this.durationSeconds = durationSeconds;
        this.current = initialValue;
        this.previous = initialValue;
    }

    /**
     * Advances this value toward {@code target}, by however much time has passed (in nanoseconds,
     * e.g. {@code System.nanoTime()}) since the last call to {@link #update} — the first call after
     * construction (or after a long pause) advances by {@code 0} (no elapsed-time baseline yet), so
     * the value starts exactly at its initial value and only begins easing from the next call on.
     *
     * @return the new current (eased) value — same as {@link #value()} right after this call
     */
    public float update(float target, long nowNanos) {
        previous = current;
        float dt = 0f;
        if (hasLast) {
            dt = Math.max(0f, (nowNanos - lastNanos) / 1_000_000_000f);
        }
        lastNanos = nowNanos;
        hasLast = true;
        float step = durationSeconds <= 0f ? 1f : Math.min(1f, dt / durationSeconds);
        current += (target - current) * step;
        return current;
    }

    /** The current eased value, as of the last {@link #update} call. */
    public float value() {
        return current;
    }

    /** The eased value as of the call to {@link #update} before the most recent one — for a caller that wants its own trend/direction comparison against the animated value rather than the raw target. */
    public float previousValue() {
        return previous;
    }
}
